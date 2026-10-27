package local.grove.focus.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class Session(
    val id: String = UUID.randomUUID().toString(),
    val tag: String,
    @Serializable(InstantSerializer::class) val start: Instant,
    val seconds: Double,
    val synced: Boolean = false,
)

/** ISO 8601 with offset; accepts PostgREST's `2026-10-25T10:00:00.123456+00:00` (fraction optional). */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(decoder.decodeString(), Instant::from)
}

enum class StatRange(val label: String) {
    TODAY("Today"), WEEK("7 Days"), MONTH("Month");

    fun start(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Instant {
        val today = now.atZone(zone).toLocalDate()
        val day = when (this) {
            TODAY -> today
            WEEK -> today.minusDays(6)
            MONTH -> today.minusMonths(1)
        }
        return day.atStartOfDay(zone).toInstant()
    }
}

/** Total seconds per tag for sessions started on/after [since], largest first. */
// ponytail: a session is counted by its start time; one spanning midnight counts fully for the start day.
fun totals(sessions: List<Session>, since: Instant): List<Pair<String, Double>> =
    sessions.filter { it.start >= since }
        .groupBy { it.tag }
        .map { (tag, list) -> tag to list.sumOf { it.seconds } }
        .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })

fun formatDuration(t: Double): String {
    val s = t.toLong()
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

fun formatTotal(t: Double): String {
    val m = t.toLong() / 60
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

/** Pomodoro length stepper: snaps to multiples of 5 within 1..180 (1 → 5 → 10, and back 10 → 5 → 1). */
fun stepMinutes(m: Int, up: Boolean): Int =
    if (up) minOf(180, (m / 5 + 1) * 5) else maxOf(1, (m - 1) / 5 * 5)
