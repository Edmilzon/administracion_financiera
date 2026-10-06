package com.moonspace.adminfinanciera.feature.auth.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moonspace.adminfinanciera.core.ui.theme.FinanceShapes
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing

@Composable
fun LoginGlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = FinanceShapes.Panel
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    colorScheme.onSurface.copy(alpha = GLASS_BORDER_ALPHA),
                    colorScheme.primary.copy(alpha = 0.42f),
                    colorScheme.onSurface.copy(alpha = 0.08f)
                )
            )
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            colorScheme.surface.copy(alpha = 0.86f),
                            colorScheme.surfaceVariant.copy(alpha = 0.72f),
                            colorScheme.surface.copy(alpha = 0.78f)
                        )
                    )
                )
                .padding(FinanceSpacing.Large),
            content = { content() }
        )
    }
}

private const val GLASS_BORDER_ALPHA = 0.18f
