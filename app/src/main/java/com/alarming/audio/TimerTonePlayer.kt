package com.alarming.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.SoundPool
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.alarming.feature.reminders.ReminderTones
import com.alarming.ui.ScheduledCue
import java.util.concurrent.ConcurrentHashMap

/** Cues reached more than this long ago are dropped instead of replayed, e.g. after the CPU slept with the screen off. */
private const val STALE_CUE_MILLIS = 1_500L
private const val KEEP_WARM_SECONDS = 1

private val CueAttributes: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/**
 * Plays timer cues from samples decoded ahead of time.
 *
 * Boundary cues are started by a dedicated audio-priority thread at their exact `elapsedRealtime` deadline, so they do not
 * wait for the UI tick or main-thread work. While [keepWarm] is on, a looping silent low-latency track keeps the audio
 * output from entering standby between cues, avoiding the wake-up delay that otherwise makes the first sound late.
 */
internal class PreciseCuePlayer(private val context: Context, private val fallback: (String) -> Unit) {
    private val thread = HandlerThread("StageBellCues", Process.THREAD_PRIORITY_URGENT_AUDIO).apply { start() }
    private val handler = Handler(thread.looper)
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(CueAttributes).build()
    private val requested = ConcurrentHashMap<String, Int>()
    private val ready = ConcurrentHashMap<String, Int>()
    private val fireToken = Any()

    // Only touched on [thread].
    private var pending: ScheduledCue? = null
    private var warmTrack: AudioTrack? = null

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) requested.entries.firstOrNull { it.value == sampleId }?.let { ready[it.key] = sampleId }
        }
    }

    suspend fun preload(toneIds: Set<String>, library: ToneLibrary) {
        for (id in toneIds) {
            if (id == "Silent" || id.isBlank() || requested.containsKey(id)) continue
            val sampleId = ReminderTones.all.firstOrNull { it.id == id }?.let { pool.load(context, it.resourceId, 1) }
                ?: library.importedFile(id)?.let { pool.load(it.absolutePath, 1) }
                ?: continue
            requested[id] = sampleId
        }
    }

    fun playNow(soundId: String) {
        handler.post { play(soundId) }
    }

    /** Replaces the pending boundary cue. A cue whose deadline has already been reached still plays once. */
    fun schedule(cue: ScheduledCue?) {
        handler.post {
            if (cue == pending) return@post
            firePendingIfDue()
            handler.removeCallbacksAndMessages(fireToken)
            pending = cue ?: return@post
            val delay = (cue.atElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            handler.postAtTime({ firePendingIfDue() }, fireToken, SystemClock.uptimeMillis() + delay)
        }
    }

    fun keepWarm(enabled: Boolean) {
        handler.post { if (enabled) startWarmTrack() else stopWarmTrack() }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        handler.post {
            stopWarmTrack()
            pool.release()
        }
        thread.quitSafely()
    }

    private fun firePendingIfDue() {
        val cue = pending ?: return
        val late = SystemClock.elapsedRealtime() - cue.atElapsedRealtime
        if (late < 0) return
        pending = null
        if (late <= STALE_CUE_MILLIS) play(cue.soundId)
    }

    private fun play(soundId: String) {
        val streamId = ready[soundId]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) } ?: 0
        if (streamId == 0) fallback(soundId)
    }

    private fun startWarmTrack() {
        if (warmTrack != null) return
        val rate = context.getSystemService(AudioManager::class.java)
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48_000
        val frames = rate * KEEP_WARM_SECONDS
        warmTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(CueAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(frames * 2)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
                .apply {
                    write(ShortArray(frames), 0, frames)
                    setLoopPoints(0, frames, -1)
                    play()
                }
        } catch (_: Exception) {
            null // Cues still play; only the standby wake-up saving is lost.
        }
    }

    private fun stopWarmTrack() {
        warmTrack?.let { track ->
            runCatching { track.stop() }
            track.release()
        }
        warmTrack = null
    }
}

@Composable
internal fun rememberPreciseCuePlayer(toneIds: Set<String>, keepWarm: Boolean): PreciseCuePlayer {
    val context = LocalContext.current
    val library = rememberToneLibrary()
    val fallback by rememberUpdatedState(rememberTonePreview { })
    val player = remember { PreciseCuePlayer(context.applicationContext) { id -> fallback(id) } }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, toneIds) { player.preload(toneIds, library) }
    LaunchedEffect(player, keepWarm) { player.keepWarm(keepWarm) }
    return player
}
