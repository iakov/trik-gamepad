package com.trikset.gamepad2

import android.view.ViewGroup
import android.widget.LinearLayout
import com.trikset.gamepad2.glyphs.GlyphButton
import com.trikset.gamepad2.glyphs.GlyphMetrics
import com.trikset.gamepad2.glyphs.GlyphRendering
import com.trikset.gamepad2.glyphs.GlyphRow
import com.trikset.gamepad2.glyphs.GlyphTextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Direct tests for the reusable glyph suite ([GlyphRendering] + the [GlyphTextView]/[GlyphButton]
 * views + [GlyphRow]).
 */
@RunWith(RobolectricTestRunner::class)
class GlyphViewsTest : RobolectricTestBase() {

  private val context = org.robolectric.RuntimeEnvironment.getApplication()

  @Test
  fun glyphTextViewRendersBundledGlyphCentered() {
    val view = GlyphTextView(context)
    view.renderGlyph("1", 20f)

    // Bundled font + no font-line padding + center gravity: the shared pre-conditions.
    assertEquals(GlyphRendering.typeface(context), view.typeface)
    assertTrue(view.includeFontPadding.not())
    assertEquals(android.view.Gravity.CENTER, view.gravity)
    assertEquals("1", view.text.toString())
    // Sized from the metric: textSize * visualHeightEm == target (within int rounding).
    val metric = GlyphMetrics.metricFor("1")
    assertNotNull(metric)
    assertEquals(20f, view.textSize * metric!!.visualHeightEm, 0.6f)
  }

  @Test
  fun glyphTextViewCentersExistingGlyphAtCurrentSize() {
    val view = GlyphTextView(context)
    view.text = "⏻"
    view.textSize = 24f
    view.centerExistingGlyph()
    // Configured (bundled font, no font padding, centered) without changing the text size.
    assertEquals("⏻", view.text.toString())
    assertEquals(24f, view.textSize, 0.01f)
    assertEquals(GlyphRendering.typeface(context), view.typeface)
  }

  @Test
  fun glyphButtonRendersAndIsTappable() {
    val button = GlyphButton(context)
    button.renderGlyph("■", 20f)
    assertEquals("■", button.text.toString())
    assertTrue(button.isEnabled)
  }

  @Test
  fun renderCapsTextSizeWhenGlyphWouldClip() {
    // A fixed 48dp-square cell is too small for the triangle's line box + bias padding at an
    // equalized size; the cap must shrink the text size so the glyph stays inside the cell
    // (regression guard — hit 2026-09-01: ▲ rendered zero ink, ■/● clipped).
    val view = GlyphTextView(context)
    view.layoutParams = ViewGroup.MarginLayoutParams(48, 48)
    view.renderGlyph("▲", 100f)

    val metric = GlyphMetrics.metricFor("▲")
    assertNotNull(metric)
    val uncapped = 100f / metric!!.visualHeightEm
    assertTrue(
        "cap must shrink text size below the uncapped equalized size",
        view.textSize < uncapped,
    )
    // The line box + padding must still fit the 48px cell at fontScale 1 (Robolectric default).
    val contentHeight =
        view.paddingTop + view.paddingBottom + view.textSize * GlyphMetrics.LINE_BOX_EM
    assertTrue("glyph content must fit the cell", contentHeight <= 48f + 1f)
  }

  @Test
  fun renderFallsBackToDefaultHeightForUnknownGlyphs() {
    val view = GlyphTextView(context)
    // Ω is absent from the metrics table: default 0.7 em assumed, no cap (no metric).
    view.renderGlyph("Ω", 20f)
    assertEquals("Ω", view.text.toString())
    assertEquals(20f / GlyphRendering.DEFAULT_VISUAL_HEIGHT_EM, view.textSize, 0.01f)
  }

  @Test
  fun glyphRowPopulatesEqualCellsWithGapAndClicks() {
    val row = GlyphRow(context)
    val clicks = ArrayList<Int>()
    row.populate(
        listOf(GlyphRow.Item("1", "Video 1"), GlyphRow.Item("2", "Video 2")),
        targetVisualHeightPx = 18f,
        cellSizePx = 72,
        gapPx = 12,
        onClick = { clicks.add(it) },
    )

    assertEquals(LinearLayout.HORIZONTAL, row.orientation)
    assertEquals(2, row.childCount)
    val first = row.getChildAt(0) as GlyphButton
    val second = row.getChildAt(1) as GlyphButton
    assertEquals("Video 1", first.contentDescription)
    assertEquals("Video 2", second.contentDescription)
    assertEquals(72, first.layoutParams.width)
    assertEquals(72, first.layoutParams.height)
    // Gap only between cells (index > 0).
    assertEquals(0, (first.layoutParams as ViewGroup.MarginLayoutParams).marginStart)
    assertEquals(12, (second.layoutParams as ViewGroup.MarginLayoutParams).marginStart)

    (row.getChildAt(1) as GlyphButton).performClick()
    assertEquals(listOf(1), clicks)
  }

  @Test
  fun glyphRowRepopulateReplacesCells() {
    val row = GlyphRow(context)
    row.populate(listOf(GlyphRow.Item("1", "a")), 18f, 72, 12) {}
    row.populate(listOf(GlyphRow.Item("1", "a"), GlyphRow.Item("2", "b")), 18f, 72, 12) {}
    assertEquals(2, row.childCount)
  }

  @Test
  fun glyphRowSingleItemHasNoGap() {
    val row = GlyphRow(context)
    row.populate(listOf(GlyphRow.Item("1", "only")), 18f, 72, 12) {}
    val only = row.getChildAt(0)
    assertEquals(0, (only.layoutParams as ViewGroup.MarginLayoutParams).marginStart)
  }
}
