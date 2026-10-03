package com.moonspace.adminfinanciera.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColorScheme = lightColorScheme(
    primary = FinanceColors.Primary,
    onPrimary = FinanceColors.OnPrimary,
    primaryContainer = FinanceColors.PrimaryContainer,
    onPrimaryContainer = FinanceColors.OnPrimaryContainer,
    secondary = FinanceColors.Secondary,
    background = FinanceColors.Background,
    surface = FinanceColors.Surface,
    surfaceVariant = FinanceColors.SurfaceVariant,
    onSurface = FinanceColors.OnSurface,
    onSurfaceVariant = FinanceColors.OnSurfaceVariant,
    outline = FinanceColors.Outline,
    outlineVariant = FinanceColors.OutlineVariant,
    error = FinanceColors.Expense,
    errorContainer = FinanceColors.ExpenseContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = FinanceColors.DarkPrimary,
    onPrimary = FinanceColors.DarkOnPrimary,
    primaryContainer = FinanceColors.DarkPrimaryContainer,
    onPrimaryContainer = FinanceColors.DarkOnPrimaryContainer,
    secondary = FinanceColors.DarkSecondary,
    background = FinanceColors.DarkBackground,
    surface = FinanceColors.DarkSurface,
    surfaceVariant = FinanceColors.DarkSurfaceVariant,
    onSurface = FinanceColors.DarkOnSurface,
    onSurfaceVariant = FinanceColors.DarkOnSurfaceVariant,
    outline = FinanceColors.DarkOutline,
    outlineVariant = FinanceColors.DarkOutlineVariant,
    error = FinanceColors.DarkExpense,
    errorContainer = FinanceColors.DarkExpenseContainer
)

@Composable
fun FinanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalFinanceSemanticColors provides if (darkTheme) {
            DarkFinanceSemanticColors
        } else {
            LightFinanceSemanticColors
        }
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = FinanceTypography,
            content = content
        )
    }
}
