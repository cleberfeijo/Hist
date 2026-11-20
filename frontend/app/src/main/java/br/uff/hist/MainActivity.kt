package br.uff.hist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.uff.hist.core.designsystem.HistTheme
import br.uff.hist.core.designsystem.ThemePreference
import br.uff.hist.core.navigation.HistNavHost
import br.uff.hist.core.session.SessionStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sessionStore: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val theme by sessionStore.theme.collectAsStateWithLifecycle(initialValue = ThemePreference.SYSTEM)
            HistTheme(theme) {
                HistNavHost()
            }
        }
    }
}
