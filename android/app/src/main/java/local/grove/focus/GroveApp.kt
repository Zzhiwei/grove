package local.grove.focus

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import local.grove.focus.core.celebrationPcm

/** Holds the single [Store] shared by the UI and (later) the alarm and notification receivers. */
class GroveApp : Application() {
    val store by lazy { Store(File(filesDir, "data.json")) }
}

val Context.store get() = (applicationContext as GroveApp).store

private val tune by lazy { celebrationPcm() }

/** Plays the pomodoro-complete tune (generated in memory, no audio asset). */
fun playCelebration() {
    val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT) // follows the ringer / Do Not Disturb
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        )
        .setAudioFormat(
            AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(44_100)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(tune.size * 2)
        .build()
    track.write(tune, 0, tune.size)
    track.notificationMarkerPosition = tune.size
    track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
        override fun onMarkerReached(t: AudioTrack) = t.release()
        override fun onPeriodicNotification(t: AudioTrack) {}
    })
    track.play()
}
