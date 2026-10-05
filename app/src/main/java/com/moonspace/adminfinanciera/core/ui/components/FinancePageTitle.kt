package com.moonspace.adminfinanciera.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moonspace.adminfinanciera.core.ui.theme.FinanceShapes
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing

@Composable
fun FinancePageTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    val shape = FinanceShapes.Panel
    val glassBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f),
            MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
        )
    )
    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
            MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = shape)
            .clip(shape)
            .background(brush = glassBrush, shape = shape)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .padding(horizontal = FinanceSpacing.Medium, vertical = FinanceSpacing.Compact)
            .semantics { heading() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.50f),
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    ),
                    shape = RoundedCornerShape(50)
                )
        )
        Text(
            text = title,
            modifier = Modifier.padding(top = FinanceSpacing.Small),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
