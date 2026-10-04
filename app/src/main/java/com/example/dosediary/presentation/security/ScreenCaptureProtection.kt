package com.example.dosediary.presentation.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Turns screenshot / screen-recording / recent-apps-thumbnail protection on or off.
 *
 * `FLAG_SECURE` is a window flag, so it can be changed at any time without recreating the Activity.
 */
fun Window.setSecure(enabled: Boolean) {
    if (enabled) {
        setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/**
 * Keeps the host window's `FLAG_SECURE` in sync with [enabled] (e.g. user setting or lock state).
 * Re-applied after every successful recomposition, so it also survives configuration changes.
 */
@Composable
fun ScreenCaptureProtection(enabled: Boolean) {
    val window = LocalContext.current.findActivity()?.window ?: return
    SideEffect { window.setSecure(enabled) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
