package local.grove.focus

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import java.time.Instant
import local.grove.focus.core.Mode
import local.grove.focus.core.Session
import local.grove.focus.core.TimerState
import local.grove.focus.core.formatDuration

/**
 * Keeps the ongoing notification and the completion alarm in line with the timer.
 * The countdown in the notification is drawn by the system (chronometer), so no service runs.
 */
object TimerNotifications {
    private const val CHANNEL_TIMER = "timer"
    private const val CHANNEL_DONE = "done"
    private const val ID_TIMER = 1
    private const val ID_DONE = 2

    fun createChannels(ctx: Context) {
        val done = NotificationChannel(CHANNEL_DONE, "Pomodoro complete", NotificationManager.IMPORTANCE_HIGH)
            .apply { setSound(null, null) } // the app plays its own tune
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_TIMER, "Running timer", NotificationManager.IMPORTANCE_LOW),
            done,
        ))
    }

    /** Call after every timer change: posts/cancels the ongoing notification and the alarm. */
    fun sync(ctx: Context, timer: TimerState, now: Instant = Instant.now()) {
        val notifications = ctx.getSystemService(NotificationManager::class.java)
        val alarms = ctx.getSystemService(AlarmManager::class.java)
        val alarm = receiverIntent(ctx, TimerReceiver.ALARM)

        val endsAt = timer.endsAt
        if (endsAt != null && alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt.toEpochMilli(), alarm)
        } else if (endsAt != null) {
            // ponytail: inexact fallback if the exact-alarm permission is ever revoked; may fire minutes late.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt.toEpochMilli(), alarm)
        } else {
            alarms.cancel(alarm)
        }

        if (!timer.isActive) return notifications.cancel(ID_TIMER)
        val builder = Notification.Builder(ctx, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${timer.mode.label} · ${timer.tag}")
            .setContentIntent(openApp(ctx))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
        if (timer.isRunning) {
            val countDown = timer.mode == Mode.POMODORO
            builder.setUsesChronometer(true).setChronometerCountDown(countDown).setShowWhen(true)
                .setWhen(if (countDown) endsAt!!.toEpochMilli() else now.toEpochMilli() - (timer.elapsed(now) * 1000).toLong())
                .addAction(action(ctx, "Pause", TimerReceiver.PAUSE))
        } else {
            builder.setContentText("Paused · ${formatDuration(timer.display(now))}").setShowWhen(false)
                .addAction(action(ctx, "Resume", TimerReceiver.RESUME))
        }
        builder.addAction(action(ctx, "Stop & Save", TimerReceiver.STOP))
        notifications.notify(ID_TIMER, builder.build())
    }

    /** Pomodoro finished: tune + banner (same text as macOS). */
    fun celebrate(ctx: Context, session: Session, minutes: Int) {
        playCelebration()
        val n = Notification.Builder(ctx, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Pomodoro complete 🌳")
            .setContentText("$minutes min of ${session.tag} focus. Time for a break.")
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_DONE, n)
    }

    private fun receiverIntent(ctx: Context, action: String) = PendingIntent.getBroadcast(
        ctx, action.hashCode(), Intent(ctx, TimerReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(ctx: Context, title: String, action: String) =
        Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_notification), title, receiverIntent(ctx, action)).build()

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )
}
