package com.trikset.gamepad

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.MotionEvents
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.filters.LargeTest
import java.util.Locale
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters

@RunWith(Enclosed::class)
class MainWindowTests {

  private companion object {
    val tapPrecision = floatArrayOf(1f, 1f)
  }

  @LargeTest
  @RunWith(Parameterized::class)
  class SquareButtonTest {
    @get:Rule val mActivityTestRule = FocusAwareActivityTestRule(MainActivity::class.java)

    @Parameter @JvmField var currentPadId: Int = 0

    @Parameter(1) @JvmField var currentPadName: String = ""

    @Before
    fun initNetworkSettings() {
      initNetworkSettings(mActivityTestRule.activity)
    }

    @Test
    fun squareButtonsShouldHandleCircularTapsCorrectly() {
      val server = DummyServer()
      onView(withId(currentPadId)).perform(movingTap())
      server.stopListening()

      assertPadCommands(server.receivedMessages, currentPadName) { x, y ->
        val radius = Math.sqrt((x * x + y * y).toDouble())
        assertTrue(radius > 40 && radius < 60)
      }
    }

    @Test
    fun squareButtonsShouldHandleDiagonalTapsCorrectly() {
      val server = DummyServer()
      onView(withId(currentPadId)).perform(diagonalTap())
      server.stopListening()

      var currentIndex = 0
      assertPadCommands(server.receivedMessages, currentPadName) { x, y ->
        assertTrue(Math.abs(-100 + currentIndex * 200 / 10 - x) <= 25)
        assertTrue(Math.abs(100 - currentIndex * 200 / 10 - y) <= 25)
        ++currentIndex
      }
    }

    /**
     * Asserts [messages] is a sequence of `pad <name> x y` moves (validated by [validateMove])
     * ending with `pad <name> up`.
     */
    private fun assertPadCommands(
        messages: List<String>,
        padName: String,
        validateMove: (x: Int, y: Int) -> Unit,
    ) {
      val iterator = messages.iterator()
      while (iterator.hasNext()) {
        val current = iterator.next()
        val up = String.format(Locale.ROOT, "pad %s up", padName)
        if (iterator.hasNext()) {
          assertNotEquals(up, current)
          val splitCommand = current.split(" ")
          assertEquals(4, splitCommand.size)
          assertEquals("pad", splitCommand[0])
          assertEquals(padName, splitCommand[1])
          validateMove(splitCommand[2].toInt(), splitCommand[3].toInt())
        } else {
          assertEquals(up, current)
        }
      }
    }

    private fun diagonalTap(): ViewAction {
      return object : ViewAction {
        private val tapSegmentCount = 10

        override fun getConstraints(): Matcher<View> = isDisplayed()

        override fun getDescription(): String =
            "Diagonal tap from top left corner to bottom right corner"

        override fun perform(uiController: UiController, view: View) {
          val topLeftCoords = IntArray(2)
          view.getLocationOnScreen(topLeftCoords)
          val startCoords =
              floatArrayOf(
                  topLeftCoords[0] + tapPrecision[0],
                  topLeftCoords[1] - tapPrecision[1],
              )
          val tap = MotionEvents.sendDown(uiController, startCoords, tapPrecision).down
          uiController.loopMainThreadUntilIdle()
          try {
            for (i in 1 until tapSegmentCount) {
              val currentCoords =
                  floatArrayOf(
                      startCoords[0] + i.toFloat() * view.width / tapSegmentCount,
                      startCoords[1] - i.toFloat() * view.height / tapSegmentCount,
                  )
              if (!MotionEvents.sendMovement(uiController, tap, currentCoords)) {
                MotionEvents.sendCancel(uiController, tap)
                break
              }
            }
            if (!MotionEvents.sendUp(uiController, tap)) {
              MotionEvents.sendCancel(uiController, tap)
            }
          } finally {
            tap.recycle()
          }
        }
      }
    }

    private fun movingTap(): ViewAction {
      return object : ViewAction {
        private val tapSegmentCount = 10

        override fun getConstraints(): Matcher<View> = isDisplayed()

        override fun getDescription(): String = "Circular press around the starting point"

        override fun perform(uiController: UiController, view: View) {
          val tapRadius = view.width / 4
          val topLeftCoords = IntArray(2)
          view.getLocationOnScreen(topLeftCoords)
          val centerCoords =
              intArrayOf(
                  topLeftCoords[0] + view.width / 2,
                  topLeftCoords[1] + view.width / 2,
              )
          val startCoords =
              floatArrayOf(
                  centerCoords[0] + tapRadius.toFloat(),
                  centerCoords[1].toFloat(),
              )
          val tap = MotionEvents.sendDown(uiController, startCoords, tapPrecision).down
          uiController.loopMainThreadForAtLeast(100)
          for (i in 1..tapSegmentCount) {
            val currentAngle = 2 * i * Math.PI / tapSegmentCount
            val currentCoords =
                floatArrayOf(
                    centerCoords[0] + tapRadius * Math.cos(currentAngle).toFloat(),
                    centerCoords[1] + tapRadius * Math.sin(currentAngle).toFloat(),
                )
            MotionEvents.sendMovement(uiController, tap, currentCoords)
            uiController.loopMainThreadForAtLeast(50)
          }
          MotionEvents.sendUp(uiController, tap)
        }
      }
    }

    companion object {
      @JvmStatic
      @Parameters
      fun data(): Collection<Array<Any>> =
          listOf(arrayOf(R.id.leftPad, "1"), arrayOf(R.id.rightPad, "2"))
    }
  }

  @LargeTest
  @RunWith(JUnit4::class)
  class MagicButtonsTests {
    // The magic-button row renders at the configured count during onCreate; set it
    // to 5 before the activity launches so the full row (buttons 1..5) is asserted.
    @get:Rule
    val mActivityTestRule =
        object : FocusAwareActivityTestRule<MainActivity>(MainActivity::class.java) {
          override fun beforeActivityLaunched() {
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                        .targetContext
                )
                .edit()
                .putInt(SettingsFragment.SK_MAGIC_BUTTON_COUNT, 5)
                .commit()
          }
        }

    @Before
    fun initNetworkSettings() {
      initNetworkSettings(mActivityTestRule.activity)
    }

    @Test
    @Throws(InterruptedException::class)
    fun magicButtonsShouldSendCorrectCommands() {
      val server = DummyServer()
      for (i in 1..5) {
        // Direct performClick (no touch injection): the Espresso touch-based click() raced the
        // connect->video-reload work and silently dropped the second tap in the sequence
        // (hit 2026-08-11, whichever button was second). The wiring under test is the button
        // listener -> command; pad touch handling is covered by SquareButtonTest.
        onView(withContentDescription("Button $i")).perform(performClickAction())
        // The listener fires an async send (connect + write on a real executor); bounded-await
        // each command so the socket write lands (TESTING.md: never a bare assert on
        // server-received content).
        assertTrue(
            "expected 'btn $i down', received so far: ${server.receivedMessages}",
            server.awaitMessage(String.format(Locale.ROOT, "btn %d down", i), 30_000),
        )
      }
      server.stopListening()

      val messages = server.receivedMessages.iterator()
      for (i in 1..5) {
        assertTrue(messages.hasNext())
        val currentMessage = messages.next()
        assertEquals(String.format(Locale.ROOT, "btn %d down", i), currentMessage)
      }
      assertFalse(messages.hasNext())
    }

    /** A [ViewAction] that calls [View.performClick] directly (bypasses touch injection). */
    private fun performClickAction(): ViewAction {
      return object : ViewAction {
        override fun getConstraints(): Matcher<View> = isDisplayed()

        override fun getDescription(): String = "performClick"

        override fun perform(uiController: UiController, view: View) {
          view.performClick()
        }
      }
    }
  }
}
