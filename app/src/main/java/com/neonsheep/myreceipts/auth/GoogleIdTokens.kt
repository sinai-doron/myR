package com.neonsheep.myreceipts.auth

import com.neonsheep.myreceipts.R
import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.neonsheep.myreceipts.BuildConfig

/**
 * Google ID tokens from Credential Manager, minted for the backend's web client and bound to the
 * backend's challenge nonce (auth design §6.1). The legacy Google Sign-In library is not used.
 */
class GoogleIdTokens(private val activity: Activity) {

    private val credentials = CredentialManager.create(activity)

    /** Shows Google's account picker. Throws [SignInCancelled] when the user backs out. */
    suspend fun idToken(nonce: String): String {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            credentials.getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            throw SignInCancelled()
        } catch (e: NoCredentialException) {
            throw SignInFailed(activity.getString(R.string.error_no_google_account), e)
        } catch (e: GetCredentialException) {
            throw SignInFailed(activity.getString(R.string.error_google_sign_in), e)
        }
        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw SignInFailed(activity.getString(R.string.error_unexpected_credential), null)
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    companion object {
        /** After signing out, so Google doesn't silently pick the same account next time. */
        suspend fun forget(context: Context) {
            runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        }
    }
}

class SignInCancelled : Exception("cancelled")
class SignInFailed(message: String, cause: Throwable?) : Exception(message, cause)
