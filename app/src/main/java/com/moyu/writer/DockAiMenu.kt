package com.moyu.writer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val AiMenuRowH = 42.dp

/** 菜单整体高（三行 + 上 6dp / 下 4dp）。落位时用它把底边贴到能力栏顶边。 */
val AiMenuHeight: Dp = AiMenuRowH * 3 + 10.dp

/** 「AI」的两种形态：✦（两组交线）↔ ✕（对角线）。MorphIcon 在这两套线段之间插值。 */
val MorphAiStar = listOf(
    MorphStroke(.50f, .16f, .50f, .84f),
    MorphStroke(.16f, .50f, .84f, .50f),
)
val MorphAiCross = listOf(
    MorphStroke(.24f, .24f, .76f, .76f),
    MorphStroke(.76f, .24f, .24f, .76f),
)

/**
 * 选择态操作条里的「AI」竖排菜单（3.26.34）。
 *
 * 观感要求（用户原话）：「和能力栏同框那种从一个泡被挤出那种感觉，但是没有脱离的感觉」，
 * 「动画末尾框归位」。
 *
 * 做法：
 * ① 底色用能力栏同一个 tone、左边贴屏幕最左、底边压进栏顶——读作栏体本身被挤出一截；
 * ② 纵向 `scaleY` 用低阻尼弹簧（有过冲），横向 `scaleX = 2 - scaleY` 体积守恒：
 *    被拉起来时变窄、落位时回宽，末尾正好归位；
 * ③ 收的时候同一条弹簧反放（稍快），压回泡里再卸载，不会硬切。
 */
@Composable
fun AiDockMenu(
    open: Boolean,
    tone: Color,
    ink: Color,
    onProof: (() -> Unit)?,
    onPolish: (() -> Unit)?,
    onAsk: (() -> Unit)?,
    onExited: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = remember { Animatable(0f) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(open) {
        if (open) {
            t.animateTo(1f, spring(dampingRatio = .52f, stiffness = 300f))
        } else if (t.value > .01f) {
            t.animateTo(0f, spring(dampingRatio = .62f, stiffness = 520f))
            onExited()
        }
    }
    // 点一项：先压回去，再动作。
    LaunchedEffect(pending) {
        val a = pending ?: return@LaunchedEffect
        t.animateTo(0f, spring(dampingRatio = .62f, stiffness = 520f))
        onExited()
        a()
    }

    val v = t.value.coerceIn(0f, 1f)
    Column(
        modifier
            .width(112.dp)
            .graphicsLayer {
                // 底边锚定在能力栏顶边：整块从栏里被挤出来，底边一刻也不离开栏
                transformOrigin = TransformOrigin(0f, 1f)
                fun shape(x: Float): Float = .16f + .84f * x
                val sy = shape(v)
                scaleY = sy
                // 体积守恒：拉长的时候变窄，落位回宽 —— 末尾正好归位
                scaleX = (2f - sy).coerceIn(.85f, 1.06f)
                alpha = (v * 1.6f).coerceIn(0f, 1f)
            }
            .clip(SmoothCorner(topStart = 16.dp, topEnd = 16.dp))
            .background(tone)
            .padding(top = 6.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        onProof?.let { a -> AiMenuRow(Glyph.Check, "校核", ink) { pending = a } }
        onPolish?.let { a -> AiMenuRow(Glyph.Edit, "修饰", ink) { pending = a } }
        onAsk?.let { a -> AiMenuRow(Glyph.Sparkles, "问", ink) { pending = a } }
    }
}

@Composable
private fun AiMenuRow(glyph: Glyph, label: String, ink: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(AiMenuRowH)
            .bouncy(scale = .96f) { onClick() }
            .padding(start = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(glyph, ink, 15.dp)
        Spacer(Modifier.width(10.dp))
        Txt(label, size = 12f, color = ink)
    }
}
