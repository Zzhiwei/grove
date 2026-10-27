package local.grove.focus

import java.io.File
import java.nio.file.Files
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreTest {
    private val dir = Files.createTempDirectory("grove").toFile()
    private val file = File(dir, "data.json")
    private val t0 = Instant.parse("2026-10-27T09:00:00Z")

    @Test fun runningTimerAndSessionsSurviveReload() {
        Store(file).apply {
            start(t0)
            stop(t0.plusSeconds(120))
            start(t0.plusSeconds(200))
        }
        val reloaded = Store(file).data
        assertEquals(1, reloaded.sessions.size)
        assertEquals(t0.plusSeconds(200), reloaded.timer.runningSince)
    }

    @Test fun overduePomodoroCompletesOnceAfterReload() {
        Store(file).apply { setMinutes(1); start(t0) }
        val store = Store(file) // e.g. app reopened after the phone rebooted
        assertEquals(60.0, store.completeIfDue(t0.plusSeconds(3600))!!.seconds, 0.0)
        assertNull(store.completeIfDue(t0.plusSeconds(3601)))
        assertEquals(1, store.data.sessions.size)
    }

    @Test fun unreadableFileIsKept() {
        file.writeText("not json")
        val store = Store(file)
        assertEquals(Saved(), store.data)
        assertNotNull(dir.listFiles()!!.singleOrNull { it.name.startsWith("data.json.corrupt-") })
        store.addTag("New") // saving afterwards must not touch the kept copy
        assertEquals("not json", dir.listFiles()!!.single { it.name.startsWith("data.json.corrupt-") }.readText())
    }

    @Test fun tagChangesArePendingForSync() {
        val store = Store(file)
        store.addTag("  Art ")
        assertEquals("Art", store.data.timer.tag)
        store.removeTag("Art")
        assertEquals(listOf("Focus", "Study", "Reading"), store.data.tags)
        assertEquals(mapOf("Art" to true), store.data.pendingTags)
        assertEquals("Focus", store.data.timer.tag)
    }

    @Test fun settingsLockedWhileActive() {
        val store = Store(file)
        store.start(t0)
        store.setMinutes(50)
        store.selectTag("Study")
        assertEquals(25, store.data.timer.minutes)
        assertEquals("Focus", store.data.timer.tag)
        assertTrue(store.data.timer.isActive)
    }

    @Test fun notifiesOnlyOnTimerChanges() {
        val seen = mutableListOf<Boolean>()
        val store = Store(file) { seen += it.isRunning }
        store.addTag("Art") // also selects it: timer changed
        store.addTag("Art") // already selected: no change
        store.start(t0)
        store.pause(t0.plusSeconds(5))
        assertEquals(listOf(false, true, false), seen)
    }
}
