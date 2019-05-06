# CardScan

CardScan Android installation guide

## Contents

* [Requirements](#requirements)
* [Installation](#installation)
* [Using CardScan](#using-cardscan)
* [Authors](#authors)
* [License](#license)

## Requirements

* Android API level 15 or higher
* We're not quite ready for production use yet, give us about a week to finish cleaning things up.

## Installation

Download the cardscan.aar file and then import it into your project using `File -> New -> New Module -> Import .jar/.aar`

Then, add a dependency to your app's build.gradle file:

```gradle
dependencies {
    implementation project(':cardscan')
}
```

and in your settings.gradle file

```gradle
include 'app', ':cardscan'
```

## Using CardScan

To use CardScan, you create a `ScanActivity` intent, start it, and
get the results via the `onActivityResult` method:

```java
void scanCard() {
    Intent scanIntent = new Intent(this, ScanActivity.class);
    // the number '1234' can be anything, we just used the lock combo for my luggage
    startActivityForResult(scanIntent, 1234);
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
   super.onActivityResult(requestCode, resultCode, data);

   if (requestCode == 1234) {
      if ( resultCode == ScanActivity.RESULT_OK && data != null &&
      	 data.hasExtra(ScanActivity.SCAN_RESULT)) {

         String resultString = data.getStringExtra(ScanActivity.SCAN_RESULT);
         CreditCard scanResult = new CreditCard(resultString);

	 // at this point pass the info to your app's enter card flow
	 // this is how we do it in our example app
         Intent intent = new Intent(this, EnterCard.class);
         intent.putExtra("number", scanResult.number);

         if (scanResult.expiryMonth != null) {
            intent.putExtra("expiryMonth", Integer.parseInt(scanResult.expiryMonth));
	    intent.putExtra("expiryYear", Integer.parseInt(scanResult.expiryYear));
	 }

         startActivity(intent);
      }
   }
}
```

## Adding to Your App

When added to your app successfully, you should see the card numbers
being passed into your payment form. This is what it looks like using a standard Stripe mobile payment form:

![alt text](https://raw.githubusercontent.com/getbouncer/cardscan-ios/master/card_scan.gif "Card Scan Gif")

## Authors

Sam King

## License

CardScan is available under the BSD license. See the LICENSE file for more info.
