package com.moyu.writer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 3.26.42：横滑移光标时「工具行被替换成的那条轴」的状态与画法。
 *
 * 用户原话：「直接给工具栏替换的轴，而且不是进度条，是用来跟随手指位置的，
 * 要求来去都有动画」。所以这里不再有胶囊底、轨道进度和「当前 / 总字数」：
 * 轴就是一条 2dp 的线 + 一个 12dp 的白球，白球的 x 严格等于手指在栏内的 x
 * （两端各留 16dp、clamp），光标按白球的绝对比例映射
 * （见 Editor.kt 的 `dockScrubGesture`）。
 *
 * 状态提到页面层 [WritingScreen] 只为一件事：能力栏收起（abilityT < .5）
 * 时要立刻取消并复位。动画值（0 = 工具行，1 = 轴）留在 EditorDock 里，
 * 这样每帧只让这一行的绘制失效，不会重组整条能力栏。
 */
internal class ScrubAxisState {
    /** 手势进行中；false 时轴走退场动画（或被强制复位）。 */
    var active by mutableStateOf(false)

    /** 手指在这一行里的 x（px），-1 = 还没定位。 */
    var fingerX by mutableFloatStateOf(-1f)

    /** 这一行的实测宽度（px），绝对映射要用它换算比例。 */
    var rowW by mutableIntStateOf(0)

    /** 强制复位计数：能力栏收起时页面层 +1，EditorDock 收到后直接贴回工具行。 */
    var resetTick by mutableIntStateOf(0)

    /** 手势结束：轴收回去。 */
    fun end() {
        active = false
    }

    /** 位置失效（这一行被换掉 / 页面离开）：下一帧起别再画白球。 */
    fun abort() {
        active = false
        fingerX = -1f
    }

    /** 「abilityT < .5 立即取消并复位」：不走退场动画，直接归零。 */
    fun forceReset() {
        abort()
        resetTick++
    }
}

/**
 * 工具行的「轴」覆盖层。画在工具行同一位置、同一行高上（调用方给 fillMaxSize），
 * 不参与测量、不抬高能力栏，所以它不是上一版那种浮在栏上方的 HUD。
 *
 * [progress] 返回 0..1（进 / 退场弹簧值，>1 的过冲在这里 clamp）：
 *  - 轴的两端从白球所在的 x 向左右插值展开到两段 16dp 内边距；
 *  - 透明度跟 progress 走，所以「来」「去」都是连续过渡，没有 if/else 硬切。
 */
@Composable
internal fun DockScrubAxis(
    state: ScrubAxisState,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val p = LocalInk.current.palette
    // Canvas 的 draw lambda 里读 state / progress：手指每动一帧只失效这一块的绘制，
    // 不触发重组（长章节里边拖边 setSelection 已经够忙了）。
    Canvas(modifier) {
        val t = progress().coerceIn(0f, 1f)
        if (t <= .002f) return@Canvas
        val pad = 16.dp.toPx()
        val w = size.width
        val left = pad.coerceAtMost(w / 2f)
        val right = (w - pad).coerceAtLeast(left)
        val rawX = state.fingerX
        val thumbX = (if (rawX >= 0f) rawX else w / 2f).coerceIn(left, right)
        val cy = size.height / 2f
        // 从白球处向左右展开：两端同时插值，读作一条轴被拉出来。
        val axisLeft = thumbX + (left - thumbX) * t
        val axisRight = thumbX + (right - thumbX) * t
        drawLine(
            color = p.ink.copy(alpha = .18f * t),
            start = Offset(axisLeft, cy),
            end = Offset(axisRight, cy),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        // 白球 12dp（半径 6dp）：位置严格跟手，不做弹簧滞后；尺寸随展开略放大。
        val r = 6.dp.toPx() * (.55f + .45f * t)
        drawCircle(Color.White.copy(alpha = t), radius = r, center = Offset(thumbX, cy))
        // 浅色主题下白球压在纸色上会糊，加一圈 0.75dp 的淡墨描边（不是厚边框）。
        drawCircle(
            color = p.ink.copy(alpha = .16f * t),
            radius = r,
            center = Offset(thumbX, cy),
            style = Stroke(width = .75.dp.toPx()),
        )
    }
}
