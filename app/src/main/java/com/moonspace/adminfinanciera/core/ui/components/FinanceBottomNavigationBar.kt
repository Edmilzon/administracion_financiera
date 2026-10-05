package com.moonspace.adminfinanciera.core.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moonspace.adminfinanciera.core.ui.theme.FinanceShapes
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing

data class FinanceNavigationDestination(
    val route: String,
    @param:StringRes
    @field:StringRes val labelRes: Int,
    @param:DrawableRes
    @field:DrawableRes val iconRes: Int
)

@Composable
fun FinanceBottomNavigationBar(
    selectedDestination: String,
    destinations: List<FinanceNavigationDestination>,
    onDestinationSelected: (String) -> Unit
) {
    val shape = FinanceShapes.Pill
    val glassBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
        )
    )
    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
            MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = FinanceSpacing.Medium, vertical = FinanceSpacing.XSmall)
            .shadow(elevation = FinanceSpacing.Small, shape = shape)
            .clip(shape)
            .background(brush = glassBrush, shape = shape)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .padding(horizontal = FinanceSpacing.XSmall, vertical = FinanceSpacing.XSmall),
        horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        destinations.forEach { destination ->
            val isSelected = selectedDestination == destination.route
            val label = stringResource(destination.labelRes)
            val indicatorBrush = if (isSelected) {
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.66f)
                    )
                )
            } else {
                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = FinanceSpacing.MinimumTouchTarget)
                    .clip(CircleShape)
                    .background(brush = indicatorBrush, shape = CircleShape)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape
                    )
                    .clickable(
                        role = Role.Tab,
                        onClick = { onDestinationSelected(destination.route) }
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = label
                        selected = isSelected
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(destination.iconRes),
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
