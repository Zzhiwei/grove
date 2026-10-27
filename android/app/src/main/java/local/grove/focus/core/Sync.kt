package local.grove.focus.core

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row shape of `public.sessions` in Supabase. */
@Serializable
data class RemoteSession(
    val id: String,
    val tag: String,
    @SerialName("started_at") @Serializable(InstantSerializer::class) val startedAt: Instant,
    val seconds: Double,
) {
    constructor(s: Session) : this(s.id, s.tag, s.start, s.seconds)
    // Postgres returns uuids lowercase; local ids are generated lowercase too.
    val session get() = Session(id.lowercase(), tag, startedAt, seconds, synced = true)
}

/** Row shape of `public.tags` in Supabase (`deleted` is a tombstone). */
@Serializable
data class RemoteTag(val name: String, val deleted: Boolean)

/** Adds remote sessions missing locally (matched by id). Sessions are append-only, so this never conflicts. */
fun mergeSessions(local: List<Session>, remote: List<Session>): List<Session> {
    val known = local.map { it.id }.toSet()
    return local + remote.filter { it.id !in known }
}

/** Server tag state wins, except local changes not uploaded yet ([pending]: name → deleted). Keeps local order. */
fun mergeTags(local: List<String>, server: List<RemoteTag>, pending: Map<String, Boolean>): List<String> {
    val deletedOnServer = server.filter { it.deleted }.map { it.name }.toSet()
    val result = local.filter { name -> pending[name]?.not() ?: (name !in deletedOnServer) }.toMutableList()
    for (t in server) {
        if (!t.deleted && pending[t.name] != true && t.name !in result) result += t.name
    }
    return result
}
