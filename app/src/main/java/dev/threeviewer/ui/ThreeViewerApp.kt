@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
package dev.threeviewer.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.animation.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.navigation.compose.*
import dev.threeviewer.BuildConfig
import dev.threeviewer.R
import dev.threeviewer.ViewerState
import dev.threeviewer.ViewerViewModel
import dev.threeviewer.data.ModelSource

@Composable
fun ThreeViewerApp(state: ViewerState, vm: ViewerViewModel) {
    val context = LocalContext.current
    val nav = rememberNavController()
    var handledRequest by rememberSaveable { mutableLongStateOf(0) }
    LaunchedEffect(state.openRequest) {
        if (state.openRequest > handledRequest) {
            handledRequest = state.openRequest
            nav.navigate("viewer") { popUpTo("home"); launchSingleTop = true }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.open(it)
        }
    }
    val open = { picker.launch(arrayOf("*/*")) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    Surface(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = "home",
            enterTransition = { fadeIn(effects) + slideInHorizontally(spatial) { direction * it / 12 } },
            exitTransition = { fadeOut(effects) },
            popEnterTransition = { fadeIn(effects) + scaleIn(effects, initialScale = .96f) },
            popExitTransition = { fadeOut(effects) + scaleOut(effects, targetScale = .9f) }) {
            composable("home") {
                HomeScreen(state.recent, open, { vm.open() }, { vm.open(Uri.parse(it.uri)) }, vm::remove,
                    { nav.navigate("settings") { launchSingleTop = true } })
            }
            composable("settings") {
                SettingsScreen(state, vm, { nav.navigateUp() }, { nav.navigate("licenses") { launchSingleTop = true } })
            }
            composable("licenses") {
                LicensesScreen({ nav.navigateUp() }, { id -> nav.navigate("license/$id") })
            }
            composable("license/{id}") { entry ->
                LicenseTextScreen(entry.arguments?.getString("id").orEmpty(), { nav.navigateUp() })
            }
            composable("viewer") { entry ->
                DisposableEffect(entry) {
                    onDispose {
                        // Keep the model alive through the outgoing animation and cancelled back gestures.
                        if (runCatching { nav.getBackStackEntry("viewer") }.isFailure) vm.home()
                    }
                }
                LaunchedEffect(Unit) {
                    if (state.openRequest == 0L) nav.popBackStack("home", false)
                }
                when {
                    state.model != null -> ViewerScreen(state.model, { nav.navigateUp() }, open)
                    state.loading -> Scaffold { padding ->
                        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                LoadingIndicator()
                                Text(stringResource(R.string.loading), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                TextButton({ nav.navigateUp() }) { Text(stringResource(R.string.cancel)) }
                            }
                        }
                    }
                }
            }
        }
    }
    val dismissError = {
        vm.dismissError()
        nav.popBackStack("home", false)
        Unit
    }
    state.error?.let { error ->
        AlertDialog(onDismissRequest = dismissError,
            title = { Text(stringResource(R.string.open_failed)) },
            text = { Text(stringResource(error)) },
            confirmButton = { TextButton({ dismissError(); open() }) { Text(stringResource(R.string.open_model)) } },
            dismissButton = { TextButton(dismissError) { Text(stringResource(R.string.close)) } })
    }
}

@Composable
private fun HomeScreen(recent: List<ModelSource>, open: () -> Unit, sample: () -> Unit,
    openRecent: (ModelSource) -> Unit, remove: (String) -> Unit, settings: () -> Unit) {
    val context = LocalContext.current
    val openLabel = stringResource(R.string.open_model)
    Scaffold(topBar = {
        MediumFlexibleTopAppBar(title = { Text(stringResource(R.string.models)) },
            actions = { ActionIcon(R.drawable.ic_settings, R.string.settings, onClick = settings) })
    }, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = open, icon = { Symbol(R.drawable.ic_folder_open) },
            modifier = Modifier.semantics { contentDescription = openLabel },
            text = { Text(openLabel) })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(onClick = sample, Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(96.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.sample), style = MaterialTheme.typography.labelLarge)
                            Text(stringResource(R.string.sample_name), style = MaterialTheme.typography.headlineMedium)
                            Text("GLB", style = MaterialTheme.typography.bodyMedium)
                        }
                        Symbol(R.drawable.ic_navigate_next)
                    }
                }
            }
            item { SectionTitle(R.string.recent, Modifier.padding(start = 4.dp, top = 8.dp)) }
            if (recent.isEmpty()) item {
                Box(Modifier.fillMaxWidth().heightIn(min = 136.dp).background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(24.dp)).padding(24.dp),
                    contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_recent), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
            items(recent, key = { it.uri }) { source ->
                var menu by remember { mutableStateOf(false) }
                Surface(onClick = { openRecent(source) }, shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { Symbol(R.drawable.ic_view_in_ar) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(source.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text(Formatter.formatFileSize(context, source.size), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            ActionIcon(R.drawable.ic_more_vert, R.string.more) { menu = true }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.remove_recent)) },
                                    leadingIcon = { Symbol(R.drawable.ic_delete) }, onClick = { menu = false; remove(source.uri) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: ViewerState, vm: ViewerViewModel, back: () -> Unit, licenses: () -> Unit) {
    val context = LocalContext.current
    var clear by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) },
        navigationIcon = { ActionIcon(R.drawable.ic_arrow_back, R.string.back, onClick = back) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SectionTitle(R.string.appearance)
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.theme), Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
                    listOf(R.string.system_default, R.string.light, R.string.dark).forEachIndexed { index, label ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(state.theme == index, role = Role.RadioButton, onClick = { vm.theme(index) })
                            .padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            RadioButton(state.theme == index, null)
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    if (LocalDensity.current.fontScale >= 1.5f) Column(Modifier.fillMaxWidth()
                        .toggleable(state.dynamic, role = Role.Switch, onValueChange = vm::dynamic).padding(20.dp)) {
                        Text(stringResource(R.string.dynamic_color), style = MaterialTheme.typography.bodyLarge)
                        Switch(state.dynamic, null, Modifier.align(Alignment.End).padding(top = 12.dp))
                    } else Row(Modifier.fillMaxWidth().toggleable(state.dynamic, role = Role.Switch, onValueChange = vm::dynamic)
                        .padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.dynamic_color), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(state.dynamic, null)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                Surface(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")))
                }, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
                    ListItem(headlineContent = { Text(stringResource(R.string.language)) },
                        supportingContent = { Text(stringResource(R.string.language_summary),
                            style = MaterialTheme.typography.bodyMedium.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)) },
                        leadingContent = { Symbol(R.drawable.ic_language) }, trailingContent = { Symbol(R.drawable.ic_navigate_next) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.padding(4.dp))
                }
            }
            SectionTitle(R.string.recent)
            OutlinedButton(onClick = { clear = true }, enabled = state.recent.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Symbol(R.drawable.ic_delete); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.clear_recent))
            }
            SectionTitle(R.string.about)
            Text("ThreeViewer", style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Surface(onClick = licenses, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                ListItem(headlineContent = { Text(stringResource(R.string.open_source_licenses)) },
                    trailingContent = { Symbol(R.drawable.ic_navigate_next) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.heightIn(min = 56.dp))
            }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text(stringResource(R.string.clear_recent)) },
        text = { Text(stringResource(R.string.clear_recent_message)) },
        confirmButton = { TextButton({ vm.clearRecent(); clear = false }) { Text(stringResource(R.string.clear)) } },
        dismissButton = { TextButton({ clear = false }) { Text(stringResource(R.string.cancel)) } })
}
