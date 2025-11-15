package com.jlindemann.science.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

object AuthManager {
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    fun isSignedIn(): Boolean = firebaseAuth.currentUser != null

    fun getUid(): String? = firebaseAuth.currentUser?.uid

    fun getUserDisplayName(): String? = firebaseAuth.currentUser?.displayName
    fun getUserEmail(): String? = firebaseAuth.currentUser?.email

    fun signOut(googleClient: GoogleSignInClient? = null, onComplete: (() -> Unit)? = null) {
        try {
            firebaseAuth.signOut()
        } catch (_: Exception) { }
        try {
            googleClient?.signOut()?.addOnCompleteListener { onComplete?.invoke() } ?: onComplete?.invoke()
        } catch (_: Exception) {
            onComplete?.invoke()
        }
    }

    fun handleIdTokenForFirebase(idToken: String, onResult: (Boolean, Exception?) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, task.exception)
                }
            }
    }

    fun buildGoogleSignInClient(activity: Activity, webClientId: String): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(activity, gso)
    }
}