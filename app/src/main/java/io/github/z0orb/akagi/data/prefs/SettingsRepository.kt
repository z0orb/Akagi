package io.github.z0orb.akagi.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.z0orb.akagi.core.common.AudioCodec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class AutoUpdate { OFF, WIFI_ONLY, ANY_NETWORK }

/** When yt-dlp may use the YouTube sign-in (PLAN §7.15, D-020). */
enum class CookiePolicy { ONLY_WHEN_NEEDED, ALWAYS, NEVER }

data class Settings(
    /** The codec and tier picked last in the format sheet; the tier is a `FormatTier` name, or null for a plain option. */
    val lastCodec: AudioCodec? = null,
    val lastTier: String? = null,
    val prefetchFormats: Boolean = true,
    val nightlyYtDlp: Boolean = false,
    val autoUpdate: AutoUpdate = AutoUpdate.WIFI_ONLY,
    val lastUpdateCheckMillis: Long = 0,
    val cookiePolicy: CookiePolicy = CookiePolicy.ONLY_WHEN_NEEDED,
    /** Route playback to a connected USB DAC even when another output is active (PLAN §7.19). */
    val preferUsbDac: Boolean = true,
    /** Open the USB DAC at each track's own sample rate (Android 14+). */
    val matchSampleRate: Boolean = true,
)

// A corrupt file would otherwise throw on every read, including the start-up yt-dlp check, so the app could never
// start again (B-10). Losing the settings to their defaults is the lesser harm.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.dataStore

    val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            lastCodec = prefs[LAST_CODEC]?.let { name -> AudioCodec.entries.firstOrNull { it.name == name } },
            lastTier = prefs[LAST_TIER],
            prefetchFormats = prefs[PREFETCH] ?: true,
            nightlyYtDlp = prefs[NIGHTLY] ?: false,
            autoUpdate = prefs[AUTO_UPDATE]?.let { name -> AutoUpdate.entries.firstOrNull { it.name == name } }
                ?: AutoUpdate.WIFI_ONLY,
            lastUpdateCheckMillis = prefs[LAST_UPDATE_CHECK] ?: 0,
            cookiePolicy = prefs[COOKIE_POLICY]?.let { name -> CookiePolicy.entries.firstOrNull { it.name == name } }
                ?: CookiePolicy.ONLY_WHEN_NEEDED,
            preferUsbDac = prefs[PREFER_USB_DAC] ?: true,
            matchSampleRate = prefs[MATCH_SAMPLE_RATE] ?: true,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun rememberChoice(codec: AudioCodec, tier: String?) = store.edit { prefs ->
        prefs[LAST_CODEC] = codec.name
        if (tier == null) prefs.remove(LAST_TIER) else prefs[LAST_TIER] = tier
    }

    suspend fun setPrefetchFormats(enabled: Boolean) = store.edit { it[PREFETCH] = enabled }

    suspend fun setNightlyYtDlp(enabled: Boolean) = store.edit { it[NIGHTLY] = enabled }

    suspend fun setAutoUpdate(mode: AutoUpdate) = store.edit { it[AUTO_UPDATE] = mode.name }

    suspend fun setLastUpdateCheck(millis: Long) = store.edit { it[LAST_UPDATE_CHECK] = millis }

    suspend fun setCookiePolicy(policy: CookiePolicy) = store.edit { it[COOKIE_POLICY] = policy.name }

    suspend fun setPreferUsbDac(enabled: Boolean) = store.edit { it[PREFER_USB_DAC] = enabled }

    suspend fun setMatchSampleRate(enabled: Boolean) = store.edit { it[MATCH_SAMPLE_RATE] = enabled }

    private companion object {
        val LAST_CODEC = stringPreferencesKey("last_codec")
        val LAST_TIER = stringPreferencesKey("last_tier")
        val PREFETCH = booleanPreferencesKey("prefetch_formats")
        val NIGHTLY = booleanPreferencesKey("nightly_ytdlp")
        val AUTO_UPDATE = stringPreferencesKey("auto_update")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val COOKIE_POLICY = stringPreferencesKey("cookie_policy")
        val PREFER_USB_DAC = booleanPreferencesKey("prefer_usb_dac")
        val MATCH_SAMPLE_RATE = booleanPreferencesKey("match_sample_rate")
    }
}
