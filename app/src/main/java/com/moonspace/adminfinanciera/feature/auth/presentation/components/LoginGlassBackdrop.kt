package com.moonspace.adminfinanciera.feature.auth.presentation.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun LoginGlassBackdrop(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) > 0f
        }.getOrDefault(true)
    }
    val progress = rememberLoginMotionProgress(animationsEnabled)
    val colorScheme = MaterialTheme.colorScheme

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val phase = progress * (2f * PI.toFloat())
        val primary = colorScheme.primary
        val secondary = colorScheme.secondary
        val firstCenter = Offset(
            x = width * (0.10f + progress * 0.48f),
            y = height * (0.20f + sin(phase) * 0.075f)
        )
        val secondCenter = Offset(
            x = width * (0.94f - progress * 0.50f),
            y = height * (0.72f + sin(phase + 1.7f) * 0.08f)
        )
        val thirdCenter = Offset(
            x = width * (0.48f + sin(phase + 0.8f) * 0.26f),
            y = height * (0.48f + sin(phase + 2.2f) * 0.12f)
        )
        val largeRadius = width * 0.92f

        drawRect(color = colorScheme.background)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.30f), primary.copy(alpha = 0.08f), Color.Transparent),
                center = firstCenter,
                radius = largeRadius
            ),
            center = firstCenter,
            radius = largeRadius
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondary.copy(alpha = 0.26f), secondary.copy(alpha = 0.07f), Color.Transparent),
                center = secondCenter,
                radius = largeRadius * 0.86f
            ),
            center = secondCenter,
            radius = largeRadius * 0.86f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.18f), Color.Transparent),
                center = thirdCenter,
                radius = largeRadius * 0.58f
            ),
            center = thirdCenter,
            radius = largeRadius * 0.58f
        )

        val upperReflection = Path().apply {
            moveTo(-width * 0.12f, height * (0.33f + sin(phase) * 0.025f))
            cubicTo(
                width * 0.28f,
                height * 0.25f,
                width * 0.68f,
                height * 0.42f,
                width * 1.12f,
                height * (0.31f + sin(phase + 1f) * 0.025f)
            )
        }
        drawPath(
            path = upperReflection,
            color = colorScheme.onSurface.copy(alpha = 0.08f),
            style = Stroke(width = 1.dp.toPx())
        )

        val lowerReflection = Path().apply {
            moveTo(-width * 0.10f, height * (0.78f + sin(phase + 2f) * 0.02f))
            cubicTo(
                width * 0.32f,
                height * 0.68f,
                width * 0.64f,
                height * 0.88f,
                width * 1.10f,
                height * (0.76f + sin(phase + 3f) * 0.02f)
            )
        }
        drawPath(
            path = lowerReflection,
            color = secondary.copy(alpha = 0.11f),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

@Composable
private fun rememberLoginMotionProgress(enabled: Boolean): Float {
    if (!enabled) return 0f
    val transition = rememberInfiniteTransition(label = "login_glass_background")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = LOGIN_BACKGROUND_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "login_glass_background_progress"
    )
    return progress
}

private const val LOGIN_BACKGROUND_CYCLE_MS = 26_000
