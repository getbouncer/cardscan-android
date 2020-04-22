# CardScan UI

This repository provides user interfaces for the CardScan library. [CardScan](https://cardscan.io/) is a relatively small library (1.9 MB) that provides fast and accurate payment card scanning.

CardScan is the foundation for CardVerify enterprise libraries, which validate the authenticity of payment cards as they are scanned.

![CardScan](docs/images/cardscan_demo.gif)

## Contents

* [Requirements](#requirements)
* [Demo](#demo)
* [Installation](#installation)
* [Using CardScan](#using-cardscan-ui)
* [Customizing](#customizing-cardscan)
* [Developing](#developing-cardscan)
* [Authors](#authors)
* [License](#license)

## Requirements

* Android API level 21 or higher
* AndroidX compatibility
* Kotlin coroutine compatibility

Note: Your app does not have to be written in kotlin to integrate cardscan, but must be able to depend on kotlin functionality.

## Demo

An app demonstrating the basic capabilities of CardScan is available in [github](https://github.com/getbouncer/cardscan-demo-android).

## Installation

The CardScan libraries are published in the [jcenter](https://jcenter.bintray.com/com/getbouncer/) repository, so for most gradle configurations you only need to add the dependencies to your app's `build.gradle` file:

```gradle
dependencies {
    implementation 'com.getbouncer:cardscan-ui:2.0.0001'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.3'
}
```

## Using cardscan-ui

CardScan provides a user interface through which payment cards can be scanned.

```kotlin
class MyActivity : Activity {

    /**
     * This method should be called as soon in the application as possible to give time for
     * the SDK to warm up ML model processing.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        CardScanActivity.warmUp(this)
    }

    /**
     * This method launches the CardScan SDK.
     */
    private fun onScanCardClicked() {
        CardScanActivity.start(
            activity = this,
            apiKey = "<YOUR_API_KEY_HERE>",
            enableEnterCardManually = true
        )
    }
    
    /**
     * This method receives the result from the CardScan SDK.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (CardScanActivity.isScanResult(requestCode)) {
            if (resultCode == Activity.RESULT_OK) {
                handleCardScanSuccess(CardScanActivity.getScannedCard(data))
            } else if (resultCode == Activity.RESULT_CANCELED) {
                handleCardScanCanceled(data.getIntExtra(RESULT_CANCELED_REASON, -1))
            }
        }
    }
    
    private fun handleCardScanSuccess(result: ScanResult) {
        // do something with the scanned credit card
    }
    
    private fun handleCardScanCanceled(reason: Int) = when (reason) {
        CANCELED_REASON_USER -> handleUserCanceled()
        CANCELED_REASON_ENTER_MANUALLY -> handleEnterCardManually()
        CANCELED_REASON_CAMERA_ERROR -> handleCameraError()
        else -> handleCardScanFailed()
    }
    
    private fun handleUserCanceled() {
        // do something when the user cancels the card scan
    }
    
    private fun handleEnterCardManually() {
        // do something when the user wants to enter a card manually
    }
    
    private fun handleCameraError() {
        // do something when camera had an error
    }
    
    private fun handleCardScanFailed() {
        // do something when scanning a card failed
    }
}
```

## Customizing CardScan

CardScan is built to be customized to fit your UI.

### Basic modifications

To modify text, colors, or padding of the default UI, see the [customization](docs/customize.md) documentation.

### Extensive modifications

To modify arrangement or UI functionality, CardScan can be used as a library for your custom implementation. See examples in the [cardscan-base-android](https://github.com/getbouncer/cardscan-base-android) repository.

## Developing CardScan

See the [development docs](docs/develop.md) for details on developing for CardScan.

## Authors

Adam Wushensky, Sam King, and Zain ul Abi Din

## License

CardScan is available under paid and free licenses. See the [LICENSE](LICENSE) file for the full license text.

In short, CardScan is free for use forever for non-commercial applications, and free for use for 90 days for commercial applications, after which use must be discontinued or an agreement made with Bouncer Technologies, Inc.

Allowed:
* Contributions (contributors must agree to the [CLA](Contributor%20License%20Agreement))
* Modifications as needed to work in your app
* Inclusion and distributed in your app

Not Allowed:
* Redistribution under a different license
* Removing attribution
* Modifying logos
* Use for more than 90 days in a commercial application
* Sue Bouncer Technologies, Inc. for problems caused by this library
