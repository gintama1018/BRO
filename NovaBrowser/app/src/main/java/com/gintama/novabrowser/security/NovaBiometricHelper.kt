package com.gintama.novabrowser.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * NovaBiometricHelper: Manages hardware biometric authentication (Fingerprint, Face)
 * and device credential (PIN, pattern, password) fallbacks for Private Vault & App Lock.
 */
object NovaBiometricHelper {

    const val PREF_LOCK_PRIVATE_TABS = "pref_biometric_private_tabs"
    const val PREF_LOCK_APP_LAUNCH = "pref_biometric_app_lock"

    /**
     * Checks if the device has either hardware biometrics enrolled or a secure lockscreen (PIN/Pattern/Password).
     */
    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val result = biometricManager.canAuthenticate(authenticators)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Checks if biometric private tab protection is enabled in preferences.
     */
    fun isPrivateTabLockEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_LOCK_PRIVATE_TABS, false)
    }

    /**
     * Checks if app launch lock is enabled in preferences.
     */
    fun isAppLockEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_LOCK_APP_LAUNCH, false)
    }

    /**
     * Triggers the native BiometricPrompt dialog.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Private Tabs",
        subtitle: String = "Verify identity via fingerprint or device PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Single attempt failed; prompt stays open for retry
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }
}
