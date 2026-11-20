package br.uff.hist.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import br.uff.hist.R

val Fraunces = FontFamily(
    Font(R.font.fraunces_medium, FontWeight.Medium),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private val base = Typography()

/** Títulos em Fraunces, o restante em Inter. */
val HistTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Inter),
    displayMedium = base.displayMedium.copy(fontFamily = Inter),
    displaySmall = base.displaySmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Inter),
    headlineMedium = base.headlineMedium.copy(fontFamily = Inter),
    headlineSmall = base.headlineSmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.Medium),
    titleSmall = base.titleSmall.copy(fontFamily = Inter),
    bodyLarge = base.bodyLarge.copy(fontFamily = Inter),
    bodyMedium = base.bodyMedium.copy(fontFamily = Inter),
    bodySmall = base.bodySmall.copy(fontFamily = Inter),
    labelLarge = base.labelLarge.copy(fontFamily = Inter),
    labelMedium = base.labelMedium.copy(fontFamily = Inter),
    labelSmall = base.labelSmall.copy(fontFamily = Inter),
)
