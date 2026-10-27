package local.grove.focus

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import local.grove.focus.core.RemoteSession
import local.grove.focus.core.RemoteTag

/**
 * Supabase auth + Data API via supabase-kt. The session (incl. refresh token) is kept in the library's
 * default app-private storage; a refresh rejected by the server clears it (= signed out).
 */
class Cloud : Remote {
    private val client = createSupabaseClient(
        supabaseUrl = "https://ryaqrgzgnhbiybtsogln.supabase.co",
        // Publishable key is meant to ship in apps; row-level security limits each user to their own rows.
        supabaseKey = "sb_publishable_Vc0wZNzI9N2ZlL_8IlzoaA_yYvlFN2f",
    ) {
        install(Auth)
        install(Postgrest)
    }
    private val auth get() = client.auth

    /** Changes on sign-in / sign-out / expiry; read it from Compose to recompose. */
    val status get() = auth.sessionStatus
    val signedIn get() = auth.currentSessionOrNull() != null
    val email get() = auth.currentUserOrNull()?.email

    suspend fun awaitReady() = auth.awaitInitialization() // the saved session loads asynchronously

    /** Returns false when Supabase wants the email confirmed before signing in. */
    suspend fun signUp(email: String, password: String): Boolean {
        auth.signUpWith(Email) { this.email = email; this.password = password }
        return signedIn
    }

    suspend fun signIn(email: String, password: String) =
        auth.signInWith(Email) { this.email = email; this.password = password }

    suspend fun signOut() {
        try { auth.signOut() } catch (_: Exception) {} // best effort; local sign-out happens regardless
        auth.clearSession()
    }

    override suspend fun pushSessions(rows: List<RemoteSession>) {
        client.from("sessions").upsert(rows) { onConflict = "id"; ignoreDuplicates = true }
    }

    override suspend fun pushTags(rows: List<RemoteTag>) {
        client.from("tags").upsert(rows) { onConflict = "user_id,name" }
    }

    override suspend fun pullSessions(): List<RemoteSession> = selectAll("sessions", "id")

    override suspend fun pullTags(): List<RemoteTag> = selectAll("tags", "name")

    /** Fetches every row the user can see, a page at a time. */
    // ponytail: pulls the full table each sync (same as macOS); switch to `created_at=gt.<last>` once histories get large.
    private suspend inline fun <reified T : Any> selectAll(table: String, orderBy: String): List<T> {
        val pageSize = 1000L // Supabase's default max-rows; a lower project setting would end paging early
        val rows = mutableListOf<T>()
        while (true) {
            val page = client.from(table).select(Columns.ALL) {
                order(orderBy, Order.ASCENDING)
                range(rows.size.toLong(), rows.size + pageSize - 1)
            }.decodeList<T>()
            rows += page
            if (page.size < pageSize) return rows
        }
    }
}

/** Sign-in state and sync runs for the UI (same flow and messages as macOS Model.swift). */
class CloudSync(private val store: Store, private val cloud: Cloud, private val scope: CoroutineScope) {
    var status by mutableStateOf("")
        private set
    var syncing by mutableStateOf(false)
        private set

    fun schedule() {
        scope.launch { sync() }
    }

    suspend fun sync() {
        cloud.awaitReady()
        if (!cloud.signedIn || syncing) return
        syncing = true
        try {
            store.sync(cloud)
            status = "Synced ${LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}"
        } catch (e: Exception) {
            Log.w("Grove", "sync failed", e)
            status = "Sync failed: ${e.friendly()}"
        } finally {
            syncing = false
        }
    }

    fun signIn(email: String, password: String, create: Boolean) = scope.launch {
        status = if (create) "Creating account…" else "Signing in…"
        try {
            if (create && !cloud.signUp(email, password)) {
                status = "Confirm the email we sent, then sign in."
                return@launch
            }
            if (!create) cloud.signIn(email, password)
            store.signedIn()
            sync()
        } catch (e: Exception) {
            Log.w("Grove", "sign-in failed", e)
            status = e.friendly()
        }
    }

    fun signOut() = scope.launch {
        cloud.signOut()
        store.signedOut()
        status = ""
    }
}

private fun Throwable.friendly(): String = when (this) {
    is AuthRestException -> errorDescription
    is RestException -> description ?: error
    is HttpRequestException -> "Can't reach the server"
    else -> message ?: toString()
}
