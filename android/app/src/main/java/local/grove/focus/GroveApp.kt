package local.grove.focus

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import kotlinx.coroutines.MainScope
import local.grove.focus.core.celebrationPcm

/** Holds the single [Store] (and cloud sync) shared by the UI and the alarm / notification receiver. */
class GroveApp : Application() {
    val store: Store by lazy {
        Store(File(filesDir, "data.json"), onUnsent = { cloudSync.schedule() }) { TimerNotifications.sync(this, it) }
    }
    val cloud by lazy { Cloud() }
    val cloudSync: CloudSync by lazy { CloudSync(store, cloud, MainScope()) }

    override fun onCreate() {
        super.onCreate()
        TimerNotifications.createChannels(this)
        // Restore notification + alarm after a reboot or app update (an overdue pomodoro's alarm fires at once).
        TimerNotifications.sync(this, store.data.timer)
    }
}

val Context.store get() = (applicationContext as GroveApp).store
val Context.cloudSync get() = (applicationContext as GroveApp).cloudSync
val Context.cloud get() = (applicationContext as GroveApp).cloud

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
