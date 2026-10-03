package com.moonspace.adminfinanciera.feature.auth.presentation

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.components.FinanceButton
import com.moonspace.adminfinanciera.core.ui.components.FinanceCard
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusBanner
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusTone
import com.moonspace.adminfinanciera.core.ui.forms.FinancePasswordField
import com.moonspace.adminfinanciera.core.ui.forms.FinanceTextField
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing

@Composable
fun LoginScreen(
    isAuthConfigured: Boolean,
    isSubmitting: Boolean,
    errorMessage: String?,
    noticeMessage: String?,
    onCreateAccount: (String, String) -> Unit,
    onSignIn: (String, String) -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordConfirmation by rememberSaveable { mutableStateOf("") }
    var creatingAccount by rememberSaveable { mutableStateOf(false) }
    var emailTouched by rememberSaveable { mutableStateOf(false) }
    var passwordConfirmationTouched by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val passwordFocusRequester = remember { FocusRequester() }
    val confirmationFocusRequester = remember { FocusRequester() }
    val validEmail = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passwordTooShort = creatingAccount && password.isNotEmpty() && password.length < MIN_PASSWORD_LENGTH
    val passwordsMatch = !creatingAccount || password == passwordConfirmation
    val canSubmit = isAuthConfigured && !isSubmitting && validEmail && password.isNotEmpty() &&
        (!creatingAccount || (password.length >= MIN_PASSWORD_LENGTH && passwordsMatch))
    val onSubmit: () -> Unit = {
        if (canSubmit) {
            focusManager.clearFocus()
            if (creatingAccount) onCreateAccount(email.trim(), password)
            else onSignIn(email.trim(), password)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FinanceSpacing.ScreenHorizontal, vertical = FinanceSpacing.Large),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.login_eyebrow),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(FinanceSpacing.Small))
            Text(
                text = stringResource(if (creatingAccount) R.string.auth_create_title else R.string.login_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(FinanceSpacing.XSmall))
            Text(
                text = stringResource(if (creatingAccount) R.string.auth_create_description else R.string.login_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(FinanceSpacing.Large))

            FinanceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(FinanceSpacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)
                ) {
                    FinanceTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            emailTouched = true
                        },
                        label = stringResource(R.string.login_email_label),
                        enabled = !isSubmitting,
                        isError = emailTouched && email.isNotBlank() && !validEmail,
                        supportingText = if (emailTouched && email.isNotBlank() && !validEmail) {
                            stringResource(R.string.login_invalid_email)
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                            autoCorrectEnabled = false
                        ),
                        keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() })
                    )

                    FinancePasswordField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.login_password_label),
                        modifier = Modifier.focusRequester(passwordFocusRequester),
                        enabled = !isSubmitting,
                        isError = passwordTooShort,
                        supportingText = if (creatingAccount) stringResource(R.string.auth_password_rule) else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (creatingAccount) ImeAction.Next else ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { if (creatingAccount) confirmationFocusRequester.requestFocus() },
                            onDone = { onSubmit() }
                        )
                    )

                    if (creatingAccount) {
                        FinancePasswordField(
                            value = passwordConfirmation,
                            onValueChange = {
                                passwordConfirmation = it
                                passwordConfirmationTouched = true
                            },
                            label = stringResource(R.string.auth_confirm_password_label),
                            modifier = Modifier.focusRequester(confirmationFocusRequester),
                            enabled = !isSubmitting,
                            isError = passwordConfirmationTouched && passwordConfirmation.isNotEmpty() && !passwordsMatch,
                            supportingText = if (passwordConfirmationTouched && passwordConfirmation.isNotEmpty() && !passwordsMatch) {
                                stringResource(R.string.auth_password_mismatch)
                            } else null,
                            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                            showVisibilityControl = false
                        )
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    FinanceButton(
                        label = stringResource(
                            if (creatingAccount) R.string.auth_create_action else R.string.login_submit
                        ),
                        onClick = onSubmit,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canSubmit,
                        isLoading = isSubmitting,
                        loadingLabel = stringResource(R.string.login_submitting)
                    )
                }
            }

            if (noticeMessage != null) {
                Spacer(Modifier.height(FinanceSpacing.Medium))
                FinanceStatusBanner(
                    message = noticeMessage,
                    tone = FinanceStatusTone.Info
                )
            }

            Spacer(Modifier.height(FinanceSpacing.Small))
            TextButton(
                onClick = {
                    creatingAccount = !creatingAccount
                    password = ""
                    passwordConfirmation = ""
                    passwordConfirmationTouched = false
                },
                enabled = !isSubmitting
            ) {
                Text(
                    text = stringResource(
                        if (creatingAccount) R.string.auth_switch_to_sign_in
                        else R.string.auth_switch_to_create
                    )
                )
            }
        }
    }
}

private const val MIN_PASSWORD_LENGTH = 8
