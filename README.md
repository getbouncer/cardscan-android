# Deprecation Notice
Hello from the Stripe (formerly Bouncer) team!

We're excited to provide an update on the state and future of the [Card Scan OCR](https://github.com/stripe/stripe-android/tree/master/stripecardscan) product! As we continue to build into Stripe's ecosystem, we'll be supporting the mission to continuously improve the end customer experience in many of Stripe's core checkout products.

This SDK has been [migrated to Stripe](https://github.com/stripe/stripe-android/tree/master/stripecardscan) and is now free for use under the MIT license!

If you are not currently a Stripe user, and interested in learning more about improving checkout experience through Stripe, please let us know and we can connect you with the team.

If you are not currently a Stripe user, and want to continue using the existing SDK, you can do so free of charge. Starting January 1, 2022, we will no longer be charging for use of the existing Bouncer Card Scan OCR SDK. For product support on [Android](https://github.com/stripe/stripe-android/issues) and [iOS](https://github.com/stripe/stripe-ios/issues). For billing support, please email [bouncer-support@stripe.com](mailto:bouncer-support@stripe.com).

For the new product, please visit the [stripe github repository](https://github.com/stripe/stripe-android/tree/master/stripecardscan).

# Overview
This repository contains the legacy, deprecated open source code for [Bouncer](https://www.getbouncer.com) products (e.g. CardScan). See the individual sub modules for more information on each.

[CardScan](https://getbouncer.com/scan) is a relatively small library that provides fast and accurate payment card scanning.

CardScan is the foundation for CardVerify enterprise libraries, which validate the authenticity of payment cards as they are scanned.

![Lint](https://github.com/getbouncer/cardscan-android/workflows/Lint/badge.svg)
![Instrumentation Tests](https://github.com/getbouncer/cardscan-android/workflows/Instrumentation%20Tests/badge.svg)
![Unit Tests](https://github.com/getbouncer/cardscan-android/workflows/Unit%20Tests/badge.svg)
![Release](https://github.com/getbouncer/cardscan-android/workflows/Release/badge.svg)
[![GitHub release](https://img.shields.io/github/release/getbouncer/cardscan-android.svg?maxAge=60)](https://github.com/getbouncer/cardscan-android/releases)

![demo](docs/images/demo.gif)

## Contents
* [Requirements](#requirements)
* [Demo](#demo)
* [Integration](#integration)
* [Customizing](#customizing)
* [Developing](#developing)
* [Authors](#authors)
* [License](#license)

## Requirements
* Android API level 21 or higher
* AndroidX compatibility
* Kotlin coroutine compatibility

Note: Your app does not have to be written in kotlin to integrate this library, but must be able to depend on kotlin functionality.

## Demo
This repository contains a demonstration app for the CardScan product. To build and run the demo app, follow the instructions in the [demo documentation](https://docs.getbouncer.com/card-scan/android-integration-guide#demo).

## Integration
See the [integration documentation](https://docs.getbouncer.com/card-scan/android-integration-guide) in the Bouncer Docs.

### Provisioning an API key
CardScan requires a valid API key to run. To provision an API key, visit the [Bouncer API console](https://api.getbouncer.com/console).

### Name and expiration extraction support (BETA)
To test name and/or expiration extraction, please first provision an API key, then reach out to [bouncer-support@stripe.com](mailto:bouncer-support@stripe.com) with details about your use case and estimated volumes.

Before launching the CardScan flow, make sure to call the ```CardScanActivity.warmup()``` function with your API key and set ```initializeNameAndExpiryExtraction``` to ```true```

```kotlin
CardScanActivity.warmup(this, API_KEY, true)
```

## Customizing
CardScan is built to be customized to fit your UI.

### Basic modifications
To modify text, colors, or padding of the default UI, see the [customization](https://docs.getbouncer.com/card-scan/android-integration-guide/customization-guide) documentation.

### Extensive modifications
To modify arrangement or UI functionality, CardScan can be used as a library for your custom implementation. See the [example single-activity demo app](demo/src/main/java/com/getbouncer/cardscan/demo/SingleActivityDemo.java).

## Developing
See the [development docs](https://docs.getbouncer.com/card-scan/android-integration-guide/development-guide) for details on developing for CardScan.

## Authors
Adam Wushensky, Sam King, and Zain ul Abi Din

## License
This library is available under the MIT license. See the [LICENSE](LICENSE) file for the full license text.
