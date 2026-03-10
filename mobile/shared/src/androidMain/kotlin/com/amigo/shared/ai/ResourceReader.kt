package com.amigo.shared.ai

/**
 * Android implementation for reading resource files.
 */
actual fun readResourceFile(path: String): String {
    // Use the class loader to read from resources
    val classLoader = object {}.javaClass.classLoader
    val inputStream = classLoader?.getResourceAsStream(path)
        ?: throw IllegalArgumentException("Resource not found: $path")
    
    return inputStream.bufferedReader().use { it.readText() }
}
