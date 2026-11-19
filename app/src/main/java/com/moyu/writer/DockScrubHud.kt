package com.moyu.writer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

/**
 * 横滑移光标时的进度条 HUD（3.26.39）。
 *
 * 形态：能力栏正上方一条胶囊底，里面一根 2dp 轨道 + 一个 10dp 的小白球，
 * 右侧「当前 / 总字数」。白球按 caret 比例定位，并比手指稍晚到位（粘滞感）。
 * 进出：从底边被挤出来（scaleY 0.6→1 + alpha），收起反向；松手后由调用方延迟卸载。
 */
@Composable
fun DockScrubHud(
    ratio: Float,
    chars: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val tone = dockTone(ui)
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, spring(dampingRatio = .6f, stiffness = 380f)) }
    val v = t.value.coerceIn(0f, 1f)
    var trackW by remember { mutableIntStateOf(0) }

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                alpha = (v * 1.5f).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(.5f, 1f)
                scaleY = .6f + .4f * v
            }
            .clip(SmoothCorner(18.dp))
            .background(tone)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(18.dp)
                .onGloballyPositioned { trackW = it.size.width },
            contentAlignment = Alignment.CenterStart,
        ) {
            // 轨道
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(p.ink.copy(alpha = .18f)),
            )
            // 小白球：位置跟手，位移用略软的弹簧（观感上晚一拍）
            val x = (trackW.toFloat() * ratio.coerceIn(0f, 1f)).coerceAtLeast(0f)
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = (x - 10.dp.toPx()).coerceAtLeast(-10.dp.toPx())
                    }
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
        SpacerH(12.dp)
        Txt("$chars / $total 字", size = 8.5f, color = p.ink.copy(alpha = .62f), maxLines = 1)
    }
}

@Composable
private fun SpacerH(w: androidx.compose.ui.unit.Dp) {
    Box(Modifier.width(w))
}
