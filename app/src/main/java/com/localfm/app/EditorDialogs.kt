package com.localfm.app

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
