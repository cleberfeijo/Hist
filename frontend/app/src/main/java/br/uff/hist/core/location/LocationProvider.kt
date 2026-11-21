package br.uff.hist.core.location

import android.location.Location
import android.os.Build
import br.uff.hist.core.model.GeoPoint
import kotlinx.coroutines.flow.Flow

const val EXPLORE_INTERVAL_MS = 10_000L
const val CHAT_INTERVAL_MS = 5_000L

data class LocationFix(val point: GeoPoint, val accuracyM: Float, val isMock: Boolean)

enum class LocationPriority { BALANCED, HIGH }

interface LocationProvider {
    /** FINE ou COARSE concedida. */
    fun hasPermission(): Boolean

    /** FINE concedida (só COARSE = localização aproximada, insuficiente para o chat). */
    fun hasPreciseLocation(): Boolean

    /** Completa vazio sem permissão. */
    fun updates(priority: LocationPriority, intervalMs: Long): Flow<LocationFix>

    /** `null` sem permissão ou se a leitura falhar. */
    suspend fun current(priority: LocationPriority): LocationFix?

    suspend fun lastKnown(): LocationFix?
}

fun Location.toFix(): LocationFix = LocationFix(
    point = GeoPoint(latitude, longitude),
    accuracyM = accuracy,
    isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else @Suppress("DEPRECATION") isFromMockProvider,
)
