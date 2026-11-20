package br.uff.hist.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val SecondaryTextLight = Color(0xFF857565)
internal val SecondaryTextDark = Color(0xFF8B93A3)

/** Paletas "Patrimônio" (claro) e "Noturno" (escuro), conforme spec 5.1. */
object HistColors {
    val light: ColorScheme = lightColorScheme(
        primary = Color(0xFFB4532A),
        onPrimary = Color(0xFFFFFFFF),
        background = Color(0xFFF6F1E9),
        onBackground = Color(0xFF2A211B),
        surface = Color(0xFFFBF8F3),
        onSurface = Color(0xFF2A211B),
        onSurfaceVariant = SecondaryTextLight,
        outline = Color(0xFFE6DCCD),
        outlineVariant = Color(0xFFE6DCCD),
    )

    val dark: ColorScheme = darkColorScheme(
        primary = Color(0xFFF5B544),
        onPrimary = Color(0xFF1A1305),
        background = Color(0xFF0F1115),
        onBackground = Color(0xFFF2F4F7),
        surface = Color(0xFF1C2029),
        onSurface = Color(0xFFF2F4F7),
        onSurfaceVariant = SecondaryTextDark,
        outline = Color(0xFF2A2F3A),
        outlineVariant = Color(0xFF2A2F3A),
    )
}
