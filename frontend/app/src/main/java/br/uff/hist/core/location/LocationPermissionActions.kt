package br.uff.hist.core.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import br.uff.hist.R

/** Pede localização (qualquer precisão) ou a precisa; [Dialog] explica como ir aos ajustes depois de uma negação permanente. */
class LocationPermissionActions internal constructor(
    val requestLocation: () -> Unit,
    val requestPrecise: () -> Unit,
    private val showSettings: Boolean,
    private val dismiss: () -> Unit,
    private val openSettings: () -> Unit,
) {
    @Composable
    fun Dialog() {
        if (!showSettings) return
        AlertDialog(
            onDismissRequest = dismiss,
            text = { Text(stringResource(R.string.permission_denied_settings)) },
            confirmButton = {
                TextButton(onClick = { dismiss(); openSettings() }) { Text(stringResource(R.string.open_app_settings)) }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** [onResult] roda depois de cada resposta do sistema; o ViewModel relê as permissões do [LocationProvider]. */
@Composable
fun rememberLocationPermissionActions(onResult: () -> Unit): LocationPermissionActions {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    var showSettings by remember { mutableStateOf(false) }
    var askedPrecise by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        currentOnResult()
        val satisfied = if (askedPrecise) result[Manifest.permission.ACCESS_FINE_LOCATION] == true else result.values.any { it }
        val activity = context.findActivity()
        // Negada e o sistema não vai mais perguntar: só os ajustes do app resolvem.
        if (!satisfied && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            showSettings = true
        }
    }
    return LocationPermissionActions(
        requestLocation = {
            askedPrecise = false
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        },
        requestPrecise = {
            askedPrecise = true
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        },
        showSettings = showSettings,
        dismiss = { showSettings = false },
        openSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
