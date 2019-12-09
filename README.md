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
    implementation 'com.getbouncer:cardscan-base:1.0.5116'
    implementation 'com.getbouncer:cardscan:1.0.5116'
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

## Adding to Your App

When added to your app successfully, you should see the card numbers
being passed into your payment form. This is what it looks like using a standard Stripe mobile payment form:

![alt text](https://raw.githubusercontent.com/getbouncer/cardscan-android/master/card_scan.gif "Card Scan Gif")

## Authors

Sam King, Rui Guo, and Zain ul Abi Din

## License

CardScan is available under the BSD license. See the LICENSE file for more info.
