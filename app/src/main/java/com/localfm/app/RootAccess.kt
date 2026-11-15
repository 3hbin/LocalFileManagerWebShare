package com.localfm.app

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.File

object RootAccess {
    private const val P = "local_fm_prefs"
    const val SU = "su"
    const val SHIZUKU = "shizuku"
    const val SHIZUKU_REQ = 7102
    @Volatile var lastDenied: Boolean = false

    fun mode(ctx: Context) = ctx.getSharedPreferences(P, 0).getString("root_mode", SU) ?: SU
    fun setMode(ctx: Context, m: String) {
        ctx.getSharedPreferences(P, 0).edit().putString("root_mode", m).apply()
    }

    fun systemFiles(ctx: Context) = ctx.getSharedPreferences(P, 0).getBoolean("root_sys", false)
    fun setSystemFiles(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(P, 0).edit().putBoolean("root_sys", on).apply()
    }

    fun selinuxFix(ctx: Context) = ctx.getSharedPreferences(P, 0).getBoolean("root_selinux", false)
    fun setSelinuxFix(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(P, 0).edit().putBoolean("root_selinux", on).apply()
    }

    fun shizukuInstalled(ctx: Context): Boolean {
        return try {
            ctx.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun shizukuAlive(): Boolean = try { Shizuku.pingBinder() } catch (_: Exception) { false }

    fun shizukuGranted(): Boolean {
        return try {
            shizukuAlive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }
    }

    fun requestShizuku(): String {
        return try {
            if (!shizukuAlive()) return "Mở app Shizuku và bật dịch vụ trước"
            if (shizukuGranted()) return "Đã cấp quyền Shizuku"
            Shizuku.requestPermission(SHIZUKU_REQ)
            "Da gui yeu cau. Bat app trong danh sach Shizuku"
        } catch (e: Exception) {
            e.message ?: "Không xin được Shizuku"
        }
    }

    fun exec(cmd: String): Pair<Int, String> {
        val ctxMode = try {
            // prefer shizuku when binder ready
            if (shizukuGranted()) execShizuku(cmd) else execSu(cmd)
        } catch (_: Exception) {
            execSu(cmd)
        }
        return ctxMode
    }

    private fun execSu(cmd: String): Pair<Int, String> {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val stdout = p.inputStream.bufferedReader().readText()
            val stderr = p.errorStream.bufferedReader().readText()
            Pair(p.waitFor(), (stdout + stderr).trim())
        } catch (e: Exception) {
            Pair(-1, e.message ?: "su loi")
        }
    }

    fun execShizuku(cmd: String): Pair<Int, String> {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val p = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val stdout = p.inputStream.bufferedReader().readText()
            val stderr = p.errorStream.bufferedReader().readText()
            Pair(p.waitFor(), (stdout + stderr).trim())
        } catch (e: Exception) {
            Pair(-1, e.message ?: "shizuku loi")
        }
    }

    fun hasSu(): Boolean {
        val r = execSu("id")
        return r.first == 0 && (r.second.contains("uid=0") || r.second.contains("root"))
    }

    fun shouldListAsRoot(ctx: Context, dir: File): Boolean {
        if (!systemFiles(ctx)) return false
        val path = dir.absolutePath
        return path == "/" ||
            path.startsWith("/system") ||
            path.startsWith("/data") ||
            path.startsWith("/vendor") ||
            path.startsWith("/proc") ||
            path.startsWith("/sys") ||
            path.contains("/Android/data") ||
            path.contains("/Android/obb")
    }

    fun list(dir: File, showHidden: Boolean): List<File> {
        val path = dir.absolutePath.replace("'", "'\\''")
        val r = exec("ls -1ap -- '$path'")
        if (r.first != 0) {
            return dir.listFiles()?.filter { showHidden || !it.name.startsWith(".") }.orEmpty()
        }
        return r.second.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "./" && it != "../" }
            .filter { showHidden || !it.removeSuffix("/").startsWith(".") }
            .map { File(dir, it.removeSuffix("/")) }
            .toList()
    }

    fun restorecon(path: String) {
        val p = path.replace("'", "'\\''")
        exec("restorecon -RF '$p'")
    }
}
