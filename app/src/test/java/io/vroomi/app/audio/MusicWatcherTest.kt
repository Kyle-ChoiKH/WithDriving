package io.vroomi.app.audio

import android.media.AudioAttributes.CONTENT_TYPE_MUSIC
import android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION
import android.media.AudioAttributes.CONTENT_TYPE_SPEECH
import android.media.AudioAttributes.CONTENT_TYPE_UNKNOWN
import android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
import android.media.AudioAttributes.USAGE_ASSISTANT
import android.media.AudioAttributes.USAGE_MEDIA
import android.media.AudioAttributes.USAGE_UNKNOWN
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicWatcherTest {
    @Test fun onlyAnotherAppsMediaCountsAsMusic() {
        val media = USAGE_MEDIA to CONTENT_TYPE_MUSIC
        assertFalse(MusicWatcher.othersPlay(emptyList(), 0))
        assertTrue(MusicWatcher.othersPlay(listOf(media), 0))
        // Vroomi's own theme sound is not the driver's music.
        assertFalse(MusicWatcher.othersPlay(listOf(media), 1))
        assertTrue(MusicWatcher.othersPlay(listOf(media, media), 1))
        // Speech, engine sound and other cues are not music.
        assertFalse(MusicWatcher.othersPlay(listOf(USAGE_ASSISTANT to CONTENT_TYPE_SPEECH, USAGE_ASSISTANCE_SONIFICATION to CONTENT_TYPE_SONIFICATION), 0))
    }

    @Test fun anOldPlayerThatNeverSaidWhatItPlaysCountsAsMusic() {
        // The emulator's music app plays with no attributes at all (seen on the Pleos emulator).
        assertTrue(MusicWatcher.othersPlay(listOf(USAGE_UNKNOWN to CONTENT_TYPE_UNKNOWN), 0))
        assertFalse(MusicWatcher.othersPlay(listOf(USAGE_UNKNOWN to CONTENT_TYPE_SPEECH), 0))
        assertFalse(MusicWatcher.othersPlay(listOf(USAGE_UNKNOWN to CONTENT_TYPE_SONIFICATION), 0))
    }
}
