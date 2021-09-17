package com.getbouncer.scan.framework.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

private val illegalFileNameCharacters = setOf('"', '*', '/', ':', '<', '>', '?', '\\', '|', '+', ',', ';', '=', '[', ']')

/**
 * Sanitize the name of a file for storage
 */
fun sanitizeFileName(unsanitized: String) =
    unsanitized.map { char -> if (char in illegalFileNameCharacters) "_" else char }.joinToString("")

/**
 * Determine if a [File] matches the expected [hash].
 */
suspend fun fileMatchesHash(localFile: File, hash: String, hashAlgorithm: String) = try {
    hash == calculateHash(localFile, hashAlgorithm)
} catch (t: Throwable) {
    false
}

/**
 * Calculate the hash of a file using the [hashAlgorithm].
 */
@Throws(IOException::class, NoSuchAlgorithmException::class)
suspend fun calculateHash(file: File, hashAlgorithm: String): String? =
    withContext(Dispatchers.IO) {
        if (file.exists()) {
            val digest = MessageDigest.getInstance(hashAlgorithm)
            FileInputStream(file).use { digest.update(it.readBytes()) }
            digest.digest().joinToString("") { "%02x".format(it) }
        } else {
            null
        }
    }