package com.moonspace.adminfinanciera.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class FinanceSemanticColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color
)

val LightFinanceSemanticColors = FinanceSemanticColors(
    success = FinanceColors.Success,
    successContainer = FinanceColors.SuccessContainer,
    onSuccessContainer = FinanceColors.OnSuccessContainer,
    expense = FinanceColors.Expense,
    expenseContainer = FinanceColors.ExpenseContainer,
    onExpenseContainer = FinanceColors.OnExpenseContainer,
    warning = FinanceColors.Warning,
    warningContainer = FinanceColors.WarningContainer,
    onWarningContainer = FinanceColors.OnWarningContainer,
    info = FinanceColors.Info,
    infoContainer = FinanceColors.InfoContainer,
    onInfoContainer = FinanceColors.OnInfoContainer
)

val DarkFinanceSemanticColors = FinanceSemanticColors(
    success = FinanceColors.DarkSuccess,
    successContainer = FinanceColors.DarkSuccessContainer,
    onSuccessContainer = FinanceColors.DarkOnSuccessContainer,
    expense = FinanceColors.DarkExpense,
    expenseContainer = FinanceColors.DarkExpenseContainer,
    onExpenseContainer = FinanceColors.DarkOnExpenseContainer,
    warning = FinanceColors.DarkWarning,
    warningContainer = FinanceColors.DarkWarningContainer,
    onWarningContainer = FinanceColors.DarkOnWarningContainer,
    info = FinanceColors.DarkInfo,
    infoContainer = FinanceColors.DarkInfoContainer,
    onInfoContainer = FinanceColors.DarkOnInfoContainer
)

val LocalFinanceSemanticColors = staticCompositionLocalOf { LightFinanceSemanticColors }

val FinanceSemanticColorScheme: FinanceSemanticColors
    @Composable get() = LocalFinanceSemanticColors.current
