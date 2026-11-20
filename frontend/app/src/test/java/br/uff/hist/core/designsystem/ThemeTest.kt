package br.uff.hist.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "notnight")
class ThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lightSchemeUsesPatrimonioColors() {
        assertEquals(Color(0xFFB4532A), HistColors.light.primary)
        assertEquals(Color(0xFFF6F1E9), HistColors.light.background)
        assertEquals(Color(0xFFFBF8F3), HistColors.light.surface)
        assertEquals(Color(0xFF2A211B), HistColors.light.onSurface)
        assertEquals(Color(0xFFE6DCCD), HistColors.light.outline)
        assertEquals(Color(0xFFFFFFFF), HistColors.light.onPrimary)
    }

    @Test
    fun darkSchemeUsesNoturnoColors() {
        assertEquals(Color(0xFFF5B544), HistColors.dark.primary)
        assertEquals(Color(0xFF1A1305), HistColors.dark.onPrimary)
        assertEquals(Color(0xFF0F1115), HistColors.dark.background)
        assertEquals(Color(0xFF1C2029), HistColors.dark.surface)
        assertEquals(Color(0xFFF2F4F7), HistColors.dark.onSurface)
        assertEquals(Color(0xFF2A2F3A), HistColors.dark.outline)
    }

    @Test
    fun darkPreferenceWinsOverLightSystem() {
        var background = Color.Unspecified
        var secondary = Color.Unspecified
        compose.setContent {
            HistTheme(ThemePreference.DARK) {
                background = MaterialTheme.colorScheme.background
                secondary = LocalSecondaryText.current
            }
        }
        compose.waitForIdle()
        assertEquals(Color(0xFF0F1115), background)
        assertEquals(Color(0xFF8B93A3), secondary)
    }

    @Test
    fun lightPreferenceExposesLightSecondaryText() {
        var secondary = Color.Unspecified
        compose.setContent {
            HistTheme(ThemePreference.LIGHT) { secondary = LocalSecondaryText.current }
        }
        compose.waitForIdle()
        assertEquals(Color(0xFF857565), secondary)
    }

    @Test
    fun systemPreferenceFollowsSystemMode() {
        var background = Color.Unspecified
        compose.setContent {
            HistTheme(ThemePreference.SYSTEM) { background = MaterialTheme.colorScheme.background }
        }
        compose.waitForIdle()
        assertEquals(Color(0xFFF6F1E9), background)
    }
}
