package com.getbouncer.scan.framework

import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.scan.framework.test.R
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LoaderTest {
    private val testContext = InstrumentationRegistry.getInstrumentation().context

    @Test
    @SmallTest
    fun loadData_fromResource_success() = runBlocking {
        val fetchedData = FetchedResource(
            modelClass = "sample_class",
            modelFrameworkVersion = 2049,
            modelVersion = "sample_resource",
            resourceId = R.raw.sample_resource
        )

        val byteBuffer = Loader(testContext).loadData(fetchedData)
        assertNotNull(byteBuffer)
        assertEquals(14, byteBuffer.limit(), "File is not expected size")
        byteBuffer.rewind()

        // ensure not all bytes are zero
        var encounteredNonZeroByte = false
        while (!encounteredNonZeroByte) {
            encounteredNonZeroByte = byteBuffer.get().toInt() != 0
        }
        assertTrue(encounteredNonZeroByte, "All bytes were zero")

        // ensure bytes are correct
        byteBuffer.rewind()
        assertEquals('A', byteBuffer.get().toChar())
        assertEquals('B', byteBuffer.get().toChar())
        assertEquals('C', byteBuffer.get().toChar())
        assertEquals('1', byteBuffer.get().toChar())
    }

    @Test
    @SmallTest
    fun loadData_fromFile_success() = runBlocking {
        val sampleFile = File(testContext.cacheDir, "sample_file")
        if (sampleFile.exists()) {
            sampleFile.delete()
        }

        sampleFile.createNewFile()
        sampleFile.writeText("ABC123")

        val fetchedData = FetchedFile(
            modelClass = "sample_class",
            modelFrameworkVersion = 2049,
            modelVersion = "sample_file",
            file = sampleFile
        )

        val byteBuffer = Loader(testContext).loadData(fetchedData)
        assertNotNull(byteBuffer)
        assertEquals(6, byteBuffer.limit(), "File is not expected size")
        byteBuffer.rewind()

        // ensure not all bytes are zero
        var encounteredNonZeroByte = false
        while (!encounteredNonZeroByte) {
            encounteredNonZeroByte = byteBuffer.get().toInt() != 0
        }
        assertTrue(encounteredNonZeroByte, "All bytes were zero")

        // ensure bytes are correct
        byteBuffer.rewind()
        assertEquals('A', byteBuffer.get().toChar())
        assertEquals('B', byteBuffer.get().toChar())
        assertEquals('C', byteBuffer.get().toChar())
        assertEquals('1', byteBuffer.get().toChar())
    }
}
