package com.trikset.gamepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure math tests for [TouchPadController] (ROADMAP Phase B4). */
class TouchPadControllerTest {

  private fun controller(): TouchPadController = TouchPadController()

  @Test
  fun centerShouldReturnNull() {
    // Center maps to (0,0); no movement from (0,0) -> no command.
    assertNull(controller().nextCoordinates(100f, 100f, 200f, 200f))
  }

  @Test
  fun movedRightShouldSendPositiveX() {
    // x at the right edge -> rX = 230*(1-0.5) = 115 -> clamped to 100.
    assertEquals(
        TouchPadController.Command(100, 0),
        controller().nextCoordinates(200f, 100f, 200f, 200f),
    )
  }

  @Test
  fun movedLeftShouldSendNegativeX() {
    // x at the left edge -> rX = 230*(0-0.5) = -115 -> clamped to -100.
    assertEquals(
        TouchPadController.Command(-100, 0),
        controller().nextCoordinates(0f, 100f, 200f, 200f),
    )
  }

  @Test
  fun movedTopShouldSendPositiveY() {
    // y at the top edge -> rY = -(-115) = 115 -> clamped to 100.
    assertEquals(
        TouchPadController.Command(0, 100),
        controller().nextCoordinates(100f, 0f, 200f, 200f),
    )
  }

  @Test
  fun movedBottomShouldSendNegativeY() {
    // y at the bottom edge -> rY = -115 -> clamped to -100.
    assertEquals(
        TouchPadController.Command(0, -100),
        controller().nextCoordinates(100f, 200f, 200f, 200f),
    )
  }

  @Test
  fun smallYMoveBeyondSensitivityShouldSendEvenWhenXIsWithin() {
    // After a previous command of (0,0), curX stays 0 but curY moves to -100:
    // first operand false, second true -> the || still sends.
    val c = controller()
    assertNull(c.nextCoordinates(100f, 100f, 200f, 200f))
    assertEquals(
        TouchPadController.Command(0, -100),
        c.nextCoordinates(100f, 200f, 200f, 200f),
    )
  }

  @Test
  fun moveWithinSensitivityShouldBeSuppressed() {
    // After a command of (100, 0), a tiny move still computes (100, 0) -> the
    // sensitivity gate suppresses a repeat.
    val c = controller()
    assertEquals(
        TouchPadController.Command(100, 0),
        c.nextCoordinates(200f, 100f, 200f, 200f),
    )
    assertNull(c.nextCoordinates(190f, 100f, 200f, 200f))
  }
}
