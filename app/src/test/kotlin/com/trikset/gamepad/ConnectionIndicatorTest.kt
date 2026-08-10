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
  fun disconnectedShouldMapToRedRegardlessOfReason() {
    assertEquals(
        R.color.red,
        indicator.borderColorResource(ConnectionState.Disconnected("Target changed.")),
    )
    assertEquals(R.color.red, indicator.borderColorResource(ConnectionState.Disconnected("")))
  }
}
