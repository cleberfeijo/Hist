package br.uff.hist.testutil

import br.uff.hist.chat.SocketEvent
import br.uff.hist.core.model.GeoPoint
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeChatSocketFactoryTest {
    @Test fun failBeforeOpenEmitsOnlyFailure() = runBlocking {
        val factory = FakeChatSocketFactory().apply { nextOpens = false }
        val events = factory.connect("a", "t", GeoPoint(0.0, 0.0)).events.toList()
        assertEquals(1, events.size)
        assertTrue(events[0] is SocketEvent.Failure)
        // só vale para a próxima conexão
        val next = factory.connect("a", "t", GeoPoint(0.0, 0.0))
        next.close()
        assertEquals(SocketEvent.Opened, next.events.toList().first())
    }
}
