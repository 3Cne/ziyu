package com.moyu.writer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
    // 3.26.45：每个 tab 自己的「我的 / AI」页码 + 同步后的一步撤销，切 tab 不回到第一页。
    val pager = remember { StoryPagerState() }
    // 3.26.45：待同意的 AI 改动（AiBible 侧车 → 差异卡；映射在文件末尾 storyPendingChanges）。
    var pending by remember(book.id) { mutableStateOf(storyPendingChanges(book.id)) }
    LaunchedEffect(book.bible) { pending = storyPendingChanges(book.id) }
    fun resolveChange(change: StoryPendingChange, accept: Boolean) {
        storyResolveChange(book.id, change.id, accept)
        pending = pending.filterNot { it.id == change.id }
        ToastBus.show(if (accept) "已同意这一处 AI 改动" else "已拒绝这一处 AI 改动")
    }

    // 3.26.19：全屏工作台——标题/分段固定，内容区自己滚，切 tab 高度不跳。
    // 3.26.46：滑动内容铺到面板本体（而不是被 content 槽两侧的 20dp chrome 裁在里面），
    // 用户报的「边框/内窗口把内容挡住」就是这层裁剪造成的。
    val ui = LocalInk.current
    val motion = ui.settings.motion != "none"
    val synced = remember(book.bible, pager.syncTick) { bibleSyncedIds(book.id) }
    InlineModalSheet(
        onDismiss,
        tone = ui.palette.paper,
        imeAware = false,
        dismissOnOutsideClick = false,
        fillHeight = true,
        pinnedContent = { close ->
            DialogTitle("故事", onClose = close)
            Spacer(Modifier.height(12.dp))
            Segmented(listOf("角色", "时间线", "大纲"), tab, { tab = it })
            // 3.26.43：打开即触发「全书补齐」（受 3 分钟窗口节流）；有进度时显示一行 + 手动补齐。
            val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
            val store = LocalStore.current
            LaunchedEffect(book.id) {
                // 3.26.45：给 AiBible 的无参入口预热 Context / Store（待同意、同步预览都靠它取数据）。
                AiBible.host(appCtx, store)
                AiBibleSync.noteBookOpened(appCtx, store, book.id)
                pending = storyPendingChanges(book.id)
            }
            val sync = AiBibleSync.progressFor(book.id)
            if (sync.label.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(sync.label, size = 10.5f, color = ui.palette.ink.copy(alpha = .55f))
                    if (!sync.running) {
                        Spacer(Modifier.width(8.dp))
                        Txt(
                            "再补一次",
                            size = 10.5f,
                            color = ui.accent,
                            modifier = Modifier.bouncy(scale = .94f) {
                                AiBibleSync.syncAll(appCtx, store, book.id, force = true)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        },
    ) { _ ->
        // 3.26.46：只滑内容、只裁在面板本体；页与面板之间不再有「内窗口」边框。
        Box(Modifier.fillMaxSize().storyBleed().clipToBounds()) {
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { storyPageTransform(motion, targetState > initialState) },
                label = "storyTab",
            ) { index ->
                // 页自己补回 chrome 的 20dp 边距，静止时与旧布局逐像素一致。
                Column(Modifier.fillMaxSize().padding(horizontal = STORY_CHROME_INSET)) {
                    when (index) {
                        0 -> CharacterTab(book, onBible, pager, pending, synced) { c, a -> resolveChange(c, a) }
                        1 -> TimelineTab(book, onBible, onOpenChapter, pager, pending, synced) { c, a -> resolveChange(c, a) }
                        else -> OutlineTab(book, onBible, pager, pending, synced) { c, a -> resolveChange(c, a) }
                    }
                }
            }
        }
    }
}

// ───────────────────────── 角色 + 关系 ─────────────────────────

@Composable
private fun CharacterMinePane(book: Book, onBible: (BookBible) -> Unit, synced: Set<String>) {
    // 3.26.45：作者页只放 origin == "author" 的角色/关系；AI 写进来的在「AI」页（只读）。
    // 3.26.46：同步过的 AI 条目也铺在作者页（同步只打标记、不搬数据），否则点了同步什么都看不见。
    val bible = book.bible.mineWith(synced)
    var editing by remember { mutableStateOf<StoryCharacter?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<StoryCharacter?>(null) }
    var deletingRelation by remember { mutableStateOf<StoryRelation?>(null) }
    var linking by remember { mutableStateOf<StoryCharacter?>(null) }
    var centerId by remember { mutableStateOf(bible.characters.firstOrNull()?.id.orEmpty()) }
    // 3.26.44：换中心不再有「旧布局 → 新布局」的插值状态——力导向从当前坐标继续跑，
    // 节点自己走过去；点球就是直接改 centerId。
    fun pick(id: String) {
        if (id.isEmpty() || id == centerId) return
        centerId = id
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
                    message = "先记下一个名字，关系线和时间线都从这里长出来。AI 整理出来的角色在「AI」页。",
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
                        bookId = book.id,
                        bible = bible,
                        centerId = centerId,
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
                        // 3.26.46：与 AI 页共用同一张角色卡（同外观 / 间距 / 字号 / 颜色）。
                        StoryCharacterCard(
                            character = center,
                            actions = {
                                GlassChip("加关系", false, leading = null) { linking = center }
                                GlassChip("编辑", false) { editing = center }
                                GlassChip("删除", false) { deleting = center }
                            },
                        )
                        val links = bible.relationsOf(center.id)
                        if (links.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            links.forEach { r ->
                                val other = bible.otherEnd(r, center.id) ?: return@forEach
                                StoryRelationRow(
                                    title = other.name.ifBlank { "未命名" },
                                    kind = r.kind,
                                    onDelete = { deletingRelation = r },
                                )
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
            // 3.26.45：作者只能连自己的角色；AI 角色在「AI」页，同步后才出现。
            bible = book.bible.mineWith(synced),
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
 * 力导向关系图（3.26.44，用户要「Obsidian 关系图谱那套逻辑」）。
 *
 * 为什么换掉环形：环是固定槽位——角色一多，多出来的只能藏起来（旧版「还有 N 位在下面」），
 * 而且球挨着球、关系线被两头的球盖掉。力导向没有槽位：关系亲近的两球弹簧更短、所有球互相
 * 排斥、整组再被一个很轻的向心力收住，角色多了是自己铺开。
 *
 * 布局（[ForceGraph]）：
 * ① 节点斥力 ∝ 1/d²，距离夹在「两球半径和 + 2dp」上做软约束，靠得再近力也有限不会炸；
 * ② 每条关系一根弹簧，目标长度按关系类型分档（亲人/恋人最短，仇敌最长）；
 * ③ 整组一个很轻的向心力，中心角色单独用更强的一档留在画布中心；
 * ④ 迭代在帧回调里推进、每帧 2 步，**收敛即停**——没有常驻帧循环；只有拖球、换中心、
 *    增删数据才把布局重新唤醒（拖动结束后再收一次）；
 * ⑤ 初始位置由 `bookId + 角色 id` 派生固定种子，同一份数据每次打开的落点一致；
 *    每一步只取决于上一步（与帧率无关），所以收敛结果也确定。
 *
 * 手势：
 * ① 单指按在球上拖动 = 钉住这颗球（Obsidian 的拖球），其余球在模拟里让位；松手解除并重新收敛；
 * ② 单指按空白拖动 = 平移画布；双指 = 以双指中心为锚缩放（0.35–3×）；
 * ③ 单击球**立刻**换中心（不等双击超时）；双击空白复位视图；
 * ④ 换中心不清空当前坐标：模拟从当前坐标继续跑，节点是「走过去」不是闪跳。
 *
 * 每帧只画一个 Canvas（不建 per-球 composable）：坐标只在绘制阶段读，模拟推进只让绘制失效、
 * 不触发重组；50 个角色也只是几十帧 O(n²) 的小循环，收敛后完全静止。
 */
@Composable
private fun RelationGraph(
    bookId: String,
    bible: BookBible,
    centerId: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = LocalInk.current
    val density = LocalDensity.current
    val onPickState = rememberUpdatedState(onPick)

    // 视图（缩放 / 平移）：绘制阶段直接读，捏合拖动只重画、不重组。
    var viewScale by remember { mutableFloatStateOf(GRAPH_SCALE_DEFAULT) }
    var viewPan by remember { mutableStateOf(Offset.Zero) }
    val viewScope = rememberCoroutineScope()
    var viewJob by remember { mutableStateOf<Job?>(null) }

    // 布局：坐标与力都放在普通内存里（不进快照），模拟推进只靠 frameTick 让 Canvas 重画。
    val graph = remember(bookId) { ForceGraph(bookId) }
    var frameTick by remember { mutableIntStateOf(0) }
    // 唤醒布局：拖球开始 / 松手 / 点球换中心都自增，重启下面「跑到收敛就退出」的 effect。
    var wakeTick by remember { mutableIntStateOf(0) }

    // 双击复位的判定（点球不参与：点球立刻换中心）。
    var lastTapAt by remember { mutableLongStateOf(0L) }
    var lastTapPos by remember { mutableStateOf(Offset.Zero) }
    // 手势进行中不自动适配视图（不然用户在拖/捏的时候会被 autoFit 的对齐动画抢走）。
    var gestureActive by remember { mutableStateOf(false) }
    // 3.26.46：点球换中心时，选中环从球边「长出来」（spring .6/380，用户点名的同柔曲线）；
    // motion=none 直接落终态，不留半途。
    val motion = ui.settings.motion != "none"
    val selectPulse = remember { Animatable(1f) }
    LaunchedEffect(centerId) {
        if (motion) {
            selectPulse.snapTo(0f)
            selectPulse.animateTo(1f, spring(dampingRatio = .6f, stiffness = 380f))
        } else {
            selectPulse.snapTo(1f)
        }
    }

    // 缩放后图形会超出这块画布，必须裁在画布内：否则放大的球会画到下面的图例/角色卡上。
    BoxWithConstraints(modifier.clipToBounds()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val geom = remember(bible.characters.size, density) {
            graphGeometry(bible.characters.size, density)
        }

        // 数据 → 布局：改名字/颜色只更新节点内容；增删角色/关系、换中心才记一次 layoutChanged。
        // 这里写的是普通内存不是 Compose 状态，所以不违反「组合期不写状态」。
        graph.sync(
            characters = bible.characters,
            relations = bible.relations,
            centerId = centerId,
            nodeR = geom.nodeR,
            centerR = geom.centerR,
            gapPx = with(density) { 2.dp.toPx() },
        )

        fun screenToWorld(screen: Offset): Offset = Offset(
            (screen.x - cx - viewPan.x) / viewScale,
            (screen.y - cy - viewPan.y) / viewScale,
        )

        /** 屏幕坐标命中球：容差 = 球在屏幕上的半径 + 8dp，缩放后依然准。 */
        fun hitTest(screen: Offset): ForceGraph.Node? {
            val scale = viewScale
            val pan = viewPan
            val tol = with(density) { 8.dp.toPx() }
            var best: ForceGraph.Node? = null
            var bestD = Float.MAX_VALUE
            graph.nodes.forEach { n ->
                val dx = cx + n.x * scale + pan.x - screen.x
                val dy = cy + n.y * scale + pan.y - screen.y
                val d = sqrt(dx * dx + dy * dy)
                if (d < bestD) {
                    bestD = d
                    best = n
                }
            }
            val hit = best ?: return null
            return if (bestD <= hit.r * scale + tol) hit else null
        }

        /** 当前布局的世界包围盒（含球半径），[minX, minY, maxX, maxY]。 */
        fun contentBounds(): FloatArray {
            if (graph.nodes.isEmpty()) return floatArrayOf(-1f, -1f, 1f, 1f)
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            graph.nodes.forEach { n ->
                if (n.x - n.r < minX) minX = n.x - n.r
                if (n.y - n.r < minY) minY = n.y - n.r
                if (n.x + n.r > maxX) maxX = n.x + n.r
                if (n.y + n.r > maxY) maxY = n.y + n.r
            }
            return floatArrayOf(minX, minY, maxX, maxY)
        }

        /** 平移约束：整张图的包围盒至少留 25% 在视口里，拖不飞也拖不没。 */
        fun clampPan(candidate: Offset, scale: Float): Offset {
            val b = contentBounds()
            var x = candidate.x
            var y = candidate.y
            val left = cx + b[0] * scale + x
            val right = cx + b[2] * scale + x
            if (left > w * .75f) x += w * .75f - left
            if (right < w * .25f) x += w * .25f - right
            val top = cy + b[1] * scale + y
            val bottom = cy + b[3] * scale + y
            if (top > h * .75f) y += h * .75f - top
            if (bottom < h * .25f) y += h * .25f - bottom
            return Offset(x, y)
        }

        fun animateView(targetScale: Float, targetPan: Offset) {
            viewJob?.cancel()
            val s0 = viewScale
            val p0 = viewPan
            if (s0 == targetScale && p0 == targetPan) return
            viewJob = viewScope.launch {
                coroutineScope {
                    launch {
                        animate(s0, targetScale, animationSpec = spring(MOYU_DAMPING, 420f)) { v, _ ->
                            viewScale = v
                        }
                    }
                    launch {
                        animate(p0.x, targetPan.x, animationSpec = spring(MOYU_DAMPING, 420f)) { v, _ ->
                            viewPan = Offset(v, viewPan.y)
                        }
                    }
                    launch {
                        animate(p0.y, targetPan.y, animationSpec = spring(MOYU_DAMPING, 420f)) { v, _ ->
                            viewPan = Offset(viewPan.x, v)
                        }
                    }
                }
            }
        }

        /** 双击空白：弹簧回到 1× 居中。 */
        fun resetView() = animateView(GRAPH_SCALE_DEFAULT, Offset.Zero)

        /** 收敛后：新布局装不进当前视口时缩一点把整张图框进来（装得下就不抢用户的缩放）。 */
        fun autoFit() {
            val b = contentBounds()
            val pad = with(density) { 10.dp.toPx() }
            val bw = (b[2] - b[0]).coerceAtLeast(1f)
            val bh = (b[3] - b[1]).coerceAtLeast(1f)
            val fit = min((w - pad * 2f) / bw, (h - pad * 2f) / bh)
                .coerceIn(GRAPH_SCALE_MIN, GRAPH_SCALE_MAX)
            if (fit >= viewScale - .02f) return
            val bcx = (b[0] + b[2]) / 2f
            val bcy = (b[1] + b[3]) / 2f
            animateView(fit, Offset(-bcx * fit, -bcy * fit))
        }

        /**
         * 推到收敛为止：每帧 2 步。钉住期间不认「收敛」（拖多久都得让其余球让位）；
         * 收敛后直接退出循环——这也保证静止后不再有帧回调，不烧电。
         */
        suspend fun runLayout() {
            var frames = 0
            while (true) {
                var settled = false
                withFrameNanos {
                    settled = graph.step(2)
                    frameTick++
                }
                frames++
                if (settled) break
                if (frames >= GRAPH_MAX_FRAMES && graph.pinnedId == null) break
            }
        }

        LaunchedEffect(bookId, bible.characters, bible.relations, centerId, wakeTick) {
            val freshLayout = graph.takeLayoutChanged()
            runLayout()
            if (freshLayout && !gestureActive) autoFit()
        }

        val ballPaint = remember(geom.ballTextSize) {
            android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = geom.ballTextSize
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        }
        val centerBallPaint = remember(geom.centerTextSize) {
            android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = geom.centerTextSize
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        }

        // 自己写手势而不用 detectTransformGestures / detectTapGestures：那两个都没有「抬手」
        // 回调，拖球钉住没法在松手那一刻解除；而且点球要和拖球、平移、缩放放在同一条判定里。
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(w, h) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        viewJob?.cancel()
                        gestureActive = true
                        val start = down.position
                        val startNode = hitTest(start)
                        var pastSlop = false
                        var pin: ForceGraph.Node? = null
                        var grab = Offset.Zero
                        var multi = false
                        var maxMove = 0f
                        var centroid = start
                        var zoom = 1f
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size > 1) multi = true
                            var sum = Offset.Zero
                            pressed.forEach { sum += it.position - it.previousPosition }
                            val pan = sum / pressed.size.toFloat()
                            var cen = Offset.Zero
                            pressed.forEach { cen += it.position }
                            centroid = cen / pressed.size.toFloat()
                            zoom = if (pressed.size < 2) 1f else zoomOf(pressed)
                            if (!pastSlop) {
                                maxMove = maxOf(maxMove, (centroid - start).getDistance())
                                if (multi || zoom != 1f || maxMove > slop) {
                                    pastSlop = true
                                    if (!multi && zoom == 1f && startNode != null) {
                                        // 拖球：记下「球心 - 手指世界坐标」的抓取偏移，拖动不跳位。
                                        val wp = screenToWorld(centroid)
                                        pin = startNode
                                        grab = Offset(startNode.x - wp.x, startNode.y - wp.y)
                                        graph.setPinned(startNode.id)
                                        wakeTick++
                                    }
                                }
                            }
                            if (pastSlop) {
                                if (multi && pin != null) {
                                    // 拖球中途多了第二根手指：让位给缩放，球解除钉住。
                                    graph.setPinned(null)
                                    pin = null
                                    wakeTick++
                                }
                                if (pin != null) {
                                    // 钉住：球心钉在手指处，其余球在模拟里让位。
                                    val wp = screenToWorld(centroid)
                                    pin.x = wp.x + grab.x
                                    pin.y = wp.y + grab.y
                                    frameTick++
                                } else {
                                    // 视图：双指以双指中心为锚缩放 + 平移。
                                    val old = viewScale
                                    val next = (old * zoom).coerceIn(GRAPH_SCALE_MIN, GRAPH_SCALE_MAX)
                                    val factor = next / old
                                    val rel = centroid - Offset(cx, cy)
                                    viewPan = clampPan(rel - (rel - viewPan) * factor + pan, next)
                                    viewScale = next
                                }
                                event.changes.forEach { if (it.position != it.previousPosition) it.consume() }
                            }
                        }
                        if (pin != null) {
                            // 松手：解除钉住，再收一次（wakeTick 让 effect 重新跑到收敛）。
                            graph.setPinned(null)
                            wakeTick++
                        }
                        if (!pastSlop) {
                            if (startNode != null) {
                                // 单击球立刻换中心：不设双击等待。
                                lastTapAt = 0L
                                onPickState.value(startNode.id)
                            } else {
                                val now = android.os.SystemClock.uptimeMillis()
                                val near = (start - lastTapPos).getDistance() < with(density) { 40.dp.toPx() }
                                if (now - lastTapAt in 1L..GRAPH_DOUBLE_TAP_MS && near) {
                                    lastTapAt = 0L
                                    resetView()
                                } else {
                                    lastTapAt = now
                                    lastTapPos = start
                                }
                            }
                        }
                        gestureActive = false
                    }
                },
        ) {
            // 订阅模拟帧：坐标是普通内存，靠这个计数让 Canvas 重画（只失效绘制层，不重组）。
            if (frameTick < 0) return@Canvas
            val scale = viewScale
            val pan = viewPan
            val lineInset = 2.dp.toPx()
            val halo = 1.5.dp.toPx()
            withTransform({
                translate(pan.x, pan.y)
                scale(scale, scale, Offset(cx, cy))
            }) {
                fun DrawScope.edge(s: ForceGraph.Spring, strong: Boolean) {
                    val a = graph.nodes[s.a]
                    val b = graph.nodes[s.b]
                    val pa = Offset(cx + a.x, cy + a.y)
                    val pb = Offset(cx + b.x, cy + b.y)
                    val dir = unitDirection(pa, pb) ?: return
                    // 两端都用圆周交点断开（球心沿方向退 r + 2dp），线不会在球边露头。
                    val start = pa + dir * (a.r + lineInset)
                    val end = pb - dir * (b.r + lineInset)
                    if ((end - start).getDistance() <= 0f) return
                    drawLine(
                        relationTone(s.kind, ui).copy(alpha = if (strong) .9f else .28f),
                        start,
                        end,
                        strokeWidth = if (strong) 1.6.dp.toPx() else 1.2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                fun DrawScope.ball(node: ForceGraph.Node) {
                    val p = Offset(cx + node.x, cy + node.y)
                    val color = ballColor(node.char)
                    // 先铺一圈大 3dp 的纸色 halo 再画球：线与球之间留一层纸。
                    drawCircle(ui.palette.paper, radius = node.r + halo, center = p)
                    drawCircle(color, radius = node.r, center = p)
                    if (node.isCenter) {
                        // 3.26.46：选中环带弹簧长出来（半径从「贴球」扩到 +1dp，同时淡入）。
                        val sel = selectPulse.value.coerceIn(0f, 1f)
                        drawCircle(
                            ui.accent.copy(alpha = .9f * sel),
                            radius = node.r + (1.dp.toPx() + 6.dp.toPx() * (1f - sel)),
                            center = p,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                    drawCircle(
                        ui.palette.ink.copy(alpha = if (node.isCenter) .16f else .20f),
                        radius = node.r,
                        center = p,
                        style = Stroke(width = .8.dp.toPx()),
                    )
                    val paint = if (node.isCenter) centerBallPaint else ballPaint
                    paint.color = inkOnBall(color, ui.palette).toArgb()
                    paint.alpha = 255
                    drawContext.canvas.nativeCanvas.drawText(
                        ballLabel(node.char.name),
                        p.x,
                        p.y + paint.textSize * (if (node.isCenter) .35f else .36f),
                        paint,
                    )
                }

                // 边分两遍：与本中心无关的弱、中心的强；球画在最后（线从球下过，球口断开）。
                graph.springs.forEach { s ->
                    if (!graph.nodes[s.a].isCenter && !graph.nodes[s.b].isCenter) edge(s, false)
                }
                graph.springs.forEach { s ->
                    if (graph.nodes[s.a].isCenter || graph.nodes[s.b].isCenter) edge(s, true)
                }
                graph.nodes.forEach { node -> if (!node.isCenter) ball(node) }
                graph.nodes.firstOrNull { it.isCenter }?.let { ball(it) }
            }
        }
    }
}

// ───────────────────────── 角色图几何 / 力导向（3.26.44） ─────────────────────────

private const val GRAPH_SCALE_MIN = .35f
private const val GRAPH_SCALE_MAX = 3f
private const val GRAPH_SCALE_DEFAULT = 1f
private const val GRAPH_DOUBLE_TAP_MS = 320L

/** 节点斥力系数（每一步乘平均球半径，再按 1/d² 衰减）。 */
private const val GRAPH_REPULSION = .16f
/** 关系边弹簧系数：每步受力 = 系数 × 长度差（px）。 */
private const val GRAPH_SPRING = .06f
/** 整组向心力：很轻，只负责别让图飘走。 */
private const val GRAPH_CENTER_PULL = .04f
/** 中心角色单独一档：要它留在画布中心。 */
private const val GRAPH_CENTER_PULL_CENTER = .14f
/** 速度阻尼：把弹簧在平衡点附近的来回弹吃掉（不阻尼时 50 球会一直晃）。 */
private const val GRAPH_DAMPING = .8f
/** 单步位移上限（× 球半径），防止第一帧从随机位置一步飞出去。 */
private const val GRAPH_STEP_LIMIT = .22f
/** 斥力的最小间距软约束：距离小于「两球半径和 × 30%」后不再放大。 */
private const val GRAPH_MIN_CLAMP = .3f
/** 连续几步最大位移都小于这个值就算收敛（px）。 */
private const val GRAPH_SETTLE_PX = .8f
/** 收敛硬上限；拖球期间不计（拖多久都得让其余球让位）。 */
private const val GRAPH_MAX_FRAMES = 240

/**
 * 力导向布局的运行时状态（3.26.44）。
 *
 * 坐标系是世界坐标：(0, 0) = 画布中心，绘制时再叠视图的缩放/平移。
 * 所有字段都是**普通内存**而不是 Compose 快照状态——模拟每帧改坐标只靠外层 Canvas 的
 * frameTick 重画，不触发重组（50 颗球也不会引起布局/绘制节点重建）。
 */
private class ForceGraph(private val bookId: String) {
    class Node(
        var char: StoryCharacter,
        var isCenter: Boolean,
        var x: Float,
        var y: Float,
        var r: Float,
    ) {
        val id: String get() = char.id
    }

    class Spring(val a: Int, val b: Int, val rest: Float, val kind: RelationKind)

    val nodes = mutableListOf<Node>()
    val springs = mutableListOf<Spring>()
    var pinnedId: String? = null
        private set

    private val index = HashMap<String, Int>()
    private var fx = FloatArray(0)
    private var fy = FloatArray(0)
    private var vx = FloatArray(0)
    private var vy = FloatArray(0)
    private var gapPx = 0f
    private var stepLimitPx = 0f
    private var settleFrames = 0
    private var layoutChanged = true
    private var lastIds = emptyList<String>()
    private var lastRelations = emptyList<String>()
    private var lastCenter = ""

    fun setPinned(id: String?) {
        pinnedId = id
    }

    /** 取走「布局输入变了」的标记；一次布局只取一次。 */
    fun takeLayoutChanged(): Boolean {
        val changed = layoutChanged
        layoutChanged = false
        return changed
    }

    /**
     * 把圣经数据同步进布局：改名字/颜色只更新节点内容，不重排；增删角色/关系或换中心
     * 才记一次 layoutChanged，交给 LaunchedEffect 重新跑到收敛为止。
     */
    fun sync(
        characters: List<StoryCharacter>,
        relations: List<StoryRelation>,
        centerId: String,
        nodeR: Float,
        centerR: Float,
        gapPx: Float,
    ) {
        this.gapPx = gapPx
        stepLimitPx = nodeR * GRAPH_STEP_LIMIT

        // 复用已有节点对象：位置跟着 id 走，增删角色时其它球不会跳。
        val previous = HashMap<String, Node>(nodes.size)
        nodes.forEach { previous[it.id] = it }
        val rebuilt = ArrayList<Node>(characters.size)
        val count = characters.size.coerceAtLeast(1)
        // 新角色的初始位置按当前规模铺开；种子只由 bookId + 角色 id 决定，落点稳定。
        val spread = (nodeR + centerR) * (1.2f + .55f * sqrt(count.toFloat()))
        characters.forEach { c ->
            val radius = if (c.id == centerId) centerR else nodeR
            val old = previous[c.id]
            if (old != null) {
                old.char = c
                old.isCenter = c.id == centerId
                old.r = radius
                rebuilt += old
            } else if (c.id == centerId) {
                rebuilt += Node(c, true, 0f, 0f, radius)
            } else {
                val rnd = Random(seedOf(bookId, c.id))
                val angle = rnd.nextFloat() * 2f * PI.toFloat()
                val dist = spread * (.45f + .55f * rnd.nextFloat())
                rebuilt += Node(c, false, cos(angle) * dist, sin(angle) * dist, radius)
            }
        }
        nodes.clear()
        nodes.addAll(rebuilt)
        index.clear()
        nodes.forEachIndexed { i, node -> index[node.id] = i }

        val ids = nodes.map { it.id }
        val relationKey = relations.map { "${it.from}\u0000${it.to}\u0000${it.kind.ordinal}" }
        if (ids != lastIds || relationKey != lastRelations || centerId != lastCenter) {
            layoutChanged = true
            settleFrames = 0
        }
        lastIds = ids
        lastRelations = relationKey
        lastCenter = centerId

        springs.clear()
        relations.forEach { r ->
            val a = index[r.from] ?: return@forEach
            val b = index[r.to] ?: return@forEach
            if (a == b) return@forEach
            springs += Spring(a, b, (nodes[a].r + nodes[b].r) * springRestFactor(r.kind), r.kind)
        }
        val pinned = pinnedId
        if (pinned != null && index[pinned] == null) pinnedId = null
    }

    /**
     * 推进 [iterations] 步；返回「已收敛」。钉住期间永远不收敛——拖球时模拟必须活着，
     * 其余球才会一直给这颗球让位。
     */
    fun step(iterations: Int): Boolean {
        if (nodes.isEmpty()) return true
        var moved = 0f
        repeat(iterations) { moved = maxOf(moved, relax()) }
        if (pinnedId != null) {
            settleFrames = 0
            return false
        }
        settleFrames = if (moved < GRAPH_SETTLE_PX) settleFrames + 1 else 0
        return settleFrames >= 3
    }

    /**
     * 一步力导向松弛：斥力（1/d²，带最小间距软约束）+ 关系弹簧 + 向心力，合力积到速度上，
     * 速度带阻尼、单步封顶后再位移。加阻尼而不是直接按合力位移：50 个角色、几十条边时
     * 直接位移会在平衡点附近来回弹（实测收敛不了），阻尼把振荡吃掉。返回这一步最大的位移。
     */
    private fun relax(): Float {
        val n = nodes.size
        if (fx.size < n) {
            fx = FloatArray(n)
            fy = FloatArray(n)
            vx = FloatArray(n)
            vy = FloatArray(n)
        }
        for (i in 0 until n) {
            fx[i] = 0f
            fy[i] = 0f
        }
        // ① 节点斥力：1/d²，距离夹在「两球半径和 + 2dp」上（软约束），靠得再近力也有限。
        for (i in 0 until n) {
            val a = nodes[i]
            for (j in i + 1 until n) {
                val b = nodes[j]
                var dx = a.x - b.x
                var dy = a.y - b.y
                var d = sqrt(dx * dx + dy * dy)
                if (d < .01f) {
                    // 完全重合：按序号给一个固定方向分开，同一份数据每次都一样。
                    val angle = (i * 37 + j * 11) * .7f
                    dx = cos(angle)
                    dy = sin(angle)
                    d = 1f
                }
                val minD = (a.r + b.r) * 1.06f + gapPx
                // 软约束：距离再近也不再按 1/d² 无限放大（夹到最小间距的 30%），
                // 否则两个完全重合的球第一帧会把整张图炸飞。
                val dd = maxOf(d, minD * GRAPH_MIN_CLAMP)
                val mag = GRAPH_REPULSION * (a.r + b.r) * .5f * (minD / dd) * (minD / dd)
                val ux = dx / d
                val uy = dy / d
                fx[i] += ux * mag
                fy[i] += uy * mag
                fx[j] -= ux * mag
                fy[j] -= uy * mag
            }
        }
        // ② 关系边弹簧：越亲近的目标长度越短（rest 在 sync 里按类型算好）。
        for (s in springs) {
            val a = nodes[s.a]
            val b = nodes[s.b]
            var dx = b.x - a.x
            var dy = b.y - a.y
            var d = sqrt(dx * dx + dy * dy)
            if (d < .01f) {
                dx = 1f
                dy = 0f
                d = 1f
            }
            val ux = dx / d
            val uy = dy / d
            val mag = GRAPH_SPRING * (d - s.rest)
            fx[s.a] += ux * mag
            fy[s.a] += uy * mag
            fx[s.b] -= ux * mag
            fy[s.b] -= uy * mag
        }
        // ③ 向心收拢：整组很轻；中心角色单独一档（它要留在画布中心）。
        for (i in 0 until n) {
            val node = nodes[i]
            val k = if (node.isCenter) GRAPH_CENTER_PULL_CENTER else GRAPH_CENTER_PULL
            fx[i] -= node.x * k
            fy[i] -= node.y * k
        }
        // ④ 位移：合力带阻尼积到速度里，速度单步封顶；钉住的球完全不动（手指说了算）。
        var moved = 0f
        val limit = stepLimitPx
        for (i in 0 until n) {
            val node = nodes[i]
            if (node.id == pinnedId) {
                // 钉住期间清掉速度：松手后不会带着拖动时的惯性弹出去。
                vx[i] = 0f
                vy[i] = 0f
                continue
            }
            var dx = (vx[i] + fx[i]) * GRAPH_DAMPING
            var dy = (vy[i] + fy[i]) * GRAPH_DAMPING
            val len = sqrt(dx * dx + dy * dy)
            if (len > limit) {
                dx = dx / len * limit
                dy = dy / len * limit
            }
            vx[i] = dx
            vy[i] = dy
            node.x += dx
            node.y += dy
            val step = sqrt(dx * dx + dy * dy)
            if (step > moved) moved = step
        }
        return moved
    }
}

/** 关系类型 → 弹簧目标长度系数（× 两球半径和）：越亲近越短。 */
private fun springRestFactor(kind: RelationKind): Float = when (kind) {
    RelationKind.LOVER, RelationKind.FAMILY -> 1.24f
    RelationKind.FRIEND, RelationKind.ALLY -> 1.45f
    RelationKind.MENTOR -> 1.60f
    RelationKind.OTHER -> 1.78f
    RelationKind.RIVAL -> 1.95f
    RelationKind.ENEMY -> 2.12f
}

/** 固定种子的来源：bookId + 角色 id，同一本书同一批角色每次落点一致。 */
private fun seedOf(bookId: String, id: String): Int {
    var h = 1125899906842597L
    bookId.forEach { h = 31L * h + it.code }
    h = 31L * h + '|'.code
    id.forEach { h = 31L * h + it.code }
    return h.toInt()
}

/** 双指缩放比例（自己算，和 Compose 内部用的公式一致）。 */
private fun zoomOf(changes: List<PointerInputChange>): Float {
    if (changes.size < 2) return 1f
    var curX = 0f
    var curY = 0f
    var prevX = 0f
    var prevY = 0f
    changes.forEach {
        curX += it.position.x
        curY += it.position.y
        prevX += it.previousPosition.x
        prevY += it.previousPosition.y
    }
    val n = changes.size.toFloat()
    curX /= n
    curY /= n
    prevX /= n
    prevY /= n
    var curSize = 0f
    var prevSize = 0f
    changes.forEach {
        curSize += (it.position - Offset(curX, curY)).getDistance()
        prevSize += (it.previousPosition - Offset(prevX, prevY)).getDistance()
    }
    curSize /= n
    prevSize /= n
    return if (prevSize < 1f) 1f else curSize / prevSize
}

/** 角色图一帧的几何量（px）。 */
private class GraphGeometry(
    /** 旁球的半径。 */
    val nodeR: Float,
    /** 中心球半径（直径比旁球大 6dp，保留 3.26.16 的中心强调）。 */
    val centerR: Float,
    val ballTextSize: Float,
    val centerTextSize: Float,
)

/**
 * 角色图尺寸（沿用 3.26.43 的分档）：N≤6 → 48dp，7–10 → 42dp，>10 → 36dp；名字字号跟着降一档。
 * 力导向不需要环半径这类「反推」几何：位置由力决定，视野由收敛后的 autoFit 适配。
 */
private fun graphGeometry(characterCount: Int, density: Density): GraphGeometry {
    fun dp(value: Float): Float = with(density) { value.dp.toPx() }
    val tier = when {
        characterCount <= 6 -> 0
        characterCount <= 10 -> 1
        else -> 2
    }
    val diameters = floatArrayOf(48f, 42f, 36f)
    val nodeText = floatArrayOf(10f, 9f, 8f)
    val centerText = floatArrayOf(11f, 10f, 9f)
    val nodeR = dp(diameters[tier] / 2f)
    return GraphGeometry(
        nodeR = nodeR,
        centerR = nodeR + dp(6f),
        ballTextSize = dp(nodeText[tier]),
        centerTextSize = dp(centerText[tier]),
    )
}

/** 两点之间的单位向量；两点重合时没有方向可断，返回 null。 */
private fun unitDirection(from: Offset, to: Offset): Offset? {
    val delta = to - from
    val length = delta.getDistance()
    return if (length < .01f) null else delta / length
}

// ───────────────────────── 时间线 ─────────────────────────

@Composable
private fun TimelineMinePane(
    book: Book,
    onBible: (BookBible) -> Unit,
    onOpenChapter: (String) -> Unit,
    synced: Set<String>,
) {
    var editing by remember { mutableStateOf<StoryEvent?>(null) }
    var creating by remember { mutableStateOf(false) }
    // 3.26.18：卡片上直接可删（原来只有进编辑框才有「删除」，用户读作「删不掉」）。
    var deleting by remember { mutableStateOf<StoryEvent?>(null) }
    // 3.26.45：作者页只放自己记的事件；AI / 正文提取的在「AI」页（只读）。
    // 3.26.46：同步过的 AI 事件也铺在作者页（同步只打标记，不搬数据）。
    val events = book.bible.mineWith(synced).events

    // 3.26.20：时间线主轴改成「事件」——所有事件按故事时间排成一条竖轴，
    // 章名只作为卡片上的归属标签；解析不出时间的排最后，加「时间不详」分组。
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (events.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        glyph = Glyph.Clock,
                        title = "时间线还空着",
                        message = "把「哪天发生了什么」记下来，写后面的章节时就不会撞时间。AI 整理出来的事件在「AI」页。",
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

/** 事件卡：作者页可编辑可删；AI 页复用同一张卡，只读 + 顶部来源标签 / 已同步角标。 */
@Composable
private fun EventCard(
    event: StoryEvent,
    book: Book,
    chapterLabel: String,
    synced: Boolean = false,
    originTag: String? = null,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val ui = LocalInk.current
    val alpha = storySyncedAlpha(synced)
    MoyuSurface(
        Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha },
        radius = 18f,
        flat = true,
    ) {
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
                if (originTag != null) {
                    Spacer(Modifier.width(6.dp))
                    AiOriginTag(originTag)
                }
                if (synced) {
                    Spacer(Modifier.width(6.dp))
                    SyncedTag(true)
                }
                if (onDelete != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDelete),
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(Glyph.Trash, ui.palette.muted, 14.dp) }
                }
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

/**
 * 大纲页（3.26.46）。
 *
 * 用户纠正：「大纲先于章节，二者不是一一对应」。所以大纲不再只按章挂：
 * - 底部「新建大纲」可以建**章级节点**（先立大纲，以后按它「建章」）和**自由节点**
 *   （纯剧情规划，不绑章、不显示章名、不参与建章）；
 * - 「建章」只是章级节点的一个动作，不是节点存在的前提；
 * - 空态改成「还没有大纲，先写一条」，没有章节也能新建。
 */
@Composable
private fun OutlineMinePane(book: Book, onBible: (BookBible) -> Unit, synced: Set<String>) {
    val ui = LocalInk.current
    val store = LocalStore.current
    var beatTarget by remember { mutableStateOf<OutlineNode?>(null) }
    // 3.26.18：补删除入口。原来情节点与整条大纲一条都删不掉，还能造出空节点。
    var deletingBeat by remember { mutableStateOf<Pair<OutlineNode, OutlineBeat>?>(null) }
    // (node, 是否挂在某一章) → 菜单与确认框文案不同
    var menuNode by remember { mutableStateOf<Pair<OutlineNode, Boolean>?>(null) }
    var confirmNode by remember { mutableStateOf<Pair<OutlineNode, Boolean>?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newKind by remember { mutableIntStateOf(0) }
    var newTitle by remember { mutableStateOf("") }
    // 新建后等数据真的落库，再开情节点编辑器；直接开会用旧 bible 的 onSave 再插一条（重复）。
    var pendingBeatId by remember { mutableStateOf<String?>(null) }
    val bible = book.bible
    val mine = bible.mineWith(synced)
    val freeNodes = mine.outline.filter { isFreeOutlineNode(it) }
    val chapterLoose = mine.outline.filter {
        !isFreeOutlineNode(it) && book.chapters.none { c -> c.id == it.chapterId }
    }

    LaunchedEffect(bible.outline) {
        val id = pendingBeatId ?: return@LaunchedEffect
        val node = bible.outline.firstOrNull { it.id == id } ?: return@LaunchedEffect
        pendingBeatId = null
        beatTarget = node
    }

    fun addNode(title: String, free: Boolean) {
        val node = OutlineNode(
            id = if (free) FREE_OUTLINE_PREFIX + uuid() else uuid(),
            chapterId = null,
            title = title,
            origin = BIBLE_ORIGIN_AUTHOR,
        )
        onBible(book.bible.copy(outline = book.bible.outline + node))
        pendingBeatId = node.id
        ToastBus.show(if (free) "已新建自由大纲" else "已新建章级大纲")
    }

    // 3.26.19：工作台模式——列表自己滚，主按钮固定在 pane 底部。
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (mine.outline.isEmpty()) {
                EmptyState(
                    glyph = Glyph.List,
                    title = "还没有大纲，先写一条",
                    message = "大纲先于章节：先写一条章级节点，以后按它「建章」；也可以写自由节点，只做剧情规划。",
                )
            } else {
                // ① 章级节点（还没落地）：行尾「建章」——建章是它的一个动作，不是它存在的前提。
                chapterLoose.forEach { node ->
                    StoryOutlineCard(
                        title = node.title.ifBlank { "未命名大纲" },
                        beats = node.beats,
                        statusText = "未建章",
                        originTag = node.origin.takeIf { it != BIBLE_ORIGIN_AUTHOR },
                        trailing = {
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .clickable { menuNode = node to false },
                                contentAlignment = Alignment.Center,
                            ) { LineIcon(Glyph.More, ui.palette.muted, 16.dp) }
                            Spacer(Modifier.width(2.dp))
                            GlassChip("建章", false) { buildChapterFromOutline(store, book.id, node) }
                        },
                        onCardClick = { beatTarget = node },
                        onToggleBeat = { b -> onBible(toggleOutlineBeat(bible, node, b)) },
                        onDeleteBeat = { b -> deletingBeat = node to b },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                // ② 章卡：已有大纲挂在这一章上（含已同步的 AI 节点）。
                book.chapters.forEachIndexed { index, chapter ->
                    // 3.26.20：空大纲节点按「待写」显示，不再报 0/0；编辑时仍复用这条空节点，避免越堆越多。
                    // 3.26.46：已同步的 AI 节点也铺在作者页（同步只打标记），点开编辑即视为作者接手。
                    val authorNode = bible.outline.firstOrNull {
                        it.chapterId == chapter.id && it.origin == BIBLE_ORIGIN_AUTHOR
                    }
                    val anyNode = bible.outline.firstOrNull { it.chapterId == chapter.id }
                    val node = authorNode ?: anyNode
                    StoryOutlineCard(
                        title = chapter.title,
                        beats = node?.beats ?: emptyList(),
                        seqText = "%02d".format(index + 1),
                        statusText = when {
                            node == null -> "待写"
                            node.origin != BIBLE_ORIGIN_AUTHOR && node.id in synced -> "已同步"
                            node.origin != BIBLE_ORIGIN_AUTHOR -> "AI 待同步"
                            else -> "${node.beats.count { it.done }}/${node.beats.size}"
                        },
                        statusAccent = node != null,
                        onCardClick = {
                            beatTarget = node ?: OutlineNode(chapterId = chapter.id, title = chapter.title)
                        },
                        trailing = {
                            if (node != null) {
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .clickable { menuNode = node to true },
                                    contentAlignment = Alignment.Center,
                                ) { LineIcon(Glyph.More, ui.palette.muted, 16.dp) }
                            }
                            Spacer(Modifier.width(2.dp))
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        beatTarget = node ?: OutlineNode(
                                            chapterId = chapter.id,
                                            title = chapter.title,
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) { LineIcon(Glyph.Plus, ui.palette.muted, 16.dp) }
                        },
                        onToggleBeat = if (node != null) {
                            { b -> onBible(toggleOutlineBeat(bible, node, b)) }
                        } else null,
                        onDeleteBeat = if (node != null) {
                            { b -> deletingBeat = node to b }
                        } else null,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                // ③ 自由节点：纯剧情规划，不绑章、不显示章名、不参与建章。
                if (freeNodes.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Txt("自由节点", size = 11f, color = ui.palette.muted)
                    Spacer(Modifier.height(6.dp))
                    freeNodes.forEach { node ->
                        StoryOutlineCard(
                            title = node.title.ifBlank { "未命名大纲" },
                            beats = node.beats,
                            seqText = "自由",
                            onCardClick = { beatTarget = node },
                            trailing = {
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .clickable { menuNode = node to false },
                                    contentAlignment = Alignment.Center,
                                ) { LineIcon(Glyph.More, ui.palette.muted, 16.dp) }
                            },
                            onToggleBeat = { b -> onBible(toggleOutlineBeat(bible, node, b)) },
                            onDeleteBeat = { b -> deletingBeat = node to b },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("新建大纲", { creating = true }, Modifier.fillMaxWidth(), glyph = Glyph.Plus)
    }

    // 新建大纲：章级节点 / 自由节点两种。
    if (creating) {
        GlassDialog(onDismiss = { creating = false }, dismissOnOutsideClick = false) {
            DialogTitle("新建大纲")
            Spacer(Modifier.height(12.dp))
            Segmented(listOf("章级节点", "自由节点"), newKind, { newKind = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Txt(
                if (newKind == 0) "章级节点：先立一条大纲，以后可以按它「建章」；现在不绑任何章。"
                else "自由节点：只做剧情规划，不绑章、不显示章名、不参与建章。",
                size = 10.5f,
                color = ui.palette.muted,
                lineHeight = 17f,
            )
            Spacer(Modifier.height(12.dp))
            InkField(
                newTitle,
                { newTitle = it },
                if (newKind == 0) "这一章打算写什么" else "这条大纲叫什么",
                singleLine = true,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                GlassButton("取消", { creating = false }, Modifier.weight(1f))
                PrimaryButton(
                    "创建",
                    {
                        val title = newTitle.trim()
                            .ifBlank { if (newKind == 0) "新章大纲" else "自由大纲" }
                        creating = false
                        newTitle = ""
                        addNode(title, newKind == 1)
                    },
                    Modifier.weight(1f),
                )
            }
        }
    }

    // 3.26.18：情节点删除（行尾垃圾桶 → 确认）。
    deletingBeat?.let { (node, beat) ->
        val remaining = node.beats.filterNot { it.id == beat.id }
        ConfirmDialog(
            title = "删除这个情节点？",
            message = "「${beat.text}」会从这条大纲移除。",
            confirmLabel = "删除",
            onConfirm = {
                onBible(
                    bible.copy(
                        // 3.26.20：章卡删掉最后一条情节点时连空节点一起清掉，回到「待写」；
                        // 3.26.46：章级 / 自由节点是独立存在的大纲，删光情节点也保留节点本身。
                        outline = if (remaining.isEmpty() &&
                            !isFreeOutlineNode(node) && node.chapterId != null
                        ) {
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

    // 3.26.18：整条大纲的删除/清空（卡片「···」→ 菜单 → 确认，两个 Dialog 不同时出现在同一帧）。
    menuNode?.let { (node, attached) ->
        GlassDialog(onDismiss = { menuNode = null }, dismissOnOutsideClick = false) {
            DialogTitle(
                if (attached) "本章大纲" else "这条大纲",
                subtitle = book.chapters.firstOrNull { it.id == node.chapterId }?.title
                    ?: node.title.ifBlank { "未命名大纲" },
            )
            Spacer(Modifier.height(10.dp))
            if (node.beats.isNotEmpty()) {
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
            }
            SettingRow(
                "删除整条大纲",
                if (attached) "这一章回到「待写」，章节正文不动" else "这条大纲会移除，章节正文不动",
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
            title = if (clearOnly) "清空这条大纲的情节点？" else "删除这条大纲？",
            message = if (clearOnly) "情节点会全部移除；章节正文不受影响。"
            else if (node.chapterId != null) "整条大纲会移除，这一章回到「待写」；章节正文不受影响。"
            else "这条大纲会移除；章节正文不受影响。",
            confirmLabel = if (clearOnly) "清空" else "删除",
            onConfirm = {
                onBible(
                    bible.copy(
                        // 3.26.20：清空情节点 = 章卡回到「待写」，空节点不留在数据里。
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

/** 勾选 / 取消一个情节点（任何来源的节点都可以，写回后作者手改即归 author）。 */
private fun toggleOutlineBeat(bible: BookBible, node: OutlineNode, beat: OutlineBeat): BookBible =
    bible.copy(
        outline = bible.outline.map { n ->
            if (n.id != node.id) n
            else n.copy(beats = n.beats.map { if (it.id == beat.id) it.copy(done = !it.done) else it })
        },
    )

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
    // 3.26.46：章级 / 自由大纲还没落地（chapterId 为空）时标题就是它自己的，顺手可改；
    // 挂在章上的节点标题跟随章名，不在这里编辑。
    val loose = node.chapterId == null
    var nodeTitle by remember { mutableStateOf(node.title) }
    val titleDirty = loose && nodeTitle.trim() != node.title
    val chapterTitle = book.chapters.firstOrNull { it.id == node.chapterId }?.title ?: node.title
    GlassDialog(onDismiss, dismissOnOutsideClick = false) {
        DialogTitle(
            if (editingBeat == null) "第 ${node.beats.size + 1} 个情节点" else "编辑情节点",
            subtitle = if (loose) null else chapterTitle,
        )
        if (loose) {
            Spacer(Modifier.height(12.dp))
            InkField(nodeTitle, { nodeTitle = it }, "这条大纲的标题", singleLine = true)
            Spacer(Modifier.height(10.dp))
        } else {
            Spacer(Modifier.height(14.dp))
        }
        InkField(
            text,
            { text = it },
            if (editingBeat == null) {
                if (loose) "这条大纲要写的剧情" else "这一章要发生的事"
            } else {
                "改这一条情节点"
            },
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
                when {
                    editingBeat != null -> "保存修改"
                    text.isNotBlank() -> "添加"
                    else -> "保存标题"
                },
                {
                    val base = if (titleDirty) node.copy(title = nodeTitle.trim()) else node
                    val body = text.trim()
                    when {
                        editingBeat != null && body.isNotBlank() -> onSave(
                            base.copy(
                                beats = base.beats.map {
                                    if (it.id == editingBeat!!.id) it.copy(text = body) else it
                                },
                            ),
                        )
                        editingBeat == null && body.isNotBlank() ->
                            onSave(base.copy(beats = base.beats + OutlineBeat(text = body)))
                        titleDirty -> onSave(base)
                    }
                },
                Modifier.weight(1f),
                enabled = text.isNotBlank() || titleDirty,
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
    // 3.26.45：浮层与故事面板共用同一套「我的 / AI」两页（单一事实源，改一处两边都生效）。
    val pager = remember { StoryPagerState() }
    var pending by remember(book.id) { mutableStateOf(storyPendingChanges(book.id)) }
    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val store = LocalStore.current
    LaunchedEffect(book.id) {
        // 3.26.45：预热 AiBible 的无参入口，再读一次待同意（故事面板打开时可能已有侧车）。
        AiBible.host(appCtx, store)
        pending = storyPendingChanges(book.id)
    }
    LaunchedEffect(book.bible) { pending = storyPendingChanges(book.id) }
    fun resolveChange(change: StoryPendingChange, accept: Boolean) {
        storyResolveChange(book.id, change.id, accept)
        pending = pending.filterNot { it.id == change.id }
        ToastBus.show(if (accept) "已同意这一处 AI 改动" else "已拒绝这一处 AI 改动")
    }
    val motion = ui.settings.motion != "none"
    val synced = remember(book.bible, pager.syncTick) { bibleSyncedIds(book.id) }
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
                // 3.26.46：与故事面板同一套滑动（只滑不淡 + 同柔弹簧），并且铺到面板本体宽度，
                // 内容不再被 content 槽两侧的 chrome 裁成「内窗口」。
                Box(Modifier.weight(1f).fillMaxWidth().storyBleed().clipToBounds()) {
                    AnimatedContent(
                        targetState = tab,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = { storyPageTransform(motion, targetState > initialState) },
                        label = "floatStoryTab",
                    ) { index ->
                        Column(Modifier.fillMaxSize().padding(horizontal = STORY_CHROME_INSET)) {
                            when (index) {
                                0 -> CharacterTab(book, onBible, pager, pending, synced) { c, a -> resolveChange(c, a) }
                                1 -> TimelineTab(
                                    book,
                                    onBible,
                                    onOpenChapter = {},
                                    pager = pager,
                                    pending = pending,
                                    synced = synced,
                                ) { c, a -> resolveChange(c, a) }
                                else -> OutlineTab(book, onBible, pager, pending, synced) { c, a -> resolveChange(c, a) }
                            }
                        }
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

// ───────────────────────── 我的 / AI 双页 + 同意卡 + 同步（3.26.45） ─────────────────────────
//
// 数据层不加字段：`origin` 早就有（author / text / ai）。三个 tab 各自按 origin 分成两页——
// 「我的」只放 origin == "author"；「AI」放其余，只读。AI 想改作者条目要经过差异卡
// （同意 / 拒绝）；AI 写的新东西走「同步 AI 内容到我的」整批并入作者页，做完一步可撤销。

private const val BIBLE_ORIGIN_AUTHOR = "author"
private const val BIBLE_SECTION_CHARACTER = 0
private const val BIBLE_SECTION_TIMELINE = 1
private const val BIBLE_SECTION_OUTLINE = 2

/** 三个 tab 各自的「我的 / AI」页码 + 同步后的一步撤销；切 tab 不回到第一页。 */
private class StoryPagerState {
    var character by mutableIntStateOf(0)
    var timeline by mutableIntStateOf(0)
    var outline by mutableIntStateOf(0)
    /** 上次同步实际落地的处数；0 = 没有可撤销的同步（撤销条不出现）。 */
    var synced by mutableIntStateOf(0)
    /** 3.26.46：同步 / 撤销后自增，用来重读「已同步」侧车标记（bible 本身可能没变）。 */
    var syncTick by mutableIntStateOf(0)
}

/** AI 对作者条目提出的一处改动——差异卡的一条。 */
private data class StoryPendingChange(
    val id: String,
    /** [BIBLE_SECTION_CHARACTER] / [BIBLE_SECTION_TIMELINE] / [BIBLE_SECTION_OUTLINE] */
    val section: Int,
    /** 谁：角色「李四」/ 关系「李四—王五」/ 事件「雨夜」/ 大纲「第三章」 */
    val who: String,
    /** 哪个字段：身份 / 一句话 / 关系 / 故事时间 / 情节点 */
    val field: String,
    val before: String,
    val after: String,
)

/** AI 条目类型 → 中文（差异卡 / 同步预览同用）。 */
private fun bibleKindLabel(kind: String): String = when (kind) {
    "character" -> "角色"
    "relation" -> "关系"
    "event" -> "事件"
    "outline" -> "大纲"
    else -> "设定"
}

/**
 * 待同意的 AI 改动：把 AiBible 的字段级差异 [BibleChange] 映射成差异卡的一条。
 * 角色 / 关系归角色 tab，事件归时间线，大纲归大纲。
 */
private fun storyPendingChanges(bookId: String): List<StoryPendingChange> =
    pendingChanges(bookId).map { c ->
        StoryPendingChange(
            id = c.id,
            section = when (c.kind) {
                "event" -> BIBLE_SECTION_TIMELINE
                "outline" -> BIBLE_SECTION_OUTLINE
                else -> BIBLE_SECTION_CHARACTER
            },
            who = "${bibleKindLabel(c.kind)}「${c.title}」",
            field = c.field,
            before = c.before,
            after = c.after,
        )
    }

/** 同意 / 拒绝一条 AI 改动；同意才写 bible。 */
private fun storyResolveChange(bookId: String, changeId: String, accept: Boolean) {
    if (!resolveChange(changeId, accept)) ToastBus.show("这条改动已经不在了")
}

/** 我的 / AI 两页的骨架：顶部固定两段切换，内容整幅横移、只滑不淡。 */
@Composable
private fun BiblePages(
    page: Int,
    onPage: (Int) -> Unit,
    mine: @Composable () -> Unit,
    ai: @Composable () -> Unit,
) {
    val motion = LocalInk.current.settings.motion != "none"
    Column(Modifier.fillMaxSize()) {
        Segmented(listOf("我的", "AI"), page, onPage, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        // 3.26.46：滑动层铺到所在页的本体宽度，两侧 20dp 边距交给每页自己补；
        // 内容不再被「内窗口」的裁剪切掉（用户报的同框动画两侧边框遮内容）。
        Box(Modifier.weight(1f).fillMaxWidth().storyBleed().clipToBounds()) {
            AnimatedContent(
                targetState = page,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { storyPageTransform(motion, targetState > initialState) },
                label = "biblePage",
            ) { p ->
                Column(Modifier.fillMaxSize().padding(horizontal = STORY_CHROME_INSET)) {
                    if (p == 0) mine() else ai()
                }
            }
        }
    }
}

@Composable
private fun CharacterTab(
    book: Book,
    onBible: (BookBible) -> Unit,
    pager: StoryPagerState,
    pending: List<StoryPendingChange>,
    synced: Set<String>,
    resolve: (StoryPendingChange, Boolean) -> Unit,
) {
    BiblePages(
        page = pager.character,
        onPage = { pager.character = it },
        mine = { CharacterMinePane(book, onBible, synced) },
        ai = {
            CharacterAiPane(
                book = book,
                pager = pager,
                pending = pending.filter { it.section == BIBLE_SECTION_CHARACTER },
                synced = synced,
                resolve = resolve,
            )
        },
    )
}

@Composable
private fun TimelineTab(
    book: Book,
    onBible: (BookBible) -> Unit,
    onOpenChapter: (String) -> Unit,
    pager: StoryPagerState,
    pending: List<StoryPendingChange>,
    synced: Set<String>,
    resolve: (StoryPendingChange, Boolean) -> Unit,
) {
    BiblePages(
        page = pager.timeline,
        onPage = { pager.timeline = it },
        mine = { TimelineMinePane(book, onBible, onOpenChapter, synced) },
        ai = {
            TimelineAiPane(
                book = book,
                pager = pager,
                pending = pending.filter { it.section == BIBLE_SECTION_TIMELINE },
                synced = synced,
                resolve = resolve,
            )
        },
    )
}

@Composable
private fun OutlineTab(
    book: Book,
    onBible: (BookBible) -> Unit,
    pager: StoryPagerState,
    pending: List<StoryPendingChange>,
    synced: Set<String>,
    resolve: (StoryPendingChange, Boolean) -> Unit,
) {
    BiblePages(
        page = pager.outline,
        onPage = { pager.outline = it },
        mine = { OutlineMinePane(book, onBible, synced) },
        ai = {
            OutlineAiPane(
                book = book,
                pager = pager,
                pending = pending.filter { it.section == BIBLE_SECTION_OUTLINE },
                synced = synced,
                resolve = resolve,
            )
        },
    )
}

// ───────────────────────── AI 页（只读） ─────────────────────────
//
// 3.26.46：三页都复用「我的」页同一套卡片组件（同外观 / 间距 / 字号 / 颜色），
// 只是不可编辑——点开是只读详情。同步过的条目淡一档 + 右上角「已同步」，内容留在 AI 页，
// 不再像 3.26.45 那样同步完就空。

/** AI 页骨架：待同意差异卡 + 内容卡（LazyColumn 让同意/拒绝后卡片弹簧落位）+ 同步 footer。 */
@Composable
private fun AiPane(
    book: Book,
    pager: StoryPagerState,
    pending: List<StoryPendingChange>,
    resolve: (StoryPendingChange, Boolean) -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(pending, key = { it.id }) { change ->
                // 同意 / 拒绝后，剩下的卡片带弹簧上移到新位置（差异卡自己负责滑出）。
                Box(
                    Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = storyPlacementSpec(),
                        fadeOutSpec = null,
                    ),
                ) { PendingChangeCard(change, resolve) }
            }
            content()
            item { Spacer(Modifier.height(2.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        AiSyncFooter(book, pager)
    }
}

@Composable
private fun CharacterAiPane(
    book: Book,
    pager: StoryPagerState,
    pending: List<StoryPendingChange>,
    synced: Set<String>,
    resolve: (StoryPendingChange, Boolean) -> Unit,
) {
    val ui = LocalInk.current
    val bible = book.bible
    val chars = bible.characters.filter { it.origin != BIBLE_ORIGIN_AUTHOR }
    val rels = bible.relations.filter { it.origin != BIBLE_ORIGIN_AUTHOR }
    var detail by remember { mutableStateOf<StoryCharacter?>(null) }
    var relationDetail by remember { mutableStateOf<StoryRelation?>(null) }
    AiPane(book = book, pager = pager, pending = pending, resolve = resolve) {
        if (chars.isEmpty() && rels.isEmpty()) {
            item {
                EmptyState(
                    glyph = Glyph.Sparkles,
                    title = "AI 还没写进来角色",
                    message = "问书或自动整理时，AI 记下的角色会先放在这里；同步后归到「我的」，这里仍留一条「已同步」。",
                )
            }
        }
        items(chars, key = { "c_" + it.id }) { c ->
            Box(
                Modifier.animateItem(
                    fadeInSpec = null,
                    placementSpec = storyPlacementSpec(),
                    fadeOutSpec = null,
                ),
            ) {
                StoryCharacterCard(
                    character = c,
                    synced = c.id in synced,
                    originTag = c.origin,
                    onClick = { detail = c },
                )
            }
        }
        if (rels.isNotEmpty()) {
            item { Txt("关系", size = 11f, color = ui.palette.muted) }
            items(rels, key = { "r_" + it.id }) { r ->
                Box(
                    Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = storyPlacementSpec(),
                        fadeOutSpec = null,
                    ),
                ) {
                    StoryRelationRow(
                        title = relationTitle(bible, r),
                        kind = r.kind,
                        synced = r.id in synced,
                        originTag = r.origin,
                        onClick = { relationDetail = r },
                    )
                }
            }
        }
    }
    detail?.let { c ->
        StoryReadOnlySheet(
            title = c.name.ifBlank { "未命名角色" },
            subtitle = "AI 角色 · ${originLabel(c.origin)}",
            rows = listOf(
                "身份" to c.role,
                "一句话" to c.note,
                "球的颜色" to (c.color ?: "自动"),
            ),
            onDismiss = { detail = null },
        )
    }
    relationDetail?.let { r ->
        StoryReadOnlySheet(
            title = relationTitle(bible, r),
            subtitle = "AI 关系 · ${originLabel(r.origin)}",
            rows = listOf("类型" to r.kind.label),
            onDismiss = { relationDetail = null },
        )
    }
}

@Composable
private fun TimelineAiPane(
    book: Book,
    pager: StoryPagerState,
    pending: List<StoryPendingChange>,
    synced: Set<String>,
    resolve: (StoryPendingChange, Boolean) -> Unit,
) {
    // 与时间线主轴同一套排序：能解析出故事时间的按时间排，解析不出的排最后。
    val events = book.bible.events
        .filter { it.origin != BIBLE_ORIGIN_AUTHOR }
        .sortedBy { storyTimeKey(it.whenText) ?: Int.MAX_VALUE }
    var detail by remember { mutableStateOf<StoryEvent?>(null) }
    AiPane(book = book, pager = pager, pending = pending, resolve = resolve) {
        if (events.isEmpty()) {
            item {
                EmptyState(
                    glyph = Glyph.Sparkles,
                    title = "AI 还没写进来事件",
                    message = "AI 整理出来的事件会先放在这里；同步后归到「我的」，这里仍留一条「已同步」。",
                )
            }
        }
        items(events, key = { "e_" + it.id }) { e ->
            Box(
                Modifier.animateItem(
                    fadeInSpec = null,
                    placementSpec = storyPlacementSpec(),
                    fadeOutSpec = null,
                ),
            ) {
                EventCard(
                    event = e,
                    book = book,
                    chapterLabel = e.chapterIds
                        .mapNotNull { id -> book.chapters.firstOrNull { it.id == id }?.title }
                        .joinToString("、")
                        .ifBlank { "未关联" },
                    synced = e.id in synced,
                    originTag = e.origin,
                    onEdit = { detail = e },
                    onDelete = null,
                )
            }
        }
    }
    detail?.let { e ->
        StoryReadOnlySheet(
            title = e.title.ifBlank { "未命名事件" },
            subtitle = "AI 事件 · ${originLabel(e.origin)}",
            rows = listOf(
                "故事时间" to e.whenText,
                "备注" to e.note,
                "章节" to e.chapterIds.mapNotNull { id -> book.chapters.firstOrNull { it.id == id }?.title }
                    .joinToString("、"),
                "出场角色" to e.characterIds.mapNotNull { id -> book.bible.character(id)?.name }
                    .joinToString(" · "),
            ),
            onDismiss = { detail = null },
        )
    }
}

@Composable
private fun OutlineAiPane(
    book: Book,
    pager: StoryPagerState,
    pending: List<StoryPendingChange>,
    synced: Set<String>,
    resolve: (StoryPendingChange, Boolean) -> Unit,
) {
    val store = LocalStore.current
    val bible = book.bible
    val nodes = bible.outline.filter { it.origin != BIBLE_ORIGIN_AUTHOR }
    // 未落地：chapterId 为空，或指向一个已经不存在的章（对齐 AI 工具 chapters_from_outline 的口径）。
    val loose = nodes.filter { n -> book.chapters.none { it.id == n.chapterId } }
    val attached = nodes.filter { n -> book.chapters.any { it.id == n.chapterId } }
    var detail by remember { mutableStateOf<OutlineNode?>(null) }
    AiPane(book = book, pager = pager, pending = pending, resolve = resolve) {
        if (nodes.isEmpty()) {
            item {
                EmptyState(
                    glyph = Glyph.Sparkles,
                    title = "AI 还没写大纲",
                    message = "AI 整理出来的情节点会先放在这里；同步后归到「我的」，这里仍留一条「已同步」。",
                )
            }
        }
        items(loose, key = { "lo_" + it.id }) { node ->
            Box(
                Modifier.animateItem(
                    fadeInSpec = null,
                    placementSpec = storyPlacementSpec(),
                    fadeOutSpec = null,
                ),
            ) {
                // 未落地节点行尾「建章」——建完节点即挂上新章，随后出现在「我的」。
                StoryOutlineCard(
                    title = node.title.ifBlank { "未命名大纲" },
                    beats = node.beats,
                    statusText = if (node.id in synced) "已同步" else "未建章",
                    statusAccent = node.id in synced,
                    synced = node.id in synced,
                    originTag = node.origin,
                    onCardClick = { detail = node },
                    trailing = {
                        GlassChip("建章", false) { buildChapterFromOutline(store, book.id, node) }
                    },
                )
            }
        }
        items(attached, key = { "at_" + it.id }) { node ->
            val title = book.chapters.firstOrNull { it.id == node.chapterId }?.title
                ?: node.title.ifBlank { "未命名章节" }
            Box(
                Modifier.animateItem(
                    fadeInSpec = null,
                    placementSpec = storyPlacementSpec(),
                    fadeOutSpec = null,
                ),
            ) {
                StoryOutlineCard(
                    title = title,
                    beats = node.beats,
                    statusText = if (node.id in synced) "已同步" else "AI 待同步",
                    statusAccent = node.id in synced,
                    synced = node.id in synced,
                    originTag = node.origin,
                    onCardClick = { detail = node },
                )
            }
        }
    }
    detail?.let { node ->
        val title = book.chapters.firstOrNull { it.id == node.chapterId }?.title
            ?: node.title.ifBlank { "未命名大纲" }
        StoryReadOnlySheet(
            title = title,
            subtitle = "AI 大纲 · ${originLabel(node.origin)}",
            rows = listOf(
                "情节点" to node.beats.joinToString("；") { (if (it.done) "✓ " else "") + it.text },
            ),
            onDismiss = { detail = null },
        )
    }
}

/**
 * 同意机制的差异卡：谁、改哪个字段、原文 → 新文，按钮「同意 / 拒绝」。
 *
 * 3.26.45：从底部挤出、来去都有动画，弹簧用用户点名的 `spring(.6f, 380f)`。
 * 点按钮先让卡片滑出去，退场完再真的同意/拒绝（`MutableTransitionState` 空闲后再落库）。
 */
@Composable
private fun PendingChangeCard(
    change: StoryPendingChange,
    resolve: (StoryPendingChange, Boolean) -> Unit,
) {
    val ui = LocalInk.current
    val motion = ui.settings.motion != "none"
    val vt = remember { MutableTransitionState(false).apply { targetState = true } }
    var decision by remember { mutableStateOf<Boolean?>(null) }
    AnimatedVisibility(
        visibleState = vt,
        enter = if (motion) {
            slideInVertically(spring(dampingRatio = .6f, stiffness = 380f)) { it } + fadeIn(tween(140))
        } else {
            slideInVertically(tween(0)) { it } + fadeIn(tween(0))
        },
        exit = if (motion) {
            slideOutVertically(spring(dampingRatio = .6f, stiffness = 380f)) { it } + fadeOut(tween(120))
        } else {
            slideOutVertically(tween(0)) { it } + fadeOut(tween(0))
        },
    ) {
        MoyuSurface(Modifier.fillMaxWidth(), radius = 18f, flat = true) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Txt(
                        change.who,
                        size = 14f,
                        weight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    LineIcon(Glyph.Sparkles, ui.accent, 13.dp)
                    Spacer(Modifier.width(4.dp))
                    Txt("AI 想改", size = 10.5f, color = ui.accent, weight = FontWeight.Medium)
                }
                Spacer(Modifier.height(3.dp))
                Txt("改「${change.field}」", size = 10.5f, color = ui.palette.muted)
                Spacer(Modifier.height(10.dp))
                Txt("原", size = 9.5f, color = ui.palette.muted)
                Spacer(Modifier.height(2.dp))
                Txt(
                    change.before,
                    size = 12f,
                    color = ui.palette.muted,
                    lineHeight = 18f,
                    decoration = TextDecoration.LineThrough,
                )
                Spacer(Modifier.height(8.dp))
                Txt("新", size = 9.5f, color = ui.palette.muted)
                Spacer(Modifier.height(2.dp))
                Txt(change.after, size = 12.5f, lineHeight = 19f)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    GlassButton("拒绝", {
                        if (decision == null) {
                            decision = false
                            vt.targetState = false
                        }
                    }, Modifier.weight(1f))
                    PrimaryButton("同意", {
                        if (decision == null) {
                            decision = true
                            vt.targetState = false
                        }
                    }, Modifier.weight(1f))
                }
            }
        }
    }
    LaunchedEffect(vt.currentState, vt.isIdle) {
        if (!vt.currentState && vt.isIdle) {
            decision?.let { resolve(change, it) }
        }
    }
}

// ───────────────────────── 同步 AI 内容到我的 + 一步撤销 ─────────────────────────

@Composable
private fun AiSyncFooter(book: Book, pager: StoryPagerState) {
    val ui = LocalInk.current
    val motion = ui.settings.motion != "none"
    // 预览行来自 AiBible.previewMerge；非 null = 预览面板开着。
    var previewRows by remember { mutableStateOf<List<BibleChange>?>(null) }
    // 3.26.46：已同步的条目按侧车标记跳过，按钮可用性看「还没同步的 AI 条目」。
    val syncMarks = remember(book.bible, pager.syncTick) { bibleSyncedIds(book.id) }
    val total = aiBibleCount(book.bible, syncMarks)
    val synced = pager.synced
    Column(Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = synced > 0,
            enter = if (motion) {
                slideInVertically(spring(dampingRatio = .6f, stiffness = 380f)) { it } + fadeIn(tween(140))
            } else {
                slideInVertically(tween(0)) { it } + fadeIn(tween(0))
            },
            exit = if (motion) {
                slideOutVertically(spring(dampingRatio = .6f, stiffness = 380f)) { it } + fadeOut(tween(120))
            } else {
                slideOutVertically(tween(0)) { it } + fadeOut(tween(0))
            },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(SmoothCorner(14.dp))
                    .background(dockTone(ui))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt("已同步 $synced 条到「我的」", size = 11f, color = ui.palette.muted)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .bouncy {
                            // 3.26.45：撤销走 AiBible 的 merge journal（取最近一次同步的 before 写回）。
                            // 3.26.46：同时清掉这一批「已同步」标记，AI 页的淡一档一起收回去。
                            if (undoLastMerge(book.id)) {
                                pager.synced = 0
                                pager.syncTick++
                                ToastBus.show("已撤销同步")
                            } else {
                                ToastBus.show("没有可撤销的同步")
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) { Txt("撤销", size = 11.5f, color = ui.accent, weight = FontWeight.Medium) }
            }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            "同步 AI 内容到我的",
            {
                // 先出预览再确认：预览里列的是字段级差异，确认后一次 applyMerge 落地。
                val rows = previewMerge(book.id)
                if (rows.isEmpty()) ToastBus.show("没有可同步的 AI 内容") else previewRows = rows
            },
            Modifier.fillMaxWidth(),
            glyph = Glyph.Sparkles,
            enabled = total > 0,
        )
    }
    previewRows?.let { rows ->
        SyncPreviewDialog(
            rows = rows,
            onDismiss = { previewRows = null },
            onConfirm = {
                previewRows = null
                val n = applyMerge(book.id, rows.map { it.id })
                if (n > 0) {
                    pager.synced = n
                    pager.syncTick++
                    ToastBus.show("已同步 $n 条到「我的」")
                } else {
                    ToastBus.show("没有可同步的 AI 内容")
                }
            },
        )
    }
}

/** 同步前先出预览：字段级差异逐条列出来（谁 · 哪个字段 · 原文 → 新文），确认才落地。 */
@Composable
private fun SyncPreviewDialog(rows: List<BibleChange>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val ui = LocalInk.current
    GlassDialog(onDismiss, dismissOnOutsideClick = false) {
        DialogTitle(
            "同步 AI 内容到我的",
            subtitle = "共 ${rows.size} 处差异；同步后归到「我的」，AI 页保留并标「已同步」",
        )
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            rows.forEach { c ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Txt(
                        "${bibleKindLabel(c.kind)}「${c.title}」· ${c.field}",
                        size = 12f,
                        weight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Txt(
                        "${c.before} → ${c.after}",
                        size = 10.5f,
                        color = ui.palette.muted,
                        lineHeight = 16f,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Txt(
            "只把字段并进「我的」，不删 AI 条目；同步完可以一步撤销。",
            size = 10.5f,
            color = ui.palette.muted,
            lineHeight = 17f,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassButton("取消", onDismiss, Modifier.weight(1f))
            PrimaryButton("同步", onConfirm, Modifier.weight(1f))
        }
    }
}

// ───────────────────────── 共用卡片 / 滑动骨架 / 「我的」页数据（3.26.46） ─────────────────────────

/** 面板 content 槽左右各 20dp 的 chrome（Design.kt 的 InlineModalSheet / ModalSheet 里的 padding）。 */
private val STORY_CHROME_INSET = 20.dp

/** 自由大纲节点的 id 前缀：OutlineNode 没有类型字段，这里用 id 前缀区分「不绑章的自由节点」。 */
private const val FREE_OUTLINE_PREFIX = "free_"

private fun isFreeOutlineNode(n: OutlineNode): Boolean =
    n.chapterId == null && n.id.startsWith(FREE_OUTLINE_PREFIX)

/**
 * 让滑动内容铺到面板本体，而不是被 content 槽两侧 20dp 的 chrome 裁在里面。
 *
 * 3.26.46 用户报「边框/内窗口把内容挡住」：面板本身没有描边，真正的原因是
 * InlineModalSheet 的 content Column 左右各 20dp padding（Design.kt 2035-2043），
 * AnimatedContent 在这个槽里滑动，内容在槽边被裁掉，那 20dp 纸色 chrome 读作两道竖框。
 * 这里反向多量 2×inset、再左移 inset；每个页面自己补回 inset 边距，静止布局不变。
 */
private fun Modifier.storyBleed(inset: Dp = STORY_CHROME_INSET): Modifier = this.layout { measurable, constraints ->
    val px = inset.roundToPx()
    val wide = if (constraints.hasBoundedWidth) {
        constraints.copy(
            minWidth = constraints.minWidth + px * 2,
            maxWidth = constraints.maxWidth + px * 2,
        )
    } else {
        constraints
    }
    val placeable = measurable.measure(wide)
    val width = (placeable.width - px * 2).coerceAtLeast(0)
    layout(width, placeable.height) {
        placeable.place(if (constraints.hasBoundedWidth) -px else 0, 0)
    }
}

/** 同框滑动：只滑不淡、同柔曲线；motion=none 时 tween(0) 直接落终态（协议 §5）。 */
private fun storyPageTransform(motion: Boolean, forward: Boolean): ContentTransform {
    val dir = if (forward) 1 else -1
    val spec: FiniteAnimationSpec<IntOffset> =
        if (motion) spring(dampingRatio = .6f, stiffness = 380f) else tween(0)
    return slideInHorizontally(spec) { it * dir } togetherWith slideOutHorizontally(spec) { -it * dir }
}

/** LazyColumn 里卡片落位的弹簧（同意 / 拒绝后剩下的卡片被推上来）。 */
@Composable
private fun storyPlacementSpec(): FiniteAnimationSpec<IntOffset> {
    val motion = LocalInk.current.settings.motion != "none"
    return if (motion) spring(dampingRatio = .6f, stiffness = 380f) else tween(0)
}

/** 已同步：整张卡片淡一档（.55）；同步 / 撤销都走同一根弹簧，不硬切。 */
@Composable
private fun storySyncedAlpha(synced: Boolean): Float {
    val motion = LocalInk.current.settings.motion != "none"
    val alpha by animateFloatAsState(
        targetValue = if (synced) .55f else 1f,
        animationSpec = if (motion) spring(dampingRatio = .6f, stiffness = 380f) else tween(0),
        label = "storySyncedAlpha",
    )
    return alpha
}

/** 卡片右上角「已同步」角标：淡入淡出 + 轻微缩放。 */
@Composable
private fun SyncedTag(visible: Boolean) {
    val ui = LocalInk.current
    val motion = ui.settings.motion != "none"
    AnimatedVisibility(
        visible = visible,
        enter = if (motion) {
            fadeIn(tween(160)) +
                scaleIn(initialScale = .82f, animationSpec = spring(dampingRatio = .6f, stiffness = 380f))
        } else {
            fadeIn(tween(0))
        },
        exit = if (motion) {
            fadeOut(tween(120)) +
                scaleOut(targetScale = .9f, animationSpec = spring(dampingRatio = .6f, stiffness = 380f))
        } else {
            fadeOut(tween(0))
        },
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(accentTint(ui.palette, ui.accent))
                .padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineIcon(Glyph.Check, ui.accent, 9.dp, 1.4f)
            Spacer(Modifier.width(3.dp))
            Txt("已同步", size = 9.5f, color = ui.accent, weight = FontWeight.Medium)
        }
    }
}

/** AI 内容来源：ai = 问书/工具写入，text = 章节保存后自动整理提取。 */
private fun originLabel(origin: String): String = when (origin) {
    "text" -> "正文提取"
    "ai" -> "AI 整理"
    else -> if (origin.isBlank()) "AI" else origin
}

/** AI 内容来源小标签（卡片右上角，与作者页卡片只差这一枚小字 + 已同步角标）。 */
@Composable
private fun AiOriginTag(origin: String) {
    Txt(originLabel(origin), size = 9.5f, color = LocalInk.current.palette.muted.copy(alpha = .8f))
}

/**
 * 角色卡：作者页的「中心角色」与 AI 页的角色列表共用同一套外观
 * （20dp 圆角 / 16dp 内边距 / 18f 名字 / 11f 身份 / 11.5f 一句话）。
 */
@Composable
private fun StoryCharacterCard(
    character: StoryCharacter,
    synced: Boolean = false,
    originTag: String? = null,
    onClick: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val ui = LocalInk.current
    val alpha = storySyncedAlpha(synced)
    MoyuSurface(
        Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha },
        radius = 20f,
        flat = true,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.bouncy(scale = .98f) { onClick() } else Modifier)
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(ballColor(character), CircleShape))
                Spacer(Modifier.width(8.dp))
                Txt(character.name.ifBlank { "未命名" }, size = 18f, weight = FontWeight.SemiBold)
                if (character.role.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Txt(character.role, size = 11f, color = ui.palette.muted)
                }
                Spacer(Modifier.weight(1f))
                if (originTag != null) {
                    AiOriginTag(originTag)
                    Spacer(Modifier.width(6.dp))
                }
                SyncedTag(synced)
            }
            if (character.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Txt(character.note, size = 11.5f, color = ui.palette.muted, lineHeight = 18f)
            }
            if (actions != null) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            }
        }
    }
}

/** 关系行：作者页带「删除」，AI 页只读（点开详情）+ 来源标签 / 已同步角标。 */
@Composable
private fun StoryRelationRow(
    title: String,
    kind: RelationKind,
    synced: Boolean = false,
    originTag: String? = null,
    onClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val ui = LocalInk.current
    val alpha = storySyncedAlpha(synced)
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .then(if (onClick != null) Modifier.bouncy(scale = .98f) { onClick() } else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(relationTone(kind, ui), CircleShape))
        Spacer(Modifier.width(10.dp))
        Txt(title, size = 13f)
        Spacer(Modifier.width(8.dp))
        Txt(kind.label, size = 11f, color = ui.palette.muted)
        Spacer(Modifier.weight(1f))
        if (originTag != null) {
            AiOriginTag(originTag)
            Spacer(Modifier.width(6.dp))
        }
        SyncedTag(synced)
        if (onDelete != null) {
            Spacer(Modifier.width(6.dp))
            Txt(
                "删除",
                size = 11f,
                color = ui.palette.muted,
                // 3.26.18：关系删除也要确认（原来点一下立即删，误触无法恢复）。
                modifier = Modifier.clickable(onClick = onDelete),
            )
        }
    }
}

private fun relationTitle(bible: BookBible, r: StoryRelation): String {
    val a = bible.character(r.from)?.name?.ifBlank { "未命名" } ?: "已删除角色"
    val b = bible.character(r.to)?.name?.ifBlank { "未命名" } ?: "已删除角色"
    return "$a — $b"
}

/**
 * 大纲卡：作者页的三种节点（章级未落地 / 章卡 / 自由节点）与 AI 页共用同一套外观
 * （18dp 圆角 / 14dp 内边距 / 15f 标题 / 10f 序号 / 12f 情节点）。
 */
@Composable
private fun StoryOutlineCard(
    title: String,
    beats: List<OutlineBeat>,
    seqText: String? = null,
    statusText: String? = null,
    statusAccent: Boolean = false,
    synced: Boolean = false,
    originTag: String? = null,
    onCardClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onToggleBeat: ((OutlineBeat) -> Unit)? = null,
    onDeleteBeat: ((OutlineBeat) -> Unit)? = null,
) {
    val ui = LocalInk.current
    val alpha = storySyncedAlpha(synced)
    MoyuSurface(
        Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha },
        radius = 18f,
        flat = true,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (onCardClick != null) Modifier.bouncy(scale = .99f) { onCardClick() } else Modifier)
                .padding(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (seqText != null) {
                    Txt(seqText, size = 10f, color = ui.palette.muted, letterSpacing = .8f)
                    Spacer(Modifier.width(10.dp))
                }
                Txt(
                    title,
                    size = 15f,
                    weight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f, fill = false))
                if (statusText != null) {
                    Txt(
                        statusText,
                        size = 10.5f,
                        color = if (statusAccent) ui.accent else ui.palette.muted,
                    )
                }
                if (originTag != null) {
                    Spacer(Modifier.width(6.dp))
                    AiOriginTag(originTag)
                }
                if (synced) {
                    Spacer(Modifier.width(6.dp))
                    SyncedTag(true)
                }
                if (trailing != null) {
                    Spacer(Modifier.width(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
                }
            }
            if (beats.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                beats.forEach { b ->
                    StoryBeatRow(beat = b, onToggle = onToggleBeat, onDelete = onDeleteBeat)
                }
            }
        }
    }
}

/** 情节点行：勾选走全局 SelectMark（已带选中动画）；只读时整行不响应点击。 */
@Composable
private fun StoryBeatRow(
    beat: OutlineBeat,
    onToggle: ((OutlineBeat) -> Unit)? = null,
    onDelete: ((OutlineBeat) -> Unit)? = null,
) {
    val ui = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable { onToggle(beat) } else Modifier)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectMark(
            checked = beat.done,
            onToggle = { onToggle?.invoke(beat) },
            size = 13.dp,
            corner = 4.dp,
            interactive = false,
        )
        Spacer(Modifier.width(10.dp))
        Txt(
            beat.text,
            size = 12f,
            color = if (beat.done) ui.palette.muted else ui.palette.ink,
            lineHeight = 18f,
        )
        Spacer(Modifier.weight(1f))
        if (onDelete != null) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable { onDelete(beat) },
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(Glyph.Trash, ui.palette.muted.copy(alpha = .75f), 13.dp)
            }
        }
    }
}

/** AI 页点开卡片：只读详情（GlassDialog 的进出沿用全局动画）。 */
@Composable
private fun StoryReadOnlySheet(
    title: String,
    subtitle: String?,
    rows: List<Pair<String, String>>,
    onDismiss: () -> Unit,
) {
    val ui = LocalInk.current
    GlassDialog(onDismiss) {
        DialogTitle(title, subtitle = subtitle)
        Spacer(Modifier.height(12.dp))
        rows.forEach { (label, value) ->
            Txt(label, size = 10f, color = ui.palette.muted)
            Spacer(Modifier.height(3.dp))
            Txt(value.ifBlank { "（空）" }, size = 13f, lineHeight = 20f)
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(6.dp))
        GlassButton("关闭", onDismiss, Modifier.fillMaxWidth())
    }
}

/**
 * 「我的」页数据（3.26.46）：作者自己的条目 + 已同步的 AI 条目。
 *
 * 同步只打标记、不搬数据，所以「我的」页要按标记把同步过的 AI 条目也铺出来；
 * 有同名 / 同标题 / 同章作者条目的 AI 条目不重复铺（内容已经并过去了），
 * 关系两端按同名角色换成作者 id，避免图里出现两个同名球。
 */
private fun BookBible.mineWith(synced: Set<String>): BookBible {
    val authors = characters.filter { it.origin == BIBLE_ORIGIN_AUTHOR }
    if (synced.isEmpty()) {
        val ids = authors.map { it.id }.toHashSet()
        return copy(
            characters = authors,
            relations = relations.filter { it.origin == BIBLE_ORIGIN_AUTHOR && it.from in ids && it.to in ids },
            events = events.filter { it.origin == BIBLE_ORIGIN_AUTHOR },
            outline = outline.filter { it.origin == BIBLE_ORIGIN_AUTHOR },
        )
    }
    val authorByName = authors.filter { it.name.isNotBlank() }.associateBy { it.name }
    val idMap = HashMap<String, String>()
    characters.forEach { c ->
        when {
            c.origin == BIBLE_ORIGIN_AUTHOR -> idMap[c.id] = c.id
            c.id in synced -> idMap[c.id] = authorByName[c.name]?.id ?: c.id
        }
    }
    val mineChars = characters.filter { c ->
        c.origin == BIBLE_ORIGIN_AUTHOR || (c.id in synced && idMap[c.id] == c.id)
    }
    val ids = mineChars.map { it.id }.toHashSet()
    val rels = ArrayList<StoryRelation>()
    val seenPairs = HashSet<String>()
    relations.filter { it.origin == BIBLE_ORIGIN_AUTHOR || it.id in synced }
        .sortedBy { if (it.origin == BIBLE_ORIGIN_AUTHOR) 0 else 1 }
        .forEach { r ->
            val from = idMap[r.from] ?: return@forEach
            val to = idMap[r.to] ?: return@forEach
            if (from !in ids || to !in ids || from == to) return@forEach
            val pair = if (from < to) "$from\u0000$to" else "$to\u0000$from"
            if (seenPairs.add(pair)) rels += r.copy(from = from, to = to)
        }
    val authorEventTitles = events.filter { it.origin == BIBLE_ORIGIN_AUTHOR }
        .map { it.title.trim() }
        .filter { it.isNotBlank() }
        .toHashSet()
    val mineEvents = events.filter { e ->
        e.origin == BIBLE_ORIGIN_AUTHOR ||
            (e.id in synced && (e.title.isBlank() || e.title.trim() !in authorEventTitles))
    }.map { e ->
        if (e.origin == BIBLE_ORIGIN_AUTHOR) e
        else e.copy(characterIds = e.characterIds.mapNotNull { idMap[it] }.distinct())
    }
    fun outlineKey(n: OutlineNode): String = n.chapterId?.let { "c:$it" } ?: ("t:" + n.title.trim())
    val authorOutlineKeys = outline.filter { it.origin == BIBLE_ORIGIN_AUTHOR }.map { outlineKey(it) }.toHashSet()
    val mineOutline = outline.filter { n ->
        n.origin == BIBLE_ORIGIN_AUTHOR || (n.id in synced && outlineKey(n) !in authorOutlineKeys)
    }
    return copy(
        characters = mineChars,
        relations = rels,
        events = mineEvents,
        outline = mineOutline,
    )
}

/** 未同步的 AI 条目数（同步按钮的可用性；已同步的按标记跳过，重复点没意义）。 */
private fun aiBibleCount(bible: BookBible, synced: Set<String>): Int =
    bible.characters.count { it.origin != BIBLE_ORIGIN_AUTHOR && it.id !in synced } +
        bible.relations.count { it.origin != BIBLE_ORIGIN_AUTHOR && it.id !in synced } +
        bible.events.count { it.origin != BIBLE_ORIGIN_AUTHOR && it.id !in synced } +
        bible.outline.count { it.origin != BIBLE_ORIGIN_AUTHOR && it.id !in synced }

// ───────────────────────── 按大纲建章（3.26.45） ─────────────────────────

/**
 * 按大纲建章：把一个未落地节点变成一章（标题用节点标题），再把 node.chapterId 指回新章。
 *
 * 与 AI 工具 `chapters_from_outline` 的落地逻辑同源（那里在 AiTab.applyQueue 的
 * `AiPendingKind.ChapterFromOutline` 分支），但那边是 composable 里的 local function、
 * 本文件调不到，所以这里用同一对 store 写入口复刻：updateBook 加章 + updateBible 回填 chapterId。
 */
private fun buildChapterFromOutline(store: AppStore, bookId: String, node: OutlineNode) {
    val book = store.book(bookId) ?: return
    val chapter = Chapter(
        title = node.title.ifBlank { chapterFallbackTitle(book.chapters.size) },
        content = "",
    )
    store.updateBook(bookId) { it.copy(chapters = it.chapters + chapter) }
    val after = store.book(bookId) ?: return
    store.updateBible(
        bookId,
        after.bible.copy(
            outline = after.bible.outline.map {
                if (it.id == node.id) it.copy(chapterId = chapter.id) else it
            },
        ),
    )
    ToastBus.show("已建章「${chapter.title}」")
}
