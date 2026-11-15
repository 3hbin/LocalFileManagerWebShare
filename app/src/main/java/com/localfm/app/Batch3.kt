package com.localfm.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/* ---------- 8. Khoá thư mục bằng PIN (tách khỏi khoá app) ---------- */

object FolderLock {
    private const val P = "local_fm_prefs"
    private const val KEY = "folder_pins"
    /** path -> SHA-256 của PIN */
    val pinned = mutableStateMapOf<String, String>()
    /** Thư mục đã nhập đúng PIN trong phiên hiện tại */
    private val unlocked = mutableStateListOf<String>()
    private var loaded = false

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        val raw = ctx.applicationContext.getSharedPreferences(P, 0).getString(KEY, "{}")
        val obj = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
        obj.keys().forEach { k -> pinned[k] = obj.optString(k) }
    }

    private fun save(ctx: Context) {
        val obj = JSONObject()
        pinned.forEach { (k, v) -> obj.put(k, v) }
        ctx.applicationContext.getSharedPreferences(P, 0).edit().putString(KEY, obj.toString()).apply()
    }

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun isPinned(folder: File): Boolean = pinned.containsKey(folder.absolutePath)

    fun setPin(ctx: Context, folder: File, pin: String) {
        init(ctx)
        pinned[folder.absolutePath] = sha(pin)
        if (folder.absolutePath !in unlocked) unlocked.add(folder.absolutePath)
        save(ctx)
    }

    fun removePin(ctx: Context, folder: File) {
        init(ctx)
        pinned.remove(folder.absolutePath)
        unlocked.remove(folder.absolutePath)
        save(ctx)
    }

    fun tryUnlock(folder: File, pin: String): Boolean {
        val expected = pinned[folder.absolutePath] ?: return true
        if (sha(pin) != expected) return false
        if (folder.absolutePath !in unlocked) unlocked.add(folder.absolutePath)
        return true
    }

    /** Thư mục PIN đang chặn [dir] (chính nó hoặc một thư mục cha), null nếu được phép vào. */
    fun blockingFolder(dir: File): String? {
        var f: File? = dir
        while (f != null) {
            val p = f.absolutePath
            if (pinned.containsKey(p) && p !in unlocked) return p
            f = f.parentFile
        }
        return null
    }
}

@Composable
fun PinUnlockDialog(folder: File, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Thư mục đã khoá") },
        text = {
            Column {
                Text(folder.name, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(12); wrong = false },
                    label = { Text("Nhập PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = wrong,
                    supportingText = { if (wrong) Text("Sai PIN") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (FolderLock.tryUnlock(folder, pin)) { /* mở khoá, danh sách tự cập nhật */ }
                else wrong = true
            }, enabled = pin.length >= 4) { Text("Mở") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Quay lại") } }
    )
}

@Composable
fun PinSetupDialog(folder: File, onDone: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val same = pin.length >= 4 && pin == again
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Khoá PIN thư mục") },
        text = {
            Column {
                Text("Khoá: ${folder.name}. Cần ít nhất 4 chữ số.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(12) },
                    label = { Text("PIN mới") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = again,
                    onValueChange = { again = it.filter { c -> c.isDigit() }.take(12) },
                    label = { Text("Nhập lại PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = again.isNotEmpty() && again != pin,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                FolderLock.setPin(ctx, folder, pin)
                onDone()
            }, enabled = same) { Text("Khoá") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Huỷ") } }
    )
}

/* ---------- 9. Chế độ lưới cho ảnh và video ---------- */

private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGrid(
    files: List<File>,
    selected: Set<String>,
    onOpen: (File) -> Unit,
    onToggleSelect: (File) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(files, key = { it.absolutePath }) { f ->
            FileTile(
                file = f,
                selected = f.absolutePath in selected,
                onClick = { onOpen(f) },
                onLong = { onToggleSelect(f) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileTile(file: File, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val isVideo = file.extension.lowercase() in VIDEO_EXT
    val mediaLike = !file.isDirectory && (isImage(file) || isVideo)
    val thumb by produceState<Bitmap?>(null, file.absolutePath, file.lastModified()) {
        value = if (!mediaLike) null else withContext(Dispatchers.IO) {
            if (isVideo) ThumbCache.video(file) else ThumbCache.image(file)
        }
    }
    val shown = thumb
    Column(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (shown != null) {
                Image(
                    bitmap = shown.asImageBitmap(),
                    contentDescription = file.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(typeIconRes(file)),
                    contentDescription = file.name,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            file.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/* ---------- 11. Xem PDF nhanh: nhiều trang, phóng to/thu nhỏ ---------- */

private class PdfDoc(val pfd: ParcelFileDescriptor, val renderer: PdfRenderer) {
    val count: Int get() = renderer.pageCount

    /** PdfRenderer chỉ mở một trang tại một thời điểm, nên khoá khi render. */
    @Synchronized
    fun render(index: Int, zoom: Float): Bitmap {
        val pg = renderer.openPage(index)
        try {
            var w = (pg.width * 1.6f * zoom).toInt().coerceAtLeast(1)
            var h = (pg.height * 1.6f * zoom).toInt().coerceAtLeast(1)
            val maxSide = 2000f
            val k = maxOf(w, h) / maxSide
            if (k > 1f) {
                w = (w / k).toInt().coerceAtLeast(1)
                h = (h / k).toInt().coerceAtLeast(1)
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        } finally {
            pg.close()
        }
    }

    fun close() {
        try { renderer.close() } catch (_: Exception) {}
        try { pfd.close() } catch (_: Exception) {}
    }
}

@Composable
fun PdfViewerDialog(file: File, onDismiss: () -> Unit) {
    val doc = remember(file.absolutePath) {
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfDoc(pfd, PdfRenderer(pfd))
        } catch (_: Exception) { null }
    }
    DisposableEffect(doc) { onDispose { doc?.close() } }

    var page by remember(file.absolutePath) { mutableIntStateOf(0) }
    var zoom by remember(file.absolutePath) { mutableFloatStateOf(1f) }
    val count = doc?.count ?: 0

    val bmp by produceState<Bitmap?>(null, doc, page, zoom) {
        value = if (doc == null || count == 0) null else withContext(Dispatchers.IO) {
            doc.render(page.coerceIn(0, count - 1), zoom)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Đóng") }
                    Text(
                        file.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val shown = bmp
                    when {
                        doc == null -> Text("Không mở được PDF", modifier = Modifier.padding(16.dp))
                        shown == null -> ExpressiveCircularProgress(modifier = Modifier.padding(24.dp))
                        else -> Image(
                            bitmap = shown.asImageBitmap(),
                            contentDescription = file.name,
                            modifier = Modifier.width((360f * zoom).dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("Trước") }
                    Text("${if (count == 0) 0 else page + 1}/$count")
                    TextButton(onClick = { if (page < count - 1) page++ }, enabled = page < count - 1) { Text("Sau") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = { zoom = (zoom - 0.5f).coerceAtLeast(0.5f) }, enabled = zoom > 0.5f) { Text("Thu nhỏ") }
                    Text("${(zoom * 100).toInt()}%", modifier = Modifier.padding(horizontal = 12.dp))
                    TextButton(onClick = { zoom = (zoom + 0.5f).coerceAtMost(3f) }, enabled = zoom < 3f) { Text("Phóng to") }
                }
            }
        }
    }
}
