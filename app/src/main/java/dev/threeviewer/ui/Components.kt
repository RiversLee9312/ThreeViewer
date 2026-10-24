package dev.threeviewer.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun Symbol(@DrawableRes icon: Int, description: String? = null, modifier: Modifier = Modifier) {
    Icon(painterResource(icon), description, modifier.size(24.dp))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionIcon(@DrawableRes icon: Int, @StringRes label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    val text = stringResource(label)
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } }, state = rememberTooltipState()) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp), enabled = enabled, shapes = IconButtonDefaults.shapes()) { Symbol(icon, text) }
    }
}

@Composable
fun SectionTitle(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(stringResource(text), modifier.semantics { heading() }.padding(top = 16.dp, bottom = 12.dp),
        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}
