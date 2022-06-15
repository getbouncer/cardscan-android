package com.getbouncer.scan.framework

import com.getbouncer.scan.framework.exception.InvalidBouncerApiKeyException
import com.getbouncer.scan.framework.time.Duration
import com.getbouncer.scan.framework.time.Rate
import com.getbouncer.scan.framework.time.seconds
import kotlinx.serialization.json.Json

private const val REQUIRED_API_KEY_LENGTH = 32

@Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
object Config {

    /**
     * If set to true, turns on debug information.
     */
    @JvmStatic
    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    var isDebug: Boolean = false

    /**
     * A log tag used by this library.
     */
    @JvmStatic
    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    var logTag: String = "Bouncer"

    /**
     * The API key to interface with Bouncer servers
     */
    @JvmStatic
    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    var apiKey: String? = null
        set(value) {
            if (value != null && value.length != REQUIRED_API_KEY_LENGTH) {
                throw InvalidBouncerApiKeyException
            }
            field = value
        }

    /**
     * The JSON configuration to use throughout this SDK.
     */
    @JvmStatic
    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    var json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Whether or not to track stats
     */
    @JvmStatic
    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    val trackStats: Boolean = true

    /**
     * Whether or not to upload stats
     */
    @JvmStatic
    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    var uploadStats: Boolean = true

    /**
     * Whether or not to display the Bouncer logo
     */
    @JvmStatic
    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    var displayLogo: Boolean = true

    /**
     * Whether or not to display the result of the scan to the user
     */
    @JvmStatic
    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    var displayScanResult: Boolean = true

    /**
     * If set to true, opt-in to beta versions of the ML models.
     */
    @JvmStatic
    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    var betaModelOptIn: Boolean = false

    /**
     * The frame rate of a device that is considered slow will be below this rate.
     */
    @JvmStatic
    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    var slowDeviceFrameRate = Rate(2, 1.seconds)

    /**
     * Allow downloading ML models.
     */
    @JvmStatic
    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    var downloadModels = true
}

@Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
object NetworkConfig {

    /**
     * The base URL where all network requests will be sent
     */
    @JvmStatic
    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    var baseUrl = "https://api.getbouncer.com"

    /**
     * Whether or not to compress network request bodies.
     */
    @JvmStatic
    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    var useCompression: Boolean = false

    /**
     * The total number of times to try making a network request.
     */
    @JvmStatic
    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    var retryTotalAttempts: Int = 3

    /**
     * The delay between network request retries.
     */
    @JvmStatic
    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    var retryDelay: Duration = 5.seconds

    /**
     * Status codes that should be retried from bouncer servers.
     */
    @JvmStatic
    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    var retryStatusCodes: Iterable<Int> = 500..599
}
