package br.uff.hist.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.uff.hist.R
import br.uff.hist.auth.AuthTextField
import br.uff.hist.core.designsystem.ThemePreference

class SettingsActions(
    val onBack: () -> Unit,
    val onTheme: (ThemePreference) -> Unit,
    val onOpenDialog: (SettingsDialog) -> Unit,
    val onLogout: () -> Unit,
    val onCloseDialog: () -> Unit,
    val onName: (String) -> Unit,
    val onLastName: (String) -> Unit,
    val onEmail: (String) -> Unit,
    val onCurrentPassword: (String) -> Unit,
    val onNewPassword: (String) -> Unit,
    val onSubmit: () -> Unit,
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onBack) {
        SettingsActions(
            onBack = onBack, onTheme = viewModel::setTheme, onOpenDialog = viewModel::openDialog,
            onLogout = viewModel::logout, onCloseDialog = viewModel::closeDialog, onName = viewModel::onName,
            onLastName = viewModel::onLastName, onEmail = viewModel::onEmail,
            onCurrentPassword = viewModel::onCurrentPassword, onNewPassword = viewModel::onNewPassword,
            onSubmit = viewModel::submit,
        )
    }
    SettingsContent(state, actions, modifier, onMessageShown = viewModel::messageShown)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    onMessageShown: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionTitle(R.string.settings_appearance)
            ThemeOption(R.string.theme_light, ThemePreference.LIGHT, state.theme, actions.onTheme)
            ThemeOption(R.string.theme_dark, ThemePreference.DARK, state.theme, actions.onTheme)
            ThemeOption(R.string.theme_system, ThemePreference.SYSTEM, state.theme, actions.onTheme)
            HorizontalDivider()
            SectionTitle(R.string.settings_account)
            ActionRow(R.string.settings_edit_name) { actions.onOpenDialog(SettingsDialog.EDIT_NAME) }
            ActionRow(R.string.settings_edit_email) { actions.onOpenDialog(SettingsDialog.EDIT_EMAIL) }
            ActionRow(R.string.settings_change_password) { actions.onOpenDialog(SettingsDialog.CHANGE_PASSWORD) }
            HorizontalDivider()
            ActionRow(R.string.settings_logout, enabled = !state.loggingOut, onClick = actions.onLogout)
            ActionRow(R.string.settings_delete_account, color = MaterialTheme.colorScheme.error) {
                actions.onOpenDialog(SettingsDialog.DELETE_ACCOUNT)
            }
        }
    }
    state.dialog?.let { SettingsFormDialog(it, state, actions) }
}

@Composable
private fun SectionTitle(label: Int) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ThemeOption(label: Int, value: ThemePreference, selected: ThemePreference, onSelect: (ThemePreference) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        leadingContent = { RadioButton(selected = selected == value, onClick = null) },
        modifier = Modifier.clickable(role = Role.RadioButton) { onSelect(value) },
    )
}

@Composable
private fun ActionRow(label: Int, enabled: Boolean = true, color: Color = Color.Unspecified, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label), color = color) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun SettingsFormDialog(dialog: SettingsDialog, state: SettingsUiState, actions: SettingsActions) {
    val form = state.form
    val errors = form.fieldErrors
    val isDelete = dialog == SettingsDialog.DELETE_ACCOUNT
    val title = when (dialog) {
        SettingsDialog.EDIT_NAME -> R.string.settings_edit_name
        SettingsDialog.EDIT_EMAIL -> R.string.settings_edit_email
        SettingsDialog.CHANGE_PASSWORD -> R.string.settings_change_password
        SettingsDialog.DELETE_ACCOUNT -> R.string.delete_account_title
    }
    AlertDialog(
        onDismissRequest = actions.onCloseDialog,
        title = { Text(stringResource(title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isDelete) Text(stringResource(R.string.delete_account_body))
                if (SettingsFields.NAME in dialog.fields) {
                    AuthTextField(
                        form.name, actions.onName, stringResource(R.string.field_name),
                        capitalization = KeyboardCapitalization.Words,
                        error = errors[SettingsFields.NAME], enabled = !state.submitting,
                    )
                }
                if (SettingsFields.LAST_NAME in dialog.fields) {
                    AuthTextField(
                        form.lastName, actions.onLastName, stringResource(R.string.field_last_name),
                        capitalization = KeyboardCapitalization.Words,
                        error = errors[SettingsFields.LAST_NAME], enabled = !state.submitting,
                    )
                }
                if (SettingsFields.EMAIL in dialog.fields) {
                    AuthTextField(
                        form.email, actions.onEmail, stringResource(R.string.field_email),
                        keyboardType = KeyboardType.Email,
                        error = errors[SettingsFields.EMAIL], enabled = !state.submitting,
                    )
                }
                if (SettingsFields.CURRENT_PASSWORD in dialog.fields) {
                    SecretField(
                        form.currentPassword, actions.onCurrentPassword, stringResource(R.string.field_current_password),
                        errors[SettingsFields.CURRENT_PASSWORD], !state.submitting,
                    )
                }
                if (SettingsFields.NEW_PASSWORD in dialog.fields) {
                    SecretField(
                        form.newPassword, actions.onNewPassword, stringResource(R.string.field_new_password),
                        errors[SettingsFields.NEW_PASSWORD], !state.submitting,
                    )
                }
                form.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onSubmit, enabled = !state.submitting) {
                Text(stringResource(if (isDelete) R.string.settings_delete_account else R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onCloseDialog, enabled = !state.submitting) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: String, error: String?, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
