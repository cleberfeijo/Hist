package br.uff.hist.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun isDarkFollowsPreferenceOrSystem() {
        assertEquals(false, ThemePreference.LIGHT.isDark(systemDark = true))
        assertEquals(true, ThemePreference.DARK.isDark(systemDark = false))
        assertEquals(true, ThemePreference.SYSTEM.isDark(systemDark = true))
        assertEquals(false, ThemePreference.SYSTEM.isDark(systemDark = false))
    }

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

    private fun customizedRoles(s: ColorScheme) = mapOf(
        "surfaceVariant" to s.surfaceVariant,
        "surfaceDim" to s.surfaceDim,
        "surfaceBright" to s.surfaceBright,
        "surfaceContainerLowest" to s.surfaceContainerLowest,
        "surfaceContainerLow" to s.surfaceContainerLow,
        "surfaceContainer" to s.surfaceContainer,
        "surfaceContainerHigh" to s.surfaceContainerHigh,
        "surfaceContainerHighest" to s.surfaceContainerHighest,
        "primaryContainer" to s.primaryContainer,
        "onPrimaryContainer" to s.onPrimaryContainer,
        "secondary" to s.secondary,
        "onSecondary" to s.onSecondary,
        "secondaryContainer" to s.secondaryContainer,
        "onSecondaryContainer" to s.onSecondaryContainer,
        "tertiary" to s.tertiary,
        "onTertiary" to s.onTertiary,
        "tertiaryContainer" to s.tertiaryContainer,
        "onTertiaryContainer" to s.onTertiaryContainer,
        "outlineVariant" to s.outlineVariant,
        "error" to s.error,
        "onError" to s.onError,
        "errorContainer" to s.errorContainer,
        "onErrorContainer" to s.onErrorContainer,
        "inversePrimary" to s.inversePrimary,
        "inverseSurface" to s.inverseSurface,
        "inverseOnSurface" to s.inverseOnSurface,
        "surfaceTint" to s.surfaceTint,
    )

    @Test
    fun noRoleKeepsMaterialBaselineDefaults() {
        val lightBase = customizedRoles(lightColorScheme())
        val darkBase = customizedRoles(darkColorScheme())
        customizedRoles(HistColors.light).forEach { (role, color) ->
            assertNotEquals("light $role ainda e o padrao do M3", lightBase[role], color)
        }
        customizedRoles(HistColors.dark).forEach { (role, color) ->
            assertNotEquals("dark $role ainda e o padrao do M3", darkBase[role], color)
        }
    }

    @Test
    @Config(qualifiers = "night")
    fun systemPreferenceResolvesToDarkWhenSystemIsNight() {
        var background = Color.Unspecified
        compose.setContent {
            HistTheme(ThemePreference.SYSTEM) { background = MaterialTheme.colorScheme.background }
        }
        compose.waitForIdle()
        assertEquals(Color(0xFF0F1115), background)
    }
}
