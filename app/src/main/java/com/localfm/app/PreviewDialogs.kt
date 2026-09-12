package com.localfm.app

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

fun isImage(file: File) =
    file.extension.lowercase() in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic")

fun isMedia(file: File) =
    file.extension.lowercase() in setOf("mp4", "mkv", "webm", "3gp", "mp3", "wav", "aac", "ogg", "m4a", "flac")

fun isText(file: File) =
    file.extension.lowercase() in setOf("txt", "md", "log", "json", "xml", "csv", "kt", "java")

fun isPdf(file: File) = file.extension.lowercase() == "pdf"

@Composable
fun ImagePreviewDialog(file: File, onDismiss: () -> Unit) {
    val bmp = remember(file.absolutePath) {
        try {
            BitmapFactory.Options().run {
                inJustDecodeBounds = true
                BitmapFactory.decodeFile(file.absolutePath, this)
                inSampleSize = (outWidth / 1080).coerceAtLeast(1)
                inJustDecodeBounds = false
                BitmapFactory.decodeFile(file.absolutePath, this)
            }
        } catch (_: Exception) { null }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name) },
        text = {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = file.name,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentScale = ContentScale.Fit
                )
            } else Text("Không mở được ảnh")
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

@Composable
fun MediaPreviewDialog(file: File, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name) },
        text = {
            AndroidView(
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(file.absolutePath)
                        setOnPreparedListener { it.isLooping = false; start() }
                    }
                },
                onRelease = { it.stopPlayback() }
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

@Composable
fun TextPreviewDialog(file: File, onDismiss: () -> Unit) {
    val content = remember(file.absolutePath) {
        try { file.readText().take(20_000) } catch (_: Exception) { "Không đọc được tệp" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name) },
        text = {
            Text(
                content,
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

@Composable
fun PdfPreviewDialog(file: File, onDismiss: () -> Unit) {
    val page = remember(file.absolutePath) {
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pg = renderer.openPage(0)
            val bmp = android.graphics.Bitmap.createBitmap(
                pg.width.coerceAtMost(1080),
                pg.height.coerceAtMost(1600),
                android.graphics.Bitmap.Config.ARGB_8888
            )
            pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pg.close(); renderer.close(); pfd.close()
            bmp
        } catch (_: Exception) { null }
    }
    DisposableEffect(file.absolutePath) { onDispose { } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name + " (trang 1)") },
        text = {
            if (page != null) {
                Image(
                    bitmap = page.asImageBitmap(),
                    contentDescription = file.name,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentScale = ContentScale.Fit
                )
            } else Text("Không mở được PDF")
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}
