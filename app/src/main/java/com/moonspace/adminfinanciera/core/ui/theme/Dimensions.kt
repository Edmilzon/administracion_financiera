package com.moonspace.adminfinanciera.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object FinanceSpacing {
    val XSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Compact: Dp = 12.dp
    val Medium: Dp = 16.dp
    val Large: Dp = 24.dp
    val XLarge: Dp = 32.dp

    val ScreenHorizontal: Dp = 16.dp
    val CardPadding: Dp = 16.dp
    val MinimumTouchTarget: Dp = 48.dp
    val ButtonHeight: Dp = 48.dp
}

object FinanceShapes {
    val Control: Shape = RoundedCornerShape(12.dp)
    val Card: Shape = RoundedCornerShape(12.dp)
    val Panel: Shape = RoundedCornerShape(16.dp)
    val Sheet: Shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val Pill: Shape = RoundedCornerShape(50)
}
