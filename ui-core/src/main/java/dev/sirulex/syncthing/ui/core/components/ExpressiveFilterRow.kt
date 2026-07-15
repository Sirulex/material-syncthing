package dev.sirulex.syncthing.ui.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Expressive filter chip row for list screens.
 *
 * @param filters Ordered list of filter definitions (label + selected state).
 * @param onFilterSelected Called when a filter chip is tapped.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveFilterRow(
    filters: List<FilterDefinition>,
    onFilterSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEachIndexed { index, filter ->
            FilterChip(
                selected = filter.selected,
                onClick = { onFilterSelected(index) },
                label = { Text(filter.label) },
            )
        }
    }
}

/**
 * Single filter chip definition for [ExpressiveFilterRow].
 */
data class FilterDefinition(
    val label: String,
    val selected: Boolean,
)
