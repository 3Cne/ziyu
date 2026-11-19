package com.moyu.writer

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

/**
 * 3.26.43：故事圣经「全书补齐」。
 *
 * 3.26.23 的整理只在章节保存那一刻处理「刚保存的那一章」，其余章节只要当时没赶上
 * （离线、超时、解析失败、旧数据从没整理过）就永远进不了故事圣经。现在改成按 hash 扫全书：
 *
 * - 侧车 `filesDir/ai/sync-state.json` 记「每本书每一章最后一次成功整理的正文 SHA-256」：
 *   `{ "<bookId>": { "<chapterId>": "<sha256>" } }`，临时文件 + rename 原子写；
 * - 保存 / 打开 / 手动补齐任一触发：扫这本书**全部章节**，正文 hash 与侧车不一致的排队，
 *   逐章串行整理；同一章 hash 没变就不重复整理；
 * - 自动触发仍受 3 分钟窗口节流（原来只有「刚保存的那一章」，现在窗口到了扫全书）；
 *   手动 [syncAll] 可以立刻跑，不等窗口；
 * - 进度用 [progress] 暴露给 UI：已整理 N / 共 M 章 + 当前章名 + running。
 *
 * 其余边界沿用 3.26.23：模型输出解析不了重试一次再静默放弃；落库切主线程，避免与正文保存
 * 互相覆盖；`origin == "author"` 的作者手改条目由 [AiBible.mutate] 保护，自动整理只记冲突不覆盖。
 *
 * 3.26.56：一章不再只读前 6000 字。正文按约 2000–2500 字切块（相邻 200 字重叠、优先段落 /
 * 句末切），每块各跑一次同一套 prompt + 解析，结果按名字 / 事件标题 / 人名对 / 情节点文本
 * 去重合并；冲突时**先写入的块优先**，后块只补空字段。每章最多 6 块（≈1.4 万字），超出的
 * 正文不读，但日志会写清「还有 N 字未读」。节流 / 失败冷却 / 无新信息不写 / journal 不变。
 *
 * `aiBibleSync` 开关只拦「自动触发」；手动补齐是作者明确点的，只要 AI 开着、有 Key 就允许。
 */
object AiBibleSync {
    private const val TAG = "MOYU_BIBLE_SYNC"

    /** 同一本书两次「自动触发」之间的最小间隔。 */
    private const val WINDOW_MS = 3 * 60 * 1000L

    /** 一章整理失败后，这段时间内不再自动重试（防止坏输出无限烧请求）。 */
    private const val RETRY_COOLDOWN_MS = 10 * 60 * 1000L

    /** 3.26.56：长章按块读，不再只读前 6000 字。目标块大小 / 切点搜索下界 / 硬上限（字）。 */
    private const val CHUNK_TARGET_CHARS = 2400
    private const val CHUNK_MIN_CHARS = 2000
    private const val CHUNK_MAX_CHARS = 2500

    /** 相邻块重叠字数：横跨两块的一句话（人物第一次出场、事件的因果）不会被整齐切断。 */
    private const val CHUNK_OVERLAP_CHARS = 200

    /**
     * 每章最多读几块。6 块 ≈ 1.3–1.4 万字（总送进 prompt 的正文量 ≈ 1.4 万）；
     * 超出的正文不读，但日志会写清「跳过 N 字未读」，不静默丢。
     */
    private const val MAX_CHUNKS_PER_CHAPTER = 6

    /** 一条事件最多带几个牵涉人名（块间取并集时按这个上限截）。 */
    private const val MAX_EVENT_NAMES = 6

    /** 一次整理返回四类 JSON；思考关掉，免得预算全花在 reasoning 上。 */
    private const val MAX_TOKENS = 3000

    private const val MAX_CHARACTERS = 12
    private const val MAX_RELATIONS = 12
    private const val MAX_EVENTS = 12
    private const val MAX_BEATS = 12

    /** 提示词里最多列几个自定义关系类型名（3.26.50）；只是一本小说实际用过的名字，不会太多。 */
    private const val MAX_PROMPT_CUSTOM_TYPES = 10

    /** 侧车文件名：只记 hash，不进 moyu.json、不改 schema。 */
    private const val SYNC_FILE = "sync-state.json"

    /**
     * 一次整理的可观察进度（UI 用 [progress] 读）。
     * `total` = 本书有正文的章节数；`done` = 其中已同步 / 本轮已整理的章数。
     */
    data class Progress(
        val bookId: String = "",
        val done: Int = 0,
        val total: Int = 0,
        val currentChapter: String = "",
        val running: Boolean = false,
    ) {
        /** 面板可直接显示的短文案；没数据时为空串。 */
        val label: String
            get() = when {
                total <= 0 -> ""
                running && currentChapter.isNotBlank() -> "补齐 $done/$total 章 · $currentChapter"
                running -> "补齐 $done/$total 章"
                done >= total -> "已补齐 $done/$total 章"
                else -> "已整理 $done/$total 章"
            }
    }

    /**
     * 最近一次整理进度。全世界只有一份：故事面板打开的是哪本书，就直接读
     * [progressFor]（会按 bookId 过滤）；同时整理两本书这种极端情况后写的那本覆盖先写的，
     * 面板不会串数据（bookId 不匹配时返回空进度）。
     */
    val progress = mutableStateOf(Progress())

    fun progressFor(bookId: String): Progress =
        progress.value.takeIf { it.bookId == bookId } ?: Progress(bookId = bookId)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 节流 / 队列 / 进度 都用这把锁；AI 请求本身不在锁里。 */
    private val lock = Any()
    private val states = HashMap<String, BookState>()

    /** 整理失败的章节冷却到什么时候（只在内存里，杀进程即失效，不写侧车）。 */
    private val cooldown = HashMap<String, Long>()

    private class BookState {
        /** 上一次真跑结束的时间。 */
        var lastRunAt: Long = 0L
        /** 有待处理的触发（保存 / 打开 / 手动）。 */
        var pending: Boolean = false
        /** 这批 pending 里至少有一次是手动补齐，要绕过 3 分钟窗口。 */
        var pendingForce: Boolean = false
        /** 正在真跑（等待窗口不算）。 */
        var running: Boolean = false
        var job: Job? = null
        /** 等窗口时的手动唤醒门闩：手动补齐到点直接叫醒。 */
        var gate: CompletableDeferred<Unit>? = null
    }

    // ───────────────────────── 触发 ─────────────────────────

    /**
     * 章节保存后调一次（[AppStore.updateChapter] 是唯一调用点）。
     * 不再只整理这一章：触发一次全书扫描，hash 不符的章节排队补。
     */
    fun noteChapterSaved(ctx: Context, store: AppStore, bookId: String, chapterId: String) {
        if (trigger(ctx, store, bookId, force = false)) {
            Log.d(TAG, "保存后排队补全书：$bookId / $chapterId")
        }
    }

    /**
     * 打开故事圣经 / 书籍详情时调一次（UI 接线点）。与保存触发同一条路径：
     * 扫全书、受 3 分钟窗口节流，不额外花请求。
     */
    fun noteBookOpened(ctx: Context, store: AppStore, bookId: String) {
        if (trigger(ctx, store, bookId, force = false)) {
            Log.d(TAG, "打开后排队补全书：$bookId")
        }
    }

    /**
     * 手动「补齐全书」：立刻扫这本书全部章节，把 hash 不符的章节排队逐章整理，
     * 不等 3 分钟窗口（AI 关着 / 没 Key 时返回 false，调用方可以提示）。
     */
    fun syncAll(ctx: Context, store: AppStore, bookId: String, force: Boolean = true): Boolean =
        trigger(ctx, store, bookId, force)

    private fun trigger(ctx: Context, store: AppStore, bookId: String, force: Boolean): Boolean {
        if (bookId.isBlank()) return false
        val settings = store.data.settings
        if (!settings.aiEnabled || !AiKey.has(ctx)) return false
        // 自动触发受「自动整理故事圣经」开关约束；手动补齐是作者明确点的，允许。
        if (!force && !settings.aiBibleSync) return false
        // 持 applicationContext 到协程里：Activity 退出后补齐还要继续。
        val appContext = ctx.applicationContext
        synchronized(lock) {
            val state = states.getOrPut(bookId) { BookState() }
            state.pending = true
            if (force) state.pendingForce = true
            if (state.job?.isActive == true) {
                // 手动补齐把还在等窗口的任务立刻叫醒；自动触发只记 pending。
                if (force) state.gate?.complete(Unit)
                Log.d(TAG, "已有整理任务，记一次待处理：$bookId（manual=$force）")
                return true
            }
            state.job = scope.launch { drain(appContext, store, bookId, state) }
            Log.d(TAG, "排队整理全书：$bookId（manual=$force）")
            return true
        }
    }

    /** UI 可以拿来显示「整理中」；还在排队窗口里也算 true。 */
    fun isSyncing(bookId: String): Boolean = synchronized(lock) {
        val state = states[bookId] ?: return@synchronized false
        state.job?.isActive == true || state.running
    }

    // ───────────────────────── 调度 ─────────────────────────

    /**
     * 一轮一轮消费 pending：每轮 = 一次全书扫描 + 逐章整理。
     * 自动触发在真跑前补等满 3 分钟窗口；手动补齐不等。跑完若窗口内又来了触发（dirty），
     * 下一轮继续，不丢触发。
     */
    private suspend fun drain(ctx: Context, store: AppStore, bookId: String, state: BookState) {
        try {
            while (true) {
                var force = false
                val shouldRun = synchronized(lock) {
                    if (!state.pending) return@synchronized false
                    force = state.pendingForce
                    state.pending = false
                    state.pendingForce = false
                    true
                }
                if (!shouldRun) break
                // 排队期间作者关了自动整理：自动触发直接作废（手动补齐仍可跑）。
                if (!force && !store.data.settings.aiBibleSync) {
                    Log.d(TAG, "自动整理已关，丢掉排队的自动触发：$bookId")
                    continue
                }
                if (!force) {
                    val wait = synchronized(lock) {
                        (state.lastRunAt + WINDOW_MS - System.currentTimeMillis()).coerceAtLeast(0L)
                    }
                    if (wait > 0L) {
                        val gate = synchronized(lock) {
                            CompletableDeferred<Unit>().also { state.gate = it }
                        }
                        val early = withTimeoutOrNull(wait) { gate.await() } != null
                        synchronized(lock) {
                            if (state.gate === gate) state.gate = null
                            // 手动补齐把窗口叫醒了：这一轮已经覆盖了它的请求，别再补跑一轮。
                            if (early) {
                                state.pending = false
                                state.pendingForce = false
                            }
                        }
                    }
                }
                synchronized(lock) { state.running = true }
                try {
                    runBook(ctx, store, bookId)
                } catch (e: Exception) {
                    Log.d(TAG, "整理异常，放弃本轮：${e.message}")
                }
                synchronized(lock) {
                    state.running = false
                    state.lastRunAt = System.currentTimeMillis()
                }
            }
        } finally {
            synchronized(lock) {
                state.running = false
                state.gate = null
                state.job = null
                // 收尾瞬间又来了触发：补排一次，不丢。
                if (state.pending) {
                    state.job = scope.launch { drain(ctx, store, bookId, state) }
                }
            }
        }
    }

    // ───────────────────────── 扫全书 ─────────────────────────

    private suspend fun runBook(ctx: Context, store: AppStore, bookId: String) {
        val settings = store.data.settings
        if (!settings.aiEnabled) {
            Log.d(TAG, "AI 没开，跳过整理：$bookId")
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
        // 删掉的章从侧车清掉，别让旧 hash 一直占着。
        pruneState(ctx, bookId, book.chapters.map { it.id }.toSet())
        val chapters = book.chapters.filter { it.content.isNotBlank() }
        if (chapters.isEmpty()) {
            Log.d(TAG, "《${book.title}》还没有正文，跳过整理")
            publish(bookId, 0, 0, "", running = false)
            return
        }
        val hashes = HashMap<String, String>(chapters.size)
        chapters.forEach { hashes[it.id] = contentHash(it.content) }
        val stored = storedHashes(ctx, bookId)
        var done = chapters.count { stored[it.id] == hashes[it.id] }
        val queue = chapters.filter { stored[it.id] != hashes[it.id] }
        Log.d(
            TAG,
            "扫描《${book.title}》：共 ${book.chapters.size} 章（有正文 ${chapters.size}），" +
                "已同步 $done 章，待整理 ${queue.size} 章",
        )
        if (queue.isEmpty()) {
            publish(bookId, done, chapters.size, "", running = false)
            return
        }
        publish(bookId, done, chapters.size, queue.first().title, running = true)
        for (chapter in queue) {
            // 队列排好后作者可能又改了正文 / 删了章：每章开始前按最新数据重取。
            val freshBook = store.book(bookId)
            if (freshBook == null) {
                Log.d(TAG, "整理期间书没了，停止本轮：$bookId")
                break
            }
            val fresh = freshBook.chapters.firstOrNull { it.id == chapter.id }
            if (fresh == null || fresh.content.isBlank()) continue
            val sentHash = contentHash(fresh.content)
            if (stored[fresh.id] == sentHash) {
                // 这一章在排队期间已经被别的触发整理过了。
                done++
                publish(bookId, done, chapters.size, fresh.title, running = true)
                continue
            }
            if (inCooldown(bookId, fresh.id)) {
                Log.d(TAG, "这一章刚失败过，先跳过：${fresh.title}")
                continue
            }
            publish(bookId, done, chapters.size, fresh.title, running = true)
            Log.d(TAG, "开始整理《${book.title}》${fresh.title}（${countWords(fresh.content)} 字）")
            // 3.26.50：提示词按这本书实例化（内置 8 种 + 本书已有的自定义关系类型）。
            val system = systemFor(freshBook)
            // 3.26.56：长章按块读，不再只取前 6000 字——每块各跑一次同一套抽取，结果合并后一次落库。
            val content = fresh.content.trim()
            val chunks = splitChunks(content)
            val readChars = chunks.lastOrNull()?.end ?: content.length
            val unreadChars = (content.length - readChars).coerceAtLeast(0)
            Log.d(
                TAG,
                "分块《${fresh.title}》：${chunks.size} 块（每块约 $CHUNK_TARGET_CHARS 字、重叠 " +
                    "$CHUNK_OVERLAP_CHARS 字、上限 $MAX_CHUNKS_PER_CHAPTER 块），已读 $readChars 字，" +
                    (if (unreadChars > 0) "跳过 $unreadChars 字未读（超出块数上限）" else "跳过 0 字"),
            )
            val merger = PlanMerger()
            var skippedChunks = 0
            var failure: AskOutcome? = null
            var failureChunk = 0
            for ((chunkIndex, chunk) in chunks.withIndex()) {
                when (
                    val outcome = askPlan(
                        settings,
                        key,
                        system,
                        buildUser(freshBook, fresh, chunk, chunkIndex, chunks.size),
                    )
                ) {
                    is AskOutcome.Down -> {
                        failure = outcome
                        failureChunk = chunkIndex + 1
                        break
                    }
                    is AskOutcome.Bad -> {
                        failure = outcome
                        failureChunk = chunkIndex + 1
                        break
                    }
                    is AskOutcome.Ok -> {
                        if (outcome.plan.skip) {
                            skippedChunks++
                        } else {
                            merger.add(outcome.plan)
                        }
                    }
                }
            }
            val merged = merger.build()
            // 失败前已经读出来的块也落库，别白烧前面几块的请求；但这一章不算整理完（不记 hash），
            // 下一轮会整章重过一遍——前几块此时已「无新信息」，不会重复写 journal。
            if (!merged.isEmpty) apply(ctx, store, bookId, fresh.id, merged)
            // 失败时把「已经读出来的那几块到底写没写」说清楚，别让日志撒谎。
            val partialNote =
                if (merged.isEmpty) "前 ${failureChunk - 1} 块没有可写数据"
                else "前 ${failureChunk - 1} 块已落库（本章不算整理完，仍会重试）"
            val failed = failure
            if (failed != null) {
                if (failed is AskOutcome.Down) {
                    // 网络 / 服务挂了：后面的章节多半也一样，停掉这一轮，等下次触发。
                    Log.d(
                        TAG,
                        "第 $failureChunk/${chunks.size} 块请求失败，停止本轮（$partialNote）：${failed.reason}",
                    )
                    markCooldown(bookId, fresh.id)
                    break
                }
                // 模型输出两次都解析不了：记冷却、跳过这一章，别拖累其它章节。
                Log.d(
                    TAG,
                    "第 $failureChunk/${chunks.size} 块输出不是合法 JSON，跳过这一章" +
                        "（$partialNote）：${fresh.title}",
                )
                markCooldown(bookId, fresh.id)
                continue
            }
            if (merged.isEmpty) {
                if (skippedChunks == chunks.size) {
                    Log.d(TAG, "本章没有可整理的内容（skip）：${fresh.title}")
                } else {
                    Log.d(TAG, "解析结果为空，不写数据：${fresh.title}")
                }
            }
            // 只有正文在请求期间没被改过，才把送出去的那份 hash 记成已整理；
            // 改过就留空，下一轮按新正文重整理。
            val latest = store.book(bookId)?.chapters?.firstOrNull { it.id == fresh.id }?.content
            if (latest != null && contentHash(latest) == sentHash) {
                recordHash(ctx, bookId, fresh.id, sentHash)
                stored[fresh.id] = sentHash
                clearCooldown(bookId, fresh.id)
            }
            done++
            publish(bookId, done, chapters.size, fresh.title, running = true)
        }
        publish(bookId, done, chapters.size, "", running = false)
        Log.d(TAG, "本轮整理结束《${book.title}》：已整理 $done/${chapters.size} 章")
    }

    private fun publish(bookId: String, done: Int, total: Int, current: String, running: Boolean) {
        progress.value = Progress(bookId = bookId, done = done, total = total, currentChapter = current, running = running)
    }

    private fun inCooldown(bookId: String, chapterId: String): Boolean {
        val until = synchronized(lock) { cooldown["$bookId/$chapterId"] ?: 0L }
        return until > System.currentTimeMillis()
    }

    private fun markCooldown(bookId: String, chapterId: String) {
        synchronized(lock) { cooldown["$bookId/$chapterId"] = System.currentTimeMillis() + RETRY_COOLDOWN_MS }
    }

    private fun clearCooldown(bookId: String, chapterId: String) {
        synchronized(lock) { cooldown.remove("$bookId/$chapterId") }
    }

    // ───────────────────────── 一次整理（请求 + 解析） ─────────────────────────

    private sealed class AskOutcome {
        class Ok(val plan: Plan) : AskOutcome()
        /** 请求本身失败（网络 / Key / 服务端），整轮该停。 */
        class Down(val reason: String) : AskOutcome()
        /** 请求成功但两次都不是合法 JSON，跳过这一章。 */
        class Bad(val reason: String) : AskOutcome()
    }

    private suspend fun askPlan(settings: AppSettings, key: String, system: String, user: String): AskOutcome {
        val first = chat(settings, key, system, user, .2f)
        if (!first.ok) {
            Log.d(TAG, "请求失败，放弃本次整理：${first.error}")
            return AskOutcome.Down(first.error.orEmpty())
        }
        parse(first.text)?.let { return AskOutcome.Ok(it) }
        // 解析失败重试一次：同一请求，temperature 降到 0.1；再失败就静默放弃，不写数据。
        Log.d(TAG, "输出不是合法 JSON，重试一次：${first.text.take(160)}")
        val second = chat(settings, key, system, user, .1f)
        if (!second.ok) {
            Log.d(TAG, "重试请求失败，放弃本次整理：${second.error}")
            return AskOutcome.Down(second.error.orEmpty())
        }
        val plan = parse(second.text)
        if (plan == null) {
            Log.d(TAG, "重试仍不是合法 JSON，放弃本次整理：${second.text.take(160)}")
            return AskOutcome.Bad(second.text.take(160))
        }
        return AskOutcome.Ok(plan)
    }

    private suspend fun chat(
        settings: AppSettings,
        key: String,
        system: String,
        user: String,
        temperature: Float,
    ): AiResult = AiClient.chat(
        baseUrl = settings.aiBaseUrl,
        model = settings.aiModel,
        key = key,
        temperature = temperature,
        system = system,
        user = user,
        maxTokens = MAX_TOKENS,
        disableThinking = true,
    )

    /**
     * 3.26.56：一章切块后，每块单独送一次 prompt。系统提示词（[SYSTEM]）一个字没动，
     * 只在用户消息里说明「这是整章的第几段、只整理这一段」。
     */
    private fun buildUser(
        book: Book,
        chapter: Chapter,
        chunk: ChapterChunk,
        chunkIndex: Int,
        chunkCount: Int,
    ): String = buildString {
        append("已有故事圣经：\n")
        append(AiBible.summary(book))
        append("\n\n这一章：")
        val index = book.chapters.indexOfFirst { it.id == chapter.id }
        if (index >= 0) {
            append("第 ")
            append(index + 1)
            append(" 章（共 ")
            append(book.chapters.size)
            append(" 章）")
        }
        append(chapter.title)
        append("（")
        append(countWords(chapter.content))
        append(" 字）")
        if (chunkCount > 1) {
            append("\n注意：这一章较长，正文分 ")
            append(chunkCount)
            append(" 段依次整理；这是第 ")
            append(chunkIndex + 1)
            append(" 段（相邻两段有少量重叠）。只整理这一段里已经写明的信息。")
        }
        append("\n正文：\n")
        append(chunk.text)
    }

    // ───────────────────────── 3.26.56 长章分块 ─────────────────────────

    /** 一章正文里的一块：`start` / `end` 是相对「trim 过的整章正文」的下标，用来算跳过了多少字。 */
    private class ChapterChunk(val text: String, val start: Int, val end: Int)

    /**
     * 把一章正文切成约 [CHUNK_MIN_CHARS]–[CHUNK_MAX_CHARS] 字的块，相邻块重叠
     * [CHUNK_OVERLAP_CHARS] 字；切点优先段落边界，其次句末标点，都没有才硬切。
     *
     * 最多切 [MAX_CHUNKS_PER_CHAPTER] 块（≈1.4 万字）。上限之外的正文不读，调用方按最后一块的
     * [ChapterChunk.end] 把「还有多少字未读」写进日志，不静默丢。
     */
    private fun splitChunks(content: String): List<ChapterChunk> {
        if (content.isEmpty()) return emptyList()
        if (content.length <= CHUNK_MAX_CHARS) {
            return listOf(ChapterChunk(content, 0, content.length))
        }
        val chunks = ArrayList<ChapterChunk>(MAX_CHUNKS_PER_CHAPTER)
        var start = 0
        while (start < content.length && chunks.size < MAX_CHUNKS_PER_CHAPTER) {
            val end = if (content.length - start <= CHUNK_MAX_CHARS) {
                content.length
            } else {
                cutEnd(content, start)
            }
            if (end <= start) break
            chunks += ChapterChunk(content.substring(start, end), start, end)
            if (end >= content.length) break
            val next = end - CHUNK_OVERLAP_CHARS
            // 至少往前走：切点再偏也不会原地打转。
            start = if (next > start) next else end
        }
        return chunks
    }

    /**
     * 从 [start] 找本块的结尾（不含）：在「下界 start+2000」到「硬上限 start+2500」之间，
     * 取离目标点（start+2250）最近的段落边界 → 句末标点 → 都不行就硬切在目标点。
     */
    private fun cutEnd(text: String, start: Int): Int {
        val minEnd = (start + CHUNK_MIN_CHARS).coerceAtMost(text.length)
        val maxEnd = (start + CHUNK_MAX_CHARS).coerceAtMost(text.length)
        if (maxEnd <= minEnd) return maxEnd
        val target = (start + CHUNK_TARGET_CHARS).coerceIn(minEnd, maxEnd)

        // 1) 段落边界：中文小说里换行就是分段，切在连续换行之后。
        var best = -1
        var i = minEnd
        while (i < maxEnd) {
            if (text[i] == '\n') {
                var j = i
                while (j < maxEnd && text[j] == '\n') j++
                if (j > minEnd && (best < 0 || abs(j - target) < abs(best - target))) best = j
                i = j
            } else {
                i++
            }
        }
        if (best > 0) return best

        // 2) 句末标点（连同紧跟的收尾引号 / 括号）：切在标点之后。
        best = -1
        i = minEnd
        while (i < maxEnd) {
            if (SENTENCE_ENDS.indexOf(text[i]) >= 0) {
                var j = i + 1
                while (j < maxEnd && CLOSERS.indexOf(text[j]) >= 0) j++
                if (j > minEnd && (best < 0 || abs(j - target) < abs(best - target))) best = j
                i = j
            } else {
                i++
            }
        }
        if (best > 0) return best

        // 3) 实在找不到边界，硬切在目标点。
        return target
    }

    private const val SENTENCE_ENDS = "。！？；…!?;."
    private const val CLOSERS = "”’」』）》】\"'"

    // ───────────────────────── 落库 ─────────────────────────

    /** AppStore.mutate is an unlocked read-modify-write; keep every bible write on the
     *  main thread so the 3-minute sync cannot race a body save and drop either side. */
    private suspend fun apply(ctx: Context, store: AppStore, bookId: String, chapterId: String, plan: Plan) {
        withContext(Dispatchers.Main) {
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
        var unchanged = 0

        // 顺序有意义：先角色，关系两端才找得到人；事件再引用角色名。
        // 「没有新信息」的条目直接跳过：全书补齐会把每章都过一遍，若每次都把同样的角色 /
        // 关系 / 事件重写一次，会刷满 bible 的 journal、把 updatedAt 全部推高。
        for (args in plan.characters) {
            val name = args.optString("name").trim()
            if (name.isBlank()) continue
            val old = book.bible.characters.firstOrNull { it.name == name }
            if (old != null) {
                val role = args.optString("role").trim()
                val note = args.optString("note").trim()
                if ((role.isBlank() || role == old.role) && (note.isBlank() || note == old.note)) {
                    unchanged++
                    continue
                }
            }
            if (AiBible.mutate(ctx, store, bookId, "character", "upsert", args, source = "text")
                    .startsWith("已写入")
            ) {
                characters++
            } else {
                skipped++
            }
        }
        // 3.26.56：旧写法只在开头取一次 book，而 AppStore.mutate 每次都会换掉 data。
        // 一章合并多块之后，同一章里新出现的角色 / 关系 / 事件必须能被下一类看到，
        // 所以每类之前重新取一次最新 book（否则「本章新角色之间的关系」会整条被跳过）。
        val afterCharacters = store.book(bookId)
        if (afterCharacters == null) {
            Log.d(TAG, "整理结果没处可写：书已经不在了")
            return
        }
        for (args in plan.relations) {
            val fromName = args.optString("from").trim()
            val toName = args.optString("to").trim()
            if (fromName.isNotBlank() && toName.isNotBlank()) {
                val from = afterCharacters.bible.characters.firstOrNull { it.name == fromName }
                val to = afterCharacters.bible.characters.firstOrNull { it.name == toName }
                if (from != null && to != null) {
                    val old = afterCharacters.bible.relations.firstOrNull {
                        (it.from == from.id && it.to == to.id) || (it.from == to.id && it.to == from.id)
                    }
                    val rawKind = args.optString("relation").trim()
                    if (old != null) {
                        // 3.26.50：无变化判据同时比 kind / kindLabel / kindTier（任一不同就算有变化），
                        // 换算口径与 AiBible.mutate 的落库逻辑是同一个函数，不会「判据说没变、落库却改了」。
                        // 以前只比 kind：自定义关系被自动整理改名时 kind 都是 OTHER，会被误判无变化跳过。
                        val spec = relationTypeSpec(rawKind, old, afterCharacters.bible)
                        if (rawKind.isBlank() ||
                            (spec.kind == old.kind && spec.label == old.kindText && spec.tier == old.tier)
                        ) {
                            unchanged++
                            continue
                        }
                    }
                }
            }
            if (AiBible.mutate(ctx, store, bookId, "relation", "upsert", args, source = "text")
                    .startsWith("已写入")
            ) {
                relations++
            } else {
                skipped++
            }
        }
        val afterRelations = store.book(bookId)
        if (afterRelations == null) {
            Log.d(TAG, "整理结果没处可写：书已经不在了")
            return
        }
        for (args in plan.events) {
            // 事件归属这一章：直接给 chapterId，别让模型猜标题。
            // 已经存在的事件（同名同时间）保住原有的多章 / 出场角色关联，只把本章并进去，
            // 免得自动整理把作者手动挂的第二章、人物抹掉。
            val title = args.optString("title").trim()
            val whenText = args.optString("whenText").trim()
            val exact = afterRelations.bible.events.firstOrNull { it.title == title && it.whenText == whenText }
            // 本章这一条没带时间、而书里同名的只有一条时按标题合并：同一件事在不同章的正文里
            // 很容易一边写得出时间、一边写不出。两边都带了不同时间就当成两件事，不硬并。
            val byTitle = if (exact == null && whenText.isBlank()) {
                afterRelations.bible.events.filter { it.title == title }.takeIf { it.size == 1 }?.first()
            } else {
                null
            }
            val old = exact ?: byTitle
            val extractedNames = eventNames(args)
            if (old == null) {
                args.put("chapterIds", JSONArray().put(chapterId))
            } else {
                args.put("id", old.id)
                val chapterIds = LinkedHashSet(old.chapterIds)
                chapterIds.add(chapterId)
                args.put("chapterIds", JSONArray(chapterIds))
                val names = LinkedHashSet<String>()
                old.characterIds.forEach { id ->
                    afterRelations.bible.characters.firstOrNull { it.id == id }?.name?.let { names.add(it) }
                }
                names.addAll(extractedNames)
                if (names.isNotEmpty()) args.put("characters", JSONArray(names))
                // 本章已在事件里、时间没变、补充说明没变、牵涉的人也没多 → 不用重写。
                val whenChanged = args.has("whenText") && whenText.isNotBlank() && whenText != old.whenText
                val noteChanged = args.has("note") && args.optString("note").trim() != old.note
                val castIds = extractedNames.map { n ->
                    afterRelations.bible.characters.firstOrNull { it.name == n }?.id
                }
                val castUnchanged = castIds.none { it == null } && castIds.filterNotNull().all { it in old.characterIds }
                if (!whenChanged && !noteChanged && castUnchanged && chapterId in old.chapterIds) {
                    unchanged++
                    continue
                }
            }
            if (AiBible.mutate(ctx, store, bookId, "event", "upsert", args, source = "text")
                    .startsWith("已写入")
            ) {
                events++
            } else {
                skipped++
            }
        }
        val afterEvents = store.book(bookId)
        if (afterEvents == null) {
            Log.d(TAG, "整理结果没处可写：书已经不在了")
            return
        }
        if (plan.beats.isNotEmpty()) {
            val old = afterEvents.bible.outline.firstOrNull { it.chapterId == chapter.id }
            if (old != null && old.beats.map { it.text } == plan.beats) {
                unchanged++
            } else if (afterEvents.chapters.count { it.title == chapter.title } > 1) {
                // AiBible.mutate 的 outline 用章名定位章节：章名重名时可能写到别的章上，
                // 宁可不写情节点，也不把内容挂错。
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
                "事件 $events、情节点 $beats（无变化 $unchanged，跳过 $skipped）",
        )
    }

    private fun eventNames(args: JSONObject): List<String> {
        val out = ArrayList<String>(6)
        args.optJSONArray("characters")?.let { arr ->
            for (i in 0 until minOf(arr.length(), 6)) {
                val name = arr.optString(i).trim().take(24)
                if (name.isNotBlank() && name != "null") out += name
            }
        }
        return out
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
                    // 时间只写「规范化后」的字符串；解析不了的不写，让既有「时间不详」兜底。
                    val whenText = normalizeWhenText(str(o, "whenText"))
                    if (whenText != null) put("whenText", whenText)
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

    // ───────────────────────── 时间规范化 ─────────────────────────

    // 模型输出的是「规范化故事内时间」；这里只接受能写进现有 whenText 且 storyTimeKey
    // 能排序的三种形态，其它（「当天夜里」「傍晚」这类）一律不写，界面显示「时间不详」。
    private val whenIso = Regex("(\\d{4})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})\\s*[日号]?")
    private val whenMonthDay = Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?")
    private val whenRelative = Regex("([0-9]+|[零一二两三四五六七八九十百千]+)\\s*(个月|年|月|天|日|周|星期)\\s*(?:以|之)?\\s*(前|后)")
    private val whenDayIndex = Regex("第\\s*([0-9]+|[零一二两三四五六七八九十百千]+)\\s*[天日]")

    /**
     * 把模型给的 whenText 压成 `YYYY-MM-DD` / `M月D日` / `N年[前|后]` / `第N天`。
     * 返回 null 表示这串时间解析不了，调用方不写这个字段。
     */
    private fun normalizeWhenText(raw: String): String? {
        if (raw.isBlank()) return null
        val text = normalizeStoryWidth(raw)
        whenIso.find(text)?.let { m ->
            val year = m.groupValues[1].toIntOrNull() ?: return@let
            val month = m.groupValues[2].toIntOrNull() ?: return@let
            val day = m.groupValues[3].toIntOrNull() ?: return@let
            if (year < 1 || month !in 1..12 || day !in 1..31) return@let
            return "%04d-%02d-%02d".format(year, month, day)
        }
        whenMonthDay.find(text)?.let { m ->
            val month = m.groupValues[1].toIntOrNull() ?: return@let
            val day = m.groupValues[2].toIntOrNull() ?: return@let
            if (month !in 1..12 || day !in 1..31) return@let
            return "${month}月${day}日"
        }
        whenRelative.find(text)?.let { m ->
            val n = numberValue(m.groupValues[1]) ?: return@let
            if (n <= 0) return@let
            val unit = m.groupValues[2]
            val dir = m.groupValues[3]
            return "$n$unit$dir"
        }
        whenDayIndex.find(text)?.let { m ->
            val n = numberValue(m.groupValues[1]) ?: return@let
            if (n <= 0) return@let
            return "第${n}天"
        }
        return null
    }

    /** 中文输入法常把 `-` `/` `.` 和数字打成全角，解析前先归一成 ASCII。 */
    private fun normalizeStoryWidth(text: String): String {
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

    private fun numberValue(raw: String): Int? = raw.trim().toIntOrNull() ?: chineseNumber(raw.trim())

    private fun chineseNumber(text: String): Int? {
        if (text.isEmpty()) return null
        val digits = mapOf(
            '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        var section = 0
        var number = 0
        for (c in text) {
            when {
                digits.containsKey(c) -> number = digits.getValue(c)
                c == '十' -> {
                    section += (if (number == 0) 1 else number) * 10
                    number = 0
                }
                c == '百' -> {
                    section += (if (number == 0) 1 else number) * 100
                    number = 0
                }
                c == '千' -> {
                    section += (if (number == 0) 1 else number) * 1000
                    number = 0
                }
                else -> return null
            }
        }
        return section + number
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

    /**
     * 3.26.56：一章多块抽取结果的合并器。
     *
     * 去重口径：角色按名字、关系按无序人名对、事件按标题、情节点按去掉空白 / 标点后的文本。
     * 冲突口径：**先写入的块优先**——前块已经给出的 role / note / relation / whenText 不被后块
     * 覆盖；后块只能补空字段、给事件的人名集合补人（并集）。
     */
    private class PlanMerger {
        private val characters = LinkedHashMap<String, JSONObject>()
        private val relations = LinkedHashMap<String, JSONObject>()
        private val events = LinkedHashMap<String, JSONObject>()
        private val beats = LinkedHashMap<String, String>()

        fun add(plan: Plan) {
            plan.characters.forEach { mergeCharacter(it) }
            plan.relations.forEach { mergeRelation(it) }
            plan.events.forEach { mergeEvent(it) }
            plan.beats.forEach { mergeBeat(it) }
        }

        fun build(): Plan = Plan(
            characters = characters.values.toList(),
            relations = relations.values.toList(),
            events = events.values.toList(),
            beats = beats.values.toList(),
        )

        private fun mergeCharacter(src: JSONObject) {
            val name = src.optString("name").trim()
            if (name.isBlank()) return
            val old = characters[name]
            if (old == null) {
                characters[name] = JSONObject().apply {
                    put("name", name)
                    put("role", src.optString("role").trim())
                    put("note", src.optString("note").trim())
                }
            } else {
                fillBlank(old, src, "role")
                fillBlank(old, src, "note")
            }
        }

        private fun mergeRelation(src: JSONObject) {
            val from = src.optString("from").trim()
            val to = src.optString("to").trim()
            if (from.isBlank() || to.isBlank() || from == to) return
            // 关系是无向的：A—B 与 B—A 是同一对（与 applyOnMain 的匹配口径一致）。
            val key = if (from <= to) "$from\u0000$to" else "$to\u0000$from"
            val old = relations[key]
            if (old == null) {
                relations[key] = JSONObject().apply {
                    put("from", from)
                    put("to", to)
                    val relation = src.optString("relation").trim()
                    if (relation.isNotBlank()) put("relation", relation)
                }
            } else {
                fillBlank(old, src, "relation")
            }
        }

        private fun mergeEvent(src: JSONObject) {
            val title = src.optString("title").trim()
            if (title.isBlank()) return
            val srcWhen = src.optString("whenText").trim().let { if (it == "null") "" else it }
            val base = events[title]
            // 先写入的块优先：同名且时间不冲突 → 同一件事，只补空字段 / 并人名。
            // 同名但两边都写了**不同**的规范化时间 → 是两件事（与 applyOnMain 的判据一致），
            // 另存一条，免得后块的时间把前块已写死的时间顶掉、或整件事被合并丢掉。
            val old = if (base == null) null else {
                val baseWhen = base.optString("whenText").trim()
                if (srcWhen.isNotBlank() && baseWhen.isNotBlank() && srcWhen != baseWhen) {
                    events[title + "\u0000" + srcWhen]
                } else {
                    base
                }
            }
            if (old == null) {
                val fresh = JSONObject().apply {
                    put("title", title)
                    fillBlank(this, src, "whenText")
                    fillBlank(this, src, "note")
                }
                unionEventNames(fresh, src)
                val key = if (base == null) title else title + "\u0000" + srcWhen
                events[key] = fresh
            } else {
                fillBlank(old, src, "whenText")
                fillBlank(old, src, "note")
                unionEventNames(old, src)
            }
        }

        /** 同名事件在不同块里带的牵涉人取并集，最多 [MAX_EVENT_NAMES] 个（保持首次出现顺序）。 */
        private fun unionEventNames(old: JSONObject, src: JSONObject) {
            val names = ArrayList<String>(MAX_EVENT_NAMES)
            fun collect(arr: JSONArray?) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    if (names.size >= MAX_EVENT_NAMES) return
                    val name = arr.optString(i).trim()
                    if (name.isNotBlank() && name != "null" && name !in names) names += name
                }
            }
            collect(old.optJSONArray("characters"))
            collect(src.optJSONArray("characters"))
            if (names.isNotEmpty()) old.put("characters", JSONArray(names))
        }

        /** 后块只补空字段：前块已经写死的 role / note / relation / whenText 不覆盖。 */
        private fun fillBlank(dst: JSONObject, src: JSONObject, key: String) {
            if (dst.optString(key).isNotBlank()) return
            val value = src.optString(key).trim()
            if (value.isNotBlank() && value != "null") dst.put(key, value)
        }

        private fun mergeBeat(text: String) {
            val value = text.trim()
            if (value.isBlank()) return
            val key = beatKey(value)
            if (key.isBlank() || beats.containsKey(key)) return
            beats[key] = value
        }
    }

    /** 情节点去重键：去掉空白和常见标点，避免相邻块把同一句写成「……。」和「……」两条。 */
    private fun beatKey(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            if (c.isWhitespace()) continue
            if (CJK_PUNCT.indexOf(c) >= 0) continue
            sb.append(c)
        }
        return sb.toString()
    }

    private const val CJK_PUNCT = "，。！？；：、,.!?;:…—-“”‘’\"'（）()「」『』【】《》"

    // ───────────────────────── 侧车 sync-state.json ─────────────────────────

    private var syncRoot: JSONObject? = null

    private fun aiDir(ctx: Context): File = File(ctx.filesDir, "ai").apply { mkdirs() }
    private fun syncFile(ctx: Context): File = File(aiDir(ctx), SYNC_FILE)

    @Synchronized
    private fun loadRoot(ctx: Context): JSONObject {
        syncRoot?.let { return it }
        val loaded = runCatching {
            val file = syncFile(ctx)
            if (file.exists()) JSONObject(file.readText(Charsets.UTF_8)) else JSONObject()
        }.getOrElse { JSONObject() }
        syncRoot = loaded
        return loaded
    }

    /** 某本书已整理的章节 hash 快照。 */
    private fun storedHashes(ctx: Context, bookId: String): HashMap<String, String> = synchronized(lock) {
        val root = loadRoot(ctx)
        val book = root.optJSONObject(bookId) ?: return@synchronized HashMap<String, String>()
        val out = HashMap<String, String>(book.length())
        for (key in book.keys().asSequence()) {
            val value = book.optString(key, "")
            if (value.isNotBlank()) out[key] = value
        }
        return@synchronized out
    }

    private fun contentHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    /** 记一章「已按这份正文整理过」。失败只记日志，不影响这一轮。 */
    private fun recordHash(ctx: Context, bookId: String, chapterId: String, hash: String) {
        synchronized(lock) {
            val root = loadRoot(ctx)
            val book = root.optJSONObject(bookId) ?: JSONObject()
            book.put(chapterId, hash)
            root.put(bookId, book)
            writeRoot(ctx, root)
        }
    }

    /** 章节被删掉后，把它的 hash 从侧车清掉。 */
    private fun pruneState(ctx: Context, bookId: String, liveChapterIds: Set<String>) {
        synchronized(lock) {
            val root = loadRoot(ctx)
            val book = root.optJSONObject(bookId) ?: return
            val stale = book.keys().asSequence().filter { it !in liveChapterIds }.toList()
            if (stale.isEmpty()) return
            stale.forEach { book.remove(it) }
            if (book.length() == 0) root.remove(bookId) else root.put(bookId, book)
            writeRoot(ctx, root)
        }
    }

    /** 临时文件 + rename：杀进程不会留下半截 JSON。 */
    private fun writeRoot(ctx: Context, root: JSONObject) {
        runCatching {
            val target = syncFile(ctx)
            val text = root.toString()
            val tmp = File(target.parentFile, "sync-state.tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                target.writeText(text, Charsets.UTF_8)
                tmp.delete()
            }
        }.onFailure { Log.d(TAG, "写 sync-state 失败：${it.message}") }
    }

    private val HEX = "0123456789abcdef".toCharArray()

    // ───────────────────────── prompt ─────────────────────────

    /** 3.26.50：关系类型清单占位符；每本书按自己已有的自定义类型实例化，见 [systemFor]。 */
    private const val RELATION_TYPES_TOKEN = "{{RELATION_TYPES}}"

    /** 内置 8 种关系类型的中文名（提示词里排在自定义类型前面）。 */
    private val builtInRelationLabels = RelationKind.entries.map { it.label }

    private val SYSTEM = """
你是一个故事圣经整理器。作者保存了一章正文，你要把这一章里「已经写定」的信息整理进这本书的故事圣经。

四类必须同时过一遍，每类都要认真判断，不许只看其中一两类：
1. characters 角色：本章出场、有戏份的人物全部列出。name 用正文里的叫法；role 是身份（主角/配角/反派/师长等，正文没写就留空）；note 一句话说清他是谁、想要什么，只依据正文。故事圣经里已经有的角色，正文没给新信息时 role / note 留空即可。
2. relations 关系：本章已经写明的关系。from / to 是人名，必须出现在 characters 或已有故事圣经里；relation 只能用：${RELATION_TYPES_TOKEN}
3. events 事件：本章已经发生的事。title 一句话说清发生了什么；note 一两句补充；characters 列出牵涉的人名；whenText 必须按下面的时间格式写。
4. outline.beats 情节点：这一章实际写出来的情节，按正文顺序，每条一句话，3–8 条。

whenText 时间格式（必须规范化，只接受下面三种）：
A. 明确日期：「YYYY-MM-DD」，如 1987-03-15；只知道月日写「M月D日」，如 3月15日。
B. 相对时间：阿拉伯数字 + 年/个月/天/周 + 前/后，如「3年前」「2个月后」「5天后」；原文写「三年前」也要改写成「3年前」。
C. 第几天：「第N天」，如「第3天」。
原文只有「当天夜里」「傍晚」「昨天」这类没法归一成上面三种的说法时，whenText 留空；不要自己推算、不要编日期。宁可留空。

硬性规则：
- 只写正文里已经写明的。「暗示」「计划」「以后要发生」都不算，不要编造、不要补设定、不要把作者的计划当成已经发生。
- 正文里没出现过的名字一律不写。
- 同一件事不要换说法重复输出。
- 关系两端必须是人物；拿不准是什么关系就写「其他」。
- relation 只能用上面列出的类型名（内置 8 种，或这本书已有的自定义类型）；自定义类型必须一字不差照抄，不要自己造近义词。
- 四类都没有可提取的内容时，只输出 {"skip":true}。

只输出一个 JSON 对象，不要解释，不要 Markdown 代码块，不要前后缀文字。格式：
{"characters":[{"name":"","role":"","note":""}],"relations":[{"from":"","to":"","relation":""}],"events":[{"whenText":"","title":"","note":"","characters":[""]}],"outline":{"beats":[""]}}
没有的类别给空数组；最多 12 个角色、12 条关系、12 个事件、12 个情节点。
""".trim()

    /**
     * 3.26.50：把静态 [SYSTEM] 实例化成这本书的提示词——内置 8 种类型 + 这本书已有的自定义
     * `kindLabel`。书里还没有自定义类型时，提示词与 3.26.49 完全一致。
     */
    private fun systemFor(book: Book): String = SYSTEM.replace(RELATION_TYPES_TOKEN, relationTypesFor(book))

    /**
     * relation 字段的合法取值清单。自定义类型按出现顺序去重，最多列 [MAX_PROMPT_CUSTOM_TYPES] 个：
     * 列太多会稀释模型对关系判断的注意力，而且这些名字本来就只有本书实际用过的几个。
     */
    private fun relationTypesFor(book: Book): String {
        val builtIn = builtInRelationLabels.joinToString("、")
        val custom = LinkedHashSet<String>()
        book.bible.relations.forEach { r ->
            if (r.customKind && r.kindText !in builtInRelationLabels) custom += r.kindText
        }
        if (custom.isEmpty()) return "$builtIn。"
        return builtIn + "，以及这本书已有的自定义类型：" +
            custom.take(MAX_PROMPT_CUSTOM_TYPES).joinToString("、") + "。"
    }
}
