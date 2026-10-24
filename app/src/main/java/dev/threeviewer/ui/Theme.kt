@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package dev.threeviewer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun ThreeViewerTheme(theme: Int, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val context = LocalContext.current
    SideEffect {
        (context as? ComponentActivity)?.let { activity ->
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val scheme = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFCEC0FF), onPrimary = Color(0xFF35245C),
            primaryContainer = Color(0xFF4C3B74), onPrimaryContainer = Color(0xFFEADDFF),
            secondaryContainer = Color(0xFF494458), onSecondaryContainer = Color(0xFFE8DEF8),
            tertiaryContainer = Color(0xFF623B50), onTertiaryContainer = Color(0xFFFFD8E9),
            surface = Color(0xFF151218), surfaceContainer = Color(0xFF221F26),
            surfaceContainerLow = Color(0xFF1D1A21), surfaceContainerHigh = Color(0xFF2D2930))
        else -> lightColorScheme(primary = Color(0xFF66518E), onPrimary = Color.White,
            primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF241047),
            secondaryContainer = Color(0xFFE8DEF8), onSecondaryContainer = Color(0xFF211B2D),
            tertiaryContainer = Color(0xFFFFD8E9), onTertiaryContainer = Color(0xFF311122),
            surface = Color(0xFFFEF7FF), surfaceContainer = Color(0xFFF3EDF7),
            surfaceContainerLow = Color(0xFFF8F1FC), surfaceContainerHigh = Color(0xFFEDE6F2))
    }
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), content = content)
}
