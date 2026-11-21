package br.uff.hist.core.designsystem

import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.RelativeDateTimeFormatter.Direction
import android.icu.text.RelativeDateTimeFormatter.RelativeUnit
import android.icu.util.ULocale
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

/** O app é em pt-BR: números e datas não seguem o idioma do aparelho. */
val PtBr: Locale = Locale.forLanguageTag("pt-BR")

private const val KM_THRESHOLD_M = 999.5
private const val RELATIVE_DAYS_LIMIT = 7L

/** "a 87 m" abaixo de 999,5 m; a partir daí "a 1,2 km" (uma casa, vírgula pt-BR). */
fun formatDistance(meters: Double): String =
    if (meters < KM_THRESHOLD_M) {
        "a ${meters.roundToInt()} m"
    } else {
        "a ${String.format(PtBr, "%.1f", meters / 1000)} km"
    }

/** "agora", "há 5 minutos", "há 3 horas", "há 2 dias"; depois de uma semana, a data por extenso. */
fun formatRelativeDate(at: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val elapsed = Duration.between(at, now)
    val formatter = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(PtBr))
    return when {
        elapsed.toMinutes() < 1 -> formatter.format(Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
        elapsed.toHours() < 1 -> formatter.format(elapsed.toMinutes().toDouble(), Direction.LAST, RelativeUnit.MINUTES)
        elapsed.toDays() < 1 -> formatter.format(elapsed.toHours().toDouble(), Direction.LAST, RelativeUnit.HOURS)
        elapsed.toDays() < RELATIVE_DAYS_LIMIT -> formatter.format(elapsed.toDays().toDouble(), Direction.LAST, RelativeUnit.DAYS)
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(PtBr).format(at.atZone(zone))
    }
}
