package com.trikset.gamepad

import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import androidx.preference.PreferenceManager
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@LargeTest
@RunWith(JUnit4::class)
class SettingsTests {

  @get:Rule
  val mActivityTestRule =
      object : FocusAwareActivityTestRule<MainActivity>(MainActivity::class.java) {
        override fun beforeActivityLaunched() {
          val preferences =
              PreferenceManager.getDefaultSharedPreferences(
                  InstrumentationRegistry.getInstrumentation().targetContext
              )
          preferences.edit().clear().commit()
        }
      }

  @Test
  fun settingsShouldWorkCorrectly() {
    openSettings()

    editPreference(0, "localhost") // host address
    editPreference(1, "12345") // port
    editPreference(2, "50") // pads opacity
    editPreference(3, "5") // wheel step
    editPreference(4, "3000") // keepalive
    editPreference(5, "http://localhost:8080/?action=stream") // video URI

    Espresso.pressBack()

    val preferences = PreferenceManager.getDefaultSharedPreferences(mActivityTestRule.activity)
    assertEquals("localhost", preferences.getString(SettingsFragment.SK_HOST_ADDRESS, ""))
    assertEquals("12345", preferences.getString(SettingsFragment.SK_HOST_PORT, ""))
    assertEquals("50", preferences.getString(SettingsFragment.SK_SHOW_PADS, ""))
    assertEquals("5", preferences.getString(SettingsFragment.SK_WHEEL_STEP, ""))
    assertEquals("3000", preferences.getString(SettingsFragment.SK_KEEPALIVE, ""))
  }

  @Test
  fun keepAliveTimeoutLowerThanMinimalShouldNotBeStored() {
    val initialKeepaliveTimeout =
        mActivityTestRule.activity.getSenderService().getKeepaliveTimeout()

    openSettings()
    editPreference(4, "500") // keepalive below MINIMAL_KEEPALIVE

    Espresso.pressBack()

    assertEquals(
        initialKeepaliveTimeout,
        mActivityTestRule.activity.getSenderService().getKeepaliveTimeout(),
    )
  }

  /** From the gamepad: reveal + tap the action-bar Settings item. */
  private fun openSettings() {
    // The gear button is unique, so no child-index matcher is needed; index-based
    // locators break on any layout reorder (MainActivity's controlsOverlay.bringToFront()
    // and new children moving btnSettings from rendered child #1).
    onView(allOf(withId(R.id.btnSettings), isDisplayed())).perform(click())

    onView(
            allOf(
                withId(R.id.settings),
                withText("Settings"),
                childAtPosition(childAtPosition(withId(androidx.appcompat.R.id.action_bar), 1), 1),
                isDisplayed(),
            )
        )
        .perform(click())
  }

  /** Opens the preference dialog at [row], sets its edit text to [value] and confirms. */
  private fun editPreference(row: Int, value: String) {
    onView(
            allOf(
                childAtPosition(
                    allOf(
                        withId(androidx.preference.R.id.recycler_view),
                        childAtPosition(withId(android.R.id.list_container), 0),
                    ),
                    row,
                ),
                isDisplayed(),
            )
        )
        .perform(click())

    onView(allOf(withId(android.R.id.edit), isDisplayed()))
        .perform(scrollTo(), replaceText(value), closeSoftKeyboard())

    onView(
            allOf(
                withId(android.R.id.button1),
                withText("OK"),
                childAtPosition(childAtPosition(withId(androidx.appcompat.R.id.buttonPanel), 0), 3),
            )
        )
        .perform(scrollTo(), click())
  }

  private fun childAtPosition(parentMatcher: Matcher<View>, position: Int): Matcher<View> {
    return object : TypeSafeMatcher<View>() {
      override fun describeTo(description: Description) {
        description.appendText("Child at position $position in parent ")
        parentMatcher.describeTo(description)
      }

      override fun matchesSafely(view: View): Boolean {
        val parent: ViewParent = view.parent
        return parent is ViewGroup &&
            parentMatcher.matches(parent) &&
            view == parent.getChildAt(position)
      }
    }
  }
}
