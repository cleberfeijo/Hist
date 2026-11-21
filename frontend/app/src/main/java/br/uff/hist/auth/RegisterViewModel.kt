package br.uff.hist.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.uff.hist.R
import br.uff.hist.core.model.PASSWORD_MAX_LENGTH
import br.uff.hist.core.model.PASSWORD_MIN_LENGTH
import br.uff.hist.core.network.ApiResult
import br.uff.hist.core.network.AppError
import br.uff.hist.core.network.ErrorMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** As chaves de [fieldErrors] seguem o servidor: `name`, `last_name`, `email`, `password`. */
data class RegisterUiState(
    val name: String = "",
    val lastName: String = "",
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
)

/** Chaves de campo do formulário (as mesmas do servidor); a tela e o ViewModel usam só estas. */
object RegisterFields {
    const val NAME = "name"
    const val LAST_NAME = "last_name"
    const val EMAIL = "email"
    const val PASSWORD = "password"
    val ALL = setOf(NAME, LAST_NAME, EMAIL, PASSWORD)
}


@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val messages: ErrorMessages,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun onNameChange(v: String) = edit(RegisterFields.NAME) { it.copy(name = v) }
    fun onLastNameChange(v: String) = edit(RegisterFields.LAST_NAME) { it.copy(lastName = v) }
    fun onEmailChange(v: String) = edit(RegisterFields.EMAIL) { it.copy(email = v) }
    fun onPasswordChange(v: String) = edit(RegisterFields.PASSWORD) { it.copy(password = v) }

    private fun edit(field: String, change: (RegisterUiState) -> RegisterUiState) =
        _uiState.update { change(it).copy(fieldErrors = it.fieldErrors - field, error = null) }

    fun submit() {
        val current = _uiState.value
        if (current.isSubmitting) return
        val local = validate(current)
        if (local.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = local, error = null) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, fieldErrors = emptyMap(), error = null) }
        viewModelScope.launch {
            val result = repository.register(
                current.name.trim(), current.lastName.trim(), current.email.trim(), current.password,
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _done.value = true
                }
                is ApiResult.Failure -> _uiState.update { it.withFailure(result.error) }
            }
        }
    }

    private fun validate(s: RegisterUiState): Map<String, String> = buildMap {
        val required = messages.string(R.string.field_required)
        if (s.name.isBlank()) put(RegisterFields.NAME, required)
        if (s.lastName.isBlank()) put(RegisterFields.LAST_NAME, required)
        if (s.email.isBlank()) put(RegisterFields.EMAIL, required)
        if (s.password.length !in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH) {
            put(RegisterFields.PASSWORD, messages.string(R.string.field_password_length))
        }
    }

    private fun RegisterUiState.withFailure(error: AppError): RegisterUiState {
        if (error is AppError.Api) {
            if (error.code.equals("email_taken", ignoreCase = true)) {
                return copy(isSubmitting = false, fieldErrors = mapOf(RegisterFields.EMAIL to messages.of(error)))
            }
            if (error.fieldErrors.isNotEmpty()) {
                val fields = error.fieldErrors.mapValues { it.value.take(ErrorMessages.MAX_SERVER_MESSAGE) }
                // Chaves que nenhum campo exibe (caminho aninhado, "" de erro de modelo) viram o erro geral.
                val unrendered = fields.filterKeys { it !in RegisterFields.ALL }
                return copy(
                    isSubmitting = false,
                    fieldErrors = fields,
                    error = if (unrendered.isEmpty()) null else generalMessage(error, unrendered),
                )
            }
        }
        return copy(isSubmitting = false, error = messages.of(error))
    }

    private fun generalMessage(error: AppError.Api, unrendered: Map<String, String>): String =
        unrendered.values.filter { it.isNotBlank() }.joinToString("\n").take(ErrorMessages.MAX_SERVER_MESSAGE)
            .ifBlank { messages.of(error) }
            .ifBlank { messages.string(R.string.error_unknown) }
}
