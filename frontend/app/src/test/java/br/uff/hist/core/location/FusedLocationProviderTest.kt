package br.uff.hist.core.location

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.Tasks
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.toList
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class FusedLocationProviderTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    /** Cliente cujas chamadas de localização falham de forma assíncrona. */
    private val failingClient: FusedLocationProviderClient = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(FusedLocationProviderClient::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "requestLocationUpdates", "getCurrentLocation", "getLastLocation" ->
                Tasks.forException<Any>(RuntimeException("Play Services indisponível"))
            "removeLocationUpdates" -> Tasks.forResult<Void>(null)
            else -> error("inesperado: ${method.name}")
        }
    } as FusedLocationProviderClient

    /** Os listeners das Tasks rodam na main: roda o corpo fora dela e drena o Looper até terminar. */
    private fun awaitOnBackground(block: suspend () -> Unit) {
        val job = CoroutineScope(Dispatchers.Default).async { block() }
        val deadline = System.currentTimeMillis() + 10_000
        while (!job.isCompleted && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        check(job.isCompleted) { "o corpo do teste não terminou (fluxo não foi fechado?)" }
        job.getCompleted()
    }

    private fun provider(grant: Boolean): FusedLocationProvider {
        if (grant) shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        return FusedLocationProvider(app, failingClient)
    }

    @Test fun `updates completa quando a requisicao falha`() = awaitOnBackground {
        assertEquals(emptyList<LocationFix>(), provider(true).updates(LocationPriority.HIGH, 5_000).toList())
    }

    @Test fun `current e lastKnown retornam null quando a tarefa falha`() = awaitOnBackground {
        val p = provider(true)
        assertNull(p.current(LocationPriority.BALANCED))
        assertNull(p.lastKnown())
    }

    @Test fun `sem permissao nada e consultado`() = awaitOnBackground {
        val p = provider(false)
        assertEquals(emptyList<LocationFix>(), p.updates(LocationPriority.HIGH, 5_000).toList())
        assertNull(p.current(LocationPriority.HIGH))
        assertNull(p.lastKnown())
    }
}
