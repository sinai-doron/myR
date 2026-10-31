package com.neonsheep.myreceipts.auth

import com.neonsheep.myreceipts.net.BackendApi
import com.neonsheep.myreceipts.net.BackendException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SignedInUser(val id: String, val email: String, val displayName: String?)

/**
 * Sign-in with doron-backend and the tokens that come with it (auth design §6).
 *
 * - The access token lives in memory only and is renewed shortly before it expires.
 * - The refresh token is kept by [SessionStore] and rotates on every use. Refreshes are
 *   serialised: a second refresh with an already-used token looks like theft to the backend and
 *   revokes the whole session, so only one may ever be in flight.
 * - When the backend refuses the refresh token the session is over, and the app is signed out.
 */
class AuthRepository(
    private val api: BackendApi,
    private val store: SessionStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class AccessToken(val value: String, val expiresAt: Long)

    private val refreshLock = Mutex()

    @Volatile
    private var accessToken: AccessToken? = null

    /** The signed-in user, or null when signed out. */
    val user: Flow<SignedInUser?> = store.session.map { it?.toUser() }

    suspend fun currentUser(): SignedInUser? = store.current()?.toUser()

    /**
     * Signs in with a Google ID token minted for the backend's challenge nonce.
     * [idTokenFor] gets the nonce and returns the ID token (Credential Manager, on a device).
     */
    suspend fun signIn(deviceLabel: String, idTokenFor: suspend (nonce: String) -> String): SignedInUser {
        val challenge = api.challenge()
        val idToken = idTokenFor(challenge.nonce)
        val response = api.signInWithGoogle(challenge.challengeId, idToken, deviceLabel)
        refreshLock.withLock {
            store.save(
                StoredSession(
                    refreshToken = response.refreshToken,
                    userId = response.user.id,
                    email = response.user.email,
                    displayName = response.user.displayName,
                ),
            )
            accessToken = AccessToken(response.accessToken, expiry(response.accessTokenExpiresIn))
        }
        return SignedInUser(response.user.id, response.user.email, response.user.displayName)
    }

    /**
     * Runs [call] with a valid access token. If the backend still answers 401 (the token was
     * revoked early, say), refreshes once and tries again.
     *
     * @throws BackendException.Unauthorized when signed out or the session has ended.
     */
    suspend fun <T> withAccessToken(call: suspend (accessToken: String) -> T): T {
        val token = validAccessToken()
        return try {
            call(token)
        } catch (e: BackendException.Unauthorized) {
            call(refreshAccessToken(staleToken = token))
        }
    }

    /** Ends the session on the backend when it can be reached, and always forgets it here. */
    suspend fun signOut() {
        val token = accessToken?.value
        refreshLock.withLock {
            accessToken = null
            store.clear()
        }
        if (token != null) runCatching { api.logout(token) }
    }

    private suspend fun validAccessToken(): String {
        val current = accessToken
        if (current != null && current.expiresAt > now()) return current.value
        return refreshAccessToken(staleToken = current?.value)
    }

    private suspend fun refreshAccessToken(staleToken: String?): String = refreshLock.withLock {
        // Someone else refreshed while this call waited for the lock.
        accessToken?.let { if (it.value != staleToken && it.expiresAt > now()) return@withLock it.value }

        val session = store.current() ?: throw BackendException.Unauthorized
        val tokens = try {
            api.refresh(session.refreshToken)
        } catch (e: BackendException.Unauthorized) {
            // The session is revoked or expired, or the token was already used: sign out.
            accessToken = null
            store.clear()
            throw e
        }
        store.save(session.copy(refreshToken = tokens.refreshToken))
        AccessToken(tokens.accessToken, expiry(tokens.accessTokenExpiresIn))
            .also { accessToken = it }
            .value
    }

    // Renew a minute early, so a token never expires between this check and the server's.
    private fun expiry(expiresInSeconds: Long): Long =
        now() + (expiresInSeconds - EXPIRY_MARGIN_SECONDS).coerceAtLeast(0) * 1000

    private fun StoredSession.toUser() = SignedInUser(userId, email, displayName)

    private companion object {
        const val EXPIRY_MARGIN_SECONDS = 60L
    }
}
