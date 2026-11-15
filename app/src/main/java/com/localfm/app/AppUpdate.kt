package com.localfm.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AppUpdate {
    const val RELEASES_PAGE = "https://github.com/3hbin/LocalFileManagerWebShare/releases/latest"
    private const val API = "https://api.github.com/repos/3hbin/LocalFileManagerWebShare/releases/latest"

    fun installed(ctx: Context): String {
        val pm = ctx.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(ctx.packageName, 0)
        }
        return info.versionName ?: "1.0"
    }

    fun openStore(ctx: Context) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE)))
    }

    /** Trả tag latest (vd v1.2.1) hoặc null nếu mạng lỗi. */
    fun latestTag(): String? {
        return try {
            val conn = URL(API).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.inputStream.bufferedReader().use { body ->
                JSONObject(body.readText()).optString("tag_name").ifBlank { null }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isNewer(remote: String, local: String): Boolean {
        fun nums(s: String) = s.trim().removePrefix("v").split('.', '-', '_')
            .mapNotNull { it.toIntOrNull() }
        val a = nums(remote)
        val b = nums(local)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
