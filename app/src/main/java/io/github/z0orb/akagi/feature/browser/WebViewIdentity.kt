package io.github.z0orb.akagi.feature.browser

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebSettings
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Makes the WebView look like Chrome everywhere a site can look (PLAN §7.1, §7.15): the UA string, the
 * client-hint brands and the X-Requested-With header. Google's sign-in refuses embedded browsers it recognises.
 */
// The header allow-list is deprecated and restricted in androidx.webkit 1.17, yet it is still the only way
// to stop WebView sending the app's package name to every site.
@SuppressLint("RestrictedApi")
internal object WebViewIdentity {
    private const val TAG = "WebViewIdentity"

    @Suppress("DEPRECATION")
    fun hide(settings: WebSettings) {
        settings.userAgentString = UserAgents.chromeLike(settings.userAgentString)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            val metadata = WebSettingsCompat.getUserAgentMetadata(settings)
            val brands = metadata.brandVersionList.map { brand ->
                UserAgentMetadata.BrandVersion.Builder(brand).setBrand(UserAgents.chromeBrand(brand.brand)).build()
            }
            WebSettingsCompat.setUserAgentMetadata(
                settings,
                UserAgentMetadata.Builder(metadata).setBrandVersionList(brands).build(),
            )
        }
        val headerHidden = WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)
        if (headerHidden) WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
        Log.i(
            TAG,
            "brands=${WebSettingsCompat.getUserAgentMetadata(settings).brandVersionList.map { it.brand }} " +
                "requestedWithHidden=$headerHidden",
        )
    }
}
