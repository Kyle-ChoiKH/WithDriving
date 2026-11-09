package io.vroomi.app.settings

import android.content.Context
import io.vroomi.character.Accessory
import io.vroomi.character.CharacterSkin
import io.vroomi.character.calibre.CalibreFinish
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.engine.EnginePowertrain
import io.vroomi.core.vehicle.SourceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class CharacterSize(val label: String) { SMALL("작게"), NORMAL("보통"), LARGE("크게") }

data class Settings(
    val muted: Boolean = false,
    val voiceRare: Boolean = false,
    val calm: Boolean = false,
    val size: CharacterSize = CharacterSize.NORMAL,
    val sourceMode: SourceMode = SourceMode.AUTO,
    val wardrobe: Set<Accessory> = emptySet(),
    val userCorner: Boolean = false,
    /** The first-run welcome card was seen. */
    val onboarded: Boolean = false,
    /** Which friend rides in the kart (15 themes). */
    val skin: CharacterSkin = CharacterSkin.VROOMI,
    /** Kart world with the friends (default), the Calibre watch or the Cutaway engine. */
    val sceneTheme: SceneTheme = SceneTheme.KART,
    val calibreFinish: CalibreFinish = CalibreFinish.AUTO,
    /** Character voice lines in the themes without a character (Calibre, Cutaway); off by default. */
    val quietThemeVoice: Boolean = false,
    /** Cutaway theme: AUTO detects an EV from the battery signals. */
    val enginePowertrain: EnginePowertrain = EnginePowertrain.AUTO,
    /** Theme sound of the kit themes (music box, marble machine): media audio, off by default. */
    val themeSound: Boolean = false,
    /** Optional mechanical sound (hidden, developer panel only). */
    val mechSound: Boolean = false,
    /** Developer: frame-time overlay (debug builds only). */
    val devFrameTime: Boolean = false,
    /** Developer: every animation 5 x slower (debug builds only, not stored). */
    val devSlowMotion: Boolean = false,
    /** The backdrop colour of the theme shown last (ARGB; 0 = none yet): the first frame fades to it. */
    val launchBackdrop: Int = 0,
)

/** SharedPreferences-backed settings (small, synchronous, per device). */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("vroomi_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun read(): Settings = Settings(
        muted = prefs.getBoolean("muted", false),
        voiceRare = prefs.getBoolean("voiceRare", false),
        calm = prefs.getBoolean("calm", false),
        size = runCatching { CharacterSize.valueOf(prefs.getString("size", "NORMAL")!!) }.getOrDefault(CharacterSize.NORMAL),
        sourceMode = runCatching { SourceMode.valueOf(prefs.getString("source", "AUTO")!!) }.getOrDefault(SourceMode.AUTO),
        wardrobe = prefs.getStringSet("wardrobe", emptySet())!!.mapNotNull { n -> Accessory.entries.find { it.name == n } }.toSet(),
        userCorner = false,
        onboarded = prefs.getBoolean("onboarded", false),
        skin = runCatching { CharacterSkin.valueOf(prefs.getString("skin", "VROOMI")!!) }.getOrDefault(CharacterSkin.VROOMI),
        sceneTheme = runCatching { SceneTheme.valueOf(prefs.getString("sceneTheme", "KART")!!) }.getOrDefault(SceneTheme.KART),
        calibreFinish = runCatching { CalibreFinish.valueOf(prefs.getString("calibreFinish", "AUTO")!!) }.getOrDefault(CalibreFinish.AUTO),
        // Migrated from the Calibre-only key, so a user's earlier choice is kept.
        quietThemeVoice = if (prefs.contains("quietThemeVoice")) prefs.getBoolean("quietThemeVoice", false) else prefs.getBoolean("calibreVoice", false),
        enginePowertrain = runCatching { EnginePowertrain.valueOf(prefs.getString("enginePowertrain", "AUTO")!!) }.getOrDefault(EnginePowertrain.AUTO),
        themeSound = prefs.getBoolean("themeSound", false),
        mechSound = prefs.getBoolean("mechSound", false),
        devFrameTime = false,
        devSlowMotion = false,
        launchBackdrop = prefs.getInt("launchBackdrop", 0),
    )

    /** The Calibre power reserve and when it was saved (real time keeps draining it). */
    fun readReserve(): Pair<Float, Long>? {
        val at = prefs.getLong("calibreReserveAt", 0L)
        return if (at <= 0L) null else prefs.getFloat("calibreReserveH", 41f) to at
    }

    fun saveReserve(hours: Float, atMs: Long) {
        prefs.edit().putFloat("calibreReserveH", hours).putLong("calibreReserveAt", atMs).apply()
    }

    /** A kit theme's small persisted state (namespaced key "theme.<NAME>.state"; no personal data). */
    fun readThemeState(theme: SceneTheme): String? = prefs.getString("theme.${theme.name}.state", null)

    fun saveThemeState(theme: SceneTheme, state: String) {
        prefs.edit().putString("theme.${theme.name}.state", state).apply()
    }

    fun update(change: (Settings) -> Settings) {
        _settings.update(change)
        val s = _settings.value
        prefs.edit()
            .putBoolean("muted", s.muted)
            .putBoolean("voiceRare", s.voiceRare)
            .putBoolean("calm", s.calm)
            .putString("size", s.size.name)
            .putString("source", s.sourceMode.name)
            .putStringSet("wardrobe", s.wardrobe.map { it.name }.toSet())
            .putBoolean("onboarded", s.onboarded)
            .putString("skin", s.skin.name)
            .putString("sceneTheme", s.sceneTheme.name)
            .putString("calibreFinish", s.calibreFinish.name)
            .putBoolean("quietThemeVoice", s.quietThemeVoice)
            .putString("enginePowertrain", s.enginePowertrain.name)
            .putBoolean("themeSound", s.themeSound)
            .putBoolean("mechSound", s.mechSound)
            .putInt("launchBackdrop", s.launchBackdrop)
            .apply()
    }
}
