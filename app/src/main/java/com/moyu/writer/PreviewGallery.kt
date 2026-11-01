package com.moyu.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Stable, data-free surfaces for Compose screenshot tests and Android Studio previews.
 * Haze is deliberately disabled here: RenderEffect is not available in layoutlib.
 */
@Composable
fun PreviewGallery() {
    MoyuTheme(AppSettings(theme = "night", motion = "none")) {
        CompositionLocalProvider(LocalHazeHandle provides null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(LocalInk.current.palette.bgTop)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                PreviewCard {
                    MoyuTabBar(current = 0, haze = null) {}
                }
                PreviewCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassIconButton(Glyph.Edit, {}, size = 44.dp)
                        GlassChip("标签", false) {}
                        GlassButton("继续写", {}, Modifier.width(120.dp), glyph = Glyph.Edit)
                    }
                }
                PreviewCard {
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
            }
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
