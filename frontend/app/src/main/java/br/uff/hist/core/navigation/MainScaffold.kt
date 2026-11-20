package br.uff.hist.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import br.uff.hist.R
import br.uff.hist.explore.ExploreScreen

private enum class MainTab(val labelRes: Int, val icon: ImageVector) {
    EXPLORE(R.string.tab_explore, Icons.Filled.Explore),
    PROFILE(R.string.tab_profile, Icons.Filled.Person),
}

/** Barra inferior Explorar | Perfil; o corpo do Perfil é provisório até a Task 14. */
@Composable
fun MainScaffold(
    onOpenLandmark: (String) -> Unit,
    onSuggestLandmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        when (MainTab.entries[selected]) {
            MainTab.EXPLORE -> ExploreScreen(onOpenLandmark, onSuggestLandmark, Modifier.padding(padding))
            // Task 14 substitui este corpo.
            MainTab.PROFILE -> PlaceholderScreen(Modifier.padding(padding))
        }
    }
}
