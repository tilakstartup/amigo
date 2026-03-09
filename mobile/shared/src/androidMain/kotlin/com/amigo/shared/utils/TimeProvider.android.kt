package com.amigo.shared.utils

actual object TimeProvider {
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()
}
