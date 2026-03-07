package com.amigo.shared.ai

import platform.Foundation.NSLock

private val lock = NSLock()

actual fun <R> synchronized(lock: Any, block: () -> R): R {
    Companion.lock.lock()
    try {
        return block()
    } finally {
        Companion.lock.unlock()
    }
}

private object Companion {
    val lock = NSLock()
}
