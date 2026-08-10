package com.trikset.gamepad

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Direct tests for [MagicButtonSymbols] — the glyph resolution (display-only, protocol stays
 * numeric).
 */
@RunWith(RobolectricTestRunner::class)
class MagicButtonSymbolsTest : RobolectricTestBase() {

  @Test
  fun defaultsFollowThePsGeometricConvention() {
    assertEquals("▲", MagicButtonSymbols.default(1))
    assertEquals("■", MagicButtonSymbols.default(2))
    assertEquals("●", MagicButtonSymbols.default(3))
    assertEquals("✕", MagicButtonSymbols.default(4))
    assertEquals("◆", MagicButtonSymbols.default(5))
  }

  @Test
  fun resolveFallsBackToDefaultForMissingOrBlank() {
    assertEquals("▲", MagicButtonSymbols.resolve(1, null))
    assertEquals("▲", MagicButtonSymbols.resolve(1, "  "))
  }

  @Test
  fun resolveHonorsStoredSymbol() {
    assertEquals("A", MagicButtonSymbols.resolve(1, "A"))
    assertEquals("★", MagicButtonSymbols.resolve(4, "★"))
  }
}
