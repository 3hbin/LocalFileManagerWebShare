package com.localfm.app

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

/* ---------- Cài đặt trình cài ---------- */

enum class InstallMethod { AUTO, ANDROID, SHIZUKU }

object InstallPrefs {
    private const val P = "local_fm_prefs"
    private const val KEY_METHOD = "inst_method"
    private const val KEY_DEL = "inst_del_tmp"
    var method by mutableStateOf(InstallMethod.AUTO)
    var deleteTemp by mutableStateOf(true)
    private var loaded = false

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = ctx.applicationContext.getSharedPreferences(P, 0)
        method = try {
            InstallMethod.valueOf(p.getString(KEY_METHOD, InstallMethod.AUTO.name) ?: InstallMethod.AUTO.name)
        } catch (_: Exception) { InstallMethod.AUTO }
        deleteTemp = p.getBoolean(KEY_DEL, true)
    }

    fun setMethod(ctx: Context, m: InstallMethod) {
        method = m
        ctx.applicationContext.getSharedPreferences(P, 0).edit().putString(KEY_METHOD, m.name).apply()
    }

    fun setDeleteTemp(ctx: Context, on: Boolean) {
        deleteTemp = on
        ctx.applicationContext.getSharedPreferences(P, 0).edit().putBoolean(KEY_DEL, on).apply()
    }
}

/* ---------- Lịch sử cài đặt ---------- */

object InstallHistory {
    private const val P = "local_fm_prefs"
    private const val KEY = "install_history"
    private const val MAX = 300
    /** Mục mới nhất ở đầu. Trạng thái: OK, FAIL, PENDING (chờ xác nhận trên trình cài Android). */
    val entries = mutableStateListOf<String>()
    private var loaded = false
    private val main = Handler(Looper.getMainLooper())

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        val raw = ctx.applicationContext.getSharedPreferences(P, 0).getString(KEY, "[]")
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        for (i in 0 until arr.length()) entries.add(arr.getString(i))
    }

    /** Gọi được từ luồng nền. */
    fun add(ctx: Context, name: String, status: String, method: String, message: String) {
        val stamp = SimpleDateFormat("dd/MM HH:mm", Locale("vi")).format(Date())
        val symbol = when (status) { "OK" -> "✓"; "PENDING" -> "⏳"; else -> "✗" }
        val line = "$stamp  $symbol  $name · $method · $message"
        val app = ctx.applicationContext
        main.post {
            entries.add(0, line)
            while (entries.size > MAX) entries.removeAt(entries.size - 1)
            save(app)
        }
    }

    fun clear(ctx: Context) {
        entries.clear()
        save(ctx.applicationContext)
    }

    private fun save(app: Context) {
        val arr = JSONArray()
        entries.forEach { arr.put(it) }
        app.getSharedPreferences(P, 0).edit().putString(KEY, arr.toString()).apply()
    }
}

/* ---------- Đọc thông tin APK / XAPK ---------- */

data class PkgEntry(
    val file: File,
    val isXapk: Boolean,
    val packageName: String?,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val sizeBytes: Long,
    val apkNames: List<String>,
    val obbNames: List<String>,
    val error: String?
)

object PkgParser {
    private fun longVer(pi: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()

    fun parse(ctx: Context, file: File): PkgEntry {
        val size = file.length()
        return when (file.extension.lowercase(Locale.ROOT)) {
            "apk" -> parseApk(ctx, file, size)
            "xapk", "apks" -> parseXapk(file, size)
            else -> PkgEntry(file, false, null, file.name, null, 0, size, emptyList(), emptyList(), "Không phải APK/XAPK")
        }
    }

    private fun parseApk(ctx: Context, file: File, size: Long): PkgEntry {
        val pm = ctx.packageManager
        val info = try {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (_: Exception) { null }
        val ai = info?.applicationInfo
        if (info == null || ai == null) {
            return PkgEntry(file, false, null, file.nameWithoutExtension, null, 0, size, emptyList(), emptyList(),
                "Không đọc được APK (tệp có thể hỏng)")
        }
        ai.sourceDir = file.absolutePath
        ai.publicSourceDir = file.absolutePath
        val label = try { ai.loadLabel(pm).toString() } catch (_: Exception) { info.packageName }
        return PkgEntry(file, false, info.packageName, label, info.versionName, longVer(info), size,
            listOf(file.name), emptyList(), null)
    }

    private fun parseXapk(file: File, size: Long): PkgEntry {
        return try {
            ZipFile(file).use { z ->
                val manifest = z.getEntry("manifest.json")?.let {
                    JSONObject(z.getInputStream(it).bufferedReader().use { r -> r.readText() })
                }
                val apks = z.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                    .map { it.name }.toList()
                val obbs = z.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith("Android/obb/", ignoreCase = true) }
                    .map { it.name }.toList()
                if (apks.isEmpty()) {
                    return@use PkgEntry(file, true, null, file.nameWithoutExtension, null, 0, size, apks, obbs,
                        "XAPK không có file APK bên trong")
                }
                PkgEntry(
                    file = file,
                    isXapk = true,
                    packageName = manifest?.optString("package_name")?.ifBlank { null },
                    label = manifest?.optString("name")?.ifBlank { null } ?: file.nameWithoutExtension,
                    versionName = manifest?.optString("version_name")?.ifBlank { null },
                    versionCode = manifest?.optLong("version_code", 0L) ?: 0L,
                    sizeBytes = size,
                    apkNames = apks,
                    obbNames = obbs,
                    error = null
                )
            }
        } catch (_: Exception) {
            PkgEntry(file, true, null, file.nameWithoutExtension, null, 0, size, emptyList(), emptyList(),
                "Không mở được XAPK (tệp có thể hỏng)")
        }
    }
}

/* ---------- Tìm APK/XAPK trên bộ nhớ trong và thẻ nhớ ---------- */

object InstallScanner {
    private val EXT = setOf("apk", "xapk", "apks")

    fun scan(ctx: Context): List<File> {
        val roots = StorageVolumes.list(ctx)
            .filter { it.mounted && StorageVolumes.readable(it.root) }
            .map { it.root }
        val out = mutableListOf<File>()
        fun walk(d: File, depth: Int) {
            if (depth > 6 || out.size > 500) return
            d.listFiles().orEmpty().forEach { f ->
                if (f.name.startsWith(".")) return@forEach
                if (isAndroidRestrictedPath(f.absolutePath)) return@forEach
                if (f.isDirectory) walk(f, depth + 1)
                else if (f.extension.lowercase(Locale.ROOT) in EXT) out.add(f)
            }
        }
        roots.forEach { walk(it, 0) }
        return out.distinctBy { it.absolutePath }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }
}

/* ---------- Kiểm tra xung đột trước khi cài ---------- */

enum class Conflict { NEW, UPDATE, DOWNGRADE, SIGNATURE_MISMATCH, UNKNOWN }

object InstallCheck {
    private fun longVer(pi: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()

    /** Tập băm chữ ký. Chỉ đọc được từ Android 9 (API 28). */
    private fun signers(pi: PackageInfo?): Set<Int>? {
        if (pi == null || Build.VERSION.SDK_INT < 28) return null
        val s = pi.signingInfo ?: return null
        return s.apkContentsSigners.map { it.toByteArray().contentHashCode() }.toSet()
    }

    fun check(ctx: Context, e: PkgEntry): Pair<Conflict, String> {
        if (e.error != null) return Conflict.UNKNOWN to e.error
        val pkg = e.packageName ?: return Conflict.UNKNOWN to "Không đọc được tên gói"
        val pm = ctx.packageManager
        val installed = try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            }
        } catch (_: Exception) { null }
        if (installed == null) return Conflict.NEW to "Cài mới"

        val instVer = longVer(installed)
        if (e.versionCode in 1 until instVer) {
            return Conflict.DOWNGRADE to "Đang cài bản ${installed.versionName} mới hơn; cần gỡ bản đang cài trước"
        }
        if (!e.isXapk) {
            val archive = try {
                pm.getPackageArchiveInfo(e.file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            } catch (_: Exception) { null }
            val a = signers(archive)
            val b = signers(installed)
            if (a != null && b != null && a != b) {
                return Conflict.SIGNATURE_MISMATCH to "Khác chữ ký với bản đang cài; cần gỡ bản cũ trước"
            }
        }
        val note = if (e.isXapk) " (XAPK không kiểm tra chữ ký trước)" else ""
        return if (e.versionCode == instVer && instVer > 0) {
            Conflict.UPDATE to "Cài lại cùng phiên bản$note"
        } else {
            Conflict.UPDATE to "Cập nhật từ ${installed.versionName ?: "?"}$note"
        }
    }
}

/* ---------- Shizuku ---------- */

object ShizukuInstaller {
    enum class State { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    fun state(ctx: Context): State = when {
        !RootAccess.shizukuInstalled(ctx) -> State.NOT_INSTALLED
        !RootAccess.shizukuAlive() -> State.NOT_RUNNING
        !RootAccess.shizukuGranted() -> State.NO_PERMISSION
        else -> State.READY
    }

    fun label(s: State): String = when (s) {
        State.NOT_INSTALLED -> "Chưa cài app Shizuku"
        State.NOT_RUNNING -> "Shizuku chưa chạy (bật dịch vụ trong app Shizuku)"
        State.NO_PERMISSION -> "Chưa cấp quyền cho app"
        State.READY -> "Sẵn sàng"
    }

    /**
     * Cài bằng lệnh pm chạy qua Shizuku. Kết quả lấy từ đầu ra của pm, không tự báo thành công.
     * Các APK được chép vào thư mục riêng của app để tiến trình shell đọc được, rồi xoá sau khi xong.
     */
    fun install(ctx: Context, apks: List<File>): Pair<Boolean, String> {
        val staging = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "shizuku-" + System.currentTimeMillis())
        staging.mkdirs()
        return try {
            val staged = apks.map { src -> File(staging, src.name).also { src.copyTo(it, overwrite = true) } }
            val q = { s: String -> "'" + s.replace("'", "'\\''") + "'" }
            val cmd = if (staged.size == 1) {
                "pm install -r ${q(staged[0].absolutePath)}"
            } else {
                val writes = staged.joinToString(" && ") { f ->
                    "pm install-write -S ${f.length()} \$SID ${q(f.name)} ${q(f.absolutePath)}"
                }
                "SID=\$(pm install-create -r | grep -o '[0-9]*' | tail -n 1) && [ -n \"\$SID\" ] && ($writes) " +
                    "&& pm install-commit \$SID || { pm install-abandon \$SID 2>/dev/null; false; }"
            }
            val (code, out) = RootAccess.execShizuku(cmd)
            val ok = code == 0 && out.contains("Success", ignoreCase = true)
            val msg = if (ok) "cài thành công" else "lỗi: " + (out.lines().lastOrNull { it.isNotBlank() } ?: "không rõ")
            ok to msg
        } catch (e: Exception) {
            false to "lỗi: ${e.message ?: "không xác định"}"
        } finally {
            staging.deleteRecursively()
        }
    }
}

/* ---------- Điều phối cài đặt ---------- */

object InstallRunner {
    class Outcome(val ok: Boolean, val pending: Boolean, val method: String, val message: String)

    /** Chọn phương thức thực tế. Shizuku không sẵn sàng thì dùng trình cài Android. */
    private fun resolve(ctx: Context, method: InstallMethod): Pair<Boolean, String> {
        val st = ShizukuInstaller.state(ctx)
        return when (method) {
            InstallMethod.ANDROID -> false to ""
            InstallMethod.SHIZUKU ->
                if (st == ShizukuInstaller.State.READY) true to ""
                else false to "Shizuku: ${ShizukuInstaller.label(st)}; dùng trình cài Android. "
            InstallMethod.AUTO -> (st == ShizukuInstaller.State.READY) to ""
        }
    }

    /** Cài một gói. Không xoá tệp gốc người dùng, chỉ xoá thư mục tạm đã giải nén. */
    suspend fun run(ctx: Context, e: PkgEntry, method: InstallMethod): Outcome = withContext(Dispatchers.IO) {
        val name = e.label
        val (conflict, why) = InstallCheck.check(ctx, e)
        if (conflict == Conflict.DOWNGRADE || conflict == Conflict.SIGNATURE_MISMATCH || conflict == Conflict.UNKNOWN) {
            InstallHistory.add(ctx, name, "FAIL", "—", why)
            return@withContext Outcome(false, false, "—", why)
        }

        val temp = File(ctx.cacheDir, "install-" + System.currentTimeMillis()).apply { mkdirs() }
        try {
            val apks = if (e.isXapk) extractApks(e.file, temp) else listOf(e.file)
            if (apks.isEmpty()) {
                InstallHistory.add(ctx, name, "FAIL", "—", "Không có APK để cài")
                return@withContext Outcome(false, false, "—", "Không có APK để cài")
            }
            val obbNote = if (e.isXapk) copyObb(e) else ""

            val (useShizuku, note) = resolve(ctx, method)
            if (useShizuku) {
                val (ok, msg) = ShizukuInstaller.install(ctx, apks)
                if (ok || method == InstallMethod.SHIZUKU) {
                    val text = (note + msg + obbNote).trim()
                    InstallHistory.add(ctx, name, if (ok) "OK" else "FAIL", "Shizuku", text)
                    return@withContext Outcome(ok, false, "Shizuku", text)
                }
                // Tự động: Shizuku lỗi thì chuyển sang trình cài Android
            }
            val fallbackNote = if (useShizuku) "Shizuku lỗi, dùng trình cài Android. " else note
            withContext(Dispatchers.Main) { ApkInstaller.installSession(ctx, apks, name) }
            val text = (fallbackNote + "đã gửi tới trình cài Android, chờ bạn xác nhận" + obbNote).trim()
            InstallHistory.add(ctx, name, "PENDING", "Android", text)
            Outcome(false, true, "Android", text)
        } finally {
            if (InstallPrefs.deleteTemp) temp.deleteRecursively()
        }
    }

    private fun extractApks(xapk: File, dest: File): List<File> {
        val out = mutableListOf<File>()
        ZipFile(xapk).use { z ->
            z.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) && !it.name.startsWith("Android/obb/", true) }
                .forEach { en ->
                    val target = File(dest, File(en.name).name)
                    z.getInputStream(en).use { ins -> target.outputStream().use { ins.copyTo(it) } }
                    out += target
                }
        }
        return out
    }

    /** Chép OBB vào Android/obb theo đường dẫn trong XAPK. Android có thể chặn thư mục này. */
    private fun copyObb(e: PkgEntry): String {
        if (e.obbNames.isEmpty()) return ""
        return try {
            val root = Environment.getExternalStorageDirectory()
            ZipFile(e.file).use { z ->
                z.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith("Android/obb/", true) }
                    .forEach { en ->
                        val dst = File(root, en.name)
                        dst.parentFile?.mkdirs()
                        z.getInputStream(en).use { ins -> dst.outputStream().use { ins.copyTo(it) } }
                    }
            }
            "; đã chép OBB"
        } catch (ex: Exception) {
            "; không chép được OBB (Android giới hạn Android/obb): ${ex.message ?: ""}"
        }
    }
}
