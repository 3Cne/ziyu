package com.moyu.writer

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 3.26.64：一次性清掉问书 / 自动整理留在本机数据里的东西。
 *
 * 用户拍板彻底删掉问书与全部 AI 功能（校核 / 修饰 / 问保留），并要求把已经被 AI
 * 写进故事圣经的数据一起清掉；用户确认「我有备份」，所以清理可以果断，但动手前
 * 仍先留一份轻量索引 [INDEX] 便于回查。
 *
 * 清理口径（唯一出处，别处不要再放宽 / 收紧）：
 * - 故事圣经四类条目：`characters` / `relations` / `events` / `outline` 里
 *   **`origin != "author"` 的条目删掉**；`origin == "author"`（作者在故事页写的）一条不动。
 * - `filesDir/ai/` 下四份 AI 侧车整份删：[PENDING] / [SYNCED] / [JOURNAL] / [SYNC_STATE]。
 * - 每本书的记忆文件 `filesDir/ai/<bookId>.json` **只摘掉 `turns` / `facts` 两个键**，
 *   `chapters`（校核 / 修饰 / 问仍在读的本章摘要）与其余键原样保留。
 * - `filesDir/ai.key`（API Key）**不碰**：删功能不等于替作者抹掉凭据，留给他自己处理。
 *
 * 幂等：标记文件 [MARKER] 存在就直接返回，跑第二次什么都不做。标记放在最后一步写，
 * 中途失败只记日志（runCatching，不抛 UI），下次启动会整体重跑——清聊天 / 删侧车 /
 * 提交 bible 三步本身都是幂等的。
 *
 * 只依赖 [AppStore] 与 `Model.kt` 的数据类，不引用任何即将删除的 `Ai*` 文件。
 */
internal object AiCleanup {
    private const val TAG = "MOYU_AI_CLEANUP"
    private const val DIR = "ai"
    private const val MARKER = ".removed"
    private const val INDEX = "before-ai-removal.json"
    /** 圣经条目「来源」里代表作者的取值；只有不等于它的条目才算 AI / 自动整理写进来的。 */
    private const val AUTHOR = "author"

    private const val PENDING = "bible-pending.json"
    private const val SYNCED = "bible-synced.json"
    private const val JOURNAL = "bible-journal.json"
    private const val SYNC_STATE = "sync-state.json"

    /** 四份 AI 侧车：整份删；同时用作「聊天记忆文件」扫描时的排除表。 */
    private val SIDECARS = listOf(PENDING, SYNCED, JOURNAL, SYNC_STATE)

    /**
     * 幂等入口。成功时什么也不返回；失败只留一条 `MOYU_AI_CLEANUP` 日志，绝不打扰 UI。
     *
     * 调用方只需要保证 [store] 就是 `MoyuApplication.store`（同一个实例），
     * 否则 `updateBible` 提交后可能被另一个实例的旧数据覆盖。
     */
    fun runOnce(context: Context, store: AppStore) {
        runCatching { clean(context.applicationContext, store) }
            .onFailure {
                Log.w(TAG, "AI 数据清理失败（下次启动会重跑）：" + (it.message ?: it.javaClass.simpleName), it)
            }
    }

    private fun clean(context: Context, store: AppStore) {
        val dir = File(context.filesDir, DIR)
        val marker = File(dir, MARKER)
        if (marker.exists()) return
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) error("无法创建目录 $dir")

        // ── ① 圣经：先在内存里算好要删的条目（此时不落库，索引写完才提交）──
        val removed = JSONObject()
        listOf("characters", "relations", "events", "outline").forEach { removed.put(it, JSONArray()) }
        val bibleUpdates = ArrayList<Pair<String, BookBible>>()
        var removedEntries = 0
        for (book in store.data.books) {
            val bible = book.bible
            val characters = bible.characters.filter { it.origin == AUTHOR }
            val relations = bible.relations.filter { it.origin == AUTHOR }
            val events = bible.events.filter { it.origin == AUTHOR }
            val outline = bible.outline.filter { it.origin == AUTHOR }
            val dropped = (bible.characters.size - characters.size) +
                (bible.relations.size - relations.size) +
                (bible.events.size - events.size) +
                (bible.outline.size - outline.size)
            if (dropped > 0) {
                bibleUpdates += book.id to BookBible(characters, relations, events, outline)
                removedEntries += dropped
            }
            bible.characters.filterNot { it.origin == AUTHOR }.forEach { c ->
                removed.getJSONArray("characters").put(JSONObject().apply {
                    put("bookId", book.id); put("bookTitle", book.title)
                    put("id", c.id); put("name", c.name); put("origin", c.origin)
                })
            }
            bible.relations.filterNot { it.origin == AUTHOR }.forEach { r ->
                removed.getJSONArray("relations").put(JSONObject().apply {
                    put("bookId", book.id); put("bookTitle", book.title)
                    put("id", r.id); put("name", relationName(book, r)); put("origin", r.origin)
                })
            }
            bible.events.filterNot { it.origin == AUTHOR }.forEach { e ->
                removed.getJSONArray("events").put(JSONObject().apply {
                    put("bookId", book.id); put("bookTitle", book.title)
                    put("id", e.id); put("title", e.title); put("origin", e.origin)
                })
            }
            bible.outline.filterNot { it.origin == AUTHOR }.forEach { n ->
                // 大纲节点自身没标题时，用对应章节标题兜底（与故事页显示口径一致）。
                val title = n.title.ifBlank {
                    book.chapters.firstOrNull { it.id == n.chapterId }?.title.orEmpty()
                }
                removed.getJSONArray("outline").put(JSONObject().apply {
                    put("bookId", book.id); put("bookTitle", book.title)
                    put("id", n.id); put("title", title); put("origin", n.origin)
                })
            }
        }

        // ── ② 四份 AI 侧车原文塞进索引（文件不存在就跳过）──
        val sidecars = JSONObject()
        SIDECARS.forEach { name ->
            val file = File(dir, name)
            if (!file.isFile) return@forEach
            runCatching { sidecars.put(name, file.readText(Charsets.UTF_8)) }
                .onFailure { Log.w(TAG, "读侧车失败，跳过：$name", it) }
        }

        // ── ③ 聊天记忆文件：只统计，不存内容 ──
        val chats = ArrayList<Pair<File, JSONObject>>()
        val chatFiles = JSONObject()
        dir.listFiles()?.sortedBy { it.name }?.forEach { file ->
            if (!file.isFile || !file.name.endsWith(".json")) return@forEach
            if (file.name in SIDECARS || file.name == INDEX) return@forEach
            val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
            val root = text?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (root == null) {
                Log.w(TAG, "聊天记忆读不动，跳过：${file.name}")
                return@forEach
            }
            chats += file to root
            chatFiles.put(file.name, JSONObject().apply {
                put("turns", arrayLength(root, "turns"))
                put("facts", arrayLength(root, "facts"))
            })
        }

        // ── ④ 索引先落盘，再动数据 ──
        val now = System.currentTimeMillis()
        val index = JSONObject().apply {
            put("version", 1)
            put("createdAt", now)
            put("removedEntries", removed)
            put("sidecars", sidecars)
            put("chatFiles", chatFiles)
        }
        writeAtomically(File(dir, INDEX), index.toString(2))

        // ── ⑤ 侧车整份删 ──
        var sidecarsDeleted = 0
        SIDECARS.forEach { name ->
            val file = File(dir, name)
            if (!file.isFile) return@forEach
            if (file.delete() || !file.exists()) sidecarsDeleted++
            else error("侧车删不掉：${file.name}")
        }

        // ── ⑥ 聊天记忆只摘 turns / facts，chapters 与其余键原样保留 ──
        var chatsCleaned = 0
        chats.forEach { (file, root) ->
            if (!root.has("turns") && !root.has("facts")) return@forEach
            root.remove("turns")
            root.remove("facts")
            writeAtomically(file, root.toString())
            chatsCleaned++
        }

        // ── ⑦ 圣经删 AI 条目：只走 AppStore.updateBible，不直接改文件 ──
        bibleUpdates.forEach { (bookId, next) -> store.updateBible(bookId, next) }
        // 先把 bible 落盘再写标记：updateBible 默认只有 500ms 防抖写盘，若进程在这窗口里
        // 被杀，标记已存在却清不掉圣经里的 AI 条目（以后不会再跑）。flush 是同步写。
        if (bibleUpdates.isNotEmpty()) store.flush()

        // ── ⑧ 标记：时间戳 + 删除条数，方便排查这一版到底动了什么 ──
        writeAtomically(
            marker,
            buildString {
                appendLine("removedAt=$now")
                appendLine("removedAtText=${formatDateTime(now)}")
                appendLine("removedEntries=$removedEntries")
                appendLine("sidecars=$sidecarsDeleted")
                appendLine("chatFiles=$chatsCleaned")
                appendLine("bibles=${bibleUpdates.size}")
            },
        )
    }

    /** 关系摘要用「A — B」：角色还在就用名字，查不到就退回 id，保证索引里每条都能定位。 */
    private fun relationName(book: Book, relation: StoryRelation): String {
        fun nameOf(id: String): String =
            book.bible.character(id)?.name?.takeIf { it.isNotBlank() } ?: id
        return nameOf(relation.from) + " — " + nameOf(relation.to)
    }

    private fun arrayLength(root: JSONObject, key: String): Int = root.optJSONArray(key)?.length() ?: 0

    /** 与 `moyu.json` / 故事圣经侧车同一套原子写：临时文件写完再 rename，不留半截 JSON。 */
    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.writeText(text, Charsets.UTF_8)
            tmp.delete()
        }
    }
}
