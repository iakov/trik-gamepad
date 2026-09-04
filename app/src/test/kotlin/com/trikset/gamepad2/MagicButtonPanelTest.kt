package com.trikset.gamepad2

import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Direct tests for [MagicButtonPanel]. */
@RunWith(RobolectricTestRunner::class)
class MagicButtonPanelTest : RobolectricTestBase() {

  private val context = org.robolectric.RuntimeEnvironment.getApplication()
  private val sent = ArrayList<String>()
  private val panel = MagicButtonPanel(context) { sent.add(it) }
  private val symbols = listOf("▲", "■", "●", "✕", "◆")

  @Test
  fun populateShouldCreateRequestedButtonsInOrder() {
    val container = FrameLayout(context)
    panel.populate(container, 3, symbols)

    assertEquals(3, container.childCount)
    val first = container.getChildAt(0) as Button
    val last = container.getChildAt(2) as Button
    assertEquals("▲", first.text.toString())
    assertEquals("●", last.text.toString())
  }

  @Test
  fun populateShouldReplaceExistingButtons() {
    val container = FrameLayout(context)
    panel.populate(container, 2, symbols)
    panel.populate(container, 4, symbols)

    assertEquals(4, container.childCount)
  }

  @Test
  fun populateWithZeroCountShouldCreateNoButtons() {
    val container = FrameLayout(context)
    panel.populate(container, 0, symbols)
    assertEquals(0, container.childCount)
  }

  @Test
  fun populateShouldSetAccessibleButtonDescriptions() {
    val container = FrameLayout(context)
    panel.populate(container, 2, symbols)

    assertEquals("Button 1 · ▲", (container.getChildAt(0) as Button).contentDescription)
    assertEquals("Button 2 · ■", (container.getChildAt(1) as Button).contentDescription)
  }

  @Test
  fun populateFallsBackToNumbersForMissingSymbols() {
    val container = FrameLayout(context)
    panel.populate(container, 3, listOf("▲"))

    assertEquals("▲", (container.getChildAt(0) as Button).text.toString())
    assertEquals("2", (container.getChildAt(1) as Button).text.toString())
    assertEquals("3", (container.getChildAt(2) as Button).text.toString())
  }

  @Test
  fun populateShouldSet48dpTouchTargets() {
    val container = FrameLayout(context)
    panel.populate(container, 3, symbols)
    val expected = context.resources.getDimensionPixelSize(R.dimen.touch_target_min)
    for (i in 0 until container.childCount) {
      val btn = container.getChildAt(i) as Button
      // The touch target is the fixed square layout params (48dp), not the theme's
      // minimumWidth (88dp Material default) — the circle drawable stretches to these bounds.
      val lp = btn.layoutParams as ViewGroup.MarginLayoutParams
      assertEquals("width must be 48dp", expected, lp.width)
      assertEquals("height must be 48dp", expected, lp.height)
    }
  }

  @Test
  fun populateShouldSetContrastSafeTextColor() {
    val container = FrameLayout(context)
    panel.populate(container, 1, symbols)
    val btn = container.getChildAt(0) as Button
    assertEquals(
        androidx.core.content.ContextCompat.getColor(context, R.color.magic_button_text),
        btn.currentTextColor,
    )
  }

  @Test
  fun centerGlyphShouldNotTranslateTheWholeButton() {
    val container = FrameLayout(context)
    panel.populate(container, 3, symbols)

    for (i in 0 until container.childCount) {
      val btn = container.getChildAt(i) as Button
      // The glyph centering must move only the ink (via padding), never the whole button:
      // a translation would shift the circular background off-center in the cluster and into
      // the cluster's clip area (regression guard — hit 2026-08-15).
      assertEquals("button $i translationX must stay 0", 0f, btn.translationX, 0f)
      assertEquals("button $i translationY must stay 0", 0f, btn.translationY, 0f)
    }
  }

  @Test
  fun centerGlyphShouldCancelTheMetricMedianOffsetViaPadding() {
    val container = FrameLayout(context)
    panel.populate(container, 1, symbols)
    val btn = container.getChildAt(0) as Button
    val glyph = btn.text.toString()
    val metric = com.trikset.gamepad2.glyphs.GlyphMetrics.metricFor(glyph)
    // The default glyph ▲ is in the metrics table, so its vertical centering cancels the
    // weighted-ink median offset from the line-box center (medianBiasEm * textSize), NOT the
    // paint ink-box center. padTop - padBottom + 2*offsetY == 0 with offsetY =
    // -medianBiasEm * textSizePx.
    assertNotNull("default glyph ▲ must have metrics", metric)
    val offsetY = -metric!!.medianBiasEm * btn.textSize
    assertEquals(
        "vertical padding must cancel the metric median offset",
        0f,
        btn.paddingTop - btn.paddingBottom + 2f * offsetY,
        0.5f,
    )
  }

  @Test
  fun centerGlyphShouldUsePaintInkBoxForUnknownGlyphs() {
    val container = FrameLayout(context)
    panel.populate(container, 1, listOf("Ω")) // Ω is absent from the metrics table
    val btn = container.getChildAt(0) as Button
    val paint = btn.paint
    val text = btn.text.toString()
    val bounds = android.graphics.Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    val fm = paint.fontMetrics
    // Ink-box center minus line-box center (the fallback offset when no metric exists).
    val offsetY = (bounds.top + bounds.bottom) / 2f - (fm.ascent + fm.descent) / 2f
    assertEquals(
        "vertical padding must cancel the ink offset for unknown glyphs",
        0f,
        btn.paddingTop - btn.paddingBottom + 2f * offsetY,
        0.5f,
    )
  }

  @Test
  fun recenterModeShouldCenterKnownGlyphByPaintInkBoxPadding() {
    // "Re-center symbols" ON: the centering target flips from the metric table's weighted median to
    // the glyph's runtime ink-box center, but the vehicle stays asymmetric padding — the button
    // (and its circular background) must not move (regression guard: recenter was once wired as a
    // translation and shifted the circle instead of the ink — hit 2026-09-03).
    val container = FrameLayout(context)
    panel.populate(container, 1, symbols, recenter = true)
    val btn = container.getChildAt(0) as Button
    val metric = com.trikset.gamepad2.glyphs.GlyphMetrics.metricFor(btn.text.toString())
    assertNotNull("default glyph ▲ must have metrics", metric)
    assertEquals("recenter must never translate the button", 0f, btn.translationX, 0f)
    assertEquals("recenter must never translate the button", 0f, btn.translationY, 0f)
    val paint = btn.paint
    val text = btn.text.toString()
    val bounds = android.graphics.Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    val fm = paint.fontMetrics
    // Ink-box center minus line-box center (the [recenter] target), NOT the metric median offset.
    val inkOffsetY = (bounds.top + bounds.bottom) / 2f - (fm.ascent + fm.descent) / 2f
    assertEquals(
        "padding must cancel the paint ink-box offset in recenter mode",
        0f,
        btn.paddingTop - btn.paddingBottom + 2f * inkOffsetY,
        0.5f,
    )
    // The ink-box target must differ from the metric median for this glyph (▲ carries most of its
    // ink in its base, so its median sits well below its geometric box center) — otherwise the
    // toggle would have nothing to change and the assertion above would be vacuous.
    val medianOffsetY = -metric!!.medianBiasEm * btn.textSize
    assertTrue(
        "▲ ink-box offset must differ from its metric median offset",
        kotlin.math.abs(inkOffsetY - medianOffsetY) > 0.5f,
    )
  }

  @Test
  fun recenterModeShouldUsePaintInkOffsetForUnknownGlyphs() {
    val container = FrameLayout(context)
    panel.populate(container, 1, listOf("Ω"), recenter = true) // Ω is absent from the metrics table
    val btn = container.getChildAt(0) as Button
    val paint = btn.paint
    val text = btn.text.toString()
    val bounds = android.graphics.Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    val fm = paint.fontMetrics
    val offsetY = (bounds.top + bounds.bottom) / 2f - (fm.ascent + fm.descent) / 2f
    assertEquals("recenter must never translate the button", 0f, btn.translationX, 0f)
    assertEquals("recenter must never translate the button", 0f, btn.translationY, 0f)
    assertEquals(
        "padding must cancel the paint ink offset for unknown glyphs in recenter mode",
        0f,
        btn.paddingTop - btn.paddingBottom + 2f * offsetY,
        0.5f,
    )
  }

  @Test
  fun clickShouldSendBtnDownCommand() {
    val container = FrameLayout(context)
    panel.populate(container, 3, symbols)

    (container.getChildAt(1) as Button).performClick()

    // The command stays numeric even though the glyph is display-only.
    assertEquals(listOf("btn 2 down"), sent)
  }

  @Test
  fun clickShouldPerformHapticFeedback() {
    val container = FrameLayout(context)
    panel.populate(container, 1, symbols)

    val button = container.getChildAt(0) as Button
    // The haptic contract: the button is haptic-enabled and its click performs
    // the HEAVY (strong) feedback. (The old `isPressed || !isPressed` tautology asserted
    // nothing — hit 2026-08-11.)
    assertTrue(button.isHapticFeedbackEnabled)
    button.performClick()
    assertTrue(sent.isNotEmpty())
    assertEquals(
        "magic buttons must fire the strong pulse on tap",
        Haptics.constant(Haptics.Level.HEAVY),
        org.robolectric.Shadows.shadowOf(button).lastHapticFeedbackPerformed(),
    )
  }

  @Test
  fun clearListenersShouldRemoveClickHandlers() {
    val container = FrameLayout(context)
    panel.populate(container, 2, symbols)
    panel.clearListeners(container)

    (container.getChildAt(0) as Button).performClick()
    (container.getChildAt(1) as Button).performClick()
    assertTrue("no commands should be sent after clearListeners", sent.isEmpty())
  }

  @Test
  fun clearListenersShouldBeSafeOnEmptyContainer() {
    val container = FrameLayout(context)
    panel.clearListeners(container)
    // No crash and no buttons to wire.
    assertEquals(0, container.childCount)
  }
}
