package com.mhma.smartlearn.data.network

import java.net.URI

/**
 * Every address we touch on the portal, in one place. Keeping this file free of
 * Android imports means the parser tests can use it on a plain JVM.
 */
object MoodleUrls {
    const val HOST = "smartlearning.must.edu.eg"
    const val BASE = "https://$HOST"

    const val LOGIN = "$BASE/login/index.php"
    const val DASHBOARD = "$BASE/my/"
    const val MY_COURSES = "$BASE/my/courses.php"
    const val PROFILE = "$BASE/user/profile.php"
    const val UPCOMING_EVENTS = "$BASE/calendar/view.php?view=upcoming"
    const val AJAX_SERVICE = "$BASE/lib/ajax/service.php"

    fun course(courseId: Long) = "$BASE/course/view.php?id=$courseId"

    fun ajax(sesskey: String, method: String) = "$AJAX_SERVICE?sesskey=$sesskey&info=$method"

    /** The pages Moodle sends a student to right after a successful sign-in. */
    fun isLandingPage(url: String): Boolean {
        if (!isOnSite(url)) return false
        val path = pathOf(url) ?: return false
        return path == "/my" || path.startsWith("/my/") || path.endsWith("/courses.php")
    }

    /** Site home. Only counts as "signed in" once the page itself confirms it. */
    fun isSiteHome(url: String): Boolean {
        if (!isOnSite(url)) return false
        val path = pathOf(url) ?: return false
        return path == "/" || path == "/index.php" || path.isEmpty()
    }

    fun isOnSite(url: String): Boolean = hostOf(url)?.equals(HOST, ignoreCase = true) == true

    fun isLoginPath(path: String): Boolean = path.startsWith("/login/")

    fun isLoginPage(url: String): Boolean = isOnSite(url) && isLoginPath(pathOf(url).orEmpty())

    /** Hosts we expect to see mid-sign-in. Landing on one of them means the session is gone. */
    fun isIdentityProvider(host: String): Boolean {
        val h = host.lowercase()
        return h.contains("login.microsoftonline.com") ||
            h.contains("login.live.com") ||
            h.contains("login.microsoft.com") ||
            h.startsWith("sts.") ||
            h.startsWith("adfs.") ||
            h.contains("sso.") ||
            h.contains("auth.")
    }

    private fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()

    private fun pathOf(url: String): String? = runCatching { URI(url).path }.getOrNull()
}
