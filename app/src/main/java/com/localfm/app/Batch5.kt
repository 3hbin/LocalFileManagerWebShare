package com.localfm.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ---------- 19. Link chia sẻ có thời hạn ---------- */

@Composable
fun TempLinkDialog(file: File, baseUrl: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val server = ServerBridge.server
    var minutes by remember { mutableIntStateOf(60) }
    var oneTime by remember { mutableStateOf(true) }
    var link by remember { mutableStateOf<String?>(null) }
    val qr = remember(link) { link?.let { QrCodeUtils.makeBitmap(it) } }
    val label = when (minutes) {
        15 -> "15 phút"
        60 -> "1 giờ"
        else -> "24 giờ"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link tạm: ${file.name}") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                val current = link
                when {
                    server == null -> Text("Web Share chưa bật. Hãy bật Web Share rồi tạo lại link.")
                    current == null -> {
                        Text("Thời hạn", style = MaterialTheme.typography.labelLarge)
                        Row {
                            listOf(15, 60, 1440).forEach { m ->
                                TextButton(onClick = { minutes = m }, enabled = minutes != m) {
                                    Text(if (m == 1440) "24 giờ" else if (m == 60) "1 giờ" else "15 phút")
                                }
                            }
                        }
                        Text("Số lần tải", style = MaterialTheme.typography.labelLarge)
                        Row {
                            TextButton(onClick = { oneTime = true }, enabled = !oneTime) { Text("1 lần") }
                            TextButton(onClick = { oneTime = false }, enabled = oneTime) { Text("Nhiều lần") }
                        }
                    }
                    else -> {
                        SelectionContainer { Text(current, style = MaterialTheme.typography.bodySmall) }
                        Spacer(Modifier.height(8.dp))
                        qr?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Mã QR link",
                                modifier = Modifier.size(200.dp)
                            )
                        }
                        Text(
                            "Hết hạn sau $label · ${if (oneTime) "chỉ tải 1 lần" else "tải nhiều lần"}. " +
                                "Link chỉ hoạt động khi Web Share đang bật và máy nhận cùng mạng.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            val current = link
            if (server != null && current == null) {
                TextButton(onClick = {
                    val token = server.createTempLink(file, minutes, oneTime)
                    link = baseUrl.trimEnd('/') + "/s/" + token
                    OpLog.add("Tạo link tạm: ${file.name}")
                }) { Text("Tạo link") }
            } else if (current != null) {
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("link", current))
                    android.widget.Toast.makeText(ctx, "Đã sao chép link", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Sao chép") }
            } else TextButton(onClick = onDismiss) { Text("Đóng") }
        },
        dismissButton = {
            if (link != null || server == null) TextButton(onClick = onDismiss) { Text("Đóng") }
        }
    )
}

/* ---------- 24. Checksum kiểm tra toàn vẹn ---------- */

object ChecksumUtil {
    /** Tính MD5 và SHA-256 trong một lần đọc file. */
    fun both(file: File): Pair<String, String> {
        val md5 = MessageDigest.getInstance("MD5")
        val sha = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { ins ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md5.update(buf, 0, n)
                sha.update(buf, 0, n)
            }
        }
        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
        return hex(md5.digest()) to hex(sha.digest())
    }
}

@Composable
fun ChecksumDialog(file: File, onDismiss: () -> Unit) {
    val result by produceState<Pair<String, String>?>(null, file.absolutePath) {
        value = withContext(Dispatchers.IO) { runCatching { ChecksumUtil.both(file) }.getOrNull() } ?: ("" to "")
    }
    var expected by remember { mutableStateOf("") }
    val md5 = result?.first.orEmpty()
    val sha = result?.second.orEmpty()
    val target = expected.trim().lowercase()
    val match = target.isNotEmpty() && (target == md5 || target == sha)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Checksum: ${file.name}") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                val r = result
                if (r == null) {
                    Text("Đang tính… (file lớn có thể mất một lúc)")
                    ExpressiveLinearProgress(modifier = Modifier.fillMaxWidth())
                } else if (md5.isEmpty()) {
                    Text("Không đọc được tệp.")
                } else {
                    Text("MD5", style = MaterialTheme.typography.labelLarge)
                    SelectionContainer { Text(md5, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(6.dp))
                    Text("SHA-256", style = MaterialTheme.typography.labelLarge)
                    SelectionContainer { Text(sha, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = expected,
                        onValueChange = { expected = it },
                        label = { Text("Dán giá trị gốc để so sánh (tuỳ chọn)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (target.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (match) "Khớp: tệp nguyên vẹn" else "Không khớp: tệp có thể bị hỏng hoặc khác bản gốc",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

/* ---------- 21. Quản lý quyền truy cập thư mục (SAF) ---------- */

@Composable
fun AccessDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val perms = remember(version) { ctx.contentResolver.persistedUriPermissions.toList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quyền truy cập thư mục") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Các thư mục bạn đã cấp cho app qua chọn thư mục (SAF). " +
                        "Thu hồi thì app không còn truy cập được cho đến khi cấp lại.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                if (perms.isEmpty()) Text("Chưa có thư mục nào được cấp qua SAF.")
                perms.forEach { p ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            Uri.decode(p.uri.toString()),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val rights = listOfNotNull(
                            if (p.isReadPermission) "đọc" else null,
                            if (p.isWritePermission) "ghi" else null
                        ).joinToString(" + ")
                        Text(rights, style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = {
                            val flags =
                                (if (p.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                                    (if (p.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
                            try { ctx.contentResolver.releasePersistableUriPermission(p.uri, flags) } catch (_: Exception) {}
                            version++
                        }) { Text("Thu hồi") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    try {
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${ctx.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {}
                }) { Text("Mở quyền truy cập tất cả tệp của app") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

/* ---------- 23. Tìm file tên gần giống (chỉ gợi ý, không xoá) ---------- */

object NameLikeFinder {
    /** Bỏ hậu tố kiểu "(1)", "- copy", " copy", "_copy" ở cuối tên. */
    private val COPY_SUFFIX = Regex("""\s*(\(\d+\)|-\s*copy|\s+copy|_copy)$""", RegexOption.IGNORE_CASE)

    fun scan(root: File): List<List<File>> {
        val files = mutableListOf<File>()
        fun walk(d: File, depth: Int) {
            if (depth > 12 || files.size > 50_000) return
            d.listFiles().orEmpty().forEach { f ->
                if (f.name.startsWith(".")) return@forEach
                if (isAndroidRestrictedPath(f.absolutePath)) return@forEach
                if (f.isDirectory) walk(f, depth + 1) else files.add(f)
            }
        }
        walk(root, 0)
        return files
            .groupBy { f ->
                val base = f.nameWithoutExtension.replace(COPY_SUFFIX, "").trim().lowercase(Locale.ROOT)
                "${f.parentFile?.absolutePath}|$base|${f.extension.lowercase(Locale.ROOT)}"
            }
            .values
            .filter { it.size > 1 }
            .map { g -> g.sortedBy { it.name } }
            .sortedBy { it.first().absolutePath }
    }
}

@Composable
fun NameLikeDialog(root: File, onOpen: (File) -> Unit, onDismiss: () -> Unit) {
    val groups by produceState<List<List<File>>?>(null, root.absolutePath) {
        value = withContext(Dispatchers.IO) { NameLikeFinder.scan(root) }
    }
    val relRoot = root.absolutePath.trimEnd('/') + "/"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tên gần giống") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Gợi ý các tệp có tên giống nhau như \"ảnh.jpg\" và \"ảnh (1).jpg\". Không tự xoá gì.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                val list = groups
                when {
                    list == null -> ExpressiveLinearProgress(modifier = Modifier.fillMaxWidth())
                    list.isEmpty() -> Text("Không có tệp tên gần giống trong thư mục này.")
                    else -> list.take(100).forEach { g ->
                        Text("${g.size} tệp", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                        g.forEach { f ->
                            TextButton(onClick = { onOpen(f) }) {
                                Text(
                                    f.absolutePath.removePrefix(relRoot),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

/* ---------- 26. Mẫu tên file khi tạo mới ---------- */

object NameTemplates {
    private const val P = "local_fm_prefs"
    private const val KEY = "name_templates"
    val items = mutableStateListOf<String>()
    private var loaded = false
    private val TOKEN = Regex("""\{([^{}]+)\}""")

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        val raw = ctx.applicationContext.getSharedPreferences(P, 0).getString(KEY, null)
        if (raw == null) {
            items.addAll(listOf("Ghi_chu_{yyyy-MM-dd}", "anh_{yyyyMMdd_HHmmss}"))
        } else {
            val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
            for (i in 0 until arr.length()) items.add(arr.getString(i))
        }
    }

    private fun save(ctx: Context) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        ctx.applicationContext.getSharedPreferences(P, 0).edit().putString(KEY, arr.toString()).apply()
    }

    fun add(ctx: Context, template: String) {
        init(ctx)
        val t = template.trim()
        if (t.isNotEmpty() && t !in items) {
            items.add(t)
            save(ctx)
        }
    }

    fun remove(ctx: Context, template: String) {
        init(ctx)
        items.remove(template)
        save(ctx)
    }

    /** Thay {định dạng ngày} bằng ngày giờ hiện tại, ví dụ {yyyy-MM-dd}. Định dạng sai thì giữ nguyên. */
    fun render(template: String): String {
        val now = Date()
        return TOKEN.replace(template) { m ->
            try { SimpleDateFormat(m.groupValues[1], Locale.US).format(now) } catch (_: Exception) { m.value }
        }
    }
}

@Composable
fun TemplatesDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    NameTemplates.init(ctx)
    var newOne by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mẫu tên file") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    "Dùng {yyyy-MM-dd}, {HHmmss}... để chèn ngày giờ. Ví dụ: Ghi_chu_{yyyy-MM-dd}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                NameTemplates.items.forEach { t ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "$t  →  ${NameTemplates.render(t)}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(vertical = 12.dp)
                        )
                        TextButton(onClick = { NameTemplates.remove(ctx, t) }) { Text("Xoá") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newOne,
                    onValueChange = { newOne = it },
                    label = { Text("Mẫu mới") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = {
                    NameTemplates.add(ctx, newOne)
                    newOne = ""
                }, enabled = newOne.isNotBlank()) { Text("Thêm mẫu") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}
