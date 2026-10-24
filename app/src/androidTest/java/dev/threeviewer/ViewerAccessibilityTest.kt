@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package dev.threeviewer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import org.junit.Rule
import org.junit.Test
import androidx.activity.BackEventCompat

class ViewerAccessibilityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun text(id: Int) = rule.activity.getString(id)

    @Test fun licenseNavigationSupportsPredictiveCancelAndCommit() {
        rule.enableAccessibilityChecks()
        rule.onNodeWithContentDescription(text(R.string.settings)).performClick()
        rule.onNodeWithText(text(R.string.open_source_licenses)).performScrollTo().performClick()
        rule.onRoot().tryPerformAccessibilityChecks()
        rule.onNodeWithText("ThreeViewer").performClick()
        rule.onNodeWithTag("license_text").assertIsDisplayed()
        val dispatcher = rule.activity.onBackPressedDispatcher
        rule.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT)) }
        rule.waitForIdle()
        rule.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(200f, 500f, .5f, BackEventCompat.EDGE_LEFT)) }
        rule.waitForIdle()
        rule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        rule.waitForIdle()
        rule.onNodeWithTag("license_text").assertIsDisplayed()
        rule.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT)) }
        rule.waitForIdle()
        rule.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(700f, 500f, .85f, BackEventCompat.EDGE_LEFT)) }
        rule.waitForIdle()
        rule.runOnUiThread { dispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText(text(R.string.open_source_licenses)).assertIsDisplayed()
        rule.onNodeWithTag("license_text").assertDoesNotExist()
        rule.onNodeWithContentDescription(text(R.string.back)).performClick()
        rule.onNodeWithContentDescription(text(R.string.back)).performClick()
        rule.onNodeWithContentDescription(text(R.string.open_model)).assertIsDisplayed()
        rule.runOnUiThread { org.junit.Assert.assertFalse(dispatcher.hasEnabledCallbacks()) }
    }

    @Test fun homeAndSettingsHaveAccessibleControls() {
        rule.enableAccessibilityChecks()
        rule.onNodeWithContentDescription(text(R.string.open_model)).assertHasClickAction()
        rule.onRoot().tryPerformAccessibilityChecks()
        rule.onNodeWithContentDescription(text(R.string.settings)).performClick()
        rule.onNodeWithText(text(R.string.dynamic_color)).assertIsDisplayed().assertHasClickAction()
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test fun sampleRendersWithAlternativeCameraActions() {
        rule.onNodeWithText(text(R.string.sample_name)).performClick()
        rule.waitUntil(30_000) {
            rule.onAllNodesWithContentDescription(text(R.string.reset_view)).fetchSemanticsNodes().any {
                !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            }
        }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.model_view, text(R.string.sample_name)))
            .assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.CustomActions))
        rule.onNodeWithContentDescription(text(R.string.view_options)).performClick()
        rule.onNodeWithContentDescription(text(R.string.rotate_left)).assertHasClickAction()
        rule.onNodeWithContentDescription(text(R.string.zoom_in)).assertHasClickAction()
        rule.onNodeWithContentDescription(text(R.string.zoom_in)).performClick()
        rule.onNodeWithContentDescription(text(R.string.close)).performClick()
        rule.onNodeWithContentDescription(text(R.string.model_info)).performClick()
        rule.onNodeWithText(text(R.string.vulkan)).assertExists()
    }

    @Test fun launcherProvidesMonochromeLayer() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val icon = rule.activity.packageManager.getApplicationIcon(rule.activity.applicationInfo)
            org.junit.Assert.assertTrue(icon is android.graphics.drawable.AdaptiveIconDrawable)
            org.junit.Assert.assertNotNull((icon as android.graphics.drawable.AdaptiveIconDrawable).monochrome)
        }
    }
}
