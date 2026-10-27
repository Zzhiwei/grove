package local.grove.focus.core

import java.time.Duration
import java.time.Instant
import kotlinx.serialization.Serializable

enum class Mode(val label: String) { POMODORO("Pomodoro"), STOPWATCH("Stopwatch") }

/**
 * Live timer. Time is derived from dates (`accumulated + now - runningSince`), not tick counting,
 * and the whole state is persisted, so it survives sleep and Android killing the app.
 */
@Serializable
data class TimerState(
    val mode: Mode = Mode.POMODORO,
    val minutes: Int = 25,
    val tag: String = "Focus",
    @Serializable(InstantSerializer::class) val sessionStart: Instant? = null,
    @Serializable(InstantSerializer::class) val runningSince: Instant? = null,
    val accumulated: Double = 0.0,
) {
    val isRunning get() = runningSince != null
    val isActive get() = sessionStart != null
    val target get() = minutes * 60.0

    fun elapsed(now: Instant) = accumulated + (runningSince?.let { Duration.between(it, now).toMillis() / 1000.0 } ?: 0.0)

    fun display(now: Instant) = if (mode == Mode.POMODORO) maxOf(0.0, target - elapsed(now)) else elapsed(now)

    /** 0..1 ring fill: pomodoro progress, or progress through the current hour for stopwatch. */
    fun progress(now: Instant) =
        if (mode == Mode.POMODORO) minOf(1.0, elapsed(now) / target) else elapsed(now) % 3600 / 3600

    /** When a running pomodoro reaches its target, or null. */
    val endsAt: Instant? get() =
        if (mode == Mode.POMODORO) runningSince?.plusMillis(((target - accumulated) * 1000).toLong()) else null

    fun isDue(now: Instant) = isRunning && mode == Mode.POMODORO && elapsed(now) >= target

    fun start(now: Instant) = copy(runningSince = now, sessionStart = sessionStart ?: now)

    fun pause(now: Instant) = copy(accumulated = elapsed(now), runningSince = null)

    /**
     * Ends the session. Returns the reset state and the session to record: partial pomodoros count,
     * capped at the target; runs under 30 s are treated as accidental starts and dropped.
     * Stopping an inactive timer records nothing, so a pomodoro can't be completed twice.
     */
    fun stop(now: Instant): Pair<TimerState, Session?> {
        val seconds = if (mode == Mode.POMODORO) minOf(elapsed(now), target) else elapsed(now)
        val session = sessionStart?.takeIf { seconds >= 30 }?.let { Session(tag = tag, start = it, seconds = seconds) }
        return copy(sessionStart = null, runningSince = null, accumulated = 0.0) to session
    }
}
