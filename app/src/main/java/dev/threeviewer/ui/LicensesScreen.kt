@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.threeviewer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.threeviewer.R
import org.json.JSONArray

private data class LicenseEntry(val id: String, val title: String, val license: String, val file: String)

@Composable
private fun licenseEntries(): List<LicenseEntry> {
    val context = LocalContext.current
    return remember {
        val entries = JSONArray(context.assets.open("licenses/index.json").bufferedReader().use { it.readText() })
        List(entries.length()) { i -> entries.getJSONObject(i).let {
            LicenseEntry(it.getString("id"), it.getString("title"), it.getString("license"), it.getString("file"))
        } }
    }
}

@Composable
fun LicensesScreen(back: () -> Unit, open: (String) -> Unit) {
    val entries = licenseEntries()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.open_source_licenses)) },
        navigationIcon = { ActionIcon(R.drawable.ic_arrow_back, R.string.back, onClick = back) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.id }) { entry ->
                Surface(onClick = { open(entry.id) }, shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    ListItem(headlineContent = { Text(entry.title) },
                        supportingContent = { Text(entry.license) },
                        trailingContent = { Symbol(R.drawable.ic_navigate_next) },
                        modifier = Modifier.heightIn(min = 72.dp),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
                }
            }
        }
    }
}

@Composable
fun LicenseTextScreen(id: String, back: () -> Unit) {
    val entry = licenseEntries().firstOrNull { it.id == id } ?: return
    val context = LocalContext.current
    val paragraphs = remember(entry.id) {
        context.assets.open("licenses/${entry.file}").bufferedReader().use { it.readText() }
            .split(Regex("\\n\\s*\\n"))
    }
    Scaffold(topBar = { TopAppBar(title = { Text(entry.title) },
        navigationIcon = { ActionIcon(R.drawable.ic_arrow_back, R.string.back, onClick = back) }) }) { padding ->
        SelectionContainer {
            LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("license_text"), contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text(entry.license, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
                items(paragraphs) { paragraph -> Text(paragraph, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
