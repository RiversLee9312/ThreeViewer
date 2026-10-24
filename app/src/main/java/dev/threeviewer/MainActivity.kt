package dev.threeviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.threeviewer.ui.ThreeViewerApp
import dev.threeviewer.ui.ThreeViewerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ViewerViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) accept(intent)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            ThreeViewerTheme(state.theme, state.dynamic) { ThreeViewerApp(state, viewModel) }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); accept(intent) }
    private fun accept(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) intent.data?.takeIf { it.scheme == "content" || it.scheme == "file" }?.let { viewModel.open(it) }
    }
}
