# CardScan

CardScan Android installation guide

## Contents

* [Requirements](#requirements)
* [Installation](#installation)
* [Using CardScan](#using-cardscan)
* [Authors](#authors)
* [License](#license)

## Requirements

* Android API level 19 or higher

## Installation

We publish our library in the jcenter repository, so for most gradle configurations you only need to add the dependency to your app's build.gradle file:

```gradle
dependencies {
    implementation 'com.getbouncer:cardscan-base:1.0.5131'
    implementation 'com.getbouncer:cardscan:1.0.5131'
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
            } else if (resultCode == ScanActivity.RESULT_CANCELLED) {
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

Sam King, Rui Guo, and Zain ul Abi Din

## License

CardScan is available under the BSD license. See the LICENSE file for more info.
