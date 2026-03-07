package com.amigo.shared

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
