package com.example.pullupchecker.diagnostics

import android.util.Log

object AppLogger {
    fun camera(message: String, error: Throwable? = null) = log("camera", message, error)
    fun model(message: String, error: Throwable? = null) = log("model", message, error)
    fun analysis(message: String, error: Throwable? = null) = log("analysis", message, error)
    fun ui(message: String, error: Throwable? = null) = log("ui", message, error)
    fun lifecycle(message: String, error: Throwable? = null) = log("lifecycle", message, error)

    private fun log(category: String, message: String, error: Throwable?) {
        if (error != null) {
            Log.e("PullupChecker/$category", message, error)
        } else {
            Log.d("PullupChecker/$category", message)
        }
    }
}
