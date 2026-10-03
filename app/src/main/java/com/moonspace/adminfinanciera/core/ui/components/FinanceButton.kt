package com.moonspace.adminfinanciera.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moonspace.adminfinanciera.core.ui.theme.FinanceShapes
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing

enum class FinanceButtonVariant {
    Primary,
    Secondary,
    Text,
    Destructive
}

@Composable
fun FinanceButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FinanceButtonVariant = FinanceButtonVariant.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingLabel: String = label
) {
    val buttonModifier = modifier.heightIn(min = FinanceSpacing.ButtonHeight)
    val canClick = enabled && !isLoading

    when (variant) {
        FinanceButtonVariant.Primary -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = canClick,
            shape = FinanceShapes.Control
        ) {
            ButtonContent(label = label, loadingLabel = loadingLabel, isLoading = isLoading)
        }

        FinanceButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = canClick,
            shape = FinanceShapes.Control
        ) {
            ButtonContent(label = label, loadingLabel = loadingLabel, isLoading = isLoading)
        }

        FinanceButtonVariant.Text -> TextButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = canClick,
            shape = FinanceShapes.Control
        ) {
            ButtonContent(label = label, loadingLabel = loadingLabel, isLoading = isLoading)
        }

        FinanceButtonVariant.Destructive -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = canClick,
            shape = FinanceShapes.Control,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            ButtonContent(label = label, loadingLabel = loadingLabel, isLoading = isLoading)
        }
    }
}

@Composable
private fun ButtonContent(label: String, loadingLabel: String, isLoading: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp
            )
            Text(loadingLabel)
        } else {
            Text(label)
        }
    }
}

@Composable
fun FinanceIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(
                minWidth = FinanceSpacing.MinimumTouchTarget,
                minHeight = FinanceSpacing.MinimumTouchTarget
            )
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        enabled = enabled,
    ) {
        content()
    }
}
