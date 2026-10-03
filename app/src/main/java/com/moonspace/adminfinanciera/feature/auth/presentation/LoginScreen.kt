package com.moonspace.adminfinanciera.feature.auth.presentation

import android.util.Patterns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.moonspace.adminfinanciera.R

@Composable
fun LoginScreen(
    hasLocalAccount: Boolean,
    isSubmitting: Boolean,
    errorMessage: String?,
    onCreateLocalAccount: (String, String) -> Unit,
    onSignIn: (String, String) -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordConfirmation by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var emailTouched by rememberSaveable { mutableStateOf(false) }
    var passwordConfirmationTouched by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val passwordFocusRequester = remember { FocusRequester() }
    val creatingAccount = !hasLocalAccount
    val validEmail = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val validPassword = if (creatingAccount) password.length >= MIN_PASSWORD_LENGTH else password.isNotEmpty()
    val passwordsMatch = !creatingAccount || password == passwordConfirmation
    val canSubmit = !isSubmitting && validEmail && validPassword && passwordsMatch
    val onSubmit: () -> Unit = {
        if (canSubmit) {
            focusManager.clearFocus()
            if (creatingAccount) onCreateLocalAccount(email.trim(), password)
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
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.login_eyebrow),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(if (creatingAccount) R.string.local_create_title else R.string.login_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(if (creatingAccount) R.string.local_create_description else R.string.login_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    EmailField(
                        value = email,
                        enabled = !isSubmitting,
                        nextFocusRequester = passwordFocusRequester,
                        onValueChange = {
                            email = it
                            emailTouched = true
                        }
                    )
                    if (emailTouched && email.isNotBlank() && !validEmail) {
                        Text(
                            text = stringResource(R.string.login_invalid_email),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    PasswordField(
                        value = password,
                        enabled = !isSubmitting,
                        visible = passwordVisible,
                        focusRequester = passwordFocusRequester,
                        label = stringResource(R.string.login_password_label),
                        showVisibilityControl = !creatingAccount,
                        onToggleVisibility = { passwordVisible = !passwordVisible },
                        onValueChange = { password = it },
                        onDone = onSubmit
                    )

                    if (creatingAccount) {
                        OutlinedTextField(
                            value = passwordConfirmation,
                            onValueChange = {
                                passwordConfirmation = it
                                passwordConfirmationTouched = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSubmitting,
                            label = { Text(stringResource(R.string.local_confirm_password_label)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { onSubmit() })
                        )
                        Text(
                            text = stringResource(R.string.local_password_rule),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (passwordConfirmationTouched && passwordConfirmation.isNotEmpty() && !passwordsMatch) {
                            Text(
                                text = stringResource(R.string.local_password_mismatch),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = onSubmit,
                        enabled = canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSubmitting) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Text(stringResource(R.string.login_submitting))
                            }
                        } else {
                            Text(
                                stringResource(
                                    if (creatingAccount) R.string.local_create_action
                                    else R.string.login_submit
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.local_only_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmailField(
    value: String,
    enabled: Boolean,
    nextFocusRequester: FocusRequester,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(R.string.login_email_label)) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            autoCorrectEnabled = false
        ),
        keyboardActions = KeyboardActions(onNext = { nextFocusRequester.requestFocus() })
    )
}

@Composable
private fun PasswordField(
    value: String,
    enabled: Boolean,
    visible: Boolean,
    focusRequester: FocusRequester,
    label: String,
    showVisibilityControl: Boolean,
    onToggleVisibility: () -> Unit,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = if (showVisibilityControl) {
            {
                TextButton(
                    onClick = onToggleVisibility,
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(if (visible) R.string.login_hide_password else R.string.login_show_password))
                }
            }
        } else null
    )
}

private const val MIN_PASSWORD_LENGTH = 8
