package br.uff.hist.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.uff.hist.R

@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    onBackToLogin: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) onRegistered() }
    RegisterContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onLastNameChange = viewModel::onLastNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onBackToLogin = onBackToLogin,
    )
}

@Composable
fun RegisterContent(
    state: RegisterUiState,
    onNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.register_title), style = MaterialTheme.typography.headlineSmall)
        AuthTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = stringResource(R.string.field_name),
            capitalization = KeyboardCapitalization.Words,
            error = state.fieldErrors["name"],
            enabled = !state.isSubmitting,
        )
        AuthTextField(
            value = state.lastName,
            onValueChange = onLastNameChange,
            label = stringResource(R.string.field_last_name),
            capitalization = KeyboardCapitalization.Words,
            error = state.fieldErrors["last_name"],
            enabled = !state.isSubmitting,
        )
        AuthTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.field_email),
            keyboardType = KeyboardType.Email,
            error = state.fieldErrors["email"],
            enabled = !state.isSubmitting,
        )
        PasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            error = state.fieldErrors["password"],
            enabled = !state.isSubmitting,
        )
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onSubmit, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth()) {
            if (state.isSubmitting) {
                CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.register_submit))
            }
        }
        TextButton(onClick = onBackToLogin, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.register_to_login))
        }
    }
}
