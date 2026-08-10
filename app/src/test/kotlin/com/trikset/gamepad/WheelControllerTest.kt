package com.trikset.gamepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure math tests for [WheelController]. */
class WheelControllerTest {

  private val controller = WheelController()

  @Test
  fun nextAngleShouldMapAndClamp() {
    data class Case(
        val x: Float,
        val y: Float,
        val currentAngle: Int = 0,
        val step: Int = 7,
        val enabled: Boolean = true,
        val expected: Int?,
    )

    val cases =
        listOf(
            Case(x = 0.7f, y = 0.7f, enabled = false, expected = null), // disabled -> null
            Case(x = 0.0000001f, y = 0.7f, expected = null), // below the acceleration floor
            Case(x = 1f, y = 0f, expected = null), // straight ahead -> dead zone zeroes it
            Case(x = 0.7f, y = 0.7f, expected = 75), // atan2(0.7,0.7)=pi/4 -> 200*1.5*(pi/4)/pi
            Case(x = 1f, y = 0.01f, expected = null), // tiny delta -> step gate blocks
            Case(x = 1f, y = 100f, expected = 100), // clamped to the positive max
            Case(x = 1f, y = -2f, expected = -100), // clamped to the negative max
        )
    for (case in cases) {
      val actual = controller.nextAngle(case.x, case.y, case.currentAngle, case.step, case.enabled)
      if (case.expected == null) {
        assertNull("case $case", actual)
      } else {
        assertEquals("case $case", case.expected, actual)
      }
    }
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
