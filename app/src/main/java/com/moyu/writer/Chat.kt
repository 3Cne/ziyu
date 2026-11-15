package com.moyu.writer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/**
 * Lifecycle of one reply, mirroring the three states the DSH chat view gives an
 * assistant node: it is either still running, settled, or was interrupted.
 */
enum class ChatStatus { Running, Settled, Interrupted }

/**
 * One row of the transcript.
 *
 * A reply is born [ChatStatus.Running], grows **in place**, and flips to
 * Settled or Interrupted on the same row. Its [id] never changes, so the list
 * cannot show the same answer twice — which is exactly what the previous
 * design did: committed turns lived in `memo.turns`, the streaming reply lived
 * in `streamText`, and both were rendered at once while the answer committed.
 */
/**
 * One tool the assistant called while producing an answer. Kept on the row so
 * the transcript can show what happened without a second message stream.
 */
data class ToolTrace(
    val id: String,
    val name: String,
    val label: String,
    val args: String,
    val result: String,
    val ok: Boolean,
    val elapsedMs: Long,
)

/**
 * 一条已经落地的改动，挂在产生它的那条回答下面。
 *
 * 只活在会话内存里：不进 MemoTurn、不进 moyu.json。`kind` 为 "info" 的只是
 * 一行说明（改名 / 建章 / 待办 / 记忆），没有撤销；"append"/"replace" 才是正文
 * 改动，带原文与改后，可以单独退回。
 */
data class ChatEdit(
    val id: String,
    val label: String,
    /** "append" | "replace" | "info" */
    val kind: String,
    val bookId: String = "",
    val chapterId: String = "",
    /** replace：被换掉的原文片段；append：空。 */
    val before: String = "",
    /** 写进去的片段。 */
    val after: String = "",
    val undone: Boolean = false,
)

data class ChatMsg(
    val id: String,
    val role: String,
    val text: String,
    val refs: List<String> = emptyList(),
    val status: ChatStatus = ChatStatus.Settled,
    val error: String? = null,
    val tools: List<ToolTrace> = emptyList(),
    /** 深度思考的 reasoning 原文；只在本次会话里保留，不进历史。 */
    val reasoning: String = "",
    /** 这一轮落地的改动（只活在本会话内存里）。 */
    val edits: List<ChatEdit> = emptyList(),
)

/**
 * The transcript as one ordered list of rows with stable ids.
 *
 * Persistence still goes through [MemoTurn]: only settled rows are written, so
 * a process death in the middle of a reply loses that reply instead of storing
 * a half sentence as history.
 */
class ChatSession {
    val messages = mutableStateListOf<ChatMsg>()
    private var seq = 0

    /** Replace the transcript with stored history (once per book). */
    fun load(turns: List<MemoTurn>) {
        messages.clear()
        seq = 0
        turns.forEach { t ->
            seq += 1
            messages += ChatMsg("h$seq", t.role, t.text, t.refs, ChatStatus.Settled)
        }
    }

    fun addUser(text: String, refs: List<String>) {
        seq += 1
        messages += ChatMsg("u$seq", "user", text, refs, ChatStatus.Settled)
    }

    /** Append an empty running row and hand back its id. */
    fun beginAnswer(): String {
        seq += 1
        val id = "a$seq"
        messages += ChatMsg(id, "assistant", "", emptyList(), ChatStatus.Running)
        return id
    }

    fun update(id: String, text: String) {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) messages[i] = messages[i].copy(text = text)
    }

    fun setTools(id: String, tools: List<ToolTrace>) {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) messages[i] = messages[i].copy(tools = tools)
    }

    /** Keep the model's reasoning for the live transcript (never persisted). */
    fun setReasoning(id: String, reasoning: String) {
        if (reasoning.isBlank()) return
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) messages[i] = messages[i].copy(reasoning = reasoning.take(8_000))
    }

    fun settle(id: String, refs: List<String>) {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) messages[i] = messages[i].copy(status = ChatStatus.Settled, refs = refs)
    }

    /** 这一轮落地的改动挂到这条回答下面。 */
    fun addEdits(id: String, edits: List<ChatEdit>) {
        if (edits.isEmpty()) return
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) messages[i] = messages[i].copy(edits = messages[i].edits + edits)
    }

    /** 逐条撤销后把那一张卡标成已撤销（不删卡：作者看得见自己退过哪一条）。 */
    fun markEditUndone(id: String, editId: String) {
        val i = messages.indexOfFirst { it.id == id }
        if (i < 0) return
        messages[i] = messages[i].copy(
            edits = messages[i].edits.map { if (it.id == editId) it.copy(undone = true) else it },
        )
    }

    fun fail(id: String, message: String) {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) messages[i] = messages[i].copy(status = ChatStatus.Interrupted, error = message)
    }

    /** Drop the running row, e.g. after a failed first round with no text yet. */
    fun drop(id: String) {
        messages.removeAll { it.id == id }
    }

    fun toTurns(): List<MemoTurn> = messages
        .filter { it.status != ChatStatus.Running }
        .map { MemoTurn(it.role, it.text, it.refs) }
}

/**
 * Bottom-follow intent for one lazy list, ported from the DSH chat view's
 * `use-scroll-follow`.
 *
 * The old code flipped a boolean from "is the last item visible", so the
 * program's own scrolls read as reader movement — every follow scroll made the
 * list "not at the bottom" for a frame and cancelled the intent it had just
 * served, while a reader who scrolled up got yanked back by the next token.
 *
 * Here intent and motion are separate:
 * - a program scroll ([beginProgramScroll] … [endProgramScroll]) never changes
 *   [following] while it is running;
 * - only a reader gesture ([readerMoved]) may turn follow off;
 * - [nearBottom] uses a tolerance, not an exact comparison.
 */
class ScrollFollow(thresholdPx: Float = 72f) {
    /** Accepted distance from the floor, in pixels. */
    var threshold by mutableStateOf(thresholdPx)

    /** Whether content growth should keep the tail in view. */
    var following by mutableStateOf(true)
        private set

    private var animating = false

    fun nearBottom(top: Float, floor: Float): Boolean = abs(floor - top) <= threshold

    /** The reader's finger moved the list: the only thing that changes intent. */
    fun readerMoved(top: Float, floor: Float) {
        if (animating) return
        following = nearBottom(top, floor)
    }

    /**
     * Reader-geometry form used by a [androidx.compose.foundation.lazy.LazyList]:
     * `atBottom` is `!canScrollForward`, which is false as soon as the tail can
     * move further. Program scrolls never call this.
     */
    fun readerMovedAtBottom(atBottom: Boolean) {
        if (animating) return
        following = atBottom
    }

    fun beginProgramScroll() {
        animating = true
    }

    /** Adopt where the program scroll landed as the new intent. */
    fun endProgramScroll(top: Float, floor: Float) {
        animating = false
        following = nearBottom(top, floor)
    }

    /** Explicit "jump to newest": follows again from here. */
    fun followNow() {
        animating = false
        following = true
    }

    fun reset() {
        animating = false
        following = true
    }
}
