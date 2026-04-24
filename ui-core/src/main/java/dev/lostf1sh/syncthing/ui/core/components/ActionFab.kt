package dev.lostf1sh.syncthing.ui.core.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.lostf1sh.syncthing.ui.core.R

/**
 * Expressive medium extended FAB for primary screen actions.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddActionFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.add_action_label),
    icon: ImageVector = Icons.Default.Add,
) {
    MediumExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(label) },
    )
}

/**
 * Expressive small extended FAB for secondary screen actions.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SecondaryActionFab(
    onClick: () -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    SmallExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(label) },
    )
}
