package br.uff.hist.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.uff.hist.R
import br.uff.hist.core.designsystem.LocalSecondaryText
import br.uff.hist.core.model.ModerationStatus

@Composable
fun StatusBadge(status: ModerationStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        ModerationStatus.PENDING -> R.string.status_pending to LocalSecondaryText.current
        ModerationStatus.APPROVED -> R.string.status_approved to MaterialTheme.colorScheme.primary
        ModerationStatus.REJECTED -> R.string.status_rejected to MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color),
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
