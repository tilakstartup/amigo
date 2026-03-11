package com.amigo.shared.utils

import kotlin.time.Clock

/**
 * Utility to get current time in a way that works across all platforms
 * Uses Clock.System from kotlin.time (standard library)
 */
object CurrentTime {
    fun now() = Clock.System.now()
    
    fun nowIso8601(): String = now().toString()
    
    fun nowEpochSeconds(): Long = now().epochSeconds
}
