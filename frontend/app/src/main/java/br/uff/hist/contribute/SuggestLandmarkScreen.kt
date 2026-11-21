package br.uff.hist.contribute

import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.uff.hist.R
import br.uff.hist.core.model.Category
import br.uff.hist.core.model.GeoPoint
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.filter

private const val MAP_ZOOM = 16f
private val MapHeight = 260.dp
private val CollapsedMapHeight = 72.dp
private val PinSize = 40.dp

@Composable
fun SuggestLandmarkScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SuggestLandmarkViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.done) {
        if (state.done) {
            // Toast sobrevive à saída da tela; um snackbar aqui seria cancelado pelo pop.
            Toast.makeText(context, context.getString(R.string.suggest_done), Toast.LENGTH_LONG).show()
            onDone()
        }
    }
    SuggestLandmarkContent(
        state = state,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onCategoryChange = viewModel::onCategoryChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onCuriosityChange = viewModel::onCuriosityChange,
        onSubmit = viewModel::submit,
        modifier = modifier,
        mapContent = { mapModifier ->
            val pin = state.pin
            if (pin == null) {
                Box(mapModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                SuggestMap(initial = pin, onCameraMoved = viewModel::onCameraMoved, modifier = mapModifier)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SuggestLandmarkContent(
    state: SuggestLandmarkUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onCategoryChange: (Category) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCuriosityChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    // Ponto de injeção: o GoogleMap não roda em testes locais (Robolectric).
    mapContent: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.suggest_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        // Com o teclado aberto o mapa encolhe para uma faixa (continua composto: câmera e pino se mantêm).
        val mapHeight by animateDpAsState(if (WindowInsets.isImeVisible) CollapsedMapHeight else MapHeight, label = "mapHeight")
        // O Scaffold já reservou a barra de navegação em `padding`; o teclado só soma o que passar dela.
        Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
            // O mapa fica fora da rolagem do formulário: arrastar o mapa não pode rolar a tela.
            Box(Modifier.fillMaxWidth().height(mapHeight)) {
                mapContent(Modifier.fillMaxSize())
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    // A ponta do alfinete fica no centro exato do mapa.
                    modifier = Modifier.align(Alignment.Center).size(PinSize).offset(y = -PinSize / 2),
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ) {
                    Text(
                        stringResource(R.string.suggest_map_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val enabled = !state.submitting
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.suggest_field_name)) },
                    singleLine = true,
                    enabled = enabled,
                    isError = SuggestFields.NAME in state.fieldErrors,
                    supportingText = state.fieldErrors[SuggestFields.NAME]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryPicker(state.category, state.fieldErrors[SuggestFields.CATEGORY], enabled, onCategoryChange)
                OutlinedTextField(
                    value = state.description,
                    onValueChange = onDescriptionChange,
                    label = { Text(stringResource(R.string.suggest_field_description)) },
                    minLines = 3,
                    enabled = enabled,
                    isError = SuggestFields.DESCRIPTION in state.fieldErrors,
                    supportingText = state.fieldErrors[SuggestFields.DESCRIPTION]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.curiosity,
                    onValueChange = onCuriosityChange,
                    label = { Text(stringResource(R.string.suggest_field_curiosity)) },
                    minLines = 2,
                    enabled = enabled,
                    isError = SuggestFields.CURIOSITY in state.fieldErrors,
                    supportingText = state.fieldErrors[SuggestFields.CURIOSITY]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = onSubmit,
                    enabled = enabled && state.pin != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.suggest_submit)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPicker(selected: Category?, error: String?, enabled: Boolean, onSelect: (Category) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selected?.let { stringResource(it.label) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.suggest_field_category)) },
            leadingIcon = selected?.let { { Icon(painterResource(it.icon), contentDescription = null) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Category.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(stringResource(category.label)) },
                    leadingIcon = { Icon(painterResource(category.icon), contentDescription = null) },
                    onClick = {
                        onSelect(category)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** A câmera começa em [initial]; o centro vira o pino a cada parada do mapa (o alfinete é desenhado por cima). */
@Composable
private fun SuggestMap(initial: GeoPoint, onCameraMoved: (GeoPoint) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val style = remember(dark) {
        MapStyleOptions.loadRawResourceStyle(context, if (dark) R.raw.map_style_dark else R.raw.map_style_light)
    }
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(initial.latitude, initial.longitude), MAP_ZOOM)
    }
    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.isMoving }.filter { !it }.collect {
            val target = cameraState.position.target
            onCameraMoved(GeoPoint(target.latitude, target.longitude))
        }
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraState,
        properties = MapProperties(mapStyleOptions = style),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
    )
}
