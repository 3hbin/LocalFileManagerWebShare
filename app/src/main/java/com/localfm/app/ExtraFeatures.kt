package com.localfm.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.speech.RecognizerIntent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

object DeviceInfo {
    fun name(): String {
        val raw = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        return raw.replace(Regex("\\s+"), " ")
    }
}

object AppLock {
    private const val P = "local_fm_prefs"
    fun hasPin(ctx: Context) = !pinHash(ctx).isNullOrBlank()
    fun pinHash(ctx: Context) = ctx.getSharedPreferences(P, 0).getString("pin_hash", null)
    fun setPin(ctx: Context, pin: String) {
        ctx.getSharedPreferences(P, 0).edit().putString("pin_hash", sha(pin)).apply()
    }
    fun clear(ctx: Context) {
        ctx.getSharedPreferences(P, 0).edit().remove("pin_hash").apply()
    }
    fun check(ctx: Context, pin: String) = sha(pin) == pinHash(ctx)
    private fun sha(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}

object TrashBin {
    private const val DIR = ".LocalFM_Trash"
    private const val INDEX = "index.json"

    /** Thùng rác cùng ổ với file gốc: chuyển bằng rename, không copy nên không tốn thêm dung lượng. */
    private fun rootFor(ctx: Context, file: File): File {
        val p = file.absolutePath
        val dir = when {
            p.startsWith("/storage/emulated/") || p.startsWith("/storage/self/primary") || p.startsWith("/sdcard") ->
                File(android.os.Environment.getExternalStorageDirectory(), DIR)
            p.startsWith("/storage/") -> File(p.split("/").take(3).joinToString("/"), DIR)
            else -> File(ctx.filesDir, "trash")
        }
        return dir.apply { mkdirs() }
    }

    /** Mọi thư mục thùng rác đang có (ổ chính, thẻ nhớ, và thư mục cũ trong bộ nhớ app). */
    private fun roots(ctx: Context): List<File> {
        val list = mutableListOf(File(android.os.Environment.getExternalStorageDirectory(), DIR))
        File("/storage").listFiles()?.forEach { v ->
            if (v.name != "emulated" && v.name != "self") list += File(v, DIR)
        }
        list += File(ctx.filesDir, "trash")
        return list.distinctBy { it.absolutePath }
    }

    private fun loadIndex(root: File): JSONObject = try {
        File(root, INDEX).takeIf { it.exists() }?.let { JSONObject(it.readText()) } ?: JSONObject()
    } catch (_: Exception) { JSONObject() }

    private fun saveIndex(root: File, obj: JSONObject) {
        File(root, INDEX).writeText(obj.toString())
    }

    /** Thời điểm đưa vào thùng rác, lấy từ tiền tố tên file. */
    private fun stampOf(f: File): Long = f.name.substringBefore("_").toLongOrNull() ?: f.lastModified()

    fun list(ctx: Context): List<File> =
        roots(ctx).flatMap { r -> r.listFiles()?.filter { it.name != INDEX }?.toList().orEmpty() }
            .sortedByDescending { stampOf(it) }

    private fun copyAny(src: File, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            if (src.isDirectory) src.copyRecursively(dest, overwrite = true)
            else src.copyTo(dest, overwrite = true)
            dest.exists()
        } catch (_: Exception) { false }
    }

    private fun deleteAny(file: File): Boolean {
        return try {
            if (!file.exists()) true
            else file.deleteRecursively() || !file.exists()
        } catch (_: Exception) { !file.exists() }
    }

    fun moveToTrash(ctx: Context, file: File): Boolean = moveToTrashFile(ctx, file) != null

    /** Đưa vào thùng rác và trả về file đã nằm trong thùng rác (để hoàn tác). */
    fun moveToTrashFile(ctx: Context, file: File): File? {
        if (!file.exists()) return null
        val root = rootFor(ctx, file)
        val name = "${System.currentTimeMillis()}_${file.name}"
        val dest = File(root, name)
        val ok = file.renameTo(dest) ||
            (copyAny(file, dest) && (deleteAny(file) || run { dest.deleteRecursively(); false }))
        if (!ok) return null
        val idx = loadIndex(root)
        idx.put(name, file.absolutePath)
        saveIndex(root, idx)
        return if (dest.exists()) dest else null
    }

    fun restore(ctx: Context, trashFile: File, destDir: File): Boolean {
        if (!trashFile.exists()) return false
        val root = trashFile.parentFile ?: return false
        val idx = loadIndex(root)
        val original = idx.optString(trashFile.name, "")
        val display = trashFile.name.substringAfter("_")
        var dest = if (original.isNotBlank()) File(original) else File(destDir, display)
        if (dest.exists()) dest = File(dest.parentFile ?: destDir, "restored_${System.currentTimeMillis()}_$display")
        dest.parentFile?.mkdirs()
        val ok = trashFile.renameTo(dest) || (copyAny(trashFile, dest) && deleteAny(trashFile))
        if (!ok) return false
        idx.remove(trashFile.name)
        saveIndex(root, idx)
        return true
    }

    /** Xoá hẳn một mục trong thùng rác và dọn luôn mục trong index. */
    fun deleteForever(file: File): Boolean {
        val ok = deleteAny(file)
        file.parentFile?.let { root ->
            val idx = loadIndex(root)
            if (idx.has(file.name)) { idx.remove(file.name); saveIndex(root, idx) }
        }
        return ok
    }

    fun purgeOld(ctx: Context, days: Int = 30): Int {
        val cut = System.currentTimeMillis() - days * 24L * 3600_000
        var n = 0
        list(ctx).forEach { if (stampOf(it) < cut && deleteForever(it)) n++ }
        return n
    }
}

object ShareLog {
    private const val P = "local_fm_prefs"
    fun add(ctx: Context, line: String) {
        val arr = JSONArray(ctx.getSharedPreferences(P, 0).getString("share_log", "[]"))
        arr.put(JSONObject().put("t", System.currentTimeMillis()).put("m", line))
        while (arr.length() > 80) arr.remove(0)
        ctx.getSharedPreferences(P, 0).edit().putString("share_log", arr.toString()).apply()
    }
    fun lines(ctx: Context): List<String> {
        val arr = JSONArray(ctx.getSharedPreferences(P, 0).getString("share_log", "[]"))
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            o.optString("m")
        }.asReversed()
    }
}

object JunkCleaner {
    /** Chỉ dọn cache / ZIP tạm / thùng rác cũ — không phải diệt virus. */
    fun clean(ctx: Context): String {
        var bytes = 0L
        var files = 0
        fun eat(f: File) {
            if (!f.exists()) return
            val sz = if (f.isFile) f.length() else 0
            if (f.deleteRecursively()) { bytes += sz; files++ }
        }
        ctx.cacheDir.listFiles()?.forEach { eat(it) }
        ctx.codeCacheDir.listFiles()?.forEach { eat(it) }
        ctx.externalCacheDir?.listFiles()?.forEach { eat(it) }
        File(ctx.cacheDir, "tmp").deleteRecursively()
        val purged = TrashBin.purgeOld(ctx, 30)
        return "Đã dọn $files mục cache, ${bytes / 1024} KB, thùng rác cũ $purged. Đây không phải diệt virus."
    }
}

object BackupKit {
    fun exportJson(ctx: Context): String {
        val o = JSONObject()
        o.put("device", DeviceInfo.name())
        o.put("id", Build.ID)
        o.put("favorites", JSONArray(FmSettings.favorites(ctx)))
        o.put("recents", JSONArray(FmSettings.recents(ctx)))
        o.put("hide_sizes", FmSettings.hideSizes(ctx))
        o.put("web_pass_set", FmSettings.webPassword(ctx).isNotBlank())
        o.put("google_email", FmSettings.googleEmail(ctx))
        return o.toString(2)
    }

    fun importJson(ctx: Context, raw: String): Boolean {
        return try {
            val o = JSONObject(raw)
            val fav = o.optJSONArray("favorites") ?: JSONArray()
            for (i in 0 until fav.length()) {
                val path = fav.getString(i)
                if (!FmSettings.isFavorite(ctx, path) && File(path).exists()) {
                    FmSettings.toggleFavorite(ctx, path)
                }
            }
            val mail = o.optString("google_email")
            if (mail.isNotBlank()) FmSettings.setGoogleEmail(ctx, mail)
            true
        } catch (_: Exception) { false }
    }

    fun backupFolder(ctx: Context): File {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "LocalFM_Backup/${DeviceInfo.name().replace('/', '-')}")
        dir.mkdirs()
        File(dir, "settings.json").writeText(exportJson(ctx))
        return dir
    }

    fun listLocal(): List<File> {
        val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "LocalFM_Backup")
        if (!root.exists()) return emptyList()
        return root.walkTopDown().filter { it.isFile && it.name == "settings.json" }.toList()
    }
}

object ThumbCache {
    private val mem = android.util.LruCache<String, Bitmap>(80)
    fun image(file: File): Bitmap? {
        if (PowerMode.active) return null
        mem.get(file.absolutePath)?.let { return it }
        return try {
            val opt = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            file.inputStream().use { BitmapFactory.decodeStream(it, null, opt) }
            opt.inSampleSize = (maxOf(opt.outWidth, opt.outHeight) / 192).coerceAtLeast(1)
            opt.inJustDecodeBounds = false
            file.inputStream().use { BitmapFactory.decodeStream(it, null, opt) }?.also {
                mem.put(file.absolutePath, it)
            }
        } catch (_: Exception) { null }
    }
    fun video(file: File): Bitmap? {
        if (PowerMode.active) return null
        val key = "v:" + file.absolutePath + ":" + file.lastModified()
        mem.get(key)?.let { return it }
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val k = if (w > 0 && h > 0) minOf(1f, 192f / maxOf(w, h)) else 1f
            val frame = if (Build.VERSION.SDK_INT >= 27 && w > 0 && h > 0) {
                r.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    (w * k).toInt().coerceAtLeast(1), (h * k).toInt().coerceAtLeast(1))
            } else {
                r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { scaleTo(it, 192) }
            }
            frame?.also { mem.put(key, it) }
        } catch (_: Exception) { null } finally { r.release() }
    }

    private fun scaleTo(src: Bitmap, max: Int): Bitmap {
        val k = max.toFloat() / maxOf(src.width, src.height)
        if (k >= 1f) return src
        val b = Bitmap.createScaledBitmap(src, (src.width * k).toInt().coerceAtLeast(1),
            (src.height * k).toInt().coerceAtLeast(1), true)
        src.recycle()
        return b
    }

    fun appPkg(ctx: Context, pkg: String): Bitmap? {
        if (PowerMode.active) return null
        val key = "pkg:" + pkg
        mem.get(key)?.let { return it }
        return try {
            val pm = ctx.packageManager
            val d = pm.getApplicationIcon(pkg)
            drawableToBitmap(d, 96)?.also { mem.put(key, it) }
        } catch (_: Exception) { null }
    }

    fun appLabel(ctx: Context, pkg: String): String? {
        return try {
            val pm = ctx.packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai)?.toString()
        } catch (_: Exception) { null }
    }

    fun apk(ctx: Context, file: File): Bitmap? {
        if (PowerMode.active) return null
        val key = "apk:" + file.absolutePath + ":" + file.lastModified()
        mem.get(key)?.let { return it }
        var tmpPath: String? = null
        return try {
            val path = resolveApkPath(ctx, file) ?: return null
            if (path != file.absolutePath) tmpPath = path
            val pm = ctx.packageManager
            val pkg = pm.getPackageArchiveInfo(path, 0) ?: return null
            val app = pkg.applicationInfo ?: return null
            app.sourceDir = path
            app.publicSourceDir = path
            val d = app.loadIcon(pm) ?: return null
            drawableToBitmap(d, 96)?.also { mem.put(key, it) }
        } catch (_: Exception) { null } finally {
            // Bản APK tạm trích từ XAPK chỉ cần để lấy icon: xoá ngay sau khi dùng
            tmpPath?.let { File(it).delete() }
        }
    }

    private fun resolveApkPath(ctx: Context, file: File): String? {
        val ext = file.extension.lowercase()
        if (ext == "apk") return file.absolutePath
        if (ext != "xapk" && ext != "apks") return null
        val out = File(ctx.cacheDir, "xapk-icon-" + file.name.hashCode() + ".apk")
        if (out.exists() && out.length() > 2048) return out.absolutePath
        java.util.zip.ZipFile(file).use { z ->
            val e = z.entries().toList()
                .filter { !it.isDirectory && it.name.lowercase().endsWith(".apk") }
                .filter { !it.name.lowercase().contains("config.") }
                .maxByOrNull { it.size } ?: return null
            if (e.size > 120L * 1024 * 1024) return null
            z.getInputStream(e).use { ins -> out.outputStream().use { ins.copyTo(it) } }
        }
        return if (out.exists()) out.absolutePath else null
    }

    private fun drawableToBitmap(d: android.graphics.drawable.Drawable, size: Int): Bitmap? {
        if (d is android.graphics.drawable.BitmapDrawable && d.bitmap != null) {
            return Bitmap.createScaledBitmap(d.bitmap, size, size, true)
        }
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return b
    }
}

object FolderSize {
    fun of(dir: File, limitFiles: Int = 4000): Long {
        var n = 0
        var sum = 0L
        fun walk(f: File) {
            if (n > limitFiles) return
            if (f.isFile) { sum += f.length(); n++ }
            else f.listFiles()?.forEach { walk(it) }
        }
        walk(dir)
        return sum
    }
}

object FileCompare {
    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun looksText(f: File): Boolean {
        val head = ByteArray(8192)
        val n = f.inputStream().use { it.read(head) }
        return n <= 0 || (0 until n).none { head[it].toInt() == 0 }
    }

    fun compare(a: File, b: File): String {
        val sb = StringBuilder()
        sb.append("A: ${a.name} — ${a.length()} byte\n")
        sb.append("B: ${b.name} — ${b.length()} byte\n\n")
        if (a.length() == b.length() && sha256(a) == sha256(b)) {
            return sb.append("Nội dung giống hệt nhau.").toString()
        }
        sb.append("Nội dung khác nhau.")
        if (a.length() <= 2_000_000L && b.length() <= 2_000_000L && looksText(a) && looksText(b)) {
            val la = a.readLines()
            val lb = b.readLines()
            val setA = la.toHashSet()
            val setB = lb.toHashSet()
            val onlyA = la.filter { it !in setB }
            val onlyB = lb.filter { it !in setA }
            sb.append("\n\nDòng chỉ có trong A: ${onlyA.size}, chỉ có trong B: ${onlyB.size}.")
            onlyA.take(5).forEach { sb.append("\n- ").append(it.take(160)) }
            onlyB.take(5).forEach { sb.append("\n+ ").append(it.take(160)) }
        }
        return sb.toString()
    }
}

fun speechIntent(): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói tên mới")
    }
