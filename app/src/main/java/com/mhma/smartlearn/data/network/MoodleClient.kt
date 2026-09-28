package com.mhma.smartlearn.data.network

import com.mhma.smartlearn.data.network.scraper.MoodleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over OkHttp that knows two things about Moodle:
 *
 *  1. A redirect to `/login/` (or straight to Microsoft) means the session is dead.
 *     Redirects are followed by hand so we can spot that before it turns into
 *     a confusing HTML login page.
 *  2. Web-UI AJAX calls need the page's `sesskey`, which we remember from any
 *     page we happen to load.
 */
@Singleton
class MoodleClient @Inject constructor(
    private val http: OkHttpClient,
    private val session: SessionManager,
    private val parser: MoodleParser,
) {
    @Volatile
    private var sesskey: String? = null

    /** Loads a page and returns its HTML. Throws [SessionExpiredException] if we've been logged out. */
    suspend fun getHtml(url: String): String = withContext(Dispatchers.IO) {
        open(url).use { response ->
            if (!response.isSuccessful) {
                throw IOException("Smart Learning answered ${response.code} for $url")
            }
            val html = response.body?.string().orEmpty()
            if (looksLikeLoginPage(html)) expire()
            parser.sesskey(html)?.let { sesskey = it }
            html
        }
    }

    /**
     * Calls one of the AJAX methods the Moodle web UI itself uses
     * (`lib/ajax/service.php`). Returns the raw JSON response.
     */
    suspend fun callService(method: String, args: JSONObject): String = withContext(Dispatchers.IO) {
        var body = ""
        // Two tries: the second one only happens when Moodle says our sesskey went stale.
        for (attempt in 0..1) {
            val key = sesskey ?: run {
                getHtml(MoodleUrls.DASHBOARD)
                sesskey
            } ?: throw IOException("Couldn't find a session key on the page")

            val payload = JSONArray()
                .put(
                    JSONObject()
                        .put("index", 0)
                        .put("methodname", method)
                        .put("args", args)
                )
                .toString()

            val request = Request.Builder()
                .url(MoodleUrls.ajax(key, method))
                .post(payload.toRequestBody(JSON))
                .build()

            body = execute(request).use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Smart Learning answered ${response.code} for $method")
                }
                response.body?.string().orEmpty()
            }

            when {
                body.contains("servicerequireslogin") -> expire()
                body.contains("invalidsesskey") -> sesskey = null
                else -> return@withContext body
            }
        }
        body
    }

    /** Opens a GET request and returns the live response. The caller must close it. */
    suspend fun open(url: String): Response = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(url).get().build())
    }

    fun forgetSessionKey() {
        sesskey = null
    }

    private fun execute(request: Request): Response {
        if (session.state.value == SessionState.SIGNED_OUT) throw SessionExpiredException()

        var current = request
        repeat(MAX_REDIRECTS) {
            val response = http.newCall(current).execute()
            if (!response.isRedirect) return response

            val location = response.header("Location")
            response.close()
            if (location == null) throw IOException("Redirect without a Location header")

            val next = current.url.resolve(location)
                ?: throw IOException("Couldn't follow redirect to $location")
            if (isLoginBounce(next)) expire()

            // Redirects after a POST continue as GET, like a browser would.
            current = current.newBuilder().url(next).get().build()
        }
        throw IOException("Too many redirects for ${request.url}")
    }

    private fun isLoginBounce(url: HttpUrl): Boolean {
        val onSite = url.host.equals(MoodleUrls.HOST, ignoreCase = true)
        return if (onSite) MoodleUrls.isLoginPath(url.encodedPath) else MoodleUrls.isIdentityProvider(url.host)
    }

    private fun looksLikeLoginPage(html: String): Boolean =
        html.contains("id=\"page-login-index\"") || notLoggedInBody.containsMatchIn(html)

    private fun expire(): Nothing {
        sesskey = null
        session.markExpired()
        throw SessionExpiredException()
    }

    private companion object {
        const val MAX_REDIRECTS = 6
        val JSON = "application/json; charset=utf-8".toMediaType()
        // Moodle tags the <body> with "notloggedin" on every page shown to a guest.
        val notLoggedInBody = Regex("<body[^>]*\\bnotloggedin\\b")
    }
}
