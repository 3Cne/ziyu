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
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/** 镜片落下：阻尼 .78、刚度 380，比页面（pageStiffness ≈321）略快，读作「镜片领着页面走」。 */
private val LensSettle = spring<Float>(dampingRatio = .78f, stiffness = 380f, visibilityThreshold = 1e-3f)

/** 镜片抬起/放下：快起、略带过冲，读作「提起来」。 */
private val LensLift = spring<Float>(dampingRatio = .56f, stiffness = 520f, visibilityThreshold = 1e-3f)

// ── 3.26.60 镜片倍率换算常量（集中一处，改口径只改这几行）──────────────
// 镜片组 5 个滑杆不再各自是一套绝对材质参数，而是乘在「栏的值」上的倍率：
// 默认读数必须刚好落在 1× 恒等点（见 MoyuTabBar 内「镜片倍率」段的逐项说明）。
/** 折射：lensRefraction 默认 9 → 1×；上限 LENS_REFRACTION_MAX×（18 → 2×）。 */
private const val LENS_REFRACTION_BASE = 9f
/** 折射倍率上限（再往上拉仍是 2×，避免 bevel 无意义地变大）。 */
private const val LENS_REFRACTION_MAX = 2f
/** 模糊：lensBlur 0..9 → 倍率 1×..2×。 */
private const val LENS_BLUR_SPAN = 9f
/** 不透明度：tabLensOpacity 0..1 → 倍率 1×..2×。 */
private const val LENS_OPACITY_SPAN = 1f

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
    /** 显示哪些页（0 书架 / 1 笔记 / 2 数据 / 3 问书 / 4 设置），顺序固定；默认全显示。 */
    visible: List<Int> = listOf(0, 1, 2, 3, 4),
    /** 长按拖动时的连续位置（0..n-1），松手回 null。root 用它驱动页面跟着走。 */
    onDrag: (Float?) -> Unit = {},
) {
    val ui = LocalInk.current
    val p = ui.palette
    val s = ui.settings
    val motionNone = s.motion == "none"
    val defaults = listOf("书架", "笔记", "数据", "问书", "设置")
    // Five tabs since 3.10.0: 问书 sits between 数据 and 设置. The name list is
    // read through `defaults` so a data file written by an older build (four
    // names) still lines up after normalize() inserts the fifth slot.
    val allNames = List(defaults.size) { i ->
        s.tabNames.getOrNull(i)?.ifBlank { defaults[i] } ?: defaults[i]
    }
    val allIcons = listOf(Glyph.Book, Glyph.Note, Glyph.Chart, Glyph.Sparkles, Glyph.Gear)
    val shown = visible.ifEmpty { listOf(4) }
    val names = shown.map { allNames.getOrElse(it) { defaults[it] } }
    val icons = shown.map { allIcons[it] }
    val height = if (s.showTabLabels) 64.dp else 56.dp
    // 栏体是胶囊。注意 SmoothCorner(50) 命中 Int 重载 = CircleShape，
    // 这在「宽远大于高」的栏体上正好是胶囊；但**不能**拿给镜片用（会变椭圆）。
    val capsule = SmoothCorner(50)
    // 镜片是圆角矩形，不是胶囊。
    // 3.26.9：普通 RoundedCornerShape 在直线与圆弧的切点处曲率从 0 跳到 1/R，
    // 22dp 的角在大屏上能看出「棱角」。换成连续曲率的 SmoothCornerShape：
    // 它仍继承 CornerBasedShape，drawBackdrop 的 lens() 才不会抛
    // "Only CornerBasedShape is supported in lens effects"；折射仍按圆弧半径
    // 计算，所以角上 1–3dp 与裁剪路径略有偏差，正好被加宽后的边缘带盖住。
    // 3.26.21：用户要求「像水滴一样圆满圆润」。镜片从 75.35×54dp 的 22dp 圆角矩形
    // 改成 65.55×57dp（比例 1.15）的完整胶囊——视觉直边最短，读作水滴；
    // 圆角取高度一半，SmoothCornerShape 在 CornerBasedShape 上才能继续用 lens() 折射。
    val lensShape: androidx.compose.foundation.shape.CornerBasedShape = SmoothCornerShape(
        androidx.compose.foundation.shape.CornerSize(28.5.dp),
        androidx.compose.foundation.shape.CornerSize(28.5.dp),
        androidx.compose.foundation.shape.CornerSize(28.5.dp),
        androidx.compose.foundation.shape.CornerSize(28.5.dp),
    )
    val lensTargetW = 65.55.dp
    val lensTargetH = 57.dp
    val glassEnabled = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val count = names.size.coerceAtLeast(1)
    val scope = rememberCoroutineScope()
    val haptic = LocalHaptic.current

    // 手势协程只在 key 变化时重启，闭包会把当时的 current / 回调冻结住。
    // 切过一次页之后，闭包里还是旧值——「长按是否按在当前格」永远不成立，
    // 表现就是长按拖动彻底失效（2026-11-15 实测：切到问书后 DOWN 仍报 cur=0）。
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

    // 3.26.60：镜片的专属填充（选中薄涂 selectedTint + tabLensOpacity 纸色覆层）
    // 整段删掉。镜片与栏只剩同一套材质（同一 AGSL 厚度场 / 同一 veil / 同一
    // 基础模糊），选中感只由图标与文字的墨色承担（见下方 fg）。
    // 3.26.60：镜片补上栏的两层表面料。栏在 liquidGlassBar().onDrawSurface 里
    // 先画 tint、再画 paperVeil；这里按同一顺序补：tint 与栏同一个表达式，
    // paperVeil 的取值见下方「镜片倍率」（默认 1× 时就是栏的 veil）。
    //
    // ── 镜片倍率（3.26.60）─────────────────────────────────────────────
    // 3d80122 把镜片材质全部改成与栏同源后，镜片组 5 个滑杆在 TabBar.kt 里
    // 0 处读取 ——「设置 → 玻璃 → 镜片」的滑杆调了看不出任何变化。这里统一
    // 改成「栏的值 × 倍率」：
    //   折射 lensRefraction / 模糊 lensBlur / 不透明度 tabLensOpacity /
    //   边缘 lensEdge / 色散 lensDispersion
    // **默认值 = 1× 恒等**：默认读数换算后与栏逐项相等（折射 9→1×、模糊
    // 0→1×、不透明度 0→1×、边缘 1→1×、色散 true→取栏的 tabDispersion），
    // 所以默认观感与 3.26.60 完全一致，只上调某个滑杆才会让镜片比栏更强。
    // **不迁移旧数据**：同一字段从「绝对值」改读为「倍率」；旧备份里
    // 1 / 0 / 9 这些默认读数正好落在恒等点，不会突变；只有作者确实动过镜片
    // 滑杆的旧数据会被按倍率解读（如 18 → 2× 上限、0 → 0×），不写盘、不改码。
    // 五个滑杆由此恢复真实落点：下方 effects 与 onDrawSurface 都消费这组值。
    val lensRefractionFactor = (s.lensRefraction / LENS_REFRACTION_BASE).coerceIn(0f, LENS_REFRACTION_MAX)
    val lensBlurFactor = 1f + (s.lensBlur / LENS_BLUR_SPAN).coerceIn(0f, 1f)
    val lensOpacityFactor = 1f + (s.tabLensOpacity / LENS_OPACITY_SPAN).coerceIn(0f, 1f)
    // ① 折射：bevel = 栏的 tabRefraction × 倍率（栏自己是 0 时仍为 0）。
    val lensBevelDp = s.tabRefraction * lensRefractionFactor
    // ② 模糊：栏的 tabBlur>0 时按倍率放大；栏为 0（默认）时乘出来还是 0，
    //    改让镜片单独吃 lensBlur 的量。默认 lensBlur=0 → 仍是 0 = 与栏一致。
    val lensBlurDp = if (s.tabBlur > 0f) s.tabBlur * lensBlurFactor else s.lensBlur * (lensBlurFactor - 1f)
    // ③ 不透明度：×倍率后**必须夹 0..1**，否则 2× 会溢出。paperVeil 与
    //    AGSL 的 veilK 共用这一个值（默认 = 栏的 tabBarOpacity）。
    val lensVeil = (s.tabBarOpacity * lensOpacityFactor).coerceIn(0f, 1f)
    // ④ 边缘带：栏的 tabEdge × lensEdge，再夹 0..1。AGSL 路径下这只是
    //    「增强项」（Fresnel 亮环 / 赤道线 / 边缘变量模糊有基线，0 也不熄）；
    //    API 31–32 回退路径仍按旧语义 = 0 就不画宽边带。
    val lensEdgeStrength = (s.tabEdge * s.lensEdge).coerceIn(0f, 1f)
    // ⑤ 色散：两边都开才有（默认 lensDispersion=true → 与栏的 tabDispersion 同值）。
    val lensDispersion = s.tabDispersion && s.lensDispersion

    val glassTint = if (p.isDark) Color.White.copy(alpha = .06f) else p.ink.copy(alpha = .045f)
    val glassVeil = lensVeil
    val glassPaperVeil = p.paper.copy(alpha = glassVeil * .94f)

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
            // 镜片四周留白：左右各 4dp（不压邻格），上下各 5dp（与 tab 内容对齐）。
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
                        dispersion = s.tabDispersion,
                        shadowAlpha = s.tabShadow,
                        veil = s.tabBarOpacity,
                        edgeStrength = s.tabEdge,
                    )
            )

            // 镜片：外层是整格槽位（管位置与抬起），内层是水滴本体（管速度拉伸）。
            // 3.26.21：本体固定 65.55×57dp、在槽内居中；槽宽与命中区域不变，
            // 只是视觉变圆润（用户点的时候还是要整格可点）。
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
                        .width(lensTargetW)
                        .height(lensTargetH)
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
                                        // 镜片与栏同一材质，参数 = 栏的值 × 镜片滑杆倍率
                                        // （默认 1× 时逐项等于栏；见上方「镜片倍率」段）。
                                        vibrancy()
                                        if (lensBlurDp > 0f) blur(lensBlurDp.dp.toPx())
                                        if (lensBevelDp > 0f) {
                                            // 3.26.59：选中镜片与玻璃条同源。API 33+ 走 GlassBar 的
                                            // 真厚度场 AGSL；31–32 或 shader 构建失败仍回退 Backdrop
                                            // 的 lens()，不崩、不画空。
                                            // 3.26.60：bevel / veilK / 亮环 / 色散都取上方倍率换算值，
                                            // 默认 1× 时与栏同值；折射 0× 时整段跳过（与栏 tabRefraction=0
                                            // 同义：不折射），下面的回退 lens() 用的也是这组换算值。
                                            val ags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                runCatching {
                                                    buildLensEffect(
                                                        bevelDp = lensBevelDp,
                                                        edgeStrength = lensEdgeStrength,
                                                        veilK = lensVeil,
                                                        dispersion = lensDispersion,
                                                    )
                                                }.getOrNull()
                                            } else {
                                                null
                                            }
                                            if (ags != null) {
                                                effect(ags)
                                            } else {
                                                lens(
                                                    refractionHeight = lensBevelDp.dp.toPx(),
                                                    refractionAmount = lensBevelDp.dp.toPx(),
                                                    depthEffect = true,
                                                    chromaticAberration = lensDispersion,
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
                                    // 3.26.60：内阴影只留深度，去掉 +.07 的 alpha 增量——
                                    // 那点增量在没有纸色填充后会被读成一圈内衬料。
                                    innerShadow = { InnerShadow(radius = 6.dp, alpha = s.lensShadow) },
                                    layerBlock = {
                                        scaleX = 1f
                                        scaleY = 1f
                                    },
                                    onDrawSurface = {
                                        // 3.26.60：与栏完全同材质——先 tint、再 paperVeil、最后
                                        // 边缘带，三层顺序与取值和 liquidGlassBar 一致。
                                        drawRect(glassTint)
                                        if (glassVeil > 0f) drawRect(glassPaperVeil)
                                        glassEdgeBand(
                                            lensShape,
                                            p,
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                lensBevelDp > 0f
                                            ) {
                                                // 3.26.59：AGSL 路径上 shader 自带 Fresnel/赤道亮线，
                                                // 再叠一条宽边带就是第二道亮环（3.26.13/14/52 三次被否的坑），
                                                // 与 GlassBar 同口径：AGSL 生效时跳过宽边带。
                                                0f
                                            } else {
                                                // 3.26.60：非 AGSL 回退路径的亮环 = 栏的 tabEdge × lensEdge
                                                // （默认 lensEdge=1 → 与栏同值）。
                                                lensEdgeStrength
                                            },
                                        )
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
