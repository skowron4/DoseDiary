package com.example.dosediary.presentation.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dosediary.domain.usecase.ObserveSettingsUseCase
import com.example.dosediary.domain.util.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * @property isLoaded `false` until the persisted settings have been read (avoids a theme/lock flash).
 * @property darkModeEnabled Explicit user choice, or `null` to follow the system.
 * @property isLocked Whether the diary content must be hidden behind authentication.
 * @property isAuthenticating A biometric prompt is currently showing.
 * @property lockMessage Why the last attempt failed, if it did.
 * @property isScreenProtected Whether the window must be `FLAG_SECURE` right now. Defaults to `true`
 * so the window is protected until the real settings are known (fail-safe).
 */
data class AppUiState(
    val isLoaded: Boolean = false,
    val darkModeEnabled: Boolean? = null,
    val isLocked: Boolean = true,
    val isAuthenticating: Boolean = false,
    val lockMessage: String? = null,
    val isScreenProtected: Boolean = true,
)

/**
 * Owns the "is the diary unlocked" session state. The Activity drives the actual BiometricPrompt
 * (it needs a FragmentActivity) and reports results here.
 */
class AppViewModel(
    observeSettings: ObserveSettingsUseCase,
    private val clock: Clock,
) : ViewModel() {

    private data class Session(
        val unlocked: Boolean = false,
        val authenticating: Boolean = false,
        val message: String? = null,
    )

    private val session = MutableStateFlow(Session())
    private var backgroundedAtMillis: Long? = null

    val uiState: StateFlow<AppUiState> = combine(observeSettings(), session) { settings, session ->
        val isLocked = settings.biometricsEnabled && !session.unlocked
        AppUiState(
            isLoaded = true,
            darkModeEnabled = settings.darkModeEnabled,
            isLocked = isLocked,
            isAuthenticating = session.authenticating,
            lockMessage = session.message,
            // Protected when the user wants it, and always while the lock screen is up.
            isScreenProtected = settings.screenProtectionEnabled || isLocked,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    /** @return `true` if the caller should now show the prompt, `false` if one is already showing. */
    fun beginAuthentication(): Boolean {
        var started = false
        session.update { current ->
            if (current.authenticating) {
                current
            } else {
                started = true
                current.copy(authenticating = true, message = null)
            }
        }
        return started
    }

    fun onAuthenticationSucceeded() {
        session.value = Session(unlocked = true)
    }

    /** The device has nothing to authenticate with (no biometrics or screen lock): don't lock the user out. */
    fun onAuthenticationNotSupported() {
        session.value = Session(unlocked = true)
    }

    /** @param message System-provided text for hard errors; `null` when the user simply dismissed the prompt. */
    fun onAuthenticationFailed(message: String?) {
        session.value = Session(unlocked = false, message = message)
    }

    fun onAppBackgrounded() {
        backgroundedAtMillis = clock.nowMillis()
    }

    /** Re-locks the diary if the app was away for longer than the grace period. */
    fun onAppForegrounded() {
        val leftAt = backgroundedAtMillis ?: return
        backgroundedAtMillis = null
        if (clock.nowMillis() - leftAt > RELOCK_GRACE_MILLIS) {
            session.update { it.copy(unlocked = false) }
        }
    }

    private companion object {
        /** Short grace so permission dialogs / system pickers don't force re-authentication. */
        const val RELOCK_GRACE_MILLIS = 30_000L
    }
}
