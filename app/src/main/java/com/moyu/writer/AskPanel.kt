package com.moyu.writer

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 「问」的系统提示：只针对选中的那一段，不越界、不假装改过正文。
 *
 * 3.26.45：上下文从「选区 + 上 200 字」扩到「选区 + 上 600 字 + 章名/字数/位置 +
 * 本章摘要 + 故事圣经摘要」，所以这里要把「能看到什么」写清楚——不然模型会拿摘要
 * 当正文，或者拿【设定】里的规划当已经写进书里的事实。
 */
const val ASK_SYSTEM =
    "你是作者的写作助手，只处理他选中的那一小段正文。回答用中文，简短、具体，不要说客套话。\n" +
        "作者问问题时，直接答；作者要一版改写时，只输出改写后的那一段文字本身，" +
        "不要前言、不要解释、不要引号、不要用「好的，以下是…」开头。\n" +
        "这一轮你能看到的只有下面这几块，没给到的就是看不到：\n" +
        "· 【选中】：他选中的原文，是这次要处理的正文；\n" +
        "· 【紧接在上文】：选中之前的一小段（最多 600 字），用来接语气、承上文；\n" +
        "· 【章节】：章名、本章已写多少字、选中大概在章内什么位置；\n" +
        "· 【本章摘要】：本章给读者的前情提要（拿到摘要才给）；\n" +
        "· 【设定】：故事圣经摘要（角色、关系、事件、大纲；太长会被截断）。\n" +
        "这几块之外的正文、别的章节、他的聊天记录，你都看不到。" +
        "问「前面写过什么」时，只用【本章摘要】【设定】【紧接在上文】里写到的回答，" +
        "并说清是摘要还是原文；三处都没有就直说「这一轮看不到」，不要编。\n" +
        "不要把【设定】里的规划当成已经写进正文的事实。\n" +
        "你从来不自己改正文：改不改由作者点「替换选中」决定。"

/**
 * 选段「问」的一条消息（3.26.32）。
 *
 * `applied` = 这条回答已经替换回正文；`stale` = 正文在这期间被改过，回答作废。
 * 只活在内存里，不进 moyu.json。
 */
data class AskTurn(
    val id: String,
    val mine: Boolean,
    val text: String,
    val applied: Boolean = false,
    val stale: Boolean = false,
)

/**
 * 这一轮「问」实际带进 prompt 的上下文（3.26.45）。
 *
 * 只做数据：怎么截断在 [AskContextBuilder] 里，怎么发在 [AskStream] 里，
 * 怎么显示在 [AskChat] 里。三者分开是为了让 Editor 接线时只换一行。
 */
data class AskContext(
    val user: String,
    val chars: Int,
    /** 有块因为超预算被截短 / 整块丢掉了。 */
    val trimmed: Boolean = false,
) {
    /** 粗估 token（中文约 1.4–1.6 字/token，按 1.5 算）。 */
    val approxTokens: Int get() = chars * 2 / 3
}

/**
 * 「问」的跨段上下文拼装（3.26.45）。
 *
 * 3.26.32 只带「选区 + 紧接上文 200 字 + 章名 + 圣经摘要」，于是「前面写过什么」这类
 * 问题答不上来。现在按这个顺序给：选区 → 上 600 字 → 章名/本章字数/选中位置 →
 * 本章摘要（问书记忆层里的那一份）→ 故事圣经摘要，最后是之前几轮对话。
 *
 * **上限**：user 段硬上限 [MAX_CHARS] 字（约 4k token），加上 system 与 1600 token
 * 的输出预算，仍然远低于服务商的上下文上限。超预算时按重要度从后往前砍：
 * 对话 → 圣经 → 本章摘要 → 紧接上文（选区与问题永远给）。
 *
 * 接线（Editor.sendAsk，本轮没做；改完把 `AskChat(streaming = …)` 一起传上）：
 * ```
 * scope.launch {
 *     val askCtx = AskContextBuilder.forBook(
 *         context, proofBookId?.let { store.book(it) }, seedKey,
 *         chapterTitle = title.ifBlank { "未命名" },
 *         preview = askPreview,
 *         before = body.text.substring(0, askRange.first.coerceIn(0, body.text.length)),
 *         question = q, history = askTurns, chapterWords = body.text.length,
 *     )
 *     val r = AskStream.chat(
 *         baseUrl = settings.aiBaseUrl, model = settings.aiModel, key = key,
 *         temperature = .6f, system = ASK_SYSTEM, user = askCtx.user,
 *     ) { delta -> askStream += delta }
 *     …
 * }
 * ```
 */
object AskContextBuilder {
    /** user 段最多带多少字符（不含 system）。 */
    const val MAX_CHARS = 6_000

    /** 紧接上文最多取多少字（任务要求 600）。 */
    const val BEFORE_CHARS = 600

    /** 选区太长时只放前这么多字。 */
    const val PREVIEW_CHARS = 3_000

    /** 本章摘要的上限。 */
    const val SUMMARY_CHARS = 320

    /** 故事圣经摘要的上限。 */
    const val BIBLE_CHARS = 1_600

    /** 前几轮对话的上限（从最新一轮往回拼，整条收）。 */
    const val HISTORY_CHARS = 1_200

    const val QUESTION_CHARS = 800
    const val TITLE_CHARS = 60

    /**
     * @param before 选中之前的**整段**正文（builder 自己取最后 [BEFORE_CHARS] 字），
     *   不是只传 600 字：长度还用来告诉模型「选中在章内第几字」。
     * @param chapterWords 本章总字数；不知道传 0。
     */
    fun build(
        chapterTitle: String,
        preview: String,
        before: String,
        chapterSummary: String = "",
        bibleSummary: String = "",
        history: List<AskTurn> = emptyList(),
        question: String = "",
        chapterWords: Int = 0,
    ): AskContext {
        val q = question.trim().take(QUESTION_CHARS)
        val title = chapterTitle.trim().ifBlank { "未命名" }.take(TITLE_CHARS)
        val previewFull = preview.trim()
        var previewBody = previewFull
        var trimmed = false
        if (previewBody.length > PREVIEW_CHARS) {
            previewBody = previewBody.take(PREVIEW_CHARS)
            trimmed = true
        }
        // ① 章节行：章名 + 本章总字数 + 选中在章里的位置。
        //    「在第几字」是让模型知道自己站在开头还是章末——几百字的上文看不到全章。
        val chapterLine = buildString {
            append("【章节】").append(title)
            val bits = ArrayList<String>()
            if (chapterWords > 0) bits += "本章已写 $chapterWords 字"
            if (before.isNotBlank()) bits += "选中在约第 ${before.length + 1} 字处"
            if (bits.isNotEmpty()) append("（").append(bits.joinToString("，")).append("）")
            append('\n')
        }
        // ② 必须先给的：选中 + 这一问。两者永远完整（选区超长才砍）。
        val selection = buildString {
            append("【选中】\n").append(previewBody).append('\n')
            if (previewBody.length < previewFull.length) {
                append("（选中一共 ${previewFull.length} 字，这里只到前 $PREVIEW_CHARS 字）\n")
            }
        }
        val questionLine = if (q.isBlank()) "" else "作者：$q\n"

        var budget = (MAX_CHARS - chapterLine.length - selection.length - questionLine.length)
            .coerceAtLeast(0)

        /** 拼一块「【标签】+ 正文」；预算不够就截短或整块不拼。 */
        fun block(label: String, text: String, cap: Int): String {
            val body = text.trim()
            if (body.isEmpty() || budget <= 0) return ""
            val head = "【$label】\n"
            val room = minOf(cap, budget - head.length - 1)
            if (room <= 0) return ""
            var cut = body
            if (cut.length > room) {
                cut = cut.take(room)
                trimmed = true
            }
            val out = head + cut + "\n"
            if (out.length > budget) return ""
            budget -= out.length
            return out
        }

        // ③ 可裁的块按重要度分预算：紧接上文 → 本章摘要 → 圣经摘要 → 对话。
        val beforeText = if (before.length > BEFORE_CHARS) before.takeLast(BEFORE_CHARS) else before
        val beforeBlock = block("紧接在上文", beforeText, BEFORE_CHARS)
        val summaryBlock = block("本章摘要", chapterSummary, SUMMARY_CHARS)
        val bibleBlock = block("设定", bibleSummary, BIBLE_CHARS)

        // ④ 之前几轮：从最新往回拼，整条收，不切半句。
        var historyBlock = ""
        if (history.isNotEmpty() && budget > 0) {
            val head = "【对话】\n"
            var room = minOf(HISTORY_CHARS, budget - head.length)
            if (room > 0) {
                val lines = ArrayList<String>()
                var used = 0
                for (t in history.asReversed()) {
                    if (t.text.isBlank()) continue
                    val line = (if (t.mine) "作者：" else "AI：") + t.text.trim()
                    if (used + line.length + 1 > room) break
                    lines.add(0, line)
                    used += line.length + 1
                }
                if (lines.size < history.count { it.text.isNotBlank() }) trimmed = true
                if (lines.isNotEmpty()) {
                    historyBlock = head + lines.joinToString("\n") + "\n"
                    budget -= historyBlock.length
                }
            }
        }

        // 显示顺序沿用旧 prompt：章节 → 本章摘要 → 设定 → 紧接上文 → 选中 → 对话 → 问题。
        val user = chapterLine + summaryBlock + bibleBlock + beforeBlock +
            selection + historyBlock + questionLine
        return AskContext(user = user, chars = user.length, trimmed = trimmed)
    }

    /**
     * 从一本书里把上下文抓齐：圣经摘要走内存，本章摘要读问书记忆层（IO）。
     * Editor 接线时直接用它，省得自己在主线程读记忆文件。
     */
    suspend fun forBook(
        ctx: Context,
        book: Book?,
        chapterId: String?,
        chapterTitle: String,
        preview: String,
        before: String,
        question: String,
        history: List<AskTurn> = emptyList(),
        chapterWords: Int = 0,
    ): AskContext = build(
        chapterTitle = chapterTitle,
        preview = preview,
        before = before,
        chapterSummary = chapterSummary(ctx, book?.id, chapterId),
        // 圣经还是空的时候 summary 只会给一句「还空着」，别占预算。
        bibleSummary = if (book != null && !book.bible.isEmpty) AiBible.summary(book) else "",
        history = history,
        question = question,
        chapterWords = chapterWords,
    )

    /** 本章摘要：问书记忆层里 `filesDir/ai/<bookId>.json` 的那一份。读不到就空，不报错。 */
    suspend fun chapterSummary(ctx: Context, bookId: String?, chapterId: String?): String {
        if (bookId.isNullOrBlank() || chapterId.isNullOrBlank()) return ""
        return withContext(Dispatchers.IO) {
            runCatching {
                MemoStore.load(ctx, bookId)
                    .chapters
                    .firstOrNull { it.chapterId == chapterId }
                    ?.summary
                    .orEmpty()
            }.getOrDefault("")
        }
    }
}

/**
 * 「问」的流式请求（3.26.45）。
 *
 * 3.26.32 用 [AiClient.chat] 一次性拿整段，面板里只有「正在想…」，长回答要干等十几秒。
 * 这里按 OpenAI-compatible 的 SSE 自己解：POST `stream: true`，逐行读 `data:`，
 * `choices[0].delta.content` 是增量，`data: [DONE]` 收尾。
 *
 * 三条退化路径，用户都不会白等：
 * ① 服务端不认 `stream`、直接回整包 JSON → 按一次性回答收下，照常回调一次；
 * ② SSE 走完一个字都没有（网关直接关流）→ 再发一次非流式请求兜底；
 * ③ 流到一半断网 → 已收到的部分交回去（[Result.partial]），UI 可以显示「没写完」。
 *
 * [onDelta] 在 IO 线程回调；只往里写 Compose 状态是安全的（Snapshot 状态可跨线程写），
 * 要碰 View 请自己切主线程。
 *
 * 接线（Editor.sendAsk，本轮没做）：见 [AskContextBuilder] 的注释，把那里的
 * `user = askCtx.user` 与 `onDelta` 接上，再把 `AskChat(streaming = askStream)` 传进来。
 */
object AskStream {
    /** 一次流式问答的结果。`text` 是已经收到的全部内容（可能为空）。 */
    data class Result(
        val text: String = "",
        val error: String? = null,
        val modelHint: String? = null,
    ) {
        val ok: Boolean get() = error == null && text.isNotBlank()

        /** 收到了一部分但流在半路断了：既有 text 也有 error。 */
        val partial: Boolean get() = text.isNotBlank() && error != null
    }

    suspend fun chat(
        baseUrl: String,
        model: String,
        key: String,
        temperature: Float,
        system: String,
        user: String,
        maxTokens: Int = 1600,
        disableThinking: Boolean = false,
        onDelta: (String) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext Result(error = "还没填 Key")
        if (baseUrl.isBlank()) return@withContext Result(error = "还没填服务地址")
        val endpoint = baseUrl.trim().trimEnd('/') + "/chat/completions"
        val got = StringBuilder()
        var conn: HttpURLConnection? = null
        var streamError: String? = null

        val result = runCatching {
            val c = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                // 流式：每段之间可能有停顿，但整段回答不该超过两分钟。
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "text/event-stream")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Authorization", "Bearer $key")
            }
            conn = c
            val payload = JSONObject().apply {
                put("model", model)
                put("temperature", temperature.toDouble().coerceIn(0.0, 1.4))
                put("max_tokens", maxTokens)
                put("stream", true)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", system) })
                    put(JSONObject().apply { put("role", "user"); put("content", user) })
                })
                if (disableThinking) put("thinking", JSONObject().put("type", "disabled"))
            }
            c.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code !in 200..299) {
                val body = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Result(error = AiClient.explain(code, body), modelHint = AiClient.suggestedModel(body))
            } else {
                var done = false
                c.inputStream.bufferedReader().use { reader ->
                    while (!done) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        // SSE 注释（心跳）：`:` 开头，直接丢。
                        if (line[0] == ':') continue
                        if (line.startsWith("data:")) {
                            val data = line.substring(5).trim()
                            if (data.isEmpty()) continue
                            if (data == "[DONE]") {
                                done = true
                                continue
                            }
                            val obj = runCatching { JSONObject(data) }.getOrNull() ?: continue
                            if (obj.has("error")) {
                                streamError = obj.optJSONObject("error")
                                    ?.optString("message")
                                    ?.takeIf { it.isNotBlank() }
                                    ?: obj.optString("error").takeIf { it.isNotBlank() }
                                    ?: "服务在流里报了错"
                                done = true
                                continue
                            }
                            val deltaObj = obj.optJSONArray("choices")
                                ?.optJSONObject(0)
                                ?.optJSONObject("delta")
                            // delta.content 为 null 的是 tool/reasoning 增量，跳过不显示。
                            val delta = if (deltaObj != null && !deltaObj.isNull("content")) {
                                deltaObj.optString("content")
                            } else ""
                            if (delta.isNotEmpty()) {
                                got.append(delta)
                                onDelta(delta)
                            }
                        } else if (line.startsWith("{")) {
                            // 有网关无视 stream、直接回整包 JSON：当一次性回答收下。
                            val obj = runCatching { JSONObject(line) }.getOrNull() ?: continue
                            val text = obj.optJSONArray("choices")
                                ?.optJSONObject(0)
                                ?.optJSONObject("message")
                                ?.optString("content")
                                .orEmpty()
                            if (text.isNotEmpty()) {
                                got.append(text)
                                onDelta(text)
                                done = true
                            }
                        }
                    }
                }
                if (streamError != null) {
                    Result(text = got.toString(), error = streamError)
                } else {
                    Result(text = got.toString())
                }
            }
        }.getOrElse { e ->
            if (got.isEmpty()) {
                Result(error = AiClient.networkError(e))
            } else {
                // 半路断网：已经出来的部分留着，UI 至少能显示它。
                Result(text = got.toString(), error = "回答没写完：" + AiClient.networkError(e))
            }
        }
        runCatching { conn?.disconnect() }
        // ② 一个字都没收到（服务端不认 stream / 网关直接关流）才兜底，避免白等一次。
        if (result.text.isBlank() && result.error == null) {
            return@withContext fallback(
                baseUrl = baseUrl, model = model, key = key, temperature = temperature,
                system = system, user = user, maxTokens = maxTokens,
                disableThinking = disableThinking, onDelta = onDelta,
            )
        }
        result
    }

    /** 流一个字都没回：退一次非流式请求，整段当一次增量交出去。 */
    private suspend fun fallback(
        baseUrl: String,
        model: String,
        key: String,
        temperature: Float,
        system: String,
        user: String,
        maxTokens: Int,
        disableThinking: Boolean,
        onDelta: (String) -> Unit,
    ): Result {
        val r = AiClient.chat(
            baseUrl = baseUrl,
            model = model,
            key = key,
            temperature = temperature,
            system = system,
            user = user,
            maxTokens = maxTokens,
            disableThinking = disableThinking,
        )
        return if (r.ok) {
            onDelta(r.text)
            Result(text = r.text)
        } else {
            Result(error = r.error ?: "服务没有返回内容", modelHint = r.modelHint)
        }
    }
}

/**
 * 选段「问」的内容区：塞进校核/修饰那块右侧浮窗里（`ProofSidePanel(content = …)`）。
 *
 * 顶部是选区预览（三行，点一下展开），中间是消息流，底部是输入行。
 * 每条 AI 回答下面只有「替换选中」——默认不写正文，写了也能一键撤销。
 *
 * 3.26.45：加了 [streaming]——流式回答边收边画在这里，但**不显示「替换选中」**；
 * 等 Editor 把最后一段落成一条完整的 [AskTurn] 之后，按钮才随那条回答出现
 * （没落定就允许替换，等于把一个残句写进稿子）。
 */
@Composable
fun ColumnScope.AskChat(
    preview: String,
    chars: Int,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    turns: List<AskTurn>,
    input: String,
    onInput: (String) -> Unit,
    busy: Boolean,
    error: String?,
    canWrite: Boolean,
    onSend: () -> Unit,
    onApply: (AskTurn) -> Unit,
    /** 正在流式接收的回答（已收到的部分）；空 = 还没收到第一段。 */
    streaming: String = "",
) {
    val ui = LocalInk.current
    val p = ui.palette
    // 3.26.48：本面板新补的动效统一读这一个开关；motion=none 时全部退化成 tween(0)。
    val motionOn = ui.settings.motion != "none"
    val list = rememberLazyListState()
    val extra = (if (busy) 1 else 0) + (if (error != null) 1 else 0)
    // 每来一条、每收够一小段（120 字一档）就跟到底，不是每个 token 都滚一次。
    LaunchedEffect(turns.size, busy, streaming.length / 120) {
        val count = turns.size + extra
        if (count > 0) list.animateScrollToItem(count - 1)
    }

    Row(
        Modifier
            .fillMaxWidth()
            // 3.26.48：选区预览展开/收起时高度用同一根 spring 长出来，不再当帧跳。
            .animateContentSize(
                animationSpec = if (motionOn) {
                    spring(dampingRatio = .6f, stiffness = 380f)
                } else {
                    tween(0)
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onToggleExpand() }
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Txt(
            preview,
            size = 10.5f,
            color = p.muted,
            lineHeight = 15f,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Txt(
            if (expanded) "收起" else "$chars 字",
            size = 9f,
            color = p.muted.copy(alpha = .7f),
        )
    }
    RowDivider(inset = 13.dp)

    LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        state = list,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 13.dp,
            vertical = 10.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (turns.isEmpty() && !busy) {
            item {
                Txt(
                    "就这一段问一句：更冷一点 / 换个开头 / 这里节奏是不是太快",
                    size = 10.5f,
                    color = p.muted,
                    lineHeight = 16f,
                )
            }
        }
        items(turns, key = { it.id }) { t ->
            // 3.26.48：新的一轮 160ms 淡入，后面的轮次弹簧落位；
            // motion=none 时两个 spec 都是 tween(0)。
            val itemAnim = Modifier.animateItem(
                fadeInSpec = tween(if (motionOn) 160 else 0),
                placementSpec = if (motionOn) {
                    spring(dampingRatio = .6f, stiffness = 380f)
                } else {
                    tween(0)
                },
            )
            if (t.mine) {
                Row(itemAnim.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(
                        Modifier
                            .fillMaxWidth(.88f)
                            .clip(SmoothCorner(12.dp))
                            .background(p.faint)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Txt(t.text, size = 11f, color = p.ink, lineHeight = 16f)
                    }
                }
            } else {
                Column(itemAnim.fillMaxWidth()) {
                    AiMarkdown(t.text, size = 11.5f, lineHeight = 17f)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when {
                            t.applied -> Txt("已替换选中", size = 9f, color = p.muted)
                            t.stale -> Txt("这段已经改过，这条作废", size = 9f, color = p.muted)
                            // 3.26.45：写回正文前先去掉 markdown 标记——气泡里照旧按
                            // markdown 排版，但「**」不能落到稿子里（3.26.33 的坑）。
                            else -> AskPill("替换选中", canWrite) {
                                onApply(t.copy(text = mdPlain(t.text)))
                            }
                        }
                    }
                }
            }
        }
        if (busy) {
            item(key = "ask-progress") {
                if (streaming.isNotBlank()) {
                    // 边收边显示：已经收到的部分直接按回答排版画出来。
                    Column(Modifier.fillMaxWidth()) {
                        AiMarkdown(streaming, size = 11.5f, lineHeight = 17f)
                        Spacer(Modifier.height(4.dp))
                        Txt("正在写…", size = 9f, color = p.muted)
                    }
                } else {
                    Txt("正在想…", size = 10f, color = p.muted)
                }
            }
        }
        if (error != null) {
            item(key = "ask-error") {
                Txt(error, size = 10f, color = p.muted, lineHeight = 15f)
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            InkField(
                value = input,
                onValueChange = onInput,
                placeholder = "问问这一段…",
                singleLine = false,
                minHeight = 38.dp,
                textSize = 12f,
                maxLines = 3,
            )
        }
        Spacer(Modifier.width(7.dp))
        val on = !busy && input.isNotBlank()
        Box(
            Modifier
                .height(34.dp)
                .clip(SmoothCorner(12.dp))
                .background(if (on) ui.accent else p.faint)
                .bouncy(enabled = on) { if (on) onSend() }
                .padding(horizontal = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Txt(
                if (busy) "…" else "发送",
                size = 10.5f,
                color = if (on) p.paper else p.muted,
            )
        }
    }
}

@Composable
private fun AskPill(text: String, enabled: Boolean, onClick: () -> Unit) {
    val ui = LocalInk.current
    val p = ui.palette
    Box(
        Modifier
            .height(26.dp)
            .clip(SmoothCorner(10.dp))
            .background(if (enabled) p.faint else p.faint.copy(alpha = .5f))
            .bouncy(enabled = enabled, scale = .94f) { if (enabled) onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, size = 9.5f, color = if (enabled) p.ink else p.muted)
    }
}
