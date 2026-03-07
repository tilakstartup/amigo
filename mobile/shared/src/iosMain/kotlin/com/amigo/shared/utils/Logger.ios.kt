package com.amigo.shared.utils

import platform.Foundation.NSLog

actual fun platformLog(message: String) {
    NSLog(message)
}
