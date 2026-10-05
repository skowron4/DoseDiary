package com.example.dosediary

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dosediary.presentation.app.AppViewModel
import com.example.dosediary.presentation.app.LockScreen
import com.example.dosediary.presentation.navigation.DoseDiaryNavHost
import com.example.dosediary.presentation.security.AuthResult
import com.example.dosediary.presentation.security.BiometricAuthenticator
import com.example.dosediary.presentation.security.ScreenCaptureProtection
import com.example.dosediary.presentation.security.setSecure
import com.example.dosediary.ui.theme.DoseDiaryTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Single-activity host. Extends [AppCompatActivity] (a FragmentActivity, as BiometricPrompt requires)
 * so that per-app language changes also work on Android 8-12.
 *
 * Until the user is authenticated no diary content is composed at all, so nothing sensitive is
 * ever in the view hierarchy while locked.
 */
class MainActivity : AppCompatActivity() {

    private val appViewModel: AppViewModel by viewModel()

    // Created in onCreate so a prompt surviving a configuration change re-attaches to this instance.
    private lateinit var authenticator: BiometricAuthenticator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Fail-safe default: protected from the very first frame. ScreenCaptureProtection in
        // setContent then follows the user's setting / lock state once they are loaded.
        window.setSecure(true)

        authenticator = BiometricAuthenticator(this) { result ->
            when (result) {
                AuthResult.Success -> appViewModel.onAuthenticationSucceeded()
                AuthResult.NotSupported -> appViewModel.onAuthenticationNotSupported()
                is AuthResult.Failed -> appViewModel.onAuthenticationFailed(result.message)
            }
        }

        setContent {
            val state by appViewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = state.darkModeEnabled ?: isSystemInDarkTheme()

            // Dynamically toggles FLAG_SECURE (screenshots, screen recording, recents thumbnail).
            ScreenCaptureProtection(enabled = state.isScreenProtected)

            // Prompt automatically whenever the app becomes locked (cold start, or re-lock after a while away).
            LaunchedEffect(state.isLoaded, state.isLocked) {
                if (state.isLoaded && state.isLocked) startAuthentication()
            }

            DoseDiaryTheme(darkTheme = darkTheme) {
                when {
                    !state.isLoaded -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    state.isLocked -> LockScreen(
                        isAuthenticating = state.isAuthenticating,
                        message = state.lockMessage,
                        onUnlockClick = ::startAuthentication,
                    )
                    else -> DoseDiaryNavHost()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appViewModel.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) appViewModel.onAppBackgrounded()
    }

    private fun startAuthentication() {
        if (appViewModel.beginAuthentication()) authenticator.authenticate()
    }
}