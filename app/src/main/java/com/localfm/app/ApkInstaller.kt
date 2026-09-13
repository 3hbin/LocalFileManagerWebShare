package com.localfm.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipFile

object ApkInstaller {
    fun isInstallable(file: File): Boolean =
        file.extension.lowercase() in setOf("apk", "xapk", "apks")

    fun install(context: Context, file: File) {
        if (!file.exists()) {
            toast(context, "Không thấy tệp")
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            toast(context, "Bật quyền cài app không rõ nguồn cho Quản lý tệp")
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        when (file.extension.lowercase()) {
            "apk" -> installSingle(context, file)
            "xapk", "apks" -> installArchive(context, file)
            else -> toast(context, "Không phải APK")
        }
    }

    private fun installSingle(context: Context, apk: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            toast(context, "Không mở được trình cài đặt")
        }
    }

    private fun installArchive(context: Context, archive: File) {
        try {
            val dir = File(context.cacheDir, "xapk-" + archive.name.hashCode())
            dir.deleteRecursively()
            dir.mkdirs()
            val apks = mutableListOf<File>()
            ZipFile(archive).use { z ->
                z.entries().asSequence()
                    .filter { !it.isDirectory && it.name.lowercase().endsWith(".apk") }
                    .forEach { e ->
                        val name = File(e.name).name
                        val out = File(dir, name)
                        z.getInputStream(e).use { ins -> out.outputStream().use { ins.copyTo(it) } }
                        apks += out
                    }
            }
            if (apks.isEmpty()) {
                toast(context, "XAPK không có file APK bên trong")
                return
            }
            if (apks.size == 1) {
                installSingle(context, apks.first())
                return
            }
            installSession(context, apks)
        } catch (e: Exception) {
            toast(context, "Không giải được XAPK")
        }
    }

    private fun installSession(context: Context, apks: List<File>) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            apks.forEach { apk ->
                session.openWrite(apk.name, 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
            }
            val callback = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(context, sessionId, callback, flags)
            session.commit(pi.intentSender)
            toast(context, "Đang cài ${apks.size} gói APK…")
        } catch (e: Exception) {
            session.abandon()
            toast(context, "Cài XAPK thất bại")
        } finally {
            session.close()
        }
    }

    private fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirm != null) context.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS ->
                Toast.makeText(context, "Cài đặt thành công", Toast.LENGTH_SHORT).show()
            else ->
                Toast.makeText(context, "Cài thất bại: ${msg ?: status}", Toast.LENGTH_LONG).show()
        }
    }
}
