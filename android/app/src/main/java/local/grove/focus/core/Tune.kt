package local.grove.focus.core

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** A short, happy bell arpeggio (C5–E5–G5–C6, then a C6+E6 chord) as 16-bit mono PCM. Same as macOS Tune.swift. */
fun celebrationPcm(sampleRate: Int = 44_100): ShortArray {
    val notes = listOf(
        doubleArrayOf(523.25) to 0.12, doubleArrayOf(659.25) to 0.12, doubleArrayOf(783.99) to 0.12,
        doubleArrayOf(1046.50) to 0.14, doubleArrayOf(1046.50, 1318.51) to 0.7,
    )
    val samples = ArrayList<Short>()
    for ((freqs, length) in notes) {
        val count = (length * sampleRate).roundToInt()
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val envelope = minOf(1.0, t / 0.005) * exp(-t * 5) // quick attack, bell-like decay
            val tone = freqs.sumOf { f -> sin(2 * PI * f * t) + 0.3 * sin(4 * PI * f * t) } / freqs.size
            samples += ((0.45 * envelope * tone).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }
    return samples.toShortArray()
}
