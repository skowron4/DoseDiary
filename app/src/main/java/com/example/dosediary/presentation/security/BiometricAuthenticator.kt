package com.example.dosediary.presentation.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.dosediary.R

sealed interface AuthResult {
    data object Success : AuthResult

    /** The device has no biometrics/screen lock enrolled, so there is nothing to authenticate with. */
    data object NotSupported : AuthResult

    /** Authentication failed or was dismissed. [message] is `null` for a plain user dismissal. */
    data class Failed(val message: String?) : AuthResult
}

/**
 * Thin wrapper around [BiometricPrompt].
 *
 * Must be constructed during `Activity.onCreate` so a prompt that is showing across a configuration
 * change re-attaches to the new Activity instance and its result is not lost.
 *
 * Accepts fingerprint/face (weak or strong class) *or* the device PIN/pattern/password, which is the
 * combination supported on every API level >= 21 and avoids locking out users whose biometrics fail.
 */
class BiometricAuthenticator(
    private val activity: FragmentActivity,
    private val onResult: (AuthResult) -> Unit,
) {
    private val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(AuthResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val dismissed = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onResult(AuthResult.Failed(message = if (dismissed) null else errString.toString()))
            }

            // onAuthenticationFailed (a single bad scan) is ignored on purpose: the prompt stays open.
        },
    )

    private val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.biometric_title))
        .setSubtitle(activity.getString(R.string.biometric_subtitle))
        .setAllowedAuthenticators(authenticators)
        .setConfirmationRequired(false)
        .build()

    fun authenticate() {
        when (BiometricManager.from(activity).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> prompt.authenticate(promptInfo)

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> onResult(AuthResult.NotSupported)

            else -> onResult(
                AuthResult.Failed(
                    message = activity.getString(R.string.biometric_unavailable),
                ),
            )
        }
    }
}
