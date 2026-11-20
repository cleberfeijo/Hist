package br.uff.hist.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.uff.hist.R

/** Tela provisória das rotas que pertencem a tasks seguintes. */
@Composable
fun PlaceholderScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.placeholder_soon))
    }
}

/** Provisório: a Task 7 troca por a explicação de localização + pedido de permissão. */
@Composable
fun LocationOnboardingPlaceholder(onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.placeholder_soon))
        Button(onClick = onContinue) { Text(stringResource(R.string.onboarding_continue)) }
    }
}
