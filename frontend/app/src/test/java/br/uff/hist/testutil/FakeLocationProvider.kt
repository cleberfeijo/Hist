package br.uff.hist.testutil

import br.uff.hist.core.location.LocationFix
import br.uff.hist.core.location.LocationPriority
import br.uff.hist.core.location.LocationProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow

class FakeLocationProvider(private var permission: Boolean = true, private var precise: Boolean = true) : LocationProvider {
    private val flow = MutableSharedFlow<LocationFix>(extraBufferCapacity = 16)

    var currentResult: LocationFix? = null
    var currentCalls = 0
        private set
    var lastKnownResult: LocationFix? = null

    fun setPermission(value: Boolean) { permission = value }

    /** Só vale com permissão: `false` simula "só localização aproximada". */
    fun setPrecise(value: Boolean) { precise = value }

    suspend fun emit(fix: LocationFix) = flow.emit(fix)

    override fun hasPermission() = permission
    override fun hasPreciseLocation() = permission && precise
    override fun updates(priority: LocationPriority, intervalMs: Long): Flow<LocationFix> =
        if (permission) flow else emptyFlow()
    override suspend fun current(priority: LocationPriority): LocationFix? {
        currentCalls++
        return if (permission) currentResult else null
    }
    override suspend fun lastKnown() = if (permission) lastKnownResult else null
}
