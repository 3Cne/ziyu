package com.moyu.writer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * The one fixed surface material: a flat, opaque paper card.
 *
 * 3.26.66：旧玻璃链（`GlassSurface` → `Modifier.liquidGlass` + Haze 模糊）已删。
 * 全仓每一处 `MoyuSurface(` 调用点都传了 `flat = true`，非 flat 的玻璃分支
 * （含逐帧 Haze 采样）早就没有被绘制。`flat` 保留在签名里只为避免
 * 同一提交里改 30+ 处调用点；圆角仍是调用点的布局决定。
 */
object GlassStyle {
    const val corner = 26f
}

@Composable
fun MoyuSurface(
    modifier: Modifier = Modifier,
    radius: Float? = null,
    shape: Shape? = null,
    /** 保留参数只为兼容既有调用点：全部传 true，纯色纸卡是唯一实现。 */
    flat: Boolean = false,
    /** Flat fill override. Buttons pass the dock tone so one solid colour is
     *  shared by the toolbar and every button, with no gradient or shadow. */
    tone: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val ui = LocalInk.current
    val resolvedRadius = radius ?: GlassStyle.corner
    val resolvedShape = shape ?: SmoothCorner(resolvedRadius.dp)
    // One flat tone: no diagonal gradient, no caustic bloom, no rim. The card
    // needs a few levels of separation from the page behind it or a pure paper
    // fill disappears into the background gradient. Alpha is forced to 1: the
    // old glass branch kept its paper floor under a RenderEffect, and a
    // translucent flat card would let page text bleed through.
    val fillTone = (tone ?: if (ui.palette.isDark) {
        lerp(ui.palette.paper, Color.White, .04f)
    } else {
        lerp(ui.palette.paper, ui.palette.ink, .04f)
    }).copy(alpha = 1f)
    Box(
        modifier
            .drawBehind {
                // Draw the *actual* shape, never a radius-rounded rectangle: a
                // sheet with only its top corners rounded used to get a fill with
                // four round corners, so the bottom edge stayed round even though
                // the content was clipped square (the 3.9.36 menu bug).
                val outlinePath = Path().apply {
                    addOutline(resolvedShape.createOutline(size, layoutDirection, this@drawBehind))
                }
                drawPath(outlinePath, fillTone)
            }
            .clip(resolvedShape),
        content = content,
    )
}