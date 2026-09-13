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
    private fun root(ctx: Context) = File(ctx.filesDir, "trash").apply { mkdirs() }
    private fun index(ctx: Context) = File(root(ctx), "index.json")

    fun list(ctx: Context): List<File> =
        root(ctx).listFiles()?.filter { it.name != "index.json" }.orEmpty().sortedByDescending { it.lastModified() }

    fun moveToTrash(ctx: Context, file: File): Boolean {
        if (!file.exists()) return false
        val dest = File(root(ctx), "${System.currentTimeMillis()}_${file.name}")
        return try {
            if (file.renameTo(dest)) true
            else {
                if (file.isDirectory) file.copyRecursively(dest) else file.copyTo(dest)
                file.deleteRecursively()
            }
        } catch (_: Exception) { false }
    }

    fun restore(ctx: Context, trashFile: File, destDir: File): Boolean {
        val dest = File(destDir, trashFile.name.substringAfter("_"))
        return try { trashFile.renameTo(dest) } catch (_: Exception) { false }
    }

    fun purgeOld(ctx: Context, days: Int = 7): Int {
        val cut = System.currentTimeMillis() - days * 24L * 3600_000
        var n = 0
        list(ctx).forEach {
            if (it.lastModified() < cut) {
                it.deleteRecursively(); n++
            }
        }
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
        val purged = TrashBin.purgeOld(ctx, 7)
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
}

object ThumbCache {
    private val mem = android.util.LruCache<String, Bitmap>(24)
    fun image(file: File): Bitmap? {
        mem.get(file.absolutePath)?.let { return it }
        return try {
            val opt = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, opt)
            opt.inSampleSize = (maxOf(opt.outWidth, opt.outHeight) / 192).coerceAtLeast(1)
            opt.inJustDecodeBounds = false
            BitmapFactory.decodeFile(file.absolutePath, opt)?.also {
                mem.put(file.absolutePath, it)
            }
        } catch (_: Exception) { null }
    }
    fun video(file: File): Bitmap? {
        mem.get("v:"+file.absolutePath)?.let { return it }
        return try {
            val r = MediaMetadataRetriever()
            r.setDataSource(file.absolutePath)
            val b = r.getFrameAtTime(0)
            r.release()
            b?.also { mem.put("v:"+file.absolutePath, it) }
        } catch (_: Exception) { null }
    }

    fun apk(ctx: Context, file: File): Bitmap? {
        val key = "apk:" + file.absolutePath + ":" + file.lastModified()
        mem.get(key)?.let { return it }
        return try {
            val path = resolveApkPath(ctx, file) ?: return null
            val pm = ctx.packageManager
            val pkg = pm.getPackageArchiveInfo(path, 0) ?: return null
            val app = pkg.applicationInfo ?: return null
            app.sourceDir = path
            app.publicSourceDir = path
            val d = app.loadIcon(pm) ?: return null
            drawableToBitmap(d, 96)?.also { mem.put(key, it) }
        } catch (_: Exception) { null }
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

fun speechIntent(): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói tên mới")
    }
