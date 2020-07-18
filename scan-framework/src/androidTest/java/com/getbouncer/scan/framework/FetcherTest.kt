package com.getbouncer.scan.framework

import androidx.test.filters.LargeTest
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.scan.framework.test.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FetcherTest {
    private val testContext = InstrumentationRegistry.getInstrumentation().context

    @Before
    fun before() {
        Config.apiKey = "uXDc2sbugrkmvj1Bm3xOTXBw7NW4llgn"
    }

    @After
    fun after() {
        Config.apiKey = null
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun fetchResource_success() = runBlockingTest {
        class ResourceFetcherImpl : ResourceFetcher() {
            override val resource: Int = R.raw.sample_resource
            override val modelVersion: String = "sample_resource"
            override val modelClass: String = "sample_class"
            override val modelFrameworkVersion: Int = 2049
        }

        assertEquals(
            expected = FetchedResource(
                modelClass = "sample_class",
                modelFrameworkVersion = 2049,
                modelVersion = "sample_resource",
                resourceId = R.raw.sample_resource
            ),
            actual = ResourceFetcherImpl().fetchData(false)
        )
    }

    @Test
    @LargeTest
    fun fetchModelFromWebDirectly_success() = runBlocking {
        class FetcherImpl : DirectDownloadWebFetcher(testContext) {
            override val url: URL = URL("https://downloads.getbouncer.com/bob/v0.5.64.16/android/bob.tflite")
            override val hash: String = "137f537e9d35e98c30a1654c78b7bace90bd3d2d12e336431ff9a65b0b4bfcc8"
            override val hashAlgorithm: String = "SHA-256"
            override val modelVersion: String = "0.5.64.16"
            override val modelClass: String = "bob"
            override val modelFrameworkVersion: Int = 1
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(false)
        assertTrue { fetchedModel is FetchedFile }

        val file = (fetchedModel as FetchedFile).file
        assertNotNull(file)

        val reader = file.reader()
        reader.skip(4)
        assertEquals('T', reader.read().toChar())
        assertEquals('F', reader.read().toChar())
        assertEquals('L', reader.read().toChar())
        assertEquals('3', reader.read().toChar())
    }

    @Test
    @LargeTest
    fun fetchModelFromWebSignedUrl_success() = runBlocking {
        class FetcherImpl : SignedUrlModelWebFetcher(testContext) {
            override val modelClass = "object_detection"
            override val modelFrameworkVersion: Int = 2049
            override val modelVersion = "v0.0.3"
            override val modelFileName = "ssd.tflite"
            override val hash: String = "7c5a294ff9a1e665f07d3e64d898062e17a2348f01b0be75b2d5295988ce6a4c"
            override val hashAlgorithm = "SHA-256"
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(false)
        assertTrue { fetchedModel is FetchedFile }

        val file = (fetchedModel as FetchedFile).file
        assertNotNull(file)

        val reader = file.reader()
        reader.skip(4)
        assertEquals('T', reader.read().toChar())
        assertEquals('F', reader.read().toChar())
        assertEquals('L', reader.read().toChar())
        assertEquals('3', reader.read().toChar())
    }

    @Test
    @LargeTest
    fun fetchModelFromWebSignedUrl_downloadFail() = runBlocking {
        class FetcherImpl : SignedUrlModelWebFetcher(testContext) {
            override val modelClass = "invalid_model"
            override val modelFrameworkVersion: Int = 2049
            override val modelVersion = "v0.0.2"
            override val modelFileName = "ssd.tflite"
            override val hash: String = "b7331fd09bf479a20e01b77ebf1b5edbd312639edf8dd883aa7b86f4b7fbfa62"
            override val hashAlgorithm = "SHA-256"
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(false)
        assertTrue { fetchedModel is FetchedFile }

        assertNull((fetchedModel as FetchedFile).file)
    }

    @Test
    @LargeTest
    fun fetchModelFromWebSignedUrl_getSignedUrlFail() = runBlocking {
        Config.apiKey = "__INTEGRATION_TEST_INVALID_KEY__"

        class FetcherImpl : SignedUrlModelWebFetcher(testContext) {
            override val modelClass = "object_detection"
            override val modelFrameworkVersion: Int = 2049
            override val modelVersion = "v0.0.3"
            override val modelFileName = "ssd.tflite"
            override val hash: String = "7c5a294ff9a1e665f07d3e64d898062e17a2348f01b0be75b2d5295988ce6a4c"
            override val hashAlgorithm = "SHA-256"
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(false)
        assertTrue { fetchedModel is FetchedFile }

        assertNull((fetchedModel as FetchedFile).file)
    }

    @Test
    @LargeTest
    fun fetchUpgradableModelFromWeb_success() = runBlocking {
        class FetcherImpl : UpdatingModelWebFetcher(testContext) {
            override val modelClass = "object_detection"
            override val modelFrameworkVersion: Int = 1
            override val defaultModelVersion: String = "v0.0.3"
            override val defaultModelFileName: String = "ssd.tflite"
            override val defaultModelHash: String = "7c5a294ff9a1e665f07d3e64d898062e17a2348f01b0be75b2d5295988ce6a4c"
            override val defaultModelHashAlgorithm = "SHA-256"
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(false)
        assertTrue { fetchedModel is FetchedFile }

        val file = (fetchedModel as FetchedFile).file
        assertNotNull(file)

        val reader = file.reader()
        reader.skip(4)
        assertEquals('T', reader.read().toChar())
        assertEquals('F', reader.read().toChar())
        assertEquals('L', reader.read().toChar())
        assertEquals('3', reader.read().toChar())
    }

    @Test
    @LargeTest
    fun fetchUpgradableModelFromWeb_successForImmediateUse() = runBlocking {
        class FetcherImpl : UpdatingModelWebFetcher(testContext) {
            override val modelClass = "object_detection"
            override val modelFrameworkVersion: Int = 1
            override val defaultModelVersion: String = "v0.0.3"
            override val defaultModelFileName: String = "ssd.tflite"
            override val defaultModelHash: String = "7c5a294ff9a1e665f07d3e64d898062e17a2348f01b0be75b2d5295988ce6a4c"
            override val defaultModelHashAlgorithm = "SHA-256"
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(true)
        assertTrue { fetchedModel is FetchedFile }

        val file = (fetchedModel as FetchedFile).file
        assertNotNull(file)

        val reader = file.reader()
        reader.skip(4)
        assertEquals('T', reader.read().toChar())
        assertEquals('F', reader.read().toChar())
        assertEquals('L', reader.read().toChar())
        assertEquals('3', reader.read().toChar())
    }

    @Test
    @LargeTest
    fun fetchUpgradableResourceModel_success() = runBlocking {
        class FetcherImpl : UpdatingResourceFetcher(testContext) {
            override val resource: Int = R.raw.sample_resource
            override val resourceModelVersion: String = "demo"
            override val resourceModelHash: String = "0dcf3e387c68dfea8dd72a183f1f765478ebaa4d8544cfc09a16e87a795d8ccf"
            override val resourceModelHashAlgorithm: String = "SHA-256"
            override val modelClass: String = "ocr"
            override val modelFrameworkVersion: Int = 1
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(false)
        assertTrue { fetchedModel is FetchedFile }

        val file = (fetchedModel as FetchedFile).file
        assertNotNull(file)

        val reader = file.reader()
        reader.skip(4)
        assertEquals('T', reader.read().toChar())
        assertEquals('F', reader.read().toChar())
        assertEquals('L', reader.read().toChar())
        assertEquals('3', reader.read().toChar())
    }

    @Test
    @LargeTest
    fun fetchUpgradableResourceModel_successForImmediateUse() = runBlocking {
        class FetcherImpl : UpdatingResourceFetcher(testContext) {
            override val resource: Int = R.raw.sample_resource
            override val resourceModelVersion: String = "demo"
            override val resourceModelHash: String = "0dcf3e387c68dfea8dd72a183f1f765478ebaa4d8544cfc09a16e87a795d8ccf"
            override val resourceModelHashAlgorithm: String = "SHA-256"
            override val modelClass: String = "ocr"
            override val modelFrameworkVersion: Int = 1
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(true)
        assertTrue { fetchedModel is FetchedResource }

        assertEquals(R.raw.sample_resource, (fetchedModel as FetchedResource).resourceId)
    }

    @Test
    @LargeTest
    fun fetchUpgradableResourceModel_downloadFail() = runBlocking {
        class FetcherImpl : UpdatingResourceFetcher(testContext) {
            override val resource: Int = R.raw.sample_resource
            override val resourceModelVersion: String = "demo"
            override val resourceModelHash: String = "0dcf3e387c68dfea8dd72a183f1f765478ebaa4d8544cfc09a16e87a795d8ccf"
            override val resourceModelHashAlgorithm: String = "SHA-256"
            override val modelClass: String = "invalid_model_class"
            override val modelFrameworkVersion: Int = 1
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(false)
        assertTrue { fetchedModel is FetchedResource }

        assertEquals(R.raw.sample_resource, (fetchedModel as FetchedResource).resourceId)
    }
}
