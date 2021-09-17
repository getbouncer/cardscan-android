package com.getbouncer.scan.framework.util

private val illegalFileNameCharacters = setOf('"', '*', '/', ':', '<', '>', '?', '\\', '|', '+', ',', '.', ';', '=', '[', ']')

/**
 * Sanitize the name of a file for storage
 */
fun sanitizeFileName(unsanitized: String) =
    unsanitized.map { char -> if (char in illegalFileNameCharacters) "_" else char }.joinToString("")
