package com.example.pullupchecker.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("pullup_session_store", Context.MODE_PRIVATE)

    fun saveSessionSummary(summary: SessionSummary) {
        val history = loadSessionHistory().toMutableList()
        history.add(summary)
        val array = JSONArray()
        history.takeLast(MAX_SESSIONS).forEach { item ->
            array.put(
                JSONObject()
                    .put("timestamp", item.timestamp)
                    .put("reps", item.totalReps)
                    .put("peakPowerWatts", item.peakPowerWatts)
            )
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun loadSessionHistory(): List<SessionSummary> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        SessionSummary(
                            timestamp = item.optLong("timestamp"),
                            totalReps = item.optInt("reps"),
                            peakPowerWatts = item.optDouble("peakPowerWatts")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val KEY_HISTORY = "history"
        private const val MAX_SESSIONS = 20
    }
}

data class SessionSummary(
    val timestamp: Long,
    val totalReps: Int,
    val peakPowerWatts: Double
)
