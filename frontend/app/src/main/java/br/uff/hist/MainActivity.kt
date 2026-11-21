package br.uff.hist

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import br.uff.hist.core.designsystem.HistTheme
import br.uff.hist.core.designsystem.ThemePreference
import br.uff.hist.core.designsystem.isDark
import br.uff.hist.core.navigation.HistNavHost
import br.uff.hist.core.session.SessionStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// Fundo da paleta (Color.kt) como cor de barra de navegação nas versões sem barra transparente (Android 8-9).
private val PaperBackground = Color.parseColor("#F6F1E9")
private val NightBackground = Color.parseColor("#0F1115")

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sessionStore: SessionStore

    /** `null` até a preferência ser lida do disco: evita desenhar com SYSTEM e piscar de tema. */
    private val theme = MutableStateFlow<ThemePreference?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { theme.value == null }
        lifecycleScope.launch { sessionStore.theme.collect { theme.value = it } }
        setContent {
            val preference by theme.collectAsStateWithLifecycle()
            val loaded = preference ?: return@setContent
            val dark = loaded.isDark(isSystemInDarkTheme())
            // Ícones das barras seguem o tema efetivo (não o do sistema) e são reaplicados quando ele muda.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(PaperBackground, NightBackground) { dark },
                )
                onDispose {}
            }
            HistTheme(loaded) {
                HistNavHost()
            }
        }
    }
}
