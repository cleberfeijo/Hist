package br.uff.hist.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.uff.hist.R
import br.uff.hist.auth.AuthRepository
import br.uff.hist.core.model.PASSWORD_MAX_LENGTH
import br.uff.hist.core.model.PASSWORD_MIN_LENGTH
import br.uff.hist.core.designsystem.ThemePreference
import br.uff.hist.core.model.User
import br.uff.hist.core.network.ApiResult
import br.uff.hist.core.network.AppError
import br.uff.hist.core.network.ErrorMessages
import br.uff.hist.core.session.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SettingsDialog(val fields: Set<String>) {
    EDIT_NAME(setOf(SettingsFields.NAME, SettingsFields.LAST_NAME)),
    EDIT_EMAIL(setOf(SettingsFields.EMAIL, SettingsFields.CURRENT_PASSWORD)),
    CHANGE_PASSWORD(setOf(SettingsFields.CURRENT_PASSWORD, SettingsFields.NEW_PASSWORD)),
    DELETE_ACCOUNT(setOf(SettingsFields.CURRENT_PASSWORD)),
}

/** Chaves de campo dos diálogos (as mesmas do servidor). */
object SettingsFields {
    const val NAME = "name"
    const val LAST_NAME = "last_name"
    const val EMAIL = "email"
    const val CURRENT_PASSWORD = "current_password"
    const val NEW_PASSWORD = "new_password"
}

data class SettingsForm(
    val name: String = "",
    val lastName: String = "",
    val email: String = "",
    val currentPassword: String = "",
    val newPassword: String = "",
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
)

data class SettingsUiState(
    val user: User? = null,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val dialog: SettingsDialog? = null,
    val form: SettingsForm = SettingsForm(),
    val submitting: Boolean = false,
    val loggingOut: Boolean = false,
    /** Confirmação de sucesso, exibida uma vez pela tela (snackbar). */
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val account: AccountRepository,
    private val auth: AuthRepository,
    private val store: SessionStore,
    private val messages: ErrorMessages,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState(user = store.session.value?.user))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { store.session.collect { s -> _uiState.update { it.copy(user = s?.user) } } }
        viewModelScope.launch { store.theme.collect { t -> _uiState.update { it.copy(theme = t) } } }
    }

    fun setTheme(p: ThemePreference) {
        viewModelScope.launch { store.setTheme(p) }
    }

    fun openDialog(dialog: SettingsDialog) {
        val user = store.session.value?.user
        _uiState.update {
            it.copy(
                dialog = dialog,
                form = SettingsForm(name = user?.name.orEmpty(), lastName = user?.lastName.orEmpty(), email = user?.email.orEmpty()),
            )
        }
    }

    fun closeDialog() = _uiState.update { if (it.submitting) it else it.copy(dialog = null, form = SettingsForm()) }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    fun onName(v: String) = edit(SettingsFields.NAME) { it.copy(name = v) }
    fun onLastName(v: String) = edit(SettingsFields.LAST_NAME) { it.copy(lastName = v) }
    fun onEmail(v: String) = edit(SettingsFields.EMAIL) { it.copy(email = v) }
    fun onCurrentPassword(v: String) = edit(SettingsFields.CURRENT_PASSWORD) { it.copy(currentPassword = v) }
    fun onNewPassword(v: String) = edit(SettingsFields.NEW_PASSWORD) { it.copy(newPassword = v) }

    private fun edit(field: String, change: (SettingsForm) -> SettingsForm) = _uiState.update {
        it.copy(form = change(it.form).copy(fieldErrors = it.form.fieldErrors - field, error = null))
    }

    /** `AuthRepository.logout` sempre limpa a sessão; o resultado não importa para a tela. */
    fun logout() {
        if (_uiState.value.loggingOut) return
        _uiState.update { it.copy(loggingOut = true) }
        viewModelScope.launch {
            auth.logout()
            _uiState.update { it.copy(loggingOut = false) }
        }
    }

    fun submit() {
        val state = _uiState.value
        val dialog = state.dialog ?: return
        if (state.submitting) return
        val form = state.form
        val local = validate(dialog, form)
        if (local.isNotEmpty()) {
            _uiState.update { it.copy(form = form.copy(fieldErrors = local, error = null)) }
            return
        }
        _uiState.update { it.copy(submitting = true, form = form.copy(fieldErrors = emptyMap(), error = null)) }
        viewModelScope.launch {
            val result: ApiResult<*> = when (dialog) {
                SettingsDialog.EDIT_NAME -> account.updateName(form.name.trim(), form.lastName.trim())
                SettingsDialog.EDIT_EMAIL -> account.updateEmail(form.email.trim(), form.currentPassword)
                SettingsDialog.CHANGE_PASSWORD -> account.changePassword(form.currentPassword, form.newPassword)
                SettingsDialog.DELETE_ACCOUNT -> account.deleteAccount(form.currentPassword)
            }
            when (result) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        submitting = false,
                        dialog = null,
                        form = SettingsForm(),
                        message = successMessage(dialog),
                    )
                }
                is ApiResult.Failure -> _uiState.update { it.copy(submitting = false, form = it.form.withFailure(dialog, result.error)) }
            }
        }
    }

    private fun successMessage(dialog: SettingsDialog): String? = when (dialog) {
        SettingsDialog.EDIT_NAME, SettingsDialog.EDIT_EMAIL -> messages.string(R.string.settings_saved)
        SettingsDialog.CHANGE_PASSWORD -> messages.string(R.string.settings_password_changed)
        SettingsDialog.DELETE_ACCOUNT -> null // a sessão some e a navegação vai ao Login
    }

    private fun validate(dialog: SettingsDialog, f: SettingsForm): Map<String, String> = buildMap {
        val required = messages.string(R.string.field_required)
        if (SettingsFields.NAME in dialog.fields && f.name.isBlank()) put(SettingsFields.NAME, required)
        if (SettingsFields.LAST_NAME in dialog.fields && f.lastName.isBlank()) put(SettingsFields.LAST_NAME, required)
        if (SettingsFields.EMAIL in dialog.fields && f.email.isBlank()) put(SettingsFields.EMAIL, required)
        if (SettingsFields.CURRENT_PASSWORD in dialog.fields && f.currentPassword.isEmpty()) {
            put(SettingsFields.CURRENT_PASSWORD, required)
        }
        if (SettingsFields.NEW_PASSWORD in dialog.fields &&
            f.newPassword.length !in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH
        ) {
            put(SettingsFields.NEW_PASSWORD, messages.string(R.string.new_password_length))
        }
    }

    private fun SettingsForm.withFailure(dialog: SettingsDialog, error: AppError): SettingsForm {
        if (error is AppError.Api) {
            val code = error.code.lowercase()
            if (code == "invalid_password") {
                return copy(fieldErrors = mapOf(SettingsFields.CURRENT_PASSWORD to messages.of(error).ifBlank { messages.string(R.string.error_unknown) }))
            }
            if (code == "email_taken") return copy(fieldErrors = mapOf(SettingsFields.EMAIL to messages.of(error)))
            if (error.fieldErrors.isNotEmpty()) {
                // O corpo de DELETE /users/me chama a senha de `password`.
                val fields = error.fieldErrors
                    .mapKeys { (k, _) -> if (k == "password") SettingsFields.CURRENT_PASSWORD else k }
                    .mapValues { it.value.take(ErrorMessages.MAX_SERVER_MESSAGE) }
                // Chaves que nenhum campo do diálogo exibe viram o erro geral.
                val unrendered = fields.filterKeys { it !in dialog.fields }
                return copy(
                    fieldErrors = fields,
                    error = if (unrendered.isEmpty()) null else unrendered.values.filter { it.isNotBlank() }
                        .joinToString("\n").take(ErrorMessages.MAX_SERVER_MESSAGE)
                        .ifBlank { messages.of(error) }
                        .ifBlank { messages.string(R.string.error_unknown) },
                )
            }
        }
        return copy(error = messages.of(error))
    }
}
