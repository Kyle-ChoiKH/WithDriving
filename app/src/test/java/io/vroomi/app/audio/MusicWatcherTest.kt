package io.vroomi.app.audio

import android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
import android.media.AudioAttributes.USAGE_ASSISTANT
import android.media.AudioAttributes.USAGE_MEDIA
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicWatcherTest {
    @Test fun onlyAnotherAppsMediaCountsAsMusic() {
        assertFalse(MusicWatcher.othersPlay(emptyList(), 0))
        assertTrue(MusicWatcher.othersPlay(listOf(USAGE_MEDIA), 0))
        // Vroomi's own theme sound is not the driver's music.
        assertFalse(MusicWatcher.othersPlay(listOf(USAGE_MEDIA), 1))
        assertTrue(MusicWatcher.othersPlay(listOf(USAGE_MEDIA, USAGE_MEDIA), 1))
        // Speech, engine sound and other cues are not music.
        assertFalse(MusicWatcher.othersPlay(listOf(USAGE_ASSISTANT, USAGE_ASSISTANCE_SONIFICATION), 0))
    }
}
