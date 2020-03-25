# CardScan

CardScan Android installation guide

## IMPORTANT NOTICE:

Our license will be changing in an upcoming release. Please view the [License](#license) section for more information.

## Contents

* [Requirements](#requirements)
* [Installation](#installation)
* [Using CardScan](#using-cardscan)
* [Authors](#authors)
* [License](#license)
* [Creating changelogs](#changelog)

## Requirements

* Android API level 19 or higher

## Installation

We publish our library in the jcenter repository, so for most gradle configurations you only need to add the dependency to your app's build.gradle file:

```gradle
dependencies {
    implementation 'com.getbouncer:cardscan-base:1.0.5150'
    implementation 'com.getbouncer:cardscan:1.0.5150'
}
```

## Using CardScan

To use CardScan, you create a `ScanActivity` intent, start it, and
get the results via the `onActivityResult` method:

```java
class Example {
    public void scanCard() {
        ScanActivity.start(this);
    }
    
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    
        if (ScanActivity.isScanResult(requestCode)) {
            if (resultCode == ScanActivity.RESULT_OK && data != null) {
                CreditCard scanResult = ScanActivity.creditCardFromResult(data);
    
            // at this point pass the info to your app's enter card flow
            // this is how we do it in our example app
                Intent intent = new Intent(this, EnterCard.class);
                intent.putExtra("card", scanResult);
                startActivity(intent);
            } else if (resultCode == ScanActivity.RESULT_CANCELED) {
                Log.d(TAG, "The user pressed the back button");
            }
        }
    }
}
```

## Configuring CardScan

Make sure that you get an [API key](https://api.getbouncer.com/console) and configure the library
when your application launches. If you are using the provided `ScanActivity`, Set the static
`apiKey` variable before invoking CardScan:

```kotlin
import com.getbouncer.cardscan.ScanActivity

class MyAppActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ScanActivity.apiKey = "YOUR_API_KEY_HERE"
    }
    
    fun launchCardScan() {
        ScanActivity.start(this)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (ScanActivity.isScanResult(requestCode)) {
            if (resultCode == ScanActivity.RESULT_OK && data != null) {
                val scanResult = ScanActivity.creditCardFromResult(data)
                // TODO: something with the scan result
            } else if (resultCode == ScanActivity.RESULT_CANCELED) {
                if (data.getBooleanExtra(ScanActivity.RESULT_FATAL_ERROR, false)) {
                    // TODO: handle a fatal error with cardscan
                } else {
                    // TODO: the user pressed the back button
                }
            }
        }
    }
}
```

`ScanActivity` will send the following statistics to the bouncer servers:
- `success`: boolean indicating if a card was successfully scanned
- `duration`: how long the cardscan activity was running
- `scans`: the number of camera images (frames) scanned
- `torch_on`: whether the flashlight was turned on during scanning
- `model`: the ML model used to detect card numbers
- `device_type`: The `Build.MANUFACTURER` and `Build.MODEL` values
- `sdk_version`: The version of the SDK
- `os`: the operating system version defined by `Build.VERSION.RELEASE`

This information helps bouncer understand the user experience so that we can continue to improve our
SDK.

## Adding to Your App

When added to your app successfully, you should see the card numbers
being passed into your payment form. This is what it looks like using a standard Stripe mobile payment form:

![alt text](https://raw.githubusercontent.com/getbouncer/cardscan-android/master/card_scan.gif "Card Scan Gif")

## Authors

Adam Wushensky, Sam King, and Zain ul Abi Din

## License

IMPORTANT: OUR LICENSE IS CHANGING.

Card Scan is currently available under the BSD license (See the [LICENSE](LICENSE) file for the full text). Version 1.0.5148 will be the last version of Card Scan that is available under the BSD-3 license. Future versions of the Card Scan library will remain open source, but will be under a new license (see the new [LICENSE](https://github.com/getbouncer/cardscan-android/blob/24ac9491f36e92241f37d8eebc5bd394a70bd4dd/LICENSE) file for the full text).

### Quick summary
In short, Card Scan will remain free forever for non-commercial applications, but use by commercial applications is limited to 90 days, after which time a licensing agreement is required. We’re also adding some legal liability protections.

After this period commercial applications need to convert to a licensing agreement to continue to use Card Scan.
* Details of licensing (pricing, etc) are [here](https://cardscan.io/pricing), or you can contact us at [license@getbouncer.com](mailto:license@getbouncer.com).

### More detailed summary
What's allowed under the new license:
* Free use for any app for 90 days (for demos, evaluations, hack-a-thons, etc.)
* Contributions (contributors must agree to the [CLA](Contributor%20License%20Agreement))
* Any modifications as needed to work in your app

What's not allowed under the new license:
* Commercial applications using the license for longer than 90 days without a license agreement
  * Using us now in a commercial app today? No worries! Just email [license@getbouncer.com](mailto:license@getbouncer.com) and we’ll get you set up
* Redistribution under a different license
* Removing attribution
* Modifying logos
* Indemnification: using this free software is "at your own risk", so you can’t sue Bouncer Technologies, Inc. for problems caused by this library

### Questions? Concerns?
Please email us at [license@getbouncer.com](mailto:license@getbouncer.com) or ask us on [slack](https://getbouncer.slack.com).

## Changelog

```bash
# checkout https://github.com/github-changelog-generator/github-changelog-generator for installation instructions
# put your github token in a file called github_token in the base directory
# run:
github_changelog_generator -u getbouncer -p cardscan-android -t `cat github_token`
```
