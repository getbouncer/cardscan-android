package com.getbouncer.scan.payment.card

import android.content.Context
import androidx.annotation.CheckResult
import androidx.annotation.RawRes
import com.getbouncer.scan.framework.util.cacheFirstResultSuspend
import com.getbouncer.scan.payment.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.zip.ZipInputStream

sealed class CardType {
    object Credit : CardType()
    object Debit : CardType()
    object Prepaid : CardType()
    object Unknown : CardType()
}

val getTypeTable: suspend (Context) -> Map<IntRange, CardType> = cacheFirstResultSuspend { context: Context ->
    readRawZippedResourceToStringArray(context, R.raw.payment_card_types).map {
        val fields = it.split(",")
        fields[0].toInt()..fields[1].toInt() to when (fields[2]) {
            "CREDIT" -> CardType.Credit
            "DEBIT" -> CardType.Debit
            "PREPAID" -> CardType.Prepaid
            else -> CardType.Unknown
        }
    }.toMap()
}

/**
 * Read a raw resource into a [ByteBuffer].
 */
@CheckResult
private suspend fun readRawZippedResourceToStringArray(context: Context, @RawRes resourceId: Int) =
    withContext(Dispatchers.IO) {
        val byteArray = context.resources.openRawResourceFd(resourceId).use { fileDescriptor ->
            val byteBuffer = ByteBuffer.allocate(fileDescriptor.declaredLength.toInt())
            FileInputStream(fileDescriptor.fileDescriptor).use { fileInputStream ->
                fileInputStream.channel.read(byteBuffer, fileDescriptor.startOffset)
                byteBuffer.rewind()
                byteBuffer.array()
            }
        }

        ByteArrayInputStream(byteArray).use { byteArrayInputStream ->
            ZipInputStream(byteArrayInputStream).use { zipInputStream ->
                var entry = zipInputStream.nextEntry
                while (entry != null && !entry.name.endsWith(".txt")) {
                    entry = zipInputStream.nextEntry
                }
                if (entry != null) {
                    zipInputStream.bufferedReader().readLines()
                } else {
                    emptyList()
                }
            }
        }
    }
