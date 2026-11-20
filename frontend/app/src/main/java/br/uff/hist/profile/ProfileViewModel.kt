package br.uff.hist.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(private val account: AccountRepository) : ViewModel() {
    private var job: Job? = null

    /** Relê o usuário (atualiza a sessão); o resultado é ignorado, o `ApiCaller` já trata sessão revogada. */
    fun refresh() {
        if (job?.isActive == true) return
        job = viewModelScope.launch { account.refreshMe() }
    }
}
