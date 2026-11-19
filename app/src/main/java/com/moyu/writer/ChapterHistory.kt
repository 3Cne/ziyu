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
 */
object ChapterHistory {
    /** 每章保留几份；够覆盖一次会话里的反复改稿。 */
    private const val MAX_PER_CHAPTER = 20

    /** 超过这个长度不存：正常章节远小于它，存了只会把磁盘吃光。 */
    private const val MAX_CHARS = 1_000_000

    data class Version(val ts: Long, val words: Int, val file: File)

    private fun dir(ctx: Context, chapterId: String): File =
        File(File(ctx.filesDir, "history"), chapterId).apply { mkdirs() }

    /** 写正文之前调用；false = 没留底，调用方必须放弃写入。 */
    @Synchronized
    fun record(ctx: Context, chapterId: String, content: String): Boolean {
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
            while (File(d, "${ts}_$words.txt").exists()) ts += 1
            val tmp = File(d, "$ts.tmp")
            tmp.writeText(content)
            val target = File(d, "${ts}_$words.txt")
            if (!tmp.renameTo(target)) {
                tmp.delete()
                return false
            }
            val all = d.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
                ?.sortedBy { it.name } ?: return true
            all.dropLast(MAX_PER_CHAPTER).forEach { it.delete() }
            true
        }.getOrDefault(false)
    }

    /** 新的在前。 */
    fun list(ctx: Context, chapterId: String): List<Version> =
        dir(ctx, chapterId).listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.mapNotNull { f ->
                val base = f.name.removeSuffix(".txt")
                val ts = base.substringBefore('_').toLongOrNull() ?: return@mapNotNull null
                val words = base.substringAfter('_', "").toIntOrNull() ?: 0
                Version(ts, words, f)
            }
            ?.sortedByDescending { it.ts }
            ?: emptyList()

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
