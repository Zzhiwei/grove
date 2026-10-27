package local.grove.focus.core

import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerTest {
    private val t0 = Instant.parse("2026-10-27T09:00:00Z")
    private fun at(seconds: Long) = t0.plusSeconds(seconds)

    @Test fun pauseAndResumeAccumulate() {
        val paused = TimerState(mode = Mode.STOPWATCH).start(t0).pause(at(10))
        assertEquals(10.0, paused.elapsed(at(50)), 0.0)
        val resumed = paused.start(at(100))
        assertEquals(15.0, resumed.elapsed(at(105)), 0.0)
        assertEquals(t0, resumed.sessionStart)
    }

    @Test fun stopDropsRunsUnder30s() {
        val (state, session) = TimerState().start(t0).stop(at(29))
        assertNull(session)
        assertFalse(state.isActive)
        assertEquals(0.0, state.accumulated, 0.0)
    }

    @Test fun stopRecordsStopwatch() {
        val (_, session) = TimerState(mode = Mode.STOPWATCH, tag = "Read").start(t0).stop(at(30))
        assertEquals(30.0, session!!.seconds, 0.0)
        assertEquals(t0, session.start)
        assertEquals("Read", session.tag)
    }

    @Test fun pomodoroCappedAtTarget() {
        val (_, session) = TimerState(minutes = 1).start(t0).stop(at(300))
        assertEquals(60.0, session!!.seconds, 0.0)
    }

    @Test fun completesOnlyOnce() {
        val running = TimerState(minutes = 1).start(t0)
        assertFalse(running.isDue(at(59)))
        assertTrue(running.isDue(at(60)))
        val (stopped, first) = running.stop(at(60))
        assertTrue(first != null)
        assertFalse(stopped.isDue(at(61)))
        assertNull(stopped.stop(at(61)).second)
    }

    @Test fun endsAtAccountsForPauses() {
        val s = TimerState(minutes = 25).start(t0).pause(at(60)).start(at(100))
        assertEquals(at(100 + 25 * 60 - 60), s.endsAt)
        assertNull(s.pause(at(200)).endsAt)
        assertNull(TimerState(mode = Mode.STOPWATCH).start(t0).endsAt)
    }

    @Test fun persistsRoundTrip() {
        val s = TimerState(mode = Mode.STOPWATCH, minutes = 40, tag = "Art").start(t0).pause(at(5)).start(at(9))
        assertEquals(s, Json.decodeFromString<TimerState>(Json.encodeToString(s)))
    }
}
