package com.moyu.writer

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/** 镜片落下：阻尼 .78、刚度 380，比页面（pageStiffness ≈321）略快，读作「镜片领着页面走」。 */
private val LensSettle = spring<Float>(dampingRatio = .78f, stiffness = 380f, visibilityThreshold = 1e-3f)

/** 镜片抬起/放下：快起、略带过冲，读作「提起来」。 */
private val LensLift = spring<Float>(dampingRatio = .56f, stiffness = 520f, visibilityThreshold = 1e-3f)

/**
 * 3.26.67：选中镜片尺寸的**唯一事实源**。
 *
 * 真机（1220px / density 3.25）条宽 343.38dp，4 格 → **每格 83.35dp**。用户要求
 * 「镜片不能大于标签框、扁一点点的圆角矩形、越润越好」：
 *  - 宽 72dp < 83.35dp → 左右各余 ≈5.7dp，静止态不会压到邻格；
 *  - 高 56dp < 条高 64dp → 上下各留 4dp，**不再凸出条**（3.26.66 是 76×72、上下各鼓 4dp）；
 *  - 宽高比 72:56 ≈ 1.29，读作「扁圆角矩形」而不是胶囊。
 * 长按拖动时的粘滞水滴拉伸（最多 1.75×）**允许**超出格子甚至超出条：用户明确要求
 * 「只有长按拖动镜片时其大小才可以超过标签栏」，所以这里不加任何 clamp / 裁剪，
 * 也不为了让拖动不越界去缩小静止尺寸。
 */
private val TAB_LENS_W_DP = 72.dp
private val TAB_LENS_H_DP = 56.dp

/**
 * 3.26.67：镜片圆角 18dp（< 高/2 = 28dp），比 3.26.21 的纯胶囊（高/2）扁一档；
 * 再交给 [SmoothCornerShape] 做曲率连续的平滑圆角，切点不会出现「棱角」——
 * 用户要的「越润越好」由这层平滑给，尺寸交给上面两个常量。
 */
private val TAB_LENS_CORNER_DP = 18.dp

/**
 * Floating four-tab bar made from a real liquid-glass surface.
 *
 * 3.24.1 修的四条（用户报「移动时全是 bug、没有对劲的位置、太圆太大」）：
 *  1. 镜片不再用 `CircleShape`（在宽>高的矩形上会画成**椭圆**，看着又圆又大）；
 *     改成 22dp 圆角矩形，四周留 4dp（左右）/ 5dp（上下），只占自己那一格。
 *  2. 抬起只放大 4%、上浮 1.5dp，且**以中心为锚**——原来 10% 放大 + 前端锚点，
 *     溢出全堆在一侧，压过相邻格子、也溢出栏体，这就是「位置不对劲」。
 *  3. 横向位置改回 `graphicsLayer.translationX`：`Modifier.offset` 是布局属性，
 *     每帧动画都要重新布局一次，和图层缩放不同步。
 *  4. **按压不再抬起镜片**：按在任何格只让整条栏轻收；镜片只在「短按切页」和
 *     「长按拖动」时抬起，而且长按只有按在**当前镜片那一格**里才生效。
 *
 * 动作语义对齐 iOS 26 的 tab bar：按下轻收 → 镜片抬起 → 移到目标格 → 落下回弹；
 * 长按可以抬起镜片跟手拖、页面跟着镜片走，松手吸附最近格。
 */
@Composable
internal fun MoyuTabBar(
    current: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop? = null,
    /** 显示哪些页（0 书架 / 1 笔记 / 2 数据 / 3 设置），顺序固定；默认全显示。 */
    visible: List<Int> = listOf(0, 1, 2, 3),
    /** 长按拖动时的连续位置（0..n-1），松手回 null。root 用它驱动页面跟着走。 */
    onDrag: (Float?) -> Unit = {},
) {
    val ui = LocalInk.current
    val p = ui.palette
    val s = ui.settings
    val motionNone = s.motion == "none"
    val defaults = listOf("书架", "笔记", "数据", "设置")
    // Four tabs since 3.26.64（问书页下线）：设置永远在最后一项。名字表仍按
    // `defaults` 逐位读，旧数据（含「问书」那一位的五个名字）由 normalize 换成四个。
    val allNames = List(defaults.size) { i ->
        s.tabNames.getOrNull(i)?.ifBlank { defaults[i] } ?: defaults[i]
    }
    val allIcons = listOf(Glyph.Book, Glyph.Note, Glyph.Chart, Glyph.Gear)
    val shown = visible.ifEmpty { listOf(3) }
    val names = shown.map { allNames.getOrElse(it) { defaults[it] } }
    val icons = shown.map { allIcons[it] }
    val height = if (s.showTabLabels) 64.dp else 56.dp
    // 栏体是胶囊。注意 SmoothCorner(50) 命中 Int 重载 = CircleShape，
    // 这在「宽远大于高」的栏体上正好是胶囊；但**不能**拿给镜片用（会变椭圆）。
    val capsule = SmoothCorner(50)
    // 镜片是**扁圆角矩形**，不是胶囊。
    // 3.26.9：普通 RoundedCornerShape 在直线与圆弧的切点处曲率从 0 跳到 1/R，大屏上
    // 能看出「棱角」；SmoothCornerShape 曲率连续，且仍继承 CornerBasedShape，
    // drawBackdrop 的 lens() 才不会抛 "Only CornerBasedShape is supported in lens
    // effects"（折射仍按圆弧半径算，角上 1–3dp 偏差被边缘带盖住）。
    // 3.26.21：用户要「像水滴一样圆满圆润」——圆角一度取到高度一半（= 胶囊）。
    // 3.26.67：用户改口「扁一点点的圆角矩形、越润越好」——半径收回 18dp（< 高/2 =
    // 28dp），上下读作扁；「润」由 SmoothCornerShape 的曲率连续负责（平滑量
    // p=(1+.8)×r 会被预算夹到 min(宽,高)/2 = 28dp，过渡更长、更软）。
    val lensShape: androidx.compose.foundation.shape.CornerBasedShape = SmoothCornerShape(
        androidx.compose.foundation.shape.CornerSize(TAB_LENS_CORNER_DP),
        androidx.compose.foundation.shape.CornerSize(TAB_LENS_CORNER_DP),
        androidx.compose.foundation.shape.CornerSize(TAB_LENS_CORNER_DP),
        androidx.compose.foundation.shape.CornerSize(TAB_LENS_CORNER_DP),
    )
    val glassEnabled = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val count = names.size.coerceAtLeast(1)
    val scope = rememberCoroutineScope()
    val haptic = LocalHaptic.current

    // 手势协程只在 key 变化时重启，闭包会把当时的 current / 回调冻结住。
    // 切过一次页之后，闭包里还是旧值——「长按是否按在当前格」永远不成立，
    // 表现就是长按拖动彻底失效（2026-11-15 实测：切到别的 tab 后 DOWN 仍报 cur=0）。
    val currentNow by androidx.compose.runtime.rememberUpdatedState(current)
    val onSelectNow by androidx.compose.runtime.rememberUpdatedState(onSelect)
    val onDragNow by androidx.compose.runtime.rememberUpdatedState(onDrag)

    var pressed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    // 短按切页时让镜片先抬起来再落下；按住本身不再抬镜片。
    var clickLift by remember { mutableStateOf(false) }
    LaunchedEffect(clickLift) {
        if (clickLift) {
            delay(260)
            clickLift = false
        }
    }
    // 连点两个 tab 只执行最后一次。
    var selectJob by remember { mutableStateOf<Job?>(null) }

    val lensAlpha = .08f * s.tabLensOpacity.coerceIn(0f, 1f)
    // 3.26.53：镜片内的选中薄涂随昼夜连续从墨色走到白色（原来 if (isDark) 中点硬翻）。
    val selectedTint = lerp(p.ink, Color.White, p.darkAmount).copy(alpha = lensAlpha)

    // 镜片只在「切页」和「拖动」时抬起；按住不抬（2026-11-15 用户报：按住别的格也抬）。
    val liftTarget = if (dragging || clickLift) 1f else 0f
    val lift by animateFloatAsState(
        targetValue = liftTarget,
        animationSpec = if (motionNone) tween(0) else LensLift,
        visibilityThreshold = 1e-3f,
        label = "tabLensLift",
    )
    val press by animateFloatAsState(
        targetValue = if (pressed && !dragging) 1f else 0f,
        animationSpec = spring(stiffness = 720f, dampingRatio = MOYU_DAMPING, visibilityThreshold = PRESS_THRESHOLD),
        visibilityThreshold = PRESS_THRESHOLD,
        label = "tabPress",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer {
                    // 按住时整条栏轻收。不放大了（用户报「整个标签栏放大了一下」）。
                    val k = 1f - .012f * press
                    scaleX = k
                    scaleY = k
                }
        ) {
            val itemWidth = (maxWidth - 10.dp) / count
            val density = LocalDensity.current
            val itemPx = with(density) { itemWidth.toPx() }
            // 镜片槽位：左右各留 4dp（不压邻格）。3.26.67 起镜片本体 72×56dp，同时小于
            // 格宽 83.35dp 与条高 64dp → 槽内居中即可，静止态没有任何凸出。
            // 长按拖动时的粘滞拉伸由内层 graphicsLayer 缩放实现，Box 默认不裁剪，
            // 允许瞬时越格/越条（用户要求）。
            val lensInsetX = 4.dp
            val lensWidth = itemWidth - lensInsetX * 2
            val lensLeftPx = with(density) { (5.dp + lensInsetX).toPx() }
            val padPx = with(density) { 5.dp.toPx() }
            val risePx = with(density) { 1.5.dp.toPx() }

            var dragOffset by remember { mutableStateOf<Float?>(null) }
            val lensPos = remember { Animatable(current.toFloat()) }

            var lensStretch by remember { mutableFloatStateOf(0f) }
            var lensImpact by remember { mutableFloatStateOf(0f) }
            var lensRightward by remember { mutableStateOf(true) }
            var lensLast by remember { mutableFloatStateOf(current.toFloat()) }
            var lensLastNs by remember { mutableLongStateOf(0L) }

            // 粘滞水滴：松手 / 落定后形变按 ViscousDrop 的曲线指数衰减，
            // 不再一帧切掉——切掉只剩「弹」，读不出「粘」（3.24.3 之前就是这样）。
            var stretchDecay by remember { mutableStateOf<Job?>(null) }
            fun settleStretch() {
                stretchDecay?.cancel()
                stretchDecay = scope.launch {
                    var prev = androidx.compose.runtime.withFrameNanos { it }
                    while (lensStretch > .004f) {
                        val now = androidx.compose.runtime.withFrameNanos { it }
                        val dtMs = ((now - prev) / 1_000_000f).coerceIn(.5f, 40f)
                        prev = now
                        lensStretch *= ViscousDrop.decayFor(dtMs)
                    }
                    lensStretch = 0f
                }
            }

            LaunchedEffect(current, count) {
                lensRightward = current >= lensPos.value
                lensLast = lensPos.value
                lensLastNs = 0L
                lensPos.animateTo(
                    targetValue = current.coerceIn(0, count - 1).toFloat(),
                    animationSpec = if (motionNone) tween(0) else LensSettle,
                ) {
                    val step = abs(value - lensLast)
                    lensLast = value
                    val now = System.nanoTime()
                    val dt = if (lensLastNs == 0L) 1f / 60f
                    else ((now - lensLastNs) / 1_000_000_000f).coerceIn(.002f, .05f)
                    lensLastNs = now
                    val step60 = step * (1f / 60f) / dt
                    val target = (step60 * itemPx / 30f).coerceIn(0f, 1f)
                    val follow = 1f - (1f - .32f).pow(dt * 60f)
                    // 收尾：最后 0.12 tab 用 smoothstep 从 1 收到 0，位置到终点时
                    // 形变同步是 0，不会停住之后再收一下。
                    val settle = (abs(value - current) / .12f).coerceIn(0f, 1f)
                        .let { it * it * (3f - 2f * it) }
                    lensStretch = (lensStretch + (target - lensStretch) * follow) * settle
                    lensImpact = (abs(value - current) * 5f).coerceIn(0f, .55f) * settle
                    if (abs(value - current) < .0008f && abs(lensStretch) < .002f) {
                        lensStretch = 0f
                        lensImpact = 0f
                    }
                }
                settleStretch()
            }


            // 镜片位置在**绘制阶段**读 dragOffset / lensPos：
            // 拖动时每个触摸事件只 invalidate 这一层的绘制，不再让整条
            // TabBar 重组（3.26.8 是组合阶段读，长按拖动被读作「很卡」）。
            // 页面（TabPager）同样改成绘制阶段读同一个 State，所以两者仍同帧，
            // 不会回到 3.24.2 那种「镜片领先页面一帧」。

            // 玻璃底：整条胶囊。
            Box(
                Modifier
                    .fillMaxSize()
                    .liquidGlassBar(
                        backdrop = backdrop,
                        shape = capsule,
                        palette = p,
                        press = 0f,
                        blurDp = s.tabBlur,
                        refractionDp = s.tabRefraction,
                        // 3.26.67：条读「标签栏」那一对弯折/压缩（镜片、通知各读各的）。
                        bend = s.tabBend,
                        squeeze = s.tabSqueeze,
                        dispersion = s.tabDispersion,
                        shadowAlpha = s.tabShadow,
                        veil = s.tabBarOpacity,
                        edgeStrength = s.tabEdge,
                    )
            )

            // 镜片：外层是整格槽位（管位置与抬起），内层是水滴本体（管速度拉伸）。
            // 3.26.67：本体固定 TAB_LENS_W_DP×TAB_LENS_H_DP（72×56dp）、槽内居中；
            // 72 < 格宽 83.35、56 < 条高 64 → 静止态整块落在自己格子里，不凸出条也不压邻格；
            // 槽宽与命中区域不变（点的时候还是整格可点、镜片本身没有 pointerInput）。
            // 必须用 requiredSize：槽位由 fillMaxHeight / width 给出的是固定约束，
            // 普通 height 会被父约束夹回 64dp（高度方向的条高），requiredSize 不理会
            // 传入约束、按本体尺寸居中放置；拖动拉伸由内层 graphicsLayer 缩放，不被它限制。
            Box(
                Modifier
                    .width(lensWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        // 浮点位移，不用 Modifier.offset：那是布局属性，
                        // 每帧动画都会 relayout，和图层缩放不同步。
                        val pos = dragOffset ?: lensPos.value
                        translationX = lensLeftPx + itemPx * pos
                        translationY = -risePx * lift
                        scaleX = 1f + .04f * lift
                        scaleY = 1f + .04f * lift
                        transformOrigin = TransformOrigin(.5f, .5f)
                    },
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Box(
                    Modifier
                        .requiredSize(TAB_LENS_W_DP, TAB_LENS_H_DP)
                        .graphicsLayer {
                            // 速度拉伸：原点放在运动方向的前端（前端先到、后端拖尾）。
                            transformOrigin = TransformOrigin(if (lensRightward) 1f else 0f, .5f)
                            scaleX = ViscousDrop.along(lensStretch) * (1f - lensImpact * .18f)
                            scaleY = ViscousDrop.perp(lensStretch) * (1f + lensImpact * .22f)
                        }
                        .border(
                            .5.dp,
                            // 3.26.9：不再随 lift 变亮。点击切页时 clickLift 会在 260ms
                            // 里把边框、外阴影、内阴影一起推高再收回，用户读作「点一下
                            // 镜片边缘亮一下」。抬起感只留位置与 4% 缩放，材质三处恒定。
                            // 3.26.53：昼夜色改按 darkAmount 连续插值（墨 6% ↔ 白 7%）。
                            lerp(
                                p.ink.copy(alpha = .06f),
                                Color.White.copy(alpha = .07f),
                                p.darkAmount,
                            ),
                            lensShape,
                        )
                        .then(
                            if (glassEnabled) {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop!!,
                                    shape = { lensShape },
                                    effects = {
                                        vibrancy()
                                        if (s.lensBlur > 0f) blur(s.lensBlur.dp.toPx())
                                        if (s.lensRefraction > 0f) {
                                            // 3.26.65：与条同口径（glassLensBand）——原版把
                                            // 9dp 同时当带宽与位移，镜片上只有一圈窄边；
                                            // 放大后带才铺开，镜片才像一块真透镜。
                                            // 3.26.67：H = cap×宽度%、A = cap×压缩%（cap =
                                            // 镜片最小边 × .55 = 30.8dp），两条式子互不出现
                                            // 对方参数。历史 1.2 的位移偏置已折进
                                            // lensSqueeze 默认值（0.771），这里不再乘它。
                                            val minSide = minOf(size.width, size.height)
                                            val band = glassLensBand(minSide, s.lensBend)
                                            if (band > 0f) {
                                                lens(
                                                    refractionHeight = band,
                                                    refractionAmount = glassLensAmount(minSide, s.lensSqueeze),
                                                    depthEffect = true,
                                                    chromaticAberration = s.lensDispersion,
                                                )
                                            }
                                        }
                                    },
                                    highlight = null,
                                    shadow = {
                                        Shadow(
                                            radius = 12.dp,
                                            alpha = s.lensShadow,
                                        )
                                    },
                                    innerShadow = { InnerShadow(radius = 6.dp, alpha = s.lensShadow + .07f) },
                                    layerBlock = {
                                        scaleX = 1f
                                        scaleY = 1f
                                    },
                                    onDrawSurface = {
                                        drawRect(selectedTint)
                                        val v = s.tabLensOpacity.coerceIn(0f, 1f)
                                        if (v > 0f) drawRect(p.paper.copy(alpha = v * .94f))
                                        glassEdgeBand(lensShape, p, s.lensEdge)
                                    },
                                )
                            } else {
                                Modifier
                                    .clip(lensShape)
                                    // 3.26.53：非玻璃回退底色：墨 88%（浅色）→ 墨 18%（深色），
                                    // 透明度随 darkAmount 连续过渡，不再在中点硬翻。
                                    .background(p.ink.copy(alpha = .88f - .70f * p.darkAmount))
                            }
                        )
                )
            }

            Row(
                Modifier
                    .fillMaxSize()
                    .padding(5.dp)
            ) {
                names.forEachIndexed { index, name ->
                    val active = index == current
                    val fg by animateColorAsState(
                        targetValue = if (active) p.ink else p.muted,
                        animationSpec = tween(220),
                        label = "tabFg",
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(lensShape)
                            .semantics {
                                contentDescription = name
                                role = Role.Tab
                                onClick(label = name) { onSelectNow(index); true }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        LineIcon(icons[index], fg, 19.dp)
                        if (s.showTabLabels) {
                            Spacer(Modifier.height(3.dp))
                            Txt(
                                name.take(4),
                                size = 8.5f,
                                color = fg,
                                weight = if (active) FontWeight.Medium else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                }
            }

            // 手势：短按 = 切页；长按「当前镜片那一格」= 抬起镜片拖动。
            //
            // 不用 `awaitLongPressOrCancellation`：它按 pointerId 认人，而 adb
            // `input motionevent` 每发一条都是独立注入，pointerId 会变、长按一律
            // 返回 null（表现就是「长按没反应」）。这里用外层 coroutineScope 起一个
            // 300ms 的计时协程，`AwaitPointerEventScope` 只负责读事件；事件也只取
            // `changes.firstOrNull()`（单指拖动只有一个 change）。
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(count, itemPx, padPx) {
                        kotlinx.coroutines.coroutineScope {
                            val host = this
                            while (true) {
                                awaitPointerEventScope {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    pressed = true
                                    val pressX = down.position.x
                                    val pressIdx = ((pressX - padPx) / itemPx)
                                        .toInt().coerceIn(0, count - 1)
                                    var finished = false
                                    var longFired = false
                                    var draggingHere = false
                                    var smooth = lensPos.value
                                    val longJob = host.launch {
                                        delay(260)
                                        if (!finished && !longFired && pressIdx == currentNow) {
                                            longFired = true
                                            draggingHere = true
                                            dragging = true
                                            haptic(1)
                                            smooth = lensPos.value
                                            dragOffset = smooth
                                            onDragNow(smooth)
                                        }
                                    }
                                    while (!finished) {
                                        val ev = awaitPointerEvent(PointerEventPass.Main)
                                        val ch = ev.changes.firstOrNull() ?: break
                                        if (draggingHere) {
                                            val prev = smooth
                                            // 半格修正：镜片 index 的**中心**在 (index + .5) 格处，
                                            // 而手指换成格坐标得到的是「格右边界」。不减这 .5，
                                            // 镜片会一直落后手指半格，松手 round() 也就落到前一格
                                            // ——用户看到的「不跟手 / 和页面不匹配」都是这一处。
                                            val target = (ch.position.x - padPx) / itemPx - .5f
                                            // .9：几乎是 1:1 跟手，只留一点滤波吃掉触摸抖动。
                                            // 1:1 跟手（原来 .55 的一阶滤波会一直落后半格多）。
                                            // 触摸事件本身是离散的，120Hz 下每步约 2px，看不出台阶。
                                            smooth = (target.coerceIn(0f, (count - 1).toFloat()))
                                            val now = System.nanoTime()
                                            val dt = if (lensLastNs == 0L) 1f / 60f
                                            else ((now - lensLastNs) / 1_000_000_000f).coerceIn(.002f, .05f)
                                            lensLastNs = now
                                            val step60 = abs(smooth - prev) * (1f / 60f) / dt
                                            val stretchTarget = (step60 * itemPx / 30f).coerceIn(0f, 1f)
                                            val follow = 1f - (1f - .32f).pow(dt * 60f)
                                            lensStretch += (stretchTarget - lensStretch) * follow
                                            // 换向：先把旧方向的形变收掉再按新方向长。
                                            // 3.26.8 是每帧直接改 lensRightward，原点瞬间从
                                            // 一端翻到另一端，形状像被掰断——用户读作
                                            // 「变形不符合物理的方向性」。这里给 1e-4 的死区，
                                            // 手指停住时不会来回翻。
                                            val movingRight = smooth >= prev
                                            if (movingRight != lensRightward && abs(smooth - prev) > 1e-4f) {
                                                lensStretch *= .25f
                                                lensRightward = movingRight
                                            }
                                            dragOffset = smooth
                                            onDragNow(smooth)
                                        }
                                        if (!ch.pressed) {
                                            finished = true
                                        } else if (!longFired && abs(ch.position.x - pressX) > 56f) {
                                            // 没到长按时长就滑开这么多：这次不认长按（56px 给「按住想拖」的微动留余量，原来 24px 太紧）。
                                            finished = true
                                        }
                                        ch.consume()
                                    }
                                    longJob.cancel()
                                    pressed = false
                                    if (draggingHere) {
                                        val endPos = dragOffset ?: lensPos.value
                                        val idx = endPos.roundToInt().coerceIn(0, count - 1)
                                        lensImpact = 0f
                                        settleStretch()
                                        dragging = false
                                        scope.launch {
                                            lensPos.snapTo(endPos)
                                            dragOffset = null
                                            clickLift = true
                                            // 先换页再松开页面跟手：反过来的话页面会先
                                            // 弹回旧页、再弹到新页，读成两段。
                                            if (idx != currentNow) {
                                                onSelectNow(idx)
                                            } else {
                                                lensPos.animateTo(currentNow.toFloat(), LensSettle)
                                            }
                                            onDragNow(null)
                                        }
                                    } else if (pressIdx != currentNow) {
                                        clickLift = true
                                        haptic(1)
                                        selectJob?.cancel()
                                        selectJob = scope.launch {
                                            delay(70)
                                            onSelectNow(pressIdx)
                                        }
                                    }
                                }
                            }
                        }
                    }
            )
        }
    }
}
