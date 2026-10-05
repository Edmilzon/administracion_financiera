package com.moonspace.adminfinanciera.core.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

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
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = androidx.compose.ui.unit.Dp.Hairline,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        destinations.forEach { destination ->
            val selected = selectedDestination == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { onDestinationSelected(destination.route) },
                icon = {
                    Icon(
                        painter = painterResource(destination.iconRes),
                        contentDescription = stringResource(destination.labelRes)
                    )
                },
                label = null,
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
