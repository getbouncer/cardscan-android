# Deprecation Notice
Hello from the Stripe (formerly Bouncer) team!

We're excited to provide an update on the state and future of the [Card Scan OCR](https://github.com/stripe/stripe-android/tree/master/stripecardscan) product! As we continue to build into Stripe's ecosystem, we'll be supporting the mission to continuously improve the end customer experience in many of Stripe's core checkout products.

This SDK has been [migrated to Stripe](https://github.com/stripe/stripe-android/tree/master/stripecardscan) and is now free for use under the MIT license!

If you are not currently a Stripe user, and interested in learning more about improving checkout experience through Stripe, please let us know and we can connect you with the team.

If you are not currently a Stripe user, and want to continue using the existing SDK, you can do so free of charge. Starting January 1, 2022, we will no longer be charging for use of the existing Bouncer Card Scan OCR SDK. For product support on [Android](https://github.com/stripe/stripe-android/issues) and [iOS](https://github.com/stripe/stripe-ios/issues). For billing support, please email [bouncer-support@stripe.com](mailto:bouncer-support@stripe.com).

For the new product, please visit the [stripe github repository](https://github.com/stripe/stripe-android/tree/master/stripecardscan).

# Overview
This repository provides the legacy, deprecated open source user interfaces for scanning cards. [CardScan](https://cardscan.io/) is a relatively small library that provides fast and accurate payment card scanning.

This library is the foundation for CardScan and CardVerify enterprise libraries, which validate the authenticity of payment cards as they are scanned.

![demo](../docs/images/demo.gif)

## Contents
* [Requirements](#requirements)
* [Demo](#demo)
* [Integration](#integration)
* [Using](#using)
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
An app demonstrating the basic capabilities of this library is available in [github](https://github.com/getbouncer/cardscan-demo-android).

## Integration
See the [integration documentation](https://docs.getbouncer.com/card-scan/android-integration-guide/android-development-guide) in the Bouncer Docs.

## Using
This library provides a framework for scanning objects (cards, identification, etc). The abstract `ScanActivity` class provides connections to the camera and a set of common scan functionality. By extending this class, you can build your own user interface for scanning.

See the [CardScan UI](https://github.com/getbouncer/cardscan-ui-android/blob/master/cardscan-ui/src/main/java/com/getbouncer/cardscan/ui/CardScanActivity.kt) and [Single Activity Demo](https://github.com/getbouncer/cardscan-demo-android/blob/master/demo/src/main/java/com/getbouncer/cardscan/demo/SingleActivityDemo.java) for examples.

## Customizing
This library is built to be customized to fit your UI.

### Basic modifications
To modify text, colors, or padding of the default UI, see the [customization](https://docs.getbouncer.com/card-scan/android-integration-guide/android-customization-guide) documentation.

### Extensive modifications
This library is designed to be extended by a custom UI. Create an activity that extends the `ScanActivity` to build your own user interface on top of the CardScan logic.

## Developing
See the [development docs](https://docs.getbouncer.com/card-scan/android-integration-guide/android-development-guide) for details on developing this library.

## Authors
Adam Wushensky, Sam King, and Zain ul Abi Din

## License
This library is available under the MIT license. See the [LICENSE](../LICENSE) file for the full license text.
