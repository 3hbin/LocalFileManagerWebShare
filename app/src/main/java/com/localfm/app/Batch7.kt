package com.localfm.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.Normalizer
import java.util.Locale

/** Tên đẹp cho link Web Share, ví dụ http://192.168.1.3:8080/kho-file-nha-minh */
object ShareName {
    private const val P = "local_fm_prefs"
    private const val KEY = "share_alias"

    /** Các đường dẫn đã dùng cho chức năng, không cho đặt làm tên link. */
    val reserved = setOf("browse", "download", "zip", "upload", "zip-selected", "login", "s", "style-css")

    /** Tên đang dùng (chuỗi rỗng = chỉ dùng địa chỉ IP). */
    var alias by mutableStateOf("")
    private var loaded = false

    fun stored(ctx: Context): String =
        ctx.applicationContext.getSharedPreferences(P, 0).getString(KEY, "") ?: ""

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        alias = stored(ctx)
    }

    fun set(ctx: Context, slug: String) {
        alias = slug
        ctx.applicationContext.getSharedPreferences(P, 0).edit().putString(KEY, slug).apply()
    }

    /** "Kho file nhà mình" -> "kho-file-nha-minh". Bỏ dấu, chỉ giữ chữ thường, số và dấu gạch ngang. */
    fun slugify(raw: String): String {
        val noMarks = Normalizer.normalize(raw.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noMarks.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .trim('-')
    }

    /** Địa chỉ đầy đủ: có tên thì thêm /tên, không có thì giữ nguyên địa chỉ IP. */
    fun url(base: String): String = if (alias.isBlank()) base else "$base/$alias"
}

@Composable
fun ShareNameDialog(baseUrl: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(ShareName.alias) }
    val slug = ShareName.slugify(text)
    val error = when {
        text.isBlank() -> null
        slug.length < 3 -> "Cần ít nhất 3 chữ cái hoặc số"
        slug in ShareName.reserved -> "Tên này dùng cho chức năng khác"
        else -> null
    }
    val preview = if (text.isBlank() || error != null) baseUrl else "$baseUrl/$slug"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tên link Web Share") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Đặt tên ngắn cho link, ví dụ \"Kho file nhà mình\" sẽ thành /kho-file-nha-minh.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Tên link") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Link sẽ là: $preview", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null,
                onClick = {
                    val saved = if (text.isBlank()) "" else slug
                    ShareName.set(ctx, saved)
                    ServerBridge.server?.alias = saved
                    OpLog.add(if (saved.isBlank()) "Dùng địa chỉ IP cho Web Share" else "Đặt tên link: /$saved")
                    onDismiss()
                }
            ) { Text("Lưu") }
        },
        dismissButton = {
            TextButton(onClick = {
                ShareName.set(ctx, "")
                ServerBridge.server?.alias = ""
                onDismiss()
            }) { Text("Dùng địa chỉ IP") }
        }
    )
}
