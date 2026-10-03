package com.moonspace.adminfinanciera.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.moonspace.adminfinanciera.core.ui.theme.FinanceShapes

@Composable
fun FinanceCard(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    outlined: Boolean = true,
    shape: Shape = FinanceShapes.Card,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor ?: colorScheme.surface,
        border = if (outlined) BorderStroke(1.dp, colorScheme.outlineVariant) else null,
        tonalElevation = 0.dp,
        content = content
    )
}
