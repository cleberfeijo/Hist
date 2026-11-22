package br.uff.hist.explore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import br.uff.hist.R
import br.uff.hist.core.model.CHAT_JOIN_RADIUS_M
import br.uff.hist.core.model.GeoPoint
import br.uff.hist.core.model.distanceMeters
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.flow.filter

private const val INITIAL_ZOOM = 15f
private const val USER_ZOOM = 16f

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

/** Mapa do Google; a lógica de busca vive no [ExploreViewModel], aqui só há apresentação. */
@Composable
fun ExploreMap(
    state: ExploreUiState,
    onCameraIdle: (GeoPoint, Double) -> Unit,
    onMarkerClick: (String) -> Unit,
    onCenteredOnUser: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val style = remember(dark) {
        MapStyleOptions.loadRawResourceStyle(context, if (dark) R.raw.map_style_dark else R.raw.map_style_light)
    }
    // `centeredOnUser` vive no ViewModel: o mapa é recriado a cada troca Mapa/Lista e, sem isso, voltaria a recentralizar.
    val centeredOnUser = state.centeredOnUser
    val cameraState = rememberCameraPositionState {
        val userStart = state.user?.takeIf { centeredOnUser }
        position = if (userStart != null) {
            CameraPosition.fromLatLngZoom(userStart.toLatLng(), USER_ZOOM)
        } else {
            CameraPosition.fromLatLngZoom(state.initialCamera.toLatLng(), INITIAL_ZOOM)
        }
    }
    var loaded by remember { mutableStateOf(false) }

    // A última posição conhecida chega depois da primeira composição; só vale até o usuário mexer no mapa.
    // `CameraUpdateFactory` só pode ser usado depois que o Maps inicializa (sem chave ele nunca inicializa):
    // antes disso a posição é atribuída direto, o que é seguro.
    LaunchedEffect(state.initialCamera) {
        if (!centeredOnUser) cameraState.position = CameraPosition.fromLatLngZoom(state.initialCamera.toLatLng(), INITIAL_ZOOM)
    }
    LaunchedEffect(state.user != null, loaded) {
        val user = state.user
        if (user != null && !centeredOnUser) {
            onCenteredOnUser()
            if (loaded) {
                cameraState.animate(CameraUpdateFactory.newLatLngZoom(user.toLatLng(), USER_ZOOM))
            } else {
                cameraState.position = CameraPosition.fromLatLngZoom(user.toLatLng(), USER_ZOOM)
            }
        }
    }
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        snapshotFlow { cameraState.isMoving }.filter { !it }.collect {
            val region = cameraState.projection?.visibleRegion ?: return@collect
            val center = cameraState.position.target
            val radius = distanceMeters(
                GeoPoint(center.latitude, center.longitude),
                GeoPoint(region.farLeft.latitude, region.farLeft.longitude),
            )
            onCameraIdle(GeoPoint(center.latitude, center.longitude), radius)
        }
    }

    val icons = remember(context, primary, onPrimary) { CategoryIcons(context, primary.toArgb(), onPrimary.toArgb()) }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraState,
        contentPadding = contentPadding,
        properties = MapProperties(isMyLocationEnabled = state.hasPermission, mapStyleOptions = style),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
        onMapLoaded = { loaded = true },
    ) {
        state.user?.let {
            Circle(
                center = it.toLatLng(),
                radius = CHAT_JOIN_RADIUS_M.toDouble(),
                strokeColor = primary,
                strokeWidth = 3f,
                fillColor = primary.copy(alpha = 0.12f),
            )
        }
        state.markers.forEach { item ->
            val landmark = item.landmark
            Marker(
                state = rememberMarkerState(key = landmark.id, position = landmark.location.toLatLng()),
                title = landmark.name,
                icon = icons.of(landmark.category.icon),
                onClick = {
                    onMarkerClick(landmark.id)
                    true
                },
            )
        }
    }
}

/** Ícones de marcador: círculo na cor primária com o ícone da categoria, um bitmap por categoria. */
private class CategoryIcons(private val context: Context, private val background: Int, private val foreground: Int) {
    private val cache = HashMap<Int, BitmapDescriptor>()

    fun of(iconRes: Int): BitmapDescriptor = cache.getOrPut(iconRes) {
        val size = (MARKER_DP * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background })
        val drawable = checkNotNull(ContextCompat.getDrawable(context, iconRes)).mutate()
        drawable.setTint(foreground)
        val inset = size / 5
        val glyph = drawable.toBitmap(size - 2 * inset, size - 2 * inset)
        canvas.drawBitmap(glyph, inset.toFloat(), inset.toFloat(), null)
        BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private companion object {
        const val MARKER_DP = 36
    }
}
