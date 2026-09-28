package com.mhma.smartlearn.data.network

/** Pulls the Moodle session cookie out of a `Cookie` header string from the WebView. */
object SessionCookies {

    private const val COOKIE_PREFIX = "MoodleSession"

    /**
     * @param header the value `CookieManager.getCookie()` returns, e.g.
     *   `MoodleSession=abc123; MOODLEID1_=deleted; _ga=...`
     * @return name and value of the session cookie, or null if there isn't one.
     *   The name is kept because admins can add a suffix (`MoodleSessionprod`).
     */
    fun moodleSession(header: String?): Pair<String, String>? {
        if (header.isNullOrBlank()) return null
        for (part in header.split(';')) {
            val trimmed = part.trim()
            val eq = trimmed.indexOf('=')
            if (eq <= 0) continue
            val name = trimmed.substring(0, eq).trim()
            val value = trimmed.substring(eq + 1).trim()
            if (name.startsWith(COOKIE_PREFIX, ignoreCase = true) && value.isNotEmpty() && value != "deleted") {
                return name to value
            }
        }
        return null
    }
}
