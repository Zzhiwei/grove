package local.grove.focus.core

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cases in `shared/fixtures/` are also run by the macOS tests. */
class CoreTest {
    private fun fixture(name: String): JsonElement =
        Json.parseToJsonElement(File(System.getProperty("fixtures"), name).readText())

    private val JsonElement.str get() = jsonPrimitive.content

    @Serializable
    private data class TagCase(
        val name: String, val local: List<String>, val server: List<RemoteTag>,
        val pending: Map<String, Boolean>, val expected: List<String>,
    )

    @Test fun mergeTagsFixtures() {
        for (c in Json.decodeFromJsonElement<List<TagCase>>(fixture("merge_tags.json"))) {
            assertEquals(c.name, c.expected, mergeTags(c.local, c.server, c.pending))
        }
    }

    @Test fun mergeSessionsFixture() {
        val f = fixture("merge_sessions.json").jsonObject
        fun sessions(key: String) = f[key]!!.jsonArray.map { Session(it.str, "T", Instant.EPOCH, 1.0) }
        assertEquals(f["expected"]!!.jsonArray.map { it.str }, mergeSessions(sessions("local"), sessions("remote")).map { it.id })
    }

    @Test fun supabaseDatesFixture() {
        for (c in fixture("supabase_dates.json").jsonArray.map { it.jsonObject }) {
            val parsed = Json.decodeFromString(InstantSerializer, "\"${c["input"]!!.str}\"")
            assertEquals(c["input"]!!.str, c["epoch"]!!.jsonPrimitive.double, parsed.toEpochMilli() / 1000.0, 0.001)
            assertEquals(parsed, Json.decodeFromString(InstantSerializer, Json.encodeToString(InstantSerializer, parsed)))
        }
    }

    @Test fun totalsFixture() {
        val f = fixture("totals.json").jsonObject
        val sessions = Json.decodeFromJsonElement<List<Session>>(f["sessions"]!!)
        val expected = f["expected"]!!.jsonArray.map { it.jsonObject["tag"]!!.str to it.jsonObject["seconds"]!!.jsonPrimitive.double }
        assertEquals(expected, totals(sessions, Instant.parse(f["since"]!!.str)))
    }

    @Test fun formattingFixture() {
        val f = fixture("formatting.json").jsonObject
        for (c in f["duration"]!!.jsonArray.map { it.jsonObject }) {
            assertEquals(c["text"]!!.str, formatDuration(c["seconds"]!!.jsonPrimitive.double))
        }
        for (c in f["total"]!!.jsonArray.map { it.jsonObject }) {
            assertEquals(c["text"]!!.str, formatTotal(c["seconds"]!!.jsonPrimitive.double))
        }
        for (c in f["step"]!!.jsonArray.map { it.jsonObject }) {
            val m = c["minutes"]!!.str.toInt()
            val up = c["up"]!!.str.toBoolean()
            assertEquals("$m up=$up", c["expected"]!!.str.toInt(), stepMinutes(m, up))
        }
    }

    @Test fun rangeStarts() {
        val now = Instant.ofEpochSecond(1_700_000_000) // 2023-11-14 22:13 UTC
        assertEquals(Instant.ofEpochSecond(1_699_920_000), StatRange.TODAY.start(now, ZoneOffset.UTC))
        assertEquals(Instant.ofEpochSecond(1_699_920_000 - 6 * 86400), StatRange.WEEK.start(now, ZoneOffset.UTC))
    }

    @Test fun celebrationLength() {
        assertEquals(960 * 3 + 1120 + 5600, celebrationPcm(sampleRate = 8000).size)
    }
}
