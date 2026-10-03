package com.moonspace.adminfinanciera.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSemanticColorScheme
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing
import com.moonspace.adminfinanciera.core.ui.theme.FinanceShapes

enum class FinanceStatusTone {
    Info,
    Success,
    Warning,
    Error
}

@Composable
fun FinanceStatusBanner(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: FinanceStatusTone = FinanceStatusTone.Info
) {
    val palette = FinanceSemanticColorScheme
    val (container, content) = when (tone) {
        FinanceStatusTone.Info -> palette.infoContainer to palette.onInfoContainer
        FinanceStatusTone.Success -> palette.successContainer to palette.onSuccessContainer
        FinanceStatusTone.Warning -> palette.warningContainer to palette.onWarningContainer
        FinanceStatusTone.Error -> palette.expenseContainer to palette.onExpenseContainer
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = FinanceShapes.Control,
        color = container
    ) {
        Column(
            modifier = Modifier.padding(FinanceSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = content
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = content
            )
        }
    }
}
