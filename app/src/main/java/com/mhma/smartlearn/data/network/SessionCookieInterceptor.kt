package com.mhma.smartlearn.data.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the stored MoodleSession cookie to every request that goes to the portal.
 * Requests to other hosts (file CDNs, identity providers) are left untouched.
 */
class SessionCookieInterceptor(
    private val session: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en")

        if (original.url.host.equals(MoodleUrls.HOST, ignoreCase = true)) {
            session.cookieHeader()?.let { builder.header("Cookie", it) }
        }
        return chain.proceed(builder.build())
    }

    private companion object {
        // Moodle serves the full desktop markup to anything that looks like a browser,
        // which is exactly what the parser expects.
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36 SmartLearn/1.0"
    }
}
