package com.moyu.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Stable, data-free surfaces for Compose screenshot tests and Android Studio previews.
 * Haze is deliberately disabled here: RenderEffect is not available in layoutlib.
 */
@Composable
fun PreviewGallery(theme: String = "night") {
    PreviewShell(theme) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PreviewCard {
                MoyuTabBar(current = 0, onSelect = {})
            }
            PreviewCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassIconButton(Glyph.Edit, {}, size = 44.dp)
                    GlassChip("标签", false) {}
                    GlassButton("继续写", {}, Modifier.width(120.dp), glyph = Glyph.Edit)
                }
            }
            PreviewCard {
                PreviewCardBody()
            }
        }
    }
}

/**
 * Theme + no-Haze + page background. Every preview starts here so a composable that
 * looks right in the preview cannot rely on window-level effects it will not get.
 */
@Composable
private fun PreviewShell(theme: String = "night", content: @Composable () -> Unit) {
    MoyuTheme(AppSettings(theme = theme, motion = "none")) {
        CompositionLocalProvider(LocalHazeHandle provides null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(LocalInk.current.palette.bgTop)
                    .padding(18.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun PreviewCardBody() {
    GlassSurface(
        Modifier.fillMaxWidth().height(120.dp),
        radius = 22f,
        strength = .7f,
    ) {
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Txt("玻璃卡片", size = 16f, color = LocalInk.current.palette.ink, weight = FontWeight.Medium)
            Txt("假数据 · 无 Haze", size = 11f, color = LocalInk.current.palette.muted)
        }
    }
}

@Composable
private fun PreviewCard(content: @Composable () -> Unit) {
    GlassSurface(
        Modifier.fillMaxWidth(),
        radius = 22f,
        strength = .62f,
    ) {
        Box(Modifier.padding(12.dp), contentAlignment = Alignment.CenterStart) {
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Previews. Each tile renders one piece in isolation so material tuning does not
// require building an APK; effects that need a real window (Haze blur, native
// EditText, IME) are still verified on the device.
// ---------------------------------------------------------------------------

@Preview(name = "画廊 · 夜间", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun PreviewGalleryNight() = PreviewGallery("night")

@Preview(name = "画廊 · 纸白", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun PreviewGalleryPaper() = PreviewGallery("paper")

@Preview(name = "TabBar · 夜间", widthDp = 412, heightDp = 120, showBackground = true)
@Composable
private fun PreviewTabBarNight() {
    PreviewShell("night") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MoyuTabBar(current = 1, onSelect = {})
        }
    }
}

@Preview(name = "TabBar · 纸白", widthDp = 412, heightDp = 120, showBackground = true)
@Composable
private fun PreviewTabBarPaper() {
    PreviewShell("paper") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MoyuTabBar(current = 1, onSelect = {})
        }
    }
}

/**
 * The liquid bar in isolation, for the screenshot tests in `src/screenshotTest`
 * and `src/test`. `variant` is the focused tab, so the lens can be captured in
 * either slot. There is no window to sample in layoutlib, so this shows the
 * documented fallback fill + rim rather than the real glass.
 */
@Composable
fun LiquidBarPreview(theme: String = "night", variant: Int = 1) {
    PreviewShell(theme) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MoyuTabBar(current = variant, onSelect = {})
        }
    }
}

@Preview(name = "按钮", widthDp = 412, heightDp = 140, showBackground = true)
@Composable
private fun PreviewButtons() {
    PreviewShell("night") {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(Glyph.Edit, {}, size = 44.dp)
            GlassIconButton(Glyph.Search, {}, size = 44.dp)
            GlassChip("标签", false) {}
            GlassChip("已选", true) {}
            GlassButton("继续写", {}, Modifier.width(120.dp), glyph = Glyph.Edit)
        }
    }
}

@Preview(name = "卡片", widthDp = 412, heightDp = 200, showBackground = true)
@Composable
private fun PreviewCardTile() {
    PreviewShell("night") { PreviewCardBody() }
}
