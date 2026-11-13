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
        // The callback lists only the players that are playing now; a paused player drops out of it.
        val now = othersPlay(configs.map { it.audioAttributes.let { a -> a.usage to a.contentType } }, ownPlayers.get())
        if (now != last) { last = now; onChange(now) }
    }

    companion object {
        /** Vroomi's own media players that are playing now (the theme sound). */
        val ownPlayers = AtomicInteger(0)

        /** True when more music players (usage, content type) are active than Vroomi's own [own]. */
        fun othersPlay(active: List<Pair<Int, Int>>, own: Int): Boolean =
            active.count { (usage, content) -> isMusic(usage, content) } > own.coerceAtLeast(0)

        /**
         * Media, or a player that never said what it is: older players that only set the music stream (the emulator's
         * own music app among them) report an unknown usage, so they count unless they say they are speech or a cue.
         */
        fun isMusic(usage: Int, content: Int): Boolean = usage == AudioAttributes.USAGE_MEDIA ||
            usage == AudioAttributes.USAGE_UNKNOWN &&
            content != AudioAttributes.CONTENT_TYPE_SPEECH && content != AudioAttributes.CONTENT_TYPE_SONIFICATION
    }
}
