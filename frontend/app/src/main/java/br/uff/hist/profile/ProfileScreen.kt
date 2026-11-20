package br.uff.hist.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.uff.hist.R
import br.uff.hist.core.designsystem.LocalSecondaryText
import br.uff.hist.core.model.User

/** Corpo da aba Perfil. A sessão chega de fora (o `HistNavHost` já a observa). */
@Composable
fun ProfileScreen(
    user: User?,
    onMyContributions: () -> Unit,
    onModeration: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    // Papéis podem mudar no servidor (ex.: promoção a moderador); falhas são ignoradas.
    LaunchedEffect(Unit) { viewModel.refresh() }
    if (user == null) return
    ProfileContent(user, onMyContributions, onModeration, onSettings, modifier)
}

@Composable
fun ProfileContent(
    user: User,
    onMyContributions: () -> Unit,
    onModeration: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("${user.name} ${user.lastName}".trim(), style = MaterialTheme.typography.headlineSmall)
            Text(user.email, style = MaterialTheme.typography.bodyMedium, color = LocalSecondaryText.current)
        }
        HorizontalDivider()
        ProfileItem(R.string.profile_my_contributions, onMyContributions)
        if (user.isModerator) ProfileItem(R.string.profile_moderation, onModeration)
        ProfileItem(R.string.profile_settings, onSettings)
    }
}

@Composable
private fun ProfileItem(label: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
