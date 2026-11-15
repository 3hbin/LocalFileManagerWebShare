package com.localfm.app

import androidx.compose.foundation.horizontalScroll
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@Composable
fun CodeEditorDialog(file: File, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember(file.absolutePath) {
        mutableStateOf(
            try { file.readText().take(200_000) } catch (_: Exception) { "Không đọc được tệp" }
        )
    }
    var msg by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sửa ${file.name}") },
        text = {
            Column {
                if (msg.isNotBlank()) Text(msg)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    visualTransformation = CodeColorTransform(file.extension.lowercase())
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    if (ReadOnlyLocks.isLocked(file)) {
                        android.widget.Toast.makeText(ctx, "Tệp đang khoá chỉ đọc", android.widget.Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    file.writeText(text)
                    msg = "Đã lưu"
                    Toast.makeText(ctx, "Đã lưu ${file.name}", Toast.LENGTH_SHORT).show()
                    onDismiss()
                } catch (_: Exception) {
                    msg = "Không ghi được tệp"
                }
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlPreviewDialog(file: File, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    val url = remember(file.absolutePath) { "file://${file.absolutePath}" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name) },
        text = {
            Column {
                Row {
                    TextButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("link", url))
                        Toast.makeText(ctx, "Đã copy $url", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy link") }
                    TextButton(onClick = { web?.reload() }) { Text("Tải lại") }
                    TextButton(onClick = onEdit) { Text("Sửa mã") }
                }
                AndroidView(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 420.dp),
                    factory = { c ->
                        WebView(c).apply {
                            settings.javaScriptEnabled = true
                            settings.allowFileAccess = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            loadUrl(url)
                            web = this
                        }
                    },
                    update = { if (it.url != url) it.loadUrl(url) }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

fun findBuiltApks(dir: File): List<File> {
    val out = mutableListOf<File>()
    fun walk(f: File, depth: Int) {
        if (depth > 4) return
        f.listFiles()?.forEach { child ->
            if (child.isFile && child.extension.lowercase() == "apk") out += child
            else if (child.isDirectory && child.name in setOf("build", "outputs", "apk", "debug", "release", "app"))
                walk(child, depth + 1)
        }
    }
    walk(dir, 0)
    return out.sortedByDescending { it.lastModified() }.take(20)
}

@Composable
fun MakeQrDialog(initial: String, saveDir: File, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var content by remember { mutableStateOf(initial) }
    val bmp = remember(content) { QrCodeUtils.makeBitmap(content.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo mã QR") },
        text = {
            Column {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Chữ / link / đường dẫn") },
                    singleLine = false
                )
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "QR",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
                    )
                } else Text("Nhập nội dung để tạo QR")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val img = bmp
                if (img == null) {
                    Toast.makeText(ctx, "Chưa có mã QR", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                try {
                    val out = File(saveDir, "qr_${System.currentTimeMillis()}.png")
                    out.outputStream().use { img.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    Toast.makeText(ctx, "Đã lưu ${out.name}", Toast.LENGTH_SHORT).show()
                    onDismiss()
                } catch (_: Exception) {
                    Toast.makeText(ctx, "Không lưu được", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Lưu PNG") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

fun openHtmlInBrowser(context: Context, file: File) {
    val uri = try {
        androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
    } catch (_: Exception) {
        android.net.Uri.fromFile(file)
    }
    val view = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "text/html")
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val launched = listOf("com.android.chrome", "com.google.android.apps.chrome", "com.chrome.beta")
        .any { pkg ->
            try {
                context.startActivity(android.content.Intent(view).setPackage(pkg))
                true
            } catch (_: Exception) { false }
        }
    if (!launched) {
        try {
            context.startActivity(android.content.Intent.createChooser(view, "Mở HTML"))
        } catch (_: Exception) {
            Toast.makeText(context, "Không mở được trình duyệt", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun CreateFileDialog(dir: File, onDismiss: () -> Unit, onCreated: () -> Unit) {
    val ctx = LocalContext.current
    val exts = listOf("txt","html","css","js","py","java","json","xml","md","php","sh","zip")
    var name by remember { mutableStateOf("tep_moi") }
    var ext by remember { mutableStateOf("txt") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo tệp") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.filter { ch -> ch != '/' && ch != '\\' } },
                    label = { Text("Tên tệp") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                NameTemplates.init(ctx)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    NameTemplates.items.forEach { t ->
                        TextButton(onClick = { name = NameTemplates.render(t) }) { Text(t, maxLines = 1) }
                    }
                }
                Text("Đuôi: .$ext")
                Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                    exts.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { e ->
                                TextButton(onClick = { ext = e }) { Text(if (ext == e) "[$e]" else e) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val base = name.trim().ifBlank { "tep_moi" }
                val f = File(dir, "$base.$ext")
                if (f.exists()) {
                    Toast.makeText(ctx, "Đã có ${f.name}", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                val ok = try {
                    if (ext == "zip") ZipUtils.emptyZip(f)
                    else {
                        f.writeText(
                            if (ext == "html")
                                "<!DOCTYPE html><html><body><h1>$base</h1></body></html>\n"
                            else ""
                        )
                        true
                    }
                } catch (_: Exception) { false }
                Toast.makeText(ctx, if (ok) "Đã tạo ${f.name}" else "Lỗi tạo tệp", Toast.LENGTH_SHORT).show()
                if (ok) onCreated() else onDismiss()
            }) { Text("Tạo") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

private val HIDDEN_IN_ZIP = setOf("png","jpg","jpeg","gif","webp","bmp","heic","svg")

@Composable
fun ZipPeekDialog(file: File, onExtract: (String?) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var password by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(ZipUtils.listEntries(file)) }
    val encrypted = remember(file.absolutePath) { ZipUtils.isEncrypted(file) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trong ${file.name}") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (encrypted) {
                    Text("ZIP có mật khẩu")
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Mật khẩu") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = {
                        items = ZipUtils.listEntries(file, password)
                    }) { Text("Mở khóa") }
                }
                if (items.isEmpty()) {
                    Text(if (encrypted) "Nhập mật khẩu để xem danh sách (ảnh vẫn ẩn)" else "ZIP trống hoặc không đọc được")
                }
                items.forEach { item ->
                    val ext = item.name.substringAfterLast('.', "").lowercase()
                    val hidden = ext in HIDDEN_IN_ZIP
                    Text(
                        if (item.isDir) "[Thư mục] ${item.name}"
                        else if (hidden) "${item.name}  — ảnh đã ẩn"
                        else "${item.name}  (${item.size} B)"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onExtract(password.ifBlank { null }) }) { Text("Giải nén") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

@Composable
fun DriveRestoreDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(listOf<DriveSync.Remote>()) }
    var local by remember { mutableStateOf(listOf<java.io.File>()) }
    var msg by remember { mutableStateOf("Đang lấy danh sách…") }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        Thread {
            val acc = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(ctx)
            val list = if (acc != null) DriveSync.listBackups(ctx) else emptyList()
            val loc = BackupKit.listLocal()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                items = list
                local = loc
                msg = if (acc == null)
                    "Mới lưu email trên máy — chưa có phiên Google nên Drive trống. Dùng bản trong Download."
                else if (list.isEmpty())
                    "Đã đăng nhập Google nhưng chưa có file trên Drive. Bấm Sao lưu cấu hình trước."
                else "Chọn máy trên Drive"
            }
        }.start()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Khôi phục sao lưu") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(msg)
                items.forEach { r ->
                    TextButton(onClick = {
                        Thread {
                            val out = DriveSync.restore(ctx, r.id)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                Toast.makeText(ctx, out, Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        }.start()
                    }) { Text("Drive: ${r.name}") }
                }
                if (local.isNotEmpty()) Text("Trên máy (Download/LocalFM_Backup)")
                local.forEach { f ->
                    TextButton(onClick = {
                        val ok = BackupKit.importJson(ctx, f.readText())
                        Toast.makeText(ctx, if (ok) "Đã khôi phục ${f.parentFile?.name}" else "Lỗi file", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }) { Text(f.parentFile?.name ?: f.name) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

@Composable
fun ManualEmailDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var mail by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Email Google") },
        text = {
            OutlinedTextField(
                value = mail,
                onValueChange = { mail = it },
                label = { Text("Gmail") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(mail.trim()) }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

@Composable
fun TrashSwipeDialog(
    items: List<File>,
    reload: () -> List<File>,
    onRestore: (File) -> Boolean,
    onDeleteForever: (File) -> Boolean,
    onDismiss: () -> Unit
) {
    var list by remember { mutableStateOf(items) }
    var pendingForever by remember { mutableStateOf<File?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thùng rác") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Kéo phải khôi phục · kéo trái xóa hẳn")
                if (list.isEmpty()) Text("Trống")
                list.forEach { f -> androidx.compose.runtime.key(f.absolutePath) {
                    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            when (value) {
                                androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> {
                                    if (onRestore(f)) { list = reload(); true } else false
                                }
                                androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> {
                                    // Xoá hẳn phải xác nhận; hộp xác nhận bên dưới quyết định
                                    pendingForever = f
                                    false
                                }
                                else -> false
                            }
                        }
                    )
                    androidx.compose.material3.SwipeToDismissBox(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        state = state,
                        backgroundContent = {
                            val end = state.dismissDirection ==
                                androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
                            androidx.compose.foundation.layout.Box(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .background(
                                        if (end) androidx.compose.ui.graphics.Color(0xFF8B2E2E)
                                        else androidx.compose.ui.graphics.Color(0xFF1B5E3B)
                                    ),
                                contentAlignment = if (end) androidx.compose.ui.Alignment.CenterEnd
                                else androidx.compose.ui.Alignment.CenterStart
                            ) {
                                Text(
                                    if (end) "  Xóa hẳn  " else "  Khôi phục  ",
                                    color = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                f.name.substringAfter("_"),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )

    pendingForever?.let { f ->
        AlertDialog(
            onDismissRequest = { pendingForever = null },
            title = { Text("Xoá hẳn?") },
            text = { Text("\"${f.name.substringAfter("_")}\" sẽ bị xoá vĩnh viễn, không khôi phục được.") },
            confirmButton = {
                TextButton(onClick = {
                    if (onDeleteForever(f)) list = reload()
                    pendingForever = null
                }) { Text("Xoá hẳn") }
            },
            dismissButton = { TextButton(onClick = { pendingForever = null }) { Text("Huỷ") } }
        )
    }
}


@Composable
fun RootSettingsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(RootAccess.mode(ctx)) }
    var sys by remember { mutableStateOf(RootAccess.systemFiles(ctx)) }
    var selinux by remember { mutableStateOf(RootAccess.selinuxFix(ctx)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ROOT") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Loại quyền truy cập root")
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = mode == RootAccess.SU, onClick = {
                        mode = RootAccess.SU
                        RootAccess.setMode(ctx, RootAccess.SU)
                    })
                    Text("SU")
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = mode == RootAccess.SHIZUKU, onClick = {
                        mode = RootAccess.SHIZUKU
                        RootAccess.setMode(ctx, RootAccess.SHIZUKU)
                    })
                    Text("Shizuku / SUI")
                }
                if (mode == RootAccess.SHIZUKU && !RootAccess.shizukuInstalled(ctx)) {
                    Text("Chưa cài Shizuku. Có thể dùng SU nếu máy đã root.")
                }
                if (mode == RootAccess.SHIZUKU && RootAccess.shizukuInstalled(ctx)) {
                    val alive = RootAccess.shizukuAlive()
                    val granted = RootAccess.shizukuGranted()
                    Text(
                        when {
                            granted -> "Shizuku: đã cấp quyền. App sẽ hiện trong Quản lý ứng dụng."
                            alive -> "Shizuku đang chạy. Bấm nút dưới để xin quyền."
                            else -> "Mở app Shizuku và bật dịch vụ trước."
                        }
                    )
                    TextButton(onClick = {
                        val msg = RootAccess.requestShizuku()
                        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
                    }) { Text("Xin quyền Shizuku") }
                }
                Text("Tệp tin HĐH")
                Text("Cần SU hoặc Shizuku để vào /system. Android/data trên Android 11+ không mở bằng quyền mọi tệp. Dùng menu Mở Android/data (SAF) hoặc Shizuku. Root chỉ cần khi SAF bị chặn, thường Android 13+.", style = MaterialTheme.typography.bodySmall)
                Switch(checked = sys, onCheckedChange = {
                    sys = it
                    RootAccess.setSystemFiles(ctx, it)
                })
                Text("Sửa ngữ cảnh SELinux")
                Text("Khôi phục ngữ cảnh SELinux mặc định sau khi sao chép đến /data, /system hoặc /sys", style = MaterialTheme.typography.bodySmall)
                Switch(checked = selinux, onCheckedChange = {
                    selinux = it
                    RootAccess.setSelinuxFix(ctx, it)
                })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}
