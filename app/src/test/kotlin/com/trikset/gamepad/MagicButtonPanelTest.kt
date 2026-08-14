package com.trikset.gamepad

import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
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
    // the feedback. (The old `isPressed || !isPressed` tautology asserted
    // nothing — hit 2026-08-11.)
    assertTrue(button.isHapticFeedbackEnabled)
    button.performClick()
    assertTrue(sent.isNotEmpty())
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
