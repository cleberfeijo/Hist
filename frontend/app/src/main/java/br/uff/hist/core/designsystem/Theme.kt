package br.uff.hist.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemePreference { LIGHT, DARK, SYSTEM }

/** Cor do texto secundário do tema ativo. */
val LocalSecondaryText: ProvidableCompositionLocal<Color> =
    staticCompositionLocalOf { SecondaryTextLight }

/** Tema efetivo: a preferência explícita ou, em SYSTEM, o que o sistema estiver usando. */
fun ThemePreference.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemePreference.LIGHT -> false
    ThemePreference.DARK -> true
    ThemePreference.SYSTEM -> systemDark
}

@Composable
fun HistTheme(
    preference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = preference.isDark(isSystemInDarkTheme())
    CompositionLocalProvider(
        LocalSecondaryText provides if (dark) SecondaryTextDark else SecondaryTextLight,
    ) {
        MaterialTheme(
            colorScheme = if (dark) HistColors.dark else HistColors.light,
            typography = HistTypography,
            content = content,
        )
    }
}
