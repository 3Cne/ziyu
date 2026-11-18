package com.moyu.writer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 3.26.23：章节保存后的自动整理（source = text）。
 *
 * 一次整理 = 一次小请求：把「已有故事圣经 + 刚保存的这一章正文」交给模型，让它只挑出
 * 正文里已经写定的角色 / 关系 / 事件 / 情节点，输出严格 JSON；解析成功就逐条走
 * [AiBible.mutate] 落库——作者手改保护、冲突记录、撤销日志都由 AiBible 负责。
 *
 * 三条边界：
 * - 按 bookId 节流：同一本书 3 分钟内最多真跑一次；窗口内再保存只记 dirty，窗口到了再跑
 *   最新保存的那一章；
 * - 模型输出解析不了就重试一次（同一请求，temperature 降到 0.1），还不行就静默放弃，
 *   不写任何数据；
 * - 自己持有 CoroutineScope（SupervisorJob + Dispatchers.IO），不跟页面生命周期走：
 *   作者保存后立刻退出页面，整理也能跑完。
 *
 * `aiBibleSync` 总开关也在这里判断：关掉时既不排队也不请求（调用方若再判一次，只是重复，
 * 没有副作用）。
 */
object AiBibleSync {
    private const val TAG = "MOYU_BIBLE_SYNC"

    /** 同一本书两次真跑之间的最小间隔。 */
    private const val WINDOW_MS = 3 * 60 * 1000L

    /** 送进 prompt 的正文上限；超了截断并注明。 */
    private const val MAX_CHAPTER_CHARS = 6000

    /** 一次整理返回四类 JSON；思考关掉，免得预算全花在 reasoning 上。 */
    private const val MAX_TOKENS = 3000

    private const val MAX_CHARACTERS = 12
    private const val MAX_RELATIONS = 12
    private const val MAX_EVENTS = 12
    private const val MAX_BEATS = 12

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 所有节流状态都在这把锁下读写；AI 请求本身不在锁里。 */
    private val lock = Any()
    private val states = HashMap<String, BookState>()

    private class BookState {
        /** 上一次真跑结束的时间。 */
        var lastRunAt: Long = 0L
        /** 最近一次保存的章节，等于「窗口到了跑哪一章」。 */
        var pendingChapterId: String? = null
        /** 本轮跑完之后窗口内又保存过，需要再跑一轮。 */
        var dirty: Boolean = false
        /** 已经排上（等待窗口 / 正在跑）的任务；非空且 active 时不再起新协程。 */
        var job: Job? = null
        var running: Boolean = false
    }

    /**
     * 章节保存后调一次。同一本书 3 分钟内的多次调用只算一次，期间只记 dirty；
     * 已经在等窗口就直接返回，不会堆请求。
     */
    fun noteChapterSaved(ctx: Context, store: AppStore, bookId: String, chapterId: String) {
        if (bookId.isBlank() || chapterId.isBlank()) return
        // 快路径：AI 没开、自动整理关着、或者还没存 Key，就别排队
        // （Key 只看文件在不在，解密留给 IO）。
        val settings = store.data.settings
        if (!settings.aiEnabled || !settings.aiBibleSync || !AiKey.has(ctx)) return
        // 持 applicationContext 到协程里：Activity 退出后整理还要继续。
        val appContext = ctx.applicationContext
        synchronized(lock) {
            val state = states.getOrPut(bookId) { BookState() }
            state.pendingChapterId = chapterId
            state.dirty = true
            if (state.job?.isActive == true) {
                Log.d(TAG, "窗口内又保存了一章，先记 dirty：$bookId / $chapterId")
                return
            }
            val wait = (state.lastRunAt + WINDOW_MS - System.currentTimeMillis()).coerceAtLeast(0L)
            state.job = scope.launch {
                if (wait > 0L) delay(wait)
                drain(appContext, store, bookId, state)
            }
        }
    }

    /** UI 可以拿来显示「整理中」；还在排队窗口里也算 true。 */
    fun isSyncing(bookId: String): Boolean = synchronized(lock) {
        val state = states[bookId] ?: return@synchronized false
        state.job?.isActive == true || state.running
    }

    // ───────────────────────── 节流调度 ─────────────────────────

    /**
     * 循环消费 pending：跑完一轮后若窗口内又保存过（dirty），等满 3 分钟再跑下一轮。
     * 收尾时把 job 置空；若刚好在这之间又来了保存，补排一次，不丢触发。
     */
    private suspend fun drain(ctx: Context, store: AppStore, bookId: String, state: BookState) {
        try {
            while (true) {
                val chapterId = synchronized(lock) {
                    state.running = true
                    state.dirty = false
                    state.pendingChapterId
                } ?: break
                try {
                    runOnce(ctx, store, bookId, chapterId)
                } catch (e: Exception) {
                    Log.d(TAG, "整理异常，放弃本轮：${e.message}")
                }
                val more = synchronized(lock) {
                    state.running = false
                    state.lastRunAt = System.currentTimeMillis()
                    state.dirty
                }
                if (!more) break
                // 窗口内又保存了：等满 3 分钟窗口再跑，保证「3 分钟最多一次」。
                delay(WINDOW_MS)
            }
        } finally {
            synchronized(lock) {
                state.running = false
                state.job = null
                if (state.dirty) {
                    val wait = (state.lastRunAt + WINDOW_MS - System.currentTimeMillis())
                        .coerceAtLeast(0L)
                    state.job = scope.launch {
                        if (wait > 0L) delay(wait)
                        drain(ctx, store, bookId, state)
                    }
                }
            }
        }
    }

    // ───────────────────────── 一次整理 ─────────────────────────

    private suspend fun runOnce(ctx: Context, store: AppStore, bookId: String, chapterId: String) {
        val settings = store.data.settings
        if (!settings.aiEnabled || !settings.aiBibleSync) {
            Log.d(TAG, "AI 没开或自动整理已关，跳过整理：$bookId")
            return
        }
        val key = AiKey.load(ctx)
        if (key.isNullOrBlank()) {
            Log.d(TAG, "还没有 AI Key，跳过整理：$bookId")
            return
        }
        val book = store.book(bookId)
        if (book == null) {
            Log.d(TAG, "没找到这本书，跳过整理：$bookId")
            return
        }
        val chapter = book.chapters.firstOrNull { it.id == chapterId }
        if (chapter == null) {
            Log.d(TAG, "没找到这一章，跳过整理：$bookId / $chapterId")
            return
        }
        if (chapter.content.isBlank()) {
            Log.d(TAG, "这一章还没内容，跳过整理：${chapter.title}")
            return
        }
        Log.d(TAG, "开始整理《${book.title}》${chapter.title}（${countWords(chapter.content)} 字）")
        val plan = askPlan(settings, key, buildUser(book, chapter)) ?: return
        if (plan.skip) {
            Log.d(TAG, "本章没有可整理的内容（skip）：${chapter.title}")
            return
        }
        if (plan.isEmpty) {
            Log.d(TAG, "解析结果为空，不写数据：${chapter.title}")
            return
        }
        apply(ctx, store, bookId, chapterId, plan)
    }

    private suspend fun askPlan(settings: AppSettings, key: String, user: String): Plan? {
        val first = chat(settings, key, user, .2f)
        if (!first.ok) {
            Log.d(TAG, "请求失败，放弃本次整理：${first.error}")
            return null
        }
        parse(first.text)?.let { return it }
        // 解析失败重试一次：同一请求，temperature 降到 0.1；再失败就静默放弃，不写数据。
        Log.d(TAG, "输出不是合法 JSON，重试一次：${first.text.take(160)}")
        val second = chat(settings, key, user, .1f)
        if (!second.ok) {
            Log.d(TAG, "重试请求失败，放弃本次整理：${second.error}")
            return null
        }
        val plan = parse(second.text)
        if (plan == null) Log.d(TAG, "重试仍不是合法 JSON，放弃本次整理：${second.text.take(160)}")
        return plan
    }

    private suspend fun chat(
        settings: AppSettings,
        key: String,
        user: String,
        temperature: Float,
    ): AiResult = AiClient.chat(
        baseUrl = settings.aiBaseUrl,
        model = settings.aiModel,
        key = key,
        temperature = temperature,
        system = SYSTEM,
        user = user,
        maxTokens = MAX_TOKENS,
        disableThinking = true,
    )

    private fun buildUser(book: Book, chapter: Chapter): String = buildString {
        append("已有故事圣经：\n")
        append(AiBible.summary(book))
        append("\n\n这一章：")
        append(chapter.title)
        append("（")
        append(countWords(chapter.content))
        append(" 字，刚保存）\n正文：\n")
        val content = chapter.content.trim()
        val clipped = content.length > MAX_CHAPTER_CHARS
        append(if (clipped) content.take(MAX_CHAPTER_CHARS) else content)
        if (clipped) {
            append("\n\n（正文共 ")
            append(content.length)
            append(" 字，这里只截取前 ")
            append(MAX_CHAPTER_CHARS)
            append(" 字，结尾部分没读）")
        }
    }

    // ───────────────────────── 落库 ─────────────────────────

    /** AppStore.mutate is an unlocked read-modify-write; keep every bible write on the
     *  main thread so the 3-minute sync cannot race a body save and drop either side. */
    private suspend fun apply(ctx: Context, store: AppStore, bookId: String, chapterId: String, plan: Plan) {
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            applyOnMain(ctx, store, bookId, chapterId, plan)
        }
    }

    private fun applyOnMain(ctx: Context, store: AppStore, bookId: String, chapterId: String, plan: Plan) {
        // 请求期间作者可能删了书 / 删了章、改了标题，落库前按最新数据再取一次。
        val book = store.book(bookId)
        if (book == null) {
            Log.d(TAG, "整理结果没处可写：书已经不在了")
            return
        }
        val chapter = book.chapters.firstOrNull { it.id == chapterId }
        if (chapter == null) {
            Log.d(TAG, "整理结果没处可写：章节已经删了")
            return
        }
        var characters = 0
        var relations = 0
        var events = 0
        var beats = 0
        var skipped = 0

        // 顺序有意义：先角色，关系两端才找得到人；事件再引用角色名。
        for (args in plan.characters) {
            if (AiBible.mutate(ctx, store, bookId, "character", "upsert", args, source = "text")
                    .startsWith("已写入")
            ) {
                characters++
            } else {
                skipped++
            }
        }
        for (args in plan.relations) {
            if (AiBible.mutate(ctx, store, bookId, "relation", "upsert", args, source = "text")
                    .startsWith("已写入")
            ) {
                relations++
            } else {
                skipped++
            }
        }
        for (args in plan.events) {
            // 事件归属这一章：直接给 chapterId，别让模型猜标题。
            // 已经存在的事件（同名同时间）保住原有的多章 / 出场角色关联，只把本章并进去，
            // 免得自动整理把作者手动挂的第二章、人物抹掉。
            val old = book.bible.events.firstOrNull {
                it.title == args.optString("title").trim() &&
                    it.whenText == args.optString("whenText").trim()
            }
            if (old == null) {
                args.put("chapterIds", JSONArray().put(chapterId))
            } else {
                val chapterIds = LinkedHashSet(old.chapterIds)
                chapterIds.add(chapterId)
                args.put("chapterIds", JSONArray(chapterIds))
                val names = LinkedHashSet<String>()
                old.characterIds.forEach { id ->
                    book.bible.characters.firstOrNull { it.id == id }?.name?.let { names.add(it) }
                }
                args.optJSONArray("characters")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val name = arr.optString(i).trim()
                        if (name.isNotBlank() && name != "null") names.add(name)
                    }
                }
                if (names.isNotEmpty()) args.put("characters", JSONArray(names))
            }
            if (AiBible.mutate(ctx, store, bookId, "event", "upsert", args, source = "text")
                    .startsWith("已写入")
            ) {
                events++
            } else {
                skipped++
            }
        }
        if (plan.beats.isNotEmpty()) {
            // AiBible.mutate 的 outline 用章名定位章节：章名重名时可能写到别的章上，
            // 宁可不写情节点，也不把内容挂错。
            if (book.chapters.count { it.title == chapter.title } > 1) {
                Log.d(TAG, "章名重复（${chapter.title}），跳过情节点")
                skipped++
            } else {
                val args = JSONObject().apply {
                    put("chapter", chapter.title)
                    put("beats", JSONArray(plan.beats))
                }
                if (AiBible.mutate(ctx, store, bookId, "outline", "upsert", args, source = "text")
                        .startsWith("已写入")
                ) {
                    beats = plan.beats.size
                } else {
                    skipped++
                }
            }
        }
        Log.d(
            TAG,
            "整理完成《${book.title}》${chapter.title}：角色 $characters、关系 $relations、" +
                "事件 $events、情节点 $beats（跳过 $skipped）",
        )
    }

    // ───────────────────────── 解析 ─────────────────────────

    private fun parse(text: String): Plan? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            buildPlan(JSONObject(text.substring(start, end + 1)))
        } catch (_: Exception) {
            null
        }
    }

    private fun buildPlan(root: JSONObject): Plan {
        if (root.optBoolean("skip", false)) return Plan(skip = true)

        val characters = ArrayList<JSONObject>()
        root.optJSONArray("characters")?.let { arr ->
            for (i in 0 until arr.length()) {
                if (characters.size >= MAX_CHARACTERS) break
                val o = arr.optJSONObject(i) ?: continue
                val name = str(o, "name").take(24)
                if (name.isBlank()) continue
                characters += JSONObject().apply {
                    put("name", name)
                    put("role", str(o, "role").take(16))
                    put("note", str(o, "note").take(120))
                }
            }
        }

        val relations = ArrayList<JSONObject>()
        root.optJSONArray("relations")?.let { arr ->
            for (i in 0 until arr.length()) {
                if (relations.size >= MAX_RELATIONS) break
                val o = arr.optJSONObject(i) ?: continue
                val from = str(o, "from").take(24)
                val to = str(o, "to").take(24)
                if (from.isBlank() || to.isBlank() || from == to) continue
                val relation = str(o, "relation").take(16)
                relations += JSONObject().apply {
                    put("from", from)
                    put("to", to)
                    if (relation.isNotBlank()) put("relation", relation)
                }
            }
        }

        val events = ArrayList<JSONObject>()
        root.optJSONArray("events")?.let { arr ->
            for (i in 0 until arr.length()) {
                if (events.size >= MAX_EVENTS) break
                val o = arr.optJSONObject(i) ?: continue
                val title = str(o, "title").take(40)
                if (title.isBlank()) continue
                val names = ArrayList<String>()
                o.optJSONArray("characters")?.let { nameArr ->
                    for (j in 0 until minOf(nameArr.length(), 6)) {
                        val name = nameArr.optString(j).trim().take(24)
                        if (name.isNotBlank() && name != "null") names += name
                    }
                }
                events += JSONObject().apply {
                    put("title", title)
                    val whenText = str(o, "whenText").take(24)
                    if (whenText.isNotBlank()) put("whenText", whenText)
                    val note = str(o, "note").take(160)
                    if (note.isNotBlank()) put("note", note)
                    if (names.isNotEmpty()) put("characters", JSONArray(names))
                }
            }
        }

        val beats = ArrayList<String>()
        val beatArr = root.optJSONObject("outline")?.optJSONArray("beats")
            ?: root.optJSONArray("beats")
        beatArr?.let { arr ->
            for (i in 0 until arr.length()) {
                if (beats.size >= MAX_BEATS) break
                val text = when (val item = arr.opt(i)) {
                    is String -> item.trim()
                    is JSONObject -> str(item, "text")
                    else -> ""
                }
                if (text.isNotBlank()) beats += text.take(60)
            }
        }

        return Plan(
            characters = characters,
            relations = relations,
            events = events,
            beats = beats,
        )
    }

    /** JSON null 会被 optString 读成字面 "null"，统一当空串。 */
    private fun str(o: JSONObject, name: String): String {
        val value = o.optString(name).trim()
        return if (value == "null") "" else value
    }

    private data class Plan(
        val skip: Boolean = false,
        val characters: List<JSONObject> = emptyList(),
        val relations: List<JSONObject> = emptyList(),
        val events: List<JSONObject> = emptyList(),
        val beats: List<String> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = characters.isEmpty() && relations.isEmpty() &&
                events.isEmpty() && beats.isEmpty()
    }

    // ───────────────────────── prompt ─────────────────────────

    private val SYSTEM = """
你是一个故事圣经整理器。作者刚保存了一章正文，你要从这一章里挑出「已经写定」的信息，更新这本书的故事圣经。

只提取四类：
1. characters 角色：正文里出场、有戏份的人。name 用正文里的叫法；role 是身份（主角/配角/反派等，正文没写就留空）；note 一句话说清他是谁、想要什么，只依据正文。
2. relations 关系：正文里已经写明的关系。from / to 是人名，必须出现在 characters 或已有故事圣经里；relation 只能是：亲人、恋人、朋友、同盟、对手、仇敌、师徒、其他。
3. events 事件：正文里已经发生的事。whenText 按原文写故事内时间（如「11 月 5 日」「三年前」「当天夜里」），原文没写时间就留空；title 一句话说清发生了什么；note 一两句补充；characters 列出牵涉的人名。
4. outline 情节点：这一章实际写出来的情节，按正文顺序，每条一句话，3–8 条。

硬性规则：
- 只写正文里已经写明的。「暗示」「计划」「以后要发生」都不算，不要编造、不要补设定、不要把作者的计划当成已经发生。
- 正文里没出现过的名字一律不写；已有故事圣经里已经有的角色，正文没写新信息就不要重复输出。
- 同一件事不要换说法重复输出。
- 关系两端必须是人物；拿不准是什么关系就写「其他」。
- 没有任何可提取的内容时，只输出 {"skip":true}。

只输出一个 JSON 对象，不要解释，不要 Markdown 代码块，不要前后缀文字。格式：
{"characters":[{"name":"","role":"","note":""}],"relations":[{"from":"","to":"","relation":""}],"events":[{"whenText":"","title":"","note":"","characters":[""]}],"outline":{"beats":[""]}}
没有的类别给空数组；最多 12 个角色、12 条关系、12 个事件、12 个情节点。
""".trim()
}
