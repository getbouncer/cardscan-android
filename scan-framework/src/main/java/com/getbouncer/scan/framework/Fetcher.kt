package com.getbouncer.scan.framework

import android.content.Context
import android.util.Log
import androidx.annotation.RawRes
import com.getbouncer.scan.framework.api.NetworkResult
import com.getbouncer.scan.framework.api.getModelSignedUrl
import com.getbouncer.scan.framework.api.getModelUpgradeDetails
import com.getbouncer.scan.framework.time.ClockMark
import com.getbouncer.scan.framework.time.asEpochMillisecondsClockMark
import com.getbouncer.scan.framework.time.weeks
import com.getbouncer.scan.framework.util.memoizeSuspend
import com.getbouncer.scan.framework.util.retry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception
import java.net.URL
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

private val CACHE_MODEL_TIME = 1.weeks
private const val CACHE_MODEL_MAX_COUNT = 3

private const val PURPOSE_MODEL_UPGRADE = "model_upgrade"

/**
 * Fetched data metadata.
 */
sealed class FetchedModelMeta(open val modelVersion: String)
data class FetchedModelFileMeta(
    override val modelVersion: String,
    val modelFile: File?
) : FetchedModelMeta(modelVersion)

data class FetchedModelResourceMeta(
    override val modelVersion: String,
    @RawRes val resourceId: Int?
) : FetchedModelMeta(modelVersion)

/**
 * Fetched data information.
 */
sealed class FetchedData(
    open val modelClass: String,
    open val modelFrameworkVersion: Int,
    open val modelVersion: String,
    val successfullyFetched: Boolean
) {
    companion object {
        fun fromFetchedModelMeta(modelClass: String, modelFrameworkVersion: Int, meta: FetchedModelMeta) = when (meta) {
            is FetchedModelFileMeta -> FetchedFile(modelClass, modelFrameworkVersion, meta.modelVersion, meta.modelFile)
            is FetchedModelResourceMeta -> FetchedResource(modelClass, modelFrameworkVersion, meta.modelVersion, meta.resourceId)
        }
    }
}

data class FetchedResource(
    override val modelClass: String,
    override val modelFrameworkVersion: Int,
    override val modelVersion: String,
    @RawRes val resourceId: Int?
) : FetchedData(modelClass, modelFrameworkVersion, modelVersion, resourceId != null)

data class FetchedFile(
    override val modelClass: String,
    override val modelFrameworkVersion: Int,
    override val modelVersion: String,
    val file: File?
) : FetchedData(modelClass, modelFrameworkVersion, modelVersion, file != null)

/**
 * An interface for getting data ready to be loaded into memory.
 */
interface Fetcher {
    val modelClass: String
    val modelFrameworkVersion: Int

    /**
     * Prepare data to be loaded into memory. If the fetched data is to be used immediately, the fetcher will prioritize
     * fetching from the cache over getting the latest version.
     */
    suspend fun fetchData(forImmediateUse: Boolean): FetchedData
}

/**
 * A [Fetcher] that gets data from android resources.
 */
abstract class ResourceFetcher : Fetcher {
    protected abstract val modelVersion: String
    protected abstract val resource: Int

    override suspend fun fetchData(forImmediateUse: Boolean): FetchedResource =
        FetchedResource(
            modelClass,
            modelFrameworkVersion,
            modelVersion,
            resource
        )
}

/**
 * A [Fetcher] that downloads data from the web.
 */
sealed class WebFetcher : Fetcher {
    protected data class DownloadDetails(val url: URL, val hash: String, val hashAlgorithm: String, val modelVersion: String)

    private val fetchDataMutex = Mutex()

    /**
     * Keep track of any exceptions that occurred when fetching data  after the specified number of retries. This is
     * used to prevent the fetcher from repeatedly trying to fetch the data from multiple threads after the number of
     * retries has been reached.
     */
    private var fetchException: Throwable? = null

    override suspend fun fetchData(forImmediateUse: Boolean): FetchedData = fetchDataMutex.withLock {
        val stat = Stats.trackRepeatingTask("web_fetcher_$modelClass")
        val cachedData = FetchedData.fromFetchedModelMeta(modelClass, modelFrameworkVersion, tryFetchLatestCachedData())

        // if a previous exception was encountered, attempt to fetch cached data
        fetchException?.run {
            if (cachedData.successfullyFetched) {
                stat.trackResult("success")
            } else {
                stat.trackResult(this::class.java.simpleName)
            }
            return@withLock cachedData
        }

        // attempt to fetch the data from local cache if it's needed immediately
        if (forImmediateUse) {
            tryFetchLatestCachedData().run {
                val data = FetchedData.fromFetchedModelMeta(modelClass, modelFrameworkVersion, this)
                if (data.successfullyFetched) {
                    stat.trackResult("success")
                    return@withLock data
                }
            }
        }

        // get details for downloading the data. If download details cannot be retrieved, use the latest cached version
        val downloadDetails = getDownloadDetails(!cachedData.successfullyFetched) ?: run {
            stat.trackResult("download_details_failure")
            return@withLock cachedData
        }

        // check the local cache for a matching model
        tryFetchMatchingCachedFile(downloadDetails.hash, downloadDetails.hashAlgorithm).run {
            val data = FetchedData.fromFetchedModelMeta(modelClass, modelFrameworkVersion, this)
            if (data.successfullyFetched) {
                stat.trackResult("success")
                return@withLock data
            }
        }

        // download the model
        val downloadOutputFile = getDownloadOutputFile(downloadDetails.modelVersion)
        try {
            downloadAndVerify(
                downloadDetails.url,
                downloadOutputFile,
                downloadDetails.hash,
                downloadDetails.hashAlgorithm
            )
        } catch (t: Throwable) {
            fetchException = t
            if (cachedData.successfullyFetched) {
                Log.w(Config.logTag, "Failed to download model $modelClass, loaded from local cache", t)
                stat.trackResult("success")
            } else {
                Log.e(Config.logTag, "Failed to download model $modelClass, no local cache available", t)
                stat.trackResult(t::class.java.simpleName)
            }
            return@withLock cachedData
        }

        cleanUpPostDownload(downloadOutputFile)

        FetchedFile(
            modelClass,
            modelFrameworkVersion,
            downloadDetails.modelVersion,
            downloadOutputFile
        )
    }

    /**
     * Attempt to load the data from the local cache.
     */
    protected abstract suspend fun tryFetchLatestCachedData(): FetchedModelMeta

    /**
     * Attempt to load a cached data given the required [hash] and [hashAlgorithm].
     */
    protected abstract suspend fun tryFetchMatchingCachedFile(hash: String, hashAlgorithm: String): FetchedModelMeta

    /**
     * Get [DownloadDetails] for the data that will be downloaded.
     *
     * @param required: true if download details are required (no cache available)
     */
    protected abstract suspend fun getDownloadDetails(required: Boolean): DownloadDetails?

    /**
     * Get the file where the data should be downloaded.
     */
    protected abstract suspend fun getDownloadOutputFile(modelVersion: String): File

    /**
     * After download, clean up.
     */
    protected abstract suspend fun cleanUpPostDownload(downloadedFile: File)

    /**
     * Clear the cache for this loader. This will force new downloads.
     */
    abstract suspend fun clearCache()
}

/**
 * A [WebFetcher] that directly downloads a model.
 */
abstract class DirectDownloadWebFetcher(private val context: Context) : WebFetcher() {
    abstract val url: URL
    abstract val hash: String
    abstract val hashAlgorithm: String
    abstract val modelVersion: String

    private val localFileName: String by lazy { url.path.replace('/', '_') }

    override suspend fun tryFetchLatestCachedData(): FetchedModelMeta {
        val localFile = getDownloadOutputFile(modelVersion)
        return if (fileMatchesHash(localFile, hash, hashAlgorithm)) {
            FetchedModelFileMeta(modelVersion, localFile)
        } else {
            FetchedModelFileMeta(modelVersion, null)
        }
    }

    override suspend fun tryFetchMatchingCachedFile(hash: String, hashAlgorithm: String): FetchedModelMeta =
        FetchedModelFileMeta(modelVersion, null)

    override suspend fun getDownloadOutputFile(modelVersion: String) =
        File(context.cacheDir, localFileName)

    override suspend fun getDownloadDetails(required: Boolean): DownloadDetails? =
        DownloadDetails(url, hash, hashAlgorithm, modelVersion)

    override suspend fun cleanUpPostDownload(downloadedFile: File) { /* nothing to do */ }

    override suspend fun clearCache() {
        val localFile = getDownloadOutputFile(modelVersion)
        if (localFile.exists()) {
            localFile.delete()
        }
    }
}

/**
 * A [WebFetcher] that uses the signed URL server endpoints to download data.
 */
abstract class SignedUrlModelWebFetcher(private val context: Context) : DirectDownloadWebFetcher(context) {
    abstract val modelFileName: String

    private val localFileName by lazy { "${modelClass}_${modelFileName}_$modelVersion" }

    // this field is not used by this class
    override val url: URL = URL(NetworkConfig.baseUrl)

    override suspend fun getDownloadOutputFile(modelVersion: String) = File(context.cacheDir, localFileName)

    override suspend fun getDownloadDetails(required: Boolean) =
        when (val signedUrlResponse = getModelSignedUrl(context, modelClass, modelVersion, modelFileName)) {
            is NetworkResult.Success ->
                try {
                    URL(signedUrlResponse.body.modelUrl)
                } catch (t: Throwable) {
                    Log.e(Config.logTag, "Invalid signed url for model $modelClass: ${signedUrlResponse.body.modelUrl}", t)
                    null
                }
            is NetworkResult.Error -> {
                Log.w(Config.logTag, "Failed to get signed url for model $modelClass: ${signedUrlResponse.error}")
                null
            }
            is NetworkResult.Exception -> {
                Log.e(Config.logTag, "Exception fetching signed url for model $modelClass: ${signedUrlResponse.responseCode}", signedUrlResponse.exception)
                null
            }
        }?.let { DownloadDetails(it, hash, hashAlgorithm, modelVersion) }
}

/**
 * A [WebFetcher] that queries Bouncer servers for updated data. If a new version is found, download it. If the data
 * details match what is cached, return the cached version instead.
 */
abstract class UpdatingModelWebFetcher(private val context: Context) : SignedUrlModelWebFetcher(context) {
    abstract val defaultModelVersion: String
    abstract val defaultModelFileName: String
    abstract val defaultModelHash: String
    abstract val defaultModelHashAlgorithm: String

    private var cachedDownloadDetails: DownloadDetails? = null

    private val getCacheFolder = memoizeSuspend<File> {
        ensureLocalFolder("${modelClass}_$modelFrameworkVersion")
    }

    override val modelVersion: String by lazy { defaultModelVersion }
    override val modelFileName: String by lazy { defaultModelFileName }
    override val hash: String by lazy { defaultModelHash }
    override val hashAlgorithm: String by lazy { defaultModelHashAlgorithm }

    override suspend fun tryFetchLatestCachedData(): FetchedModelMeta =
        getLatestFile()?.let { FetchedModelFileMeta(it.name, it) } ?: FetchedModelFileMeta(defaultModelVersion, null)

    override suspend fun tryFetchMatchingCachedFile(hash: String, hashAlgorithm: String): FetchedModelMeta =
        getMatchingFile(hash, hashAlgorithm)?.let { FetchedModelFileMeta(it.name, it) } ?: FetchedModelFileMeta(defaultModelVersion, null)

    override suspend fun getDownloadOutputFile(modelVersion: String) =
        File(getCacheFolder(), modelVersion)

    override suspend fun getDownloadDetails(required: Boolean): DownloadDetails? {
        cachedDownloadDetails?.let { return DownloadDetails(url, hash, hashAlgorithm, modelVersion) }

        val nextUpgradeTime = getNextUpgradeTime()
        when {
            nextUpgradeTime.hasPassed() ->
                Log.d(Config.logTag, "Time to upgrade $modelClass, fetching upgrade details")
            required ->
                Log.d(Config.logTag, "Downloading initial version of $modelClass")
            else -> {
                Log.d(Config.logTag, "Not yet time to upgrade $modelClass (will upgrade at $nextUpgradeTime)")
                return null
            }
        }

        return when (val modelUpgradeResponse = getModelUpgradeDetails(context, modelClass, modelFrameworkVersion)) {
            is NetworkResult.Success ->
                try {
                    modelUpgradeResponse.body.queryAgainAfterMs?.asEpochMillisecondsClockMark()?.apply {
                        setNextModelUpgradeAttemptTime(this)
                    }
                    DownloadDetails(
                        url = URL(modelUpgradeResponse.body.url),
                        hash = modelUpgradeResponse.body.hash,
                        hashAlgorithm = modelUpgradeResponse.body.hashAlgorithm,
                        modelVersion = modelUpgradeResponse.body.modelVersion,
                    ).apply { cachedDownloadDetails = this }
                } catch (t: Throwable) {
                    Log.e(Config.logTag, "Invalid signed url for model $modelClass: ${modelUpgradeResponse.body.url}", t)
                    null
                }
            is NetworkResult.Error -> {
                Log.w(Config.logTag, "Failed to get latest details for model $modelClass: ${modelUpgradeResponse.error}")
                fallbackDownloadDetails()
            }
            is NetworkResult.Exception -> {
                Log.e(Config.logTag, "Exception retrieving latest details for model $modelClass: ${modelUpgradeResponse.responseCode}", modelUpgradeResponse.exception)
                fallbackDownloadDetails()
            }
        }
    }

    /**
     * Determine if we should query for a model upgrade
     */
    protected open fun getNextUpgradeTime(): ClockMark =
        StorageFactory
            .getStorageInstance(context, PURPOSE_MODEL_UPGRADE)
            .getLong(modelClass, 0)
            .asEpochMillisecondsClockMark()

    protected open fun setNextModelUpgradeAttemptTime(time: ClockMark) {
        StorageFactory
            .getStorageInstance(context, PURPOSE_MODEL_UPGRADE)
            .storeValue(modelClass, time.toMillisecondsSinceEpoch())
    }

    /**
     * Fall back to getting the download details.
     */
    protected open suspend fun fallbackDownloadDetails() =
        super.getDownloadDetails(true)?.apply { cachedDownloadDetails = this }

    /**
     * Delete all files in cache that are not the recently downloaded file.
     */
    override suspend fun cleanUpPostDownload(downloadedFile: File) = withContext(Dispatchers.IO) {
        getCacheFolder()
            .listFiles()
            ?.filter { it != downloadedFile && calculateHash(it, defaultModelHashAlgorithm) != defaultModelHash }
            ?.sortedByDescending { it.lastModified() }
            ?.filterIndexed { index, file ->
                file.lastModified().asEpochMillisecondsClockMark()
                    .elapsedSince() > CACHE_MODEL_TIME || index > CACHE_MODEL_MAX_COUNT
            }
            ?.forEach { it.delete() }
            .let { Unit }
    }

    /**
     * If a file in the cache directory matches the provided [hash], return it.
     */
    private suspend fun getMatchingFile(hash: String, hashAlgorithm: String): File? = withContext(Dispatchers.IO) {
        getCacheFolder()
            .listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.firstOrNull { calculateHash(it, hashAlgorithm) == hash }
    }

    /**
     * Get the most recently created file in the cache folder. Return null if no files in this
     */
    private suspend fun getLatestFile() = withContext(Dispatchers.IO) {
        getCacheFolder().listFiles()?.maxByOrNull { it.lastModified() }
    }

    /**
     * Ensure that the local folder exists and get it.
     */
    private suspend fun ensureLocalFolder(folderName: String): File = withContext(Dispatchers.IO) {
        val localFolder = File(context.cacheDir, folderName)
        if (localFolder.exists() && !localFolder.isDirectory) {
            localFolder.delete()
        }
        if (!localFolder.exists()) {
            localFolder.mkdirs()
        }
        localFolder
    }

    /**
     * Force re-download of models by clearing the cache.
     */
    override suspend fun clearCache() = withContext(Dispatchers.IO) {
        getCacheFolder().deleteRecursively()
        getCacheFolder().mkdirs()

        StorageFactory
            .getStorageInstance(context, PURPOSE_MODEL_UPGRADE)
            .clear()
    }.let { Unit }
}

/**
 * A [WebFetcher] that queries Bouncer servers for updated data. If a new version is found, download it. If the data
 * details match what is cached, return the cached version instead.
 */
abstract class UpdatingResourceFetcher(context: Context) : UpdatingModelWebFetcher(context) {
    protected abstract val resource: Int
    protected abstract val resourceModelVersion: String
    protected abstract val resourceModelHash: String
    protected abstract val resourceModelHashAlgorithm: String

    override val defaultModelFileName: String = ""
    override val defaultModelVersion: String by lazy { resourceModelVersion }
    override val defaultModelHash: String by lazy { resourceModelHash }
    override val defaultModelHashAlgorithm: String by lazy { resourceModelHashAlgorithm }

    override suspend fun tryFetchLatestCachedData() = super.tryFetchLatestCachedData().run {
        when (this) {
            is FetchedModelFileMeta -> if (modelFile == null) fetchModelFromResource() else this
            is FetchedModelResourceMeta -> this
        }
    }

    override suspend fun tryFetchMatchingCachedFile(hash: String, hashAlgorithm: String): FetchedModelMeta =
        if (hash == defaultModelHash && hashAlgorithm == defaultModelHashAlgorithm) {
            fetchModelFromResource()
        } else {
            super.tryFetchMatchingCachedFile(hash, hashAlgorithm)
        }

    override suspend fun fallbackDownloadDetails(): DownloadDetails? = DownloadDetails(
        url = URL("https://localhost"),
        hash = resourceModelHash,
        hashAlgorithm = resourceModelHashAlgorithm,
        modelVersion = resourceModelVersion
    )

    private fun fetchModelFromResource(): FetchedModelMeta =
        FetchedModelResourceMeta(
            modelVersion = resourceModelVersion,
            resourceId = resource
        )
}

/**
 * Determine if a [File] matches the expected [hash].
 */
private suspend fun fileMatchesHash(localFile: File, hash: String, hashAlgorithm: String) = try {
    hash == calculateHash(localFile, hashAlgorithm)
} catch (t: Throwable) {
    false
}

/**
 * Download a file from a given [url] and ensure that it matches the expected [hash].
 */
@Throws(IOException::class, FileCreationException::class, NoSuchAlgorithmException::class, HashMismatchException::class)
private suspend fun downloadAndVerify(
    url: URL,
    outputFile: File,
    hash: String,
    hashAlgorithm: String
) {
    downloadFile(url, outputFile)
    val calculatedHash = calculateHash(outputFile, hashAlgorithm)

    if (hash != calculatedHash) {
        withContext(Dispatchers.IO) { outputFile.delete() }
        throw HashMismatchException(hashAlgorithm, hash, calculatedHash)
    }
}

/**
 * Calculate the hash of a file using the [hashAlgorithm].
 */
@Throws(IOException::class, NoSuchAlgorithmException::class)
private suspend fun calculateHash(file: File, hashAlgorithm: String): String? = withContext(Dispatchers.IO) {
    if (file.exists()) {
        val digest = MessageDigest.getInstance(hashAlgorithm)
        FileInputStream(file).use { digest.update(it.readBytes()) }
        digest.digest().joinToString("") { "%02x".format(it) }
    } else {
        null
    }
}

/**
 * Download a file from the provided [url] into the provided [outputFile].
 */
@Throws(IOException::class, FileCreationException::class)
private suspend fun downloadFile(url: URL, outputFile: File) = withContext(Dispatchers.IO) {
    retry(
        NetworkConfig.retryDelay,
        excluding = listOf(FileNotFoundException::class.java)
    ) {
        val urlConnection = url.openConnection()

        if (outputFile.exists()) {
            outputFile.delete()
        }

        if (!outputFile.createNewFile()) {
            throw FileCreationException(outputFile.name)
        }

        urlConnection.getInputStream().use { stream ->
            FileOutputStream(outputFile).use { it.write(stream.readBytes()) }
        }
    }
}

/**
 * A file does not match the expected hash value.
 */
class HashMismatchException(val algorithm: String, val expected: String, val actual: String?) :
    Exception("Invalid hash result for algorithm '$algorithm'. Expected '$expected' but got '$actual'") {
    override fun toString() = "HashMismatchException(algorithm='$algorithm', expected='$expected', actual='$actual')"
}

/**
 * Unable to create a file.
 */
class FileCreationException(val fileName: String) : Exception("Unable to create local file '$fileName'") {
    override fun toString() = "FileCreationException(fileName='$fileName')"
}
