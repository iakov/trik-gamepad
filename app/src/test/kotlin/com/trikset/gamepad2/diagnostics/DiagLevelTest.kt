package com.trikset.gamepad2.diagnostics

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagLevelTest {

  @Test
  fun eachSettingMapsToTheExpectedBufferFloor() {
    assertEquals(Log.WARN.toLong(), DiagLevel.toBufferLevel(DiagLevel.KEY_ERRORS).toLong())
    assertEquals(Log.INFO.toLong(), DiagLevel.toBufferLevel(DiagLevel.KEY_INFO).toLong())
    assertEquals(Log.DEBUG.toLong(), DiagLevel.toBufferLevel(DiagLevel.KEY_DEBUG).toLong())
    assertEquals(Log.VERBOSE.toLong(), DiagLevel.toBufferLevel(DiagLevel.KEY_VERBOSE).toLong())
  }

  @Test
  fun unknownOrBlankValueFallsBackToInfo() {
    assertEquals(Log.INFO.toLong(), DiagLevel.toBufferLevel("garbage").toLong())
    assertEquals(Log.INFO.toLong(), DiagLevel.toBufferLevel("").toLong())
    assertEquals(Log.INFO.toLong(), DiagLevel.toBufferLevel(null).toLong())
  }
}
