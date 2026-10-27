package local.grove.focus

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import local.grove.focus.core.Mode
import local.grove.focus.core.Session
import local.grove.focus.core.TimerState

@Serializable
data class Saved(
    val tags: List<String> = listOf("Focus", "Study", "Reading"),
    val sessions: List<Session> = emptyList(),
    val pendingTags: Map<String, Boolean> = emptyMap(), // tag changes not yet uploaded: name → deleted
    val timer: TimerState = TimerState(),
)

/** App-wide data (tags, sessions, live timer), persisted to [file] on every change. Main thread only. */
class Store(private val file: File) {
    var data by mutableStateOf(load())
        private set

    private val timer get() = data.timer

    private fun update(change: (Saved) -> Saved) {
        data = change(data)
        save()
    }

    private fun setTimer(t: TimerState) = update { it.copy(timer = t) }

    fun start(now: Instant = Instant.now()) = setTimer(timer.start(now))

    fun pause(now: Instant = Instant.now()) = setTimer(timer.pause(now))

    /** Ends the session and records it if long enough. Returns the recorded session. */
    fun stop(now: Instant = Instant.now()): Session? {
        val (stopped, session) = timer.stop(now)
        update { it.copy(timer = stopped, sessions = it.sessions + listOfNotNull(session)) }
        return session
    }

    /** Stops a pomodoro that reached its target (also one that ended while the app was closed). */
    fun completeIfDue(now: Instant = Instant.now()): Session? = if (timer.isDue(now)) stop(now) else null

    fun setMode(mode: Mode) { if (!timer.isActive) setTimer(timer.copy(mode = mode)) }

    fun setMinutes(minutes: Int) { if (!timer.isActive) setTimer(timer.copy(minutes = minutes)) }

    fun selectTag(tag: String) { if (!timer.isActive) setTimer(timer.copy(tag = tag)) }

    fun addTag(raw: String) {
        val name = raw.trim()
        if (name.isEmpty() || timer.isActive) return
        update {
            if (name in it.tags) it.copy(timer = it.timer.copy(tag = name))
            else it.copy(tags = it.tags + name, pendingTags = it.pendingTags + (name to false), timer = it.timer.copy(tag = name))
        }
    }

    fun removeTag(name: String) {
        if (timer.isActive) return
        update {
            val tags = it.tags - name
            val tag = if (it.timer.tag == name) tags.firstOrNull() ?: "Focus" else it.timer.tag
            it.copy(tags = tags, pendingTags = it.pendingTags + (name to true), timer = it.timer.copy(tag = tag))
        }
    }

    private fun load(): Saved {
        if (!file.exists()) return Saved()
        return try {
            json.decodeFromString<Saved>(file.readText())
        } catch (e: SerializationException) {
            corrupt(e)
        } catch (e: IllegalArgumentException) {
            corrupt(e)
        }
    }

    // Keep the unreadable file instead of overwriting it on next save.
    private fun corrupt(e: Exception): Saved {
        Log.e("Grove", "data.json unreadable, keeping a copy", e)
        file.renameTo(File(file.path + ".corrupt-${Instant.now().epochSecond}"))
        return Saved()
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(data))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            Log.e("Grove", "save failed", e)
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
