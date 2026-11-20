package com.moyu.writer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 设置 → 玻璃：三个分组共用同一块 110dp 纸面、同一条 liquidGlassBar 路径。
 *
 * 只换中间那颗控件，底和宽度始终一致，滑杆动一下看到的就是同一套东西。
 * 3.26.66 起输入框 / 按钮表面不再用玻璃，对应预览档随之删除，只剩
 * 标签栏 / 镜片 / 通知三档。
 *
 * 3.26.66：弯折宽度 / 边缘压缩是全局设置，`liquidGlassBar` 内部统一读
 * `AppSettings.glassBend` / `glassSqueeze` 再进 `glassLensBand`——预览和真表面
 * 走的是同一条换算（连 `.55×minSide` 封顶都在同一函数里），这里不写死数值、
 * 也不另拼公式。因此拖动「弯折」两行时本预览会随重组即时重算。
 */
@Composable
internal fun GlassLivePreview(group: Int, s: AppSettings) {
    val ui = LocalInk.current
    val p = ui.palette
    val motion = ui.settings.motion != "none"
    val backdrop = rememberLayerBackdrop()
    Box(
        Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(SmoothCorner(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // The layer the glass samples: a static paper slice. Nothing here
        // changes while a slider moves, so the backdrop is recorded once and
        // only the effects re-run — that is what keeps the drag smooth.
        Column(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(p.paper)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .fillMaxWidth(if (i == 2) .55f else 1f)
                        .height(6.dp)
                        .background(p.ink.copy(alpha = .34f), SmoothCorner(3.dp)),
                )
                Spacer(Modifier.height(9.dp))
            }
        }
        // 3.26.49：三档（标签栏 / 镜片 / 通知）切换不再硬切：
        // 淡入 + 从 97% 轻微放大 180ms；过渡中两块玻璃采样同一层静态纸面，模糊不会闪。
        AnimatedContent(
            targetState = group,
            transitionSpec = {
                val enter = tween<Float>(if (motion) 180 else 0)
                (fadeIn(enter) + scaleIn(initialScale = .97f, animationSpec = enter))
                    .togetherWith(fadeOut(tween(if (motion) 140 else 0)))
                    .using(SizeTransform(clip = false) { _, _ -> tween(0) })
            },
            contentAlignment = Alignment.Center,
            label = "glassPreview",
        ) { g ->
            when (g) {
                0 -> Row(
                    Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .height(52.dp)
                        .liquidGlassBar(
                            backdrop = backdrop,
                            shape = SmoothCorner(26.dp),
                            palette = p,
                            blurDp = s.tabBlur,
                            refractionDp = s.tabRefraction,
                            dispersion = s.tabDispersion,
                            shadowAlpha = s.tabShadow,
                            veil = s.tabBarOpacity,
                            edgeStrength = s.tabEdge,
                        ),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(Glyph.Book, Glyph.Note, Glyph.Chart, Glyph.Sparkles, Glyph.Gear).forEach {
                        LineIcon(it, p.ink, 15.dp)
                    }
                }

                1 -> Box(
                    Modifier.width(230.dp).height(52.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(SmoothCorner(26.dp))
                            .background(p.faint)
                    )
                    Box(
                        Modifier
                            .width(84.dp)
                            .height(44.dp)
                            .liquidGlassBar(
                                backdrop = backdrop,
                                shape = SmoothCorner(22.dp),
                                palette = p,
                                blurDp = s.lensBlur,
                                refractionDp = s.lensRefraction,
                                dispersion = s.lensDispersion,
                                shadowAlpha = s.lensShadow,
                                veil = s.tabLensOpacity,
                                edgeStrength = s.lensEdge,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        LineIcon(Glyph.Book, p.ink, 16.dp)
                    }
                }

                else -> Row(
                    Modifier
                        .height(36.dp)
                        .liquidGlassBar(
                            backdrop = backdrop,
                            shape = SmoothCorner(18.dp),
                            palette = p,
                            blurDp = s.bubbleGlassBlur,
                            refractionDp = s.bubbleGlassRefraction,
                            dispersion = s.bubbleGlassDispersion,
                            shadowAlpha = s.bubbleGlassShadow,
                            veil = s.bubbleGlassOpacity,
                            edgeStrength = s.bubbleGlassEdge,
                        )
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(accentTint(p, ui.accent))
                    )
                    Spacer(Modifier.width(8.dp))
                    Txt("已保存", size = 11f, color = p.ink)
                }
            }
        }
    }
}
