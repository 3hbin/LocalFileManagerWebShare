package com.localfm.app

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import net.lingala.zip4j.ZipFile as Zip4jFile

enum class ZipKind { EXTRACT, TEST, COMPRESS }

data class ZipJob(val kind: ZipKind, val files: List<File>, val password: String? = null)

/** Cờ huỷ cho tác vụ nền. */
class JobControl {
    @Volatile var cancelled = false
}

/** Đếm byte đã đọc từ file ZIP để tính tiến trình chính xác. */
private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    var count = 0L
    override fun read(): Int {
        val r = super.read()
        if (r >= 0) count++
        return r
    }
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val r = super.read(b, off, len)
        if (r > 0) count += r
        return r
    }
}

object ZipJobs {
    class Result(val ok: Boolean, val message: String)

    private const val BUF = 64 * 1024

    private fun uniqueDir(parent: File, base: String): File {
        var d = File(parent, base)
        var i = 2
        while (d.exists()) { d = File(parent, "$base ($i)"); i++ }
        return d
    }

    private fun uniqueFile(parent: File, base: String, ext: String): File {
        var f = File(parent, "$base.$ext")
        var i = 2
        while (f.exists()) { f = File(parent, "$base ($i).$ext"); i++ }
        return f
    }

    /** Chặn zip-slip: đường dẫn trong ZIP phải nằm trong thư mục đích. */
    private fun inside(dest: File, name: String): File? {
        val root = dest.canonicalFile
        val target = File(root, name).canonicalFile
        return if (target.path == root.path || target.path.startsWith(root.path + File.separator)) target else null
    }

    private fun looksLikeZip(f: File): Boolean = try {
        FileInputStream(f).use { ins ->
            val h = ByteArray(4)
            ins.read(h) == 4 && h[0] == 'P'.code.toByte() && h[1] == 'K'.code.toByte() &&
                (h[2].toInt() == 3 || h[2].toInt() == 5)
        }
    } catch (_: Exception) { false }

    fun extract(zip: File, password: String?, report: (Float, String) -> Unit, ctl: JobControl): Result {
        if (!zip.exists()) return Result(false, "Không tìm thấy tệp")
        val lower = zip.name.lowercase()
        if (lower.endsWith(".rar") || lower.endsWith(".7z")) return Result(false, "Chưa hỗ trợ RAR/7z — hãy dùng ZIP")
        val parent = zip.parentFile ?: return Result(false, "Thư mục không hợp lệ")
        val dest = uniqueDir(parent, zip.nameWithoutExtension)
        val encrypted = ZipUtils.isEncrypted(zip)
        var count = 0
        return try {
            dest.mkdirs()
            if (encrypted) {
                if (password.isNullOrBlank()) return Result(false, "Cần mật khẩu")
                val z = Zip4jFile(zip, password.toCharArray())
                val headers = z.fileHeaders
                val total = headers.sumOf { it.uncompressedSize }.coerceAtLeast(1L)
                var done = 0L
                for (h in headers) {
                    if (ctl.cancelled) break
                    val name = h.fileName ?: continue
                    if (inside(dest, name) == null) continue
                    if (!h.isDirectory) z.extractFile(h, dest.absolutePath)
                    else inside(dest, name)?.mkdirs()
                    done += h.uncompressedSize
                    count++
                    report((done.toFloat() / total).coerceAtMost(1f), name)
                }
            } else {
                val zipLen = zip.length().coerceAtLeast(1L)
                val counter = CountingInputStream(FileInputStream(zip))
                ZipInputStream(BufferedInputStream(counter)).use { zis ->
                    val buf = ByteArray(BUF)
                    var e = zis.nextEntry
                    while (e != null && !ctl.cancelled) {
                        val target = inside(dest, e.name)
                        if (target == null) {
                            zis.closeEntry(); e = zis.nextEntry; continue
                        }
                        if (e.isDirectory) target.mkdirs()
                        else {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { out ->
                                while (true) {
                                    val n = zis.read(buf)
                                    if (n <= 0) break
                                    out.write(buf, 0, n)
                                    if (ctl.cancelled) break
                                    report((counter.count.toFloat() / zipLen).coerceAtMost(1f), e!!.name)
                                }
                            }
                            count++
                        }
                        zis.closeEntry()
                        e = zis.nextEntry
                    }
                }
            }
            if (ctl.cancelled) {
                dest.deleteRecursively()
                Result(false, "Đã huỷ, đã xoá phần giải nén dở")
            } else Result(true, "Đã giải nén $count tệp vào ./${dest.name}/")
        } catch (e: Exception) {
            dest.deleteRecursively()
            val m = e.message.orEmpty()
            if (encrypted && (m.contains("password", true) || m.contains("encrypted", true) || m.contains("wrong", true)))
                Result(false, "Sai mật khẩu")
            else Result(false, if (m.isBlank()) "Giải nén thất bại" else "Giải nén lỗi: $m")
        }
    }

    fun test(zip: File, password: String?, report: (Float, String) -> Unit, ctl: JobControl): Result {
        if (!zip.exists()) return Result(false, "Không tìm thấy tệp")
        if (!looksLikeZip(zip)) return Result(false, "Không phải tệp ZIP")
        return try {
            if (ZipUtils.isEncrypted(zip)) {
                if (password.isNullOrBlank()) return Result(false, "ZIP có mật khẩu — nhập mật khẩu để kiểm tra")
                val z = Zip4jFile(zip, password.toCharArray())
                val headers = z.fileHeaders.filter { !it.isDirectory }
                val total = headers.sumOf { it.uncompressedSize }.coerceAtLeast(1L)
                var done = 0L
                for (h in headers) {
                    if (ctl.cancelled) return Result(false, "Đã huỷ")
                    z.getInputStream(h).use { ins ->
                        val buf = ByteArray(BUF)
                        while (true) {
                            val n = ins.read(buf)
                            if (n <= 0) break
                            done += n
                        }
                    }
                    report((done.toFloat() / total).coerceAtMost(1f), h.fileName.orEmpty())
                }
                return Result(true, "ZIP mã hoá: ${headers.size} tệp đọc được với mật khẩu này (không kiểm CRC)")
            }
            val zipLen = zip.length().coerceAtLeast(1L)
            val counter = CountingInputStream(FileInputStream(zip))
            var count = 0
            ZipInputStream(BufferedInputStream(counter)).use { zis ->
                val buf = ByteArray(BUF)
                var e = zis.nextEntry
                while (e != null) {
                    if (ctl.cancelled) return Result(false, "Đã huỷ")
                    if (!e.isDirectory) {
                        // ZipInputStream kiểm CRC khi đọc hết từng tệp
                        while (zis.read(buf) > 0) { /* đọc hết */ }
                        count++
                    }
                    report((counter.count.toFloat() / zipLen).coerceAtMost(1f), e.name)
                    zis.closeEntry()
                    e = zis.nextEntry
                }
            }
            Result(true, "ZIP hợp lệ: $count tệp, CRC đúng")
        } catch (e: Exception) {
            Result(false, "ZIP lỗi: ${e.message ?: "không đọc được"}")
        }
    }

    private fun compressEncrypted(
        sources: List<File>,
        dest: File,
        password: String,
        report: (Float, String) -> Unit,
        ctl: JobControl
    ): Result {
        return try {
            val zp = Zip4jFile(dest, password.toCharArray())
            val params = net.lingala.zip4j.model.ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
                aesKeyStrength = net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256
            }
            val total = sources.size.coerceAtLeast(1)
            for ((i, f) in sources.withIndex()) {
                if (ctl.cancelled) break
                report(i.toFloat() / total, f.name)
                if (f.isDirectory) zp.addFolder(f, params) else zp.addFile(f, params)
            }
            if (ctl.cancelled) {
                dest.delete()
                Result(false, "Đã huỷ, đã xoá file nén dở")
            } else Result(true, "Đã nén ${sources.size} mục thành ${dest.name} (mã hoá AES-256)")
        } catch (e: Exception) {
            dest.delete()
            Result(false, "Nén mã hoá lỗi: ${e.message ?: "không xác định"}")
        }
    }

    fun compress(sources: List<File>, report: (Float, String) -> Unit, ctl: JobControl, password: String? = null): Result {
        val parent = sources.firstOrNull()?.parentFile ?: return Result(false, "Không có mục để nén")
        val base = if (sources.size == 1) {
            val s = sources[0]
            if (s.isDirectory) s.name else s.nameWithoutExtension
        } else "luu_tru_${System.currentTimeMillis()}"
        val dest = uniqueFile(parent, base, "zip")
        if (!password.isNullOrBlank()) return compressEncrypted(sources, dest, password, report, ctl)

        val entries = mutableListOf<Pair<String, File>>()
        fun collect(f: File, name: String) {
            if (f.isDirectory) {
                val kids = f.listFiles().orEmpty()
                if (kids.isEmpty()) entries += ("$name/" to f)
                else kids.forEach { collect(it, "$name/${it.name}") }
            } else entries += (name to f)
        }
        sources.forEach { collect(it, it.name) }
        val total = entries.filter { !it.second.isDirectory }.sumOf { it.second.length() }.coerceAtLeast(1L)

        return try {
            var done = 0L
            BufferedOutputStream(FileOutputStream(dest)).let { bos ->
                ZipOutputStream(bos).use { zos ->
                    val buf = ByteArray(BUF)
                    for ((name, f) in entries) {
                        if (ctl.cancelled) break
                        zos.putNextEntry(ZipEntry(name))
                        if (!f.isDirectory) {
                            FileInputStream(f).use { ins ->
                                while (true) {
                                    val n = ins.read(buf)
                                    if (n <= 0) break
                                    zos.write(buf, 0, n)
                                    done += n
                                    if (ctl.cancelled) break
                                    report((done.toFloat() / total).coerceAtMost(1f), name)
                                }
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }
            if (ctl.cancelled) {
                dest.delete()
                Result(false, "Đã huỷ, đã xoá file nén dở")
            } else Result(true, "Đã nén ${entries.size} mục thành ${dest.name}")
        } catch (e: Exception) {
            dest.delete()
            Result(false, "Nén lỗi: ${e.message ?: "không xác định"}")
        }
    }
}

/** Dialog giải nén / kiểm tra / nén, có thanh tiến trình M3 khi đang chạy. */
@Composable
fun ZipJobDialog(job: ZipJob, onFinished: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var phase by remember { mutableStateOf(0) } // 0 chờ, 1 đang chạy, 2 xong
    var progress by remember { mutableStateOf(0f) }
    var current by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ZipJobs.Result?>(null) }
    var password by remember { mutableStateOf(job.password.orEmpty()) }
    var ctl by remember { mutableStateOf(JobControl()) }
    val encrypted = remember(job) {
        job.kind != ZipKind.COMPRESS && job.files.firstOrNull()?.let { ZipUtils.isEncrypted(it) } == true
    }

    val title = when (job.kind) {
        ZipKind.EXTRACT -> "Giải nén"
        ZipKind.TEST -> "Kiểm tra ZIP"
        ZipKind.COMPRESS -> "Nén"
    }
    val actionLabel = when (job.kind) {
        ZipKind.EXTRACT -> "Giải nén"
        ZipKind.TEST -> "Kiểm tra"
        ZipKind.COMPRESS -> "Nén"
    }
    val description = when (job.kind) {
        ZipKind.EXTRACT -> "Giải nén vào thư mục ./${job.files.firstOrNull()?.nameWithoutExtension.orEmpty()}/ ngay tại đây."
        ZipKind.TEST -> "Kiểm tra tính toàn vẹn (CRC) của từng tệp trong ZIP."
        ZipKind.COMPRESS -> "Nén ${job.files.size} mục thành một file ZIP trong thư mục hiện tại. Để trống mật khẩu nếu không cần mã hoá."
    }

    fun start() {
        val c = JobControl()
        ctl = c
        phase = 1
        progress = 0f
        result = null
        val task = TaskCenter.start(title)
        task.progress = 0f
        task.onCancel = { c.cancelled = true }
        scope.launch {
            val lastPost = AtomicLong(0L)
            val report: (Float, String) -> Unit = { p, name ->
                val now = System.currentTimeMillis()
                if (now - lastPost.get() > 80) {
                    lastPost.set(now)
                    scope.launch { progress = p; current = name; task.progress = p; task.detail = name }
                }
            }
            val r = withContext(Dispatchers.IO) {
                when (job.kind) {
                    ZipKind.EXTRACT -> ZipJobs.extract(job.files[0], password.ifBlank { null }, report, c)
                    ZipKind.TEST -> ZipJobs.test(job.files[0], password.ifBlank { null }, report, c)
                    ZipKind.COMPRESS -> ZipJobs.compress(job.files, report, c, password.ifBlank { null })
                }
            }
            progress = if (r.ok) 1f else progress
            result = r
            phase = 2
            TaskCenter.finish(task)
            OpLog.add("${title}: ${r.message}")
            onFinished()
        }
    }

    AlertDialog(
        onDismissRequest = { if (phase != 1) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(description, style = MaterialTheme.typography.bodyMedium)
                if (phase == 0 && (job.kind == ZipKind.COMPRESS || (job.kind == ZipKind.EXTRACT && encrypted))) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (job.kind == ZipKind.COMPRESS) "Mật khẩu (tuỳ chọn, mã hoá AES-256)" else "Mật khẩu ZIP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (phase == 1) {
                    Spacer(Modifier.height(12.dp))
                    ExpressiveLinearProgress(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${(progress * 100).toInt()}%  ·  $current",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
                if (phase == 2) {
                    Spacer(Modifier.height(12.dp))
                    Text(result?.message.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            when (phase) {
                0 -> TextButton(
                    onClick = { start() },
                    enabled = !(encrypted && job.kind == ZipKind.EXTRACT && password.isBlank())
                ) { Text(actionLabel) }
                1 -> TextButton(onClick = { ctl.cancelled = true }) { Text("Huỷ") }
                else -> TextButton(onClick = {
                    if (result?.ok == true) Toast.makeText(ctx, result?.message, Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) { Text("Đóng") }
            }
        },
        dismissButton = {
            if (phase == 0) TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

object DuplicateFinder {
    data class Group(val size: Long, val files: List<File>)

    /** Hash SHA-256 của tối đa [limit] byte đầu. Trả về null nếu không đọc được. */
    private fun hash(f: File, limit: Long): String? = try {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(BUF)
            var left = limit
            while (left > 0) {
                val n = ins.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n <= 0) break
                md.update(buf, 0, n)
                left -= n
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { null }

    private const val BUF = 64 * 1024
    private const val HEAD = 64L * 1024
    private const val MAX_FILES = 200_000

    /**
     * Quét đệ quy [root]. Bước 1: gom file theo kích thước (chỉ nhóm có từ 2 file).
     * Bước 2: hash 64 KB đầu để lọc nhanh. Bước 3: hash toàn bộ file còn trùng.
     */
    fun scan(root: File, ctl: JobControl, progress: (String) -> Unit): List<Group> {
        val bySize = HashMap<Long, MutableList<File>>()
        var seen = 0
        fun walk(dir: File, depth: Int) {
            if (depth > 16 || ctl.cancelled || seen > MAX_FILES) return
            val kids = dir.listFiles() ?: return
            for (k in kids) {
                if (ctl.cancelled) return
                if (k.name.startsWith(".")) continue
                if (isAndroidRestrictedPath(k.absolutePath)) continue
                if (k.isDirectory) walk(k, depth + 1)
                else if (k.isFile && k.length() > 0) {
                    bySize.getOrPut(k.length()) { mutableListOf() }.add(k)
                    seen++
                }
            }
        }
        progress("Đang quét thư mục…")
        walk(root, 0)

        val candidates = bySize.filter { it.value.size > 1 }
        val total = candidates.values.sumOf { it.size }.coerceAtLeast(1)
        var checked = 0
        val groups = mutableListOf<Group>()
        for ((size, files) in candidates) {
            if (ctl.cancelled) break
            val byHead = files.groupBy { hash(it, HEAD) ?: "" }
            for ((head, sub) in byHead) {
                if (head.isEmpty() || sub.size < 2) continue
                val byFull = HashMap<String, MutableList<File>>()
                for (f in sub) {
                    if (ctl.cancelled) break
                    checked++
                    progress("Đang so khớp $checked / $total")
                    val h = hash(f, Long.MAX_VALUE) ?: continue
                    byFull.getOrPut(h) { mutableListOf() }.add(f)
                }
                for ((_, same) in byFull) {
                    if (same.size > 1) {
                        val sorted = same.sortedWith(compareBy({ it.absolutePath.length }, { it.absolutePath }))
                        groups += Group(size, sorted)
                    }
                }
            }
        }
        return groups.sortedByDescending { it.size * (it.files.size - 1) }
    }
}

/** Dialog tìm file trùng nội dung trong thư mục hiện tại. Giữ 1 bản mỗi nhóm, các bản còn lại vào thùng rác. */
@Composable
fun DuplicateDialog(root: File, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var phase by remember { mutableStateOf(0) } // 0 chờ, 1 đang quét, 2 có kết quả
    var status by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf<List<DuplicateFinder.Group>>(emptyList()) }
    var ctl by remember { mutableStateOf(JobControl()) }
    val relRoot = root.absolutePath.trimEnd('/') + "/"

    fun startScan() {
        val c = JobControl()
        ctl = c
        phase = 1
        val task = TaskCenter.start("Quét file trùng: ${root.name}")
        task.onCancel = { c.cancelled = true }
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                DuplicateFinder.scan(root, c) { s -> scope.launch { status = s; task.detail = s } }
            }
            TaskCenter.finish(task)
            groups = res
            phase = 2
            status = if (res.isEmpty()) "Không có file trùng trong ${root.name}" else "Tìm thấy ${res.size} nhóm file trùng"
        }
    }

    fun cleanup() {
        scope.launch {
            val moved = withContext(Dispatchers.IO) {
                var n = 0
                for (g in groups) for (f in g.files.drop(1)) if (TrashBin.moveToTrash(ctx, f)) n++
                n
            }
            groups = emptyList()
            status = "Đã chuyển $moved bản trùng vào thùng rác"
            onChanged()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (phase == 1) ctl.cancelled = true
            onDismiss()
        },
        title = { Text("Tìm file trùng") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Thư mục: ${root.absolutePath}", style = MaterialTheme.typography.bodySmall)
                if (phase == 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("So khớp theo nội dung (SHA-256), không chỉ theo tên.", style = MaterialTheme.typography.bodyMedium)
                }
                if (phase == 1) {
                    Spacer(Modifier.height(8.dp))
                    ExpressiveLinearProgress(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
                if (phase == 2) {
                    Spacer(Modifier.height(8.dp))
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                    groups.take(100).forEach { g ->
                        Spacer(Modifier.height(10.dp))
                        Text("${g.files.size} tệp · ${formatBytesDup(g.size)} mỗi tệp", style = MaterialTheme.typography.labelLarge)
                        g.files.forEachIndexed { i, f ->
                            val rel = f.absolutePath.removePrefix(relRoot)
                            Text(
                                (if (i == 0) "Giữ: " else "Trùng: ") + rel,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2
                            )
                        }
                    }
                    if (groups.size > 100) Text("…và ${groups.size - 100} nhóm nữa (dọn bớt để xem tiếp)")
                }
            }
        },
        confirmButton = {
            when (phase) {
                0 -> TextButton(onClick = { startScan() }) { Text("Quét") }
                1 -> TextButton(onClick = { ctl.cancelled = true }) { Text("Huỷ") }
                else -> if (groups.isNotEmpty()) TextButton(onClick = { cleanup() }) {
                    Text("Giữ 1 bản, dọn phần còn lại")
                } else TextButton(onClick = onDismiss) { Text("Đóng") }
            }
        },
        dismissButton = {
            if (phase == 2 && groups.isNotEmpty()) TextButton(onClick = onDismiss) { Text("Đóng") }
        }
    )
}

private fun formatBytesDup(b: Long): String = when {
    b >= 1000L * 1000 * 1000 -> "%.1f GB".format(b / 1000.0 / 1000 / 1000)
    b >= 1000L * 1000 -> "%.1f MB".format(b / 1000.0 / 1000)
    b >= 1000L -> "%.1f KB".format(b / 1000.0)
    else -> "$b B"
}
