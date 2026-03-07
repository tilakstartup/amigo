package com.amigo.shared

class Greeting {
    private val platform: Platform = getPlatform()

    fun greet(): String {
        return "Hello from Amigo on ${platform.name}!"
    }
}
