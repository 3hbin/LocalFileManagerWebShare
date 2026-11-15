package com.localfm.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

object IncomingFiles {
    fun fromIntent(ctx: Context, intent: Intent?): File? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_VIEW && intent.action != Intent.ACTION_EDIT && intent.action != Intent.ACTION_SEND) return null
        val uri = intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri ?: return null
        return toFile(ctx, uri, intent.type)
    }

    fun toFile(ctx: Context, uri: Uri, mime: String?): File? {
        if (uri.scheme == "file") {
            val p = uri.path ?: return null
            val f = File(p)
            return if (f.exists()) f else null
        }
        return try {
            val name = guessName(uri, mime)
            val dest = File(ctx.cacheDir, "open_${System.currentTimeMillis()}_$name")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: return null
            dest
        } catch (_: Exception) {
            null
        }
    }

    private fun guessName(uri: Uri, mime: String?): String {
        val last = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        if (last.contains('.')) return last.take(80)
        val ext = when {
            mime?.contains("zip") == true -> "zip"
            mime?.contains("rar") == true -> "rar"
            mime?.contains("7z") == true -> "7z"
            else -> "bin"
        }
        return "incoming.$ext"
    }

    fun isPicker(intent: Intent?): Boolean {
        val a = intent?.action ?: return false
        return a == Intent.ACTION_GET_CONTENT || a == Intent.ACTION_PICK || a == Intent.ACTION_OPEN_DOCUMENT
    }

    fun returnPicked(ctx: Context, file: File): Boolean {
        val act = ctx as? android.app.Activity ?: return false
        return try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                act, act.packageName + ".fileprovider", file
            )
            val data = Intent().apply {
                setDataAndType(uri, act.contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newUri(act.contentResolver, file.name, uri)
            }
            act.setResult(android.app.Activity.RESULT_OK, data)
            act.finish()
            true
        } catch (_: Exception) {
            false
        }
    }
}
