package com.moyu.writer

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object ReminderScheduler {
    const val CHANNEL_ID = "moyu_reminders"
    const val EXTRA_NOTE = "note_id"
    const val EXTRA_TODO = "todo_id"
    const val EXTRA_TITLE = "todo_title"
    const val EXTRA_NOTE_TITLE = "note_title"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "写作提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "笔记待办的定时提醒"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun schedule(context: Context, note: Note, todo: Todo) {
        val at = todo.remindAt ?: return
        if (at <= System.currentTimeMillis()) return
        ensureChannel(context)
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_NOTE, note.id)
            putExtra(EXTRA_TODO, todo.id)
            putExtra(EXTRA_TITLE, todo.text.ifBlank { note.title })
            putExtra(EXTRA_NOTE_TITLE, note.title)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(note.id, todo.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } catch (_: Throwable) {
        }
    }

    fun cancel(context: Context, noteId: String, todoId: String) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(noteId, todoId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.cancel(pending)
    }

    fun rescheduleAll(context: Context, data: AppData) {
        data.notes.forEach { note ->
            note.todos.filter { !it.done && it.remindAt != null && it.remindAt > System.currentTimeMillis() }
                .forEach { schedule(context, note, it) }
        }
    }

    fun cancelNote(context: Context, note: Note) {
        note.todos.forEach { cancel(context, note.id, it.id) }
    }

    fun cancelAll(context: Context, data: AppData) {
        data.notes.forEach { cancelNote(context, it) }
    }

    fun requestCode(noteId: String, todoId: String): Int = ("$noteId:$todoId").hashCode() and 0x7FFFFFFF

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE) ?: return
        val todoId = intent.getStringExtra(ReminderScheduler.EXTRA_TODO) ?: return
        val task = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "待办提醒"
        val noteTitle = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "待办提醒"
        ReminderScheduler.ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_note", noteId)
        }
        val pending = PendingIntent.getActivity(
            context,
            4321,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val note = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task)
            .setContentText(noteTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(noteTitle))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        if (ReminderScheduler.canPost(context)) {
            try {
                NotificationManagerCompat.from(context).notify(todoId.hashCode() and 0x7FFFFFFF, note)
            } catch (_: SecurityException) {
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val store = AppStore(context)
            ReminderScheduler.rescheduleAll(context, store.data)
        } catch (_: Throwable) {
        }
    }
}
