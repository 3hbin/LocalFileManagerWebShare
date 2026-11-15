package com.localfm.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Bật Material You (màu theo hình nền) trên Android 12+. Tắt = dùng bảng màu thương hiệu bên dưới. */
private const val USE_DYNAMIC_COLOR = true

/**
 * Bảng màu tối giản, thiên xanh — gần với phong cách icon ứng dụng.
 * Hỗ trợ Dark Mode theo hệ thống.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6FED),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF2A9B8F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC8F0EA),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF6B5B95),
    background = Color(0xFFF3F6FB),
    onBackground = Color(0xFF12161D),
    surface = Color(0xFFF8FAFD),
    onSurface = Color(0xFF12161D),
    surfaceVariant = Color(0xFFE4EAF3),
    onSurfaceVariant = Color(0xFF424753),
    outline = Color(0xFF72788A),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF1B4AAD),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF8CD5CB),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF145047),
    onSecondaryContainer = Color(0xFFC8F0EA),
    tertiary = Color(0xFFCBBDF0),
    background = Color(0xFF0E1218),
    onBackground = Color(0xFFE4E8F0),
    surface = Color(0xFF151A22),
    onSurface = Color(0xFFE4E8F0),
    surfaceVariant = Color(0xFF2A303A),
    onSurfaceVariant = Color(0xFFC2C8D4),
    outline = Color(0xFF8C93A3),
    error = Color(0xFFFFB4AB)
)

@Composable
fun LocalFileManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        USE_DYNAMIC_COLOR && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
