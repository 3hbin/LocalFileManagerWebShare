package com.localfm.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/*
 * Loading kiểu Material 3 Expressive: đường sóng và vòng tròn gợn sóng xoay.
 * Vẽ bằng Canvas nên không phụ thuộc phiên bản Material 3 (không cần nâng BOM).
 * progress < 0 = đang chờ (chạy liên tục); 0..1 = theo tiến trình.
 */

private const val TWO_PI_F = 6.2831855f
private const val WAVES = 6f

/** Thanh sóng ngang. */
@Composable
fun ExpressiveLinearProgress(
    progress: Float = -1f,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.24f),
    strokeWidth: Dp = 4.dp
) {
    val transition = rememberInfiniteTransition(label = "wavy-linear")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI_F,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "phase"
    )
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val amp = h * 0.32f
        val strokePx = strokeWidth.toPx()
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)

        fun wave(end: Float): Path {
            val p = Path()
            p.moveTo(0f, mid)
            var x = 0f
            while (x < end) {
                p.lineTo(x, mid + amp * sin(x / w * WAVES * TWO_PI_F + phase))
                x += 3f
            }
            p.lineTo(end, mid + amp * sin(end / w * WAVES * TWO_PI_F + phase))
            return p
        }

        drawLine(trackColor, Offset(0f, mid), Offset(w, mid), strokeWidth = strokePx, cap = StrokeCap.Round)
        val end = if (progress < 0f) w else w * progress.coerceIn(0f, 1f)
        drawPath(wave(end), color, style = stroke)
    }
}

/** Vòng tròn gợn sóng xoay. Kích thước lấy từ [modifier] (mặc định 40 dp). */
@Composable
fun ExpressiveCircularProgress(
    modifier: Modifier = Modifier.size(40.dp),
    progress: Float = -1f,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp
) {
    val transition = rememberInfiniteTransition(label = "wavy-circular")
    val rot by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "rot"
    )
    val wobble by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI_F,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "wobble"
    )
    Canvas(modifier.defaultMinSize(24.dp, 24.dp)) {
        val r = (minOf(size.width, size.height) / 2f) * 0.70f
        val amp = r * 0.12f
        val steps = 160
        val indeterminate = progress < 0f
        val last = if (indeterminate) steps else (steps * progress.coerceIn(0f, 1f)).toInt()
        val path = Path()
        for (i in 0..last) {
            val t = i.toFloat() / steps * TWO_PI_F
            val rr = r + amp * sin(7f * t + wobble)
            val x = center.x + rr * cos(t)
            val y = center.y + rr * sin(t)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (indeterminate) path.close()
        rotate(if (indeterminate) rot else -90f) {
            drawPath(path, color, style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round))
        }
    }
}
