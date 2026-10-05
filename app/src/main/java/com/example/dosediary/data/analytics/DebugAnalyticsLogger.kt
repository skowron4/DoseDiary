package com.example.dosediary.data.analytics

import android.util.Log
import com.example.dosediary.domain.analytics.AnalyticsLogger

/** Prints every event to Logcat under the [TAG] tag, e.g. `medication_added {intake_count=2}`. */
class DebugAnalyticsLogger : AnalyticsLogger {

    override fun logEvent(eventName: String, params: Map<String, String>) {
        Log.d(TAG, if (params.isEmpty()) eventName else "$eventName $params")
    }

    companion object {
        const val TAG = "DoseDiaryAnalytics"
    }
}
