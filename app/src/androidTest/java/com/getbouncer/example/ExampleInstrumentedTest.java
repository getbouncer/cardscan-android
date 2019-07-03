package com.getbouncer.example;

import android.support.test.espresso.IdlingPolicies;
import android.support.test.espresso.IdlingPolicy;
import android.support.test.espresso.IdlingRegistry;
import android.support.test.espresso.idling.CountingIdlingResource;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.rule.ActivityTestRule;


import com.getbouncer.cardscan.ScanActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    private CountingIdlingResource countingIdlingResource;

    @Rule
    public ActivityTestRule<LaunchActivity> activityActivityTestRule =
            new ActivityTestRule<>(LaunchActivity.class);

    @Before
    public void registerIdleResource() {
        countingIdlingResource = ScanActivity.getScanningIdleResource();
        IdlingRegistry.getInstance().register(countingIdlingResource);
        IdlingPolicies.setMasterPolicyTimeout(3, TimeUnit.MINUTES);
        IdlingPolicies.setIdlingResourceTimeout(3, TimeUnit.MINUTES);
    }

    @After
    public void unregisterIdleResource() {
        IdlingRegistry.getInstance().unregister(countingIdlingResource);
    }

    @Test
    public void runVideo() {

        onView(withId(R.id.scan_video))
                .perform(click());

        onView(withId(R.id.cardNumberForTesting))
                .check(matches(withText("4557095462268383")));
    }
}
