package br.uff.hist.core.designsystem

import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FormatTest {
    @Test fun `distancia abaixo de 999,5 m arredonda para metros`() {
        assertEquals("a 87 m", formatDistance(87.4))
        assertEquals("a 999 m", formatDistance(999.4))
    }

    @Test fun `distancia a partir de 999,5 m vira km com virgula pt-BR`() {
        assertEquals("a 1,0 km", formatDistance(999.6))
        assertEquals("a 1,2 km", formatDistance(1234.0))
    }

    @Test fun `formato independe do locale do aparelho`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("a 1,2 km", formatDistance(1234.0))
        } finally {
            Locale.setDefault(previous)
        }
    }

    private val now = Instant.parse("2026-11-20T12:00:00Z")

    @Test fun `data relativa em pt-BR`() {
        assertEquals("agora", formatRelativeDate(now.minusSeconds(20), now))
        assertEquals("há 5 minutos", formatRelativeDate(now.minusSeconds(5 * 60), now))
        assertEquals("há 3 horas", formatRelativeDate(now.minusSeconds(3 * 3600), now))
        assertEquals("há 2 dias", formatRelativeDate(now.minusSeconds(2 * 86_400L), now))
    }

    @Test fun `data relativa nao depende do locale do aparelho e data futura vira agora`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("há 5 minutos", formatRelativeDate(now.minusSeconds(300), now))
            assertEquals("agora", formatRelativeDate(now.plusSeconds(120), now))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun `data antiga vira data absoluta pt-BR`() {
        assertEquals("1 de nov. de 2026", formatRelativeDate(Instant.parse("2026-11-01T15:00:00Z"), now, java.time.ZoneOffset.UTC))
    }
}
