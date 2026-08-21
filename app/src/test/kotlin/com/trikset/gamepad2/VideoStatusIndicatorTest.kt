package com.trikset.gamepad2

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoStatusIndicatorTest {

  private val indicator = VideoStatusIndicator()

  @Test
  fun playingShouldMapToGreen() {
    assertEquals(R.color.greenlight, indicator.colorResource(VideoStatus.PLAYING))
  }

  @Test
  fun loadingAndReconnectingShouldMapToAmber() {
    assertEquals(R.color.amber, indicator.colorResource(VideoStatus.LOADING))
    assertEquals(R.color.amber, indicator.colorResource(VideoStatus.RECONNECTING))
  }

  @Test
  fun unavailableShouldMapToRed() {
    assertEquals(R.color.red, indicator.colorResource(VideoStatus.UNAVAILABLE))
  }

  @Test
  fun disabledShouldMapToGray() {
    assertEquals(R.color.hud_disabled, indicator.colorResource(VideoStatus.DISABLED))
  }
}
