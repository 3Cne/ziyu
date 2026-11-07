package com.moyu.writer

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class PreviewGalleryScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun gallery() {
        compose.setContent { PreviewGallery() }
        compose.onRoot().captureRoboImage("build/screens/preview-gallery.png")
    }

    @Test
    fun liquidBarPaper() {
        compose.setContent { LiquidBarPreview("paper", 1) }
        compose.onRoot().captureRoboImage("build/screens/liquid-bar-paper.png")
    }

    @Test
    fun liquidBarNight() {
        compose.setContent { LiquidBarPreview("night", 1) }
        compose.onRoot().captureRoboImage("build/screens/liquid-bar-night.png")
    }
}
