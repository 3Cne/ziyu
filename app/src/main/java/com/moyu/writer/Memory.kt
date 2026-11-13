package com.moyu.writer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What the AI is allowed to remember about one book, plus the local index that
 * finds the paragraphs worth sending with a question.
 *
 * Three kinds of content, on purpose:
 *  - summaries of chapters and the passage index are CACHES: they are derived
 *    from the body text and can be rebuilt at any time;
 *  - `facts` are the writer's confirmations (a person, a setting, a planted
 *    seed); they cannot be rebuilt from the text, so they are written to disk
 *    and travel with the backup;
 *  - `turns` are the conversation itself.
 *
 * Nothing here is stored in `moyu.json`: that file goes through `normalize()` on
 * every launch, and anything the normalizer does not copy forward would be
 * dropped. One JSON per book under `filesDir/ai/` keeps this layer independent
 * of the body store — a corrupt memory file can never take the manuscript with
 * it.
 */

data class ChapterMemo(
    val chapterId: String,
    val title: String,
    val summary: String,
    val hash: String,
    val updatedAt: Long,
)

data class MemoFact(
    val id: String,
    val kind: String,
    val name: String,
    val detail: String,
    val source: String = "",
    /** Off keeps the entry around but out of the prompt. */
    val enabled: Boolean = true,
    /** Pinned entries survive the size cap. */
    val pinned: Boolean = false,
    val createdAt: Long = 0L,
    val lastUsedAt: Long = 0L,
)

/** One thing the writer pinned to a question: the whole book, a chapter, a note.
 *  `bookId` is empty for notes and for quotes taken from the book that was
 *  current when they were picked; it is set when the writer quotes another book. */
data class QuoteRef(
    val type: String,
    val id: String,
    val label: String,
    val bookId: String = "",
    /** Only for type "text": the transcript message lifted by long-press. */
    val text: String = "",
)

data class MemoTurn(
    val role: String,
    val text: String,
    /** Chapter titles the answer leaned on, so the bubble can offer jumps. */
    val refs: List<String> = emptyList(),
)

data class BookMemo(
    val bookId: String,
    val chapters: List<ChapterMemo> = emptyList(),
    val facts: List<MemoFact> = emptyList(),
    val turns: List<MemoTurn> = emptyList(),
    /** Passages the last question pulled in, kept so the answer can cite them. */
    val updatedAt: Long = 0L,
)

/** One searchable slice of the manuscript. */
data class Passage(val index: Int, val chapter: String, val text: String)

private fun newId(): String = java.util.UUID.randomUUID().toString().take(8)

/**
 * Long-term memory operations: dedupe, cap, and "what goes into the prompt".
 *
 * Memories arrive from a model, so they are noisy by nature: the same fact comes
 * back phrased two ways, and a long book would otherwise grow thousands of
 * entries. Merging on character-bigram overlap is crude but explainable — two
 * entries phrased alike collapse, two genuinely different facts do not.
 */
object MemoryOps {
    const val PROMPT_LIMIT = 12

    /**
     * The memory tree: 大标签 → 小标签. A fact always hangs off a leaf; the big
     * label is derived from it, so no schema change was needed for the tree.
     *
     * Two kinds of memory live here and they answer different questions:
     * 回顾 is what the manuscript already says (who, where, what happened);
     * 规划 is where the writer said the story is going (大纲 / 分纲). Mixing the
     * two made "分纲" the place chapter summaries were dumped, while the model
     * was never asked for a plan at all.
     */
    val TREE: List<Pair<String, List<String>>> = listOf(
        "人物" to listOf("主角", "配角", "关系"),
        "设定" to listOf("世界观", "地点", "物品", "规则"),
        "剧情" to listOf("已发生", "进行中"),
        "时间线" to listOf("事件", "年代"),
        "伏笔" to listOf("已埋", "待收", "已收"),
        "大纲" to listOf("主线", "分卷", "结局"),
        "分纲" to listOf("下一章", "这一卷", "节拍", "章节纲要"),
        "跨书" to listOf("偏好", "习惯", "视角", "节奏", "语气"),
    )

    /** Big labels that describe a plan, not something already on the page. */
    val PLANNING_TOPS: Set<String> = setOf("大纲", "分纲")

    fun isPlanningTop(top: String): Boolean = top in PLANNING_TOPS

    /** A fact is a plan when its branch is 大纲 / 分纲. */
    fun isPlanning(kind: String): Boolean = parentOf(kind) in PLANNING_TOPS

    /** Leaf kinds the extractor may return. */
    val LEAVES: List<String> = TREE.flatMap { it.second }

    /** Big label for a leaf; old coarse kinds (人物/设定/……) are their own group. */
    fun parentOf(kind: String): String =
        TREE.firstOrNull { kind in it.second }?.first
            ?: if (TREE.any { it.first == kind }) kind else "其他"

    /** Kinds that follow the writer across books, not the manuscript. */
    val GLOBAL_KINDS = setOf("偏好", "习惯", "视角", "节奏", "语气")
    /** Fine kinds first; 人物 / 设定 are the older coarse kinds and stay in the
     *  order so entries written before the split keep their home. */
    val BOOK_KINDS = listOf(
        "主角", "配角", "关系", "世界观", "地点", "物品",
        "伏笔", "时间线", "剧情",
        "人物", "设定",
    )

    /** What a prompt should weigh: story identity beats plot summary. */
    private val KIND_WEIGHT = mapOf(
        "主角" to 1.00f, "关系" to .92f, "配角" to .88f,
        "世界观" to .95f, "地点" to .80f, "物品" to .74f,
        "人物" to 1.00f, "设定" to .94f,
        "伏笔" to .90f, "时间线" to .84f, "剧情" to .70f,
        "下一章" to .88f, "这一卷" to .85f, "节拍" to .78f, "章节纲要" to .80f,
        "主线" to .90f, "结局" to .88f, "分卷" to .80f,
        "偏好" to .78f, "习惯" to .68f, "视角" to .72f, "节奏" to .68f, "语气" to .62f,
        "其他" to .55f,
    )

    /**
     * Big-label weights: which branch of the tree a question is likely about
     * before any keyword matches. 人物 and 设定 are the story's identity, so
     * they win the tie; 剧情 is recap and only enters when the question names it.
     */
    private val TOP_WEIGHT = mapOf(
        "人物" to 1.00f, "设定" to .94f, "大纲" to .90f,
        "伏笔" to .88f, "时间线" to .82f, "分纲" to .72f,
        "剧情" to .70f, "跨书" to .74f, "其他" to .55f,
    )

    /**
     * Which branch a short question points at. "这章怎么样" shares almost no
     * characters with a memory entry, so the bigram score collapses to zero and
     * the ranking falls back to branch weights — every question then rides with
     * the same cast of 人物/设定. These hints name the branch the writer just
     * pointed at; a named branch goes ahead of the default order.
     */
    private val TOP_HINTS: List<Pair<String, List<String>>> = listOf(
        "分纲" to listOf("这章", "这段", "这一章", "本章", "这一节", "上一章", "下一章", "开头", "这一段", "接下来", "打算", "往下写"),
        "人物" to listOf("谁", "人物", "动机", "为什么", "性格", "主角", "配角", "关系"),
        "伏笔" to listOf("伏笔", "埋", "线索", "收线", "呼应", "铺垫"),
        "时间线" to listOf("时间线", "先后", "顺序", "之前", "之后", "什么时候", "几年"),
        "剧情" to listOf("剧情", "节奏", "拖沓", "注水", "太长", "太慢", "太快", "走神", "无聊", "重复", "平淡"),
        "大纲" to listOf("大纲", "主线", "结局", "结尾", "收束", "全书", "这一卷", "整本"),
        "设定" to listOf("设定", "规则", "世界观", "地点", "物品", "年代", "背景", "体系"),
    )

    /** Branches the question names outright, in the order they are named. */
    fun hintedTops(question: String): List<String> {
        val q = question.trim()
        if (q.isEmpty()) return emptyList()
        return TOP_HINTS.filter { (_, words) -> words.any { q.contains(it) } }.map { it.first }
    }

    fun isGlobal(kind: String): Boolean = kind in GLOBAL_KINDS

    /** Fixed section order for the memory pane; unknown kinds land in 其他. */
    fun sections(facts: List<MemoFact>): List<Pair<String, List<MemoFact>>> {
        val order = BOOK_KINDS + listOf("其他")
        val grouped = facts.groupBy { if (it.kind in BOOK_KINDS) it.kind else "其他" }
        return order.mapNotNull { k -> grouped[k]?.let { k to it } }.filter { it.second.isNotEmpty() }
    }

    /** Facts rolled up to the big label, in tree order. */
    fun treeGroups(facts: List<MemoFact>, global: Boolean): List<Pair<String, List<Pair<String, List<MemoFact>>>>> =
        TREE.mapNotNull { (top, leaves) ->
            if (global != (top == "跨书")) return@mapNotNull null
            val sub = leaves.mapNotNull { leaf ->
                facts.filter { it.kind == leaf }.takeIf { it.isNotEmpty() }?.let { leaf to it }
            }
            // Old coarse kinds sit under their own name.
            val legacy = facts.filter { it.kind == top }.takeIf { it.isNotEmpty() }?.let { listOf(top to it) }.orEmpty()
            val all = sub + legacy
            if (all.isEmpty()) null else top to all
        }

    private fun tokens(text: String): Set<String> {
        val out = HashSet<String>()
        for (t in text.lowercase().split(Regex("[^\\p{IsAlphabetic}\\p{IsDigit}]+"))) {
            if (t.length < 2) {
                if (t.isNotEmpty()) out += t
                continue
            }
            for (i in 0 until t.length - 1) out += t.substring(i, i + 2)
        }
        return out
    }

    fun similarity(a: String, b: String): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val inter = ta.count { it in tb }
        return inter.toDouble() / (ta.size + tb.size - inter)
    }

    /** Merge new entries into the list; returns the list, capped by `limit`. */
    fun merge(existing: List<MemoFact>, incoming: List<MemoFact>, limit: Int): List<MemoFact> {
        val out = existing.toMutableList()
        for (cand in incoming) {
            val key = cand.name + " " + cand.detail
            val dup = out.firstOrNull { similarity(it.name + " " + it.detail, key) > .8 }
            if (dup != null) {
                // Same fact, newer wording: keep the id (and its pin/disable
                // state) but refresh what it says.
                val i = out.indexOf(dup)
                out[i] = dup.copy(detail = cand.detail, lastUsedAt = System.currentTimeMillis())
                continue
            }
            out += cand
        }
        if (out.size <= limit) return out
        // Over the cap: pinned entries stay, the rest are evicted by "least
        // recently used" — a character sheet used every day must not be pushed
        // out by yesterday's small talk.
        val pinned = out.filter { it.pinned }
        val rest = out.filterNot { it.pinned }.sortedByDescending { it.lastUsedAt }
        return (pinned + rest).take(limit)
    }

    /**
     * Apply extractor output. "new" goes through the usual dedupe; "update"
     * rewrites the detail of the entry it names; "drop" turns it off (kept for
     * the memory pane, out of the prompt). An id the model invented is ignored
     * rather than applied to the wrong entry.
     */
    fun applyProposals(
        existing: List<MemoFact>,
        proposals: List<MemoryExtractor.Proposal>,
        limit: Int,
    ): List<MemoFact> {
        var out = existing
        for (p in proposals) {
            val i = if (p.id.isBlank()) -1 else out.indexOfFirst { it.id == p.id }
            when (p.op) {
                "update" -> if (i >= 0 && p.detail.isNotBlank()) {
                    out = out.toMutableList().also {
                        it[i] = it[i].copy(detail = p.detail, lastUsedAt = System.currentTimeMillis())
                    }
                }
                "drop" -> if (i >= 0) {
                    out = out.toMutableList().also { it[i] = it[i].copy(enabled = false) }
                }
                else -> if (p.name.isNotBlank() && p.detail.isNotBlank()) {
                    out = merge(out, listOf(newFact(p.kind, p.name, p.detail, "对话")), limit)
                }
            }
        }
        return out
    }

    /**
     * The entries that ride along with a prompt, picked along the tree:
     * 大类 first (which branch is this question about), then the 小标签 under
     * it, then the entries. Before this it was one flat score list, so a
     * highly-similar 剧情 recap could outrank the 主角 the writer asked about.
     *
     * A branch the question names outright ("这章" / "伏笔") outranks the branch
     * weights, so a short question no longer gets the default cast.
     *
     * Pinned entries always ride. Cross-book preferences get their own three
     * slots (语气/节奏/视角 first) and never take a manuscript slot. A
     * high-similarity entry from a branch outside the top three is still pulled
     * in as a safety net — the tree must not lose what a flat search would have
     * found.
     */
    fun forPrompt(facts: List<MemoFact>, question: String = ""): List<MemoFact> {
        val usable = facts.filter { it.enabled && it.detail.isNotBlank() }
        if (usable.isEmpty()) return emptyList()
        val q = question.trim()
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        fun staleOf(f: MemoFact) = ((now - f.lastUsedAt) / (day * 30L)).coerceIn(0L, 4L) * .04
        fun relevanceOf(f: MemoFact) = if (q.isEmpty()) 0.0 else similarity(q, f.name + " " + f.detail)
        fun leafWeight(f: MemoFact) = KIND_WEIGHT[f.kind] ?: .6f

        val global = usable.filter { isGlobal(it.kind) }
        val book = usable.filterNot { isGlobal(it.kind) }

        // ① Which 大类 the question is about: the branch it names outright comes
        //    first, then the branch's best entry (damped) decides the rest.
        val hinted = hintedTops(q)
        val byTop = book.groupBy { parentOf(it.kind) }
        val topOrder = byTop.entries
            .sortedWith(
                compareBy<Map.Entry<String, List<MemoFact>>> { if (it.key in hinted) 0 else 1 }
                    .thenBy { hinted.indexOf(it.key).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
                    .thenByDescending { (top, list) ->
                        val best = list.maxOf { f -> relevanceOf(f) }
                        (TOP_WEIGHT[top] ?: .55f) * (0.55 + 0.45 * best)
                    },
            )
            .map { it.key }

        // ② Top three branches, three leaves each, leaf weight first.
        val picked = ArrayList<MemoFact>()
        for (top in topOrder.take(3)) {
            byTop[top].orEmpty()
                .sortedByDescending { f ->
                    val pin = if (f.pinned) 2.5 else 1.0
                    leafWeight(f) * (0.7 + relevanceOf(f)) * pin - staleOf(f)
                }
                .take(3)
                .forEach { picked += it }
        }

        // ③ Pinned entries always ride.
        val pins = book.filter { it.pinned }
        val pinnedIds = pins.map { it.id }.toSet()

        // ④ Safety net: something very close to the question from an
        //    unselected branch still enters (max 2), so the tree never becomes
        //    a blocker for the obvious hit.
        val pickedIds = picked.map { it.id }.toSet()
        val extra = book.filterNot { it.id in pinnedIds || it.id in pickedIds }
            .filter { relevanceOf(it) >= .35 }
            .sortedByDescending { relevanceOf(it) }
            .take(2)

        // ⑤ Cross-book preferences decide how an answer should sound, and that
        //    applies whatever book is open. 语气 / 节奏 / 视角 ride every time;
        //    the remaining slots go to whatever scored highest.
        val globalRanked = global
            .sortedByDescending { leafWeight(it) * (0.7 + relevanceOf(it)) - staleOf(it) }
        val globalPicked = ArrayList<MemoFact>()
        for (k in listOf("语气", "节奏", "视角", "习惯", "偏好")) {
            globalRanked.firstOrNull { it.kind == k && globalPicked.none { p -> p.id == it.id } }
                ?.let { globalPicked += it }
            if (globalPicked.size >= 3) break
        }
        globalRanked.forEach { f ->
            if (globalPicked.size < 3 && globalPicked.none { p -> p.id == f.id }) globalPicked += f
        }

        val out = ArrayList<MemoFact>()
        fun addAll(list: List<MemoFact>) {
            list.forEach { f -> if (out.none { it.id == f.id }) out += f }
        }
        addAll(pins)
        addAll(picked)
        addAll(extra)
        // Three slots are reserved for the writer's own habits, so a book's
        // cast can never crowd them out.
        return out.take(PROMPT_LIMIT - 3) + globalPicked.take(3)
    }

    fun newFact(kind: String, name: String, detail: String, source: String): MemoFact {
        val now = System.currentTimeMillis()
        return MemoFact(
            id = newId(), kind = kind, name = name, detail = detail, source = source,
            enabled = true, pinned = false, createdAt = now, lastUsedAt = now,
        )
    }

    /** Stamp the entries a prompt actually carried, so eviction sees the usage. */
    fun touch(facts: List<MemoFact>, ids: List<String>): List<MemoFact> {
        if (ids.isEmpty()) return facts
        val now = System.currentTimeMillis()
        val set = ids.toSet()
        return facts.map { if (it.id in set) it.copy(lastUsedAt = now) else it }
    }
}

object BookText {
    private const val CHUNK = 320
    private const val OVERLAP = 60

    /**
     * Pack paragraphs into ~320-character slices, then glue the tail of the
     * previous slice onto the next one. The overlap is what lets a question
     * that straddles two paragraphs still find its evidence; 320 was measured
     * against 400/500 on the real manuscript — bigger slices blurred the hit.
     */
    fun passages(book: Book): List<Passage> {
        val out = ArrayList<Passage>()
        for (chapter in book.chapters) {
            val body = chapter.content
            if (body.isBlank()) continue
            val pieces = ArrayList<String>()
            val buf = StringBuilder()
            for (para in body.split(Regex("\\n+"))) {
                val p = para.trim()
                if (p.isEmpty()) continue
                if (buf.length + p.length + 1 <= CHUNK) {
                    if (buf.isNotEmpty()) buf.append('\n')
                    buf.append(p)
                    continue
                }
                if (buf.isNotEmpty()) {
                    pieces += buf.toString()
                    buf.setLength(0)
                }
                if (p.length <= CHUNK) {
                    buf.append(p)
                } else {
                    var start = 0
                    while (start < p.length) {
                        val end = (start + CHUNK).coerceAtMost(p.length)
                        pieces += p.substring(start, end)
                        start = end
                    }
                }
            }
            if (buf.isNotEmpty()) pieces += buf.toString()

            for ((i, piece) in pieces.withIndex()) {
                val text = if (i == 0) piece else pieces[i - 1].takeLast(OVERLAP) + "\n" + piece
                out += Passage(out.size, chapter.title, text)
            }
        }
        return out
    }

    /**
     * Extractive summary used when no model is configured: the opening
     * sentences, capped at 80 characters. Cheap, always available, and good
     * enough to tell two chapters apart in the memory list.
     */
    fun localSummary(body: String, limit: Int = 80): String {
        val flat = body.replace(Regex("\\s+"), " ").trim()
        if (flat.isEmpty()) return ""
        var out = ""
        for (sentence in flat.split(Regex("(?<=[。！？!?])"))) {
            if (out.length + sentence.length > limit) break
            out += sentence
        }
        if (out.isEmpty()) out = flat.take(limit)
        return out.trim()
    }

    fun hashOf(body: String): String = body.hashCode().toString(16)
}

/**
 * BM25 over Chinese bigrams. Measured on the writer's own two chapters:
 * hit@1 80%, hit@3 90%, and clearly ahead of TF-IDF at the same settings.
 * No dependency, no embedding call, and every hit can be explained.
 */
class BookIndex(val passages: List<Passage>) {
    private val termFreq = ArrayList<HashMap<String, Int>>(passages.size)
    private val lengths = IntArray(passages.size)
    private val idf = HashMap<String, Double>()
    private val avgLen: Double

    init {
        val df = HashMap<String, Int>()
        for (p in passages) {
            val tokens = tokenize(p.text)
            val tf = HashMap<String, Int>(tokens.size * 2)
            for (t in tokens) tf[t] = (tf[t] ?: 0) + 1
            termFreq += tf
            lengths[termFreq.size - 1] = tokens.size
            for (t in tf.keys) df[t] = (df[t] ?: 0) + 1
        }
        val n = passages.size
        avgLen = if (n == 0) 1.0 else lengths.sum().toDouble() / n
        for ((term, d) in df) {
            idf[term] = kotlin.math.ln(1.0 + (n - d + 0.5) / (d + 0.5))
        }
    }

    val size: Int get() = passages.size

    fun search(query: String, k: Int): List<Passage> {
        if (passages.isEmpty()) return emptyList()
        val q = tokenize(query)
        if (q.isEmpty()) return emptyList()
        val scores = DoubleArray(passages.size)
        for (i in passages.indices) {
            val tf = termFreq[i]
            val dl = lengths[i].coerceAtLeast(1)
            var s = 0.0
            for (term in q) {
                val f = tf[term] ?: continue
                val id = idf[term] ?: continue
                s += id * f * (K1 + 1) / (f + K1 * (1 - B + B * dl / avgLen))
            }
            scores[i] = s
        }
        return passages.indices
            .filter { scores[it] > 0.0 }
            .sortedByDescending { scores[it] }
            .take(k)
            .map { passages[it] }
    }

    companion object {
        private const val K1 = 1.2
        private const val B = 0.75
        private val TOKEN = Regex("[a-z0-9]+|[\\u4e00-\\u9fff]+")

        private fun tokenize(text: String): List<String> {
            val lower = text.lowercase()
            val out = ArrayList<String>(lower.length)
            for (m in TOKEN.findAll(lower)) {
                val t = m.value
                if (t[0].isAsciiDigit() || t[0] in 'a'..'z') {
                    out += t
                } else if (t.length == 1) {
                    out += t
                } else {
                    for (i in 0 until t.length - 1) out += t.substring(i, i + 2)
                }
            }
            return out
        }

        private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
    }
}

object MemoStore {
    private const val DIR = "ai"

    private fun dir(ctx: Context): File = File(ctx.filesDir, DIR).apply { if (!exists()) mkdirs() }

    private fun file(ctx: Context, bookId: String): File = File(dir(ctx), "$bookId.json")

    fun load(ctx: Context, bookId: String): BookMemo {
        val f = file(ctx, bookId)
        if (!f.exists()) return BookMemo(bookId)
        return try {
            decode(bookId, JSONObject(f.readText(Charsets.UTF_8)))
        } catch (_: Throwable) {
            // A memory file that will not parse is dropped, never merged: the
            // manuscript is the source of truth and the cache rebuilds itself.
            BookMemo(bookId)
        }
    }

    fun save(ctx: Context, memo: BookMemo) {
        runCatching {
            val f = file(ctx, memo.bookId)
            val tmp = File(f.parentFile, "${f.name}.tmp")
            tmp.writeText(encode(memo).toString(), Charsets.UTF_8)
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }
    }

    fun clear(ctx: Context, bookId: String) {
        runCatching { file(ctx, bookId).delete() }
    }

    /**
     * Author-level memory: preferences and habits that should follow the writer
     * into the next book. Same file shape as a book memo, named `_global.json`,
     * so one codec serves both and a corrupt global file cannot touch a book.
     */
    fun loadGlobal(ctx: Context): BookMemo = load(ctx, GLOBAL_ID)

    fun saveGlobal(ctx: Context, memo: BookMemo) = save(ctx, memo.copy(bookId = GLOBAL_ID))

    fun clearGlobal(ctx: Context) {
        runCatching { file(ctx, GLOBAL_ID).delete() }
    }

    const val GLOBAL_ID = "_global"

    private fun encode(memo: BookMemo): JSONObject = JSONObject().apply {
        put("version", 1)
        put("bookId", memo.bookId)
        put("updatedAt", memo.updatedAt)
        put("chapters", JSONArray().apply {
            memo.chapters.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.chapterId); put("title", c.title)
                    put("summary", c.summary); put("hash", c.hash)
                    put("updatedAt", c.updatedAt)
                })
            }
        })
        put("facts", JSONArray().apply {
            memo.facts.forEach { f ->
                put(JSONObject().apply {
                    put("id", f.id); put("kind", f.kind); put("name", f.name)
                    put("detail", f.detail); put("source", f.source)
                    put("enabled", f.enabled); put("pinned", f.pinned)
                    put("createdAt", f.createdAt); put("lastUsedAt", f.lastUsedAt)
                })
            }
        })
        put("turns", JSONArray().apply {
            memo.turns.forEach { t ->
                put(JSONObject().apply {
                    put("role", t.role); put("text", t.text)
                    if (t.refs.isNotEmpty()) put("refs", JSONArray(t.refs))
                })
            }
        })
    }

    private fun decode(bookId: String, o: JSONObject): BookMemo {
        val chapters = ArrayList<ChapterMemo>()
        o.optJSONArray("chapters")?.let { a ->
            for (i in 0 until a.length()) {
                val c = a.optJSONObject(i) ?: continue
                chapters += ChapterMemo(
                    chapterId = c.optString("id"),
                    title = c.optString("title"),
                    summary = c.optString("summary"),
                    hash = c.optString("hash"),
                    updatedAt = c.optLong("updatedAt"),
                )
            }
        }
        val facts = ArrayList<MemoFact>()
        o.optJSONArray("facts")?.let { a ->
            for (i in 0 until a.length()) {
                val f = a.optJSONObject(i) ?: continue
                facts += MemoFact(
                    id = f.optString("id").ifBlank { newId() },
                    kind = f.optString("kind"),
                    name = f.optString("name"),
                    detail = f.optString("detail"),
                    source = f.optString("source"),
                    enabled = f.optBoolean("enabled", true),
                    pinned = f.optBoolean("pinned", false),
                    createdAt = f.optLong("createdAt"),
                    lastUsedAt = f.optLong("lastUsedAt"),
                )
            }
        }
        val turns = ArrayList<MemoTurn>()
        o.optJSONArray("turns")?.let { a ->
            for (i in 0 until a.length()) {
                val t = a.optJSONObject(i) ?: continue
                val role = t.optString("role")
                val text = t.optString("text")
                val refs = ArrayList<String>()
                t.optJSONArray("refs")?.let { ra ->
                    for (k in 0 until ra.length()) {
                        val v = ra.optString(k)
                        if (v.isNotBlank()) refs += v
                    }
                }
                if (role.isNotBlank() && text.isNotBlank()) turns += MemoTurn(role, text, refs)
            }
        }
        return BookMemo(bookId, chapters, facts, turns, o.optLong("updatedAt"))
    }
}

/**
 * Brings a book's memories up to date without a model call: every chapter gets
 * a local summary whose hash says whether the body changed. When a model is
 * configured the caller can overwrite a summary with a better one; the local
 * pass is what keeps the memory tab useful before any key is entered.
 */
fun refreshLocalMemo(book: Book, memo: BookMemo): BookMemo {
    val byId = memo.chapters.associateBy { it.chapterId }
    val chapters = book.chapters.map { c ->
        val hash = BookText.hashOf(c.content)
        val old = byId[c.id]
        if (old != null && old.hash == hash && old.title == c.title) old
        else ChapterMemo(
            chapterId = c.id,
            title = c.title,
            summary = BookText.localSummary(c.content),
            hash = hash,
            updatedAt = System.currentTimeMillis(),
        )
    }
    return memo.copy(chapters = chapters, updatedAt = System.currentTimeMillis())
}
