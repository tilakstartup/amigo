package com.amigo.shared.utils

/**
 * Multiplatform time provider
 */
expect object TimeProvider {
    fun currentTimeMillis(): Long
}
