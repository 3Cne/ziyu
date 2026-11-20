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
 *   3.26.59 起还可能是 `"<sha256>#<已读字数>"`：长章读到块数上限时记的部分状态，
 *   下一轮从该字数续读（见下）；
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
 * 正文本轮不读，但日志会写清「还有 N 字未读」。节流 / 失败冷却 / 无新信息不写 / journal 不变。
 *
 * 3.26.59：四个质量问题的落地口径——
 * 1. 角色不再只要求 role + note：prompt 要求 traits（性格+外貌）/ faction（阵营）并明确
 *    「老角色正文有新信息就补全，不要因为已有就留空」；解析上限同步放宽，「无变化」判据
 *    加上这两个字段（否则只补性格 / 只补阵营会被判无变化跳过）。
 * 2. 事件 note 要求写「谁做了什么 → 结果 / 关键动作」，情节点要求「起因 → 动作 → 结果」。
 * 3. 时间：normalizeWhenText 解析不了时**保留原文**写进 whenText（不再丢时间 / 丢事件）；
 *    同时在 buildUser 里给时间锚点（本章已知时间 / 上一处确定时间 / 最近事件时间），
 *    让「当天夜里 / 傍晚 / 昨天」绑到确定时间上而不是模型自己编。
 * 4. 长章尾段：块数达上限、还有未读字数时**不记整章完成的 hash**，改记部分状态
 *    `正文hash#已读字数`，下一轮从断点续读（正文改过 hash 对不上则从头），日志写清
 *    「未读完，下次继续」——不会再出现「只读了前 1.4 万字却记成整章已整理、尾段永久丢」。
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
     * 超出的正文这一轮不读，日志写清「还有 N 字未读」，并记部分状态让下一次从断点续读
     * （3.26.59），不静默丢。
     */
    private const val MAX_CHUNKS_PER_CHAPTER = 6

    /** 一条事件最多带几个牵涉人名（块间取并集时按这个上限截）。 */
    private const val MAX_EVENT_NAMES = 6

    /**
     * 一次整理返回四类 JSON；思考关掉，免得预算全花在 reasoning 上。
     * 3.26.59：角色多了 traits / faction、事件 note 与情节点都要求写因果，输出明显变长，
     * 3000 会在 JSON 中途截断（整章解析失败）；抬到 5000 留够四类的量。
     */
    private const val MAX_TOKENS = 5000

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
        // 3.26.59：侧车值有两种——纯正文 hash = 整章读完；`hash#已读字数` = 长章读到块数上限
        // 就先停下、下次从第 N 字接着读。只有「hash 对得上且已读字数 0」才算整章同步过，
        // 否则这一章会重新排队（这是尾段不会被误判成已整理、永久丢失的关键）。
        var done = chapters.count { isFullySynced(stored[it.id], hashes[it.id]) }
        val queue = chapters.filter { !isFullySynced(stored[it.id], hashes[it.id]) }
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
            if (isFullySynced(stored[fresh.id], sentHash)) {
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
            // 3.26.59：上一轮读到块数上限、正文没读完时，侧车存了「已读到第几个字」。
            // 正文没变就从那里接着读（往前让出 CHUNK_OVERLAP_CHARS 字，保住被切断的句子），
            // 不再每轮都从头烧前 6 块请求；正文变了 hash 对不上，自然从第 0 字重来。
            val storedCursor = if (syncedHashOf(stored[fresh.id]) == sentHash) {
                readCursorOf(stored[fresh.id])
            } else {
                0
            }
            val savedCursor = storedCursor.coerceIn(0, content.length)
            val resumeFrom = if (savedCursor > 0) {
                (savedCursor - CHUNK_OVERLAP_CHARS).coerceAtLeast(0)
            } else {
                0
            }
            val chunks = splitChunks(content, resumeFrom)
            val readChars = chunks.lastOrNull()?.end ?: content.length
            val unreadChars = (content.length - readChars).coerceAtLeast(0)
            Log.d(
                TAG,
                "分块《${fresh.title}》：${chunks.size} 块（每块约 $CHUNK_TARGET_CHARS 字、重叠 " +
                    "$CHUNK_OVERLAP_CHARS 字、上限 $MAX_CHUNKS_PER_CHAPTER 块），" +
                    (if (resumeFrom > 0) "从第 ${resumeFrom + 1} 字续读，" else "") +
                    "本轮读到第 $readChars 字，" +
                    (if (unreadChars > 0) "还有 $unreadChars 字未读（块数上限），未读完，下次继续" else "整章读完"),
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
            // 下一轮会从侧车记录的断点（没有断点就是第 1 块）重过一遍——前几块此时已「无新信息」，
            // 不会重复写 journal。
            if (!merged.isEmpty) apply(ctx, store, bookId, fresh.id, merged, appendBeats = savedCursor > 0)
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
                clearCooldown(bookId, fresh.id)
                if (unreadChars > 0) {
                    // 3.26.59：块数到了上限、正文尾巴还没读——**不能**记「整章已整理」的 hash，
                    // 否则下次扫全书会认为这章没问题，尾段永久丢。改记「部分状态」hash#已读字数：
                    // 下一轮自动 / 手动整理都会从断点接着读；这一章本轮不算 done，进度如实显示没补齐。
                    val partial = partialState(sentHash, readChars)
                    recordPartialState(ctx, bookId, fresh.id, sentHash, readChars)
                    stored[fresh.id] = partial
                    Log.d(
                        TAG,
                        "《${fresh.title}》块数到上限（$MAX_CHUNKS_PER_CHAPTER 块），" +
                            "已读 $readChars/${content.length} 字，未读完，下次从第 ${readChars + 1} 字继续" +
                            "（不记完成 hash）",
                    )
                } else {
                    recordHash(ctx, bookId, fresh.id, sentHash)
                    stored[fresh.id] = sentHash
                    done++
                }
            } else {
                done++
            }
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
     * 3.26.56：一章切块后，每块单独送一次 prompt。系统提示词（[SYSTEM]）本身不动，
     * 只在用户消息里说明「这是整章的第几段、只整理这一段」。
     *
     * 3.26.59：这里再给一组「时间锚点」（本章已知时间 / 上一处确定时间 / 最近事件时间），
     * 让模型把「当天夜里」「傍晚」「昨天」这类相对说法绑到确定时间上，而不是自己编日期。
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
        append("\n时间锚点（正文里的「当天夜里 / 傍晚 / 昨天 / 第二天」先绑到下面这些确定时间上再写 whenText；绑不上就照抄原文，不要自己编日期）：\n")
        timeAnchorLines(book, chapter).forEach { line ->
            append("- ")
            append(line)
            append('\n')
        }
        append("\n正文：\n")
        append(chunk.text)
    }

    /**
     * 3.26.59：给 prompt 一组「确定的时间锚点」。
     *
     * 数据来源只有 `BookBible.events[].whenText`：作者用「写时间」标的章时间会写成一条
     * 「以章名为标题的事件」，所以本章 / 上一章的时间先找这条；没有再退到挂在该章上的
     * 已有事件里取最晚的。只有 `normalizeWhenText` / `storyTimeKey` 认得出的确定时间才
     * 拿来当锚——自由文本本身锚不了，只会把模型带偏。
     */
    private fun timeAnchorLines(book: Book, chapter: Chapter): List<String> {
        val events = book.bible.events
        val index = book.chapters.indexOfFirst { it.id == chapter.id }

        /** 某一章已知的确定时间：章名事件优先，其次挂在这章上、能排序的事件里最晚的一条。 */
        fun chapterTime(chapterIndex: Int): String? {
            val ch = book.chapters.getOrNull(chapterIndex) ?: return null
            events.firstOrNull { it.title == ch.title && it.whenText.isNotBlank() }?.let { e ->
                normalizeWhenText(e.whenText)?.let { return it }
            }
            var bestTime: String? = null
            var bestKey = Int.MIN_VALUE
            for (e in events) {
                if (ch.id !in e.chapterIds) continue
                val time = normalizeWhenText(e.whenText) ?: continue
                val key = storyTimeKey(time) ?: continue
                if (key >= bestKey) {
                    bestKey = key
                    bestTime = time
                }
            }
            return bestTime
        }

        val lines = ArrayList<String>(3)
        // 1) 本章已知时间（作者标过本章时间，或本章已有事件带确定时间）。
        chapterTime(index)?.let { lines += "本章已知时间：$it" }
        // 2) 按章序往前找最近一个有确定时间的章：绑「昨天 / 第二天 / 当天夜里」用。
        if (index > 0) {
            for (i in index - 1 downTo 0) {
                val time = chapterTime(i) ?: continue
                val where = if (i == index - 1) "上一章" else "往前第 ${index - i} 章"
                lines += "$where《${book.chapters[i].title}》的确定时间：$time"
                break
            }
        }
        // 3) 最近一条已有事件的确定时间（跨章，按 storyTimeKey 取最晚）：绑「当天夜里 / 傍晚」用。
        //    章名事件已经当成「章时间」用过了，这里不再重复列它。
        val chapterTitles = book.chapters.mapTo(HashSet<String>()) { it.title }
        var eventTime: String? = null
        var eventTitle = ""
        var eventKey = Int.MIN_VALUE
        for (e in events) {
            if (e.title in chapterTitles) continue
            val time = normalizeWhenText(e.whenText) ?: continue
            val key = storyTimeKey(time) ?: continue
            if (key >= eventKey) {
                eventKey = key
                eventTime = time
                eventTitle = e.title.ifBlank { e.whenText }
            }
        }
        val latestTime = eventTime
        if (latestTime != null && lines.none { it.endsWith(latestTime) }) {
            lines += "最近一条事件的确定时间：$latestTime · 事件「$eventTitle」"
        }
        if (lines.isEmpty()) {
            lines += "这本书还没有任何确定时间：正文里的「当天夜里 / 傍晚 / 昨天 / 第二天」照抄原文，不要推算日期"
        }
        return lines
    }

    // ───────────────────────── 3.26.56 长章分块 ─────────────────────────

    /** 一章正文里的一块：`start` / `end` 是相对「trim 过的整章正文」的下标，用来算跳过了多少字。 */
    private class ChapterChunk(val text: String, val start: Int, val end: Int)

    /**
     * 把一章正文切成约 [CHUNK_MIN_CHARS]–[CHUNK_MAX_CHARS] 字的块，相邻块重叠
     * [CHUNK_OVERLAP_CHARS] 字；切点优先段落边界，其次句末标点，都没有才硬切。
     *
     * 最多切 [MAX_CHUNKS_PER_CHAPTER] 块（≈1.4 万字）。上限之外的正文不读，调用方按最后一块的
     * [ChapterChunk.end] 把「还有多少字未读」写进日志，并记「部分状态」让下一次从断点续读。
     *
     * 3.26.59：[from] 是续读起点（上一轮已经读到第几个字；调用方会再往前让出
     * [CHUNK_OVERLAP_CHARS] 字，保住被切断的句子），默认 0 = 从头读整章。
     */
    private fun splitChunks(content: String, from: Int = 0): List<ChapterChunk> {
        if (content.isEmpty()) return emptyList()
        val first = from.coerceIn(0, content.length)
        if (content.length - first <= CHUNK_MAX_CHARS) {
            return listOf(ChapterChunk(content.substring(first), first, content.length))
        }
        val chunks = ArrayList<ChapterChunk>(MAX_CHUNKS_PER_CHAPTER)
        var start = first
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
     *  main thread so the 3-minute sync cannot race a body save and drop either side.
     *
     * 3.26.59：[appendBeats] = 这一章是「接着上一轮已读的部分继续读」（长章分次读完）时为 true，
     * 情节点按「上一轮写好的前半段 + 本轮尾段」去重拼接，不能拿尾段整段顶掉前半段。 */
    private suspend fun apply(
        ctx: Context,
        store: AppStore,
        bookId: String,
        chapterId: String,
        plan: Plan,
        appendBeats: Boolean = false,
    ) {
        withContext(Dispatchers.Main) {
            applyOnMain(ctx, store, bookId, chapterId, plan, appendBeats)
        }
    }

    private fun applyOnMain(
        ctx: Context,
        store: AppStore,
        bookId: String,
        chapterId: String,
        plan: Plan,
        appendBeats: Boolean,
    ) {
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
                val traits = args.optString("traits").trim()
                val faction = args.optString("faction").trim()
                val note = args.optString("note").trim()
                // 3.26.59：判据加上 traits / faction。role / note 之外的新字段如果还按旧的
                // 两字段判据，「只补性格 / 只补阵营」会被当成无变化跳过，新字段永远写不进去。
                if ((role.isBlank() || role == old.role) &&
                    (traits.isBlank() || traits == old.traits) &&
                    (faction.isBlank() || faction == old.faction) &&
                    (note.isBlank() || note == old.note)
                ) {
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
            // 3.26.59：时间解析不了会保留原文，所以「有没有时间」的判据统一改成
            // [hasDefiniteWhen]——空串和原文（如「当天夜里」）都算「还没确定时间」，任一侧
            // 没确定时间就按唯一标题合并；只有两端都是能排序的确定时间且不同才拆成两件事。
            // 不改的话，同一件事上一次记原文、这一次换成「第3天」或补出确定时间，会多一条同名事件。
            val byTitle = if (exact == null) {
                afterRelations.bible.events.filter { it.title == title }.takeIf { it.size == 1 }?.first()
                    ?.takeIf { !hasDefiniteWhen(whenText) || !hasDefiniteWhen(it.whenText) }
            } else {
                null
            }
            val old = exact ?: byTitle
            val extractedNames = eventNames(args)
            if (old == null) {
                args.put("chapterIds", JSONArray().put(chapterId))
            } else {
                args.put("id", old.id)
                // 老事件已经有确定时间、这次只拿到原文：不要把确定时间降级覆盖（原文只用来补空）。
                if (args.has("whenText") && !hasDefiniteWhen(whenText) && hasDefiniteWhen(old.whenText)) {
                    args.remove("whenText")
                }
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
            // 3.26.59：长章上一轮只读了前半段、这一轮从断点读尾段时（appendBeats），
            // 情节点按「前半段 + 尾段」去重拼接——拿尾段的几条整段顶掉旧情节点，前半段就丢了。
            val finalBeats = if (appendBeats && old != null) {
                val merged = ArrayList<String>(old.beats.size + plan.beats.size)
                val seen = HashSet<String>()
                old.beats.forEach { beat ->
                    if (seen.add(beatKey(beat.text))) merged += beat.text
                }
                plan.beats.forEach { text ->
                    if (seen.add(beatKey(text))) merged += text
                }
                merged
            } else {
                plan.beats
            }
            if (old != null && old.beats.map { it.text } == finalBeats) {
                unchanged++
            } else if (afterEvents.chapters.count { it.title == chapter.title } > 1) {
                // AiBible.mutate 的 outline 用章名定位章节：章名重名时可能写到别的章上，
                // 宁可不写情节点，也不把内容挂错。
                Log.d(TAG, "章名重复（${chapter.title}），跳过情节点")
                skipped++
            } else {
                val args = JSONObject().apply {
                    put("chapter", chapter.title)
                    put("beats", JSONArray(finalBeats))
                }
                if (AiBible.mutate(ctx, store, bookId, "outline", "upsert", args, source = "text")
                        .startsWith("已写入")
                ) {
                    beats = finalBeats.size
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
                    // 3.26.59：角色要求补 traits（性格+外貌）/ faction（阵营），role 与 note 的上限也放宽，
                    // 免得「身份」「动机」被截成半句；字段名与 Model.kt 的 StoryCharacter 同名字段一致。
                    put("role", str(o, "role").take(24))
                    put("traits", str(o, "traits").take(120))
                    put("faction", str(o, "faction").take(24))
                    put("note", str(o, "note").take(240))
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
                    // 3.26.59：时间归一化失败不再丢时间、更不能丢整条事件——能归一就写规范串
                    // （时间线 storyTimeKey 可排序），不能归一就把模型给的原文存进 whenText
                    // （如「当天夜里」），事件照常落库，界面按原文显示。
                    val rawWhen = str(o, "whenText").take(40)
                    val whenText = normalizeWhenText(rawWhen) ?: rawWhen
                    if (whenText.isNotBlank()) put("whenText", whenText)
                    // 3.26.59：note 要写「谁做了什么 → 结果 / 关键动作」，上限 160→280。
                    val note = str(o, "note").take(280)
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
                if (text.isNotBlank()) beats += text.take(120)
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
    // 能排序的三种形态。3.26.59 起：其它形态（「当天夜里」「傍晚」这类）**保留原文**，
    // 不再丢弃——prompt 会先让模型用 buildUser 给的时间锚点换算，换算不出才照抄原文；
    // 原文排不进时间线（storyTimeKey 返回 null，排在最后），但事件本身和它的时间说法不丢。
    private val whenIso = Regex("(\\d{4})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})\\s*[日号]?")
    private val whenMonthDay = Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?")
    private val whenRelative = Regex("([0-9]+|[零一二两三四五六七八九十百千]+)\\s*(个月|年|月|天|日|周|星期)\\s*(?:以|之)?\\s*(前|后)")
    private val whenDayIndex = Regex("第\\s*([0-9]+|[零一二两三四五六七八九十百千]+)\\s*[天日]")

    /**
     * 3.26.59：这段 whenText 是不是「能排序的确定时间」（[normalizeWhenText] 认得出）。
     * 空串和原文（「当天夜里」「傍晚」）都算「还没确定时间」——事件匹配、跨块合并都按这个判据。
     */
    private fun hasDefiniteWhen(raw: String): Boolean = normalizeWhenText(raw) != null

    /**
     * 把模型给的 whenText 压成 `YYYY-MM-DD` / `M月D日` / `N年[前|后]` / `第N天`。
     * 返回 null 表示这串时间归一不了：3.26.59 起调用方**保留原文**（写进 whenText），
     * 不再像以前那样把时间字段丢掉。
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
     * 冲突口径：**先写入的块优先**——前块已经给出的 role / traits / faction / note / relation /
     * whenText 不被后块覆盖；后块只能补空字段、给事件的人名集合补人（并集）。
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
                    put("traits", src.optString("traits").trim())
                    put("faction", src.optString("faction").trim())
                    put("note", src.optString("note").trim())
                }
            } else {
                fillBlank(old, src, "role")
                fillBlank(old, src, "traits")
                fillBlank(old, src, "faction")
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
            // 同名但两边都写了**不同、且都能排序**的确定时间 → 是两件事（与 applyOnMain 的判据一致），
            // 另存一条，免得后块的时间把前块已写死的时间顶掉、或整件事被合并丢掉。
            // 3.26.59：原文（normalizeWhenText 认不出，如「当天夜里」）和空串都算「还没确定时间」，
            // 不算冲突——否则同一件事跨块会被拆成两条同名事件。
            val old = if (base == null) null else {
                val baseWhen = base.optString("whenText").trim()
                if (hasDefiniteWhen(srcWhen) && hasDefiniteWhen(baseWhen) && srcWhen != baseWhen) {
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
                // 确定时间优先：前块只有原文、后块给了能排序的时间 → 升级成确定时间，不丢提升。
                val oldWhen = old.optString("whenText").trim()
                if (hasDefiniteWhen(srcWhen) && !hasDefiniteWhen(oldWhen)) {
                    old.put("whenText", srcWhen)
                } else {
                    fillBlank(old, src, "whenText")
                }
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

    /** 记一章「已按这份正文整章整理过」。失败只记日志，不影响这一轮。 */
    private fun recordHash(ctx: Context, bookId: String, chapterId: String, hash: String) =
        recordState(ctx, bookId, chapterId, hash)

    /**
     * 3.26.59：记「部分状态」`正文hash#已读字数`——长章读到块数上限时用。
     * 下一轮扫全书按 [isFullySynced] 判为「没读完」，会从该字数续读；正文改了 hash 对不上，
     * 自动从第 0 字重来。
     */
    private fun recordPartialState(ctx: Context, bookId: String, chapterId: String, hash: String, readChars: Int) =
        recordState(ctx, bookId, chapterId, partialState(hash, readChars))

    private fun partialState(hash: String, readChars: Int): String = "$hash#$readChars"

    /** 侧车值里「正文 hash」；`hash#N` 取 '#' 前那半，纯 hash 老数据原样返回。 */
    private fun syncedHashOf(value: String?): String? =
        value?.substringBefore('#')?.takeIf { it.isNotBlank() }

    /** 侧车值里「已读到第几个字」；整章读完 / 老版本纯 hash 都是 0。 */
    private fun readCursorOf(value: String?): Int =
        value?.substringAfter('#', "")?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    /** 这一章是不是「按当前正文整章读完了」：hash 对得上，且没有未读完的断点。 */
    private fun isFullySynced(value: String?, currentHash: String?): Boolean =
        currentHash != null && syncedHashOf(value) == currentHash && readCursorOf(value) == 0

    /** 侧车统一的写入口：只记 hash（整章读完）或 `hash#已读字数`（部分读完）。 */
    private fun recordState(ctx: Context, bookId: String, chapterId: String, value: String) {
        synchronized(lock) {
            val root = loadRoot(ctx)
            val book = root.optJSONObject(bookId) ?: JSONObject()
            book.put(chapterId, value)
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
1. characters 角色：本章出场、有戏份的人物全部列出，每个角色尽量写全下面几项。故事圣经里已经有的角色也一样——正文这次给了新信息就要补全，不要因为「已经有这条」就把字段留空：
   - name：正文里的叫法。
   - role：身份（主角/配角/反派/师长等），正文没写就留空。
   - traits：性格 + 外貌，两三句，必须来自正文依据（例：「面上温和，说话慢，遇事先退半步；左眉有道旧疤，常穿靛青旧袍」）。
   - faction：阵营 / 所属（家族、门派、势力、职位等），正文没写就留空。
   - note：动机——他想要什么、在追求什么，只依据正文。
   只有正文这次确实没给某个角色的新信息时，才把那一项留空；不要为了省字数把正文已经写出来的信息丢掉。
2. relations 关系：本章已经写明的关系。from / to 是人名，必须出现在 characters 或已有故事圣经里；relation 只能用：${RELATION_TYPES_TOKEN}
3. events 事件：本章已经发生的事。title 一句话说清发生了什么；note 按「谁做了什么 → 结果 / 关键动作」写一两句（不要只写一句空泛概括，也不要把整章复述一遍）；characters 列出牵涉的人名；whenText 必须按下面的时间格式写。
4. outline.beats 情节点：这一章实际写出来的情节，按正文顺序，每条按「起因 → 动作 → 结果」写清一条链，3–8 条。

whenText 时间格式（优先规范化，只有下面三种规范写法 + 照抄原文）：
A. 明确日期：「YYYY-MM-DD」，如 1987-03-15；只知道月日写「M月D日」，如 3月15日。
B. 相对时间：阿拉伯数字 + 年/个月/天/周 + 前/后，如「3年前」「2个月后」「5天后」；原文写「三年前」也要改写成「3年前」。
C. 第几天：「第N天」，如「第3天」。
D. 原文只有「当天夜里」「傍晚」「昨天」这类没法归一成上面三种的说法时：先看用户消息里的「时间锚点」，能换算成锚点上的日期 / 第几天就按 A–C 写（例：锚点是「第3天」，正文写「当天夜里」→ 写「第3天」；锚点是具体日期、正文写「第二天」→ 按日期顺推那天）；实在换算不出来就照抄原文（如「当天夜里」），不要自己编日期，也不要把 whenText 留空。

硬性规则：
- 只写正文里已经写明的。「暗示」「计划」「以后要发生」都不算，不要编造、不要补设定、不要把作者的计划当成已经发生。
- 正文里没出现过的名字一律不写。
- 同一件事不要换说法重复输出。
- 关系两端必须是人物；拿不准是什么关系就写「其他」。
- relation 只能用上面列出的类型名（内置 8 种，或这本书已有的自定义类型）；自定义类型必须一字不差照抄，不要自己造近义词。
- 四类都没有可提取的内容时，只输出 {"skip":true}。

只输出一个 JSON 对象，不要解释，不要 Markdown 代码块，不要前后缀文字。格式：
{"characters":[{"name":"","role":"","traits":"","faction":"","note":""}],"relations":[{"from":"","to":"","relation":""}],"events":[{"whenText":"","title":"","note":"","characters":[""]}],"outline":{"beats":[""]}}
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
