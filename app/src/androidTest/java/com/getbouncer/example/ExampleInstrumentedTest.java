package com.getbouncer.example;

import androidx.test.espresso.IdlingPolicies;
import androidx.test.espresso.IdlingPolicy;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.idling.CountingIdlingResource;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;


import com.getbouncer.cardscan.ScanActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

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
