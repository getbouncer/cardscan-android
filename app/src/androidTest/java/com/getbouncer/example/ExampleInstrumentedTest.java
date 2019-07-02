package com.getbouncer.example;

import android.support.test.runner.AndroidJUnit4;
import android.support.test.rule.ActivityTestRule;


import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Rule
    public ActivityTestRule<LaunchActivity> activityActivityTestRule =
            new ActivityTestRule<>(LaunchActivity.class);

    private void waitForDisplayed(int id, long timeInMs) {
        long now = System.currentTimeMillis();

        while((System.currentTimeMillis() - now) < timeInMs) {
            try {
                onView(withId(id))
                        .check(matches(isDisplayed()));
            } catch (Exception e) {

            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    @Test
    public void runVideo() {

        onView(withId(R.id.scan_video))
                .perform(click());

        waitForDisplayed(R.id.cardNumberForTesting, 60000);

        onView(withId(R.id.cardNumberForTesting))
                .check(matches(withText("4557095462268383")));
    }
}
