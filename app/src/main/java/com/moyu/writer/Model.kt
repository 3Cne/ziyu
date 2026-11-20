package com.moyu.writer

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

data class Chapter(
    val id: String = uuid(),
    val title: String = chapterFallbackTitle(0),
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Notes bound to this chapter. The other direction lives in [Note.chapterIds]. */
    val noteIds: List<String> = emptyList(),
    /**
     * 笔记夹 linked to this chapter. Everything inside them counts as bound to
     * this chapter too, including notes added to the folder later — the writer
     * asked for「章节不仅能链接笔记，也能链接整个笔记夹」. The reverse edge is not
     * stored on the folder: a folder is just a [Group], and keeping the links
     * on one side only means deleting a folder cannot leave a dangling edge in
     * two places. [notesOfChapter] is the single place that resolves it.
     */
    val folderIds: List<String> = emptyList(),
)

data class Book(
    val id: String = uuid(),
    val title: String = "未命名",
    val author: String = "",
    val genre: String = "小说",
    val synopsis: String = "",
    val coverStyle: Int = 0,
    val coverUri: String? = null,
    val chapters: List<Chapter> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val groupId: String? = null,
    /**
     * 故事圣经（3.26.16）：角色 / 角色关系 / 故事时间线 / 大纲。
     * 这是**唯一事实源**，问书（下一批）只读写这一层，不另建一份。
     * 空对象表示这本书还没用过；旧备份没有该字段时解出空对象，不算迁移。
     */
    val bible: BookBible = BookBible(),
) {
    val words: Int get() = chapters.sumOf { countWords(it.content) }

    fun chapter(id: String?): Chapter? = chapters.firstOrNull { it.id == id } ?: chapters.firstOrNull()
    fun lastChapter(): Chapter? = chapters.maxByOrNull { it.updatedAt } ?: chapters.lastOrNull()
}

data class Todo(
    val id: String = uuid(),
    val text: String = "",
    val done: Boolean = false,
    val remindAt: Long? = null,
    val notified: Boolean = false,
    val start: Int = -1,
    val end: Int = -1,
)

data class Note(
    val id: String = uuid(),
    // Empty means untitled; the UI shows a muted placeholder instead.
    val title: String = "",
    val content: String = "",
    val todos: List<Todo> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val groupId: String? = null,
    /** Chapters this note is bound to. The other direction lives in [Chapter.noteIds]. */
    val chapterIds: List<String> = emptyList(),
) {
    val words: Int get() = countWords(content)
    fun pendingTodos(): List<Todo> = todos.filter { !it.done }
    fun nextReminder(): Todo? = todos.filter { !it.done && it.remindAt != null }.minByOrNull { it.remindAt!! }
}

enum class GroupKind { BOOK, NOTE }

data class Group(
    val id: String = uuid(),
    val name: String = "新分组",
    val color: Long = 0xFF8A7A62,
    val order: Int = 0,
)

/**
 * 角色之间的关系**底档**（3.26.49 起关系类型可以自定义，这里只留 8 种内置档）。
 *
 * 为什么还留着枚举而不是纯自由文本：图例的三档颜色（亲近 / 对立 / 其他）、图上弹簧的档位、
 * 问书 / 自动整理读回来时认类型，都靠它；旧备份里的 `kind` 枚举名照原样读回来，一条不丢。
 * 自定义类型另外存 [StoryRelation.kindLabel] / [StoryRelation.kindColor] / [StoryRelation.kindTier]。
 *
 * 3.26.50：每种底档带一个 [tier]（0 亲近 / 1 对立 / 2 其他），给自定义类型当缺省档位用。
 * 刻意与 3.26.49 的 `relationTone` 三档同口径（师徒归「其他」），所以旧数据缺 `kindTier`
 * 时派生出来的颜色 / 档位与 3.26.49 完全一致。
 */
enum class RelationKind(val label: String, val tier: Int) {
    FAMILY("亲人", 0),
    LOVER("恋人", 0),
    FRIEND("朋友", 0),
    ALLY("同盟", 0),
    RIVAL("对手", 1),
    ENEMY("仇敌", 1),
    MENTOR("师徒", 2),
    OTHER("其他", 2),
}

data class StoryCharacter(
    val id: String = uuid(),
    val name: String = "",
    /** 主角 / 配角 / 反派，自由文本，空表示没填。 */
    val role: String = "",
    /** 一句话：他是谁、想要什么。 */
    val note: String = "",
    val origin: String = "author",
    val updatedAt: Long = 0L,
    /** 3.26.24: #RRGGBB set by the writer; null = derive a stable colour from the id. */
    val color: String? = null,
    /**
     * 3.26.59: 性格 + 外貌概述，自由文本，空表示没填。
     *
     * 旧备份没有这两个键，解码按空串兜底（见 [decodeBible]），所以老数据一条不丢；
     * 编码时**空串也照写**，与 [role] / [note] 的口径一致（空 = 没填，不靠缺键表达）。
     */
    val traits: String = "",
    /** 3.26.59: 阵营 / 所属，自由文本，空表示没填。兼容口径同 [traits]。 */
    val faction: String = "",
)

/**
 * 一条关系。3.26.49：类型可自定义（内置 8 种 + 作者自建）。3.26.50：自定义类型可挑档位。
 *
 * 五个字段的分工：
 * - [kind]：底档，旧数据就是它；决定默认颜色（三档）和图上弹簧长度。
 * - [kindLabel]：显示名。内置类型 = 枚举中文名；自定义类型 = 作者起的名字。空时回退 [kind].label
 *   （老备份没有这个字段，解码时按 kind 派生，所以读回来永远不会是空白）。
 * - [kindColor]：自定义颜色 #RRGGBB；null / 非法 = 按 [kind] 的三档取色。
 * - [kindTier]：3.26.50 档位 0 亲近 / 1 对立 / 2 其他；null = 按 [kind].tier 派生。
 *   自定义类型底档固定 OTHER 时靠它决定图例颜色与弹簧档位；旧数据没有这个字段，
 *   一律按 kind 派生（= 3.26.49 的表现），一条不丢。
 * - `origin / updatedAt`：来源与修改时间（沿用 3.26.23）。
 *
 * 编码时 `kind`（枚举名）、`kindLabel`（显示名）、`kindTier`（档位）都写：新版本都读，
 * 旧版本只认 `kind`，所以降级 / 老备份都不会把内置类型读丢。
 */
data class StoryRelation(
    val id: String = uuid(),
    val from: String,
    val to: String,
    val kind: RelationKind = RelationKind.OTHER,
    /** 3.26.49：显示名；空 = 回退 [kind].label。 */
    val kindLabel: String = "",
    /** 3.26.49：自定义颜色 #RRGGBB；null = 按 [kind] 的档位取色。 */
    val kindColor: String? = null,
    /** 3.26.50：档位 0 亲近 / 1 对立 / 2 其他；null = 按 [kind] 派生。 */
    val kindTier: Int? = null,
    val origin: String = "author",
    val updatedAt: Long = 0L,
) {
    /** 显示名：自定义名优先，旧数据 / 未设名回退内置枚举名。 */
    val kindText: String get() = kindLabel.ifBlank { kind.label }

    /** 是否作者自建类型（显示名与内置枚举名不同）。 */
    val customKind: Boolean get() = kindLabel.isNotBlank() && kindLabel != kind.label

    /**
     * 实际生效的档位（3.26.50）：显式 [kindTier] 优先；缺字段 / 越界按 [kind].tier 派生。
     * 图例取色与弹簧档位都应该读这个，而不是直接读 [kindTier]。
     */
    val tier: Int get() = kindTier?.takeIf { it in RELATION_TIER_MIN..RELATION_TIER_MAX } ?: kind.tier
}

/**
 * 3.26.50：关系档位 0 亲近 / 1 对立 / 2 其他。合法区间只认这三个值；
 * 越界 / 非数字一律当「没设」，解码后按 [RelationKind.tier] 派生（与 kindColor 同一套缺省策略）。
 */
internal const val RELATION_TIER_MIN = 0
internal const val RELATION_TIER_MAX = 2

/**
 * 3.26.50：解码 `kindTier`。旧备份没有这个字段（原样返回 null）、坏值 / 越界也返回 null，
 * 不让非法档位进数据；数字或数字字符串都认，便于手改 JSON 后还能读回来。
 */
internal fun decodeRelationTier(raw: Any?): Int? {
    val value = when (raw) {
        is Number -> raw.toInt()
        is String -> raw.trim().toIntOrNull()
        else -> null
    } ?: return null
    return value.takeIf { it in RELATION_TIER_MIN..RELATION_TIER_MAX }
}

/**
 * 3.26.49：关系颜色只收 `#RRGGBB`。空白 / 非法一律返回 null（= 按内置档位取色），
 * 这样解码、AI 差异、合并三条路径对颜色的判断完全一致。大写存储与角色球色同格式。
 */
internal fun normalizeRelationColor(raw: String?): String? {
    val v = raw?.trim().orEmpty()
    if (v.length != 7 || v[0] != '#') return null
    val hex = v.substring(1)
    if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return "#" + hex.uppercase()
}

/** 3.26.49：自定义关系类型名的长度上限（12 个字符，关系行一行放得下）。 */
internal const val RELATION_LABEL_MAX = 12

/** 按 code point 截断，不把 emoji / 生僻字切成半个；不 trim（输入过程中允许空格）。 */
internal fun capRelationLabel(raw: String, max: Int = RELATION_LABEL_MAX): String {
    if (raw.length <= max) return raw
    val codePoints = raw.codePointCount(0, raw.length).coerceAtMost(max)
    return raw.substring(0, raw.offsetByCodePoints(0, codePoints))
}

data class StoryEvent(
    val id: String = uuid(),
    /** 故事内时间，自由文本（"11 月 7 日"、"三年前"）。 */
    val whenText: String = "",
    val title: String = "",
    val note: String = "",
    /** 3.26.21：一个事件可以同时发生在多个章节；空 = 不关联。 */
    val chapterIds: List<String> = emptyList(),
    val characterIds: List<String> = emptyList(),
    val origin: String = "author",
    val updatedAt: Long = 0L,
)

data class OutlineBeat(
    val id: String = uuid(),
    val text: String = "",
    val done: Boolean = false,
)

data class OutlineNode(
    val id: String = uuid(),
    /** 对应哪一章；null 表示还没落到正文（显示「待写」）。 */
    val chapterId: String? = null,
    val title: String = "",
    val beats: List<OutlineBeat> = emptyList(),
    val origin: String = "author",
    val updatedAt: Long = 0L,
)

data class BookBible(
    val characters: List<StoryCharacter> = emptyList(),
    val relations: List<StoryRelation> = emptyList(),
    val events: List<StoryEvent> = emptyList(),
    val outline: List<OutlineNode> = emptyList(),
) {
    val isEmpty: Boolean
        get() = characters.isEmpty() && relations.isEmpty() && events.isEmpty() && outline.isEmpty()

    fun character(id: String?): StoryCharacter? = characters.firstOrNull { it.id == id }

    /** 与某个角色直接相连的所有关系（两个方向都算）。 */
    fun relationsOf(id: String): List<StoryRelation> =
        relations.filter { it.from == id || it.to == id }

    fun otherEnd(r: StoryRelation, id: String): StoryCharacter? =
        character(if (r.from == id) r.to else r.from)

    /** 删角色时连带删掉挂在它身上的关系与事件引用，避免留悬空边。 */
    fun withoutCharacter(id: String): BookBible = copy(
        characters = characters.filterNot { it.id == id },
        relations = relations.filterNot { it.from == id || it.to == id },
        events = events.map { e -> e.copy(characterIds = e.characterIds.filterNot { it == id }) },
    )

    fun withoutChapter(chapterId: String): BookBible = copy(
        outline = outline.filterNot { it.chapterId == chapterId },
        // 3.26.21：事件可属多章，删章只把这一章从列表里摘掉，其它章节归属保留。
        events = events.map { e ->
            if (chapterId in e.chapterIds) e.copy(chapterIds = e.chapterIds - chapterId) else e
        },
    )
}

/**
 * 3.26.52：默认外观对齐用户真机当前设置（来源：backups/v32643/before-1919.json 的 settings）。
 * 全新安装（没有 moyu.json）时直接落到这套；旧数据 / 旧备份里已有的字段照旧读取，不受影响。
 * 只放外观类字段（主题、点缀色、字体、字号、纸面、分节、标签栏、玻璃）；功能类默认不动。
 */
data class AppSettings(
    val theme: String = "system",
    val accent: Long = 0xFF9A4B43,
    val font: String = "sans",
    val fontScale: Float = .93f,
    val lineHeight: Float = 1.82f,
    val pageMargin: Float = 22f,
    val glow: Boolean = true,
    val grain: Boolean = true,
    val motion: String = "spring",
    val pageMotion: String = "slide",
    val libraryMode: String = "grid",
    val noteMode: String = "list",
    val librarySort: String = "updated",
    val columns: Int = 3,
    val cardScale: Float = 1f,
    val coverRadius: Float = 18f,
    val showTabLabels: Boolean = true,
    /** 能力栏（工具行 + 符号行）上次是展开还是收起；首次与旧备份默认收起。
     *  2026-11-15 用户要求「能力栏的展开和关闭要有记忆性」，默认收起。 */
    val dockOpen: Boolean = false,
    // Liquid-glass knobs, two independent sets: the whole tab bar and the
    // selected lens inside it.
    val tabBlur: Float = 0f,
    val tabRefraction: Float = 9.776876f,
    val tabDispersion: Boolean = false,
    val tabShadow: Float = .30f,
    // Tint concentration of the glass: 1 keeps today's look, 0 leaves only the
    // blur/refraction. Separate for the bar body and the selected lens.
    val tabBarOpacity: Float = .83671397f,
    val tabLensOpacity: Float = 0f,
    /** 0..1 strength of the inner bevel band: the bar and the selected lens
     *  each get their own, so the thickness can be tuned separately. */
    val tabEdge: Float = 0f,
    val lensEdge: Float = 1f,
    val lensRefraction: Float = 9f,
    val lensBlur: Float = 0f,
    val lensDispersion: Boolean = true,
    val lensShadow: Float = .28f,
    // ── 3.26.61：玻璃（TabBar 整块玻璃；条与选中镜片同源，只有这一组）────────
    // 上面 tabBlur/tabRefraction/tabEdge/tabDispersion/tabBarOpacity 与整组镜片
    // 字段只保留解码容错（旧备份能读、不报错），不再被新 UI 写、也不再被 shader 读。
    // 默认值只写在数据类这一处：decodeSettings 缺字段兜底与 withDefaultAppearance()
    // 都从 AppSettings() 派生（3.26.52 三处各写一份默认值漂移的教训）。
    /** 折射强度 0..1 → 曲面边 bevel = 2 + 16 × 值（约 2–18dp）。 */
    val glassRefract: Float = .55f,
    /** 凹面厚度 0..1：1 = 凹月牙，0 = 平的凸面（AGSL 的 `meniscus`）。 */
    val glassMeniscus: Float = 1f,
    /** 边缘模糊 0..1 → rim 额外模糊 8 × 值 px（0–8px）。 */
    val glassEdgeBlur: Float = .5f,
    /** 亮环强度 0..1 → AGSL 的 `edgeK`（0 无亮环，1 全亮）。 */
    val glassRim: Float = .55f,
    /** 色散开关（AGSL 的 R/B 分离）。 */
    val glassDisperse: Boolean = true,
    /**
     * 纸色覆盖 0..1（沿用 tabBarOpacity 的语义：0 = 纯玻璃，1 = 几乎实心纸条）。
     * 3.26.62：默认 .84 → .30（「降纸色罩 + 加真模糊」）；3.26.61 已把 .84 作为默认
     * 发给用户，[decodeSettings] 里有一条一次性迁移把旧默认读成新默认。
     */
    val glassVeil: Float = .30f,
    /**
     * 3.26.62：glassVeil 一次性迁移的落盘标记。
     * 缺省 false；[decodeSettings] 解码后一律置 true。它真正的作用是「文件里有没有
     * 这个键」——3.26.61 写的文件没有它，就是需要迁移的旧默认；有它就绝不再迁移，
     * 所以作者之后自己把纸色罩调回 84% 不会被改第二次。
     */
    val glassVeilMigrated: Boolean = false,
    /**
     * 3.26.67：折射带的「弯折宽度」= H（带宽）倍率，**按部件各存一对**：
     * 标签栏 [tabBend] / 镜片 [lensBend] / 通知 [bubbleBend]。
     * 3.26.65 之前它是 GlassBar.kt 里的内部常量 2.2；3.26.66 曾做成一对全局字段
     * （glassBend / glassSqueeze），用户指出「不是全局，是按部件算的」，此为三对。
     * 3.26.67 同轮还把 H 与 A 拆开：bend 只乘带宽，不再连带放大位移。
     * 默认 2.2 = 3.26.65/66 的常量 → `H = 基准 × 2.2` 与旧观感一致。
     */
    val tabBend: Float = 2.2f,
    /**
     * 3.26.67：「边缘压缩」= A（最大位移）倍率，与带宽正交：`A = 基准 × squeeze`。
     * 默认 2.2 是**重新标定值**：旧式子是 `A = H × squeeze_旧(1.0) = 基准 × 2.2`，
     * 取 2.2 才让默认观感与 3.26.66 逐像素一致。
     */
    val tabSqueeze: Float = 2.2f,
    /** 3.26.67：选中镜片自己的弯折宽度（默认同 [tabBend]）。 */
    val lensBend: Float = 2.2f,
    /** 3.26.67：选中镜片自己的边缘压缩（默认同 [tabSqueeze]，2.2 = 旧观感）。 */
    val lensSqueeze: Float = 2.2f,
    /** 3.26.67：通知 / toast 表面自己的弯折宽度（默认同 [tabBend]）。 */
    val bubbleBend: Float = 2.2f,
    /** 3.26.67：通知 / toast 表面自己的边缘压缩（默认同 [tabSqueeze]）。 */
    val bubbleSqueeze: Float = 2.2f,
    val tabNames: List<String> = listOf("书架", "笔记", "数据", "设置"),
    /** 被关掉的底部页面 key：library / notes / stats。设置页永远显示。 */
    val hiddenTabs: List<String> = listOf("stats"),
    val dailyGoal: Int = 2000,
    val indent: Boolean = true,
    val sectionStyle: String = "line",
    val sectionSymbol: String = "＊　＊　＊",
    val autoSave: Int = 700,
    val showWordCount: Boolean = true,
    val showStats: Boolean = true,
    val haptics: Boolean = true,
    val appName: String = "素纸",
    val showFps: Boolean = false,
    val autoUpdate: Boolean = true,
    val paperStyle: String = "solid",
    val paperLineAlpha: Float = .5f,
    val onboardingDone: Boolean = false,
    /**
     * 已教过的教练组 id（如 `tutorial.landing`）。进 moyu.json；旧备份没有这个字段 =
     * 空列表（= 一组都没教过）。不参与 normalize 的清理：这里只追加，不删。
     */
    val tutorialDone: List<String> = emptyList(),
    /** Chapter editor: keep the bound notes floating beside the body. Off by default. */
    val noteFloat: Boolean = false,
    /** Where the floating note card is pinned: right or left of the body. */
    /** Floating note card position, relative to the writing area (0–1 on each axis). */
    val noteFloatX: Float = .62f,
    val noteFloatY: Float = 0f,
    /** Floating note card width as a share of the writing area. */
    val noteFloatW: Float = .34f,
    /** Floating note card maximum height as a share of the writing area. */
    val noteFloatH: Float = .52f,
    /**
     * AI brain. Off until the writer fills a service URL and a key; the key is
     * never stored here (an exported backup is a plain JSON file in the Download
     * folder, so a key in it would leak with the first share). It lives in an
     * AndroidKeyStore-wrapped file instead — see AiKey in Ai.kt.
     */
    val aiEnabled: Boolean = false,
    val aiBaseUrl: String = AiDefaults.BASE_URL,
    val aiModel: String = AiDefaults.MODEL,
    /** How many retrieved fragments ride along with a question. */
    val aiTopK: Int = 5,
    /** Budget for the question's context, in characters of body text. */
    val aiContextChars: Int = 3000,
    val aiTemperature: Float = .7f,
    /** Doubao-style: pull 0–3 facts out of each exchange, editable later. */
    val aiAutoMemory: Boolean = true,
    /** 3.26.23: auto-organize the story bible after a chapter save (3 min throttle). */
    val aiBibleSync: Boolean = true,
    /** How much pinned text (whole book / chapters / notes) may be sent. */
    val aiQuoteMaxChars: Int = 20_000,
    val aiMemoryLimit: Int = 200,
    /** Web search / fetch. Off by default: every call is billed per request. */
    val aiWeb: Boolean = false,
    /** Searches allowed inside one answer (1–5). */
    val aiWebMax: Int = 3,
    /** Show the per-step tool strip under an answer. */
    val aiToolTrace: Boolean = true,
    /** 问书 · 深度思考：先拆问题、逐条核对原文再答，回答可以更长。 */
    val aiDeepThink: Boolean = false,
    /**
     * AI 改正文的方式：0 只读（只回答，不改稿）/ 1 询问（出补丁，作者点接受）/ 2 自动（回答结束落地）。
     * 3.26.29 起默认询问：正文改动不再默认直接写进去。
     */
    val aiWriteMode: Int = 1,
    /** 整理笔记：带上按下这条之前几条对话；0 = 全部。 */
    val noteContextCount: Int = 8,
    /** 整理笔记：输出预算（token）。4000 标准 / 8000 长。 */
    val noteMaxTokens: Int = 8000,
    /** Glass on the AI input row: veil, blur, edge band, refraction, shadow. */
    val fieldGlassOpacity: Float = .18f,
    val fieldGlassBlur: Float = 11.744422f,
    val fieldGlassEdge: Float = 0f,
    val fieldGlassRefraction: Float = 12.425123f,
    val fieldGlassShadow: Float = .3296146f,
    val fieldGlassDispersion: Boolean = false,
    /** Glass on the AI buttons (link / send), tuned on their own because they
     *  are small round pills over moving text. */
    val btnGlassOpacity: Float = .18f,
    val btnGlassBlur: Float = 12f,
    val btnGlassEdge: Float = 0f,
    val btnGlassRefraction: Float = 18f,
    val btnGlassShadow: Float = .16f,
    val btnGlassDispersion: Boolean = false,
    /** Glass on the motion notification, tuned on its own. */
    val bubbleGlassOpacity: Float = .44114578f,
    val bubbleGlassBlur: Float = 0f,
    val bubbleGlassEdge: Float = 0f,
    val bubbleGlassRefraction: Float = 15.375253f,
    val bubbleGlassShadow: Float = .2530426f,
    val bubbleGlassDispersion: Boolean = false,
)

/**
 * 3.26.53：「恢复默认外观」的唯一出口，值全部取自 [AppSettings] 的默认（3.26.52 改成真机外观）。
 *
 * 不能直接拿 `AppSettings()` 整对象替换——那会把 AI 开关/写入方式、每日目标、笔记浮窗、
 * 引导进度、dock 展开状态这些功能类设置一起重置。这里只换外观类字段：
 * 主题、点缀色、字体与排版、分节、纸面、书架/笔记列表形态、标签栏（含标签名、页签显示与镜头玻璃）、
 * 三组玻璃、动效、glow/grain。
 * 3.26.55：`hiddenTabs`（哪些页签显示）也一起恢复默认（`["stats"]`，默认隐藏数据页）——
 * 与 `decodeSettings` 缺字段时落到 [AppSettings] 默认同一口径，所以「新手默认 = 现在这套」
 * 在两条入口上一致；即恢复默认外观会连带恢复默认页签显示。
 * `tutorialDone` / `onboardingDone` 仍保留，不在这里清（引导进度不归外观重置管）。
 */
fun AppSettings.withDefaultAppearance(): AppSettings {
    val d = AppSettings()
    return copy(
        theme = d.theme,
        accent = d.accent,
        font = d.font,
        fontScale = d.fontScale,
        lineHeight = d.lineHeight,
        pageMargin = d.pageMargin,
        glow = d.glow,
        grain = d.grain,
        motion = d.motion,
        pageMotion = d.pageMotion,
        libraryMode = d.libraryMode,
        noteMode = d.noteMode,
        librarySort = d.librarySort,
        columns = d.columns,
        cardScale = d.cardScale,
        coverRadius = d.coverRadius,
        showTabLabels = d.showTabLabels,
        tabNames = d.tabNames,
        // 3.26.55：页签显示与「新手默认」同口径——默认隐藏数据页；恢复默认外观连带恢复页签。
        hiddenTabs = d.hiddenTabs,
        tabBlur = d.tabBlur,
        tabRefraction = d.tabRefraction,
        tabDispersion = d.tabDispersion,
        tabShadow = d.tabShadow,
        tabBarOpacity = d.tabBarOpacity,
        tabLensOpacity = d.tabLensOpacity,
        tabEdge = d.tabEdge,
        lensEdge = d.lensEdge,
        lensRefraction = d.lensRefraction,
        lensBlur = d.lensBlur,
        lensDispersion = d.lensDispersion,
        lensShadow = d.lensShadow,
        glassRefract = d.glassRefract,
        glassMeniscus = d.glassMeniscus,
        glassEdgeBlur = d.glassEdgeBlur,
        glassRim = d.glassRim,
        glassDisperse = d.glassDisperse,
        glassVeil = d.glassVeil,
        tabBend = d.tabBend,
        tabSqueeze = d.tabSqueeze,
        lensBend = d.lensBend,
        lensSqueeze = d.lensSqueeze,
        bubbleBend = d.bubbleBend,
        bubbleSqueeze = d.bubbleSqueeze,
        sectionStyle = d.sectionStyle,
        sectionSymbol = d.sectionSymbol,
        paperStyle = d.paperStyle,
        paperLineAlpha = d.paperLineAlpha,
        fieldGlassOpacity = d.fieldGlassOpacity,
        fieldGlassBlur = d.fieldGlassBlur,
        fieldGlassEdge = d.fieldGlassEdge,
        fieldGlassRefraction = d.fieldGlassRefraction,
        fieldGlassShadow = d.fieldGlassShadow,
        fieldGlassDispersion = d.fieldGlassDispersion,
        btnGlassOpacity = d.btnGlassOpacity,
        btnGlassBlur = d.btnGlassBlur,
        btnGlassEdge = d.btnGlassEdge,
        btnGlassRefraction = d.btnGlassRefraction,
        btnGlassShadow = d.btnGlassShadow,
        btnGlassDispersion = d.btnGlassDispersion,
        bubbleGlassOpacity = d.bubbleGlassOpacity,
        bubbleGlassBlur = d.bubbleGlassBlur,
        bubbleGlassEdge = d.bubbleGlassEdge,
        bubbleGlassRefraction = d.bubbleGlassRefraction,
        bubbleGlassShadow = d.bubbleGlassShadow,
        bubbleGlassDispersion = d.bubbleGlassDispersion,
    )
}

data class AppData(
    val books: List<Book> = emptyList(),
    val notes: List<Note> = emptyList(),
    val bookGroups: List<Group> = emptyList(),
    val noteGroups: List<Group> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val dailyWords: Map<String, Int> = emptyMap(),
    val lastBookId: String? = null,
    val lastChapterId: String? = null,
    val version: Int = 1,
)

class AppStore(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "moyu.json")
    private val backup = File(appContext.filesDir, "moyu.backup.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: kotlinx.coroutines.Job? = null

    var data by mutableStateOf(reconcileLinks(repairLinks(normalize(load()))))
        private set

    val settings: AppSettings get() = data.settings
    val books: List<Book>
        get() {
            val order = when (data.settings.librarySort) {
                "title" -> compareBy<Book> { it.title.lowercase() }
                "created" -> compareByDescending<Book> { it.createdAt }
                "words" -> compareByDescending<Book> { it.words }
                else -> compareByDescending<Book> { it.updatedAt }
            }
            return data.books.sortedWith(compareByDescending<Book> { it.pinned }.then(order))
        }
    val notes: List<Note> get() = data.notes.sortedWith(
        compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt }
    )

    fun mutate(transform: (AppData) -> AppData) {
        // Every write goes through here, so this is also where the chapter↔note
        // link is kept two-sided. A link written on either end shows up on the
        // other end without a second call site.
        data = reconcileLinks(transform(data))
        scheduleSave()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        mutate { it.copy(settings = transform(it.settings)) }
    }

    fun book(id: String?): Book? = data.books.firstOrNull { it.id == id }

    val bookGroups: List<Group> get() = data.bookGroups.sortedBy { it.order }
    val noteGroups: List<Group> get() = data.noteGroups.sortedBy { it.order }

    fun group(kind: GroupKind, id: String?): Group? =
        if (kind == GroupKind.BOOK) data.bookGroups.firstOrNull { it.id == id }
        else data.noteGroups.firstOrNull { it.id == id }

    fun addGroup(kind: GroupKind, group: Group) {
        mutate { d ->
            val groups = if (kind == GroupKind.BOOK) d.bookGroups else d.noteGroups
            val nextOrder = (groups.maxOfOrNull { it.order } ?: -1) + 1
            val added = group.copy(order = nextOrder)
            if (kind == GroupKind.BOOK) d.copy(bookGroups = groups + added)
            else d.copy(noteGroups = groups + added)
        }
    }

    fun updateGroup(kind: GroupKind, id: String, transform: (Group) -> Group) {
        mutate { d ->
            if (kind == GroupKind.BOOK) d.copy(bookGroups = d.bookGroups.map { if (it.id == id) transform(it) else it })
            else d.copy(noteGroups = d.noteGroups.map { if (it.id == id) transform(it) else it })
        }
    }

    fun deleteGroup(kind: GroupKind, id: String) {
        mutate { d ->
            if (kind == GroupKind.BOOK) {
                d.copy(
                    bookGroups = d.bookGroups.filterNot { it.id == id },
                    books = d.books.map { if (it.groupId == id) it.copy(groupId = null) else it },
                )
            } else {
                d.copy(
                    noteGroups = d.noteGroups.filterNot { it.id == id },
                    notes = d.notes.map { if (it.groupId == id) it.copy(groupId = null) else it },
                    // 章节可能把整个笔记夹链接过去了；夹没了，这条链接也失效。
                    // 夹里的笔记本身不删，只是回到「未归类」。
                    books = d.books.map { b ->
                        if (b.chapters.none { id in it.folderIds }) b else b.copy(
                            chapters = b.chapters.map {
                                if (id in it.folderIds) it.copy(folderIds = it.folderIds.filterNot { f -> f == id }) else it
                            },
                        )
                    },
                )
            }
        }
    }

    fun moveGroup(kind: GroupKind, id: String, delta: Int) {
        mutate { d ->
            val source = if (kind == GroupKind.BOOK) d.bookGroups else d.noteGroups
            val sorted = source.sortedBy { it.order }
            val index = sorted.indexOfFirst { it.id == id }
            val other = (index + delta).coerceIn(0, sorted.lastIndex)
            if (index < 0 || index == other) d else {
                val swapped = sorted.toMutableList().also {
                    val tmp = it[index]
                    it[index] = it[other]
                    it[other] = tmp
                }.mapIndexed { i, g -> g.copy(order = i) }
                if (kind == GroupKind.BOOK) d.copy(bookGroups = swapped) else d.copy(noteGroups = swapped)
            }
        }
    }

    fun setBookGroup(bookId: String, groupId: String?) {
        mutate { d ->
            d.copy(books = d.books.map { if (it.id == bookId) it.copy(groupId = groupId) else it })
        }
    }

    fun setNoteGroup(noteId: String, groupId: String?) {
        mutate { d ->
            d.copy(notes = d.notes.map { if (it.id == noteId) it.copy(groupId = groupId) else it })
        }
    }

    fun addBook(book: Book) {
        mutate { d ->
            d.copy(
                books = d.books + book,
                lastBookId = book.id,
                lastChapterId = book.chapters.firstOrNull()?.id,
            )
        }
    }

    fun updateBook(id: String, transform: (Book) -> Book) {
        mutate { d ->
            d.copy(books = d.books.map { if (it.id == id) transform(it).copy(updatedAt = now()) else it })
        }
    }

    fun deleteBook(id: String) {
        mutate { d ->
            d.copy(
                books = d.books.filterNot { it.id == id },
                lastBookId = if (d.lastBookId == id) d.books.firstOrNull { it.id != id }?.id else d.lastBookId,
                lastChapterId = if (d.lastBookId == id) null else d.lastChapterId,
            )
        }
    }

    fun touchBook(id: String, chapterId: String?) {
        mutate { d ->
            d.copy(
                lastBookId = id,
                lastChapterId = chapterId,
                books = d.books.map { b ->
                    if (b.id == id) b.copy(updatedAt = now()) else b
                }
            )
        }
    }

    fun addChapter(bookId: String, chapter: Chapter) {
        mutate { d ->
            d.copy(
                books = d.books.map { b ->
                    if (b.id == bookId) b.copy(chapters = b.chapters + chapter, updatedAt = now()) else b
                },
                lastBookId = bookId,
                lastChapterId = chapter.id,
            )
        }
    }

    /**
     * 3.26.23: single entry for writing the story bible from a composable (writer page / floating panel).
     */
    fun updateBible(bookId: String, bible: BookBible) {
        mutate { d ->
            val prev = d.books.firstOrNull { it.id == bookId }?.bible
            // 3.26.23: anything changed by a story-page edit is the writer's own setting, so it
            // becomes origin=author; untouched entries keep their origin (AI / text).
            val next = if (prev == null) bible else bible.copy(
                characters = bible.characters.map { c ->
                    if (prev.characters.firstOrNull { it.id == c.id } == c) c
                    else c.copy(origin = "author", updatedAt = now())
                },
                relations = bible.relations.map { r ->
                    if (prev.relations.firstOrNull { it.id == r.id } == r) r
                    else r.copy(origin = "author", updatedAt = now())
                },
                events = bible.events.map { e ->
                    if (prev.events.firstOrNull { it.id == e.id } == e) e
                    else e.copy(origin = "author", updatedAt = now())
                },
                outline = bible.outline.map { n ->
                    if (prev.outline.firstOrNull { it.id == n.id } == n) n
                    else n.copy(origin = "author", updatedAt = now())
                },
            )
            d.copy(books = d.books.map { b -> if (b.id == bookId) b.copy(bible = next) else b })
        }
    }

    fun updateChapter(bookId: String, chapterId: String, title: String? = null, content: String? = null) {
        mutate { d ->
            var delta = 0
            val books = d.books.map { b ->
                if (b.id != bookId) b else {
                    val chapters = b.chapters.map { c ->
                        if (c.id != chapterId) c else {
                            val newContent = content ?: c.content
                            delta += countWords(newContent) - countWords(c.content)
                            c.copy(
                                title = title ?: c.title,
                                content = newContent,
                                updatedAt = when {
                                    title == null && content == null -> c.updatedAt
                                    else -> now()
                                },
                            )
                        }
                    }
                    b.copy(chapters = chapters, updatedAt = now())
                }
            }
            val today = todayKey()
            val daily = if (delta != 0) {
                d.dailyWords.toMutableMap().also { it[today] = max(0, (it[today] ?: 0) + delta) }
            } else d.dailyWords
            d.copy(books = books, dailyWords = daily, lastBookId = bookId, lastChapterId = chapterId)
        }
    }

    /**
     * Bind or unbind a note and a chapter. The relation is stored on both sides
     * so either end can list the other without scanning the whole data set;
     * [normalize] repairs any half-written link.
     */
    fun setNoteLinked(bookId: String, chapterId: String, noteId: String, linked: Boolean) {
        android.util.Log.d("MOYU_LINK", "setNoteLinked linked=$linked note=$noteId book=$bookId ch=$chapterId")
        mutate { d ->
            // Resolve the link on both ends explicitly. An earlier version used a
            // local extension whose lambda `it` was shadowed by the enclosing
            // map's `it`, so only the chapter side was updated and reconcileLinks
            // put the note side straight back — binding could be added but never
            // removed.
            val nextNotes = d.notes.map { n ->
                if (n.id != noteId) n
                else {
                    val next = if (linked) {
                        (n.chapterIds + chapterId).distinct()
                    } else {
                        n.chapterIds.filter { id -> id != chapterId }
                    }
                    android.util.Log.d("MOYU_LINK", "note ${n.id}: ${n.chapterIds} -> $next")
                    n.copy(chapterIds = next)
                }
            }
            val nextBooks = d.books.map { b ->
                if (b.id != bookId) b
                else b.copy(
                    chapters = b.chapters.map { c ->
                        if (c.id != chapterId) c
                        else {
                            val next = if (linked) {
                                (c.noteIds + noteId).distinct()
                            } else {
                                c.noteIds.filter { id -> id != noteId }
                            }
                            android.util.Log.d("MOYU_LINK", "chapter ${c.id}: ${c.noteIds} -> $next")
                            c.copy(noteIds = next)
                        }
                    },
                )
            }
            d.copy(notes = nextNotes, books = nextBooks)
        }
    }

    /**
     * Link or unlink a whole 笔记夹 to a chapter. Resolving the link into actual
     * notes happens in `notesOfChapter()`, so a note added to the folder later
     * is bound to the chapter without touching the chapter again.
     */
    fun setChapterFolder(bookId: String, chapterId: String, folderId: String, linked: Boolean) {
        mutate { d ->
            d.copy(
                books = d.books.map { b ->
                    if (b.id != bookId) b else b.copy(
                        chapters = b.chapters.map { c ->
                            if (c.id != chapterId) c else {
                                val next = if (linked) {
                                    (c.folderIds + folderId).distinct()
                                } else {
                                    c.folderIds.filterNot { it == folderId }
                                }
                                if (next == c.folderIds) c else c.copy(folderIds = next)
                            }
                        },
                    )
                },
            )
        }
    }

    fun unlinkNoteEverywhere(noteId: String) {
        mutate { d ->
            d.copy(
                books = d.books.map { b ->
                    if (b.chapters.none { noteId in it.noteIds }) b else b.copy(
                        chapters = b.chapters.map {
                            if (noteId in it.noteIds) it.copy(noteIds = it.noteIds.filterNot { id -> id == noteId }) else it
                        },
                    )
                },
            )
        }
    }

    fun unlinkChapterEverywhere(chapterId: String) {
        mutate { d ->
            d.copy(
                notes = d.notes.map {
                    if (chapterId in it.chapterIds) it.copy(chapterIds = it.chapterIds.filterNot { id -> id == chapterId }) else it
                },
            )
        }
    }

    /** Chapter of another book can be bound to a note through the note side. */
    fun setNoteChapterBound(noteId: String, bookId: String, chapterId: String, bound: Boolean) {
        setNoteLinked(bookId, chapterId, noteId, bound)
    }

    /** Links pointing at a missing note or chapter are dropped instead of shown as blanks. */
    fun repairLinks(data: AppData): AppData {
        val noteIds = data.notes.map { it.id }.toSet()
        val chapterIds = data.books.flatMap { b -> b.chapters.map { it.id } }.toSet()
        val books = data.books.map { b ->
            val chapters = b.chapters.map { c ->
                val kept = c.noteIds.filter { it in noteIds }.distinct()
                if (kept == c.noteIds) c else c.copy(noteIds = kept)
            }
            if (chapters == b.chapters) b else b.copy(chapters = chapters)
        }
        val notes = data.notes.map { n ->
            val kept = n.chapterIds.filter { it in chapterIds }.distinct()
            if (kept == n.chapterIds) n else n.copy(chapterIds = kept)
        }
        return data.copy(books = books, notes = notes)
    }

    /**
     * Keep both ends of every chapter↔note link in agreement, keeping whichever
     * side carries the link and dropping links whose target no longer exists.
     */
    private fun reconcileLinks(data: AppData): AppData {
        val noteIds = data.notes.map { it.id }.toSet()
        val chapterOf = data.books.flatMap { b -> b.chapters.map { it.id } }.toSet()
        // An edge exists when either side names the other.
        val chapterNotes = mutableMapOf<String, MutableSet<String>>()
        val noteChapters = mutableMapOf<String, MutableSet<String>>()
        fun addEdge(chapterId: String, noteId: String) {
            if (chapterId !in chapterOf || noteId !in noteIds) return
            chapterNotes.getOrPut(chapterId) { linkedSetOf() }.add(noteId)
            noteChapters.getOrPut(noteId) { linkedSetOf() }.add(chapterId)
        }
        data.books.forEach { b ->
            b.chapters.forEach { c -> c.noteIds.forEach { n -> addEdge(c.id, n) } }
        }
        data.notes.forEach { n -> n.chapterIds.forEach { c -> addEdge(c, n.id) } }
        val books = data.books.map { b ->
            if (b.chapters.none { (chapterNotes[it.id]?.size ?: 0) != it.noteIds.size }) b
            else b.copy(
                chapters = b.chapters.map { c ->
                    val target = chapterNotes[c.id]?.toList() ?: emptyList()
                    if (target == c.noteIds) c else c.copy(noteIds = target)
                },
            )
        }
        val notes = data.notes.map { n ->
            val target = noteChapters[n.id]?.toList() ?: emptyList()
            if (target == n.chapterIds) n else n.copy(chapterIds = target)
        }
        return data.copy(books = books, notes = notes)
    }

    fun deleteChapter(bookId: String, chapterId: String) {
        mutate { d ->
            d.copy(
                books = d.books.map { b ->
                    if (b.id == bookId) {
                        b.copy(
                            chapters = b.chapters.filterNot { it.id == chapterId },
                            // 3.26.18：删章同时清大纲节点、把事件从这一章解绑。
                            // 原来不碰 bible，JSON 里会留下永远不显示、也删不掉的大纲孤儿。
                            bible = b.bible.withoutChapter(chapterId),
                        )
                    } else b
                },
                notes = d.notes.map {
                    if (chapterId in it.chapterIds) {
                        it.copy(chapterIds = it.chapterIds.filterNot { id -> id == chapterId })
                    } else it
                },
            )
        }
    }

    fun addNote(note: Note) {
        mutate { it.copy(notes = it.notes + note) }
    }

    fun updateNote(id: String, transform: (Note) -> Note) {
        mutate { d ->
            d.copy(notes = d.notes.map {
                if (it.id == id) transform(it).copy(updatedAt = now()) else it
            })
        }
    }

    fun deleteNote(id: String) {
        mutate { d ->
            d.copy(
                notes = d.notes.filterNot { n -> n.id == id },
                books = d.books.map { b ->
                    if (b.chapters.none { id in it.noteIds }) b else b.copy(
                        chapters = b.chapters.map {
                            if (id in it.noteIds) it.copy(noteIds = it.noteIds.filterNot { n -> n == id }) else it
                        },
                    )
                },
            )
        }
    }

    fun markReminderDone(noteId: String, todoId: String) {
        updateNote(noteId) { n ->
            n.copy(todos = n.todos.map { if (it.id == todoId) it.copy(done = true) else it })
        }
    }

    fun replaceAll(replacement: AppData) {
        data = repairLinks(normalize(replacement))
        saveNow()
    }

    fun clearAll(books: Boolean = true, notes: Boolean = true) {
        mutate { d ->
            // Removing either side would otherwise leave notes or chapters
            // pointing at an id that no longer exists.
            val stripped = repairLinks(
                d.copy(
                    books = if (books) emptyList() else d.books,
                    notes = if (notes) emptyList() else d.notes,
                )
            )
            stripped.copy(
                lastBookId = if (books) null else d.lastBookId,
                lastChapterId = if (books) null else d.lastChapterId,
                dailyWords = if (books && notes) emptyMap() else d.dailyWords,
            )
        }
    }

    fun storageBytes(): Long = file.length() + backup.length()

    fun wordsOn(date: LocalDate): Int = data.dailyWords[date.format(DateTimeFormatter.ISO_DATE)] ?: 0

    fun todayWords(): Int = wordsOn(LocalDate.now())

    fun streak(): Int {
        var day = LocalDate.now()
        if (wordsOn(day) == 0) day = day.minusDays(1)
        var count = 0
        while (wordsOn(day) > 0 && count < 999) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(500)
            writeNow()
        }
    }

    fun saveNow() {
        saveJob?.cancel()
        scope.launch { writeNow() }
    }

    fun flush() {
        saveJob?.cancel()
        writeNow()
    }

    private fun writeNow() {
        try {
            val json = encodeData(data)
            val tmp = File(appContext.filesDir, "moyu.tmp")
            tmp.writeText(json, Charsets.UTF_8)
            if (file.exists()) file.copyTo(backup, overwrite = true)
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        } catch (_: Throwable) {
        }
    }

    private fun normalize(data: AppData): AppData {
        // An untitled note keeps an EMPTY title so the editor can draw its
        // placeholder in the muted style, like any other hint text. Storing the
        // literal string made it real content: the placeholder branch was dead
        // because isBlank() was never true, and the title looked like a heading
        // the user had typed. Notes already carrying that literal are folded
        // back to empty here, so existing data matches the new behaviour.
        val fixedNotes = data.notes.map { note ->
            if (note.title.isBlank() || note.title.trim() == "无标题笔记") {
                note.copy(title = "")
            } else {
                note
            }
        }
        val bookGroups = data.bookGroups.distinctBy { it.id }.sortedBy { it.order }
            .mapIndexed { i, g -> g.copy(order = i) }
        val noteGroups = data.noteGroups.distinctBy { it.id }.sortedBy { it.order }
            .mapIndexed { i, g -> g.copy(order = i) }
        val bookIds = bookGroups.map { it.id }.toSet()
        val noteIds = noteGroups.map { it.id }.toSet()
        val fixedBooks = data.books.map { b ->
            val cleanBook = if (b.groupId != null && b.groupId !in bookIds) b.copy(groupId = null) else b
            // A chapter may be linked to a whole 笔记夹. If that folder is gone
            // the link must go too, otherwise the chapter would keep counting
            // notes that no longer have a folder to belong to.
            if (cleanBook.chapters.none { c -> c.folderIds.any { it !in noteIds } }) cleanBook
            else cleanBook.copy(
                chapters = cleanBook.chapters.map { c ->
                    val kept = c.folderIds.filter { it in noteIds }
                    if (kept == c.folderIds) c else c.copy(folderIds = kept)
                },
            )
        }
        val fixedNotesWithGroups = fixedNotes.map {
            if (it.groupId != null && it.groupId !in noteIds) it.copy(groupId = null) else it
        }
        var settings = data.settings.copy(
            // Only an empty name falls back. The old migration list here
            // ("墨屿", "笔滞", "3Cne") was meant to rename legacy defaults once,
            // but normalize runs on every launch, so picking any of those names
            // silently reverted to 字隅 the next time the app started.
            // 改名迁移：旧默认值「字隅」跟到新默认「素纸」，别让桌面与设置里两个名字。
            appName = data.settings.appName.ifBlank { "素纸" }
                .let { if (it == "字隅") "素纸" else it },
        )
        if (settings.paperStyle !in setOf("plain", "ruled", "solid")) {
            settings = settings.copy(paperStyle = "plain")
        }
        // The transition picker is gone: pages always slide. Data saved with
        // fade/scale falls back here so the old value cannot linger forever.
        if (settings.pageMotion != "slide") {
            settings = settings.copy(pageMotion = "slide")
        }
        if (settings.tabEdge !in 0f..1f || settings.lensEdge !in 0f..1f) {
            settings = settings.copy(
                tabEdge = settings.tabEdge.coerceIn(0f, 1f),
                lensEdge = settings.lensEdge.coerceIn(0f, 1f),
            )
        }
        if (settings.tabBarOpacity !in 0f..1f || settings.tabLensOpacity !in 0f..1f) {
            settings = settings.copy(
                tabBarOpacity = settings.tabBarOpacity.coerceIn(0f, 1f),
                tabLensOpacity = settings.tabLensOpacity.coerceIn(0f, 1f),
            )
        }
        if (settings.noteFloatW >= .59f) {
            // Widths saved while the notes were a floating card, and by the
            // first side-panel draft, took too much of the page. The panel now
            // opens at one third; the writer can still drag it to 0.60.
            settings = settings.copy(noteFloatW = .34f)
        }

        // Four tabs since 3.26.64（问书页下线）。旧数据可能是五个名字（「问书」夹在数据与设置
        // 之间）：先把「问书」那一位去掉；去完正好四条就原样保留（自定义名不丢），
        // 否则用前三个 + 默认「设置」补齐——设置永远在最后一项。
        val tabDefaults = listOf("书架", "笔记", "数据", "设置")
        val cleaned = settings.tabNames.filterNot { it == "问书" }
        settings = settings.copy(
            tabNames = if (cleaned.size == 4) cleaned
            else (0 until 3).map { cleaned.getOrElse(it) { tabDefaults[it] } } + tabDefaults[3],
        )
        settings = settings.copy(
            aiBaseUrl = settings.aiBaseUrl.trim().ifBlank { "https://api.deepseek.com" },
            aiModel = settings.aiModel.trim().ifBlank { "deepseek-chat" },
            aiTopK = settings.aiTopK.coerceIn(1, 8),
            aiContextChars = settings.aiContextChars.coerceIn(400, 8000),
            aiTemperature = settings.aiTemperature.coerceIn(0f, 1.4f),
            aiQuoteMaxChars = settings.aiQuoteMaxChars.coerceIn(2_000, 60_000),
            aiMemoryLimit = settings.aiMemoryLimit.coerceIn(20, 500),
            aiWebMax = settings.aiWebMax.coerceIn(1, 5),
            aiWriteMode = settings.aiWriteMode.coerceIn(0, 2),
            glassRefract = settings.glassRefract.coerceIn(0f, 1f),
            glassMeniscus = settings.glassMeniscus.coerceIn(0f, 1f),
            glassEdgeBlur = settings.glassEdgeBlur.coerceIn(0f, 1f),
            glassRim = settings.glassRim.coerceIn(0f, 1f),
            glassVeil = settings.glassVeil.coerceIn(0f, 1f),
            // 3.26.67：弯折/边缘压缩按部件 6 个字段的量程（与 decode 同一口径）。
            tabBend = settings.tabBend.coerceIn(GLASS_BEND_MIN, GLASS_BEND_MAX),
            tabSqueeze = settings.tabSqueeze.coerceIn(GLASS_SQUEEZE_MIN, GLASS_SQUEEZE_MAX),
            lensBend = settings.lensBend.coerceIn(GLASS_BEND_MIN, GLASS_BEND_MAX),
            lensSqueeze = settings.lensSqueeze.coerceIn(GLASS_SQUEEZE_MIN, GLASS_SQUEEZE_MAX),
            bubbleBend = settings.bubbleBend.coerceIn(GLASS_BEND_MIN, GLASS_BEND_MAX),
            bubbleSqueeze = settings.bubbleSqueeze.coerceIn(GLASS_SQUEEZE_MIN, GLASS_SQUEEZE_MAX),
            fieldGlassOpacity = settings.fieldGlassOpacity.coerceIn(0f, 1f),
            fieldGlassBlur = settings.fieldGlassBlur.coerceIn(0f, 40f),
            fieldGlassEdge = settings.fieldGlassEdge.coerceIn(0f, 2f),
            fieldGlassRefraction = settings.fieldGlassRefraction.coerceIn(0f, 40f),
            fieldGlassShadow = settings.fieldGlassShadow.coerceIn(0f, 1f),
            btnGlassOpacity = settings.btnGlassOpacity.coerceIn(0f, 1f),
            btnGlassBlur = settings.btnGlassBlur.coerceIn(0f, 40f),
            btnGlassEdge = settings.btnGlassEdge.coerceIn(0f, 2f),
            btnGlassRefraction = settings.btnGlassRefraction.coerceIn(0f, 40f),
            btnGlassShadow = settings.btnGlassShadow.coerceIn(0f, 1f),
            bubbleGlassOpacity = settings.bubbleGlassOpacity.coerceIn(0f, 1f),
            bubbleGlassBlur = settings.bubbleGlassBlur.coerceIn(0f, 40f),
            bubbleGlassEdge = settings.bubbleGlassEdge.coerceIn(0f, 2f),
            bubbleGlassRefraction = settings.bubbleGlassRefraction.coerceIn(0f, 40f),
            bubbleGlassShadow = settings.bubbleGlassShadow.coerceIn(0f, 1f),
        )

        return AppData(
            books = fixedBooks,
            notes = fixedNotesWithGroups,
            bookGroups = bookGroups,
            noteGroups = noteGroups,
            settings = settings,
        )
    }

    private fun load(): AppData {
        return try {
            if (file.exists()) decodeData(file.readText(Charsets.UTF_8))
            else AppData()
        } catch (_: Throwable) {
            try {
                if (backup.exists()) decodeData(backup.readText(Charsets.UTF_8)) else AppData()
            } catch (_: Throwable) {
                AppData()
            }
        }
    }
}


fun sectionBreakText(settings: AppSettings): String {
    val symbol = when (settings.sectionStyle) {
        "line" -> "——"
        "dots" -> "·　·　·"
        "blank" -> ""
        "custom" -> settings.sectionSymbol.ifBlank { "＊　＊　＊" }
        else -> "＊　＊　＊"
    }
    return if (symbol.isBlank()) "\n\n\n" else "\n\n$symbol\n\n"
}

fun backupJson(data: AppData): String = encodeData(data)

fun parseBackup(text: String): AppData = decodeData(text)

fun uuid(): String = UUID.randomUUID().toString()
fun now(): Long = System.currentTimeMillis()
fun todayKey(): String = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

const val REMINDER_LINE_MARK = "◷"

fun isReminderLine(line: String): Boolean =
    line.trimStart().startsWith(REMINDER_LINE_MARK)

fun contentWithoutReminders(text: String): String =
    text.lineSequence().filterNot { isReminderLine(it) }.joinToString("\n")

fun countWords(text: String): Int {
    if (text.isBlank()) return 0
    var count = 0
    contentWithoutReminders(text).forEach { c ->
        if (!c.isWhitespace()) count++
    }
    return count
}

fun formatWords(n: Int): String = when {
    n >= 100000 -> String.format("%.1f万", n / 10000f)
    n >= 10000 -> String.format("%.1f万", n / 10000f)
    else -> n.toString()
}

fun relativeTime(time: Long): String {
    val diff = now() - time
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        diff < 172_800_000 -> "昨天"
        diff < 604_800_000 -> "${diff / 86_400_000} 天前"
        else -> java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("M月d日"))
    }
}

fun formatDateTime(ms: Long): String {
    val d = java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
    return d.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
}

fun formatTime(ms: Long): String {
    val d = java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
    return d.format(DateTimeFormatter.ofPattern("HH:mm"))
}

/** Encoding is intentionally plain JSON so a backup stays readable and version tolerant. */
private fun encodeData(data: AppData): String {
    val root = JSONObject()
    root.put("version", 1)
    root.put("lastBookId", data.lastBookId ?: JSONObject.NULL)
    root.put("lastChapterId", data.lastChapterId ?: JSONObject.NULL)
    root.put("settings", encodeSettings(data.settings))
    val daily = JSONObject()
    data.dailyWords.forEach { (k, v) -> daily.put(k, v) }
    root.put("dailyWords", daily)
    val books = JSONArray()
    data.books.forEach { b ->
        val chapters = JSONArray()
        b.chapters.forEach { c ->
            chapters.put(JSONObject().apply {
                put("id", c.id); put("title", c.title); put("content", c.content)
                put("createdAt", c.createdAt); put("updatedAt", c.updatedAt)
                put("noteIds", JSONArray(c.noteIds))
                put("folderIds", JSONArray(c.folderIds))
            })
        }
        books.put(JSONObject().apply {
            put("id", b.id); put("title", b.title); put("author", b.author)
            put("genre", b.genre); put("synopsis", b.synopsis)
            put("coverStyle", b.coverStyle); put("coverUri", b.coverUri ?: JSONObject.NULL)
            put("chapters", chapters); put("createdAt", b.createdAt)
            put("updatedAt", b.updatedAt); put("pinned", b.pinned)
            put("groupId", b.groupId ?: JSONObject.NULL)
            put("bible", encodeBible(b.bible))
        })
    }
    root.put("books", books)
    root.put("bookGroups", encodeGroups(data.bookGroups))
    root.put("noteGroups", encodeGroups(data.noteGroups))
    val notes = JSONArray()
    data.notes.forEach { n ->
        val todos = JSONArray()
        n.todos.forEach { t ->
            todos.put(JSONObject().apply {
                put("id", t.id); put("text", t.text); put("done", t.done)
                put("remindAt", t.remindAt ?: JSONObject.NULL); put("notified", t.notified)
                put("start", t.start); put("end", t.end)
            })
        }
        notes.put(JSONObject().apply {
            put("id", n.id); put("title", n.title); put("content", n.content)
            put("todos", todos); put("createdAt", n.createdAt)
            put("updatedAt", n.updatedAt); put("pinned", n.pinned)
            put("groupId", n.groupId ?: JSONObject.NULL)
            put("chapterIds", JSONArray(n.chapterIds))
        })
    }
    root.put("notes", notes)
    return root.toString(2)
}

private fun encodeGroups(groups: List<Group>): JSONArray {
    val arr = JSONArray()
    groups.forEach { g ->
        arr.put(JSONObject().apply {
            put("id", g.id); put("name", g.name); put("color", g.color); put("order", g.order)
        })
    }
    return arr
}

/** 故事圣经的编码。四个 list 全写；空对象也写，读回来是空 bible，不做字段猜测。 */
internal fun encodeBible(b: BookBible): JSONObject = JSONObject().apply {
    val cs = JSONArray()
    b.characters.forEach { c ->
        cs.put(JSONObject().apply {
            put("id", c.id); put("name", c.name); put("role", c.role); put("note", c.note)
            put("origin", c.origin); put("updatedAt", c.updatedAt)
            put("color", c.color ?: JSONObject.NULL)
            // 3.26.59：traits / faction 空串也照写（与 role / note 同口径：空 = 没填，不靠缺键表达）。
            // 旧版本 / 旧备份会忽略这两个键，不会读坏已有字段。
            put("traits", c.traits); put("faction", c.faction)
        })
    }
    val rs = JSONArray()
    b.relations.forEach { r ->
        rs.put(JSONObject().apply {
            put("id", r.id); put("from", r.from); put("to", r.to)
            // 3.26.49：kind 与 kindLabel 一起写。新版本两个都读；旧版本只认 kind（枚举名），
            // 内置类型不会读丢；kindColor 只在自定义类型上有值。
            // 3.26.50：kindTier 也写（null = 按 kind 派生）；旧版本忽略这个键。
            put("kind", r.kind.name)
            put("kindLabel", r.kindText)
            put("kindColor", r.kindColor ?: JSONObject.NULL)
            put("kindTier", r.kindTier ?: JSONObject.NULL)
            put("origin", r.origin); put("updatedAt", r.updatedAt)
        })
    }
    val es = JSONArray()
    b.events.forEach { e ->
        es.put(JSONObject().apply {
            put("id", e.id); put("whenText", e.whenText); put("title", e.title)
            put("note", e.note)
            // 3.26.21：多章归属写 chapterIds；chapterId 只写第一个值，供旧备份/旧版本兼容。
            put("chapterIds", JSONArray(e.chapterIds))
            put("chapterId", e.chapterIds.firstOrNull() ?: JSONObject.NULL)
            put("characterIds", JSONArray(e.characterIds)); put("origin", e.origin); put("updatedAt", e.updatedAt)
        })
    }
    val os = JSONArray()
    b.outline.forEach { n ->
        val bs = JSONArray()
        n.beats.forEach { t ->
            bs.put(JSONObject().apply {
                put("id", t.id); put("text", t.text); put("done", t.done)
            })
        }
        os.put(JSONObject().apply {
            put("id", n.id); put("chapterId", n.chapterId ?: JSONObject.NULL)
            put("title", n.title); put("beats", bs)
            put("origin", n.origin); put("updatedAt", n.updatedAt)
        })
    }
    put("characters", cs); put("relations", rs); put("events", es); put("outline", os)
}

internal fun decodeBible(o: JSONObject?): BookBible {
    if (o == null) return BookBible()
    val chars = mutableListOf<StoryCharacter>()
    val ca = o.optJSONArray("characters") ?: JSONArray()
    for (i in 0 until ca.length()) {
        val c = ca.optJSONObject(i) ?: continue
        chars += StoryCharacter(
            id = c.optString("id", uuid()),
            name = c.optString("name", ""),
            role = c.optString("role", ""),
            note = c.optString("note", ""),
            color = if (c.isNull("color")) null else c.optString("color").takeIf { it.isNotBlank() },
            origin = c.optString("origin", "author"),
            updatedAt = c.optLong("updatedAt", 0L),
            // 3.26.59：老备份没有这两个键 —— optString 按空串兜底，不抛异常、不丢整条角色。
            traits = c.optString("traits", ""),
            faction = c.optString("faction", ""),
        )
    }
    val rels = mutableListOf<StoryRelation>()
    val ra = o.optJSONArray("relations") ?: JSONArray()
    for (i in 0 until ra.length()) {
        val r = ra.optJSONObject(i) ?: continue
        val from = r.optString("from", "")
        val to = r.optString("to", "")
        if (from.isBlank() || to.isBlank() || from == to) continue
        // 3.26.49：老备份没有 kindLabel —— 从 kind 枚举派生显示名（内置类型的映射原样保留）；
        // kindColor 缺 / 非法 = null（按内置档位取色），不让坏颜色进数据。
        // 3.26.50：kindTier 缺 / 非法 = null（按 kind 派生档位），旧数据外观与 3.26.49 一致。
        val relKind = RelationKind.entries.firstOrNull { it.name == r.optString("kind") } ?: RelationKind.OTHER
        val relLabel = r.optString("kindLabel").trim().ifBlank { relKind.label }
        rels += StoryRelation(
            id = r.optString("id", uuid()),
            from = from,
            to = to,
            kind = relKind,
            kindLabel = relLabel,
            kindColor = if (r.isNull("kindColor")) null else normalizeRelationColor(r.optString("kindColor")),
            kindTier = decodeRelationTier(r.opt("kindTier")),
            origin = r.optString("origin", "author"),
            updatedAt = r.optLong("updatedAt", 0L),
        )
    }
    val events = mutableListOf<StoryEvent>()
    val ea = o.optJSONArray("events") ?: JSONArray()
    for (i in 0 until ea.length()) {
        val e = ea.optJSONObject(i) ?: continue
        // 3.26.21：优先读 chapterIds；旧数据只有单个 chapterId，读成单元素列表。
        val ids = decodeStringList(e, "chapterIds").ifEmpty {
            val single = if (e.isNull("chapterId")) null else e.optString("chapterId")
            if (single.isNullOrBlank()) emptyList() else listOf(single)
        }
        events += StoryEvent(
            id = e.optString("id", uuid()),
            whenText = e.optString("whenText", ""),
            title = e.optString("title", ""),
            note = e.optString("note", ""),
            chapterIds = ids,
            characterIds = decodeStringList(e, "characterIds"),
            origin = e.optString("origin", "author"),
            updatedAt = e.optLong("updatedAt", 0L),
        )
    }
    val outline = mutableListOf<OutlineNode>()
    val oa = o.optJSONArray("outline") ?: JSONArray()
    for (i in 0 until oa.length()) {
        val n = oa.optJSONObject(i) ?: continue
        val beats = mutableListOf<OutlineBeat>()
        val ba = n.optJSONArray("beats") ?: JSONArray()
        for (j in 0 until ba.length()) {
            val t = ba.optJSONObject(j) ?: continue
            beats += OutlineBeat(
                id = t.optString("id", uuid()),
                text = t.optString("text", ""),
                done = t.optBoolean("done", false),
            )
        }
        outline += OutlineNode(
            id = n.optString("id", uuid()),
            chapterId = if (n.isNull("chapterId")) null else n.optString("chapterId"),
            title = n.optString("title", ""),
            beats = beats,
            origin = n.optString("origin", "author"),
            updatedAt = n.optLong("updatedAt", 0L),
        )
    }
    // 悬空引用清一遍：关系端点、事件里的角色必须还在角色表里，否则图上会画出空节点。
    val ids = chars.map { it.id }.toSet()
    return BookBible(
        characters = chars,
        relations = rels.filter { it.from in ids && it.to in ids },
        events = events.map { it.copy(characterIds = it.characterIds.filter { id -> id in ids }) },
        outline = outline,
    )
}

private fun decodeGroups(a: JSONArray?): List<Group> {
    val out = mutableListOf<Group>()
    if (a == null) return out
    for (i in 0 until a.length()) {
        val o = a.optJSONObject(i) ?: continue
        out += Group(
            id = o.optString("id", uuid()),
            name = o.optString("name", "新分组"),
            color = o.optLong("color", 0xFF8A7A62),
            order = o.optInt("order", i),
        )
    }
    return out
}

private fun encodeSettings(s: AppSettings): JSONObject = JSONObject().apply {
    put("theme", s.theme); put("accent", s.accent); put("font", s.font)
    put("fontScale", s.fontScale.toDouble()); put("lineHeight", s.lineHeight.toDouble())
    put("pageMargin", s.pageMargin.toDouble())
    put("glow", s.glow); put("grain", s.grain)
    put("motion", s.motion); put("pageMotion", s.pageMotion)
    put("libraryMode", s.libraryMode); put("noteMode", s.noteMode)
    put("librarySort", s.librarySort); put("columns", s.columns)
    put("cardScale", s.cardScale.toDouble()); put("coverRadius", s.coverRadius.toDouble())
    put("showTabLabels", s.showTabLabels)
            put("dockOpen", s.dockOpen)
    put("tabBlur", s.tabBlur.toDouble()); put("tabRefraction", s.tabRefraction.toDouble())
    put("tabDispersion", s.tabDispersion); put("tabShadow", s.tabShadow.toDouble())
    put("tabBarOpacity", s.tabBarOpacity.toDouble()); put("tabLensOpacity", s.tabLensOpacity.toDouble())
    put("tabEdge", s.tabEdge.toDouble()); put("lensEdge", s.lensEdge.toDouble())
    put("lensRefraction", s.lensRefraction.toDouble()); put("lensDispersion", s.lensDispersion)
    put("lensBlur", s.lensBlur.toDouble())
    put("lensShadow", s.lensShadow.toDouble())
    put("glassRefract", s.glassRefract.toDouble()); put("glassMeniscus", s.glassMeniscus.toDouble())
    put("glassEdgeBlur", s.glassEdgeBlur.toDouble()); put("glassRim", s.glassRim.toDouble())
    put("glassDisperse", s.glassDisperse); put("glassVeil", s.glassVeil.toDouble())
    put("glassVeilMigrated", s.glassVeilMigrated)
    // 3.26.67：弯折/边缘压缩从一对全局改为按部件各一对（标签栏 / 镜片 / 通知）。
    // 旧全局键 glassBend/glassSqueeze 不再写回；旧备份缺新键 → 解码落 AppSettings 默认。
    put("tabBend", s.tabBend.toDouble()); put("tabSqueeze", s.tabSqueeze.toDouble())
    put("lensBend", s.lensBend.toDouble()); put("lensSqueeze", s.lensSqueeze.toDouble())
    put("bubbleBend", s.bubbleBend.toDouble()); put("bubbleSqueeze", s.bubbleSqueeze.toDouble())
    put("tabNames", JSONArray(s.tabNames))
    put("hiddenTabs", JSONArray(s.hiddenTabs))
    put("dailyGoal", s.dailyGoal); put("indent", s.indent)
    put("sectionStyle", s.sectionStyle); put("sectionSymbol", s.sectionSymbol)
    put("autoSave", s.autoSave); put("showWordCount", s.showWordCount)
    put("showStats", s.showStats); put("haptics", s.haptics)
    put("appName", s.appName); put("showFps", s.showFps)
    put("autoUpdate", s.autoUpdate); put("paperStyle", s.paperStyle)
    put("paperLineAlpha", s.paperLineAlpha.toDouble())
    put("onboardingDone", s.onboardingDone)
    put("tutorialDone", JSONArray(s.tutorialDone))
    put("noteFloat", s.noteFloat)
    put("noteFloatX", s.noteFloatX.toDouble()); put("noteFloatY", s.noteFloatY.toDouble())
    put("noteFloatW", s.noteFloatW.toDouble()); put("noteFloatH", s.noteFloatH.toDouble())
    put("aiEnabled", s.aiEnabled)
    put("aiBaseUrl", s.aiBaseUrl); put("aiModel", s.aiModel)
    put("aiTopK", s.aiTopK); put("aiContextChars", s.aiContextChars)
    put("aiTemperature", s.aiTemperature.toDouble())
    put("aiAutoMemory", s.aiAutoMemory)
    put("aiBibleSync", s.aiBibleSync)
    put("aiQuoteMaxChars", s.aiQuoteMaxChars)
    put("aiMemoryLimit", s.aiMemoryLimit)
    put("aiWeb", s.aiWeb)
    put("aiWebMax", s.aiWebMax)
    put("aiToolTrace", s.aiToolTrace)
    put("aiDeepThink", s.aiDeepThink)
    put("aiWriteMode", s.aiWriteMode)
    put("noteContextCount", s.noteContextCount)
    put("noteMaxTokens", s.noteMaxTokens)
    put("fieldGlassOpacity", s.fieldGlassOpacity.toDouble())
    put("fieldGlassBlur", s.fieldGlassBlur.toDouble())
    put("fieldGlassEdge", s.fieldGlassEdge.toDouble())
    put("fieldGlassRefraction", s.fieldGlassRefraction.toDouble())
    put("fieldGlassShadow", s.fieldGlassShadow.toDouble())
    put("fieldGlassDispersion", s.fieldGlassDispersion)
    put("btnGlassOpacity", s.btnGlassOpacity.toDouble())
    put("btnGlassBlur", s.btnGlassBlur.toDouble())
    put("btnGlassEdge", s.btnGlassEdge.toDouble())
    put("btnGlassRefraction", s.btnGlassRefraction.toDouble())
    put("btnGlassShadow", s.btnGlassShadow.toDouble())
    put("btnGlassDispersion", s.btnGlassDispersion)
    put("bubbleGlassOpacity", s.bubbleGlassOpacity.toDouble())
    put("bubbleGlassBlur", s.bubbleGlassBlur.toDouble())
    put("bubbleGlassEdge", s.bubbleGlassEdge.toDouble())
    put("bubbleGlassRefraction", s.bubbleGlassRefraction.toDouble())
    put("bubbleGlassShadow", s.bubbleGlassShadow.toDouble())
    put("bubbleGlassDispersion", s.bubbleGlassDispersion)
}

private fun decodeStringList(o: JSONObject?, key: String): List<String> {
    val a = o?.optJSONArray(key) ?: return emptyList()
    val out = mutableListOf<String>()
    for (i in 0 until a.length()) {
        val v = a.optString(i, "")
        if (v.isNotBlank() && v !in out) out += v
    }
    return out
}

/**
 * 3.26.61 发给用户的 glassVeil 旧默认。只在 [decodeSettings] 的一次性迁移里用；
 * 新默认仍由 `AppSettings.glassVeil`（.30f）单源给出，这里不重复写。
 */
private const val LEGACY_GLASS_VEIL = .84f

private fun decodeSettings(o: JSONObject?): AppSettings {
    // 3.26.53：兜底值统一取自 AppSettings 的默认。3.26.52 把默认外观换成用户真机那套之后，
    // 这里还是旧字面量（paper / 0xFF22221E / serif / tabBlur=8 …），极老备份缺字段会落回旧外观。
    // 只换兜底，解码逻辑与字段名不变。
    val d = AppSettings()
    // 3.26.62：glassVeil 默认 .84 → .30（降纸色罩 + 加真模糊）。3.26.61 已经把 .84 当
    // 默认值写进用户设备，升级后要先把「旧默认」一次性读成新默认，否则老用户看不见
    // 这次改动。判据三件：文件里有 glassVeil、没有 glassVeilMigrated 键（3.26.61 写的
    // 文件没有这个键）、值恰好是旧默认 .84。只迁一次——解码后 glassVeilMigrated 一律
    // 置 true 并落盘，之后作者自己把纸色罩调回 84% 不会再被改。
    // 代价：3.26.61 里恰好手动调成 84% 的作者也会被这一次迁移改到 30%；旧默认与手动
    // 值都是 .84，没有更多信息可区分，这是「只迁一次」能接受的最小代价。
    val legacyVeil = o != null &&
        o.has("glassVeil") &&
        !o.has("glassVeilMigrated") &&
        o.optDouble("glassVeil", d.glassVeil.toDouble()).toFloat() == LEGACY_GLASS_VEIL
    val veil = if (legacyVeil) {
        d.glassVeil
    } else {
        (o?.optDouble("glassVeil", d.glassVeil.toDouble()) ?: d.glassVeil.toDouble()).toFloat()
    }
    // 3.26.67：弯折/边缘压缩从「一对全局」改为「按部件各一对」。3.26.66 已提交过、
    // 旧键 `glassBend` / `glassSqueeze` 可能已落盘：迁移成**标签栏**那一对（旧值当年
    // 就是条与镜片共用的口径），镜片/通知取默认；新键存在时优先。编码只写新键，
    // 旧键读到即不再使用，也没有第二次迁移。
    val legacyBend: Float? = o?.let { if (it.has("glassBend")) it.optDouble("glassBend").toFloat() else null }
    val legacySqueezeRaw: Float? = o?.let { if (it.has("glassSqueeze")) it.optDouble("glassSqueeze").toFloat() else null }
    // 旧 squeeze 的量纲是「×H」，新量纲是「×B（未乘 bend 的基准）」：
    // 旧式 A = H_old × sq_old = B × bend_old × sq_old → 新 squeeze = bend_old × sq_old。
    // 旧默认 2.2 × 1.0 = 2.2 = 新默认 → 升级后观感不变；调过旧滑杆的力度也按比例保留。
    // （旧 A 可以超过 .55×minSide 封顶、新 A 会被封顶，极值场景单向收敛，属可接受代价。）
    val legacySqueeze: Float? = legacySqueezeRaw?.let { sq -> (legacyBend ?: d.tabBend) * sq }
    fun pick(k: String, fallback: Float, legacy: Float?): Float =
        (o?.optDouble(k, legacy?.toDouble() ?: fallback.toDouble()) ?: fallback.toDouble()).toFloat()
    return AppSettings(
        theme = o?.optString("theme", d.theme) ?: d.theme,
        accent = o?.optLong("accent", d.accent) ?: d.accent,
        font = o?.optString("font", d.font) ?: d.font,
        fontScale = (o?.optDouble("fontScale", d.fontScale.toDouble()) ?: d.fontScale.toDouble()).toFloat(),
        lineHeight = (o?.optDouble("lineHeight", d.lineHeight.toDouble()) ?: d.lineHeight.toDouble()).toFloat(),
        pageMargin = (o?.optDouble("pageMargin", d.pageMargin.toDouble()) ?: d.pageMargin.toDouble()).toFloat(),
        glow = o?.optBoolean("glow", d.glow) ?: d.glow,
        grain = o?.optBoolean("grain", d.grain) ?: d.grain,
        motion = o?.optString("motion", d.motion) ?: d.motion,
        pageMotion = o?.optString("pageMotion", d.pageMotion) ?: d.pageMotion,
        libraryMode = o?.optString("libraryMode", d.libraryMode) ?: d.libraryMode,
        noteMode = o?.optString("noteMode", d.noteMode) ?: d.noteMode,
        librarySort = o?.optString("librarySort", d.librarySort) ?: d.librarySort,
        columns = o?.optInt("columns", d.columns) ?: d.columns,
        cardScale = (o?.optDouble("cardScale", d.cardScale.toDouble()) ?: d.cardScale.toDouble()).toFloat(),
        coverRadius = (o?.optDouble("coverRadius", d.coverRadius.toDouble()) ?: d.coverRadius.toDouble()).toFloat(),
        showTabLabels = o?.optBoolean("showTabLabels", d.showTabLabels) ?: d.showTabLabels,
        dockOpen = o?.optBoolean("dockOpen", d.dockOpen) ?: d.dockOpen,
        tabBlur = (o?.optDouble("tabBlur", d.tabBlur.toDouble()) ?: d.tabBlur.toDouble()).toFloat(),
        tabRefraction = (o?.optDouble("tabRefraction", d.tabRefraction.toDouble()) ?: d.tabRefraction.toDouble()).toFloat(),
        tabDispersion = o?.optBoolean("tabDispersion", d.tabDispersion) ?: d.tabDispersion,
        tabShadow = (o?.optDouble("tabShadow", d.tabShadow.toDouble()) ?: d.tabShadow.toDouble()).toFloat(),
        tabBarOpacity = (o?.optDouble("tabBarOpacity", d.tabBarOpacity.toDouble()) ?: d.tabBarOpacity.toDouble()).toFloat().coerceIn(0f, 1f),
        tabLensOpacity = (o?.optDouble("tabLensOpacity", d.tabLensOpacity.toDouble()) ?: d.tabLensOpacity.toDouble()).toFloat().coerceIn(0f, 1f),
        tabEdge = (o?.optDouble("tabEdge", d.tabEdge.toDouble()) ?: d.tabEdge.toDouble()).toFloat().coerceIn(0f, 1f),
        lensEdge = (o?.optDouble("lensEdge", d.lensEdge.toDouble()) ?: d.lensEdge.toDouble()).toFloat().coerceIn(0f, 1f),
        lensRefraction = (o?.optDouble("lensRefraction", d.lensRefraction.toDouble()) ?: d.lensRefraction.toDouble()).toFloat(),
        lensBlur = (o?.optDouble("lensBlur", d.lensBlur.toDouble()) ?: d.lensBlur.toDouble()).toFloat(),
        lensDispersion = o?.optBoolean("lensDispersion", d.lensDispersion) ?: d.lensDispersion,
        lensShadow = (o?.optDouble("lensShadow", d.lensShadow.toDouble()) ?: d.lensShadow.toDouble()).toFloat(),
        // 3.26.61 玻璃六项：旧备份没有这些键 → 落到 AppSettings 的默认（不迁移旧
        // tab*/lens* 的值）；显式值一律夹进 0..1，坏数据不会让滑杆读数越界。
        glassRefract = (o?.optDouble("glassRefract", d.glassRefract.toDouble()) ?: d.glassRefract.toDouble()).toFloat().coerceIn(0f, 1f),
        glassMeniscus = (o?.optDouble("glassMeniscus", d.glassMeniscus.toDouble()) ?: d.glassMeniscus.toDouble()).toFloat().coerceIn(0f, 1f),
        glassEdgeBlur = (o?.optDouble("glassEdgeBlur", d.glassEdgeBlur.toDouble()) ?: d.glassEdgeBlur.toDouble()).toFloat().coerceIn(0f, 1f),
        glassRim = (o?.optDouble("glassRim", d.glassRim.toDouble()) ?: d.glassRim.toDouble()).toFloat().coerceIn(0f, 1f),
        glassDisperse = o?.optBoolean("glassDisperse", d.glassDisperse) ?: d.glassDisperse,
        glassVeil = veil.coerceIn(0f, 1f),
        // 解码后一律 true：迁移只可能发生一次，下次落盘会把 glassVeilMigrated 键
        // 写进文件；3.26.61 的旧文件（无此键）因此只会被迁一次。
        glassVeilMigrated = true,
        // 3.26.67：弯折/边缘压缩（按部件 3 对）。缺键 → AppSettings 默认（2.2 / 2.2）；
        // 标签栏那一对还会读 3.26.66 的旧全局键（迁移）；显式坏值夹回滑杆量程。
        tabBend = pick("tabBend", d.tabBend, legacyBend).coerceIn(GLASS_BEND_MIN, GLASS_BEND_MAX),
        tabSqueeze = pick("tabSqueeze", d.tabSqueeze, legacySqueeze)
            .coerceIn(GLASS_SQUEEZE_MIN, GLASS_SQUEEZE_MAX),
        lensBend = pick("lensBend", d.lensBend, null).coerceIn(GLASS_BEND_MIN, GLASS_BEND_MAX),
        lensSqueeze = pick("lensSqueeze", d.lensSqueeze, null)
            .coerceIn(GLASS_SQUEEZE_MIN, GLASS_SQUEEZE_MAX),
        bubbleBend = pick("bubbleBend", d.bubbleBend, null).coerceIn(GLASS_BEND_MIN, GLASS_BEND_MAX),
        bubbleSqueeze = pick("bubbleSqueeze", d.bubbleSqueeze, null)
            .coerceIn(GLASS_SQUEEZE_MIN, GLASS_SQUEEZE_MAX),
        tabNames = o?.optJSONArray("tabNames")?.let { a -> (0 until a.length()).map { a.optString(it) } }
            ?: d.tabNames,
        hiddenTabs = o?.optJSONArray("hiddenTabs")
            ?.let { a -> (0 until a.length()).map { a.optString(it) } }
            ?.filter { it in setOf("library", "notes", "stats") }
            ?: d.hiddenTabs,
        dailyGoal = o?.optInt("dailyGoal", d.dailyGoal) ?: d.dailyGoal,
        indent = o?.optBoolean("indent", d.indent) ?: d.indent,
        sectionStyle = o?.optString("sectionStyle", d.sectionStyle) ?: d.sectionStyle,
        sectionSymbol = o?.optString("sectionSymbol", d.sectionSymbol) ?: d.sectionSymbol,
        autoSave = o?.optInt("autoSave", d.autoSave) ?: d.autoSave,
        showWordCount = o?.optBoolean("showWordCount", d.showWordCount) ?: d.showWordCount,
        showStats = o?.optBoolean("showStats", d.showStats) ?: d.showStats,
        haptics = o?.optBoolean("haptics", d.haptics) ?: d.haptics,
        appName = o?.optString("appName", d.appName) ?: d.appName,
        showFps = o?.optBoolean("showFps", d.showFps) ?: d.showFps,
        autoUpdate = o?.optBoolean("autoUpdate", d.autoUpdate) ?: d.autoUpdate,
        // 缺字段落到新默认（solid）；显式旧值 "plain" 仍按原样读，不动已有数据。
        // o == null（整份 settings 对象都没有）时 when 会落到 else，所以单独给出 null 分支。
        paperStyle = when (o?.optString("paperStyle", d.paperStyle)) {
            "ruled" -> "ruled"
            "solid" -> "solid"
            "plain" -> "plain"
            null -> d.paperStyle
            else -> "plain"
        },
        paperLineAlpha = (o?.optDouble("paperLineAlpha", d.paperLineAlpha.toDouble()) ?: d.paperLineAlpha.toDouble()).toFloat(),
        onboardingDone = o?.optBoolean("onboardingDone", d.onboardingDone) ?: d.onboardingDone,
        // 旧备份没有 tutorialDone：空列表 = 一组都没教过。解码走已有的容错列表读取，
        // 坏值/空串/重复项都会被压掉，不会让整份设置读失败。
        tutorialDone = decodeStringList(o, "tutorialDone"),
        noteFloat = o?.optBoolean("noteFloat", d.noteFloat) ?: d.noteFloat,
        noteFloatX = (o?.optDouble("noteFloatX", d.noteFloatX.toDouble()) ?: d.noteFloatX.toDouble()).toFloat().coerceIn(0f, 1f),
        noteFloatY = (o?.optDouble("noteFloatY", d.noteFloatY.toDouble()) ?: d.noteFloatY.toDouble()).toFloat().coerceIn(0f, 1f),
        noteFloatW = (o?.optDouble("noteFloatW", d.noteFloatW.toDouble()) ?: d.noteFloatW.toDouble()).toFloat().coerceIn(.34f, .60f),
        noteFloatH = (o?.optDouble("noteFloatH", d.noteFloatH.toDouble()) ?: d.noteFloatH.toDouble()).toFloat().coerceIn(.24f, .86f),
        aiEnabled = o?.optBoolean("aiEnabled", d.aiEnabled) ?: d.aiEnabled,
        aiBaseUrl = o?.optString("aiBaseUrl", d.aiBaseUrl) ?: d.aiBaseUrl,
        aiModel = o?.optString("aiModel", d.aiModel) ?: d.aiModel,
        aiTopK = (o?.optInt("aiTopK", d.aiTopK) ?: d.aiTopK).coerceIn(1, 8),
        aiContextChars = (o?.optInt("aiContextChars", d.aiContextChars) ?: d.aiContextChars).coerceIn(400, 8000),
        aiTemperature = (o?.optDouble("aiTemperature", d.aiTemperature.toDouble()) ?: d.aiTemperature.toDouble()).toFloat().coerceIn(0f, 1.4f),
        aiAutoMemory = o?.optBoolean("aiAutoMemory", d.aiAutoMemory) ?: d.aiAutoMemory,
        aiBibleSync = o?.optBoolean("aiBibleSync", d.aiBibleSync) ?: d.aiBibleSync,
        aiQuoteMaxChars = (o?.optInt("aiQuoteMaxChars", d.aiQuoteMaxChars) ?: d.aiQuoteMaxChars).coerceIn(2_000, 60_000),
        aiMemoryLimit = (o?.optInt("aiMemoryLimit", d.aiMemoryLimit) ?: d.aiMemoryLimit).coerceIn(20, 500),
        aiWeb = o?.optBoolean("aiWeb", d.aiWeb) ?: d.aiWeb,
        aiWebMax = (o?.optInt("aiWebMax", d.aiWebMax) ?: d.aiWebMax).coerceIn(1, 5),
        aiToolTrace = o?.optBoolean("aiToolTrace", d.aiToolTrace) ?: d.aiToolTrace,
        aiDeepThink = o?.optBoolean("aiDeepThink", d.aiDeepThink) ?: d.aiDeepThink,
        // 旧备份没有这个字段 = 询问档：宁可多点一次，也不默认替作者改正文。
        aiWriteMode = (o?.optInt("aiWriteMode", d.aiWriteMode) ?: d.aiWriteMode).coerceIn(0, 2),
        noteContextCount = (o?.optInt("noteContextCount", d.noteContextCount) ?: d.noteContextCount)
            .let { if (it == 0) 0 else it.coerceIn(2, 40) },
        noteMaxTokens = (o?.optInt("noteMaxTokens", d.noteMaxTokens) ?: d.noteMaxTokens)
            .let { if (it >= 8000) 8000 else 4000 },
        fieldGlassOpacity = (o?.optDouble("fieldGlassOpacity", d.fieldGlassOpacity.toDouble()) ?: d.fieldGlassOpacity.toDouble()).toFloat().coerceIn(0f, 1f),
        fieldGlassBlur = (o?.optDouble("fieldGlassBlur", d.fieldGlassBlur.toDouble()) ?: d.fieldGlassBlur.toDouble()).toFloat().coerceIn(0f, 40f),
        fieldGlassEdge = (o?.optDouble("fieldGlassEdge", d.fieldGlassEdge.toDouble()) ?: d.fieldGlassEdge.toDouble()).toFloat().coerceIn(0f, 2f),
        fieldGlassRefraction = (o?.optDouble("fieldGlassRefraction", d.fieldGlassRefraction.toDouble()) ?: d.fieldGlassRefraction.toDouble()).toFloat().coerceIn(0f, 40f),
        fieldGlassShadow = (o?.optDouble("fieldGlassShadow", d.fieldGlassShadow.toDouble()) ?: d.fieldGlassShadow.toDouble()).toFloat().coerceIn(0f, 1f),
        fieldGlassDispersion = o?.optBoolean("fieldGlassDispersion", d.fieldGlassDispersion) ?: d.fieldGlassDispersion,
        btnGlassOpacity = (o?.optDouble("btnGlassOpacity", d.btnGlassOpacity.toDouble()) ?: d.btnGlassOpacity.toDouble()).toFloat().coerceIn(0f, 1f),
        btnGlassBlur = (o?.optDouble("btnGlassBlur", d.btnGlassBlur.toDouble()) ?: d.btnGlassBlur.toDouble()).toFloat().coerceIn(0f, 40f),
        btnGlassEdge = (o?.optDouble("btnGlassEdge", d.btnGlassEdge.toDouble()) ?: d.btnGlassEdge.toDouble()).toFloat().coerceIn(0f, 2f),
        btnGlassRefraction = (o?.optDouble("btnGlassRefraction", d.btnGlassRefraction.toDouble()) ?: d.btnGlassRefraction.toDouble()).toFloat().coerceIn(0f, 40f),
        btnGlassShadow = (o?.optDouble("btnGlassShadow", d.btnGlassShadow.toDouble()) ?: d.btnGlassShadow.toDouble()).toFloat().coerceIn(0f, 1f),
        btnGlassDispersion = o?.optBoolean("btnGlassDispersion", d.btnGlassDispersion) ?: d.btnGlassDispersion,
        bubbleGlassOpacity = (o?.optDouble("bubbleGlassOpacity", d.bubbleGlassOpacity.toDouble()) ?: d.bubbleGlassOpacity.toDouble()).toFloat().coerceIn(0f, 1f),
        bubbleGlassBlur = (o?.optDouble("bubbleGlassBlur", d.bubbleGlassBlur.toDouble()) ?: d.bubbleGlassBlur.toDouble()).toFloat().coerceIn(0f, 40f),
        bubbleGlassEdge = (o?.optDouble("bubbleGlassEdge", d.bubbleGlassEdge.toDouble()) ?: d.bubbleGlassEdge.toDouble()).toFloat().coerceIn(0f, 2f),
        bubbleGlassRefraction = (o?.optDouble("bubbleGlassRefraction", d.bubbleGlassRefraction.toDouble()) ?: d.bubbleGlassRefraction.toDouble()).toFloat().coerceIn(0f, 40f),
        bubbleGlassShadow = (o?.optDouble("bubbleGlassShadow", d.bubbleGlassShadow.toDouble()) ?: d.bubbleGlassShadow.toDouble()).toFloat().coerceIn(0f, 1f),
        bubbleGlassDispersion = o?.optBoolean("bubbleGlassDispersion", d.bubbleGlassDispersion) ?: d.bubbleGlassDispersion,
    )
}

private fun decodeData(text: String): AppData {
    val root = JSONObject(text)
    val settings = decodeSettings(root.optJSONObject("settings"))
    val books = mutableListOf<Book>()
    val ba = root.optJSONArray("books") ?: JSONArray()
    for (i in 0 until ba.length()) {
        val o = ba.optJSONObject(i) ?: continue
        val chapters = mutableListOf<Chapter>()
        val ca = o.optJSONArray("chapters") ?: JSONArray()
        for (j in 0 until ca.length()) {
            val c = ca.optJSONObject(j) ?: continue
            chapters += Chapter(
                id = c.optString("id", uuid()),
                title = c.optString("title", chapterFallbackTitle(j)),
                content = c.optString("content", ""),
                createdAt = c.optLong("createdAt", now()),
                updatedAt = c.optLong("updatedAt", now()),
                noteIds = decodeStringList(c, "noteIds"),
                folderIds = decodeStringList(c, "folderIds"),
            )
        }
        books += Book(
            id = o.optString("id", uuid()),
            title = o.optString("title", "未命名"),
            author = o.optString("author", ""),
            genre = o.optString("genre", "小说"),
            synopsis = o.optString("synopsis", ""),
            coverStyle = o.optInt("coverStyle", 0),
            coverUri = if (o.isNull("coverUri")) null else o.optString("coverUri"),
            chapters = chapters,
            createdAt = o.optLong("createdAt", now()),
            updatedAt = o.optLong("updatedAt", now()),
            pinned = o.optBoolean("pinned", false),
            groupId = if (o.isNull("groupId")) null else o.optString("groupId"),
            bible = decodeBible(o.optJSONObject("bible")),
        )
    }
    val notes = mutableListOf<Note>()
    val na = root.optJSONArray("notes") ?: JSONArray()
    for (i in 0 until na.length()) {
        val o = na.optJSONObject(i) ?: continue
        val todos = mutableListOf<Todo>()
        val ta = o.optJSONArray("todos") ?: JSONArray()
        for (j in 0 until ta.length()) {
            val t = ta.optJSONObject(j) ?: continue
            todos += Todo(
                id = t.optString("id", uuid()),
                text = t.optString("text", ""),
                done = t.optBoolean("done", false),
                remindAt = if (t.isNull("remindAt")) null else t.optLong("remindAt"),
                notified = t.optBoolean("notified", false),
                start = t.optInt("start", -1),
                end = t.optInt("end", -1),
            )
        }
        notes += Note(
            id = o.optString("id", uuid()),
            title = o.optString("title", ""),
            content = o.optString("content", ""),
            todos = todos,
            createdAt = o.optLong("createdAt", now()),
            updatedAt = o.optLong("updatedAt", now()),
            pinned = o.optBoolean("pinned", false),
            groupId = if (o.isNull("groupId")) null else o.optString("groupId"),
            chapterIds = decodeStringList(o, "chapterIds"),
        )
    }
    val daily = mutableMapOf<String, Int>()
    root.optJSONObject("dailyWords")?.let { d ->
        d.keys().forEach { k -> daily[k] = d.optInt(k, 0) }
    }
    return AppData(
        books = books,
        notes = notes,
        bookGroups = decodeGroups(root.optJSONArray("bookGroups")),
        noteGroups = decodeGroups(root.optJSONArray("noteGroups")),
        settings = settings,
        dailyWords = daily,
        lastBookId = if (root.isNull("lastBookId")) null else root.optString("lastBookId"),
        lastChapterId = if (root.isNull("lastChapterId")) null else root.optString("lastChapterId"),
    )
}

fun accentFor(accent: Long, dark: Boolean): androidx.compose.ui.graphics.Color {
    // The accent is used on text, icons and the editor dock, so it has to sit
    // close to the theme paper. Raw palette accents were far too loud next to
    // paper (and each theme clashed in its own way), so every accent is pulled
    // towards the paper's own value band: hue kept, saturation -28%, value
    // folded 22% towards the theme's mid tone.
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(accent.toInt(), hsv)
    hsv[1] = (hsv[1] * .72f).coerceIn(0f, 1f)
    val target = if (dark) .68f else .45f
    hsv[2] = (hsv[2] * .78f + target * .22f).coerceIn(0f, 1f)
    return androidx.compose.ui.graphics.Color(AndroidColor.HSVToColor(hsv))
}

fun readableOn(color: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color {
    val luminance = .2126f * color.red + .7152f * color.green + .0722f * color.blue
    return if (luminance > .62f) androidx.compose.ui.graphics.Color(0xFF171713) else androidx.compose.ui.graphics.Color(0xFFF7F3EA)
}
