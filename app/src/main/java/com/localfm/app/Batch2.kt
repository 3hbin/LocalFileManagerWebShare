package com.localfm.app

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/* ---------- 2. Hoàn tác ---------- */

class UndoOp(val label: String, val run: () -> Unit)

object UndoStack {
    val ops = mutableStateListOf<UndoOp>()

    fun push(label: String, run: () -> Unit) {
        OpLog.add(label)
        ops.add(UndoOp(label, run))
        if (ops.size > 20) ops.removeAt(0)
    }
}

/* ---------- 25. Khu vực tác vụ nền ---------- */

class FmTask(val title: String) {
    /** -1 = không xác định (vòng xoay), 0..1 = theo phần trăm. */
    var progress by mutableStateOf(-1f)
    var detail by mutableStateOf("")
    var finished by mutableStateOf(false)
    var cancelled by mutableStateOf(false)
    var onCancel: () -> Unit = {}

    fun cancel() {
        cancelled = true
        onCancel()
    }
}

object TaskCenter {
    val tasks = mutableStateListOf<FmTask>()

    fun start(title: String): FmTask {
        val t = FmTask(title)
        tasks.add(t)
        return t
    }

    fun finish(t: FmTask) {
        t.finished = true
        tasks.remove(t)
    }
}

/* ---------- 22. Chế độ chỉ đọc ---------- */

object ReadOnlyLocks {
    private const val P = "local_fm_prefs"
    private const val KEY = "readonly_paths"
    val paths = mutableStateListOf<String>()
    private var loaded = false

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        val raw = ctx.applicationContext.getSharedPreferences(P, 0).getString(KEY, "[]")
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        for (i in 0 until arr.length()) paths.add(arr.getString(i))
    }

    /** Khoá có hiệu lực cho chính mục đó và mọi mục nằm bên trong nếu đó là thư mục. */
    fun isLocked(f: File): Boolean {
        if (SystemGuard.isProtected(f)) return true
        val p = f.absolutePath
        return paths.any { p == it || p.startsWith("$it/") }
    }

    fun toggle(ctx: Context, f: File) {
        init(ctx)
        val p = f.absolutePath
        if (p in paths) paths.remove(p) else paths.add(p)
        val arr = JSONArray()
        paths.forEach { arr.put(it) }
        ctx.applicationContext.getSharedPreferences(P, 0).edit().putString(KEY, arr.toString()).apply()
    }
}

/* ---------- 4. Bộ lọc tìm kiếm nâng cao ---------- */

data class FileFilter(
    val exts: Set<String> = emptySet(),
    val minBytes: Long = 0L,
    val maxBytes: Long = Long.MAX_VALUE,
    val modifiedWithinDays: Int = 0
) {
    val active: Boolean
        get() = exts.isNotEmpty() || minBytes > 0L || maxBytes != Long.MAX_VALUE || modifiedWithinDays > 0

    fun matches(f: File): Boolean {
        if (!active) return true
        if (modifiedWithinDays > 0 &&
            System.currentTimeMillis() - f.lastModified() > modifiedWithinDays * 86_400_000L) return false
        if (f.isDirectory) return exts.isEmpty() && minBytes == 0L && maxBytes == Long.MAX_VALUE
        if (exts.isNotEmpty() && f.extension.lowercase() !in exts) return false
        val len = f.length()
        return len >= minBytes && len <= maxBytes
    }
}

/* ---------- 3. Copy/di chuyển có tiến trình ---------- */

class PasteOutcome(val ok: Int, val fail: Int, val undo: List<() -> Unit>, val cancelled: Boolean)

private fun sizeOfPath(f: File): Long =
    if (f.isFile) f.length() else FolderSize.of(f, limitFiles = 50_000)

/** Sao chép cây thư mục/tệp, báo số byte đã ghi để tính tiến trình. */
private fun copyTree(from: File, to: File, onBytes: (Long) -> Unit, cancelled: () -> Boolean) {
    if (cancelled()) return
    if (from.isDirectory) {
        to.mkdirs()
        from.listFiles().orEmpty().forEach { copyTree(it, File(to, it.name), onBytes, cancelled) }
    } else {
        to.parentFile?.mkdirs()
        FileInputStream(from).use { ins ->
            FileOutputStream(to).use { out ->
                val buf = ByteArray(64 * 1024)
                while (!cancelled()) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    onBytes(n.toLong())
                }
            }
        }
    }
}

/** Dán các mục trong [clip] vào [destDir]. Chạy trên luồng nền, không làm đứng giao diện. */
fun runPaste(
    ctx: Context,
    clip: ClipBoard,
    destDir: File,
    report: (Float, String) -> Unit,
    cancelled: () -> Boolean
): PasteOutcome {
    val srcs = clip.paths.map { File(it) }.filter { it.exists() }
    val total = srcs.sumOf { sizeOfPath(it) }.coerceAtLeast(1L)
    var done = 0L
    var ok = 0
    var fail = 0
    val undo = mutableListOf<() -> Unit>()

    for (src in srcs) {
        if (cancelled()) break
        if (ReadOnlyLocks.isLocked(destDir) || (clip.cut && ReadOnlyLocks.isLocked(src))) {
            fail++
            continue
        }
        val target = uniqueCopyName(destDir, src)
        try {
            if (clip.cut && src.renameTo(target)) {
                // Cùng ổ đĩa: chuyển tức thì, không cần copy
                done += sizeOfPath(target)
                report((done.toFloat() / total).coerceAtMost(1f), src.name)
                undo += { target.renameTo(src) }
                ok++
                continue
            }
            copyTree(src, target, { n ->
                done += n
                report((done.toFloat() / total).coerceAtMost(1f), src.name)
            }, cancelled)
            if (cancelled()) {
                target.deleteRecursively()
                break
            }
            if (clip.cut) {
                // Chuyển khác ổ: copy xong mới xoá gốc. Hoàn tác = copy ngược lại rồi xoá bản đích.
                val back = src
                src.deleteRecursively()
                undo += {
                    copyTree(target, back, {}, { false })
                    target.deleteRecursively()
                }
            } else {
                undo += { TrashBin.moveToTrashFile(ctx, target) }
            }
            ok++
        } catch (_: Exception) {
            fail++
        }
    }
    if (clip.cut && fail == 0 && !cancelled()) ClipHolder.clip = null
    return PasteOutcome(ok, fail, undo, cancelled())
}

/* ---------- Giao diện: hoàn tác, tác vụ nền ---------- */

/** Vùng dưới cùng: thanh hoàn tác và khu vực tác vụ đang chạy. */
@Composable
fun BottomOverlayHost(onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var showTasks by remember { mutableStateOf(false) }
    val op = UndoStack.ops.lastOrNull()
    if (op != null) {
        LaunchedEffect(op) {
            delay(6000)
            UndoStack.ops.remove(op)
        }
    }
    val active = TaskCenter.tasks.filter { !it.finished }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        if (op != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.inverseSurface
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        op.label,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        UndoStack.ops.remove(op)
                        scope.launch {
                            withContext(Dispatchers.IO) { op.run() }
                            onRefresh()
                        }
                    }) {
                        Text("Hoàn tác", color = MaterialTheme.colorScheme.inversePrimary)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (active.isNotEmpty()) {
            val first = active.first()
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { showTasks = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        "${active.size} tác vụ đang chạy · ${first.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    TaskBar(first.progress, Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (showTasks) TaskListDialog(onDismiss = { showTasks = false })
}

@Composable
private fun TaskBar(progress: Float, modifier: Modifier) {
    if (progress >= 0f) ExpressiveLinearProgress(progress = progress, modifier = modifier)
    else ExpressiveLinearProgress(modifier = modifier)
}

@Composable
fun TaskListDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tác vụ đang chạy") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                val list = TaskCenter.tasks.filter { !it.finished }
                if (list.isEmpty()) Text("Không có tác vụ nào")
                list.forEach { t ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                t.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (!t.cancelled) TextButton(onClick = { t.cancel() }) { Text("Huỷ") }
                        }
                        TaskBar(t.progress, Modifier.fillMaxWidth())
                        if (t.detail.isNotBlank()) {
                            Text(
                                t.detail,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

/* ---------- Giao diện: bộ lọc tìm kiếm ---------- */

@Composable
fun FilterDialog(initial: FileFilter, onApply: (FileFilter) -> Unit, onDismiss: () -> Unit) {
    var exts by remember { mutableStateOf(initial.exts.joinToString(", ")) }
    var minMb by remember {
        mutableStateOf(if (initial.minBytes > 0) (initial.minBytes / 1_000_000.0).toString() else "")
    }
    var maxMb by remember {
        mutableStateOf(if (initial.maxBytes != Long.MAX_VALUE) (initial.maxBytes / 1_000_000.0).toString() else "")
    }
    var days by remember {
        mutableStateOf(if (initial.modifiedWithinDays > 0) initial.modifiedWithinDays.toString() else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bộ lọc tìm kiếm") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = exts, onValueChange = { exts = it }, singleLine = true,
                    label = { Text("Đuôi file (cách nhau bằng dấu phẩy)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = minMb, onValueChange = { minMb = it }, singleLine = true,
                    label = { Text("Kích thước tối thiểu (MB)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = maxMb, onValueChange = { maxMb = it }, singleLine = true,
                    label = { Text("Kích thước tối đa (MB)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = days, onValueChange = { days = it }, singleLine = true,
                    label = { Text("Sửa trong vòng (ngày)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    FileFilter(
                        exts = exts.split(',').map { it.trim().lowercase().removePrefix(".") }
                            .filter { it.isNotEmpty() }.toSet(),
                        minBytes = ((minMb.toDoubleOrNull() ?: 0.0) * 1_000_000).toLong(),
                        maxBytes = maxMb.toDoubleOrNull()?.let { (it * 1_000_000).toLong() } ?: Long.MAX_VALUE,
                        modifiedWithinDays = days.toIntOrNull() ?: 0
                    )
                )
            }) { Text("Áp dụng") }
        },
        dismissButton = { TextButton(onClick = { onApply(FileFilter()) }) { Text("Xoá bộ lọc") } }
    )
}
