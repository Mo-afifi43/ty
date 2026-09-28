package com.mhma.smartlearn.ui.web

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mhma.smartlearn.data.network.MoodleUrls
import com.mhma.smartlearn.data.network.SessionCookies

/**
 * Watches where the WebView goes and grabs the MoodleSession cookie the moment we land on a
 * signed-in page. Used by the sign-in screen and by the in-app browser, so a student who
 * signs in again while reading an assignment fixes the whole app's session as a side effect.
 */
class MoodleWebViewClient(
    private val onNavigated: (url: String, canGoBack: Boolean) -> Unit,
    private val onSessionCookie: (name: String, value: String) -> Unit,
) : WebViewClient() {

    private var lastCapturedValue: String? = null

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        if (view == null || url == null) return
        onNavigated(url, view.canGoBack())
        if (MoodleUrls.isLandingPage(url)) captureCookie()
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view == null || url == null) return
        onNavigated(url, view.canGoBack())

        when {
            MoodleUrls.isLandingPage(url) -> captureCookie()
            // Any other portal page: trust the body class Moodle sets for signed-in users.
            MoodleUrls.isOnSite(url) && !MoodleUrls.isLoginPage(url) -> {
                view.evaluateJavascript(BODY_CLASS_JS) { result ->
                    if (result != null && result.contains("userid-") && !result.contains("notloggedin")) {
                        captureCookie()
                    }
                }
            }
        }
    }

    private fun captureCookie() {
        val manager = CookieManager.getInstance()
        manager.flush()
        val (name, value) = SessionCookies.moodleSession(manager.getCookie(MoodleUrls.BASE)) ?: return
        if (value == lastCapturedValue) return
        lastCapturedValue = value
        onSessionCookie(name, value)
    }

    private companion object {
        const val BODY_CLASS_JS = "(function(){var b=document.body;return b?b.className:'';})()"
    }
}
