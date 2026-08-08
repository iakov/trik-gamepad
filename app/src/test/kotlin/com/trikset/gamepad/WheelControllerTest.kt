package com.trikset.gamepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure math tests for [WheelController] (ROADMAP Phase 2-F). */
class WheelControllerTest {

  private val controller = WheelController()

  @Test
  fun nextAngleShouldReturnNullWhenDisabled() {
    assertNull(controller.nextAngle(0.7f, 0.7f, currentAngle = 0, step = 7, enabled = false))
  }

  @Test
  fun nextAngleShouldReturnNullWhenXBelowAccelerationFloor() {
    assertNull(controller.nextAngle(0.0000001f, 0.7f, currentAngle = 0, step = 7, enabled = true))
  }

  @Test
  fun nextAngleShouldBeNullForStraightAhead() {
    // atan2(0, 1) = 0 -> dead zone zeroes it and the step gate sees no change.
    assertNull(controller.nextAngle(1f, 0f, currentAngle = 0, step = 7, enabled = true))
  }

  @Test
  fun nextAngleShouldComputePositiveAngle() {
    // atan2(0.7, 0.7) = pi/4 -> 200 * 1.5 * (pi/4) / pi = 75.
    assertEquals(
        75,
        controller.nextAngle(0.7f, 0.7f, currentAngle = 0, step = 7, enabled = true),
    )
  }

  @Test
  fun nextAngleShouldSkipTinyDeltas() {
    // atan2(0.01, 1) ~ 0.01 -> toInt() = 0, dead zone -> 0; step gate blocks.
    assertNull(controller.nextAngle(1f, 0.01f, currentAngle = 0, step = 7, enabled = true))
  }

  @Test
  fun nextAngleShouldClampPositiveAngle() {
    // atan2(100, 1) ~ 1.56 -> ~149 -> clamped to 100.
    assertEquals(
        100,
        controller.nextAngle(1f, 100f, currentAngle = 0, step = 7, enabled = true),
    )
  }

  @Test
  fun nextAngleShouldClampNegativeAngle() {
    // atan2(-2, 1) ~ -1.11 -> ~-106 -> clamped to -100.
    assertEquals(
        -100,
        controller.nextAngle(1f, -2f, currentAngle = 0, step = 7, enabled = true),
    )
  }

  @Test
  fun nextAngleShouldRespectStepGate() {
    // Moving within one step of the current angle produces no command.
    assertEquals(
        75,
        controller.nextAngle(0.7f, 0.7f, currentAngle = 65, step = 7, enabled = true),
    )
    assertNull(controller.nextAngle(0.7f, 0.7f, currentAngle = 72, step = 7, enabled = true))
  }
}
