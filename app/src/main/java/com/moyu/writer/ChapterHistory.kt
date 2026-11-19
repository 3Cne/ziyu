package com.moyu.writer

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * 每章写前快照。
 *
 * 3.26.29 新增。AI 改正文之前先在这里留一份原样，写失败/改坏了还能退回来。
 * 只写文件，**不动 moyu.json**：一份正文一个 .txt，文件名 `<时间戳>_<字数>.txt`，
 * 每章最多 [MAX_PER_CHAPTER] 份，超出删最旧。
 *
 * 单一入口：任何要改正文的地方（接受补丁 / 自动档落地 / 恢复历史）都必须先
 * [record] 成功再写；record 返回 false 就放弃这次写入，宁可没改，也不能没留底。
 *
 * 3.26.57 起多一类「自动存档」：正文保存路径按 [AUTO_MIN_INTERVAL_MS] /
 * [AUTO_MIN_CHARS] 节流补一条，条目标 manual=false（文件名 `<时间戳>_<字数>a.txt`）。
 * 清理时**手动条目优先保留**；自动条目 24 小时内最多 [AUTO_MAX_PER_DAY] 条，
 * 每章总量上限不变([MAX_PER_CHAPTER])，所以自动存档不会把磁盘写爆。
 * 旧文件没有 a 标记，一律按手动算：它们来自 AI 改稿 / 替换选中 / 恢复前留底。
 */
object ChapterHistory {
    /** 每章保留几份；够覆盖一次会话里的反复改稿。 */
    private const val MAX_PER_CHAPTER = 20

    /** 超过这个长度不存：正常章节远小于它，存了只会把磁盘吃光。 */
    private const val MAX_CHARS = 1_000_000

    // ── 自动存档节流参数（3.26.57）。要调阈值只改这几行，别散到调用方。───────
    /** 距上次快照至少这么久，保存时才补一条自动存档。 */
    const val AUTO_MIN_INTERVAL_MS = 8 * 60 * 1000L

    /** 或者：距上次快照累计改动了这么多字，也可以补一条。 */
    const val AUTO_MIN_CHARS = 1200

    /** 同一章 24 小时内最多留几条自动存档；超出丢最旧的自动条目（手动条目不丢）。 */
    const val AUTO_MAX_PER_DAY = 12

    /** 上面那个「一天」的窗口长度。 */
    const val AUTO_DAY_MS = 24 * 60 * 60 * 1000L
    // ───────────────────────────────────────────────────────────────────

    /** 自动存档在文件名里跟在字数后的标记；没有它 = 手动留底。 */
    private const val AUTO_MARK = "a"

    /** [manual] 默认 false（自动）；[list] 一定按文件名显式给值。 */
    data class Version(val ts: Long, val words: Int, val file: File, val manual: Boolean = false)

    private fun dir(ctx: Context, chapterId: String): File =
        File(File(ctx.filesDir, "history"), chapterId).apply { mkdirs() }

    private fun fileName(ts: Long, words: Int, manual: Boolean): String =
        if (manual) "${ts}_$words.txt" else "${ts}_${words}$AUTO_MARK.txt"

    /** 文件名前缀是毫秒时间戳；解析不了按 0（当最旧）。 */
    private fun tsOf(name: String): Long = name.substringBefore('_').toLongOrNull() ?: 0L

    private fun isAuto(name: String): Boolean = name.endsWith("$AUTO_MARK.txt")

    /**
     * 写正文之前调用；false = 没留底，调用方必须放弃写入。
     *
     * [manual] 保持默认 true，AI 改稿 / 替换选中 / 恢复前留底这些既有调用点一行都不用改；
     * 自动存档显式传 false，文件名带 [AUTO_MARK] 标记。
     */
    @Synchronized
    fun record(
        ctx: Context,
        chapterId: String,
        content: String,
        manual: Boolean = true,
    ): Boolean {
        if (chapterId.isBlank()) return false
        // 空白章没有可丢的内容，不算失败。
        if (content.isBlank()) return true
        if (content.length > MAX_CHARS) return false
        val words = content.count { !it.isWhitespace() }
        return runCatching {
            val d = dir(ctx, chapterId)
            // 同一毫秒里连写两章同一本书（一轮回答改两处）时不能撞名：
            // 撞了 rename 会失败，第二次写入就会被当成「没留底」而丢掉。
            var ts = System.currentTimeMillis()
            var name = fileName(ts, words, manual)
            while (File(d, name).exists()) {
                ts += 1
                name = fileName(ts, words, manual)
            }
            val tmp = File(d, "$ts.tmp")
            tmp.writeText(content)
            val target = File(d, name)
            if (!tmp.renameTo(target)) {
                tmp.delete()
                return false
            }
            prune(d)
            true
        }.getOrDefault(false)
    }

    /**
     * 写盘后的清理（3.26.57：加了自动条目，规则跟着变得要保手动条目）。
     *
     * ① 24 小时内自动条目最多 [AUTO_MAX_PER_DAY] 条，超出只丢**最旧的自动条目**；
     * ② 每章总量上限仍是 [MAX_PER_CHAPTER]：先丢最旧的自动条目，自动条目不够丢时才动
     *    最旧的手动条目——这就是「手动条目优先保留」。
     */
    private fun prune(d: File) {
        // 文件名前缀是时间戳，字典序就是旧→新。
        val all = d.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedBy { it.name } ?: return
        val cutoff = System.currentTimeMillis() - AUTO_DAY_MS
        all.filter { isAuto(it.name) && tsOf(it.name) >= cutoff }
            .dropLast(AUTO_MAX_PER_DAY)
            .forEach { it.delete() }
        val left = all.filter { it.exists() }
        val over = left.size - MAX_PER_CHAPTER
        if (over <= 0) return
        val auto = left.filter { isAuto(it.name) }
        val dropAuto = auto.take(over)
        dropAuto.forEach { it.delete() }
        left.filterNot { isAuto(it.name) }
            .take((over - dropAuto.size).coerceAtLeast(0))
            .forEach { it.delete() }
    }

    /** 新的在前。 */
    fun list(ctx: Context, chapterId: String): List<Version> =
        dir(ctx, chapterId).listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.mapNotNull { f ->
                val base = f.name.removeSuffix(".txt")
                val auto = base.endsWith(AUTO_MARK)
                val stem = if (auto) base.dropLast(AUTO_MARK.length) else base
                val ts = stem.substringBefore('_').toLongOrNull() ?: return@mapNotNull null
                val words = stem.substringAfter('_', "").toIntOrNull() ?: 0
                // 没有自动标记的旧文件都是显式留底（AI 改稿 / 替换 / 恢复前），按手动算。
                Version(ts, words, f, manual = !auto)
            }
            ?.sortedByDescending { it.ts }
            ?: emptyList()

    /** 最新章节快照的时间戳；没有就 null。自动存档用它算「距上次快照多久」。 */
    fun newestTs(ctx: Context, chapterId: String): Long? =
        dir(ctx, chapterId).listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.maxOfOrNull { tsOf(it.name) }

    fun read(ctx: Context, chapterId: String, ts: Long): String? =
        list(ctx, chapterId).firstOrNull { it.ts == ts }
            ?.let { runCatching { it.file.readText() }.getOrNull() }

    fun clear(ctx: Context, chapterId: String) {
        dir(ctx, chapterId).deleteRecursively()
    }

    /**
     * 正文指纹，补丁的乐观锁：提出补丁时记下，接受时比对。
     * 只取前 16 位十六进制，够防「这期间被改过」，也不占内存。
     */
    fun fingerprint(content: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
}
