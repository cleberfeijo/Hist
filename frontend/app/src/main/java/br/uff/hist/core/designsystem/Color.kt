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
        outlineVariant = Color(0xFFEFE6D8),
        surfaceVariant = Color(0xFFEFE7DB),
        surfaceDim = Color(0xFFE9E1D5),
        surfaceBright = Color(0xFFFBF8F3),
        surfaceContainerLowest = Color(0xFFFEFCF9),
        surfaceContainerLow = Color(0xFFF9F5EE),
        surfaceContainer = Color(0xFFF6F1E9),
        surfaceContainerHigh = Color(0xFFEFE8DC),
        surfaceContainerHighest = Color(0xFFE9DFD1),
        primaryContainer = Color(0xFFF6D9CB),
        onPrimaryContainer = Color(0xFF5A230C),
        secondary = Color(0xFF7A5C48),
        onSecondary = Color(0xFFFFFBF7),
        secondaryContainer = Color(0xFFEBDDD0),
        onSecondaryContainer = Color(0xFF2E1D12),
        tertiary = Color(0xFF7D6A2B),
        onTertiary = Color(0xFFFFFBF7),
        tertiaryContainer = Color(0xFFF1E4B8),
        onTertiaryContainer = Color(0xFF2E2500),
        error = Color(0xFFA33A2E),
        onError = Color(0xFFFFFBF7),
        errorContainer = Color(0xFFF5D5CF),
        onErrorContainer = Color(0xFF4A140E),
        inversePrimary = Color(0xFFF0A98A),
        inverseSurface = Color(0xFF3A2F27),
        inverseOnSurface = Color(0xFFF6F1E9),
        surfaceTint = Color(0xFFB4532A),
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
        outlineVariant = Color(0xFF353B48),
        surfaceVariant = Color(0xFF252A34),
        surfaceDim = Color(0xFF0F1115),
        surfaceBright = Color(0xFF2A2F3A),
        surfaceContainerLowest = Color(0xFF0B0D10),
        surfaceContainerLow = Color(0xFF161A21),
        surfaceContainer = Color(0xFF1C2029),
        surfaceContainerHigh = Color(0xFF232833),
        surfaceContainerHighest = Color(0xFF2A2F3A),
        primaryContainer = Color(0xFF5C4413),
        onPrimaryContainer = Color(0xFFFCE3B0),
        secondary = Color(0xFFC9A56B),
        onSecondary = Color(0xFF2A1E08),
        secondaryContainer = Color(0xFF3E3320),
        onSecondaryContainer = Color(0xFFF3E2BE),
        tertiary = Color(0xFF9DB8A0),
        onTertiary = Color(0xFF11231A),
        tertiaryContainer = Color(0xFF2B3F33),
        onTertiaryContainer = Color(0xFFCFE6D6),
        error = Color(0xFFE5877B),
        onError = Color(0xFF3A0F0A),
        errorContainer = Color(0xFF5E2820),
        onErrorContainer = Color(0xFFFFDAD5),
        inversePrimary = Color(0xFF8A5A0F),
        inverseSurface = Color(0xFFE4E7EC),
        inverseOnSurface = Color(0xFF1C2029),
        surfaceTint = Color(0xFFF5B544),
    )
}
