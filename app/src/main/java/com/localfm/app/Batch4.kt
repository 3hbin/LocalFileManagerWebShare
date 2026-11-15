package com.localfm.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Surface
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.core.app.NotificationCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/* ---------- Dùng chung: trạng thái yêu cầu từ menu từng tệp ---------- */

object ToolRequests {
    var qrFile by mutableStateOf<File?>(null)
    var editFile by mutableStateOf<File?>(null)
    var linkFile by mutableStateOf<File?>(null)
    var checksumFile by mutableStateOf<File?>(null)
}

/** Giải mã ảnh với kích thước tối đa [maxSide] để tiết kiệm bộ nhớ. */
fun decodeLimited(file: File, maxSide: Int): Bitmap? {
    return try {
        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        file.inputStream().use { BitmapFactory.decodeStream(it, null, opt) }
        var sample = 1
        while (maxOf(opt.outWidth, opt.outHeight) / sample > maxSide) sample *= 2
        val real = BitmapFactory.Options().apply { inSampleSize = sample }
        file.inputStream().use { BitmapFactory.decodeStream(it, null, real) }
    } catch (_: Exception) { null }
}

/* ---------- 12. Chỉnh sửa ảnh cơ bản ---------- */

/** Áp dụng: thu nhỏ theo %, xoay, chỉnh sáng. Trả về bitmap mới, không sửa bản gốc. */
fun transformBitmap(src: Bitmap, rotation: Int, brightness: Float, scalePct: Int): Bitmap {
    var cur = src
    if (scalePct in 1..99) {
        val w = (src.width * scalePct / 100).coerceAtLeast(1)
        val h = (src.height * scalePct / 100).coerceAtLeast(1)
        cur = Bitmap.createScaledBitmap(cur, w, h, true)
    }
    if (rotation % 360 != 0) {
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        cur = Bitmap.createBitmap(cur, 0, 0, cur.width, cur.height, m, true)
    }
    if (brightness != 0f) {
        val off = brightness * 2.55f
        val cm = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, off,
            0f, 1f, 0f, 0f, off,
            0f, 0f, 1f, 0f, off,
            0f, 0f, 0f, 1f, 0f
        ))
        val out = Bitmap.createBitmap(cur.width, cur.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        Canvas(out).drawBitmap(cur, 0f, 0f, paint)
        cur = out
    }
    return cur
}

@Composable
fun ImageEditDialog(file: File, onSaved: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var source by remember(file.absolutePath) { mutableStateOf<Bitmap?>(null) }
    var rotation by remember { mutableIntStateOf(0) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var scalePct by remember { mutableIntStateOf(100) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(file.absolutePath) {
        source = withContext(Dispatchers.IO) { decodeLimited(file, 1600) }
    }
    LaunchedEffect(source, rotation, brightness, scalePct) {
        val s = source ?: return@LaunchedEffect
        delay(120)
        preview = withContext(Dispatchers.IO) { transformBitmap(s, rotation, brightness, scalePct) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chỉnh sửa: ${file.name}") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                val p = preview
                if (p != null) {
                    Image(
                        bitmap = p.asImageBitmap(),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    ExpressiveLinearProgress(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { rotation = (rotation + 90) % 360 }) { Text("Xoay 90°") }
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    listOf(100, 75, 50, 25).forEach { pct ->
                        TextButton(onClick = { scalePct = pct }, enabled = scalePct != pct) { Text("$pct%") }
                    }
                }
                Text("Độ sáng: ${brightness.toInt()}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = -100f..100f
                )
                Text(
                    "Lưu thành bản mới (tên có _edit), không sửa ảnh gốc.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = source != null && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val msg = withContext(Dispatchers.IO) {
                            try {
                                val full = decodeLimited(file, 3000) ?: return@withContext "Không đọc được ảnh"
                                val out = transformBitmap(full, rotation, brightness, scalePct)
                                val dest = ToolsIO.uniqueName(file.parentFile ?: return@withContext "Thư mục không hợp lệ",
                                    "${file.nameWithoutExtension}_edit", "jpg")
                                FileOutputStream(dest).use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                                "Đã lưu ${dest.name}"
                            } catch (_: Exception) { "Không lưu được" }
                        }
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                        busy = false
                        onSaved()
                        onDismiss()
                    }
                }
            ) { Text("Lưu bản mới") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } }
    )
}

object ToolsIO {
    fun uniqueName(parent: File, base: String, ext: String): File {
        var f = File(parent, "$base.$ext")
        var i = 2
        while (f.exists()) { f = File(parent, "$base ($i).$ext"); i++ }
        return f
    }
}

/* ---------- 13. Đồng bộ hai thư mục (xem trước, không xoá gì) ---------- */

object SyncState {
    var source by mutableStateOf<File?>(null)
}

class SyncOp(val src: File, val dst: File, val reason: String)

object SyncPlanner {
    private const val MAX_FILES = 50_000

    private fun index(root: File): Map<String, File> {
        val out = HashMap<String, File>()
        fun walk(d: File) {
            if (out.size > MAX_FILES) return
            d.listFiles().orEmpty().forEach { f ->
                if (f.name.startsWith(".")) return@forEach
                if (isAndroidRestrictedPath(f.absolutePath)) return@forEach
                out[f.absolutePath.removePrefix(root.absolutePath + "/")] = f
                if (f.isDirectory) walk(f)
            }
        }
        walk(root)
        return out
    }

    private fun differs(x: File, y: File): Boolean =
        x.length() != y.length() || Math.abs(x.lastModified() - y.lastModified()) > 2000

    /** Mỗi tệp chỉ xuất hiện một lần trong kế hoạch. Không có thao tác xoá. */
    fun plan(a: File, b: File): List<SyncOp> {
        val ma = index(a)
        val mb = index(b)
        val ops = mutableListOf<SyncOp>()
        for ((rel, fa) in ma) {
            val fb = mb[rel]
            if (fb == null) {
                if (fa.isFile) ops += SyncOp(fa, File(b, rel), "Chỉ có ở A")
            } else if (fa.isFile && fb.isFile && differs(fa, fb)) {
                if (fa.lastModified() > fb.lastModified()) ops += SyncOp(fa, fb, "A mới hơn")
                else ops += SyncOp(fb, fa, "B mới hơn")
            }
        }
        for ((rel, fb) in mb) {
            if (!ma.containsKey(rel) && fb.isFile) ops += SyncOp(fb, File(a, rel), "Chỉ có ở B")
        }
        return ops
    }
}

@Composable
fun SyncDialog(a: File, b: File, onDone: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var ops by remember { mutableStateOf<List<SyncOp>?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(a.absolutePath, b.absolutePath) {
        ops = withContext(Dispatchers.IO) { SyncPlanner.plan(a, b) }
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text("Đồng bộ thư mục") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("A: ${a.absolutePath}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Text("B: ${b.absolutePath}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                val list = ops
                when {
                    list == null -> ExpressiveLinearProgress(modifier = Modifier.fillMaxWidth())
                    list.isEmpty() -> Text("Hai thư mục đã giống nhau.")
                    else -> {
                        Text("Sẽ sao chép ${list.size} tệp. Không xoá tệp nào.", style = MaterialTheme.typography.bodyMedium)
                        list.take(60).forEach { op ->
                            Text(
                                "${op.reason}: ${op.src.name} → ${op.dst.parentFile?.name ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (list.size > 60) Text("…và ${list.size - 60} tệp nữa")
                    }
                }
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    ExpressiveLinearProgress(progress = progress, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            val list = ops
            if (list != null && list.isNotEmpty() && !running) {
                TextButton(onClick = {
                    running = true
                    val task = TaskCenter.start("Đồng bộ ${list.size} tệp")
                    scope.launch {
                        var done = 0
                        var skipped = 0
                        withContext(Dispatchers.IO) {
                            list.forEachIndexed { i, op ->
                                if (ReadOnlyLocks.isLocked(op.dst)) { skipped++; return@forEachIndexed }
                                try {
                                    op.dst.parentFile?.mkdirs()
                                    op.src.copyTo(op.dst, overwrite = true)
                                    done++
                                } catch (_: Exception) { skipped++ }
                                val p = (i + 1f) / list.size
                                scope.launch(Dispatchers.Main) { progress = p; task.progress = p }
                            }
                        }
                        TaskCenter.finish(task)
                        OpLog.add("Đồng bộ: $done tệp")
                        running = false
                        Toast.makeText(ctx, "Đã đồng bộ $done tệp" + if (skipped > 0) ", bỏ qua $skipped" else "", Toast.LENGTH_SHORT).show()
                        onDone()
                        onDismiss()
                    }
                }) { Text("Đồng bộ") }
            } else TextButton(onClick = onDismiss) { Text("Đóng") }
        },
        dismissButton = {
            if (!ops.isNullOrEmpty() && !running) TextButton(onClick = onDismiss) { Text("Huỷ") }
        }
    )
}


/* ---------- 15. Dọn dẹp tự động hàng tuần ---------- */

object AutoClean {
    private const val P = "local_fm_prefs"
    private const val KEY = "auto_clean"
    private const val WORK = "auto_clean_weekly"
    private const val CH = "auto_clean"
    var enabled by mutableStateOf(false)
    private var loaded = false

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        enabled = ctx.applicationContext.getSharedPreferences(P, 0).getBoolean(KEY, false)
    }

    fun set(ctx: Context, on: Boolean) {
        enabled = on
        ctx.applicationContext.getSharedPreferences(P, 0).edit().putBoolean(KEY, on).apply()
        val wm = WorkManager.getInstance(ctx.applicationContext)
        if (on) {
            wm.enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AutoCleanWorker>(7, TimeUnit.DAYS).build()
            )
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }

    /** Chuyển vào thùng rác: tệp tải dở quá 3 ngày và APK đã cài (cùng tên gói). Trả về số tệp đã chuyển. */
    fun cleanNow(ctx: Context): Int {
        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val cut = System.currentTimeMillis() - 3L * 86_400_000L
        var n = 0
        if (dl.isDirectory) {
            dl.walkTopDown().maxDepth(3).filter { it.isFile }.forEach { f ->
                val name = f.name.lowercase()
                val partial = name.endsWith(".crdownload") || name.endsWith(".part") || name.endsWith(".download")
                val staleDownload = partial && f.lastModified() < cut
                val installedApk = name.endsWith(".apk") && isInstalledApk(ctx, f)
                if ((staleDownload || installedApk) && TrashBin.moveToTrashFile(ctx, f) != null) n++
            }
        }
        return n
    }

    private fun isInstalledApk(ctx: Context, f: File): Boolean {
        val pm = ctx.packageManager
        val pkg = pm.getPackageArchiveInfo(f.absolutePath, 0)?.packageName ?: return false
        return try { pm.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
    }

    fun notifyResult(ctx: Context, text: String) {
        OpLog.add(text)
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel(CH, "Dọn tự động", NotificationManager.IMPORTANCE_LOW))
            }
            val n = NotificationCompat.Builder(ctx, CH)
                .setSmallIcon(android.R.drawable.ic_menu_delete)
                .setContentTitle("Dọn tự động")
                .setContentText(text)
                .setAutoCancel(true)
                .build()
            nm.notify(301, n)
        } catch (_: Exception) {}
    }
}

class AutoCleanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val moved = AutoClean.cleanNow(applicationContext)
        AutoClean.notifyResult(applicationContext, "Đã chuyển $moved tệp tải dở hoặc APK đã cài vào thùng rác")
        return Result.success()
    }
}

/* ---------- 17. Đọc mã QR từ ảnh ---------- */

object QrReaderUtil {
    fun decode(file: File): String? {
        val bmp = decodeLimited(file, 1600) ?: return null
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val source = RGBLuminanceSource(w, h, px)
        val hints = mapOf(DecodeHintType.TRY_HARDER to true)
        return try {
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (_: Exception) { null }
    }
}

@Composable
fun QrScanDialog(file: File, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val text by produceState<String?>(initialValue = null, file.absolutePath) {
        value = withContext(Dispatchers.IO) { QrReaderUtil.decode(file) } ?: ""
    }
    val result = text
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mã QR trong ${file.name}") },
        text = {
            when {
                result == null -> ExpressiveLinearProgress(modifier = Modifier.fillMaxWidth())
                result.isEmpty() -> Text("Không tìm thấy mã QR trong ảnh này.")
                else -> SelectionContainer { Text(result) }
            }
        },
        confirmButton = {
            if (!result.isNullOrEmpty() && (result.startsWith("http://") || result.startsWith("https://"))) {
                TextButton(onClick = {
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                        Toast.makeText(ctx, "Không mở được link", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }) { Text("Mở link") }
            } else TextButton(onClick = onDismiss) { Text("Đóng") }
        },
        dismissButton = {
            if (!result.isNullOrEmpty()) TextButton(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("qr", result))
                Toast.makeText(ctx, "Đã sao chép", Toast.LENGTH_SHORT).show()
            }) { Text("Sao chép") }
        }
    )
}

/* ---------- 18. Dòng thời gian ảnh và video theo ngày ---------- */

private val MEDIA_VIDEO = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineDialog(root: File, onOpen: (File) -> Unit, onDismiss: () -> Unit) {
    val groups by produceState<List<Pair<String, List<File>>>?>(null, root.absolutePath) {
        value = withContext(Dispatchers.IO) {
            val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("vi"))
            val files = mutableListOf<File>()
            fun walk(d: File, depth: Int) {
                if (depth > 8 || files.size > 3000) return
                d.listFiles().orEmpty().forEach { f ->
                    if (f.name.startsWith(".")) return@forEach
                    if (isAndroidRestrictedPath(f.absolutePath)) return@forEach
                    if (f.isDirectory) walk(f, depth + 1)
                    else if (isImage(f) || f.extension.lowercase() in MEDIA_VIDEO) files.add(f)
                }
            }
            walk(root, 0)
            files.sortedByDescending { it.lastModified() }
                .groupBy { fmt.format(java.util.Date(it.lastModified())) }
                .toList()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                Text(
                    "Dòng thời gian · ${root.name.ifBlank { root.absolutePath }}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                val list = groups
                when {
                    list == null -> ExpressiveLinearProgress(modifier = Modifier.fillMaxWidth().padding(16.dp))
                    list.isEmpty() -> Text("Không có ảnh hoặc video trong thư mục này.", modifier = Modifier.padding(16.dp))
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
                    ) {
                        list.forEach { (day, files) ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "$day · ${files.size} mục",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
                                )
                            }
                            items(files, key = { it.absolutePath }) { f ->
                                FileTile(file = f, selected = false, onClick = { onOpen(f) }, onLong = {})
                            }
                        }
                    }
                }
            }
        }
    }
}
