package com.moyu.writer

import android.app.Application

class MoyuApplication : Application() {
    lateinit var store: AppStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = AppStore(this)
        ReminderScheduler.ensureChannel(this)
        ReminderScheduler.rescheduleAll(this, store.data)
        // 3.26.64：一次性清掉问书 / 自动整理留在本机数据里的东西（bible 里 origin != author
        // 的条目、ai/ 下四份 AI 侧车、聊天记忆的 turns/facts）。幂等：标记存在就直接返回。
        // 放后台线程：要扫书稿数据 + 读写 ai/ 目录，不能挡住首帧；失败只打 MOYU_AI_CLEANUP
        // 日志、下次启动整体重跑，不弹 UI。
        Thread {
            AiCleanup.runOnce(this, store)
        }.start()
    }
}
