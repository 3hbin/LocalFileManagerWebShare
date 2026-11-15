package com.localfm.app

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object DriveSync {
    const val WEB_CLIENT =
        "29785662413-tfo1tvk34t3702h35912totb02vssjo2.apps.googleusercontent.com"
    private const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val FILES = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"

    fun signInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()

    fun signInOptionsDrive(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope(SCOPE))
            .build()

    data class Remote(val id: String, val name: String)

    private fun token(ctx: Context): String? {
        val acc = GoogleSignIn.getLastSignedInAccount(ctx) ?: return null
        val account = acc.account ?: return null
        return try {
            GoogleAuthUtil.getToken(ctx, account, "oauth2:$SCOPE")
        } catch (_: Exception) { null }
    }

    fun uploadBackup(ctx: Context): String {
        val tok = token(ctx) ?: return "Chưa đăng nhập Google (thiếu quyền Drive)"
        val device = DeviceInfo.name().replace(Regex("[^A-Za-z0-9._-]"), "_")
        val name = "lfm_${device}.json"
        val body = BackupKit.exportJson(ctx)
        val meta = JSONObject()
            .put("name", name)
            .put("parents", JSONArray().put("appDataFolder"))
        val boundary = "lfm${System.currentTimeMillis()}"
        val payload = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(meta.toString())
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/json\r\n\r\n")
            append(body)
            append("\r\n--$boundary--\r\n")
        }.toByteArray(Charsets.UTF_8)
        return try {
            listRemote(tok).filter { it.name == name }.forEach { delete(tok, it.id) }
            val conn = (URL(UPLOAD).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $tok")
                setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                doOutput = true
                connectTimeout = 20000
                readTimeout = 20000
            }
            conn.outputStream.use { it.write(payload) }
            val code = conn.responseCode
            val err = (if (code >= 400) conn.errorStream else conn.inputStream)
                ?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            if (code in 200..299) "Đã lưu lên Drive: $name" else "Drive lỗi $code $err"
        } catch (e: Exception) {
            e.message ?: "Không tải lên Drive"
        }
    }

    fun listBackups(ctx: Context): List<Remote> {
        val tok = token(ctx) ?: return emptyList()
        return listRemote(tok)
    }

    fun restore(ctx: Context, fileId: String): String {
        val tok = token(ctx) ?: return "Chưa đăng nhập Google"
        return try {
            val url = URL("$FILES/$fileId?alt=media")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("Authorization", "Bearer $tok")
                connectTimeout = 20000
                readTimeout = 20000
            }
            val raw = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            if (BackupKit.importJson(ctx, raw)) "Đã khôi phục cấu hình từ Drive"
            else "File Drive không đọc được"
        } catch (e: Exception) {
            e.message ?: "Không tải từ Drive"
        }
    }

    private fun listRemote(tok: String): List<Remote> {
        return try {
            val q = java.net.URLEncoder.encode("'appDataFolder' in parents", "UTF-8")
            val url = URL("$FILES?spaces=appDataFolder&fields=files(id,name)&q=$q")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("Authorization", "Bearer $tok")
            }
            val raw = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val arr = JSONObject(raw).optJSONArray("files") ?: JSONArray()
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                Remote(o.optString("id"), o.optString("name"))
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun delete(tok: String, id: String) {
        try {
            val conn = (URL("$FILES/$id").openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $tok")
            }
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {}
    }
}
