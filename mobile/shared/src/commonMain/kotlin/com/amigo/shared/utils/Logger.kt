package com.amigo.shared.utils

/**
 * Cross-platform logger that works on both iOS and Android
 */
object Logger {
    fun d(tag: String, message: String) {
        log("DEBUG", tag, message)
    }
    
    fun i(tag: String, message: String) {
        log("INFO", tag, message)
    }
    
    fun w(tag: String, message: String) {
        log("WARN", tag, message)
    }
    
    fun e(tag: String, message: String) {
        log("ERROR", tag, message)
    }
    
    private fun log(level: String, tag: String, message: String) {
        platformLog("[$level] $tag: $message")
    }
}

expect fun platformLog(message: String)
