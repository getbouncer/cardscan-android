package com.getbouncer.scan.payment.card

import android.content.Context
import androidx.annotation.CheckResult
import androidx.annotation.RawRes
import com.getbouncer.scan.framework.util.cacheFirstResultSuspend
import com.getbouncer.scan.framework.util.memoizeSuspend
import com.getbouncer.scan.payment.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
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
    readRawZippedResourceToStringArray(context, R.raw.card_types).map {
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
        context.resources.openRawResourceFd(resourceId).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { fileInputStream ->
                BufferedInputStream(fileInputStream).use { bufferedInputStream ->
                    ZipInputStream(bufferedInputStream).use { zipInputStream ->
                        BufferedReader(zipInputStream.reader()).use { bufferedReader ->
                            bufferedReader.readLines()
                        }
                    }
                }
            }
        }
    }
