package com.moyu.writer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
    val bible = book.bible
    var tab by remember { mutableIntStateOf(0) }

    // 这张面板整块是留白（关系图占了大半），所以 surface 也铺纸色：
    // 与 SheetPane 的纸底同色，中间那块「灰框」才消失，而卡片仍是卡片色。
    InlineModalSheet(onDismiss, tone = LocalInk.current.palette.paper) { close ->
        DialogTitle(
            "故事",
            subtitle = "${bible.characters.size} 位角色 · ${bible.relations.size} 条关系 · " +
                "${bible.events.size} 个事件 · ${bible.outline.size} 章大纲",
            onClose = close,
        )
        Spacer(Modifier.height(14.dp))
        Segmented(listOf("角色", "时间线", "大纲"), tab, { tab = it })
        Spacer(Modifier.height(14.dp))
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.heightIn(min = 600.dp),
            transitionSpec = {
                val forward = targetState > initialState
                val dir = if (forward) 1 else -1
                (slideInHorizontally(spring(MOYU_DAMPING, 420f)) { it * dir / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally(spring(MOYU_DAMPING, 420f)) { -it * dir / 3 } + fadeOut())
            },
            label = "storyTab",
        ) { index ->
            SheetPane {
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

    if (bible.characters.isEmpty()) {
        EmptyState(
            glyph = Glyph.Note,
            title = "还没有角色",
            message = "先记下一个名字，关系线和时间线都从这里长出来。",
            action = { PrimaryButton("新建角色", { creating = true }, glyph = Glyph.Plus) },
        )
    } else {
        RelationGraph(
            bible = bible,
            centerId = centerId,
            fromId = fromId,
            tick = tick,
            onPick = { pick(it) },
            modifier = Modifier.fillMaxWidth().height(240.dp),
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
                            modifier = Modifier.clickable {
                                onBible(bible.copy(relations = bible.relations.filterNot { it.id == r.id }))
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("新建角色", { creating = true }, Modifier.fillMaxWidth(), glyph = Glyph.Plus)
    }

    if (creating || editing != null) {
        CharacterEditor(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { name, role, note ->
                val b = book.bible
                if (editing == null) {
                    val c = StoryCharacter(name = name, role = role, note = note)
                    onBible(b.copy(characters = b.characters + c))
                    pick(c.id)
                } else {
                    onBible(
                        b.copy(
                            characters = b.characters.map {
                                if (it.id == editing!!.id) it.copy(name = name, role = role, note = note) else it
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
                drawCircle(
                    ui.palette.faint.copy(alpha = .6f * (1f - v)),
                    radius = centerR,
                    center = Offset(cx, cy),
                )
                if (shownName.isNotBlank()) {
                    namePaint.color = ui.palette.ink.toArgb()
                    drawContext.canvas.nativeCanvas.drawText(
                        shownName,
                        cx,
                        cy + centerPaint.textSize * .35f,
                        centerPaint,
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
                drawCircle(ui.palette.faint, radius = nodeR * (.72f + .28f * v), center = p)
                drawCircle(
                    ui.palette.line.copy(alpha = .9f),
                    radius = nodeR * (.72f + .28f * v),
                    center = p,
                    style = Stroke(width = .8.dp.toPx()),
                )
                namePaint.color = ui.palette.ink.toArgb()
                namePaint.alpha = (255 * v).toInt().coerceIn(0, 255)
                drawContext.canvas.nativeCanvas.drawText(
                    c.name.ifBlank { "未命名" },
                    p.x,
                    p.y + nodeR + with(density) { 14.dp.toPx() },
                    namePaint,
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

            // 中心节点画在最上面
            drawCircle(ui.palette.paper, radius = centerR, center = Offset(cx, cy))
            drawCircle(
                ui.palette.ink.copy(alpha = .16f),
                radius = centerR,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx()),
            )
            centerPaint.color = ui.palette.ink.toArgb()
            drawContext.canvas.nativeCanvas.drawText(
                center?.name?.ifBlank { "未命名" } ?: "",
                cx,
                cy + centerPaint.textSize * .35f,
                centerPaint,
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
    val events = book.bible.events

    if (events.isEmpty()) {
        EmptyState(
            glyph = Glyph.Clock,
            title = "时间线还空着",
            message = "把「哪天发生了什么」记下来，写后面的章节时就不会撞时间。",
            action = { PrimaryButton("新建事件", { creating = true }, glyph = Glyph.Plus) },
        )
    } else {
        events.forEach { e ->
            MoyuSurface(Modifier.fillMaxWidth(), radius = 18f, flat = true) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editing = e }
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt(
                            e.whenText.ifBlank { "未标时间" },
                            size = 10.5f,
                            color = ui.palette.muted,
                        )
                        Spacer(Modifier.weight(1f))
                        if (e.chapterId != null) {
                            val title = book.chapters.firstOrNull { it.id == e.chapterId }?.title
                            if (title != null) {
                                Txt(
                                    title,
                                    size = 10.5f,
                                    color = ui.accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { onOpenChapter(e.chapterId) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Txt(e.title.ifBlank { "未命名事件" }, size = 15f, weight = FontWeight.Medium)
                    if (e.note.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Txt(e.note, size = 11.5f, color = ui.palette.muted, lineHeight = 18f)
                    }
                    val names = e.characterIds.mapNotNull { id -> book.bible.character(id)?.name }
                    if (names.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Txt(names.joinToString(" · "), size = 10.5f, color = ui.palette.muted)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(4.dp))
        PrimaryButton("新建事件", { creating = true }, Modifier.fillMaxWidth(), glyph = Glyph.Plus)
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

// ───────────────────────── 大纲 ─────────────────────────

@Composable
private fun OutlinePane(book: Book, onBible: (BookBible) -> Unit) {
    val ui = LocalInk.current
    var beatTarget by remember { mutableStateOf<OutlineNode?>(null) }
    val bible = book.bible

    if (book.chapters.isEmpty()) {
        EmptyState(
            glyph = Glyph.List,
            title = "还没有章节",
            message = "先建一章，大纲才有地方挂。",
        )
        return
    }

    book.chapters.forEachIndexed { index, chapter ->
        val node = bible.outline.firstOrNull { it.chapterId == chapter.id }
        MoyuSurface(Modifier.fillMaxWidth(), radius = 18f, flat = true) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { beatTarget = node ?: OutlineNode(chapterId = chapter.id, title = chapter.title) }
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
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
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
    onSave: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var role by remember { mutableStateOf(initial?.role ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    GlassDialog(onDismiss) {
        DialogTitle(if (initial == null) "新建角色" else "编辑角色")
        Spacer(Modifier.height(14.dp))
        InkField(name, { name = it }, "名字")
        Spacer(Modifier.height(10.dp))
        InkField(role, { role = it }, "身份：主角 / 配角 / 反派（可空）")
        Spacer(Modifier.height(10.dp))
        InkField(note, { note = it }, "一句话：他是谁、想要什么（可空）", singleLine = false, minHeight = 76.dp)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassButton("取消", onDismiss, Modifier.weight(1f))
            PrimaryButton(
                "保存",
                { onSave(name.trim(), role.trim(), note.trim()) },
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
    GlassDialog(onDismiss) {
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
    var chapterId by remember { mutableStateOf(initial?.chapterId) }
    var picked by remember { mutableStateOf(initial?.characterIds ?: emptyList()) }
    GlassDialog(onDismiss) {
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
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassChip("不关联", chapterId == null) { chapterId = null }
                book.chapters.forEach { c ->
                    GlassChip(c.title, chapterId == c.id) { chapterId = c.id }
                }
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
                            chapterId = chapterId,
                            characterIds = picked,
                        ),
                    )
                },
                Modifier.weight(1f),
                enabled = title.isNotBlank() || whenText.isNotBlank(),
            )
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
    val chapterTitle = book.chapters.firstOrNull { it.id == node.chapterId }?.title ?: node.title
    GlassDialog(onDismiss) {
        DialogTitle("第 ${node.beats.size + 1} 个情节点", subtitle = chapterTitle)
        Spacer(Modifier.height(14.dp))
        InkField(text, { text = it }, "这一章要发生的事", singleLine = false, minHeight = 80.dp)
        if (node.beats.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Txt("已有", size = 11f, color = ui.palette.muted)
            Spacer(Modifier.height(6.dp))
            node.beats.forEach { b ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt("·", size = 12f, color = ui.palette.muted)
                    Spacer(Modifier.width(8.dp))
                    Txt(b.text, size = 12f, lineHeight = 18f)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassButton("取消", onDismiss, Modifier.weight(1f))
            PrimaryButton(
                "添加",
                {
                    if (text.isNotBlank()) {
                        onSave(node.copy(beats = node.beats + OutlineBeat(text = text.trim())))
                    } else {
                        onSave(node)
                    }
                },
                Modifier.weight(1f),
                enabled = text.isNotBlank() || node.id.isNotEmpty(),
            )
        }
    }
}
