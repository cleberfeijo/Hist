package br.uff.hist.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Singleton
class FusedLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: FusedLocationProviderClient,
) : LocationProvider {

    override fun hasPermission(): Boolean =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    @SuppressLint("MissingPermission")
    override fun updates(priority: LocationPriority, intervalMs: Long): Flow<LocationFix> {
        if (!hasPermission()) return emptyFlow()
        return callbackFlow {
            val request = LocationRequest.Builder(priority.toGms(), intervalMs).build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.locations.forEach { trySend(it.toFix()) }
                }
            }
            try {
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Falha ao iniciar atualizações de localização", e)
                        close()
                    }
            } catch (e: SecurityException) {
                Log.w(TAG, "Sem permissão para atualizações de localização", e)
                close()
            }
            awaitClose { client.removeLocationUpdates(callback) }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun current(priority: LocationPriority): LocationFix? {
        if (!hasPermission()) return null
        val tokenSource = CancellationTokenSource()
        return try {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { tokenSource.cancel() }
                client.getCurrentLocation(priority.toGms(), tokenSource.token)
                    .addOnSuccessListener { cont.resume(it?.toFix()) }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Falha em getCurrentLocation", e)
                        cont.resume(null)
                    }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Erro em getCurrentLocation", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun lastKnown(): LocationFix? {
        if (!hasPermission()) return null
        return try {
            suspendCancellableCoroutine { cont ->
                client.lastLocation
                    .addOnSuccessListener { cont.resume(it?.toFix()) }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Falha em lastLocation", e)
                        cont.resume(null)
                    }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Erro em lastLocation", e)
            null
        }
    }

    private companion object {
        const val TAG = "FusedLocationProvider"
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun LocationPriority.toGms() = when (this) {
        LocationPriority.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
        LocationPriority.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
    }
}
