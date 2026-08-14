package com.trikset.gamepad

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionIndicatorTest {

  private val indicator = ConnectionIndicator()

  @Test
  fun connectingShouldMapToAmber() {
    assertEquals(R.color.amber, indicator.borderColorResource(ConnectionState.Connecting))
  }

  @Test
  fun connectedShouldMapToGreen() {
    assertEquals(R.color.greendark, indicator.borderColorResource(ConnectionState.Connected))
  }

  @Test
  fun realErrorDisconnectShouldMapToRed() {
    assertEquals(
        R.color.red,
        indicator.borderColorResource(ConnectionState.Disconnected("Target changed.")),
    )
  }

  @Test
  fun idleDisconnectShouldMapToSepia() {
    // Never connected yet (empty reason) = standby, not an error.
    assertEquals(R.color.hud_sepia, indicator.borderColorResource(ConnectionState.Disconnected("")))
    assertEquals(
        R.color.hud_sepia,
        indicator.borderColorResource(
            ConnectionState.Disconnected(ConnectionState.PAUSE_DISCONNECT_REASON)
        ),
    )
  }

  @Test
  fun accentColorFollowsTheSameSemantics() {
    assertEquals(R.color.greenlight, indicator.accentColorResource(ConnectionState.Connected))
    assertEquals(R.color.amber, indicator.accentColorResource(ConnectionState.Connecting))
    assertEquals(
        R.color.hud_sepia,
        indicator.accentColorResource(ConnectionState.Disconnected("")),
    )
    assertEquals(
        R.color.red,
        indicator.accentColorResource(ConnectionState.Disconnected("Target changed.")),
    )
  }
}
