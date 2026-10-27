package local.grove.focus

import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.runBlocking
import local.grove.focus.core.RemoteSession
import local.grove.focus.core.RemoteTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private class FakeRemote(
        var sessions: List<RemoteSession> = emptyList(),
        var tags: List<RemoteTag> = emptyList(),
    ) : Remote {
        val pushedSessions = mutableListOf<RemoteSession>()
        val pushedTags = mutableListOf<RemoteTag>()
        override suspend fun pushSessions(rows: List<RemoteSession>) { pushedSessions += rows }
        override suspend fun pushTags(rows: List<RemoteTag>) { pushedTags += rows }
        override suspend fun pullSessions() = sessions + pushedSessions
        override suspend fun pullTags() = tags
    }

    @Test fun syncPushesUnsentAndMergesRemote() = runBlocking {
        val store = Store(file)
        store.start(t0)
        store.stop(t0.plusSeconds(60))
        store.addTag("Art")
        val other = RemoteSession("0e5e1e6a-0000-4000-8000-000000000001", "Study", t0, 90.0)
        val remote = FakeRemote(sessions = listOf(other), tags = listOf(RemoteTag("Reading", true), RemoteTag("Gym", false)))
        store.sync(remote)
        assertEquals(1, remote.pushedSessions.size)
        assertEquals(listOf(RemoteTag("Art", false)), remote.pushedTags)
        assertTrue(store.data.sessions.all { it.synced })
        assertEquals(2, store.data.sessions.size)
        assertEquals(listOf("Focus", "Study", "Art", "Gym"), store.data.tags) // Reading deleted on another device
        assertEquals(emptyMap<String, Boolean>(), store.data.pendingTags)
    }

    @Test fun syncReselectsDeletedTagWhenIdle() = runBlocking {
        val store = Store(file)
        store.selectTag("Reading")
        store.sync(FakeRemote(tags = listOf(RemoteTag("Reading", true))))
        assertEquals("Focus", store.data.timer.tag)
    }

    @Test fun failedPushKeepsDataUnsent() = runBlocking {
        val store = Store(file)
        store.start(t0)
        store.stop(t0.plusSeconds(60))
        val failing = object : Remote by FakeRemote() {
            override suspend fun pushSessions(rows: List<RemoteSession>) = throw java.io.IOException("offline")
        }
        assertTrue(runCatching { store.sync(failing) }.isFailure)
        assertFalse(store.data.sessions.single().synced)
    }

    @Test fun signOutMarksEverythingUnsent() = runBlocking {
        val store = Store(file)
        store.start(t0)
        store.stop(t0.plusSeconds(60))
        store.sync(FakeRemote())
        store.signedOut()
        assertFalse(store.data.sessions.single().synced)
        store.signedIn()
        assertEquals(mapOf("Focus" to false, "Study" to false, "Reading" to false), store.data.pendingTags)
    }

    @Test fun unsentChangesTriggerSync() {
        var triggers = 0
        val store = Store(file, onUnsent = { triggers++ })
        store.setMinutes(30) // timer only
        store.addTag("Art")
        store.start(t0)
        store.stop(t0.plusSeconds(10)) // too short: nothing recorded
        assertEquals(1, triggers)
    }
}
