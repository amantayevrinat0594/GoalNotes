package com.rinat.goalnotes

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
import java.util.Calendar
import java.util.concurrent.TimeUnit

const val EXTRA_GOAL_ID = "goal_id"

/** Ежедневные напоминания о целях. */
object Reminders {
    private const val CHANNEL_ID = "goals_daily"

    fun createChannel(ctx: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Напоминания о целях",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Каждый день напоминает о ваших целях" }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun scheduleAll(ctx: Context) {
        Store.load(ctx).goals.forEach { schedule(ctx, it) }
    }

    fun schedule(ctx: Context, goal: Goal) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(ctx, goal.id)
        am.cancel(pi)
        if (!goal.remind) return
        val time = nextTime(goal.hour, goal.minute)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    fun cancel(ctx: Context, goalId: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx, goalId))
        ctx.getSystemService(NotificationManager::class.java).cancel(code(goalId))
    }

    /** Показывает уведомление о цели прямо сейчас. */
    fun notify(ctx: Context, goal: Goal) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val day = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - goal.created) + 1
        val next = goal.nextStep
        val text = when {
            goal.steps.isNotEmpty() && next == null -> "Все шаги выполнены! Ты молодец 🎉"
            next != null -> "Следующий шаг: ${next.text}"
            goal.description.isNotBlank() -> goal.description
            else -> "Не забывай о своей цели — сделай сегодня хотя бы маленький шаг!"
        }
        val progress = if (goal.steps.isEmpty()) "" else " · ${goal.doneCount} из ${goal.steps.size} шагов"

        val open = Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_GOAL_ID, goal.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val openPi = PendingIntent.getActivity(
            ctx, code(goal.id), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🎯 ${goal.title}")
            .setContentText(text)
            .setSubText("День $day$progress")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(code(goal.id), n)
        } catch (e: SecurityException) {
            // нет разрешения на уведомления
        }
    }

    private fun alarmIntent(ctx: Context, goalId: Long): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).putExtra(EXTRA_GOAL_ID, goalId)
        return PendingIntent.getBroadcast(
            ctx, code(goalId), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun code(id: Long): Int = (id xor (id ushr 32)).toInt()

    private fun nextTime(hour: Int, minute: Int): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= System.currentTimeMillis() + 1000) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }
}

/** Срабатывает в заданное время: показывает уведомление и ставит будильник на завтра. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_GOAL_ID, -1L)
        val goal = Store.load(context).goals.find { it.id == id } ?: return
        if (!goal.remind) return
        Reminders.createChannel(context)
        Reminders.notify(context, goal)
        Reminders.schedule(context, goal)
    }
}

/** После перезагрузки телефона или смены времени заново ставит все напоминания. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.createChannel(context)
        Reminders.scheduleAll(context)
    }
}
