# CardScan

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
    implementation 'com.getbouncer:cardscan:2.0.0001'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.3'
}
```

## Using cardscan

CardScan provides a user interface through which payment cards can be scanned.

```kotlin
class LaunchActivity : AppCompatActivity, CardScanActivityResultHandler {

    private const val API_KEY = "qOJ_fF-WLDMbG05iBq5wvwiTNTmM2qIn";

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        // Because this activity displays card numbers, disallow screenshots.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        findViewById(R.id.scanCardButton).setOnClickListener { _ ->
            CardScanActivity.start(
                activity = LaunchActivity.this,
                apiKey = API_KEY,
                enableEnterCardManually = true
            )
        }

        CardScanActivity.warmUp(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (CardScanActivity.isScanResult(requestCode)) {
            CardScanActivity.parseScanResult(resultCode, data, this)
        }
    }

    override fun cardScanned(scanId: String?, scanResult: ScanResult) {
        // a payment card was scanned successfully
    }

    override fun enterManually(scanId: String?) {
        // the user wants to enter a card manually
    }

    override fun userCanceled(scanId: String?) {
        // the user canceled the scan
    }

    override fun cameraError(scanId: String?) {
        // scan was canceled due to a camera error
    }

    override fun analyzerFailure(scanId: String?) {
        // scan was canceled due to a failure to analyze camera images
    }

    override fun canceledUnknown(scanId: String?) {
        // scan was canceled for an unknown reason
    }
}
```

## Customizing CardScan

CardScan is built to be customized to fit your UI.

### Basic modifications

To modify text, colors, or padding of the default UI, see the [customization](https://github.com/getbouncer/cardscan-ui-android/blob/master/docs/customize.md) documentation.

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
