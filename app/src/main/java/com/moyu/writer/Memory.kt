package com.moyu.writer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 3.26.64：问书页与 AI 工具下线后，这里只剩「校核 / 修饰 / 问」还要读的那一层——
 * 每本书一个记忆文件 `filesDir/ai/<bookId>.json`：
 *  - `chapters` 是各章摘要（「问」的本章摘要从这里读，见 AskPanel.chapterSummary）；
 *  - `turns` 是旧问书留下的对话记录（AiCleanup 首启清空，格式保留以免旧备份解析失败）。
 *
 * 已删除：facts 长期记忆层、引文引用、检索索引（跨章检索改由 AskPanel 里的
 * AskCross 纯本地实现）。
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

data class MemoTurn(
    val role: String,
    val text: String,
    /** Chapter titles the answer leaned on, so the bubble can offer jumps. */
    val refs: List<String> = emptyList(),
)

data class BookMemo(
    val bookId: String,
    val chapters: List<ChapterMemo> = emptyList(),
    val turns: List<MemoTurn> = emptyList(),
    val updatedAt: Long = 0L,
)

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
        return BookMemo(bookId, chapters, turns, o.optLong("updatedAt"))
    }
}
