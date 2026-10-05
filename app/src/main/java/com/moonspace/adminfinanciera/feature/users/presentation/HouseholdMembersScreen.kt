package com.moonspace.adminfinanciera.feature.users.presentation

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.components.FinanceButton
import com.moonspace.adminfinanciera.core.ui.components.FinanceButtonVariant
import com.moonspace.adminfinanciera.core.ui.components.FinanceCard
import com.moonspace.adminfinanciera.core.ui.components.FinanceLoadingState
import com.moonspace.adminfinanciera.core.ui.components.FinancePageTitle
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusBanner
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusPill
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusTone
import com.moonspace.adminfinanciera.core.ui.dialogs.FinanceBottomSheet
import com.moonspace.adminfinanciera.core.ui.dialogs.FinanceConfirmDialog
import com.moonspace.adminfinanciera.core.ui.forms.FinancePasswordField
import com.moonspace.adminfinanciera.core.ui.forms.FinanceTextField
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing
import com.moonspace.adminfinanciera.feature.auth.domain.AuthAccountMutation
import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdMember
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdRole

@Composable
fun HouseholdMembersScreen(
    user: AuthUser,
    state: HouseholdMembersUiState,
    isSigningOut: Boolean,
    isAccountUpdateInProgress: Boolean,
    accountUpdateErrorMessage: String?,
    accountUpdateNoticeMessage: String?,
    accountUpdateVersion: Int,
    pendingAccountMutation: AuthAccountMutation?,
    lastAccountMutation: AuthAccountMutation?,
    onSignOut: () -> Unit,
    onUpdateProfileName: (String) -> Unit,
    onChangePassword: (String, String) -> Unit,
    onClearAccountUpdateMessages: () -> Unit,
    onRefresh: () -> Unit,
    onCreateHousehold: (String) -> Unit,
    onCreateMember: (String, String, HouseholdRole) -> Unit,
    onUpdateRole: (String, HouseholdRole) -> Unit,
    onRemoveMember: (String) -> Unit
) {
    var isAddMemberOpen by remember { mutableStateOf(false) }
    var isAccountEditorOpen by rememberSaveable { mutableStateOf(false) }
    var selectedMemberForRole by remember { mutableStateOf<HouseholdMember?>(null) }
    var selectedMemberForRemoval by remember { mutableStateOf<HouseholdMember?>(null) }
    val snapshot = state.snapshot

    LaunchedEffect(state.noticeMessage) {
        if (state.noticeMessage != null) {
            isAddMemberOpen = false
            selectedMemberForRole = null
            selectedMemberForRemoval = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FinanceSpacing.ScreenHorizontal, vertical = FinanceSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Large)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FinanceButton(
                label = stringResource(R.string.users_sign_out),
                onClick = onSignOut,
                variant = FinanceButtonVariant.Secondary,
                enabled = !state.isSubmitting && !isSigningOut && !isAccountUpdateInProgress,
                isLoading = isSigningOut,
                loadingLabel = stringResource(R.string.users_signing_out)
            )
        }

        FinancePageTitle(title = stringResource(R.string.users_title))

        if (snapshot?.householdId != null && snapshot.currentUserRole == HouseholdRole.Admin) {
            FinanceButton(
                label = stringResource(R.string.users_add_action),
                onClick = { isAddMemberOpen = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting
            )
        }

        AccountProfileCard(
            user = user,
            onEdit = {
                onClearAccountUpdateMessages()
                isAccountEditorOpen = true
            }
        )

        state.noticeMessage?.let { message ->
            FinanceStatusBanner(message = message, tone = FinanceStatusTone.Success)
        }
        state.errorMessage?.let { message ->
            FinanceStatusBanner(message = message, tone = FinanceStatusTone.Error)
        }

        if (state.isLoading && snapshot == null) {
            FinanceLoadingState(message = stringResource(R.string.users_loading))
        } else if (snapshot == null) {
            FinanceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(FinanceSpacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)
                ) {
                    Text(
                        text = stringResource(R.string.users_load_empty_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    FinanceButton(
                        label = stringResource(R.string.users_refresh),
                        onClick = onRefresh,
                        variant = FinanceButtonVariant.Secondary,
                        enabled = !state.isLoading
                    )
                }
            }
        } else if (snapshot.householdId == null) {
            CreateHouseholdCard(
                isSubmitting = state.isSubmitting,
                onCreate = onCreateHousehold
            )
        } else if (snapshot.currentUserRole != HouseholdRole.Admin) {
            FinanceStatusBanner(
                message = stringResource(R.string.users_permission_denied),
                tone = FinanceStatusTone.Warning
            )
            snapshot.members.firstOrNull()?.let { member ->
                MemberCard(
                    member = member,
                    isCurrentUser = true,
                    adminCount = 0,
                    isSubmitting = state.isSubmitting,
                    onChangeRole = {},
                    onRemove = {}
                )
            }
        } else {
            AdminMembersContent(
                members = snapshot.members,
                currentUserId = user.id,
                isSubmitting = state.isSubmitting,
                onChangeRole = { selectedMemberForRole = it },
                onRemove = { selectedMemberForRemoval = it }
            )
        }
    }

    if (isAddMemberOpen) {
        CreateMemberSheet(
            isSubmitting = state.isSubmitting,
            errorMessage = state.errorMessage,
            onDismiss = { if (!state.isSubmitting) isAddMemberOpen = false },
            onCreate = onCreateMember
        )
    }

    if (isAccountEditorOpen) {
        AccountProfileSheet(
            user = user,
            isUpdating = isAccountUpdateInProgress,
            pendingMutation = pendingAccountMutation,
            updateErrorMessage = accountUpdateErrorMessage,
            updateNoticeMessage = accountUpdateNoticeMessage,
            updateVersion = accountUpdateVersion,
            lastMutation = lastAccountMutation,
            onDismiss = { if (!isAccountUpdateInProgress) isAccountEditorOpen = false },
            onUpdateName = onUpdateProfileName,
            onChangePassword = onChangePassword
        )
    }

    selectedMemberForRole?.let { member ->
        ChangeRoleSheet(
            member = member,
            isSubmitting = state.isSubmitting,
            errorMessage = state.errorMessage,
            onDismiss = { if (!state.isSubmitting) selectedMemberForRole = null },
            onSave = { role -> onUpdateRole(member.userId, role) }
        )
    }

    selectedMemberForRemoval?.let { member ->
        FinanceConfirmDialog(
            title = stringResource(R.string.users_remove_title),
            message = stringResource(R.string.users_remove_message, member.email),
            confirmLabel = stringResource(R.string.users_remove_action),
            dismissLabel = stringResource(R.string.ui_cancel),
            onConfirm = { onRemoveMember(member.userId) },
            onDismissRequest = { if (!state.isSubmitting) selectedMemberForRemoval = null },
            isDestructive = true,
            isProcessing = state.isSubmitting,
            processingLabel = stringResource(R.string.users_removing)
        )
    }
}

@Composable
private fun AccountProfileCard(
    user: AuthUser,
    onEdit: () -> Unit
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(FinanceSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
        ) {
            Text(
                text = stringResource(R.string.users_account_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = user.name.ifBlank { user.email.substringBefore('@') },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FinanceButton(
                label = stringResource(R.string.users_edit_account),
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth(),
                variant = FinanceButtonVariant.Secondary
            )
        }
    }
}

@Composable
private fun AccountProfileSheet(
    user: AuthUser,
    isUpdating: Boolean,
    pendingMutation: AuthAccountMutation?,
    updateErrorMessage: String?,
    updateNoticeMessage: String?,
    updateVersion: Int,
    lastMutation: AuthAccountMutation?,
    onDismiss: () -> Unit,
    onUpdateName: (String) -> Unit,
    onChangePassword: (String, String) -> Unit
) {
    var name by rememberSaveable(user.id) {
        mutableStateOf(user.name.ifBlank { user.email.substringBefore('@') })
    }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val normalizedName = name.trim()
    val passwordsMatch = newPassword == confirmation
    val passwordIsValid = currentPassword.isNotBlank() &&
        newPassword.length >= MIN_PASSWORD_LENGTH &&
        passwordsMatch &&
        newPassword != currentPassword

    LaunchedEffect(user.name) {
        name = user.name.ifBlank { user.email.substringBefore('@') }
    }
    LaunchedEffect(updateVersion, lastMutation) {
        when (lastMutation) {
            AuthAccountMutation.ProfileName -> name = user.name.ifBlank { user.email.substringBefore('@') }
            AuthAccountMutation.Password -> {
                currentPassword = ""
                newPassword = ""
                confirmation = ""
            }
            null -> Unit
        }
    }

    FinanceBottomSheet(
        title = stringResource(R.string.users_edit_account_title),
        onDismissRequest = onDismiss
    ) {
        FinanceTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.users_profile_name_label),
            enabled = !isUpdating,
            isError = normalizedName.isBlank(),
            supportingText = if (normalizedName.isBlank()) {
                stringResource(R.string.users_profile_name_required)
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
        FinanceTextField(
            value = user.email,
            onValueChange = {},
            label = stringResource(R.string.users_profile_email_label),
            enabled = false,
            readOnly = true,
            supportingText = stringResource(R.string.users_profile_email_read_only)
        )
        FinanceButton(
            label = stringResource(R.string.users_save_profile),
            onClick = { onUpdateName(normalizedName) },
            modifier = Modifier.fillMaxWidth(),
            variant = FinanceButtonVariant.Secondary,
            enabled = !isUpdating && normalizedName.isNotBlank() && normalizedName != user.name.trim(),
            isLoading = isUpdating && pendingMutation == AuthAccountMutation.ProfileName,
            loadingLabel = stringResource(R.string.users_saving_profile)
        )

        Spacer(Modifier.height(FinanceSpacing.Medium))
        Text(
            text = stringResource(R.string.users_change_password_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        FinancePasswordField(
            value = currentPassword,
            onValueChange = { currentPassword = it },
            label = stringResource(R.string.users_current_password_label),
            enabled = !isUpdating,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        FinancePasswordField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = stringResource(R.string.users_new_password_label),
            enabled = !isUpdating,
            isError = newPassword.isNotEmpty() && newPassword.length < MIN_PASSWORD_LENGTH,
            supportingText = if (newPassword.isNotEmpty() && newPassword.length < MIN_PASSWORD_LENGTH) {
                stringResource(R.string.auth_password_rule)
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        FinancePasswordField(
            value = confirmation,
            onValueChange = { confirmation = it },
            label = stringResource(R.string.auth_confirm_password_label),
            enabled = !isUpdating,
            isError = confirmation.isNotEmpty() && !passwordsMatch,
            supportingText = if (confirmation.isNotEmpty() && !passwordsMatch) {
                stringResource(R.string.auth_password_mismatch)
            } else if (newPassword.isNotEmpty() && newPassword == currentPassword) {
                stringResource(R.string.users_new_password_must_differ)
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
        updateNoticeMessage?.let { FinanceStatusBanner(it, tone = FinanceStatusTone.Success) }
        updateErrorMessage?.let { FinanceStatusBanner(it, tone = FinanceStatusTone.Error) }
        FinanceButton(
            label = stringResource(R.string.users_save_password),
            onClick = { onChangePassword(currentPassword, newPassword) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isUpdating && passwordIsValid,
            isLoading = isUpdating && pendingMutation == AuthAccountMutation.Password,
            loadingLabel = stringResource(R.string.users_changing_password)
        )
    }
}

@Composable
private fun AdminMembersContent(
    members: List<HouseholdMember>,
    currentUserId: String,
    isSubmitting: Boolean,
    onChangeRole: (HouseholdMember) -> Unit,
    onRemove: (HouseholdMember) -> Unit
) {
    val adminCount = members.count { it.role == HouseholdRole.Admin }
    Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)) {
        Text(
            text = stringResource(R.string.users_members_count, members.size),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        members.forEach { member ->
            MemberCard(
                member = member,
                isCurrentUser = member.userId == currentUserId,
                adminCount = adminCount,
                isSubmitting = isSubmitting,
                onChangeRole = { onChangeRole(member) },
                onRemove = { onRemove(member) }
            )
        }
    }
}

@Composable
private fun MemberCard(
    member: HouseholdMember,
    isCurrentUser: Boolean,
    adminCount: Int,
    isSubmitting: Boolean,
    onChangeRole: () -> Unit,
    onRemove: () -> Unit
) {
    val isOnlyAdmin = isCurrentUser && member.role == HouseholdRole.Admin && adminCount <= 1
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(FinanceSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
        ) {
            Text(
                text = member.email,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)) {
                FinanceStatusPill(
                    label = stringResource(
                        if (member.role == HouseholdRole.Admin) R.string.users_role_admin
                        else R.string.users_role_member
                    ),
                    tone = if (member.role == HouseholdRole.Admin) FinanceStatusTone.Success
                    else FinanceStatusTone.Info
                )
                if (isCurrentUser) {
                    FinanceStatusPill(
                        label = stringResource(R.string.users_you),
                        tone = FinanceStatusTone.Info
                    )
                }
            }
            if (!isOnlyAdmin) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
                ) {
                    FinanceButton(
                        label = stringResource(R.string.users_change_role),
                        onClick = onChangeRole,
                        modifier = Modifier.weight(1f),
                        variant = FinanceButtonVariant.Secondary,
                        enabled = !isSubmitting
                    )
                    FinanceButton(
                        label = stringResource(R.string.users_remove_action),
                        onClick = onRemove,
                        modifier = Modifier.weight(1f),
                        variant = FinanceButtonVariant.Destructive,
                        enabled = !isSubmitting
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.users_only_admin_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CreateHouseholdCard(
    isSubmitting: Boolean,
    onCreate: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("Finanzas en pareja") }
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(FinanceSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)
        ) {
            Text(
                text = stringResource(R.string.users_create_space_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.users_create_space_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FinanceTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.users_space_name_label),
                enabled = !isSubmitting,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
            FinanceButton(
                label = stringResource(R.string.users_create_space_action),
                onClick = { onCreate(name) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
                isLoading = isSubmitting,
                loadingLabel = stringResource(R.string.users_creating_space)
            )
        }
    }
}

@Composable
private fun CreateMemberSheet(
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onCreate: (String, String, HouseholdRole) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(HouseholdRole.Member) }
    var emailTouched by remember { mutableStateOf(false) }
    val normalizedEmail = email.trim()
    val validEmail = Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()
    val passwordsMatch = password == confirmation
    val canSubmit = validEmail && password.length >= MIN_PASSWORD_LENGTH && passwordsMatch && !isSubmitting

    FinanceBottomSheet(
        title = stringResource(R.string.users_add_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)
        ) {
            Text(
                text = stringResource(R.string.users_add_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                )
            )
            FinancePasswordField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.users_initial_password_label),
                enabled = !isSubmitting,
                isError = password.isNotEmpty() && password.length < MIN_PASSWORD_LENGTH,
                supportingText = stringResource(R.string.auth_password_rule)
            )
            FinancePasswordField(
                value = confirmation,
                onValueChange = { confirmation = it },
                label = stringResource(R.string.auth_confirm_password_label),
                enabled = !isSubmitting,
                isError = confirmation.isNotEmpty() && !passwordsMatch,
                supportingText = if (confirmation.isNotEmpty() && !passwordsMatch) {
                    stringResource(R.string.auth_password_mismatch)
                } else null,
                showVisibilityControl = false
            )
            Text(
                text = stringResource(R.string.users_initial_password_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.users_role_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)) {
                FinanceButton(
                    label = stringResource(R.string.users_role_member),
                    onClick = { role = HouseholdRole.Member },
                    modifier = Modifier.weight(1f),
                    variant = if (role == HouseholdRole.Member) FinanceButtonVariant.Primary
                    else FinanceButtonVariant.Secondary,
                    enabled = !isSubmitting
                )
                FinanceButton(
                    label = stringResource(R.string.users_role_admin),
                    onClick = { role = HouseholdRole.Admin },
                    modifier = Modifier.weight(1f),
                    variant = if (role == HouseholdRole.Admin) FinanceButtonVariant.Primary
                    else FinanceButtonVariant.Secondary,
                    enabled = !isSubmitting
                )
            }
            errorMessage?.let { message ->
                FinanceStatusBanner(message = message, tone = FinanceStatusTone.Error)
            }
            FinanceButton(
                label = stringResource(R.string.users_create_action),
                onClick = { onCreate(normalizedEmail, password, role) },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSubmit,
                isLoading = isSubmitting,
                loadingLabel = stringResource(R.string.users_creating)
            )
        }
    }
}

@Composable
private fun ChangeRoleSheet(
    member: HouseholdMember,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (HouseholdRole) -> Unit
) {
    var role by remember(member.userId) { mutableStateOf(member.role) }
    FinanceBottomSheet(
        title = stringResource(R.string.users_change_role_title),
        onDismissRequest = onDismiss
    ) {
        Text(
            text = stringResource(R.string.users_change_role_description, member.email),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(FinanceSpacing.Medium))
        Row(horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)) {
            FinanceButton(
                label = stringResource(R.string.users_role_member),
                onClick = { role = HouseholdRole.Member },
                modifier = Modifier.weight(1f),
                variant = if (role == HouseholdRole.Member) FinanceButtonVariant.Primary
                else FinanceButtonVariant.Secondary,
                enabled = !isSubmitting
            )
            FinanceButton(
                label = stringResource(R.string.users_role_admin),
                onClick = { role = HouseholdRole.Admin },
                modifier = Modifier.weight(1f),
                variant = if (role == HouseholdRole.Admin) FinanceButtonVariant.Primary
                else FinanceButtonVariant.Secondary,
                enabled = !isSubmitting
            )
        }
        Text(
            text = stringResource(
                if (role == HouseholdRole.Admin) R.string.users_admin_permissions_note
                else R.string.users_member_permissions_note
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        errorMessage?.let { message ->
            FinanceStatusBanner(message = message, tone = FinanceStatusTone.Error)
        }
        FinanceButton(
            label = stringResource(R.string.users_save_role),
            onClick = { onSave(role) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSubmitting && role != member.role,
            isLoading = isSubmitting,
            loadingLabel = stringResource(R.string.users_saving_role)
        )
    }
}

private const val MIN_PASSWORD_LENGTH = 8
