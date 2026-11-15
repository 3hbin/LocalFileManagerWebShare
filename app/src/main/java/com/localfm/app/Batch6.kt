package com.localfm.app

import androidx.compose.foundation.layout.padding
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/* ---------- 27. Lịch sử thao tác ---------- */

object OpLog {
    private const val P = "local_fm_prefs"
    private const val KEY = "op_log"
    private const val MAX = 200
    /** Mục mới nhất ở đầu, mỗi mục là "dd/MM HH:mm  nội dung". */
    val entries = mutableStateListOf<String>()
    private var appCtx: Context? = null
    private val main = Handler(Looper.getMainLooper())

    fun init(ctx: Context) {
        if (appCtx != null) return
        appCtx = ctx.applicationContext
        val raw = appCtx!!.getSharedPreferences(P, 0).getString(KEY, "[]")
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        for (i in 0 until arr.length()) entries.add(arr.getString(i))
    }

    /** Có thể gọi từ luồng nền: việc ghi state được chuyển về luồng chính. */
    fun add(text: String) {
        val stamp = SimpleDateFormat("dd/MM HH:mm", Locale("vi")).format(Date())
        main.post {
            entries.add(0, "$stamp  $text")
            while (entries.size > MAX) entries.removeAt(entries.size - 1)
            save()
        }
    }

    fun clear() {
        entries.clear()
        save()
    }

    private fun save() {
        val c = appCtx ?: return
        val arr = JSONArray()
        entries.forEach { arr.put(it) }
        c.getSharedPreferences(P, 0).edit().putString(KEY, arr.toString()).apply()
    }
}

@Composable
fun OpLogDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lịch sử thao tác") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (OpLog.entries.isEmpty()) Text("Chưa có thao tác nào.")
                OpLog.entries.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
        dismissButton = {
            if (OpLog.entries.isNotEmpty()) TextButton(onClick = { OpLog.clear() }) { Text("Xoá lịch sử") }
        }
    )
}

/* ---------- 28. Chế độ tiết kiệm pin và dữ liệu ---------- */

object PowerMode {
    private const val P = "local_fm_prefs"
    private const val KEY = "power_saving"
    /** Người dùng bật thủ công. */
    var manual by mutableStateOf(false)
    /** Máy đang bật tiết kiệm pin của hệ thống. */
    var systemSaving by mutableStateOf(false)
    private var loaded = false

    /** Khi bật: không tạo thumbnail, không đếm số mục trong thư mục. */
    val active: Boolean get() = manual || systemSaving

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        manual = ctx.applicationContext.getSharedPreferences(P, 0).getBoolean(KEY, false)
        systemSaving = systemPowerSave(ctx)
    }

    private fun systemPowerSave(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

    fun setManual(ctx: Context, on: Boolean) {
        manual = on
        ctx.applicationContext.getSharedPreferences(P, 0).edit().putBoolean(KEY, on).apply()
    }

    /** Gọi khi hệ thống đổi trạng thái tiết kiệm pin. Tạm dừng Web Share nếu đang chạy. */
    fun onSystemChange(ctx: Context) {
        val on = systemPowerSave(ctx)
        systemSaving = on
        if (on && ServerBridge.server != null) {
            try {
                ctx.startService(Intent(ctx, WebShareService::class.java).setAction(WebShareService.ACTION_STOP))
                Toast.makeText(ctx, "Đã tạm dừng Web Share vì máy đang tiết kiệm pin", Toast.LENGTH_LONG).show()
                OpLog.add("Tạm dừng Web Share (tiết kiệm pin)")
            } catch (_: Exception) {}
        }
    }
}

/** Lắng nghe đổi trạng thái tiết kiệm pin của hệ thống trong khi app đang mở. */
@Composable
fun PowerSaveListener() {
    val ctx = LocalContext.current
    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                PowerMode.onSystemChange(c)
            }
        }
        ctx.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose {
            try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }
}

/* ---------- 29. Gửi nhanh qua mạng nội bộ (tự tìm máy) ---------- */

object LanDiscovery {
    /** Cổng UDP dùng để báo "có máy đang chạy Web Share". */
    const val PORT = 8799
    private val selfId = UUID.randomUUID().toString()
    @Volatile private var announcing = false

    class Peer(val ip: String, val port: Int, val name: String, val id: String)

    /** Máy đang chạy Web Share thì phát thông báo qua broadcast mỗi 3 giây. */
    fun startAnnounce(httpPort: Int) {
        if (announcing) return
        announcing = true
        val name = DeviceInfo.name()
        Thread {
            try {
                DatagramSocket().use { sock ->
                    sock.broadcast = true
                    val payload = JSONObject()
                        .put("lfm", 1).put("id", selfId).put("name", name).put("port", httpPort)
                        .toString().toByteArray()
                    val addr = InetAddress.getByName("255.255.255.255")
                    while (announcing) {
                        try { sock.send(DatagramPacket(payload, payload.size, addr, PORT)) } catch (_: Exception) {}
                        Thread.sleep(3000)
                    }
                }
            } catch (_: Exception) {
            } finally {
                announcing = false
            }
        }.start()
    }

    fun stopAnnounce() {
        announcing = false
    }

    /** Nghe các máy khác. Trả về hàm dừng. Danh sách được đưa về luồng chính. */
    fun listen(onPeers: (List<Peer>) -> Unit): () -> Unit {
        val sock = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(PORT))
            }
        } catch (_: Exception) {
            onPeers(emptyList())
            return {}
        }
        val main = Handler(Looper.getMainLooper())
        Thread {
            val seen = HashMap<String, Pair<Peer, Long>>()
            val buf = ByteArray(1024)
            try {
                while (!sock.isClosed) {
                    val pkt = DatagramPacket(buf, buf.size)
                    sock.receive(pkt)
                    val j = try { JSONObject(String(pkt.data, 0, pkt.length)) } catch (_: Exception) { continue }
                    val id = j.optString("id")
                    if (id.isEmpty() || id == selfId || j.optInt("lfm") != 1) continue
                    val ip = pkt.address?.hostAddress ?: continue
                    val now = System.currentTimeMillis()
                    seen[id] = Peer(ip, j.optInt("port"), j.optString("name"), id) to now
                    seen.entries.removeIf { now - it.value.second > 10_000 }
                    val list = seen.values.map { it.first }
                    main.post { onPeers(list) }
                }
            } catch (_: Exception) {
            }
        }.start()
        return { try { sock.close() } catch (_: Exception) {} }
    }

    /** Gửi một tệp tới máy kia qua trang tải lên của Web Share. Trả về (thành công, thông báo). */
    fun send(peer: Peer, file: File, onProgress: (Float) -> Unit): Pair<Boolean, String> {
        val boundary = "----lfm${System.currentTimeMillis()}"
        val conn = (URL("http://${peer.ip}:${peer.port}/upload").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            connectTimeout = 10_000
            readTimeout = 60_000
            setChunkedStreamingMode(64 * 1024)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        return try {
            val safe = file.name.replace("\"", "_")
            conn.outputStream.use { out ->
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"dir\"\r\n\r\n\r\n".toByteArray())
                out.write(
                    ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$safe\"\r\n" +
                        "Content-Type: application/octet-stream\r\n\r\n").toByteArray()
                )
                val total = file.length().coerceAtLeast(1L)
                var sent = 0L
                FileInputStream(file).use { ins ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        sent += n
                        onProgress(sent.toFloat() / total)
                    }
                }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            when (val code = conn.responseCode) {
                401 -> false to "${peer.name} đặt mật khẩu Web Share, chưa gửi được"
                in 200..399 -> true to "Đã gửi ${file.name} tới ${peer.name}"
                else -> false to "${peer.name} trả lỗi $code"
            }
        } catch (e: Exception) {
            false to "Không gửi được tới ${peer.name}: ${e.message ?: "không kết nối"}"
        } finally {
            conn.disconnect()
        }
    }
}

@Composable
fun LanShareDialog(files: List<File>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var peers by remember { mutableStateOf(listOf<LanDiscovery.Peer>()) }
    var status by remember { mutableStateOf("Đang tìm máy trong mạng. Máy kia phải bật Web Share.") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val stop = LanDiscovery.listen { peers = it }
        onDispose { stop() }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Gửi nhanh qua mạng nội bộ") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(status, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text("Gửi ${files.size} tệp", style = MaterialTheme.typography.labelLarge)
                if (peers.isEmpty()) Text("Chưa thấy máy nào.")
                peers.forEach { p ->
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            progress = 0f
                            val task = TaskCenter.start("Gửi tới ${p.name}")
                            scope.launch {
                                val (okCount, msg) = withContext(Dispatchers.IO) {
                                    var ok = 0
                                    var last = ""
                                    files.forEachIndexed { i, f ->
                                        val (success, m) = LanDiscovery.send(p, f) { pr ->
                                            val overall = (i + pr) / files.size
                                            scope.launch(Dispatchers.Main) { progress = overall; task.progress = overall }
                                        }
                                        if (success) ok++
                                        last = m
                                    }
                                    ok to (if (files.size > 1) "Đã gửi $ok/${files.size} tệp tới ${p.name}" else last)
                                }
                                TaskCenter.finish(task)
                                busy = false
                                status = msg
                                OpLog.add("Gửi tới ${p.name}: $okCount tệp")
                            }
                        }
                    ) { Text("${p.name}  (${p.ip})") }
                }
                if (busy) ExpressiveLinearProgress(progress = progress, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Đóng") } }
    )
}

/* ---------- 30. Cập nhật có xác nhận ---------- */

data class UpdateInfo(val tag: String, val notes: String, val apkUrl: String?)

object UpdateCheck {
    private const val API = "https://api.github.com/repos/3hbin/LocalFileManagerWebShare/releases/latest"

    /** Lấy bản phát hành mới nhất từ GitHub. Không có mạng thì trả về null, không báo lỗi. */
    fun fetch(): UpdateInfo? = try {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        var apk: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (apk == null && a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apk = a.optString("browser_download_url")
                }
            }
        }
        UpdateInfo(json.optString("tag_name"), json.optString("body"), apk)
    } catch (_: Exception) {
        null
    }

    /** Tải APK về [dest], báo tiến trình 0..1. */
    fun download(url: String, dest: File, onProgress: (Float) -> Unit): File {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            val total = conn.contentLengthLong.coerceAtLeast(1L)
            var got = 0L
            conn.inputStream.use { ins ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        got += n
                        onProgress((got.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
            return dest
        } finally {
            conn.disconnect()
        }
    }
}

object UpdatePrefs {
    private const val P = "local_fm_prefs"
    private const val KEY = "auto_update"
    var enabled by mutableStateOf(true)
    private var loaded = false

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        enabled = ctx.applicationContext.getSharedPreferences(P, 0).getBoolean(KEY, true)
    }

    fun set(ctx: Context, on: Boolean) {
        enabled = on
        ctx.applicationContext.getSharedPreferences(P, 0).edit().putBoolean(KEY, on).apply()
    }
}

@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Có bản mới ${info.tag}") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    "Đang dùng v${AppUpdate.installed(ctx)}. App chỉ tải và cài khi bạn đồng ý.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    info.notes.take(1500).ifBlank { "Không có ghi chú phát hành." },
                    style = MaterialTheme.typography.bodySmall
                )
                if (busy) {
                    Spacer(Modifier.height(8.dp))
                    ExpressiveLinearProgress(progress = progress, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val url = info.apkUrl
                if (url == null) {
                    AppUpdate.openStore(ctx)
                    onDismiss()
                    return@TextButton
                }
                busy = true
                val task = TaskCenter.start("Tải bản cập nhật ${info.tag}")
                scope.launch {
                    val file = withContext(Dispatchers.IO) {
                        runCatching {
                            UpdateCheck.download(url, File(ctx.cacheDir, "update.apk")) { p ->
                                scope.launch(Dispatchers.Main) { progress = p; task.progress = p }
                            }
                        }.getOrNull()
                    }
                    TaskCenter.finish(task)
                    busy = false
                    if (file == null) {
                        Toast.makeText(ctx, "Tải thất bại, thử lại sau", Toast.LENGTH_SHORT).show()
                    } else {
                        OpLog.add("Tải bản cập nhật ${info.tag}")
                        if (ApkInstaller.isInstallable(file)) ApkInstaller.install(ctx, file)
                        else Toast.makeText(ctx, "Tệp tải về không hợp lệ", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                }
            }) { Text(if (info.apkUrl != null) "Tải & cài" else "Mở trang tải") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Để sau") } }
    )
}

/* ---------- Chia sẻ qua NFC: ghi link vào thẻ NFC ---------- */

/**
 * Lưu ý: Android không còn truyền file trực tiếp bằng NFC (Android Beam đã bỏ từ Android 10).
 * Cách làm ở đây: ghi link Web Share vào thẻ NFC. Máy khác chạm thẻ thì mở link trong trình duyệt.
 */
private fun writeLinkToTag(tag: Tag, url: String): Pair<Boolean, String> {
    val ndef = Ndef.get(tag) ?: return false to "Thẻ không hỗ trợ NDEF"
    return try {
        ndef.connect()
        if (!ndef.isWritable) {
            ndef.close()
            return false to "Thẻ đang bị khoá ghi"
        }
        val msg = NdefMessage(arrayOf(NdefRecord.createUri(url)))
        if (msg.toByteArray().size > ndef.maxSize) {
            ndef.close()
            return false to "Thẻ quá nhỏ cho link này"
        }
        ndef.writeNdefMessage(msg)
        ndef.close()
        true to "Đã ghi xong. Máy khác chạm thẻ để mở link."
    } catch (e: Exception) {
        try { ndef.close() } catch (_: Exception) {}
        false to "Ghi lỗi: ${e.message ?: "không xác định"}"
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun NfcWriteDialog(url: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val activity = remember { ctx.findActivity() }
    val adapter = remember { NfcAdapter.getDefaultAdapter(ctx) }
    val main = remember { Handler(Looper.getMainLooper()) }
    var status by remember {
        mutableStateOf(
            when {
                adapter == null -> "Máy này không có NFC."
                !adapter.isEnabled -> "NFC đang tắt. Bật NFC trong cài đặt rồi thử lại."
                else -> "Đưa thẻ NFC sát mặt sau máy."
            }
        )
    }

    DisposableEffect(adapter, activity) {
        if (adapter != null && adapter.isEnabled && activity != null) {
            adapter.enableReaderMode(
                activity,
                { tag ->
                    val (ok, msg) = writeLinkToTag(tag, url)
                    main.post {
                        status = msg
                        if (ok) OpLog.add("Ghi link NFC: $url")
                    }
                },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null
            )
        }
        onDispose {
            if (adapter != null && activity != null) {
                try { adapter.disableReaderMode(activity) } catch (_: Exception) {}
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ghi link vào thẻ NFC") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(url, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Xong") } }
    )
}
