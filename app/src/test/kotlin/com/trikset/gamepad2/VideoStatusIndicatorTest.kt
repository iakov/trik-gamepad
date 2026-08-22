package com.trikset.gamepad2

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoStatusIndicatorTest {

  private val indicator = VideoStatusIndicator()

  @Test
  fun colorResourceMapsEachStatus() {
    data class Case(val status: VideoStatus, val expected: Int)
    val cases =
        listOf(
            Case(VideoStatus.PLAYING, R.color.greenlight),
            Case(VideoStatus.LOADING, R.color.amber),
            Case(VideoStatus.RECONNECTING, R.color.amber),
            Case(VideoStatus.UNAVAILABLE, R.color.red),
            Case(VideoStatus.DISABLED, R.color.hud_disabled),
        )
    for (case in cases) {
      assertEquals("${case.status}", case.expected, indicator.colorResource(case.status))
    }
  }
}
