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
 * 设置 → 玻璃：五个分组共用同一块 110dp 纸面、同一条 liquidGlassBar 路径。
 *
 * 以前标签栏/镜片走一张图、输入框/按钮走另一张图，宽度和画法都不一样，滑杆
 * 动一下看到的不是同一套东西。现在只换中间那颗控件，底和宽度始终一致。
 *
 * 3.26.61：标签栏与镜片是同一套材质（六项设置经 [glassMaterial] 一处换算），
 * 所以两个分组的预览喂同一组值；形状用 RoundedCornerShape 而不是
 * SmoothCornerShape——AGSL 厚度场只接 RoundedCornerShape，用后者预览会退回
 * Backdrop `lens()`，就又变成「预览一套、真表面一套」。
 */
@Composable
internal fun GlassLivePreview(group: Int, s: AppSettings) {
    val ui = LocalInk.current
    val p = ui.palette
    val motion = ui.settings.motion != "none"
    val backdrop = rememberLayerBackdrop()
    val glass = s.glassMaterial()
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
        // 3.26.49：五档（标签栏 / 镜片 / 输入框 / 按钮 / 通知）切换不再硬切：
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
                            // RoundedCornerShape：真条是胶囊（CircleShape 也是
                            // RoundedCornerShape），这样预览才真的走 AGSL 厚度场。
                            shape = RoundedCornerShape(26.dp),
                            palette = p,
                            // 新材质没有「整条模糊」项，与真条一致固定 0。
                            blurDp = 0f,
                            refractionDp = glass.bevelDp,
                            dispersion = glass.dispersion,
                            shadowAlpha = s.tabShadow,
                            veil = glass.veil,
                            edgeStrength = glass.rim,
                            meniscus = glass.meniscus,
                            rimBlurPx = glass.edgeBlurPx,
                            rimK = glass.rim,
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
                    // 真镜片走 GlassBar.glassLens（同一段厚度场代码、同一套六项
                    // 参数、同一 12dp/6dp 阴影），预览直接调同一个函数，不另画。
                    Box(
                        Modifier
                            .width(84.dp)
                            .height(44.dp)
                            .glassLens(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(22.dp),
                                palette = p,
                                material = glass,
                                shadowAlpha = s.lensShadow,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        LineIcon(Glyph.Book, p.ink, 16.dp)
                    }
                }

                2 -> Box(
                    Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .height(48.dp)
                        .liquidGlassBar(
                            backdrop = backdrop,
                            shape = SmoothCorner(22.dp),
                            palette = p,
                            blurDp = s.fieldGlassBlur,
                            refractionDp = s.fieldGlassRefraction,
                            dispersion = s.fieldGlassDispersion,
                            shadowAlpha = s.fieldGlassShadow,
                            veil = s.fieldGlassOpacity,
                            edgeStrength = s.fieldGlassEdge,
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(Modifier.padding(horizontal = 14.dp)) {
                        Txt("问这本书的任何事", size = 12.5f, color = p.muted)
                    }
                }

                3 -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Glyph.Link, Glyph.ArrowUp).forEach { glyph ->
                        Box(
                            Modifier
                                .size(46.dp)
                                .liquidGlassBar(
                                    backdrop = backdrop,
                                    shape = CircleShape,
                                    palette = p,
                                    blurDp = s.btnGlassBlur,
                                    refractionDp = s.btnGlassRefraction,
                                    dispersion = s.btnGlassDispersion,
                                    shadowAlpha = s.btnGlassShadow,
                                    veil = s.btnGlassOpacity,
                                    edgeStrength = s.btnGlassEdge,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            LineIcon(glyph, p.ink, 18.dp)
                        }
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
