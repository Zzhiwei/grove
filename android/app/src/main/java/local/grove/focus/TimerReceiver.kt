package local.grove.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.time.Instant

/** Handles the completion alarm and the notification's Pause / Resume / Stop buttons, with or without the UI open. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val store = ctx.store
        val now = Instant.now()
        when (intent.action) {
            // +1 s absorbs alarm/millisecond rounding; the recorded pomodoro is capped at its length anyway.
            ALARM -> store.completeIfDue(now.plusSeconds(1))?.let { session ->
                TimerNotifications.celebrate(ctx, session, store.data.timer.minutes)
                val pending = goAsync() // keep the process alive while the ~1.2 s tune plays
                Handler(Looper.getMainLooper()).postDelayed(pending::finish, 2000)
            }
            PAUSE -> store.pause(now)
            RESUME -> store.start(now)
            STOP -> store.stop(now)
        }
    }

    companion object {
        const val ALARM = "local.grove.focus.ALARM"
        const val PAUSE = "local.grove.focus.PAUSE"
        const val RESUME = "local.grove.focus.RESUME"
        const val STOP = "local.grove.focus.STOP"
    }
}
