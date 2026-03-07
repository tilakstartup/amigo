package com.amigo.shared.utils

import android.util.Log

actual fun platformLog(message: String) {
    Log.d("Amigo", message)
}
