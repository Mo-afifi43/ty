package com.mhma.smartlearn.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mhma.smartlearn.util.TimeText

/** "Due tomorrow", "Overdue by 2 days"... coloured by how worried you should be. */
@Composable
fun DueChip(
    dueMillis: Long,
    completed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val label = TimeText.dueLabel(dueMillis)
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        completed -> colors.surfaceVariant to colors.onSurfaceVariant
        label.urgency == TimeText.Urgency.OVERDUE -> colors.errorContainer to colors.onErrorContainer
        label.urgency == TimeText.Urgency.TODAY -> colors.tertiaryContainer to colors.onTertiaryContainer
        label.urgency == TimeText.Urgency.SOON -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.surfaceVariant to colors.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (completed) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (completed) "Done" else label.text,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
