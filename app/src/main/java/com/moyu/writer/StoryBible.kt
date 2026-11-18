package com.moyu.writer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * 故事圣经（3.26.16）：角色 / 关系 / 时间线 / 大纲。
 *
 * 为什么放在书籍详情而不是新 tab：这三样是「这本书的」，不是全局的；
 * 也就近成了问书（下一批）的读写面板——AI 写进来的东西必须落在人能看见、能改的地方。
 */
@Composable
fun StorySheet(
    book: Book,
    onBible: (BookBible) -> Unit,
    onOpenChapter: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // 3.26.19：写作前规划台默认停在「大纲」。
    var tab by remember { mutableIntStateOf(2) }

    // 3.26.19：全屏工作台——标题/分段固定，内容区自己滚，切 tab 高度不跳。
    // surface 仍铺纸色（与 SheetPane 同色，避免 3.26.17 的「灰框」）。
    // 3.26.18：面板内没有输入框（输入全在 GlassDialog），不跟 IME 抬高；点空白不返回。
    InlineModalSheet(
        onDismiss,
        tone = LocalInk.current.palette.paper,
        imeAware = false,
        dismissOnOutsideClick = false,
        fillHeight = true,
        pinnedContent = { close ->
            DialogTitle("故事", onClose = close)
            Spacer(Modifier.height(12.dp))
            Segmented(listOf("角色", "时间线", "大纲"), tab, { tab = it })
            Spacer(Modifier.height(4.dp))
        },
    ) { _ ->
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val forward = targetState > initialState
                val dir = if (forward) 1 else -1
                // 3.26.18：只滑不淡 + 整宽位移。三层都自带 SheetPane 纸底：
                // fade 会让纸底半透明；w/3 重叠会让较高的一层从另一层纸底下方露出来。
                slideInHorizontally(spring(MOYU_DAMPING, 420f)) { it * dir } togetherWith
                    slideOutHorizontally(spring(MOYU_DAMPING, 420f)) { -it * dir }
            },
            label = "storyTab",
        ) { index ->
            SheetPane(Modifier.fillMaxSize()) {
                when (index) {
                    0 -> CharacterPane(book, onBible)
                    1 -> TimelinePane(book, onBible, onOpenChapter)
                    else -> OutlinePane(book, onBible)
                }
            }
        }
    }
}

// ───────────────────────── 角色 + 关系 ─────────────────────────

@Composable
private fun CharacterPane(book: Book, onBible: (BookBible) -> Unit) {
    val ui = LocalInk.current
    val bible = book.bible
    var editing by remember { mutableStateOf<StoryCharacter?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<StoryCharacter?>(null) }
    var deletingRelation by remember { mutableStateOf<StoryRelation?>(null) }
    var linking by remember { mutableStateOf<StoryCharacter?>(null) }
    var centerId by remember { mutableStateOf(bible.characters.firstOrNull()?.id.orEmpty()) }
    var fromId by remember { mutableStateOf(centerId) }
    var tick by remember { mutableIntStateOf(0) }
    // 三个状态在同一次事件回调里一起写，关系图下一次组合就能拿到新的 tick。
    fun pick(id: String) {
        if (id.isEmpty() || id == centerId) return
        fromId = centerId
        centerId = id
        tick++
    }

    // 角色被删掉后，中心要落回第一个，不能指向一个不存在的 id。
    LaunchedEffect(bible.characters) {
        if (bible.characters.none { it.id == centerId }) {
            pick(bible.characters.firstOrNull()?.id.orEmpty())
        }
    }
    val center = bible.character(centerId)

    // 3.26.19：工作台模式——上半区自己滚，主按钮固定在 pane 底部，长内容不会把按钮顶出屏幕。
    Column(Modifier.fillMaxSize()) {
        if (bible.characters.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    glyph = Glyph.Note,
                    title = "还没有角色",
                    message = "先记下一个名字，关系线和时间线都从这里长出来。",
                )
            }
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // 关系图高度跟着可用空间走：原固定 240dp 在矮屏/大字号下会把下面的操作挤掉。
                val graphH = (maxHeight * .30f).coerceIn(150.dp, 240.dp)
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    RelationGraph(
                        bible = bible,
                        centerId = centerId,
                        fromId = fromId,
                        tick = tick,
                        onPick = { pick(it) },
                        modifier = Modifier.fillMaxWidth().height(graphH),
                    )
                    if (bible.relations.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        RelationLegend()
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        bible.characters.forEach { c ->
                            GlassChip(c.name.ifBlank { "未命名" }, selected = c.id == centerId) { pick(c.id) }
                        }
                        Box(Modifier.width(1.dp))
                    }
                    if (center != null) {
                        Spacer(Modifier.height(14.dp))
                        MoyuSurface(Modifier.fillMaxWidth(), radius = 20f, flat = true) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .background(ballColor(center), CircleShape)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Txt(center.name.ifBlank { "未命名" }, size = 18f, weight = FontWeight.SemiBold)
                                    if (center.role.isNotBlank()) {
                                        Spacer(Modifier.width(8.dp))
                                        Txt(center.role, size = 11f, color = ui.palette.muted)
                                    }
                                }
                                if (center.note.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Txt(center.note, size = 11.5f, color = ui.palette.muted, lineHeight = 18f)
                                }
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GlassChip("加关系", false, leading = null) { linking = center }
                                    GlassChip("编辑", false) { editing = center }
                                    GlassChip("删除", false) { deleting = center }
                                }
                            }
                        }
                        val links = bible.relationsOf(center.id)
                        if (links.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            links.forEach { r ->
                                val other = bible.otherEnd(r, center.id) ?: return@forEach
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(7.dp)
                                            .background(relationTone(r.kind, ui), CircleShape)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Txt(other.name.ifBlank { "未命名" }, size = 13f)
                                    Spacer(Modifier.width(8.dp))
                                    Txt(r.kind.label, size = 11f, color = ui.palette.muted)
                                    Spacer(Modifier.weight(1f))
                                    Txt(
                                        "删除",
                                        size = 11f,
                                        color = ui.palette.muted,
                                        // 3.26.18：关系删除也要确认（原来点一下立即删，误触无法恢复）。
                                        modifier = Modifier.clickable { deletingRelation = r },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("新建角色", { creating = true }, Modifier.fillMaxWidth(), glyph = Glyph.Plus)
    }

    if (creating || editing != null) {
        CharacterEditor(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { name, role, note, color ->
                val b = book.bible
                if (editing == null) {
                    val c = StoryCharacter(name = name, role = role, note = note, color = color)
                    onBible(b.copy(characters = b.characters + c))
                    pick(c.id)
                } else {
                    onBible(
                        b.copy(
                            characters = b.characters.map {
                                if (it.id == editing!!.id) it.copy(name = name, role = role, note = note, color = color) else it
                            },
                        ),
                    )
                }
                creating = false
                editing = null
            },
        )
    }
    deleting?.let { c ->
        ConfirmDialog(
            title = "删除「${c.name.ifBlank { "未命名" }}」？",
            message = "和 TA 相关的 ${book.bible.relationsOf(c.id).size} 条关系会一起删掉；时间线里提到 TA 的地方也会移除。",
            confirmLabel = "删除",
            onConfirm = {
                onBible(book.bible.withoutCharacter(c.id))
                deleting = null
            },
            onDismiss = { deleting = null },
            destructive = true,
        )
    }
    deletingRelation?.let { r ->
        val a = book.bible.character(r.from)?.name?.ifBlank { "未命名" } ?: "未命名"
        val b = book.bible.character(r.to)?.name?.ifBlank { "未命名" } ?: "未命名"
        ConfirmDialog(
            title = "删除这条关系？",
            message = "「$a」和「$b」之间的「${r.kind.label}」关系会被移除，两个角色都保留。",
            confirmLabel = "删除",
            onConfirm = {
                onBible(book.bible.copy(relations = book.bible.relations.filterNot { it.id == r.id }))
                deletingRelation = null
            },
            onDismiss = { deletingRelation = null },
            destructive = true,
        )
    }
    linking?.let { from ->
        RelationEditor(
            bible = book.bible,
            from = from,
            onDismiss = { linking = null },
            onSave = { to, kind ->
                val b = book.bible
                // 同一对角色只留一条关系：再连一次就是改类型。
                val existing = b.relations.firstOrNull {
                    (it.from == from.id && it.to == to) || (it.from == to && it.to == from.id)
                }
                onBible(
                    if (existing != null) {
                        b.copy(relations = b.relations.map { if (it.id == existing.id) it.copy(kind = kind) else it })
                    } else {
                        b.copy(relations = b.relations + StoryRelation(from = from.id, to = to, kind = kind))
                    },
                )
                linking = null
            },
        )
    }
}

/** 关系颜色只给三档：亲近 / 对立 / 其他。八种类型靠文字区分，不靠八种颜色。 */
private fun relationTone(kind: RelationKind, ui: InkUi): Color = when (kind) {
    RelationKind.FAMILY, RelationKind.LOVER, RelationKind.FRIEND, RelationKind.ALLY ->
        lerp(ui.palette.muted, ui.accent, .55f)
    RelationKind.RIVAL, RelationKind.ENEMY ->
        lerp(ui.palette.muted, Color(0xFFB4543C), .62f)
    else -> ui.palette.line
}

// ───────────────────────── 角色球颜色（3.26.24） ─────────────────────────
// 手动色存在 StoryCharacter.color；没设过就按 id 稳定取色板里的一色，重排/重启不变。
// 色板与「外观 → 点缀色」同一份 accentChoices，不另造第二套颜色。

private fun ballColor(c: StoryCharacter): Color {
    c.color?.let { raw ->
        runCatching { Color(android.graphics.Color.parseColor(raw)) }.getOrNull()?.let { return it }
    }
    val h = c.id.hashCode().toLong()
    // 自动色跳过「墨黑」：深色主题下黑球在深纸上几乎看不见；手动仍可选它。
    val palette = accentChoices.drop(1)
    val idx = ((if (h < 0) -h else h) % palette.size).toInt()
    return Color(palette[idx].second)
}

/** 球内文字的反差色：按 WCAG 对比度取墨/纸白里更高的一个。 */
private fun inkOnBall(bg: Color, p: InkPalette): Color {
    fun linear(c: Float): Float = if (c <= .03928f) c / 12.92f else ((c + .055f) / 1.055f).pow(2.4f)
    fun lum(c: Color): Float = .2126f * linear(c.red) + .7152f * linear(c.green) + .0722f * linear(c.blue)
    fun ratio(a: Color, b: Color): Float {
        val la = lum(a)
        val lb = lum(b)
        return (maxOf(la, lb) + .05f) / (minOf(la, lb) + .05f)
    }
    val light = Color(0xFFF6F3EC)
    val dark = Color(0xFF171713)
    // 球色是固定的调色板色，不随主题变，所以反差也用固定的深/浅两个墨色比，不能用
    // palette.ink（深色主题下它本身就是浅色，会把琥珀球判成浅字，只有 3.5:1）。
    return if (ratio(bg, dark) >= ratio(bg, light)) dark else light
}

/** 圈内只放得下一行：超过 3 个字截断，全名在点开的角色卡里。 */
private fun ballLabel(name: String): String {
    val n = name.trim().ifBlank { "未命名" }
    return if (n.length <= 3) n else n.take(3) + "…"
}

@Composable
private fun RelationLegend() {
    val ui = LocalInk.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(RelationKind.FRIEND to "亲近", RelationKind.ENEMY to "对立", RelationKind.OTHER to "其他")
            .forEach { (kind, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(relationTone(kind, ui), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Txt(label, size = 10f, color = ui.palette.muted)
                }
            }
    }
}

/**
 * 环形关系图。
 *
 * 位置在**绘制阶段**按 centerId 算，不在布局阶段：换中心时节点沿弹簧从旧位置走到新位置，
 * 而不是整块淡出淡入（淡出淡入会被读成「换了一张图」）。整张图一个 Canvas、一个 Animatable，
 * 不进 Compose 的逐帧重组。
 */
@Composable
private fun RelationGraph(
    bible: BookBible,
    centerId: String,
    fromId: String,
    tick: Int,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = LocalInk.current
    val density = LocalDensity.current
    // 过渡进度：tick 由点击处（事件回调里）自增，不是组合期写状态。
    // 点击处同时写 centerId + fromId + tick，下一次组合就一定拿到 tick 的新值，
    // `remember(tick)` 建出初值 0 的 Animatable——第一帧画的是旧布局，不会先跳新布局。
    // （把归零放进 LaunchedEffect 会晚一帧：那一帧已按 t=1 画完，于是「闪一下再弹回」。）
    val t = remember(tick) { Animatable(if (tick == 0) 1f else 0f) }
    LaunchedEffect(tick) {
        if (tick > 0) t.animateTo(1f, spring(MOYU_DAMPING, 420f))
    }

    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val nodeR = with(density) { 21.dp.toPx() }
        val centerR = with(density) { 27.dp.toPx() }
        val cx = w / 2f
        val cy = h / 2f - with(density) { 6.dp.toPx() }

        fun ring(center: String): List<StoryCharacter> =
            bible.characters.filter { it.id != center }.take(10)

        fun pos(center: String): Map<String, Offset> {
            val list = ring(center)
            if (list.isEmpty()) return emptyMap()
            // 椭圆而不是正圆：面板是竖长条，正圆会把 2 个节点排成一条竖线。
            val rx = w * .34f
            val ry = h * .31f
            // 1–2 个节点时从右侧起排（横过去），3 个以上从正上方起排（像个环）。
            val start = if (list.size <= 2) 0.0 else -PI / 2
            val step = 2 * PI / list.size
            return list.mapIndexed { i, c ->
                val a = start + i * step
                c.id to Offset(
                    cx + (rx * cos(a)).toFloat(),
                    cy + (ry * sin(a)).toFloat(),
                )
            }.toMap()
        }

        val from = remember(bible, fromId, w, h) { pos(fromId) }
        val to = remember(bible, centerId, w, h) { pos(centerId) }
        val namePaint = remember(ui.palette.ink) {
            android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = with(density) { 11.dp.toPx() } * .82f
                textAlign = android.graphics.Paint.Align.CENTER
            }
        }
        val centerPaint = remember(ui.palette.ink) {
            android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = with(density) { 12.dp.toPx() } * .9f
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        }
        // 3.26.24：名字进圈后的两种字，字号按球径收，颜色每球按底色算。
        val ballPaint = remember {
            android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = with(density) { 10.dp.toPx() }
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        }
        val centerBallPaint = remember {
            android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = with(density) { 11.dp.toPx() }
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        }

        // pointerInput 的 key 必须恒定：以 to 为 key 时，点中节点 → centerId 变 → to 变 →
        // 手势器在抬手前被重启，抬手事件漏到遮罩上，面板会自己 close()（3.26.16 实测）。
        val hitTargets = rememberUpdatedState(to)
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val cur = hitTargets.value
                        val hit = cur.entries.minByOrNull { (it.value - p).getDistance() }
                        if (hit != null && (hit.value - p).getDistance() < nodeR * 1.7f) onPick(hit.key)
                    }
                }
        ) {
            val v = t.value.coerceIn(0f, 1f)
            val center = bible.character(centerId)
            val shownName = bible.character(fromId)?.name ?: ""

            // 旧中心（渐隐）
            if (v < 1f) {
                val old = from
                old.forEach { (id, p0) ->
                    val c = bible.character(id) ?: return@forEach
                    val kind = relationKindBetween(bible, fromId, id) ?: return@forEach
                    drawLine(
                        relationTone(kind, ui).copy(alpha = .5f * (1f - v)),
                        Offset(cx, cy),
                        p0,
                        strokeWidth = 1.4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                val oldBall = bible.character(fromId)?.let { ballColor(it) } ?: ui.palette.faint
                drawCircle(
                    oldBall.copy(alpha = .6f * (1f - v)),
                    radius = centerR,
                    center = Offset(cx, cy),
                )
                if (shownName.isNotBlank()) {
                    centerBallPaint.color = inkOnBall(oldBall, ui.palette).toArgb()
                    centerBallPaint.alpha = (255 * (1f - v)).toInt().coerceIn(0, 255)
                    drawContext.canvas.nativeCanvas.drawText(
                        ballLabel(shownName),
                        cx,
                        cy + centerBallPaint.textSize * .35f,
                        centerBallPaint,
                    )
                }
            }

            // 新中心
            to.forEach { (id, p1) ->
                val c = bible.character(id) ?: return@forEach
                val kind = relationKindBetween(bible, centerId, id)
                // 同一角色在旧布局里的位置就是起点：换中心时它是「走过去」，
                // 不是先塌到中心再长出来（3.26.16 实测第一版的观感像「收起来再打开」）。
                val p0 = from[id] ?: Offset(cx, cy)
                val p = Offset(
                    p0.x + (p1.x - p0.x) * v,
                    p0.y + (p1.y - p0.y) * v,
                )
                if (kind != null) {
                    drawLine(
                        relationTone(kind, ui).copy(alpha = v),
                        Offset(cx, cy),
                        p,
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                val ball = ballColor(c)
                val r = nodeR * (.72f + .28f * v)
                drawCircle(ball.copy(alpha = v), radius = r, center = p)
                drawCircle(
                    ui.palette.ink.copy(alpha = .20f * v),
                    radius = r,
                    center = p,
                    style = Stroke(width = .8.dp.toPx()),
                )
                ballPaint.color = inkOnBall(ball, ui.palette).toArgb()
                ballPaint.alpha = (255 * v).toInt().coerceIn(0, 255)
                drawContext.canvas.nativeCanvas.drawText(
                    ballLabel(c.name),
                    p.x,
                    p.y + ballPaint.textSize * .36f,
                    ballPaint,
                )
            }

            // 显示节点总数上限的提示（>10 时）
            val hidden = (bible.characters.size - 1 - to.size).coerceAtLeast(0)
            if (hidden > 0) {
                namePaint.color = ui.palette.muted.toArgb()
                namePaint.alpha = 255
                drawContext.canvas.nativeCanvas.drawText(
                    "还有 $hidden 位在下面",
                    cx,
                    h - with(density) { 4.dp.toPx() },
                    namePaint,
                )
            }

            // 3.26.24：中心球也用角色自己的颜色，外圈 2dp 强调环区分「当前中心」。
            val centerBall = center?.let { ballColor(it) } ?: ui.palette.faint
            drawCircle(centerBall, radius = centerR, center = Offset(cx, cy))
            drawCircle(
                ui.accent.copy(alpha = .9f),
                radius = centerR + with(density) { 1.dp.toPx() },
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                ui.palette.ink.copy(alpha = .16f),
                radius = centerR,
                center = Offset(cx, cy),
                style = Stroke(width = .8.dp.toPx()),
            )
            centerBallPaint.color = inkOnBall(centerBall, ui.palette).toArgb()
            centerBallPaint.alpha = 255
            drawContext.canvas.nativeCanvas.drawText(
                ballLabel(center?.name ?: ""),
                cx,
                cy + centerBallPaint.textSize * .35f,
                centerBallPaint,
            )
        }
    }
}

private fun relationKindBetween(bible: BookBible, a: String, b: String): RelationKind? =
    bible.relations.firstOrNull {
        (it.from == a && it.to == b) || (it.from == b && it.to == a)
    }?.kind

// ───────────────────────── 时间线 ─────────────────────────

@Composable
private fun TimelinePane(book: Book, onBible: (BookBible) -> Unit, onOpenChapter: (String) -> Unit) {
    val ui = LocalInk.current
    var editing by remember { mutableStateOf<StoryEvent?>(null) }
    var creating by remember { mutableStateOf(false) }
    // 3.26.18：卡片上直接可删（原来只有进编辑框才有「删除」，用户读作「删不掉」）。
    var deleting by remember { mutableStateOf<StoryEvent?>(null) }
    val events = book.bible.events

    // 3.26.20：时间线主轴改成「事件」——所有事件按故事时间排成一条竖轴，
    // 章名只作为卡片上的归属标签；解析不出时间的排最后，加「时间不详」分组。
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (events.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        glyph = Glyph.Clock,
                        title = "时间线还空着",
                        message = "把「哪天发生了什么」记下来，写后面的章节时就不会撞时间。",
                    )
                }
            } else {
                val known = events
                    .mapNotNull { e -> storyTimeKey(e.whenText)?.let { key -> key to e } }
                    .sortedWith(compareBy({ it.first }))
                    .map { it.second }
                val unknown = events.filter { storyTimeKey(it.whenText) == null }
                val total = known.size + unknown.size + if (unknown.isEmpty()) 0 else 1
                var drawn = 0
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    known.forEach { e ->
                        TimelineEventRow(
                            event = e,
                            book = book,
                            isLast = drawn == total - 1,
                            onEdit = { editing = e },
                            onDelete = { deleting = e },
                        )
                        drawn++
                    }
                    if (unknown.isNotEmpty()) {
                        TimelineSectionLabel("时间不详")
                        drawn++
                        unknown.forEach { e ->
                            TimelineEventRow(
                                event = e,
                                book = book,
                                isLast = drawn == total - 1,
                                onEdit = { editing = e },
                                onDelete = { deleting = e },
                            )
                            drawn++
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("新建事件", { creating = true }, Modifier.fillMaxWidth(), glyph = Glyph.Plus)
    }

    deleting?.let { e ->
        ConfirmDialog(
            title = "删除这个事件？",
            message = "「${e.title.ifBlank { "未命名事件" }}」会从时间线移除，正文不受影响。",
            confirmLabel = "删除",
            onConfirm = {
                onBible(book.bible.copy(events = book.bible.events.filterNot { it.id == e.id }))
                deleting = null
            },
            onDismiss = { deleting = null },
            destructive = true,
        )
    }

    if (creating || editing != null) {
        EventEditor(
            book = book,
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { e ->
                val b = book.bible
                onBible(
                    if (editing == null) b.copy(events = b.events + e)
                    else b.copy(events = b.events.map { if (it.id == editing!!.id) e.copy(id = it.id) else it }),
                )
                creating = false
                editing = null
            },
            onDelete = { e ->
                onBible(book.bible.copy(events = book.bible.events.filterNot { it.id == e.id }))
                editing = null
            },
        )
    }
}

// 3.26.20：时间轴上的一条事件——左边竖线 + 事件点，右边事件卡。
@Composable
private fun TimelineEventRow(
    event: StoryEvent,
    book: Book,
    isLast: Boolean,
    onEdit: (StoryEvent) -> Unit,
    onDelete: (StoryEvent) -> Unit,
) {
    val ui = LocalInk.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            Modifier.width(22.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .size(7.dp)
                    .background(ui.palette.muted, CircleShape),
            )
            if (!isLast) {
                Box(
                    Modifier
                        .width(1.dp)
                        .weight(1f)
                        .background(ui.palette.line),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp, bottom = if (isLast) 4.dp else 14.dp)) {
            EventCard(
                event = event,
                book = book,
                chapterLabel = event.chapterIds
                    .mapNotNull { id -> book.chapters.firstOrNull { it.id == id }?.title }
                    .joinToString("、")
                    .ifBlank { "未关联" },
                onEdit = { onEdit(event) },
                onDelete = { onDelete(event) },
            )
        }
    }
}

// 「时间不详」分组：轴不断，只加一行小标题。
@Composable
private fun TimelineSectionLabel(label: String) {
    val ui = LocalInk.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            Modifier.width(22.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(ui.palette.line))
        }
        Column(Modifier.weight(1f).padding(start = 10.dp, top = 4.dp, bottom = 10.dp)) {
            Txt(label, size = 11f, color = ui.palette.muted)
        }
    }
}

@Composable
private fun EventCard(
    event: StoryEvent,
    book: Book,
    chapterLabel: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val ui = LocalInk.current
    MoyuSurface(Modifier.fillMaxWidth(), radius = 18f, flat = true) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(event.whenText.ifBlank { "未标时间" }, size = 10.5f, color = ui.palette.muted)
                Spacer(Modifier.weight(1f))
                // 章名只作归属标签（时间线主轴是事件时间）。
                Txt(
                    chapterLabel,
                    size = 10.5f,
                    color = ui.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Glyph.Trash, ui.palette.muted, 14.dp) }
            }
            Spacer(Modifier.height(6.dp))
            Txt(event.title.ifBlank { "未命名事件" }, size = 15f, weight = FontWeight.Medium)
            if (event.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Txt(event.note, size = 11.5f, color = ui.palette.muted, lineHeight = 18f)
            }
            val names = event.characterIds.mapNotNull { id -> book.bible.character(id)?.name }
            if (names.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Txt(names.joinToString(" · "), size = 10.5f, color = ui.palette.muted)
            }
        }
    }
}

// ───────────────────────── 大纲 ─────────────────────────

@Composable
private fun OutlinePane(book: Book, onBible: (BookBible) -> Unit) {
    val ui = LocalInk.current
    var beatTarget by remember { mutableStateOf<OutlineNode?>(null) }
    // 3.26.18：补删除入口。原来情节点与整条大纲一条都删不掉，还能造出空节点。
    var deletingBeat by remember { mutableStateOf<Pair<OutlineNode, OutlineBeat>?>(null) }
    var menuNode by remember { mutableStateOf<OutlineNode?>(null) }
    var confirmNode by remember { mutableStateOf<Pair<OutlineNode, Boolean>?>(null) }
    val bible = book.bible

    if (book.chapters.isEmpty()) {
        EmptyState(
            glyph = Glyph.List,
            title = "还没有章节",
            message = "先建一章，大纲才有地方挂。",
        )
        return
    }

    // 3.26.19：工作台模式——章卡列表自己滚，不把整块面板撑高。
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            book.chapters.forEachIndexed { index, chapter ->
        // 3.26.20：空大纲节点按「待写」显示，不再报 0/0；编辑时仍复用这条空节点，避免越堆越多。
        val rawNode = bible.outline.firstOrNull { it.chapterId == chapter.id }
        val node = rawNode?.takeIf { it.beats.isNotEmpty() }
        MoyuSurface(Modifier.fillMaxWidth(), radius = 18f, flat = true) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { beatTarget = rawNode ?: OutlineNode(chapterId = chapter.id, title = chapter.title) }
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(
                        "%02d".format(index + 1),
                        size = 10f,
                        color = ui.palette.muted,
                        letterSpacing = .8f,
                    )
                    Spacer(Modifier.width(10.dp))
                    Txt(chapter.title, size = 15f, weight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Txt(
                        if (node == null) "待写" else "${node.beats.count { it.done }}/${node.beats.size}",
                        size = 10.5f,
                        color = if (node == null) ui.palette.muted else ui.accent,
                    )
                    // 3.26.19：先「···」（仅已有大纲的章）再「+」，让「+」永远在最右侧同一列。
                    if (node != null) {
                        Spacer(Modifier.width(2.dp))
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable { menuNode = node },
                            contentAlignment = Alignment.Center,
                        ) { LineIcon(Glyph.More, ui.palette.muted, 16.dp) }
                    }
                    Spacer(Modifier.width(2.dp))
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable {
                                beatTarget = rawNode ?: OutlineNode(
                                    chapterId = chapter.id,
                                    title = chapter.title,
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(Glyph.Plus, ui.palette.muted, 16.dp) }
                }
                if (node != null && node.beats.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    node.beats.forEach { b ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onBible(
                                        bible.copy(
                                            outline = bible.outline.map { n ->
                                                if (n.id != node.id) n else n.copy(
                                                    beats = n.beats.map {
                                                        if (it.id == b.id) it.copy(done = !it.done) else it
                                                    },
                                                )
                                            },
                                        ),
                                    )
                                }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(13.dp)
                                    .border(
                                        if (b.done) 0.dp else .9.dp,
                                        if (b.done) Color.Transparent else ui.palette.muted.copy(alpha = .5f),
                                        SmoothCorner(4.dp)
                                    )
                                    .background(
                                        if (b.done) ui.accent else Color.Transparent,
                                        SmoothCorner(4.dp),
                                    ),
                            )
                            Spacer(Modifier.width(10.dp))
                            Txt(
                                b.text,
                                size = 12f,
                                color = if (b.done) ui.palette.muted else ui.palette.ink,
                                lineHeight = 18f,
                            )
                            Spacer(Modifier.weight(1f))
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .clickable { deletingBeat = node to b },
                                contentAlignment = Alignment.Center,
                            ) {
                                LineIcon(
                                    Glyph.Trash,
                                    ui.palette.muted.copy(alpha = .75f),
                                    13.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
            }
        }
    }

    // 3.26.18：情节点删除（行尾垃圾桶 → 确认）。
    deletingBeat?.let { (node, beat) ->
        ConfirmDialog(
            title = "删除这个情节点？",
            message = "「${beat.text}」会从这一章的大纲移除。",
            confirmLabel = "删除",
            onConfirm = {
                val remaining = node.beats.filterNot { it.id == beat.id }
                onBible(
                    bible.copy(
                        // 3.26.20：删掉最后一条情节点时连空节点一起清掉，章卡回到「待写」。
                        outline = if (remaining.isEmpty()) {
                            bible.outline.filterNot { it.id == node.id }
                        } else {
                            bible.outline.map { n ->
                                if (n.id != node.id) n else n.copy(beats = remaining)
                            }
                        },
                    ),
                )
                deletingBeat = null
            },
            onDismiss = { deletingBeat = null },
            destructive = true,
        )
    }

    // 3.26.18：整条大纲的删除/清空（章卡「···」→ 菜单 → 确认，两个 Dialog 不同时在同一帧出现）。
    menuNode?.let { node ->
        GlassDialog(onDismiss = { menuNode = null }, dismissOnOutsideClick = false) {
            DialogTitle(
                "本章大纲",
                subtitle = book.chapters.firstOrNull { it.id == node.chapterId }?.title ?: node.title,
            )
            Spacer(Modifier.height(10.dp))
            SettingRow(
                "清空情节点",
                "${node.beats.size} 个情节点会移除，章节正文不动",
                Glyph.Trash,
                onClick = {
                    menuNode = null
                    confirmNode = node to true
                },
            )
            RowDivider()
            SettingRow(
                "删除整条大纲",
                "这一章回到「待写」，章节正文不动",
                Glyph.Trash,
                onClick = {
                    menuNode = null
                    confirmNode = node to false
                },
            )
            Spacer(Modifier.height(14.dp))
            GlassButton("取消", { menuNode = null }, Modifier.fillMaxWidth())
        }
    }
    confirmNode?.let { (node, clearOnly) ->
        ConfirmDialog(
            title = if (clearOnly) "清空这章的情节点？" else "删除这章的大纲？",
            message = if (clearOnly) "情节点会全部移除；章节正文不受影响。"
            else "整条大纲会移除，这一章回到「待写」；章节正文不受影响。",
            confirmLabel = if (clearOnly) "清空" else "删除",
            onConfirm = {
                onBible(
                    bible.copy(
                        // 3.26.20：清空情节点 = 这一章回到「待写」，空节点不留在数据里。
                        outline = bible.outline.filterNot { it.id == node.id },
                    ),
                )
                confirmNode = null
            },
            onDismiss = { confirmNode = null },
            destructive = true,
        )
    }

    beatTarget?.let { node ->
        BeatEditor(
            book = book,
            node = node,
            onDismiss = { beatTarget = null },
            onSave = { updated ->
                val exists = bible.outline.any { it.id == updated.id }
                onBible(
                    bible.copy(
                        outline = if (exists) bible.outline.map { if (it.id == updated.id) updated else it }
                        else bible.outline + updated,
                    ),
                )
                beatTarget = null
            },
        )
    }
}

// ───────────────────────── 编辑器 ─────────────────────────

@Composable
private fun CharacterEditor(
    initial: StoryCharacter?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String?) -> Unit,
) {
    val ui = LocalInk.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var role by remember { mutableStateOf(initial?.role ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    // 3.26.24：null = 自动色，按角色 id 稳定取色板。
    var color by remember { mutableStateOf(initial?.color) }
    GlassDialog(onDismiss, dismissOnOutsideClick = false) {
        DialogTitle(if (initial == null) "新建角色" else "编辑角色")
        Spacer(Modifier.height(14.dp))
        InkField(name, { name = it }, "名字")
        Spacer(Modifier.height(10.dp))
        InkField(role, { role = it }, "身份：主角 / 配角 / 反派（可空）")
        Spacer(Modifier.height(10.dp))
        InkField(note, { note = it }, "一句话：他是谁、想要什么（可空）", singleLine = false, minHeight = 76.dp)
        Spacer(Modifier.height(16.dp))
        Txt("球的颜色", size = 11f, color = ui.palette.muted)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassChip("自动", color == null) { color = null }
            accentChoices.forEach { (_, value) ->
                val hex = "#%06X".format(value and 0xFFFFFF)
                ColorDot(Color(value), color.equals(hex, true)) { color = hex }
            }
        }
        Spacer(Modifier.height(10.dp))
        val hsv = remember(color) {
            FloatArray(3).also { out ->
                val argb = color?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
                    ?: Color(0xFF7A5B3A).toArgb()
                android.graphics.Color.colorToHSV(argb, out)
            }
        }
        HueSlider(hsv[0]) { hue ->
            val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, .50f, .58f))
            color = String.format("#%06X", argb and 0xFFFFFF)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassButton("取消", onDismiss, Modifier.weight(1f))
            PrimaryButton(
                "保存",
                { onSave(name.trim(), role.trim(), note.trim(), color) },
                Modifier.weight(1f),
                enabled = name.isNotBlank(),
            )
        }
    }
}

@Composable
private fun RelationEditor(
    bible: BookBible,
    from: StoryCharacter,
    onDismiss: () -> Unit,
    onSave: (String, RelationKind) -> Unit,
) {
    val ui = LocalInk.current
    val others = bible.characters.filter { it.id != from.id }
    var target by remember { mutableStateOf(others.firstOrNull()?.id) }
    var kind by remember { mutableStateOf(RelationKind.FRIEND) }
    GlassDialog(onDismiss, dismissOnOutsideClick = false) {
        DialogTitle("给「${from.name.ifBlank { "未命名" }}」连一条线")
        Spacer(Modifier.height(14.dp))
        Txt("和谁", size = 11f, color = ui.palette.muted)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            others.forEach { c ->
                GlassChip(c.name.ifBlank { "未命名" }, c.id == target) { target = c.id }
            }
        }
        Spacer(Modifier.height(14.dp))
        Txt("什么关系", size = 11f, color = ui.palette.muted)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RelationKind.entries.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k -> GlassChip(k.label, k == kind) { kind = k } }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassButton("取消", onDismiss, Modifier.weight(1f))
            PrimaryButton(
                "保存",
                { target?.let { onSave(it, kind) } },
                Modifier.weight(1f),
                enabled = target != null,
            )
        }
    }
}

@Composable
private fun EventEditor(
    book: Book,
    initial: StoryEvent?,
    onDismiss: () -> Unit,
    onSave: (StoryEvent) -> Unit,
    onDelete: (StoryEvent) -> Unit,
) {
    val ui = LocalInk.current
    var whenText by remember { mutableStateOf(initial?.whenText ?: "") }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    // 3.26.21：一个事件可属多章；章节选择改成「一行摘要 + 底部抽屉多选」。
    var chapterIds by remember { mutableStateOf(initial?.chapterIds ?: emptyList()) }
    var pickerOpen by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(initial?.characterIds ?: emptyList()) }
    GlassDialog(onDismiss, dismissOnOutsideClick = false) {
        DialogTitle(if (initial == null) "新建事件" else "编辑事件")
        Spacer(Modifier.height(14.dp))
        InkField(whenText, { whenText = it }, "故事内时间：11 月 7 日 / 三年前")
        Spacer(Modifier.height(10.dp))
        InkField(title, { title = it }, "发生了什么")
        Spacer(Modifier.height(10.dp))
        InkField(note, { note = it }, "补充（可空）", singleLine = false, minHeight = 70.dp)
        if (book.chapters.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Txt("发生在哪一章", size = 11f, color = ui.palette.muted)
            Spacer(Modifier.height(8.dp))
            val chapterSummary = book.chapters
                .filter { it.id in chapterIds }
                .joinToString("、") { it.title }
                .ifBlank { "不关联" }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(SmoothCorner(14.dp))
                    .background(ui.palette.faint)
                    .clickable { pickerOpen = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(
                    chapterSummary,
                    size = 13f,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                LineIcon(Glyph.Chevron, ui.palette.muted, 16.dp)
            }
        }
        if (book.bible.characters.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Txt("涉及角色", size = 11f, color = ui.palette.muted)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                book.bible.characters.forEach { c ->
                    GlassChip(c.name.ifBlank { "未命名" }, c.id in picked) {
                        picked = if (c.id in picked) picked - c.id else picked + c.id
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (initial != null) {
                GlassButton("删除", { onDelete(initial) }, Modifier.weight(1f))
            } else {
                GlassButton("取消", onDismiss, Modifier.weight(1f))
            }
            PrimaryButton(
                "保存",
                {
                    onSave(
                        (initial ?: StoryEvent()).copy(
                            whenText = whenText.trim(),
                            title = title.trim(),
                            note = note.trim(),
                            chapterIds = chapterIds.distinct(),
                            characterIds = picked,
                        ),
                    )
                },
                Modifier.weight(1f),
                enabled = title.isNotBlank() || whenText.isNotBlank(),
            )
        }
        // 3.26.21：底部抽屉多选——事件可同时发生在多章。
        if (pickerOpen) {
            ModalSheet(onDismiss = { pickerOpen = false }) {
                DialogTitle(
                    "发生在哪一章",
                    subtitle = "可多选；都不选表示不关联",
                    onClose = { pickerOpen = false },
                )
                Spacer(Modifier.height(12.dp))
                book.chapters.forEach { c ->
                    val checked = c.id in chapterIds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(SmoothCorner(12.dp))
                            .bouncy {
                                chapterIds = if (checked) chapterIds - c.id
                                else (chapterIds + c.id).distinct()
                            }
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Txt(c.title, size = 14f)
                        Spacer(Modifier.weight(1f))
                        SelectMark(
                            checked = checked,
                            onToggle = { on ->
                                chapterIds = if (on) (chapterIds + c.id).distinct()
                                else chapterIds - c.id
                            },
                            size = 22.dp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton("完成", { pickerOpen = false }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun BeatEditor(
    book: Book,
    node: OutlineNode,
    onDismiss: () -> Unit,
    onSave: (OutlineNode) -> Unit,
) {
    val ui = LocalInk.current
    var text by remember { mutableStateOf("") }
    // 3.26.18：点「已有」里的一条 = 编辑它。原来这里纯展示，改字只能删了重加；
    // 同时把「添加」的 enabled 从恒 true 改成必须非空，杜绝空情节点/空大纲节点。
    var editingBeat by remember { mutableStateOf<OutlineBeat?>(null) }
    val chapterTitle = book.chapters.firstOrNull { it.id == node.chapterId }?.title ?: node.title
    GlassDialog(onDismiss, dismissOnOutsideClick = false) {
        DialogTitle(
            if (editingBeat == null) "第 ${node.beats.size + 1} 个情节点" else "编辑情节点",
            subtitle = chapterTitle,
        )
        Spacer(Modifier.height(14.dp))
        InkField(
            text,
            { text = it },
            if (editingBeat == null) "这一章要发生的事" else "改这一条情节点",
            singleLine = false,
            minHeight = 80.dp,
        )
        if (node.beats.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Txt("已有", size = 11f, color = ui.palette.muted)
            Spacer(Modifier.height(6.dp))
            node.beats.forEach { b ->
                val selected = editingBeat?.id == b.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(SmoothCorner(10.dp))
                        .background(if (selected) ui.palette.faint else Color.Transparent)
                        .clickable {
                            if (selected) {
                                editingBeat = null
                                text = ""
                            } else {
                                editingBeat = b
                                text = b.text
                            }
                        }
                        .padding(vertical = 5.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt("·", size = 12f, color = ui.palette.muted)
                    Spacer(Modifier.width(8.dp))
                    Txt(
                        b.text,
                        size = 12f,
                        lineHeight = 18f,
                        color = if (selected) ui.accent else ui.palette.ink,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassButton("取消", onDismiss, Modifier.weight(1f))
            PrimaryButton(
                if (editingBeat == null) "添加" else "保存修改",
                {
                    val body = text.trim()
                    if (body.isNotBlank()) {
                        val current = editingBeat
                        if (current == null) {
                            onSave(node.copy(beats = node.beats + OutlineBeat(text = body)))
                        } else {
                            onSave(
                                node.copy(
                                    beats = node.beats.map {
                                        if (it.id == current.id) it.copy(text = body) else it
                                    },
                                ),
                            )
                        }
                    }
                },
                Modifier.weight(1f),
                enabled = text.isNotBlank(),
            )
        }
    }
}

// ───────────────────────── 故事时间解析（只读，不写回数据） ─────────────────────────
// 3.26.19：时间线原来按录入顺序排，看不出「谁先谁后」。现有数据只有自由文本 whenText，
// 没有 storyTime 字段；这里只做**显示排序**的启发式解析：同章事件按解析出的时间升序，
// 解析失败的排同章末尾。解析结果不写回 moyu.json、不改 schema；C 批让问书写入结构化
// 故事时间后再替换这里。

private val storyIsoDate = Regex("(\\d{4})\\s*[-/.]\\s*(\\d{1,2})\\s*[-/.]\\s*(\\d{1,2})")
private val storyShortDate = Regex("(?<![\\d/-])(\\d{1,2})\\s*[-/]\\s*(\\d{1,2})(?![\\d/-])")
private val storyDate = Regex("(\\d{1,4})\\s*年\\s*(\\d{1,2})\\s*月(?:\\s*(\\d{1,2})\\s*[日号])?")
private val storyMonthDay = Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]")
private val storyNumberUnit = Regex("(\\d+|[零一二两三四五六七八九十百千]+)\\s*(年|岁|个月|月|日|天|周|星期|点|刻)")

/** 中文输入法常把 `-` `/` `.` 和数字打成全角，解析前先归一成 ASCII。 */
private fun normalizeStoryTime(text: String): String {
    val sb = StringBuilder(text.length)
    for (c in text) {
        sb.append(
            when (c) {
                '－', '—', '–', '−' -> '-'
                '／' -> '/'
                '．', '。' -> '.'
                '　' -> ' '
                in '０'..'９' -> '0' + (c - '０')
                else -> c
            },
        )
    }
    return sb.toString()
}

/** 把「11 月 7 日 / 三年前 / 2026-11-07」这类自由文本压成一个粗略可比的整数；解析不了返回 null。 */
internal fun storyTimeKey(raw: String): Int? {
    if (raw.isBlank()) return null
    val text = normalizeStoryTime(raw)
    storyIsoDate.find(text)?.let { m ->
        val year = m.groupValues[1].toIntOrNull() ?: return@let
        val month = m.groupValues[2].toIntOrNull() ?: return@let
        val day = m.groupValues[3].toIntOrNull() ?: return@let
        return year * 372 + month * 31 + day
    }
    storyShortDate.find(text)?.let { m ->
        val month = m.groupValues[1].toIntOrNull() ?: return@let
        val day = m.groupValues[2].toIntOrNull() ?: return@let
        return month * 31 + day
    }
    storyDate.find(text)?.let { m ->
        val year = m.groupValues[1].toIntOrNull() ?: return@let
        val month = m.groupValues[2].toIntOrNull() ?: return@let
        val day = m.groupValues[3].toIntOrNull() ?: 0
        return year * 372 + month * 31 + day
    }
    storyMonthDay.find(text)?.let { m ->
        val month = m.groupValues[1].toIntOrNull() ?: return@let
        val day = m.groupValues[2].toIntOrNull() ?: return@let
        return month * 31 + day
    }
    storyNumberUnit.find(text)?.let { m ->
        val n = m.groupValues[1].toIntOrNull() ?: chineseNumber(m.groupValues[1]) ?: return@let
        val days = when (m.groupValues[2]) {
            "年", "岁" -> 360
            "个月", "月" -> 30
            "周", "星期" -> 7
            else -> 1
        }
        val key = n * days
        // 「三年前 / 以前 / 之前」是回忆，排序时放到常规时间之前。
        return if (text.contains("前") || text.contains("以前") || text.contains("之前")) -key else key
    }
    return null
}

private fun chineseNumber(text: String): Int? {
    val digits = mapOf(
        '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )
    var section = 0
    var number = 0
    for (c in text) {
        when {
            digits.containsKey(c) -> number = digits.getValue(c)
            c == '十' -> { section += (if (number == 0) 1 else number) * 10; number = 0 }
            c == '百' -> { section += (if (number == 0) 1 else number) * 100; number = 0 }
            c == '千' -> { section += (if (number == 0) 1 else number) * 1000; number = 0 }
            else -> return null
        }
    }
    return section + number
}
/**
 * 3.26.23: floating story bible for the editor dock. Same panes as the story
 * page (single source), editable in place. Bottom sheet so the writer keeps
 * the page context instead of navigating away.
 */
@Composable
internal fun StoryBibleFloatSheet(
    book: Book,
    onBible: (BookBible) -> Unit,
    onDismiss: () -> Unit,
    journal: List<Pair<String, Long>> = emptyList(),
    onUndo: (() -> Unit)? = null,
) {
    val ui = LocalInk.current
    var tab by remember { mutableIntStateOf(2) }
    var showLog by remember { mutableStateOf(false) }
    val sheetH = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * .60f).dp
    ModalSheet(onDismiss = onDismiss, imeAware = false) {
        Column(Modifier.fillMaxWidth().height(sheetH)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (showLog) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).clickable { showLog = false },
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(Glyph.Chevron, ui.palette.muted, 15.dp) }
                    Spacer(Modifier.width(6.dp))
                }
                Txt(if (showLog) "AI 改动" else "故事", size = 20f, weight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (!showLog && onUndo != null) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).clickable { onUndo() },
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(Glyph.Undo, ui.palette.muted, 15.dp) }
                }
                if (!showLog && journal.isNotEmpty()) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).clickable { showLog = true },
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(Glyph.Clock, ui.palette.muted, 15.dp) }
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier.size(30.dp).clip(CircleShape).clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Glyph.Close, ui.palette.muted, 15.dp) }
            }
            Spacer(Modifier.height(10.dp))
            if (showLog) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (journal.isEmpty()) {
                        Txt("还没有 AI 改动", size = 12f, color = ui.palette.muted)
                    } else {
                        journal.forEach { (label, time) ->
                            Txt(storyStamp(time), size = 10f, color = ui.palette.muted)
                            Spacer(Modifier.height(2.dp))
                            Txt(label, size = 13f, lineHeight = 19f)
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            } else {
                Segmented(listOf("角色", "时间线", "大纲"), tab, { tab = it })
                Spacer(Modifier.height(8.dp))
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> CharacterPane(book, onBible)
                        1 -> TimelinePane(book, onBible, onOpenChapter = {})
                        else -> OutlinePane(book, onBible)
                    }
                }
            }
        }
    }
}

/** mm-dd hh:mm for the AI change log lines. */
private fun storyStamp(ms: Long): String {
    if (ms <= 0L) return ""
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    fun two(v: Int) = if (v < 10) "0$v" else v.toString()
    return "${two(c.get(java.util.Calendar.MONTH) + 1)}-${two(c.get(java.util.Calendar.DAY_OF_MONTH))} " +
        "${two(c.get(java.util.Calendar.HOUR_OF_DAY))}:${two(c.get(java.util.Calendar.MINUTE))}"
}