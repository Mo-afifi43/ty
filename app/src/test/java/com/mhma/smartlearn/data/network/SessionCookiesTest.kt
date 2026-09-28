package com.mhma.smartlearn.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCookiesTest {

    @Test
    fun `finds the session cookie among others`() {
        val header = "MOODLEID1_=deleted; MoodleSession=abc123XYZ; _ga=GA1.2.3"
        assertEquals("MoodleSession" to "abc123XYZ", SessionCookies.moodleSession(header))
    }

    @Test
    fun `keeps a suffixed cookie name`() {
        assertEquals("MoodleSessionprod" to "v1", SessionCookies.moodleSession("MoodleSessionprod=v1"))
    }

    @Test
    fun `ignores deleted or missing cookies`() {
        assertNull(SessionCookies.moodleSession("MoodleSession=deleted; other=1"))
        assertNull(SessionCookies.moodleSession("other=1"))
        assertNull(SessionCookies.moodleSession(null))
        assertNull(SessionCookies.moodleSession(""))
    }

    @Test
    fun `landing pages and login pages are told apart`() {
        assertTrue(MoodleUrls.isLandingPage("https://smartlearning.must.edu.eg/my/"))
        assertTrue(MoodleUrls.isLandingPage("https://smartlearning.must.edu.eg/my/courses.php"))
        assertFalse(MoodleUrls.isLandingPage("https://smartlearning.must.edu.eg/login/index.php"))
        assertFalse(MoodleUrls.isLandingPage("https://login.microsoftonline.com/my/"))
        assertTrue(MoodleUrls.isLoginPage("https://smartlearning.must.edu.eg/login/index.php?authCHOICE=1"))
        assertTrue(MoodleUrls.isSiteHome("https://smartlearning.must.edu.eg/"))
        assertFalse(MoodleUrls.isSiteHome("https://smartlearning.must.edu.eg/course/view.php?id=1"))
        assertTrue(MoodleUrls.isIdentityProvider("login.microsoftonline.com"))
        assertFalse(MoodleUrls.isIdentityProvider("cdn.example.com"))
    }
}
