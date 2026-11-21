package br.uff.hist.profile

import androidx.test.core.app.ApplicationProvider
import br.uff.hist.auth.AuthRepository
import br.uff.hist.core.designsystem.ThemePreference
import br.uff.hist.core.model.Role
import br.uff.hist.core.model.User
import br.uff.hist.core.network.ApiCaller
import br.uff.hist.core.network.ErrorMessages
import br.uff.hist.core.network.HistJson
import br.uff.hist.core.session.Session
import br.uff.hist.core.session.SessionEvents
import br.uff.hist.testutil.FakeAccountApi
import br.uff.hist.testutil.FakeSessionStore
import br.uff.hist.testutil.MainDispatcherRule
import br.uff.hist.testutil.httpError
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

private val ME = User("1", "Ana", "Silva", "ana@x.com", setOf(Role.USER))

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)

    private val api = FakeAccountApi()
    private val authApi = br.uff.hist.testutil.FakeAuthApi()
    private val store = FakeSessionStore(Session("velho", ME))

    private fun vm(): SettingsViewModel {
        val caller = ApiCaller(HistJson, SessionEvents())
        return SettingsViewModel(
            AccountRepository(api, caller, store),
            AuthRepository(authApi, caller, store, kotlinx.coroutines.CoroutineScope(dispatcher)),
            store,
            ErrorMessages(ApplicationProvider.getApplicationContext()),
        )
    }

    @Test fun `trocar senha com sucesso substitui o token no store`() = runTest(dispatcher) {
        val vm = vm()
        vm.openDialog(SettingsDialog.CHANGE_PASSWORD)
        vm.onCurrentPassword("antiga123")
        vm.onNewPassword("novasenha1")
        vm.submit()
        vm.submit() // toque duplo
        advanceUntilIdle()
        assertEquals("novo", store.currentToken())
        assertEquals(1, api.changePasswordCalls)
        assertNull(vm.uiState.value.dialog)
        assertEquals("Senha alterada.", vm.uiState.value.message)
    }

    @Test fun `nova senha curta e barrada localmente`() = runTest(dispatcher) {
        val vm = vm()
        vm.openDialog(SettingsDialog.CHANGE_PASSWORD)
        vm.onCurrentPassword("antiga123")
        vm.onNewPassword("curta")
        vm.submit()
        advanceUntilIdle()
        assertEquals(0, api.changePasswordCalls)
        assertEquals("A senha precisa ter entre 8 e 72 caracteres.", vm.uiState.value.form.fieldErrors[SettingsFields.NEW_PASSWORD])
    }

    @Test fun `invalid_password ao excluir conta vira erro de campo e a sessao fica`() = runTest(dispatcher) {
        api.deleteMeHandler = { throw httpError(401, "invalid_password", "Senha incorreta.") }
        val vm = vm()
        vm.openDialog(SettingsDialog.DELETE_ACCOUNT)
        vm.onCurrentPassword("errada123")
        vm.submit()
        advanceUntilIdle()
        assertEquals("Senha incorreta.", vm.uiState.value.form.fieldErrors[SettingsFields.CURRENT_PASSWORD])
        assertNotNull(store.session.value)
        assertEquals(SettingsDialog.DELETE_ACCOUNT, vm.uiState.value.dialog)
        assertFalse(vm.uiState.value.submitting)
    }

    @Test fun `chave password do servidor vira erro em Senha atual`() = runTest(dispatcher) {
        api.deleteMeHandler = {
            throw httpError(422, "validation_error", "Inválido.", """[{"field":"password","message":"obrigatória"}]""")
        }
        val vm = vm()
        vm.openDialog(SettingsDialog.DELETE_ACCOUNT)
        vm.onCurrentPassword("x")
        vm.submit()
        advanceUntilIdle()
        assertEquals("obrigatória", vm.uiState.value.form.fieldErrors[SettingsFields.CURRENT_PASSWORD])
        assertNull(vm.uiState.value.form.error)
    }

    @Test fun `toque duplo em excluir conta faz uma so chamada`() = runTest(dispatcher) {
        val vm = vm()
        vm.openDialog(SettingsDialog.DELETE_ACCOUNT)
        vm.onCurrentPassword("senha1234")
        vm.submit()
        vm.submit()
        advanceUntilIdle()
        assertEquals(1, api.deleteMeCalls)
    }

    @Test fun `excluir conta com sucesso limpa a sessao`() = runTest(dispatcher) {
        val vm = vm()
        vm.openDialog(SettingsDialog.DELETE_ACCOUNT)
        vm.onCurrentPassword("senha1234")
        vm.submit()
        advanceUntilIdle()
        assertNull(store.session.value)
    }

    @Test fun `setTheme persiste a escolha`() = runTest(dispatcher) {
        val vm = vm()
        vm.setTheme(ThemePreference.DARK)
        advanceUntilIdle()
        assertEquals(ThemePreference.DARK, store.theme.value)
        assertEquals(ThemePreference.DARK, vm.uiState.value.theme)
    }

    @Test fun `sair limpa a sessao mesmo com falha de rede no logout`() = runTest(dispatcher) {
        authApi.logoutHandler = { throw IOException() }
        val vm = vm()
        vm.logout()
        vm.logout()
        advanceUntilIdle()
        assertNull(store.session.value)
        assertEquals(3, authApi.logoutCalls) // sem rede: 3 tentativas de revogação
    }

    @Test fun `editar nome atualiza o usuario do store`() = runTest(dispatcher) {
        val vm = vm()
        vm.openDialog(SettingsDialog.EDIT_NAME)
        assertEquals("Ana", vm.uiState.value.form.name)
        vm.onName("  Maria ")
        vm.onLastName("Souza")
        vm.submit()
        advanceUntilIdle()
        assertEquals("Maria", api.lastUpdate?.name)
        assertEquals("Maria", store.session.value?.user?.name)
        assertEquals("Dados atualizados.", vm.uiState.value.message)
        assertNull(vm.uiState.value.dialog)
    }

    @Test fun `alterar e-mail exige a senha atual`() = runTest(dispatcher) {
        val vm = vm()
        vm.openDialog(SettingsDialog.EDIT_EMAIL)
        vm.onEmail("novo@x.com")
        vm.submit()
        advanceUntilIdle()
        assertEquals(0, api.updateCalls)
        assertTrue(vm.uiState.value.form.fieldErrors.containsKey(SettingsFields.CURRENT_PASSWORD))
        vm.onCurrentPassword("senha1234")
        vm.submit()
        advanceUntilIdle()
        assertEquals("novo@x.com", api.lastUpdate?.email)
        assertEquals("senha1234", api.lastUpdate?.currentPassword)
        assertEquals("novo@x.com", store.session.value?.user?.email)
    }

    @Test fun `email_taken vira erro no campo de e-mail`() = runTest(dispatcher) {
        api.updateHandler = { throw httpError(409, "email_taken", "E-mail já em uso.") }
        val vm = vm()
        vm.openDialog(SettingsDialog.EDIT_EMAIL)
        vm.onEmail("outro@x.com")
        vm.onCurrentPassword("senha1234")
        vm.submit()
        advanceUntilIdle()
        assertEquals("E-mail já em uso.", vm.uiState.value.form.fieldErrors[SettingsFields.EMAIL])
    }

    @Test fun `erro de campo desconhecido vira erro geral`() = runTest(dispatcher) {
        api.updateHandler = {
            throw httpError(422, "validation_error", "Inválido.", """[{"field":"body.x","message":"ruim"}]""")
        }
        val vm = vm()
        vm.openDialog(SettingsDialog.EDIT_NAME)
        vm.submit()
        advanceUntilIdle()
        assertEquals("ruim", vm.uiState.value.form.error)
    }
}
