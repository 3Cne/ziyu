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
    }
}
