package com.trikset.gamepad2

import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
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
    openRobotSettings()
    editPreference("Robot IP address…", "localhost")
    editPreference("Robot TCP port…", "12345")
    editPreference("URI for video stream to play…", "http://localhost:8080/?action=stream")
    editPreference("Keep-alive timeout, ms…", "3000")

    // Out of the robot settings back to the gamepad (single-level screen since C17's settings
    // split — the old Advanced sub-screen needed a second back).
    Espresso.pressBack()

    val preferences = PreferenceManager.getDefaultSharedPreferences(mActivityTestRule.activity)
    assertEquals("localhost", preferences.getString(SettingsFragment.SK_HOST_ADDRESS, ""))
    assertEquals("12345", preferences.getString(SettingsFragment.SK_HOST_PORT, ""))
    assertEquals("3000", preferences.getString(SettingsFragment.SK_KEEPALIVE, ""))
    assertEquals(
        "http://localhost:8080/?action=stream",
        preferences.getString(SettingsFragment.SK_VIDEO_URI, ""),
    )
  }

  @Test
  fun keepAliveTimeoutLowerThanMinimalShouldNotBeStored() {
    val initialKeepaliveTimeout = mActivityTestRule.activity.senderService.keepaliveTimeout

    openRobotSettings()
    editPreference("Keep-alive timeout, ms…", "500") // keepalive below MINIMAL_KEEPALIVE

    Espresso.pressBack()

    assertEquals(
        initialKeepaliveTimeout,
        mActivityTestRule.activity.senderService.keepaliveTimeout,
    )
  }

  /** From the gamepad: open the robot/target settings (top-left IP chip). */
  private fun openRobotSettings() {
    onView(allOf(withId(R.id.targetChip), isDisplayed())).perform(click())
  }

  /** Scrolls to and clicks the preference row titled [title]. */
  private fun clickPreference(title: String) {
    onView(withId(androidx.preference.R.id.recycler_view))
        .perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(title)))
        )
    onView(allOf(withText(title), isDisplayed())).perform(click())
  }

  /** Opens the preference titled [title], sets its edit text to [value] and confirms. */
  private fun editPreference(title: String, value: String) {
    clickPreference(title)

    onView(allOf(withId(android.R.id.edit), isDisplayed()))
        .perform(replaceText(value), closeSoftKeyboard())

    onView(allOf(withId(android.R.id.button1), withText("OK"), isDisplayed()))
        .perform(scrollTo(), click())
  }
}
