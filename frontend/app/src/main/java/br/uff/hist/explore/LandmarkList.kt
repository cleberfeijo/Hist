package br.uff.hist.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.uff.hist.R
import br.uff.hist.core.designsystem.LocalSecondaryText
import br.uff.hist.core.designsystem.formatDistance
import coil3.compose.AsyncImage

@Composable
fun LandmarkList(
    items: List<LandmarkItem>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.landmark.id }) { LandmarkRow(it, onClick) }
    }
}

@Composable
private fun LandmarkRow(item: LandmarkItem, onClick: (String) -> Unit) {
    val landmark = item.landmark
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable { onClick(landmark.id) },
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val photoModifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
            if (landmark.imageUrl != null) {
                AsyncImage(
                    model = landmark.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = photoModifier,
                    error = painterResource(landmark.category.icon),
                )
            } else {
                Image(painterResource(landmark.category.icon), contentDescription = null, modifier = photoModifier.padding(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(landmark.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(landmark.category.label),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalSecondaryText.current,
                )
                Text(
                    pluralStringResource(R.plurals.curiosity_count, landmark.curiosityCount, landmark.curiosityCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalSecondaryText.current,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.distanceM != null) {
                    Text(formatDistance(item.distanceM), style = MaterialTheme.typography.labelMedium, color = LocalSecondaryText.current)
                }
                if (item.chatAvailable) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                        Text(
                            stringResource(R.string.chat_chip),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
