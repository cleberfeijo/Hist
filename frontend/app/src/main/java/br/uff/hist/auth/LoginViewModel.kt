package br.uff.hist.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.uff.hist.R
import br.uff.hist.core.network.ApiResult
import br.uff.hist.core.network.ErrorMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val messages: ErrorMessages,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = _uiState.value
        if (current.isSubmitting) return
        if (current.email.isBlank() || current.password.isEmpty()) {
            _uiState.update { it.copy(error = messages.string(R.string.login_missing_fields)) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.login(current.email.trim(), current.password)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _done.value = true
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isSubmitting = false, error = messages.of(result.error))
                }
            }
        }
    }
}
