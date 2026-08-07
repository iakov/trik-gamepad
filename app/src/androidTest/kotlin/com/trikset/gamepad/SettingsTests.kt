package com.trikset.gamepad

import android.preference.PreferenceManager
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.`is` as hamcrestIs
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
    // The sleeps below match the app's execution delay. The recommended way
    // to handle such scenarios is to use Espresso idling resources:
    // https://google.github.io/android-testing-support-library/docs/espresso/idling-resource/index.html
    Thread.sleep(7000)

    onView(
            allOf(
                withId(R.id.btnSettings),
                childAtPosition(
                    allOf(withId(R.id.main), childAtPosition(withId(android.R.id.content), 0)),
                    1,
                ),
                isDisplayed(),
            )
        )
        .perform(click())
    Thread.sleep(300)

    onView(
            allOf(
                withId(R.id.settings),
                withText("Settings"),
                childAtPosition(childAtPosition(withId(androidx.appcompat.R.id.action_bar), 1), 1),
                isDisplayed(),
            )
        )
        .perform(click())
    Thread.sleep(3000)

    onView(
            allOf(
                childAtPosition(
                    allOf(
                        withId(androidx.preference.R.id.recycler_view),
                        childAtPosition(withId(android.R.id.list_container), 0),
                    ),
                    0,
                ),
                isDisplayed(),
            )
        )
        .perform(click())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                withText("192.168.77.1"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), click())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                withText("192.168.77.1"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), replaceText(""))
    onView(
            allOf(
                withId(android.R.id.edit),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
                isDisplayed(),
            )
        )
        .perform(closeSoftKeyboard())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), replaceText("1"))
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                withText("1"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), replaceText("localhost"))
    onView(
            allOf(
                withId(android.R.id.edit),
                withText("localhost"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
                isDisplayed(),
            )
        )
        .perform(closeSoftKeyboard())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.button1),
                withText("OK"),
                childAtPosition(childAtPosition(withId(androidx.appcompat.R.id.buttonPanel), 0), 3),
            )
        )
        .perform(scrollTo(), click())

    onView(
            allOf(
                childAtPosition(
                    allOf(
                        withId(androidx.preference.R.id.recycler_view),
                        childAtPosition(withId(android.R.id.list_container), 0),
                    ),
                    1,
                ),
                isDisplayed(),
            )
        )
        .perform(click())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                withText("4444"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), click())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                withText("4444"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), replaceText(""))
    onView(
            allOf(
                withId(android.R.id.edit),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
                isDisplayed(),
            )
        )
        .perform(closeSoftKeyboard())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), replaceText("12345"), closeSoftKeyboard())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.button1),
                withText("OK"),
                childAtPosition(childAtPosition(withId(androidx.appcompat.R.id.buttonPanel), 0), 3),
            )
        )
        .perform(scrollTo(), click())

    onView(
            allOf(
                childAtPosition(
                    allOf(
                        withId(androidx.preference.R.id.recycler_view),
                        childAtPosition(withId(android.R.id.list_container), 0),
                    ),
                    2,
                ),
                isDisplayed(),
            )
        )
        .perform(click())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                withText("100"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), click())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                withText("100"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), replaceText(""))
    onView(
            allOf(
                withId(android.R.id.edit),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
                isDisplayed(),
            )
        )
        .perform(closeSoftKeyboard())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), replaceText("50"), closeSoftKeyboard())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.button1),
                withText("OK"),
                childAtPosition(childAtPosition(withId(androidx.appcompat.R.id.buttonPanel), 0), 3),
            )
        )
        .perform(scrollTo(), click())

    onView(
            allOf(
                childAtPosition(
                    allOf(
                        withId(androidx.preference.R.id.recycler_view),
                        childAtPosition(withId(android.R.id.list_container), 0),
                    ),
                    3,
                ),
                isDisplayed(),
            )
        )
        .perform(click())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                withText("7"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), replaceText(""))
    onView(
            allOf(
                withId(android.R.id.edit),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
                isDisplayed(),
            )
        )
        .perform(closeSoftKeyboard())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), replaceText("5"), closeSoftKeyboard())

    onView(
            allOf(
                withId(android.R.id.button1),
                withText("OK"),
                childAtPosition(childAtPosition(withId(androidx.appcompat.R.id.buttonPanel), 0), 3),
            )
        )
        .perform(scrollTo(), click())
    Thread.sleep(3000)

    onView(
            allOf(
                childAtPosition(
                    allOf(
                        withId(androidx.preference.R.id.recycler_view),
                        childAtPosition(withId(android.R.id.list_container), 0),
                    ),
                    4,
                ),
                isDisplayed(),
            )
        )
        .perform(click())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                withText("5000"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), replaceText("50"))
    onView(
            allOf(
                withId(android.R.id.edit),
                withText("50"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
                isDisplayed(),
            )
        )
        .perform(closeSoftKeyboard())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                withText("50"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), replaceText(""))
    onView(
            allOf(
                withId(android.R.id.edit),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
                isDisplayed(),
            )
        )
        .perform(closeSoftKeyboard())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), replaceText("30"), closeSoftKeyboard())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                withText("30"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), replaceText("3000"))
    onView(
            allOf(
                withId(android.R.id.edit),
                withText("3000"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
                isDisplayed(),
            )
        )
        .perform(closeSoftKeyboard())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.button1),
                withText("OK"),
                childAtPosition(childAtPosition(withId(androidx.appcompat.R.id.buttonPanel), 0), 3),
            )
        )
        .perform(scrollTo(), click())

    onView(
            allOf(
                childAtPosition(
                    allOf(
                        withId(androidx.preference.R.id.recycler_view),
                        childAtPosition(withId(android.R.id.list_container), 0),
                    ),
                    5,
                ),
                isDisplayed(),
            )
        )
        .perform(click())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                withText("http://192.168.77.1:8080/?action=stream"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), replaceText("http://192.168.77.1:8080/?action=strea"))
    onView(
            allOf(
                withId(android.R.id.edit),
                withText("http://192.168.77.1:8080/?action=strea"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
                isDisplayed(),
            )
        )
        .perform(closeSoftKeyboard())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                withText("http://192.168.77.1:8080/?action=strea"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), replaceText(""))
    onView(
            allOf(
                withId(android.R.id.edit),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
                isDisplayed(),
            )
        )
        .perform(closeSoftKeyboard())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(
            scrollTo(),
            replaceText("http://localhost:8080/?action=stream"),
            closeSoftKeyboard(),
        )
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                withText("http://localhost:8080/?action=stream"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
                isDisplayed(),
            )
        )
        .perform(closeSoftKeyboard())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.button1),
                withText("OK"),
                childAtPosition(childAtPosition(withId(androidx.appcompat.R.id.buttonPanel), 0), 3),
            )
        )
        .perform(scrollTo(), click())
    Thread.sleep(3000)

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
    Thread.sleep(7000)

    onView(
            allOf(
                withId(R.id.btnSettings),
                childAtPosition(
                    allOf(withId(R.id.main), childAtPosition(withId(android.R.id.content), 0)),
                    1,
                ),
                isDisplayed(),
            )
        )
        .perform(click())
    Thread.sleep(300)

    onView(
            allOf(
                withId(R.id.settings),
                withText("Settings"),
                childAtPosition(childAtPosition(withId(androidx.appcompat.R.id.action_bar), 1), 1),
                isDisplayed(),
            )
        )
        .perform(click())
    Thread.sleep(3000)

    onView(
            allOf(
                childAtPosition(
                    allOf(
                        withId(androidx.preference.R.id.recycler_view),
                        childAtPosition(withId(android.R.id.list_container), 0),
                    ),
                    4,
                ),
                isDisplayed(),
            )
        )
        .perform(click())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.edit),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
            )
        )
        .perform(scrollTo(), replaceText("500"))
    onView(
            allOf(
                withId(android.R.id.edit),
                withText("500"),
                childAtPosition(
                    childAtPosition(withClassName(hamcrestIs("android.widget.ScrollView")), 0),
                    1,
                ),
                isDisplayed(),
            )
        )
        .perform(closeSoftKeyboard())
    Thread.sleep(3000)

    onView(
            allOf(
                withId(android.R.id.button1),
                withText("OK"),
                childAtPosition(childAtPosition(withId(androidx.appcompat.R.id.buttonPanel), 0), 3),
            )
        )
        .perform(scrollTo(), click())
    Thread.sleep(3000)

    Espresso.pressBack()

    assertEquals(
        initialKeepaliveTimeout,
        mActivityTestRule.activity.getSenderService().getKeepaliveTimeout(),
    )
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
