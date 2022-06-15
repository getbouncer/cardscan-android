# Deprecation Notice
Hello from the Stripe (formerly Bouncer) team!

We're excited to provide an update on the state and future of the [Card Scan OCR](https://github.com/stripe/stripe-android/tree/master/stripecardscan) product! As we continue to build into Stripe's ecosystem, we'll be supporting the mission to continuously improve the end customer experience in many of Stripe's core checkout products.

This SDK has been [migrated to Stripe](https://github.com/stripe/stripe-android/tree/master/stripecardscan) and is now free for use under the MIT license!

If you are not currently a Stripe user, and interested in learning more about improving checkout experience through Stripe, please let us know and we can connect you with the team.

If you are not currently a Stripe user, and want to continue using the existing SDK, you can do so free of charge. Starting January 1, 2022, we will no longer be charging for use of the existing Bouncer Card Scan OCR SDK. For product support on [Android](https://github.com/stripe/stripe-android/issues) and [iOS](https://github.com/stripe/stripe-ios/issues). For billing support, please email [bouncer-support@stripe.com](mailto:bouncer-support@stripe.com).

For the new product, please visit the [stripe github repository](https://github.com/stripe/stripe-android/tree/master/stripecardscan).

# Scan Framework
This repository contains the legacy, deprecated open source framework needed to quickly and accurately scan items (payment cards, IDs, etc.). [CardScan](https://cardscan.io/) is a relatively small library that provides fast and accurate payment card scanning.

Note this library does not contain any user interfaces or ML models. Other libraries [Scan Payment](https://github.com/getbouncer/scan-payment-android) and [Scan UI](https://github.com/getbouncer/scan-ui-android) build upon this and add ML models and simple user interfaces. 

Scan Framework serves as the foundation for CardScan and CardVerify enterprise libraries, which validate the authenticity of payment cards as they are scanned.

![CardScan](../docs/images/demo.gif)

## Contents
* [Requirements](#requirements)
* [Demo](#demo)
* [Integration](#integration)
* [Using](#using)
* [Developing](#developing)
* [Authors](#authors)
* [License](#license)

## Requirements
* Android API level 21 or higher
* Kotlin coroutine compatibility

Note: Your app does not have to be written in kotlin to integrate this library, but must be able to depend on kotlin functionality.

## Demo
An app demonstrating the basic capabilities of CardScan is available in [github](https://github.com/getbouncer/cardscan-demo-android).

## Integration
See the [integration documentation](https://docs.getbouncer.com/card-scan/android-integration-guide/android-development-guide) in the Bouncer Docs.

## Using
This library is designed to be used with [scan-payment](https://github.com/getbouncer/scan-payment-android) and [scan-ui](https://github.com/getbouncer/scan-ui-android), which will provide user interfaces for scanning payment cards. However, it can be used independently.

For an overview of the architecture and design of the scan framework, see the [architecture documentation](https://docs.getbouncer.com/card-scan/android-integration-guide/android-architecture-overview).

### Processing unlimited data
Let's use an example where we process an unknown number of `MyData` values into `MyAnalyzerOutput` values, and then aggregate them into a single `MyAnalyzerOutput`.

First, create our input and output data types:
```kotlin
data class MyData(data: String)

data class MyAnalyzerOutput(output: Int)
```

Next, create an analyzer to process inputs into outputs, and a factory to create new instances of the analyzer.
```kotlin
class MyAnalyzer : Analyzer<MyData, Unit, MyAnalyzerOutput> {
    override suspend fun analyze(data: MyData, state: Unit): MyAnalyzerOutput = MyAnalyzerOutput(data.data.length)

    override val name = "my_analyzer"
}

class MyAnalyzerFactory : AnalyzerFactory<MyAnalyzer> {
    override suspend fun newInstance(): Analyzer? = MyAnalyzer()
}
```

Then, create a result handler to aggregate multiple outputs into one, and indicate when processing should cease.
```kotlin
class MyResultHandler(listener: ResultHanlder<MyData, Unit, MyAnalyzerOutput>) :
    StateUpdatingResultHandler<MyData, LoopState<Unit>, MyAnalyzerOutput>() {

    private var resultsReceived = 0
    private var totalResult = 0
    
    override suspend fun onResult(
        result: MyAnalyzerOutput,
        state: LoopState<Unit>,
        data: MyData,
        updateState: (LoopState<Unit>) -> Unit
    ) {
        resultsReceived++
        if (resultsReceived > 10) {
            updateState(state.copy(finished = true))
            listener.onResult(MyAnalyzerOutput(totalResult), state.state, data)
        } else {
            totalResult += result.output
        }
    }
}
```

Finally, tie it all together with a class that receives data and does something with the result.
```kotlin
class MyDataProcessor : CoroutineScope, ResultHandler<MyData, Unit, MyAnalyzerOutput> {

    private val analyzerPool = AnalyzerPool.Factory(MyAnalyzerFactory(), 4)
    private val resultHandler = MyResultHandler(this)
    private val loop by lazy {
        ProcessBoundAnalyzerLoop(analyzerPool, resultHandler, Unit, "my_loop", { true }, { true })
    }
    
    fun subscribeTo(flow: Flow<MyData>) {
        loop.subscribeTo(flow, this)
    }
    
    fun onResult(result: MyAnalyzerOutput, state: Unit, data: MyData) {
        // Display something
    }
}
```

### Processing a known amount of data
In this example, we need to process a known amount of data as quickly as possible using multiple analyzers.

First, create our input and output data types:
```kotlin
data class MyData(data: String)

data class MyAnalyzerOutput(output: Int)
```

Next, create an analyzer to process inputs into outputs, and a factory to create new instances of the analyzer.
```kotlin
class MyAnalyzer : Analyzer<MyData, Unit, MyAnalyzerOutput> {
    override suspend fun analyze(data: MyData, state: Unit): MyAnalyzerOutput = data.data.length
}

class MyAnalyzerFactory : AnalyzerFactory<MyAnalyzer> {
    override fun newInstance(): Analyzer? = MyAnalyzer()
}
```

Finally, tie it all together with a class that processes the data and does something with the results.
```kotlin
class MyDataProcessor : CoroutineScope, TerminatingResultHandler<MyData, Unit, MyAnalyzerOutput> {

    override val coroutineContext: CoroutineContext = Dispatchers.Default

    private val analyzerFactory = MyAnalyzerFactory()
    private val resultHandler = MyResultHandler(this)
    private val analyzerPool = AnalyzerPool(analyzerFactory)

    private val loop: AnalyzerLoop<MyData, Unit, MyAnalyzerOutput> by lazy {
        FiniteAnalyzerLoop(
            analyzerPool = analyzerPool,
            resultHandler = this,
            initialState = Unit,
            name = "loop_name",
            onAnalyzerFailure = {
                launch(Dispatchers.Main) { analyzerFailure(it) }
                true // terminate the loop on any analyzer failures
            },
            onResultFailure = {
                launch(Dispatchers.Main) { analyzerFailure(it) }
                true // terminate the loop on any result handler failures
            },
            timeLimit = 10.seconds
        )
    }
    
    fun processData(data: List<MyData>) {
        loop.process(data, this)
    }
    
    override fun onResult(result: MyAnalyzerOutput, state: Unit, data: MyData) {
        // A single frame has been processed
    }

    override fun onAllDataProcessed() {
        // Notify that all data has been processed
    }

    override fun onTerminatedEarly() {
        // Notify that not all data was processed
    }

    private fun analyzerFailure(cause: Throwable?) {
        // Notify that the data processing failed
    }
}
```

## Developing
See the [development docs](https://docs.getbouncer.com/card-scan/android-integration-guide/android-development-guide) for details on developing this library.

## Authors
Adam Wushensky, Sam King, and Zain ul Abi Din

## License
This library is available under the MIT license. See the [LICENSE](../LICENSE) file for the full license text.
