package com.getbouncer.scan.framework

import androidx.test.filters.LargeTest
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
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
        Config.apiKey = "qOJ_fF-WLDMbG05iBq5wvwiTNTmM2qIn"
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
            override val assetFileName: String = "sample_resource.tflite"
            override val modelVersion: String = "sample_resource"
            override val hash: String = "0dcf3e387c68dfea8dd72a183f1f765478ebaa4d8544cfc09a16e87a795d8ccf"
            override val hashAlgorithm: String = "SHA-256"
            override val modelClass: String = "sample_class"
            override val modelFrameworkVersion: Int = 2049
        }

        assertEquals(
            expected = FetchedResource(
                modelClass = "sample_class",
                modelFrameworkVersion = 2049,
                modelVersion = "sample_resource",
                modelHash = "0dcf3e387c68dfea8dd72a183f1f765478ebaa4d8544cfc09a16e87a795d8ccf",
                modelHashAlgorithm = "SHA-256",
                assetFileName = "sample_resource.tflite",
            ),
            actual = ResourceFetcherImpl().fetchData(forImmediateUse = false, isOptional = false)
        )
    }

    @Test
    @LargeTest
    fun fetchModelFromWebDirectly_success() = runBlocking {
        class FetcherImpl : DirectDownloadWebFetcher(testContext) {
            override val url = URL("https://downloads.getbouncer.com/ocr/darknite/android/darknite.tflite")
            override val hash = "0ef6e590a5c8b0da63546079a0afacd8ccb72418af68972b72fda45deaca543a"
            override val hashAlgorithm = "SHA-256"
            override val modelVersion = "darknite"
            override val modelClass = "ocr"
            override val modelFrameworkVersion = 1
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(forImmediateUse = false, isOptional = false)
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
            override val modelClass = "four_recognize"
            override val modelFrameworkVersion = 1
            override val modelVersion = "0.0.1.16"
            override val modelFileName = "fourrecognize.tflite"
            override val hash = "55eea0d57239a7e92904fb15209963f7236bd06919275bdeb0a765a94b559c97"
            override val hashAlgorithm = "SHA-256"
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(forImmediateUse = false, isOptional = false)
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
            override val modelFrameworkVersion = 1
            override val modelVersion = "0.0.1.16"
            override val modelFileName = "fourrecognize.tflite"
            override val hash = "55eea0d57239a7e92904fb15209963f7236bd06919275bdeb0a765a94b559c97"
            override val hashAlgorithm = "SHA-256"
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(forImmediateUse = false, isOptional = false)
        assertTrue { fetchedModel is FetchedFile }

        assertNull((fetchedModel as FetchedFile).file)
    }

    @Test
    @LargeTest
    fun fetchUpgradableModelFromWeb_success() = runBlocking {
        class FetcherImpl : UpdatingModelWebFetcher(testContext) {
            override val modelClass = "four_recognize"
            override val modelFrameworkVersion = 1
            override val defaultModelVersion = "0.0.1.16"
            override val defaultModelFileName = "fourrecognize.tflite"
            override val defaultModelHash = "abc"
            override val defaultModelHashAlgorithm = "SHA-256"
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(forImmediateUse = false, isOptional = false)
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
    fun fetchUpgradableModelFromWeb_fallbackSuccess() = runBlocking {
        class FetcherImpl : UpdatingModelWebFetcher(testContext) {
            override val modelClass = "four_recognize"
            override val modelFrameworkVersion = 2049
            override val defaultModelVersion = "0.0.1.16"
            override val defaultModelFileName = "fourrecognize.tflite"
            override val defaultModelHash = "55eea0d57239a7e92904fb15209963f7236bd06919275bdeb0a765a94b559c97"
            override val defaultModelHashAlgorithm = "SHA-256"
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(forImmediateUse = false, isOptional = false)
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
            override val modelClass = "four_recognize"
            override val modelFrameworkVersion = 2049
            override val defaultModelVersion = "0.0.1.16"
            override val defaultModelFileName = "fourrecognize.tflite"
            override val defaultModelHash = "55eea0d57239a7e92904fb15209963f7236bd06919275bdeb0a765a94b559c97"
            override val defaultModelHashAlgorithm = "SHA-256"
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(forImmediateUse = true, isOptional = false)
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
    fun fetchUpgradableModelFromWeb_fail() = runBlocking {
        class FetcherImpl : UpdatingModelWebFetcher(testContext) {
            override val modelClass = "four_recognize"
            override val modelFrameworkVersion = 2049
            override val defaultModelVersion = "0.0.1.16"
            override val defaultModelFileName = "fourrecognize.tflite"
            override val defaultModelHash = "abc"
            override val defaultModelHashAlgorithm = "SHA-256"
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(forImmediateUse = false, isOptional = false)
        assertTrue { fetchedModel is FetchedFile }

        assertNull((fetchedModel as FetchedFile).file)
    }

    @Test
    @LargeTest
    fun fetchUpgradableResourceModel_success() = runBlocking {
        class FetcherImpl : UpdatingResourceFetcher(testContext) {
            override val assetFileName: String = "sample_resource.tflite"
            override val resourceModelVersion = "demo"
            override val resourceModelHash = "0dcf3e387c68dfea8dd72a183f1f765478ebaa4d8544cfc09a16e87a795d8ccf"
            override val resourceModelHashAlgorithm = "SHA-256"
            override val modelClass = "four_recognize"
            override val modelFrameworkVersion = 1
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(forImmediateUse = false, isOptional = false)
        assertTrue("fetchedModel is $fetchedModel") { fetchedModel is FetchedFile }

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
            override val assetFileName: String = "sample_resource.tflite"
            override val resourceModelVersion = "demo"
            override val resourceModelHash = "0dcf3e387c68dfea8dd72a183f1f765478ebaa4d8544cfc09a16e87a795d8ccf"
            override val resourceModelHashAlgorithm = "SHA-256"
            override val modelClass = "four_recognize"
            override val modelFrameworkVersion = 1
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(forImmediateUse = true, isOptional = false)
        assertTrue { fetchedModel is FetchedResource }

        assertEquals("sample_resource.tflite", (fetchedModel as FetchedResource).assetFileName)
    }

    @Test
    @LargeTest
    fun fetchUpgradableResourceModel_downloadFail() = runBlocking {
        class FetcherImpl : UpdatingResourceFetcher(testContext) {
            override val assetFileName: String = "sample_resource.tflite"
            override val resourceModelVersion = "demo"
            override val resourceModelHash = "0dcf3e387c68dfea8dd72a183f1f765478ebaa4d8544cfc09a16e87a795d8ccf"
            override val resourceModelHashAlgorithm = "SHA-256"
            override val modelClass = "invalid_model_class"
            override val modelFrameworkVersion = 1
        }

        // force downloading the model for this test
        val fetcher = FetcherImpl()
        fetcher.clearCache()

        val fetchedModel = fetcher.fetchData(forImmediateUse = false, isOptional = false)
        assertTrue { fetchedModel is FetchedResource }

        assertEquals("sample_resource.tflite", (fetchedModel as FetchedResource).assetFileName)
    }
}
