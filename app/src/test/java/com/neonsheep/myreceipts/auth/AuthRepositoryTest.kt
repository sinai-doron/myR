package com.neonsheep.myreceipts.auth

import com.neonsheep.myreceipts.net.BackendApi
import com.neonsheep.myreceipts.net.BackendException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * The client half of the backend's mobile auth (auth design §6), against a fake backend that
 * behaves like the real one: refresh tokens rotate, and a used one revokes the session.
 */
class AuthRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val backend = FakeBackend()
    private val store = MemorySessionStore()
    private var clock = 1_000_000L
    private lateinit var auth: AuthRepository
    private lateinit var api: BackendApi

    @Before
    fun setUp() {
        server.dispatcher = backend
        server.start()
        api = BackendApi(server.url("/").toString())
        auth = AuthRepository(api, store, now = { clock })
    }

    @After
    fun tearDown() = server.close()

    private suspend fun signIn(): SignedInUser =
        auth.signIn(deviceLabel = "Pixel 10") { nonce ->
            assertEquals("nonce-1", nonce)
            "google-id-token"
        }

    @Test
    fun `sign-in sends the challenge id and ID token, and stores only the refresh token`() = runBlocking {
        val user = signIn()

        assertEquals("doron@example.com", user.email)
        assertEquals(StoredSession("refresh-1", "user-1", "doron@example.com", "Doron"), store.current())
        val body = backend.bodies.first { it.contains("idToken") }
        assertTrue(body.contains("\"challengeId\":\"challenge-1\""))
        assertTrue(body.contains("\"idToken\":\"google-id-token\""))
        assertTrue(body.contains("\"platform\":\"android\""))
        assertTrue(body.contains("\"label\":\"Pixel 10\""))
        assertFalse("the access token is never stored", store.current().toString().contains("access"))
    }

    @Test
    fun `a fresh access token is used as is`() = runBlocking {
        signIn()
        val seen = auth.withAccessToken { it }
        assertEquals("access-1", seen)
        assertEquals(0, backend.refreshes.get())
    }

    @Test
    fun `an access token near expiry is refreshed, and the new refresh token replaces the old`() = runBlocking {
        signIn()
        clock += 15 * 60_000L

        val seen = auth.withAccessToken { it }

        assertEquals("access-2", seen)
        assertEquals("refresh-2", store.current()?.refreshToken)
    }

    @Test
    fun `concurrent calls share one refresh, so a token is never presented twice`() = runBlocking {
        signIn()
        clock += 15 * 60_000L

        val tokens = (1..8).map { async { auth.withAccessToken { it } } }.awaitAll()

        assertEquals(1, backend.refreshes.get())
        assertEquals(setOf("access-2"), tokens.toSet())
        assertFalse(backend.reuseDetected)
    }

    @Test
    fun `after a restart the stored refresh token gets a new access token`() = runBlocking {
        signIn()
        val restarted = AuthRepository(api, store, now = { clock })

        assertEquals("access-2", restarted.withAccessToken { it })
    }

    @Test
    fun `a refused refresh token signs the app out`() = runBlocking {
        signIn()
        backend.revokeAll = true
        clock += 15 * 60_000L

        try {
            auth.withAccessToken { it }
            fail("expected Unauthorized")
        } catch (e: BackendException.Unauthorized) {
            // expected
        }
        assertNull(store.current())
        assertNull(auth.currentUser())
    }

    @Test
    fun `a 401 on a call refreshes once and retries`() = runBlocking {
        signIn()
        var attempts = 0
        val result = auth.withAccessToken { token ->
            attempts++
            if (token == "access-1") throw BackendException.Unauthorized
            "ok with $token"
        }
        assertEquals("ok with access-2", result)
        assertEquals(2, attempts)
    }

    @Test
    fun `a Google account off the allowlist is refused and nothing is stored`() = runBlocking {
        backend.allowlisted = false
        try {
            signIn()
            fail("expected NotAllowed")
        } catch (e: BackendException) {
            assertEquals(BackendException.NotAllowed, e)
        }
        assertNull(store.current())
    }

    @Test
    fun `sign-out forgets the session and revokes it on the backend`() = runBlocking {
        signIn()
        auth.signOut()

        assertNull(store.current())
        assertTrue(backend.loggedOutWith.contains("Bearer access-1"))
    }

    @Test
    fun `receipt PDF is posted with the bearer token and written to the target file`() = runBlocking {
        signIn()
        val target = File(tmp.root, "receipts/c-1.pdf")

        auth.withAccessToken { api.downloadReceiptPdf(it, "https://www.shufersal.co.il/r/abc", target) }

        assertEquals("%PDF-1.7 fake", target.readText())
        assertFalse(File(tmp.root, "receipts/c-1.pdf.part").exists())
        val call = backend.pdfCalls.single()
        assertEquals("Bearer access-1", call.first)
        assertEquals("""{"url":"https://www.shufersal.co.il/r/abc"}""", call.second)
    }

    @Test
    fun `a refused receipt link leaves no file behind`() = runBlocking {
        signIn()
        val target = File(tmp.root, "receipts/c-2.pdf")
        try {
            auth.withAccessToken { api.downloadReceiptPdf(it, "https://evil.example/r", target) }
            fail("expected HTTP 400")
        } catch (e: BackendException.Http) {
            assertEquals(400, e.code)
        }
        assertFalse(target.exists())
    }

    @Test
    fun `rate limiting reports Retry-After`() = runBlocking {
        signIn()
        backend.rateLimited = true
        try {
            auth.withAccessToken { api.downloadReceiptPdf(it, "https://www.shufersal.co.il/r/abc", File(tmp.root, "x.pdf")) }
            fail("expected RateLimited")
        } catch (e: BackendException.RateLimited) {
            assertEquals(42, e.retryAfterSeconds)
        }
    }

    /** Just enough of doron-backend's mobile auth and receipts API. */
    private class FakeBackend : Dispatcher() {
        val bodies: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val pdfCalls: MutableList<Pair<String?, String>> = Collections.synchronizedList(mutableListOf())
        val loggedOutWith: MutableList<String?> = Collections.synchronizedList(mutableListOf())
        val refreshes = AtomicInteger()
        var allowlisted = true
        var revokeAll = false
        var rateLimited = false

        @Volatile
        var reuseDetected = false
        private var generation = 1
        private val usedRefreshTokens = mutableSetOf<String>()

        @Synchronized
        override fun dispatch(request: RecordedRequest): MockResponse {
            val body = request.body?.utf8().orEmpty()
            bodies += body
            return when (request.url.encodedPath) {
                "/api/auth/mobile/challenge" -> json("""{"challengeId":"challenge-1","nonce":"nonce-1"}""")
                "/api/auth/mobile/google" ->
                    if (!allowlisted) json("""{"error":"not_authorized"}""", 403)
                    else json(
                        """{"accessToken":"access-1","accessTokenExpiresIn":900,"refreshToken":"refresh-1",
                           "user":{"id":"user-1","email":"doron@example.com","displayName":"Doron"}}""",
                    )
                "/api/auth/token/refresh" -> {
                    refreshes.incrementAndGet()
                    val presented = Regex("\"refreshToken\":\"([^\"]+)\"").find(body)!!.groupValues[1]
                    when {
                        revokeAll -> json("{}", 401)
                        presented in usedRefreshTokens || presented != "refresh-$generation" -> {
                            reuseDetected = true
                            revokeAll = true
                            json("{}", 401)
                        }
                        else -> {
                            usedRefreshTokens += presented
                            generation++
                            json(
                                """{"accessToken":"access-$generation","accessTokenExpiresIn":900,
                                   "refreshToken":"refresh-$generation"}""",
                            )
                        }
                    }
                }
                "/api/auth/logout" -> {
                    loggedOutWith += request.headers["Authorization"]
                    MockResponse.Builder().code(204).build()
                }
                "/api/receipts/pdf" -> {
                    pdfCalls += request.headers["Authorization"] to body
                    when {
                        rateLimited -> MockResponse.Builder().code(429).addHeader("Retry-After", "42").build()
                        !body.contains("shufersal.co.il") -> json("""{"statusCode":400}""", 400)
                        else -> MockResponse.Builder()
                            .addHeader("Content-Type", "application/pdf")
                            .body(Buffer().writeUtf8("%PDF-1.7 fake"))
                            .build()
                    }
                }
                else -> MockResponse.Builder().code(404).build()
            }
        }

        private fun json(body: String, code: Int = 200) = MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()
    }

    private class MemorySessionStore : SessionStore {
        private val state = MutableStateFlow<StoredSession?>(null)
        override val session: StateFlow<StoredSession?> = state
        override suspend fun save(session: StoredSession) {
            state.value = session
        }

        override suspend fun clear() {
            state.value = null
        }
    }
}
