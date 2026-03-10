package com.amigo.shared.ai

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

/**
 * iOS implementation for reading resource files.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun readResourceFile(path: String): String {
    // Get the main bundle
    val bundle = NSBundle.mainBundle
    
    // Split path into directory and filename
    val parts = path.split("/")
    val filename = parts.last().substringBeforeLast(".")
    val extension = parts.last().substringAfterLast(".")
    val subdirectory = if (parts.size > 1) parts.dropLast(1).joinToString("/") else null
    
    // Find the resource path
    val resourcePath = bundle.pathForResource(
        name = filename,
        ofType = extension,
        inDirectory = subdirectory
    ) ?: throw IllegalArgumentException("Resource not found: $path (looked for $filename.$extension in $subdirectory)")
    
    // Read the file content
    val content = NSString.stringWithContentsOfFile(
        path = resourcePath,
        encoding = NSUTF8StringEncoding,
        error = null
    ) ?: throw IllegalArgumentException("Failed to read resource: $path")
    
    return content.toString()
}
