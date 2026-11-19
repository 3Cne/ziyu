package com.moyu.writer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val AiMenuRowH = 42.dp

/** 菜单整体高（三行 + 上 6dp / 下 4dp）。 */
val AiMenuHeight: Dp = AiMenuRowH * 3 + 10.dp

/** 共享元素两端的形状：只圆上缘（下缘与能力栏同色相接）。 */
fun aiMenuShape() = SmoothCorner(topStart = 16.dp, topEnd = 16.dp)

/** 「AI」的两种形态：✦（两组交线）↔ ✕（对角线）。MorphIcon 在这两套线段之间插值。 */
val MorphAiStar = listOf(
    MorphStroke(.50f, .16f, .50f, .84f),
    MorphStroke(.16f, .50f, .84f, .50f),
)
val MorphAiCross = listOf(
    MorphStroke(.24f, .24f, .76f, .76f),
    MorphStroke(.76f, .24f, .24f, .76f),
)

/** 3.26.35 的柔曲线：Material 3 空间类 spring（damping .6 / stiffness 380）。
 *  之前用的 .52/300 比官方任何 token 都更弹更慢，观感「怪」就是它。 */
fun aiSpring() = spring<Float>(dampingRatio = .6f, stiffness = 380f)

/**
 * 选择态操作条里的「AI」竖排菜单（3.26.35，共享元素版）。
 *
 * 动画**不在这个文件里**：菜单是 `SharedTransitionLayout` 的「进入端」，bounds 与形状由
 * `sharedBounds` 从 AI 图标格插值过来（`RemeasureToBounds` = 内容按目标尺寸重排、不被拉伸，
 * 配合 overlay clip 就是「被挤出来」）。因此这里只负责画三行。
 */
@Composable
fun AiDockMenu(
    tone: Color,
    ink: Color,
    onProof: () -> Unit,
    onPolish: () -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(112.dp)
            .clip(aiMenuShape())
            .background(tone)
            .padding(top = 6.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        AiMenuRow(Glyph.Check, "校核", ink, onProof)
        AiMenuRow(Glyph.Edit, "修饰", ink, onPolish)
        AiMenuRow(Glyph.Sparkles, "问", ink, onAsk)
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

/** AI 图标格（共享元素的「退出端」）：✦ ↔ ✕ 用 MorphIcon 换形。 */
@Composable
fun AiDockCell(
    open: Boolean,
    onBar: Color,
    onBarDim: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val morph by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = aiSpring(),
        label = "aiMorph",
    )
    Column(
        modifier
            .width(36.dp)
            .bouncy(scale = .88f) { onToggle() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MorphIcon(
            progress = morph,
            a = MorphAiStar,
            b = MorphAiCross,
            modifier = Modifier.size(16.dp),
            color = onBar,
        )
        Spacer(Modifier.height(3.dp))
        Txt("AI", size = 7.6f, color = onBarDim, maxLines = 1)
    }
}
