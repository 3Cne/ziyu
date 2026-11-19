package com.moyu.writer

import org.json.JSONObject

/**
 * 3.26.45：AI 工具的统一错误码。全部小写下划线，模型看到 code 就知道该怎么改参数，
 * 不用再从一句中文里猜「这是失败了还是书里本来就没有」。
 *
 * 约定：**参数/目标类**的问题才是失败（ok=false）——名字打错、时间写不成、状态对不上；
 * 内容上的「没有」不是失败（ok=true, data 里说明）——书里没搜到、还没有笔记、大纲都落地了。
 */
object AiToolError {
    /** 必填参数没给，或给了空串。 */
    const val MISSING_PARAM = "missing_param"

    /** 参数给了但值不合法：关键词太短、时间解析不了、位次越界、枚举不认识。 */
    const val INVALID_ARG = "invalid_arg"

    /** 没有这个工具：模型自己编的名字，或被合并掉的旧名字。 */
    const val UNKNOWN_TOOL = "unknown_tool"

    /** 只读档下调了会写盘的工具。 */
    const val READ_ONLY = "read_only_mode"

    const val BOOK_NOT_FOUND = "book_not_found"
    const val CHAPTER_NOT_FOUND = "chapter_not_found"
    const val NOTE_NOT_FOUND = "note_not_found"
    const val GROUP_NOT_FOUND = "group_not_found"
    const val TODO_NOT_FOUND = "todo_not_found"
    const val ATTACHMENT_NOT_FOUND = "attachment_not_found"
    const val BACKUP_NOT_FOUND = "backup_not_found"
    const val HISTORY_NOT_FOUND = "history_not_found"

    /** 状态对不上：两章不相邻、原文出现多次、备份解析不了……改参数或先做别的操作。 */
    const val STATE_CONFLICT = "state_conflict"

    /** 故事圣经拒绝了这次写入：角色不存在、作者手改保护、没有变化。 */
    const val BIBLE_REJECTED = "bible_rejected"

    const val WEB_DISABLED = "web_disabled"
    const val WEB_FAILED = "web_failed"
    const val BUDGET_EXHAUSTED = "budget_exhausted"

    /** 没有运行环境（拿不到 Context / 附件 / 导航回调）。 */
    const val NO_RUNTIME = "no_runtime"

    /** 这个工具存在但不支持这种用法。 */
    const val UNSUPPORTED = "unsupported"

    /** 工具自己抛了异常；这是最后一道兜底，正常不该出现。 */
    const val INTERNAL = "internal_error"
}

/**
 * 3.26.45：所有工具的唯一返回形状（之前每个工具各回一句中文，模型分不清
 * 「正常但空」和「调用失败」，常把失败句原样转给作者）。
 *
 * 成功：`{"ok":true,"data":"…","error":null}`
 * 失败：`{"ok":false,"data":null,"error":{"code":"chapter_not_found","message":"…"}}`
 *
 * `data` 是工具真正要说的话（仍是给模型看的中文，含现有名字列表方便它改参数）；
 * `message` 只写失败原因，不复述整段内容。
 */
object AiToolResult {
    fun ok(data: String): String = JSONObject().apply {
        put("ok", true)
        put("data", data)
        put("error", JSONObject.NULL)
    }.toString()

    fun fail(code: String, message: String): String = JSONObject().apply {
        put("ok", false)
        put("data", JSONObject.NULL)
        put("error", JSONObject().apply {
            put("code", code)
            put("message", message)
        })
    }.toString()

    /**
     * 给 UI 的 trace 用：历史缓存里的纯文本回复没有 ok 字段，按成功处理。
     * （AiTab 里的 `ok = !reply.startsWith("error:")` 可以换成这个。）
     */
    fun isOk(reply: String): Boolean = runCatching {
        val o = JSONObject(reply)
        if (!o.has("ok")) true else o.optBoolean("ok", true)
    }.getOrDefault(true)

    /** 失败时的错误码；成功或不是信封时返回 null。 */
    fun errorCode(reply: String): String? = runCatching {
        JSONObject(reply).optJSONObject("error")?.optString("code")?.ifBlank { null }
    }.getOrNull()
}
