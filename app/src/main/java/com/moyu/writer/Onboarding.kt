package com.moyu.writer

/**
 * 上手 6 步（[TutorialGroups.LANDING]）——教练式引导的第一组。
 *
 * 语义：建书 / 建章 / 写第一句 / 选一段字 / 叫一次 AI / 保存退出；
 * 功能说明不再挤进首次引导（旧 13 页里的功能页已过期：记忆页签 3.26.23 就下线了，
 * 「说完就生效」也和默认询问档不符），改由后续的页内首次教学按页承担。
 *
 * 3.26.52 起第 1 步改成「真点才能过」：用户否掉了「只读说明点下一步」的做法，
 * 要的是「教你点哪点哪」——所以这一步设成 [TutorialAdvance.OnTargetTap]，
 * 气泡不显示「下一步」，只显示指向挖洞的「点一下这里」；必须真的点到 + 才算过。
 *
 * 页面侧接线（Lead 把这一行加到书架标题右侧的 + 上）：
 *
 * ```kotlin
 * GlassIconButton(
 *     Glyph.Plus,
 *     onClick = onAdd,
 *     modifier = Modifier.tutorialAnchor("shelf.addBook", onActivate = onAdd),
 * )
 * ```
 *
 * - `tutorialAnchor(id)` 登记坐标，引擎每帧取最新值画挖洞；
 * - `onActivate` 是洞被点中时执行的真实动作（通常就是同一个 `onClick`）。
 *   不传也能过步，只是不会真的打开建书框；
 * - 锚点不存在时引擎自动跳过该步（不会卡死），所以页面暂时没接线也不会崩。
 *
 * 其余 5 步暂时没有可指的控件（建章/写作在书里、AI 在编辑器），用居中大卡说明，
 * 等工作台那几页的锚点接好后再逐步改成 OnTargetTap。
 */
val LANDING_TUTORIAL: TutorialGroup = TutorialGroup(
    id = TutorialGroups.LANDING,
    steps = listOf(
        TutorialStep(
            id = "landing.book",
            group = TutorialGroups.LANDING,
            section = "上手 · 1 / 6",
            title = "第一本书",
            body = "点右上角的 +，填一个书名就能建好；作者、分组、简介都可以留空。素纸没有账号，书只存在这台手机里。",
            target = TutorialTarget.Anchor("shelf.addBook"),
            advanceOn = TutorialAdvance.OnTargetTap,
            glyph = Glyph.Book,
        ),
        TutorialStep(
            id = "landing.chapter",
            group = TutorialGroups.LANDING,
            section = "上手 · 2 / 6",
            title = "写下第一章",
            body = "进到书里点「继续写作」回到上次那一章；目录右侧的 + 新开一章。每章单独保存，写错了可以在这里回退。",
            glyph = Glyph.List,
        ),
        TutorialStep(
            id = "landing.firstLine",
            group = TutorialGroups.LANDING,
            section = "上手 · 3 / 6",
            title = "写第一句",
            body = "点一下正文就能打字，光标停在哪就从哪写；写下的内容自动保存，不用找保存按钮。",
            glyph = Glyph.Edit,
        ),
        TutorialStep(
            id = "landing.select",
            group = TutorialGroups.LANDING,
            section = "上手 · 4 / 6",
            title = "选一段字",
            body = "长按选词、拖动两端手柄改范围；选中后工具栏变成 校核 / 复制 / 全选 / 剪切 / 粘贴 / 删除，只对这一段生效。",
            glyph = Glyph.Quote,
        ),
        TutorialStep(
            id = "landing.ai",
            group = TutorialGroups.LANDING,
            section = "上手 · 5 / 6",
            title = "叫一次 AI",
            body = "选中一段后点「AI」，校核、修饰、问都只作用在这一段；回答你可以采纳，也可以替换回选中段。",
            glyph = Glyph.Sparkles,
        ),
        TutorialStep(
            id = "landing.save",
            group = TutorialGroups.LANDING,
            section = "上手 · 6 / 6",
            title = "保存退出",
            body = "退出编辑器会自动保存；「···」里有章节历史，写坏了能回到之前的版本。设置里还能导出备份，带到下一台手机。",
            glyph = Glyph.Check,
        ),
    ),
)
