package com.amigo.shared.ai

actual fun <R> synchronized(lock: Any, block: () -> R): R {
    return kotlin.synchronized(lock, block)
}
