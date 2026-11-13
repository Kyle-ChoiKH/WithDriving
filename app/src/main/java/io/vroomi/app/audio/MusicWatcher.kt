package io.vroomi.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger

/**
 * Whether another app is playing music now, for the scenes to sway along. It uses Android's public playback callback
 * (API 26+, no permission): the app learns only that media plays, never what or from which app. Vroomi's own theme
 * sound is media too, so its players are counted in [ownPlayers] and left out.
 */
class MusicWatcher(context: Context, private val onChange: (Boolean?) -> Unit) {
    private val am = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var last: Boolean? = null

    private val callback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) = report(configs)
    }

    fun start() {
        val m = am ?: return onChange(null)
        runCatching {
            m.registerAudioPlaybackCallback(callback, main)
            report(m.activePlaybackConfigurations)
        }.onFailure { onChange(null) }
    }

    fun stop() {
        runCatching { am?.unregisterAudioPlaybackCallback(callback) }
    }

    private fun report(configs: List<AudioPlaybackConfiguration>) {
        val now = othersPlay(configs.map { it.audioAttributes.usage }, ownPlayers.get())
        if (now != last) { last = now; onChange(now) }
    }

    companion object {
        /** Vroomi's own media players that are playing now (the theme sound). */
        val ownPlayers = AtomicInteger(0)

        /** True when more media players are active than Vroomi's own [own]. */
        fun othersPlay(activeUsages: List<Int>, own: Int): Boolean =
            activeUsages.count { it == AudioAttributes.USAGE_MEDIA } > own.coerceAtLeast(0)
    }
}
