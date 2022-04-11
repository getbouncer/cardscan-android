# Overview
This repository contains the camera framework to allow scanning cards. [CardScan](https://cardscan.io/) is a relatively small library that provides fast and accurate payment card scanning.

Note this library does not contain any user interfaces. Another library, [CardScan UI](https://github.com/getbouncer/cardscan-ui-android) builds upon this one any adds simple user interfaces. 

![demo](../docs/images/demo.gif)

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

Note: Your app does not have to be written in kotlin to integrate scan-camera, but must be able to depend on kotlin functionality.

## Demo
An app demonstrating the basic capabilities of CardScan is available in [github](https://github.com/getbouncer/cardscan-demo-android).

## Integration
See the [integration documentation](https://docs.getbouncer.com/card-scan/android-integration-guide/android-development-guide) in the Bouncer Docs.

## Using
This library is designed to be used with [CardScan UI](https://github.com/getbouncer/cardscan-ui-android), which will provide user interfaces for scanning payment cards. However, it can be used independently.

For an overview of the architecture and design of the scan framework, see the [architecture documentation](https://docs.getbouncer.com/card-scan/android-integration-guide/android-architecture-overview).

### Getting images from the camera
See the [example code](https://docs.getbouncer.com/card-scan/android-integration-guide/android-architecture-overview#example) in the Android architecture documentation.

## Developing
See the [development docs](https://docs.getbouncer.com/card-scan/android-integration-guide/android-development-guide) for details on developing this library.

## Authors
Adam Wushensky, Sam King, and Zain ul Abi Din

## License
This library is available under paid and free licenses. See the [LICENSE](../LICENSE) file for the full license text.

### Quick summary
In short, this library will remain free forever for non-commercial applications, but use by commercial applications is limited to 90 days, after which time a licensing agreement is required. We're also adding some legal liability protections.

After this period commercial applications need to convert to a licensing agreement to continue to use this library.
* Details of licensing (pricing, etc) are available at [https://cardscan.io/pricing](https://cardscan.io/pricing), or you can contact us at [bouncer-support@stripe.com](mailto:bouncer-support@stripe.com).

### More detailed summary
What's allowed under the license:
* Free use for any app for 90 days (for demos, evaluations, hackathons, etc).
* Contributions (contributors must agree to the [Contributor License Agreement](../Contributor%20License%20Agreement))
* Any modifications as needed to work in your app

What's not allowed under the license:
* Commercial applications using the license for longer than 90 days without a license agreement. 
* Using us now in a commercial app today? No worries! Just email [bouncer-support@stripe.com](mailto:bouncer-support@stripe.com) and we’ll get you set up.
* Redistribution under a different license
* Removing attribution
* Modifying logos
* Indemnification: using this free software is ‘at your own risk’, so you can’t sue Bouncer Technologies, Inc. for problems caused by this library

Questions? Concerns? Please email us at [bouncer-support@stripe.com](mailto:bouncer-support@stripe.com) or ask us on [slack](https://getbouncer.slack.com).
