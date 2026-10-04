package com.example.dosediary.domain.model

/**
 * User preferences.
 *
 * @property darkModeEnabled `true`/`false` for an explicit choice, `null` to follow the system setting.
 * @property biometricsEnabled Whether the diary is locked behind biometric / device credentials.
 * @property screenProtectionEnabled Block screenshots, screen recording and the recent-apps preview
 * (Android's `FLAG_SECURE`). On by default because this is health data.
 */
data class UserSettings(
    val darkModeEnabled: Boolean? = null,
    val biometricsEnabled: Boolean = true,
    val screenProtectionEnabled: Boolean = true,
)
