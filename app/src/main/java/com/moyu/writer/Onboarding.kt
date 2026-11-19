package com.moyu.writer

/**
 * 上手 6 步（[TutorialGroups.LANDING]）——教练式引导的第一组。
 *
 * 只保留「上手」语义：建书 / 建章 / 写第一句 / 选一段字 / 叫一次 AI / 保存退出。
 * 功能说明不再挤进首次引导（旧 13 页里的功能页已过期：记忆页签 3.26.23 就下线了，
 * 「说完就生效」也和默认询问档不符），改由后续的页内首次教学按页承担。
 *
 * 视觉语言沿用原来的引导页：大图标 + 标题 + 说明；有锚点的步（[TutorialTarget.Anchor]）
 * 改成指向控件的气泡 + 挖洞高亮，锚点不存在时引擎会自动跳过该步并记日志。
 *
 * 第 1 步的锚点 id 是 `shelf.addBook`（书架标题右侧的 +）。页面侧加锚点由 Lead 接线：
 * `GlassIconButton(Glyph.Plus, onClick = onAdd, modifier = Modifier.tutorialAnchor("shelf.addBook"))`。
 *
 * 其余 5 步没有可指的控件（建章/写作在书里、AI 在编辑器），用居中大卡说明，
 * 不做成「目标不存在就跳过」，否则首次启动只会剩 1 步。
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
