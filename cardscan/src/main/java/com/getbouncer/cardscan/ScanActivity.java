package com.getbouncer.cardscan;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.getbouncer.cardscan.base.ModelFactory;
import com.getbouncer.cardscan.base.ScanActivityImpl;
import com.getbouncer.cardscan.base.ScanBaseActivity;


/**
 * The ScanActivity class provides the main interface to the scanning functionality. To use this
 * activity, call the {@link ScanActivity#start(Activity)} method and override
 * onActivityResult in your own activity to get the result of the scan.
 */
public class ScanActivity {
    private static final String TAG = "ScanActivity";

    private static final int REQUEST_CODE = 51234;
    public static final String SCAN_RESULT = ScanActivityImpl.SCAN_RESULT;
    public static final int RESULT_CANCELED = ScanActivityImpl.RESULT_CANCELED;
    public static final int RESULT_OK = ScanActivityImpl.RESULT_OK;

    private static final ModelFactory modelFactory = new ResourceModelFactory();

    /**
     * Starts a ScanActivityImpl activity, using {@param activity} as a parent.
     *
     * @param activity the parent activity that is waiting for the result of the ScanActivity
     */
    public static void start(@NonNull Activity activity) {
        ScanBaseActivity.warmUp(activity.getApplicationContext(), modelFactory);
        activity.startActivityForResult(new Intent(activity, ScanActivityImpl.class), REQUEST_CODE);
    }

    /**
     * Starts a scan activity and customizes the text that it displays.
     *
     * @param activity the parent activity that is waiting for the result of the ScanActivity
     * @param scanCardText the large text above the card rectangle
     * @param positionCardText the small text below the card rectangle
     */
    public static void start(@NonNull Activity activity, String scanCardText,
                             String positionCardText) {

        ScanBaseActivity.warmUp(activity.getApplicationContext(), modelFactory);
        Intent intent = new Intent(activity, ScanActivityImpl.class);
        intent.putExtra(ScanActivityImpl.SCAN_CARD_TEXT, scanCardText);
        intent.putExtra(ScanActivityImpl.POSITION_CARD_TEXT, positionCardText);
        activity.startActivityForResult(intent, REQUEST_CODE);
    }

    /**
     * Initializes the machine learning models and GPU hardware for faster scan performance.
     *
     * This optional static method initializes the machine learning models and GPU hardware in a
     * background thread so that when the ScanActivity starts it can complete its first scan
     * quickly. App builders can choose to not call this method and they can call it multiple
     * times safely.
     *
     * This method is thread safe.
     *
     * @param activity the activity that invokes this method, which the library uses to get
     *                 an application context.
     */
    public static void warmUp(@NonNull Activity activity) {
        ScanBaseActivity.warmUp(activity.getApplicationContext(), modelFactory);
    }

    /**
     * Starts the scan activity and turns on a small debugging window in the bottom left.
     *
     * This debugging activity helps designers see some of the machine learning model's internals
     * by showing boxes around digits and expiry dates that it detects.
     *
     * @param activity the parent activity that is waiting for the result of the ScanActivity
     */
    public static void startDebug(@NonNull Activity activity) {
        ScanBaseActivity.warmUp(activity.getApplicationContext(), modelFactory);
        Intent intent = new Intent(activity, ScanActivityImpl.class);
        intent.putExtra("debug", true);
        activity.startActivityForResult(intent, REQUEST_CODE);
    }

    /**
     * A helper method to use within your onActivityResult method to check if the result is from our
     * scan activity.
     *
     * @param requestCode the requestCode passed into the onActivityResult method
     * @return true if the requestCode matches the requestCode we use for ScanActivity instances
     */
    public static boolean isScanResult(int requestCode) {
        return requestCode == REQUEST_CODE;
    }
}
