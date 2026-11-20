package com.moyu.writer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 故事圣经（3.26.16；3.26.47 信息架构反转；3.26.48 双页对齐；3.26.64 移除 AI 页）。
 *
 * 3.26.64：故事面板里不再有任何 AI 内容——**来源切换（我的 / AI）、待同意抽屉入口、已同步标记、
 * 同步预览 / 一步撤销**全部删除。面板只铺**作者自己的条目**（`origin == author`），
 * 角色 / 时间线 / 大纲三个视图各只有一个 composable（[CharacterPane] / [TimelinePane] /
 * [OutlinePane]），全部可编辑；页面骨架 [StoryPageScaffold] 管滚动区 + 底部主按钮。
 *
 * 入口与触发方式（每页只留底部一个主操作，其余进「···」）：
 * - 点「角色 / 时间线 / 大纲」：切视图，内容整幅横移（[storyPageTransform]，spring(.6, 380)；
 *   motion = none 时 tween(0) 直接落位）。
 * - 点底部主按钮：新建角色 / 新建事件 / 新建大纲。
 * - 点卡片：打开对应的编辑器。
 * - 点卡片右上「···」：这张卡自己的次操作（建章 / 加关系 / 编辑 / 删除 / 清空情节点）。
 * - 点标题右侧「···」：面板级次操作（按大纲建章）。
 * - 点 X / 系统返回：关面板。
 * - 长按：不设入口。滑动：面板内容纵向滚；角色图里单指平移、双指缩放、双击空白复位
 *   （可拖球钉住、点球换中心）。
 */
@Composable
fun StorySheet(
    book: Book,
    onBible: (BookBible) -> Unit,
    onDismiss: () -> Unit,
) {
    // 导航只剩「视图」一层（角色 / 时间线 / 大纲）；默认停在大纲（写作前规划台）。
    val pager = remember { StoryPagerState() }

    val ui = LocalInk.current
    // 3.26.56：「按大纲建章」——面板级次操作，把还没落地的章级节点一次建成空章节（预览勾选后才建）。
    var buildingChapters by remember { mutableStateOf(false) }
    var headerMenu by remember { mutableStateOf(false) }

    // 3.26.47：bleedContent = true —— 内容槽不再加两侧 20dp 的「内窗口」留白，横移能滑到面板本体边缘；
    // 每页自己用 SHEET_CONTENT_INSET 补回静止边距（3.26.46 的 storyBleed() 反向 padding 在这条路径退休）。
    InlineModalSheet(
        onDismiss,
        tone = ui.palette.paper,
        imeAware = false,
        dismissOnOutsideClick = false,
        fillHeight = true,
        bleedContent = true,
        pinnedContent = { close ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Txt(
                    "故事",
                    modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                    size = 23f,
                    weight = FontWeight.SemiBold,
                    letterSpacing = .3f,
                )
                // 「···」常驻：面板级次操作（按大纲建章）在这里。
                GlassIconButton(Glyph.More, { headerMenu = true }, tint = ui.palette.muted)
                Spacer(Modifier.width(2.dp))
                GlassIconButton(Glyph.Close, close, tint = ui.palette.muted)
            }
            Spacer(Modifier.height(12.dp))
            // 视图切换（见 [StoryNavTabs]）。
            StoryNavTabs(pager)
            Spacer(Modifier.height(4.dp))
        },
    ) { _ ->
        StoryNavBody(
            book = book,
            onBible = onBible,
            pager = pager,
            bleed = true,
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (headerMenu) {
        // 「按大纲建章」只在大纲视图出现：它是这一页的次操作，不是全面板动作。
        val buildable = outlineNodesToBuild(book).size
        val actions = buildList {
            if (pager.view == STORY_VIEW_OUTLINE) {
                add(
                    StoryMenuAction(
                        label = "按大纲建章",
                        subtitle = if (buildable == 0) {
                            "章级大纲都已经建章了；自由大纲不参与建章"
                        } else {
                            "还有 $buildable 条章级大纲没落地，预览勾选后一次建好"
                        },
                        glyph = Glyph.Book,
                        onClick = { buildingChapters = true },
                    ),
                )
            }
        }
        StoryMenuDialog(
            title = "故事",
            subtitle = null,
            actions = actions,
            onDismiss = { headerMenu = false },
        )
    }
    if (buildingChapters) {
        BuildChaptersFromOutlineDialog(book = book, onDismiss = { buildingChapters = false })
    }
}

// ───────────────────────── 角色 + 关系 ─────────────────────────

/**
 * 角色页（3.26.64：只铺作者自己的角色 / 关系，全部可编辑）。
 *
 * 数据 = `origin == author` 的角色与关系；卡片点开编辑、卡片「···」有次操作，
 * 底部主按钮 = 新建角色；关系图可拖球钉住。
 *
 * 入口与触发方式：
 * - 点关系图里的球 / 点角色名 chip：换中心角色。
 * - 按住球拖动：钉住这颗球，其余球让位；松手解除后重新收敛（Obsidian 式拖球）。
 * - 单指按空白拖动：平移画布；双指：以双指中心为锚缩放 0.35–3×；双击空白：弹簧复位到 1×。
 * - 点角色卡「···」：加关系 / 编辑 / 删除。点关系行「···」：删除这条关系（确认后才删）。
 * - 点底部主按钮：新建角色。
 * - 长按：不设入口（会和拖球手势打架）。滑动：本页纵向滚。
 */
@Composable
private fun CharacterPane(
    book: Book,
    onBible: (BookBible) -> Unit,
) {
    // 3.26.64：故事面板只铺作者自己的条目（AI 写进来的不再出现在界面里）。
    val bible = book.bible.mineWith(emptySet())
    var editing by remember { mutableStateOf<StoryCharacter?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<StoryCharacter?>(null) }
    var deletingRelation by remember { mutableStateOf<StoryRelation?>(null) }
    var linking by remember { mutableStateOf<StoryCharacter?>(null) }
    // 3.26.47：卡片次操作全部收进「···」菜单（旧版是卡上并排三枚 chip，和底部主按钮抢注意力）。
    var charMenu by remember { mutableStateOf(false) }
    var relationMenu by remember { mutableStateOf<StoryRelation?>(null) }
    // 3.26.49：刚保存的关系行用同一条 spring(.6f, 380f) 长出来；保存 700ms 后清掉标记。
    var freshRelationId by remember(book.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(freshRelationId) {
        if (freshRelationId != null) {
            delay(700)
            freshRelationId = null
        }
    }
    var centerId by remember(book.id) { mutableStateOf("") }
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
    StoryPageScaffold(
        primaryLabel = "新建角色",
        primaryGlyph = Glyph.Plus,
        onPrimary = { creating = true },
    ) { graphH ->
        if (bible.characters.isEmpty()) {
            emptyPage(
                glyph = Glyph.Note,
                title = "还没有角色",
                message = "先记下一个名字，关系线和时间线都从这里长出来。",
            )
        } else {
            item {
                Column(Modifier.fillMaxWidth()) {
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
                        StoryCharacterCard(
                            character = center,
                            onMore = { charMenu = true },
                        )
                        val links = bible.relationsOf(center.id)
                        if (links.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            links.forEach { r ->
                                val other = bible.otherEnd(r, center.id) ?: return@forEach
                                StoryRelationRow(
                                    title = other.name.ifBlank { "未命名" },
                                    relation = r,
                                    fresh = r.id == freshRelationId,
                                    onMore = { relationMenu = r },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    // 中心角色卡的「···」：加关系 / 编辑 / 删除（每页只留底部一个主操作，这些次操作不再铺在卡上）。
    center?.let { c ->
        if (charMenu) {
            StoryMenuDialog(
                title = c.name.ifBlank { "未命名" },
                subtitle = "角色 · ${bible.relationsOf(c.id).size} 条关系",
                actions = listOf(
                    StoryMenuAction("加一条关系", "和另一个角色连线；同一对只留一条", Glyph.Link) { linking = c },
                    StoryMenuAction("编辑角色", "名字 / 身份 / 阵营 / 性格外貌 / 一句话 / 球的颜色", Glyph.Edit) { editing = c },
                    StoryMenuAction("删除角色", "和 TA 相关的关系会一起删掉", Glyph.Trash) { deleting = c },
                ),
                onDismiss = { charMenu = false },
            )
        }
    }
    relationMenu?.let { r ->
        StoryMenuDialog(
            title = "这条关系",
            subtitle = relationTitle(bible, r),
            actions = listOf(
                StoryMenuAction("删除这条关系", "两个角色都保留，只移除这条线", Glyph.Trash) { deletingRelation = r },
            ),
            onDismiss = { relationMenu = null },
        )
    }

    if (creating || editing != null) {
        CharacterEditor(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { name, role, traits, faction, note, color ->
                val b = book.bible
                if (editing == null) {
                    // 3.26.59：手动新建也要带上性格外貌 / 阵营（空串 = 没填，与 role / note 同口径）。
                    val c = StoryCharacter(
                        name = name,
                        role = role,
                        traits = traits,
                        faction = faction,
                        note = note,
                        color = color,
                    )
                    onBible(b.copy(characters = b.characters + c))
                    pick(c.id)
                } else {
                    onBible(
                        b.copy(
                            characters = b.characters.map {
                                if (it.id == editing!!.id) {
                                    it.copy(
                                        name = name,
                                        role = role,
                                        traits = traits,
                                        faction = faction,
                                        note = note,
                                        color = color,
                                    )
                                } else {
                                    it
                                }
                            },
                        ),
                    )
                }
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
            message = "「$a」和「$b」之间的「${r.kindText}」关系会被移除，两个角色都保留。",
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
            bible = book.bible.mineWith(emptySet()),
            from = from,
            onDismiss = { linking = null },
            onSave = { to, kind, kindLabel, kindColor, kindTier ->
                val b = book.bible
                // 同一对角色只留一条关系：再连一次就是改类型（含自定义名 / 颜色 / 档位）。
                val existing = b.relations.firstOrNull {
                    (it.from == from.id && it.to == to) || (it.from == to && it.to == from.id)
                }
                val next = if (existing != null) {
                    existing.copy(kind = kind, kindLabel = kindLabel, kindColor = kindColor, kindTier = kindTier)
                } else {
                    StoryRelation(
                        from = from.id,
                        to = to,
                        kind = kind,
                        kindLabel = kindLabel,
                        kindColor = kindColor,
                        kindTier = kindTier,
                    )
                }
                onBible(
                    if (existing != null) {
                        b.copy(relations = b.relations.map { if (it.id == existing.id) next else it })
                    } else {
                        b.copy(relations = b.relations + next)
                    },
                )
                // 3.26.49：新行 / 改过的行落库后用 spring(.6f, 380f) 长出来。
                freshRelationId = next.id
            },
        )
    }
}

/**
 * 3.26.50：关系档位三档的显示名（下标 = 档位 0 / 1 / 2）。
 *
 * 自定义类型在 [RelationEditor] 里显式选一档（存 [StoryRelation.kindTier]）；内置类型 / 旧数据
 * 没有这个字段，按 [StoryRelation.tier] 从 `kind` 派生——旧数据的外观与 3.26.49 完全一致。
 */
private val RELATION_TIER_LABELS = listOf("亲近", "对立", "其他")

/**
 * 关系颜色只给三档：亲近 / 对立 / 其他。八种类型靠文字区分，不靠八种颜色。
 *
 * 3.26.50：入参从 `kind` 换成**生效档位** [StoryRelation.tier]——自定义类型选了「亲近」，
 * 关系行圆点 / 图上关系线 / 图例就都和亲人 / 恋人 / 朋友同属一档（按档位归组）。
 */
private fun relationTone(tier: Int, ui: InkUi): Color = when (tier) {
    0 -> lerp(ui.palette.muted, ui.accent, .55f)
    1 -> lerp(ui.palette.muted, Color(0xFFB4543C), .62f)
    else -> ui.palette.line
}

/**
 * 3.26.49：一条关系的颜色 = 作者自定义色优先，没设色（或存的是坏值）按生效档位取色。
 * 内置类型永远没有 kindColor，所以它们的外观与 3.26.48 完全一致。
 */
private fun relationColor(tier: Int, kindColor: String?, ui: InkUi): Color =
    kindColor?.let { raw -> runCatching { Color(android.graphics.Color.parseColor(raw)) }.getOrNull() }
        ?: relationTone(tier, ui)

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
        RELATION_TIER_LABELS.forEachIndexed { tier, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(relationTone(tier, ui), CircleShape))
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
                        relationColor(s.tier, s.kindColor, ui).copy(alpha = if (strong) .9f else .28f),
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

    // 3.26.49：每条边带上自定义颜色（null = 按档位取色），绘制阶段直接算。
    // 3.26.50：档位改用 `StoryRelation.tier`（显式 kindTier 优先，缺省按 kind 派生）。
    class Spring(val a: Int, val b: Int, val rest: Float, val tier: Int, val kindColor: String?)

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
        // 3.26.50：档位也进 key——自定义关系改档位会改弹簧长度，得重新收敛；
        // 内置类型 / 旧数据的 tier 由 kind 派生，key 与旧版等价，不会多触发一次布局。
        val relationKey = relations.map { "${it.from}\u0000${it.to}\u0000${it.kind.ordinal}\u0000${it.tier}" }
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
            springs += Spring(a, b, (nodes[a].r + nodes[b].r) * springRestFactor(r), r.tier, r.kindColor)
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

/**
 * 关系 → 弹簧目标长度系数（× 两球半径和）：越亲近越短。
 *
 * 3.26.50：自定义类型没有自己的内置档位（底档固定 OTHER），显式选了档位就按该档的代表值取长度；
 * 内置类型 / 旧数据（kindTier = null）**仍按原来的 6 档**算，旧数据布局与 3.26.49 逐像素一致。
 */
private fun springRestFactor(relation: StoryRelation): Float = if (relation.kindTier != null) {
    when (relation.tier) {
        0 -> 1.45f // 亲近：取「朋友」一档
        1 -> 1.95f // 对立：取「对手」一档
        else -> 1.78f // 其他：与底档 OTHER 相同
    }
} else {
    when (relation.kind) {
        RelationKind.LOVER, RelationKind.FAMILY -> 1.24f
        RelationKind.FRIEND, RelationKind.ALLY -> 1.45f
        RelationKind.MENTOR -> 1.60f
        RelationKind.OTHER -> 1.78f
        RelationKind.RIVAL -> 1.95f
        RelationKind.ENEMY -> 2.12f
    }
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

/**
 * 时间线页（3.26.64：只铺作者自己记的事件，全部可编辑）。
 *
 * 一根竖轴 [TimelineAxis]、一套事件卡 [EventCard]、同一份卡片间距与空态位置。
 *
 * 入口与触发方式：
 * - 点事件卡：打开事件编辑器（故事时间 / 发生了什么 / 章归属 / 出场角色）。
 * - 点事件卡右上「···」：菜单里只有「删除这个事件」，确认后才删。
 * - 点底部主按钮：新建事件。
 * - 长按：不设入口。滑动：时间线纵向滚；轴是一条连续的竖线，事件按故事时间挂在轴上。
 */
@Composable
private fun TimelinePane(
    book: Book,
    onBible: (BookBible) -> Unit,
) {
    var editing by remember { mutableStateOf<StoryEvent?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<StoryEvent?>(null) }
    // 3.26.47：删除收进卡片「···」菜单（旧版卡头常驻一个垃圾桶）。
    var menuEvent by remember { mutableStateOf<StoryEvent?>(null) }
    // 3.26.64：故事面板只铺作者自己的条目（AI 写进来的不再出现在界面里）。
    val events = book.bible.mineWith(emptySet()).events

    // 3.26.19：工作台模式——上半区自己滚，主按钮固定在 pane 底部。
    StoryPageScaffold(
        primaryLabel = "新建事件",
        primaryGlyph = Glyph.Plus,
        onPrimary = { creating = true },
    ) { _ ->
        if (events.isEmpty()) {
            emptyPage(
                glyph = Glyph.Clock,
                title = "时间线还空着",
                message = "把「哪天发生了什么」记下来，写后面的章节时就不会撞时间。",
            )
        } else {
            item {
                Column(Modifier.fillMaxWidth()) {
                    TimelineAxis(
                        events = events,
                        book = book,
                        onOpen = { editing = it },
                        onMore = { e -> menuEvent = e },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    menuEvent?.let { e ->
        StoryMenuDialog(
            title = e.title.ifBlank { "未命名事件" },
            subtitle = e.whenText.ifBlank { "未标时间" },
            actions = listOf(
                StoryMenuAction("删除这个事件", "从时间线移除，正文不受影响", Glyph.Trash) { deleting = e },
            ),
            onDismiss = { menuEvent = null },
        )
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
            },
            onDelete = { e ->
                onBible(book.bible.copy(events = book.bible.events.filterNot { it.id == e.id }))
            },
        )
    }
}

/**
 * 时间线主轴：竖线 + 刻度点 + 事件挂在轴上。
 *
 * 能解析出故事时间的按时间升序，解析不出的收进「时间不详」分组放最后（3.26.20 规约）。
 */
@Composable
private fun TimelineAxis(
    events: List<StoryEvent>,
    book: Book,
    onOpen: (StoryEvent) -> Unit,
    onMore: ((StoryEvent) -> Unit)? = null,
) {
    val known = events
        .mapNotNull { e -> storyTimeKey(e.whenText)?.let { key -> key to e } }
        .sortedWith(compareBy({ it.first }))
        .map { it.second }
    val unknown = events.filter { storyTimeKey(it.whenText) == null }
    val total = known.size + unknown.size + if (unknown.isEmpty()) 0 else 1
    var drawn = 0
    known.forEach { e ->
        TimelineEventRow(
            event = e,
            book = book,
            isLast = drawn == total - 1,
            onOpen = { onOpen(e) },
            onMore = onMore?.let { more -> { more(e) } },
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
                onOpen = { onOpen(e) },
                onMore = onMore?.let { more -> { more(e) } },
            )
            drawn++
        }
    }
}

// 3.26.20：时间轴上的一条事件——左边竖线 + 事件点，右边事件卡。
@Composable
private fun TimelineEventRow(
    event: StoryEvent,
    book: Book,
    isLast: Boolean,
    onOpen: (StoryEvent) -> Unit,
    onMore: (() -> Unit)? = null,
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
                onOpen = { onOpen(event) },
                onMore = onMore,
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

/**
 * 事件卡：点卡编辑、点「···」弹删除菜单。
 */
@Composable
private fun EventCard(
    event: StoryEvent,
    book: Book,
    chapterLabel: String,
    onOpen: () -> Unit,
    onMore: (() -> Unit)? = null,
) {
    val ui = LocalInk.current
    MoyuSurface(
        Modifier.fillMaxWidth(),
        radius = 18f,
        flat = true,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
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
                if (onMore != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onMore),
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(Glyph.More, ui.palette.muted, 15.dp) }
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
 * 大纲页（3.26.64：只铺作者自己的大纲，全部可编辑）。
 *
 * 用户拍板「大纲两种卡」：
 * - **章级卡**：卡头是章名（已建章带 01/02 序号，未落地带「章」图标 + 状态），下面一条分隔线再列情节点；
 *   未落地时「建章」在卡右上「···」菜单里，已建章的卡没有「建章」。
 * - **自由卡**：没有卡头（无章名 / 序号 / 状态），标题直接领起情节点，左侧一条细竖线；
 *   没有「建章」，只做剧情规划。
 * 情节点两种卡都是**行内列表**（沿用 [StoryBeatRow]），不是卡；两种卡的区别靠结构
 * （卡头 + 分隔线 vs 标题直领 + 竖线），不靠卡内小字标签。
 *
 * 入口与触发方式：
 * - 点卡：打开情节点编辑器（章级卡编辑这一章 / 这条计划；自由卡编辑这条自由大纲）。
 * - 点卡右上「···」：次操作菜单（建章 / 清空情节点 / 删除整条大纲）。
 * - 点底部主按钮：新建大纲（弹窗里选章级节点 / 自由节点）。
 * - 长按：不设入口。滑动：列表纵向滚。
 */
@Composable
private fun OutlinePane(
    book: Book,
    onBible: (BookBible) -> Unit,
) {
    val ui = LocalInk.current
    val store = LocalStore.current
    var beatTarget by remember { mutableStateOf<OutlineNode?>(null) }
    // 3.26.18：补删除入口。原来情节点与整条大纲一条都删不掉，还能造出空节点。
    var deletingBeat by remember { mutableStateOf<Pair<OutlineNode, OutlineBeat>?>(null) }
    // 3.26.47：卡片「···」菜单的目标 + 它是哪一种卡（章级未落地 / 已建章 / 自由），
    // 决定菜单里有没有「建章」。
    var menuNode by remember { mutableStateOf<Pair<OutlineNode, OutlineCardKind>?>(null) }
    var confirmNode by remember { mutableStateOf<Pair<OutlineNode, Boolean>?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newKind by remember { mutableIntStateOf(0) }
    var newTitle by remember { mutableStateOf("") }
    // 新建后等数据真的落库，再开情节点编辑器；直接开会用旧 bible 的 onSave 再插一条（重复）。
    var pendingBeatId by remember { mutableStateOf<String?>(null) }
    val bible = book.bible
    // 3.26.64：故事面板只铺作者自己的条目（AI 写进来的不再出现在界面里）。
    val mine = bible.mineWith(emptySet())
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
    StoryPageScaffold(
        primaryLabel = "新建大纲",
        primaryGlyph = Glyph.Plus,
        onPrimary = { creating = true },
    ) { _ ->
        if (mine.outline.isEmpty()) {
            emptyPage(
                glyph = Glyph.List,
                title = "还没有大纲，先写一条",
                message = "大纲先于章节：先立一条章级大纲，以后按它「建章」；也可以写自由大纲，只做剧情规划。",
            )
        } else {
            item {
                Column(Modifier.fillMaxWidth()) {
                    // ① 章级卡（还没落地到章）：卡头是章名，右上「···」里有「建章」。
                    chapterLoose.forEach { node ->
                        ChapterOutlineCard(
                            seq = null,
                            chapterTitle = node.title.ifBlank { "未命名大纲" },
                            beats = node.beats,
                            statusText = "未建章",
                            onCardClick = { beatTarget = node },
                            onMore = { menuNode = node to OutlineCardKind.ChapterPlanned },
                            onToggleBeat = { b -> onBible(toggleOutlineBeat(bible, node, b)) },
                            onDeleteBeat = { b -> deletingBeat = node to b },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    // ② 章级卡（已建章）：章名用章节标题，序号 01/02…；还没有节点的章铺一张「待写」卡。
                    book.chapters.forEachIndexed { index, chapter ->
                        // 3.26.20：空大纲节点按「待写」显示，不再报 0/0；编辑时仍复用这条空节点，避免越堆越多。
                        val node = bible.outline.firstOrNull { it.chapterId == chapter.id }
                        val plainTarget = OutlineNode(chapterId = chapter.id, title = chapter.title)
                        ChapterOutlineCard(
                            seq = index + 1,
                            chapterTitle = chapter.title,
                            beats = node?.beats ?: emptyList(),
                            statusText = if (node == null) "待写" else "${node.beats.count { it.done }}/${node.beats.size}",
                            statusAccent = node != null,
                            onCardClick = { beatTarget = node ?: plainTarget },
                            onMore = if (node == null) {
                                null
                            } else {
                                { menuNode = node to OutlineCardKind.ChapterAttached }
                            },
                            onToggleBeat = if (node != null) {
                                { b -> onBible(toggleOutlineBeat(bible, node, b)) }
                            } else {
                                null
                            },
                            onDeleteBeat = if (node != null) {
                                { b -> deletingBeat = node to b }
                            } else {
                                null
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    // ③ 自由卡：没有章名、序号与「建章」，标题直接领起情节点（左侧竖线是结构记号）。
                    if (freeNodes.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Txt("自由大纲", size = 11f, color = ui.palette.muted)
                        Spacer(Modifier.height(6.dp))
                        freeNodes.forEach { node ->
                            FreeOutlineCard(
                                title = node.title.ifBlank { "未命名大纲" },
                                beats = node.beats,
                                onCardClick = { beatTarget = node },
                                onMore = { menuNode = node to OutlineCardKind.Free },
                                onToggleBeat = { b -> onBible(toggleOutlineBeat(bible, node, b)) },
                                onDeleteBeat = { b -> deletingBeat = node to b },
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    // 新建大纲：章级节点 / 自由节点两种。
    if (creating) {
        GlassDialog(onDismiss = { creating = false }, dismissOnOutsideClick = false) {
            // 3.26.48：内容里的按钮一律走 close（见 [storyClose] 的 TODO）。
            val close = storyClose { creating = false }
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
                GlassButton("取消", close, Modifier.weight(1f))
                PrimaryButton(
                    "创建",
                    {
                        val title = newTitle.trim()
                            .ifBlank { if (newKind == 0) "新章大纲" else "自由大纲" }
                        newTitle = ""
                        addNode(title, newKind == 1)
                        close()
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

    // 3.26.47：整条大纲的次操作（建章 / 清空情节点 / 删除）统一收到卡片「···」菜单里；
    // 动作先关菜单、再开目标 Dialog，两个 Dialog 不同时出现在同一帧（3.26.9 的老规矩）。
    menuNode?.let { (node, kind) ->
        val actions = ArrayList<StoryMenuAction>()
        if (kind == OutlineCardKind.ChapterPlanned) {
            actions += StoryMenuAction(
                label = "建章",
                subtitle = "按这条大纲新建一章，之后再填正文",
                glyph = Glyph.Book,
            ) { buildChapterFromOutline(store, book.id, node) }
        }
        if (node.beats.isNotEmpty()) {
            actions += StoryMenuAction(
                label = "清空情节点",
                subtitle = "${node.beats.size} 个情节点会移除，章节正文不动",
                glyph = Glyph.Trash,
            ) { confirmNode = node to true }
        }
        actions += StoryMenuAction(
            label = "删除整条大纲",
            subtitle = if (kind == OutlineCardKind.ChapterAttached) {
                "这一章回到「待写」，章节正文不动"
            } else {
                "这条大纲会移除，章节正文不动"
            },
            glyph = Glyph.Trash,
        ) { confirmNode = node to false }
        StoryMenuDialog(
            title = when (kind) {
                OutlineCardKind.ChapterAttached -> "本章大纲"
                OutlineCardKind.Free -> "这条自由大纲"
                OutlineCardKind.ChapterPlanned -> "这条大纲"
            },
            subtitle = book.chapters.firstOrNull { it.id == node.chapterId }?.title
                ?: node.title.ifBlank { "未命名大纲" },
            actions = actions,
            onDismiss = { menuNode = null },
        )
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
                        // 3.26.47：删整条 = 直接移除；清空情节点时——已建章的章卡回到「待写」
                        // （空节点不留），章级计划卡 / 自由卡保留节点本身。
                        outline = if (!clearOnly) {
                            bible.outline.filterNot { it.id == node.id }
                        } else if (node.chapterId != null && !isFreeOutlineNode(node)) {
                            bible.outline.filterNot { it.id == node.id }
                        } else {
                            bible.outline.map { n ->
                                if (n.id != node.id) n else n.copy(beats = emptyList())
                            }
                        },
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

/**
 * 角色编辑器（新建 / 编辑共用）。3.26.59 在「身份」之后、「备注（一句话）」之前加两格：
 * - 阵营所属：[StoryCharacter.faction]，单行，门派 / 组织 / 势力；
 * - 性格外貌：[StoryCharacter.traits]，多行，性格 / 长相 / 口癖。
 * 两格都沿用同一个 [InkField]（不引入新控件类型）；空串 = 没填，与 role / note 同口径。
 * 保存回调顺序：名字、身份、阵营、性格外貌、一句话、球的颜色（null = 自动）。
 */
@Composable
private fun CharacterEditor(
    initial: StoryCharacter?,
    onDismiss: () -> Unit,
    onSave: (name: String, role: String, traits: String, faction: String, note: String, color: String?) -> Unit,
) {
    val ui = LocalInk.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var role by remember { mutableStateOf(initial?.role ?: "") }
    var traits by remember { mutableStateOf(initial?.traits ?: "") }
    var faction by remember { mutableStateOf(initial?.faction ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    // 3.26.24：null = 自动色，按角色 id 稳定取色板。
    var color by remember { mutableStateOf(initial?.color) }
    GlassDialog(onDismiss, dismissOnOutsideClick = false) {
        // 3.26.48：内容里的按钮一律走 close（见 [storyClose] 的 TODO）。
        val close = storyClose(onDismiss)
        DialogTitle(if (initial == null) "新建角色" else "编辑角色")
        Spacer(Modifier.height(14.dp))
        InkField(name, { name = it }, "名字")
        Spacer(Modifier.height(10.dp))
        InkField(role, { role = it }, "身份：主角 / 配角 / 反派（可空）")
        Spacer(Modifier.height(10.dp))
        // 3.26.59：阵营所属（单行）放在身份之后；性格外貌（多行）紧跟其后，备注仍在最后。
        InkField(faction, { faction = it }, "阵营所属：门派 / 组织 / 势力（可空）")
        Spacer(Modifier.height(10.dp))
        InkField(
            traits,
            { traits = it },
            "性格外貌：性格、长相、说话习惯（可空）",
            singleLine = false,
            minHeight = 76.dp,
        )
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
            GlassButton("取消", close, Modifier.weight(1f))
            PrimaryButton(
                "保存",
                {
                    onSave(name.trim(), role.trim(), traits.trim(), faction.trim(), note.trim(), color)
                    close()
                },
                Modifier.weight(1f),
                enabled = name.isNotBlank(),
            )
        }
    }
}

/**
 * 关系编辑器（3.26.16；3.26.49 支持自定义类型；3.26.50 自定义类型可选档位）。
 *
 * 「类型」= 8 个内置 chip + 一个「自定义」。选自定义时下面用 spring(dampingRatio = .6f,
 * stiffness = 380f) 展开名字输入 + **档位** + 颜色（复用角色球那套 ColorDot / HueSlider）：
 * - 名字：存 [StoryRelation.kindLabel]，关系行、图上提示、AI 读圣经都用它；
 * - 档位：存 [StoryRelation.kindTier]（0 亲近 / 1 对立 / 2 其他），关系行圆点、图上关系线
 *   与图例按它归组；默认「其他」= 3.26.49 自定义固定底档的旧行为；
 * - 颜色：存 [StoryRelation.kindColor]；选「自动」= null，按生效档位取色；
 * - 底档枚举：自定义仍固定落在 [RelationKind.OTHER]（枚举不扩，旧版本读 kind 不会读坏）。
 * 保存回调带 5 个值（对象、底档、显示名、颜色、档位），调用方原样写进 bible。
 */
@Composable
private fun RelationEditor(
    bible: BookBible,
    from: StoryCharacter,
    onDismiss: () -> Unit,
    onSave: (String, RelationKind, String, String?, Int?) -> Unit,
) {
    val ui = LocalInk.current
    val motion = ui.settings.motion != "none"
    val others = bible.characters.filter { it.id != from.id }
    var target by remember { mutableStateOf(others.firstOrNull()?.id) }
    var kind by remember { mutableStateOf(RelationKind.FRIEND) }
    // 3.26.49：自定义类型（名字 + 颜色）。custom = false 时内置 chip 的选择照旧。
    var custom by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var customColor by remember { mutableStateOf<String?>(null) }
    // 3.26.50：自定义类型的档位。默认「其他」——与 3.26.49「自定义固定落其他档」一致，
    // 不改档位的人看到的外观与上一版完全相同。
    var customTier by remember { mutableStateOf(RelationKind.OTHER.tier) }
    GlassDialog(onDismiss, dismissOnOutsideClick = false) {
        // 3.26.48：内容里的按钮一律走 close（见 [storyClose] 的 TODO）。
        val close = storyClose(onDismiss)
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
                    row.forEach { k ->
                        GlassChip(k.label, !custom && k == kind) {
                            custom = false
                            kind = k
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip("自定义", custom) { custom = true }
            }
        }
        // 3.26.49：自定义区块「长出来」用项目同柔曲线 spring(.6f, 380f)；motion = none 直接落位。
        AnimatedVisibility(
            visible = custom,
            enter = if (motion) {
                expandVertically(spring(dampingRatio = .6f, stiffness = 380f)) +
                    fadeIn(spring(dampingRatio = .6f, stiffness = 380f))
            } else {
                expandVertically(tween(0)) + fadeIn(tween(0))
            },
            exit = if (motion) {
                shrinkVertically(spring(dampingRatio = .6f, stiffness = 380f)) +
                    fadeOut(spring(dampingRatio = .6f, stiffness = 380f))
            } else {
                shrinkVertically(tween(0)) + fadeOut(tween(0))
            },
        ) {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(10.dp))
                InkField(customName, { customName = capRelationLabel(it) }, "关系名：结义兄弟 / 主仆 / 宿敌")
                Spacer(Modifier.height(12.dp))
                Txt("归到哪一档", size = 11f, color = ui.palette.muted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RELATION_TIER_LABELS.forEachIndexed { tier, label ->
                        GlassChip(label, customTier == tier) { customTier = tier }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Txt("颜色", size = 11f, color = ui.palette.muted)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlassChip("自动", customColor == null) { customColor = null }
                    accentChoices.forEach { (_, value) ->
                        val hex = "#%06X".format(value and 0xFFFFFF)
                        ColorDot(Color(value), customColor.equals(hex, true)) { customColor = hex }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val hsv = remember(customColor) {
                    FloatArray(3).also { out ->
                        val argb = customColor?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
                            ?: Color(0xFF7A5B3A).toArgb()
                        android.graphics.Color.colorToHSV(argb, out)
                    }
                }
                HueSlider(hsv[0]) { hue ->
                    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, .50f, .58f))
                    customColor = String.format("#%06X", argb and 0xFFFFFF)
                }
                Spacer(Modifier.height(8.dp))
                Txt(
                    "档位决定关系行圆点、图上关系线和图例的归组；选了颜色，就用这个颜色盖过档位色。",
                    size = 10f,
                    color = ui.palette.muted,
                    lineHeight = 16f,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassButton("取消", close, Modifier.weight(1f))
            PrimaryButton(
                "保存",
                {
                    target?.let { to ->
                        if (custom) {
                            onSave(to, RelationKind.OTHER, customName.trim(), customColor, customTier)
                        } else {
                            onSave(to, kind, kind.label, null, null)
                        }
                    }
                    close()
                },
                Modifier.weight(1f),
                enabled = target != null && (!custom || customName.isNotBlank()),
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
        // 3.26.48：内容里的按钮一律走 close（见 [storyClose] 的 TODO）。
        val close = storyClose(onDismiss)
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
                GlassButton(
                    "删除",
                    {
                        onDelete(initial)
                        close()
                    },
                    Modifier.weight(1f),
                )
            } else {
                GlassButton("取消", close, Modifier.weight(1f))
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
                    close()
                },
                Modifier.weight(1f),
                enabled = title.isNotBlank() || whenText.isNotBlank(),
            )
        }
        // 3.26.21：底部抽屉多选——事件可同时发生在多章。
        // 3.26.48：「完成」与头部 X 都走抽屉自己的 close（先播退场、再回调）。
        if (pickerOpen) {
            ModalSheet(
                onDismiss = { pickerOpen = false },
                closeableContent = { sheetClose ->
                    DialogTitle(
                        "发生在哪一章",
                        subtitle = "可多选；都不选表示不关联",
                        onClose = sheetClose,
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
                    PrimaryButton("完成", sheetClose, Modifier.fillMaxWidth())
                },
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
        // 3.26.48：内容里的按钮一律走 close（见 [storyClose] 的 TODO）。
        val close = storyClose(onDismiss)
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
            GlassButton("取消", close, Modifier.weight(1f))
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
                    close()
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
 * 3.26.23：编辑页能力栏里的悬浮故事圣经（与故事面板同一套 pane，单一事实源，可原地编辑）。
 * 3.26.64：与故事面板同步摘掉 AI 内容——只剩「角色 / 时间线 / 大纲」三视图，
 * 标题栏不再有「AI 改动记录 / 撤销最近一次 AI 改动」。
 *
 * 入口与触发方式：
 * - 点「角色 / 时间线 / 大纲」：切页，内容整幅横移（与故事面板同一套 spring）。
 * - 点卡片 / 卡片「···」：与故事面板完全同一套（编辑 / 次操作菜单）。
 * - 点 X：关浮层。
 * - 滑动：面板内容纵向滚；角色图同故事面板手势。
 *
 * TODO(bleedContent): ModalSheet 还没有 bleedContent（3.26.47 只有 InlineModalSheet 有），
 * 悬浮故事页暂时保留 [storyBleed] 反向多量；Design.kt 给 ModalSheet 补上后删掉这层、
 * 改成 bleedContent = true，并只留每页自己的 [SHEET_CONTENT_INSET]。
 */
@Composable
internal fun StoryBibleFloatSheet(
    book: Book,
    onBible: (BookBible) -> Unit,
    onDismiss: () -> Unit,
) {
    val ui = LocalInk.current
    val pager = remember { StoryPagerState() }
    val sheetH = (LocalConfiguration.current.screenHeightDp * .60f).dp
    // 3.26.48：右上 X 走面板自己的 close()——先播退场、idle 后才回调 onDismiss。
    // 旧版直接调 onDismiss，宿主 `if(flag)` 当帧就把面板卸掉，退场被跳过（Design.kt 3.26.47 已给
    // ModalSheet 补上 closeableContent）。
    ModalSheet(
        onDismiss = onDismiss,
        imeAware = false,
        closeableContent = { close ->
            Column(Modifier.fillMaxWidth().height(sheetH)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Txt("故事", size = 20f, weight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).clickable { close() },
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(Glyph.Close, ui.palette.muted, 15.dp) }
                }
                Spacer(Modifier.height(10.dp))
                // 内容层与故事面板共用 [StoryNavBody]。
                StoryNavTabs(pager)
                Spacer(Modifier.height(8.dp))
                StoryNavBody(
                    book = book,
                    onBible = onBible,
                    pager = pager,
                    bleed = false,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        },
    )
}

// ───────────── 视图导航（3.26.45；3.26.47 层级反转；3.26.64 只剩视图） ─────────────
//
// 数据层不加字段：`origin` 早就有（author / text / ai）。故事面板只铺 origin == "author" 的条目
// （[BookBible.mineWith] 传空集 = 软隐藏旧 AI 条目；数据本身第二波再删）。

private const val BIBLE_ORIGIN_AUTHOR = "author"

/** 视图：0 = 角色，1 = 时间线，2 = 大纲。 */
private const val STORY_VIEW_CHARACTER = 0
private const val STORY_VIEW_TIMELINE = 1
private const val STORY_VIEW_OUTLINE = 2

/** 故事面板的导航状态：只剩「视图」一层（我的 / AI 来源切换已在 3.26.64 删除）。 */
private class StoryPagerState {
    /** 视图：角色 / 时间线 / 大纲。3.26.19：默认停在大纲（写作前规划台）。 */
    var view by mutableIntStateOf(STORY_VIEW_OUTLINE)
}

/** 导航位置：视图（角色 / 时间线 / 大纲）。 */
private data class StoryNav(val view: Int)

/**
 * 工作台导航：只剩「角色 / 时间线 / 大纲」一行。
 *
 * 入口与触发方式：点这一行切视图；内容横移由 [StoryNavBody] 统一处理，导航条本身固定、不跟手横滑。
 */
@Composable
private fun StoryNavTabs(pager: StoryPagerState) {
    Segmented(listOf("角色", "时间线", "大纲"), pager.view, { pager.view = it }, Modifier.fillMaxWidth())
}

/** 视图切换的方向：往右切就右进左出。只滑不淡；motion = none 时 [storyPageTransform] 里是 tween(0)。 */
private fun storyNavTransform(
    motion: Boolean,
    initial: StoryNav,
    target: StoryNav,
): ContentTransform = storyPageTransform(motion, target.view > initial.view)

/**
 * 工作台内容层：视图 → 页。整幅横移、只滑不淡，页自己补静止边距。
 *
 * 入口与触发方式（「点 / 长按 / 滑动各管什么」的唯一清单）：
 * - 点导航行：切视图，见 [StoryNavTabs]。
 * - 点卡片 / 卡片「···」：编辑 / 卡片次操作（各 pane 自己的 KDoc 写明）。
 * - 点底部主按钮：每页唯一主操作；次操作不铺在页面上，只在卡片「···」菜单里。
 * - 长按：不设入口。滑动：纵向滚内容；角色图内单指平移 / 双指缩放 / 双击复位。
 *
 * [bleed]：true = 所在 [InlineModalSheet] 已传 `bleedContent = true`，内容槽已是面板全宽，
 * 这里只裁切、不再反向多量；false = 悬浮故事页的 [ModalSheet]（还没有 bleedContent），
 * 先保留 [storyBleed] 反向 padding（见 [StoryBibleFloatSheet] 的 TODO）。
 */
@Composable
private fun StoryNavBody(
    book: Book,
    onBible: (BookBible) -> Unit,
    pager: StoryPagerState,
    bleed: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = LocalInk.current.settings.motion != "none"
    val nav = StoryNav(pager.view)
    val layer = if (bleed) modifier.clipToBounds() else modifier.storyBleed().clipToBounds()
    Box(layer) {
        AnimatedContent(
            targetState = nav,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { storyNavTransform(motion, initialState, targetState) },
            label = "storyNav",
        ) { state ->
            // 页自己补回 content 槽的 20dp 静止边距：bleedContent 生效时槽里已经没有这段留白。
            Column(Modifier.fillMaxSize().padding(horizontal = SHEET_CONTENT_INSET)) {
                when (state.view) {
                    STORY_VIEW_CHARACTER -> CharacterPane(book = book, onBible = onBible)
                    STORY_VIEW_TIMELINE -> TimelinePane(book = book, onBible = onBible)
                    else -> OutlinePane(book = book, onBible = onBible)
                }
            }
        }
    }
}

// ───────────────────── 三个视图共用的页面骨架（3.26.48） ─────────────────────

/** LazyColumn 里的一整屏空态：可视区正中（不是列表顶部）。 */
private fun LazyListScope.emptyPage(glyph: Glyph, title: String, message: String) {
    item {
        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(glyph = glyph, title = title, message = message)
        }
    }
}

/**
 * 三个视图共用的页面骨架。
 *
 * 结构两层，任何一页都不许自己再拼：
 * ① 可滚动内容区（[BoxWithConstraints] 给关系图算高度，「30% 夹 150–240dp」；
 *    关系图是该列表的**第一个 item**，所以永远在首屏）；
 * ② 底部主按钮（每页唯一主操作）。
 */
@Composable
private fun StoryPageScaffold(
    primaryLabel: String,
    primaryGlyph: Glyph,
    onPrimary: () -> Unit,
    content: LazyListScope.(graphHeight: Dp) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val graphH = (maxHeight * .30f).coerceIn(150.dp, 240.dp)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content(graphH)
            }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(primaryLabel, onPrimary, Modifier.fillMaxWidth(), glyph = primaryGlyph)
    }
}

// ───────────────────────── 共用卡片 / 「···」菜单 / 滑动骨架 / 作者条目过滤 ─────────────────────────
//
// 3.26.47：按钮收敛——每页只留底部一个主操作；卡片上的次操作（建章 / 加关系 / 编辑 / 删除 /
// 清空情节点）统一收进卡片右上「···」菜单（[StoryMenuDialog]），页面上不再摆第二份。

/**
 * 一个「···」菜单项：标题 + 后果说明 + 图标。
 * [enabled] = false 时 [SettingRow] 不响应点击（例如全书整理正在跑）。
 */
private data class StoryMenuAction(
    val label: String,
    val subtitle: String? = null,
    val glyph: Glyph? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * 故事面板所有「···」菜单共用的一张对话框（GlassDialog + SettingRow 列表）。
 *
 * 入口只有一种：点卡片 / 标题右侧的「···」，页面别处不再摆同一动作的第二颗按钮。
 * 动作先关菜单、再开目标 Dialog（两个 Dialog 不在同一帧同时出现，沿用 3.26.9 的规矩）。
 */
@Composable
private fun StoryMenuDialog(
    title: String,
    subtitle: String?,
    actions: List<StoryMenuAction>,
    onDismiss: () -> Unit,
) {
    GlassDialog(onDismiss = onDismiss, dismissOnOutsideClick = false) {
        // 3.26.48：取消走 close（见 [storyClose] 的 TODO）。
        val close = storyClose(onDismiss)
        DialogTitle(title, subtitle = subtitle)
        Spacer(Modifier.height(10.dp))
        actions.forEachIndexed { i, action ->
            if (i > 0) RowDivider()
            SettingRow(
                title = action.label,
                subtitle = action.subtitle,
                glyph = action.glyph,
                // 菜单项**不**走 close：点完要立刻关菜单、同一帧开下一个对话框，
                // 若先播退场再动作，两个 Dialog 会叠一帧（3.26.9 的老规矩）。
                onClick = if (action.enabled) {
                    { onDismiss(); action.onClick() }
                } else {
                    null
                },
            )
        }
        Spacer(Modifier.height(14.dp))
        GlassButton("取消", close, Modifier.fillMaxWidth())
    }
}

/** 自由大纲节点的 id 前缀：OutlineNode 没有类型字段，这里用 id 前缀区分「不绑章的自由节点」。 */
private const val FREE_OUTLINE_PREFIX = "free_"

private fun isFreeOutlineNode(n: OutlineNode): Boolean =
    n.chapterId == null && n.id.startsWith(FREE_OUTLINE_PREFIX)

/**
 * 让滑动内容铺到面板本体，而不是被 content 槽两侧 20dp 的留白裁在里面。
 *
 * 3.26.46 用户报「边框/内窗口把内容挡住」：面板本身没有描边，真正的原因是
 * InlineModalSheet content 槽左右各 20dp 的 chrome；AnimatedContent 在槽里滑动，内容在槽边被裁掉。
 * 这里反向多量 2×inset、再左移 inset；每个页面自己补回 inset 边距，静止布局不变。
 *
 * 3.26.47：**故事面板已改用 `InlineModalSheet(bleedContent = true)`，不再需要这层**；
 * 只剩悬浮故事页的 [ModalSheet] 还用它（ModalSheet 还没有 bleedContent，见那里的 TODO）。
 */
private fun Modifier.storyBleed(inset: Dp = SHEET_CONTENT_INSET): Modifier = this.layout { measurable, constraints ->
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

/** LazyColumn 里的落位弹簧（3.26.58：待同意抽屉行、同步预览、按大纲建章这类列表共用）。 */
@Composable
private fun storyPlacementSpec(): FiniteAnimationSpec<IntOffset> {
    val motion = LocalInk.current.settings.motion != "none"
    return if (motion) spring(dampingRatio = .6f, stiffness = 380f) else tween(0)
}

/**
 * 角色卡第一行的富文本（3.26.59）：**名字 → 身份 ·（阵营，弱化）**。
 *
 * 合成一行而不是普通 Row：名字 18f、身份 / 阵营 11f，三段宽度互不相同；用同一个文本行
 * 才能在窄卡上整体单行省略（`maxLines = 1` + `Ellipsis`），不会出现名字被挤成两行、
 * 或「· 阵营」被挤出卡片右边。阵营比身份再淡一档，读作身份之外的补充。
 * 性格外貌不进卡片（整段太长会把卡片撑高），只在点开的详情里出现。
 */
private fun characterIdentityLine(c: StoryCharacter, ui: InkUi): AnnotatedString {
    val role = c.role.trim()
    val faction = c.faction.trim()
    return buildAnnotatedString {
        append(c.name.trim().ifBlank { "未命名" })
        if (role.isNotEmpty()) {
            withStyle(SpanStyle(fontSize = 11.sp, color = ui.palette.muted, fontWeight = FontWeight.Normal)) {
                append("  ")
                append(role)
            }
        }
        if (faction.isNotEmpty()) {
            withStyle(
                SpanStyle(
                    fontSize = 11.sp,
                    color = ui.palette.muted.copy(alpha = .72f),
                    fontWeight = FontWeight.Normal,
                ),
            ) {
                // 有身份才画中间点；只有阵营时直接跟在名字后面（不留下一个孤零零的「·」）。
                append(if (role.isNotEmpty()) "  ·  " else "  ")
                append(faction)
            }
        }
    }
}

/**
 * 角色卡（20dp 圆角 / 16dp 内边距 / 18f 名字 / 11f 身份 / 11.5f 一句话）。
 *
 * 3.26.59：第一行 = 名字 → 身份 · 阵营（阵营弱化，见 [characterIdentityLine]）；
 * 性格外貌**不铺在卡上**（会长），只在详情里看。
 *
 * 右上「···」= 卡片次操作（加关系 / 编辑 / 删除）；不传 [onMore] 就不画。
 */
@Composable
private fun StoryCharacterCard(
    character: StoryCharacter,
    onMore: (() -> Unit)? = null,
) {
    val ui = LocalInk.current
    MoyuSurface(
        Modifier.fillMaxWidth(),
        radius = 20f,
        flat = true,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(ballColor(character), CircleShape))
                Spacer(Modifier.width(8.dp))
                Txt(
                    characterIdentityLine(character, ui),
                    modifier = Modifier.weight(1f),
                    size = 18f,
                    weight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onMore != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).clickable(onClick = onMore),
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(Glyph.More, ui.palette.muted, 15.dp) }
                }
            }
            if (character.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Txt(character.note, size = 11.5f, color = ui.palette.muted, lineHeight = 18f)
            }
        }
    }
}

/**
 * 关系行：点「···」删这条关系（确认后才删）。
 *
 * 3.26.49：圆点与类型名都读 [StoryRelation.kindText] / [StoryRelation.kindColor]——自定义类型
 * 显示作者起的名字，圆点用自定义色（没设色按内置三档）；刚保存的那一行 `fresh = true`，
 * 用 spring(dampingRatio = .6f, stiffness = 380f) 从左侧淡入（motion = none 直接落位）。
 * 3.26.50：没设自定义色时圆点颜色改读**生效档位** [StoryRelation.tier]——自定义关系选了
 * 「亲近 / 对立 / 其他」就与图例同档同色。
 */
@Composable
private fun StoryRelationRow(
    title: String,
    relation: StoryRelation,
    fresh: Boolean = false,
    onMore: (() -> Unit)? = null,
) {
    val ui = LocalInk.current
    val motion = ui.settings.motion != "none"
    val enter = remember { Animatable(if (fresh) 0f else 1f) }
    LaunchedEffect(fresh) {
        if (fresh) {
            enter.snapTo(0f)
            enter.animateTo(1f, if (motion) spring(dampingRatio = .6f, stiffness = 380f) else tween(0))
        } else {
            enter.snapTo(1f)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter.value
                translationX = (1f - enter.value) * 10.dp.toPx()
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(relationColor(relation.tier, relation.kindColor, ui), CircleShape))
        Spacer(Modifier.width(10.dp))
        Txt(title, size = 13f)
        Spacer(Modifier.width(8.dp))
        Txt(relation.kindText, size = 11f, color = ui.palette.muted)
        Spacer(Modifier.weight(1f))
        if (onMore != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(26.dp).clip(CircleShape).clickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) { LineIcon(Glyph.More, ui.palette.muted, 14.dp) }
        }
    }
}

private fun relationTitle(bible: BookBible, r: StoryRelation): String {
    val a = bible.character(r.from)?.name?.ifBlank { "未命名" } ?: "已删除角色"
    val b = bible.character(r.to)?.name?.ifBlank { "未命名" } ?: "已删除角色"
    return "$a — $b"
}

/** 大纲卡类型（3.26.47「两种卡」）：章级未落地 / 已建章的章卡 / 自由卡。 */
private enum class OutlineCardKind { ChapterPlanned, ChapterAttached, Free }

/**
 * 章级大纲卡（3.26.47）：有章名。
 *
 * 结构（和自由卡一眼可分，不靠卡内小字标签）：卡头 = 固定序号栏 + 章名 + 状态，
 * 下面一条分隔线再列情节点；未落地节点序号栏画「章」图标，已建章显示 01/02。
 * 「建章」不画在卡上，走卡片右上「···」菜单（每页只留底部一个主操作）。
 *
 * 入口：点卡 = 开情节点编辑器；点「···」= 建章 / 清空情节点 / 删除整条大纲；
 * 点情节点 = 勾选；点情节点行尾垃圾桶 = 删这一条（确认后）。
 */
@Composable
private fun ChapterOutlineCard(
    seq: Int?,
    chapterTitle: String,
    beats: List<OutlineBeat>,
    statusText: String? = null,
    statusAccent: Boolean = false,
    onCardClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    onToggleBeat: ((OutlineBeat) -> Unit)? = null,
    onDeleteBeat: ((OutlineBeat) -> Unit)? = null,
) {
    val ui = LocalInk.current
    MoyuSurface(
        Modifier.fillMaxWidth(),
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
                // 序号栏：已建章 01/02…；未落地的计划节点画「章」图标——这是卡头结构的一部分。
                Box(Modifier.width(26.dp), contentAlignment = Alignment.CenterStart) {
                    if (seq != null) {
                        Txt("%02d".format(seq), size = 10f, color = ui.palette.muted, letterSpacing = .8f)
                    } else {
                        LineIcon(Glyph.Book, ui.palette.muted.copy(alpha = .8f), 14.dp)
                    }
                }
                Txt(
                    chapterTitle,
                    size = 15f,
                    weight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 3.26.48（用户报「标签贴着章名」）：标题吃满剩余宽度、过长就省略；
                    // 右侧标签组固定在最右、与章名之间留 ≥12dp。
                    // 旧版是「标题 fill=false + 一个同样 fill=false 的 Spacer」——两个加权子项
                    // 平分剩余宽度，Spacer 又是 0 宽，标签于是紧贴章名，标题一长还把标签挤走。
                    modifier = Modifier.weight(1f),
                )
                if (statusText != null) {
                    Spacer(Modifier.width(12.dp))
                    Txt(
                        statusText,
                        size = 10.5f,
                        color = if (statusAccent) ui.accent else ui.palette.muted,
                        maxLines = 1,
                    )
                }
                if (onMore != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onMore),
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(Glyph.More, ui.palette.muted, 16.dp) }
                }
            }
            if (beats.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 26.dp)
                        .height(.6.dp)
                        .background(ui.palette.line.copy(alpha = .75f)),
                )
                Spacer(Modifier.height(5.dp))
                Column(Modifier.fillMaxWidth().padding(start = 26.dp)) {
                    beats.forEach { b ->
                        StoryBeatRow(beat = b, onToggle = onToggleBeat, onDelete = onDeleteBeat)
                    }
                }
            }
        }
    }
}

/**
 * 自由大纲卡（3.26.47）：无章名、纯剧情规划、没有「建章」。
 *
 * 结构（不靠小字标签）：没有卡头（无序号 / 章名 / 状态），标题直接领起情节点，
 * 左侧一条 2dp 细竖线表示它不绑章；情节点同样是行内列表。
 *
 * 入口：点卡 = 编辑这条自由大纲；点「···」= 清空情节点 / 删除整条大纲；
 * 点情节点 = 勾选；点情节点行尾垃圾桶 = 删这一条（确认后）。
 */
@Composable
private fun FreeOutlineCard(
    title: String,
    beats: List<OutlineBeat>,
    onCardClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    onToggleBeat: ((OutlineBeat) -> Unit)? = null,
    onDeleteBeat: ((OutlineBeat) -> Unit)? = null,
) {
    val ui = LocalInk.current
    MoyuSurface(
        Modifier.fillMaxWidth(),
        radius = 18f,
        flat = true,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .then(if (onCardClick != null) Modifier.bouncy(scale = .99f) { onCardClick() } else Modifier)
                .padding(14.dp),
        ) {
            // 竖线是自由卡的结构记号：它不在章节序列里，行情节点与标题同宽排布。
            Box(
                Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(ui.palette.line),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Txt(
                        title,
                        size = 15f,
                        weight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // 3.26.48：与章级卡同一条规矩——标题吃满剩余宽度（过长省略），
                        // 右侧标签固定在最右、与标题之间 ≥12dp（旧版标签贴着标题）。
                        modifier = Modifier.weight(1f),
                    )
                    if (onMore != null) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onMore),
                            contentAlignment = Alignment.Center,
                        ) { LineIcon(Glyph.More, ui.palette.muted, 16.dp) }
                    }
                }
                if (beats.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    beats.forEach { b ->
                        StoryBeatRow(beat = b, onToggle = onToggleBeat, onDelete = onDeleteBeat)
                    }
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

/**
 * 故事面板的数据过滤（3.26.46；3.26.64 只剩软隐藏语义）。
 *
 * 3.26.64：「同步 AI 内容到我的」整条链路已删，三个 pane 一律调 `mineWith(emptySet())`——
 * 传空集 = 只铺作者条目，旧 AI 条目（origin = text / ai）**先不上屏**但数据仍在盘上，
 * 第二波再真删。带非空集合的分支保留：它按标记把 AI 条目并进作者页，是旧备份 / 后续清洗的
 * 现成工具，删掉会丢一层语义。
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

/**
 * 3.26.48：GlassDialog 内容里的「取消 / 保存 / 删除 / 关闭」按钮统一走这里。
 *
 * 背景：Design.kt 3.26.47 给 GlassDialog 补了退场（内部 `close()`：先播 exit、AnimatedVisibility
 * idle 后才回调宿主 onDismiss），但**没有把 close 暴露给内容**（ModalSheet 有 closeableContent，
 * GlassDialog 没有）。宿主都是 `if (flag) GlassDialog(...)`，内容按钮直接调宿主 lambda 会让窗口
 * 当帧卸掉、退场被跳过。
 *
 * 兜底：把宿主关闭推到下一帧——点击这一帧的按压反馈（bouncy / 选择标记）先画完，不是同一帧硬切。
 *
 * TODO(3.26.49+)：Design.kt 给 GlassDialog 补 `closeableContent`（与 ModalSheet 同一写法）后，
 * 删掉这个兜底、改成直接用框架的 close()。**别在本文件改 Design.kt**。
 */
@Composable
private fun storyClose(onClose: () -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onClose)
    return remember {
        val fire: () -> Unit = {
            scope.launch {
                withFrameNanos { }
                latest.value()
            }
        }
        fire
    }
}

// ───────────────────────── 按大纲建章（3.26.45；3.26.56 批量入口） ─────────────────────────

/**
 * 「按大纲建章」的候选：**章级且还没落地**的节点。
 *
 * 判据与 [OutlinePane] 卡片「建章」同口径：
 * - `free_` 前缀的自由节点不绑章、不参与（自由大纲只做剧情规划）；
 * - `chapterId` 已经指向某一章的算已落地，不再建；
 * - 3.26.64：只在**作者自己的**条目里找（与页面铺出来的那份同一过滤），
 *   否则会出现「副标题说有 N 条、列表里却看不到」的不一致。
 *
 * 标题「···」菜单的副标题与 [BuildChaptersFromOutlineDialog] 共用这一条判据，别在两处各写一份。
 */
private fun outlineNodesToBuild(book: Book): List<OutlineNode> =
    book.bible.mineWith(emptySet()).outline.filter { n ->
        !n.id.startsWith(FREE_OUTLINE_PREFIX) && book.chapters.none { it.id == n.chapterId }
    }

/**
 * 按大纲建章：把一个未落地节点变成一章（标题用节点标题），再把 node.chapterId 指回新章。
 *
 * 3.26.56：批量入口 [BuildChaptersFromOutlineDialog] 逐条调这里——**同一条落地路径**，
 * 不新造存储 / 网络层；每次调用都从 store 重读，所以连着建多章时章号、大纲回填都正确。
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

/**
 * 「按大纲建章」预览对话框（3.26.56）。
 *
 * 入口：故事面板标题「···」→「按大纲建章」（仅大纲视图出现，排在同为面板级次操作的
 * 「从正文生成大纲」旁边）。点卡片「···」→「建章」是**单条**入口；这里是**批量**入口，
 * 但落地走的是同一个 [buildChapterFromOutline]，所以两条入口建出来的章完全一样。
 *
 * 交互：逐条列出「将新建：第 N 章 · 标题」，默认全选；点整行切换 [SelectMark] 勾选。
 * 章号按「当前章数 + 已勾选里排第几」算——取消前面一条，后面的章号会跟着前移，所见即所得。
 * 没有可建节点（章级大纲已全部落地 / 只有自由大纲 / 还没有大纲）时，按钮禁用并给一行说明。
 *
 * 动效（协议 §5）：列表行 `animateItem` 用项目统一的空间类 spring(.6, 380)，
 * [SelectMark] 自带勾选扩散动画；`motion = none` 时两者都退化为 tween(0)，
 * 见 [storyPlacementSpec] 与 [SelectMark] 内部的 motion 判断。
 */
@Composable
private fun BuildChaptersFromOutlineDialog(
    book: Book,
    onDismiss: () -> Unit,
) {
    val ui = LocalInk.current
    val store = LocalStore.current
    // 打开这一下就定住候选快照：建章会改 book，若跟着 book 重算，关窗前会闪一帧「没有可建节点」。
    val candidates = remember(book.id) { outlineNodesToBuild(book) }
    var picked by remember { mutableStateOf(candidates.map { it.id }.toSet()) }
    // 确认后立刻锁住：建章 + 关窗之间若再来一次点击，不会重复建。
    var done by remember { mutableStateOf(false) }
    val selected = candidates.filter { it.id in picked }
    val chapterBase = book.chapters.size
    // 已勾选节点 → 它会成为第几章（0 基下标）；取消勾选后后面的章号跟着前移。
    val newIndex = selected.withIndex().associate { (i, n) -> n.id to i }
    fun toggle(id: String) {
        if (done) return
        picked = if (id in picked) picked - id else picked + id
    }

    GlassDialog(onDismiss, dismissOnOutsideClick = false) {
        val close = storyClose(onDismiss)
        DialogTitle(
            "按大纲建章",
            subtitle = when {
                candidates.isEmpty() -> "没有可建的章级大纲"
                else -> "共 ${candidates.size} 条章级大纲还没落地，已勾选 ${picked.size} 条；勾中的各新建一章（正文空）"
            },
        )
        Spacer(Modifier.height(12.dp))
        if (candidates.isEmpty()) {
            Txt(
                "章级大纲都已经建过章，或者这本书还没有章级大纲。自由大纲（不绑章）不参与建章。",
                size = 12f,
                color = ui.palette.muted,
                lineHeight = 19f,
            )
        } else {
            // 只有 LazyColumn 能上 animateItem；高度封顶，外层对话框本身可滚，长列表不会撑破窗口。
            val itemSpec = storyPlacementSpec()
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                items(candidates, key = { it.id }) { node ->
                    val checked = node.id in picked
                    val rank = newIndex[node.id]
                    val seq = rank?.let { chapterBase + it + 1 }
                    val title = if (seq == null) {
                        node.title.ifBlank { "未命名大纲" }
                    } else {
                        node.title.ifBlank { chapterFallbackTitle(seq - 1) }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(SmoothCorner(12.dp))
                            // 一行 = 一条勾选：标记只画不点，点击交给整行（同同步预览的写法）。
                            .bouncy(enabled = !done) { toggle(node.id) }
                            .padding(horizontal = 6.dp, vertical = 5.dp)
                            .animateItem(fadeInSpec = null, placementSpec = itemSpec, fadeOutSpec = null),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Txt(
                                if (seq == null) "不新建：$title" else "将新建：第 $seq 章 · $title",
                                size = 12f,
                                weight = FontWeight.Medium,
                                color = if (checked) ui.palette.ink else ui.palette.muted,
                            )
                            if (node.beats.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Txt(
                                    "${node.beats.size} 个情节点会留在这一章的大纲里",
                                    size = 10.5f,
                                    color = ui.palette.muted,
                                    lineHeight = 16f,
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        SelectMark(
                            checked = checked,
                            onToggle = { toggle(node.id) },
                            size = 20.dp,
                            interactive = false,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Txt(
                "建章 = 新建一个空章节（标题取这条大纲），正文从零开始写；自由大纲不参与。",
                size = 10.5f,
                color = ui.palette.muted,
                lineHeight = 17f,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassButton("取消", close, Modifier.weight(1f))
            PrimaryButton(
                if (picked.isEmpty()) "建章" else "建 ${picked.size} 章",
                {
                    if (done || picked.isEmpty()) return@PrimaryButton
                    done = true
                    val targets = candidates.filter { it.id in picked }
                    // 复用卡片「···」→「建章」的同一条落地路径；循环里每次都从 store 重读，
                    // 所以多章连建时章号与 chapterId 回填都不会串。
                    targets.forEach { buildChapterFromOutline(store, book.id, it) }
                    ToastBus.show("已建 ${targets.size} 章")
                    close()
                },
                Modifier.weight(1f),
                glyph = Glyph.Book,
                enabled = picked.isNotEmpty() && !done,
            )
        }
    }
}
