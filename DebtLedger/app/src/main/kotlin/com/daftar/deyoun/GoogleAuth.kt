package com.daftar.deyoun

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google sign-in for Drive using Google Play services' Authorization API.
 *
 * Unlike the website, there is no one-hour popup problem: once the user has approved access, calling
 * authorize() again returns a fresh access token silently. The screen is only needed the first time
 * (or if the user revoked access).
 */
class GoogleAuth(private val ctx: Context) {
    sealed interface Result {
        data class Token(val value: String) : Result
        data class NeedsConsent(val intent: PendingIntent) : Result
    }

    suspend fun authorize(): Result = suspendCancellableCoroutine { cont ->
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA)))
            .build()
        Identity.getAuthorizationClient(ctx)
            .authorize(request)
            .addOnSuccessListener { res ->
                val pi = res.pendingIntent
                val token = res.accessToken
                if (res.hasResolution() && pi != null) {
                    cont.resume(Result.NeedsConsent(pi))
                } else if (token != null) {
                    cont.resume(Result.Token(token))
                } else {
                    cont.resumeWithException(IllegalStateException("No access token returned"))
                }
            }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    companion object {
        const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    }
}
