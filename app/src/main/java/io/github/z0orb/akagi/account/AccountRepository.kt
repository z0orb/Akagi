package io.github.z0orb.akagi.account

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.z0orb.akagi.core.common.Clock
import io.github.z0orb.akagi.core.common.DispatcherProvider
import io.github.z0orb.akagi.core.common.SignInState
import io.github.z0orb.akagi.core.di.ApplicationScope
import io.github.z0orb.akagi.data.prefs.CookiePolicy
import io.github.z0orb.akagi.data.prefs.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface CookieImportResult {
    data object SignedIn : CookieImportResult

    /** YouTube cookies went in, but none of them is a sign-in (e.g. exported while signed out). */
    data object NoSignIn : CookieImportResult

    data object NoYouTubeCookies : CookieImportResult

    data object Unreadable : CookieImportResult
}

/** The YouTube sign-in that lives in the WebView's cookies (PLAN §7.15). */
@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cookies: BrowserCookies,
    settings: SettingsRepository,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val signedIn = MutableStateFlow(false)

    val state: StateFlow<SignInState> = combine(
        signedIn,
        settings.settings.map { it.cookiePolicy != CookiePolicy.NEVER },
        ::SignInState,
    ).stateIn(scope, SharingStarted.Eagerly, SignInState())

    /** Re-reads the cookies. Sign-in happens inside YouTube's own pages, so nothing tells Akagi when it changes. */
    fun refresh() {
        scope.launch { signedIn.value = YouTubeSession.isSignedIn(cookies.youtubeCookies()) }
    }

    suspend fun signOut() {
        cookies.clear()
        signedIn.value = false
    }

    /** Adds the YouTube and Google cookies from a Netscape cookies.txt to the browser. */
    suspend fun importCookies(uri: Uri): CookieImportResult {
        val text = withContext(dispatchers.io) {
            try {
                // A wrongly picked file can be huge; a cookie export is one line per cookie.
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    reader.lineSequence().take(MAX_LINES).joinToString("\n")
                }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        } ?: return CookieImportResult.Unreadable
        val imported = CookieImporter.parse(text, clock.nowMillis() / 1000)
        if (imported.isEmpty()) return CookieImportResult.NoYouTubeCookies
        cookies.add(imported)
        val nowSignedIn = YouTubeSession.isSignedIn(cookies.youtubeCookies())
        signedIn.value = nowSignedIn
        return if (nowSignedIn) CookieImportResult.SignedIn else CookieImportResult.NoSignIn
    }

    private companion object {
        const val MAX_LINES = 20_000
    }
}
