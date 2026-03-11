package com.amigo.shared.auth

import com.amigo.shared.utils.CurrentTime

internal fun normalizeUserId(rawId: Any?): String {
    return rawId?.toString()?.trim().orEmpty()
}

internal fun normalizeEpochSeconds(rawExpiresAt: Any?): Long {
    val parsed = when (rawExpiresAt) {
        is Long -> rawExpiresAt
        is Int -> rawExpiresAt.toLong()
        is Double -> rawExpiresAt.toLong()
        is Float -> rawExpiresAt.toLong()
        is String -> rawExpiresAt.toLongOrNull()
        else -> null
    }

    val epoch = when {
        parsed == null -> null
        parsed > 1_000_000_000_000L -> parsed / 1000L
        else -> parsed
    }

    return epoch ?: (CurrentTime.nowEpochSeconds() + 3600L)
}
