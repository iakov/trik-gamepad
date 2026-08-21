package com.trikset.gamepad2

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

/**
 * Builds the magic buttons (`1..count`) into a container and sends `btn N down` with haptic
 * feedback on tap. The button's [Button.text] is the display glyph from [symbols] (cosmetic — the
 * protocol command stays numeric); accessibility announces "Button N". Extracted from MainActivity
 * so the button construction and command mapping are directly testable without reflection.
 */
class MagicButtonPanel(
    private val context: Context,
    private val send: (String) -> Unit,
) {
  private var container: ViewGroup? = null

  fun populate(container: ViewGroup, count: Int, symbols: List<String>) {
    this.container = container
    container.removeAllViews()
    val touchTarget = context.resources.getDimensionPixelSize(R.dimen.touch_target_min)
    val margin = context.resources.getDimensionPixelSize(R.dimen.hud_magic_button_margin_start)
    for (num in 1..count) {
      val name = num.toString()
      val btn = Button(context)
      btn.isHapticFeedbackEnabled = true
      btn.gravity = Gravity.CENTER
      // Fixed square bounds (48dp) so the oval background renders as a perfect circle: an `oval`
      // drawable stretches to the view bounds, so WRAP_CONTENT + the themed Button's default
      // horizontal padding made the buttons wider than tall (ellipses). Zero the theme padding so
      // nothing pushes the content wider than the square.
      val lp = ViewGroup.MarginLayoutParams(touchTarget, touchTarget)
      if (num > 1) {
        lp.marginStart = margin
      }
      btn.layoutParams = lp
      btn.setPadding(0, 0, 0, 0)
      btn.setSingleLine(true)
      // The bundled mono symbol font (res/font/symbols_mono.ttf): monospace, so every glyph keeps
      // the same 1-em advance and the 0.6x-diameter sizing + centerGlyph math stay exact. It is a
      // minimal subset of DejaVu Sans Mono Nerd Font covering the defaults ▲■●✕◆ plus a few
      // letters/digits; any other user-typed symbol falls back to the system font per-glyph.
      btn.setTypeface(ResourcesCompat.getFont(context, R.font.symbols_mono) ?: Typeface.MONOSPACE)
      // Drop the font's internal ascent/descent padding so only the glyph's real ink box remains
      // inside the circle (the "extra space" was font padding, not the char-vs-circle gap).
      btn.setIncludeFontPadding(false)
      btn.text = symbols.getOrNull(num - 1) ?: name
      // Glyph height = 60% of the circle diameter (MAGIC_GLYPH_SIZE_RATIO), in px so the ratio
      // holds regardless of the system font scale (the glyphs are pictograms, not reflowable text).
      btn.setTextSize(
          android.util.TypedValue.COMPLEX_UNIT_PX,
          touchTarget * MAGIC_GLYPH_SIZE_RATIO,
      )
      centerGlyph(btn)
      // Explicit light text on the dark fills (contrast verified by WcagContrastTest); setAccent
      // recolors the glyph to the connection-state accent afterwards.
      btn.setTextColor(ContextCompat.getColor(context, R.color.magic_button_text))
      // Accessibility: the glyph is part of the description so a screen-reader user can map the
      // symbol to its meaning ("Button 1 · ▲"), not just its index.
      btn.contentDescription =
          context.getString(R.string.button_number_description, name, btn.text.toString())
      btn.setBackgroundResource(R.drawable.hud_button_circle)
      btn.setOnClickListener {
        send("btn $name down")
        // One strong pulse per tap (LONG_PRESS -> EFFECT_HEAVY_CLICK), user-chosen "one strong
        // for button". Respects the system haptics setting (no FLAG_IGNORE_GLOBAL_SETTING).
        btn.haptic(Haptics.Level.HEAVY)
      }
      container.addView(btn)
    }
  }

  /**
   * Centers the glyph's INK box (not the font's line box) in the button WITHOUT moving the button.
   * TextView gravity centers the ascent/descent box, but pictograms (▲ ● ■ ✕ ◆) carry their ink
   * asymmetrically inside that box, so they sit off-center. The fix is asymmetric padding, never
   * `translation*`: padding shrinks only the text content box, so the circular background keeps its
   * centered spot in the cluster while the ink box lands on the view center (`padTop - padBottom +
   * 2 * offsetY == 0`). A `translationY` would move the whole button — background included — out of
   * center, and the cluster's padding clip would cut its top arc (hit 2026-08-15, see DECISIONS.md
   * "Magic-button glyph centering").
   */
  private fun centerGlyph(btn: Button) {
    val paint = btn.paint
    val text = btn.text.toString()
    val bounds = android.graphics.Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    val fm = paint.fontMetrics
    // Line-box center vs ink-box center, both relative to the baseline.
    val lineCenter = (fm.ascent + fm.descent) / 2f
    val inkCenterY = (bounds.top + bounds.bottom) / 2f
    val inkCenterX = (bounds.left + bounds.right) / 2f
    // Asymmetric padding = GLYPH_CENTER_PADDING_FACTOR * the ink offset, on the side opposite the
    // offset, so the ink center lands on the view center while the background stays put.
    val offsetY = inkCenterY - lineCenter
    val offsetX = inkCenterX - (paint.measureText(text) / 2f)
    val paddingTop = (-GLYPH_CENTER_PADDING_FACTOR * offsetY).coerceAtLeast(0f).toInt()
    val paddingBottom = (GLYPH_CENTER_PADDING_FACTOR * offsetY).coerceAtLeast(0f).toInt()
    val paddingStart = (-GLYPH_CENTER_PADDING_FACTOR * offsetX).coerceAtLeast(0f).toInt()
    val paddingEnd = (GLYPH_CENTER_PADDING_FACTOR * offsetX).coerceAtLeast(0f).toInt()
    btn.setPaddingRelative(paddingStart, paddingTop, paddingEnd, paddingBottom)
  }

  fun clearListeners(container: ViewGroup) {
    for (i in 0 until container.childCount) {
      container.getChildAt(i).setOnClickListener(null)
    }
  }

  /**
   * Recolors the magic-button glyphs to the connection-state accent (green/amber/sepia/red — see
   * [ConnectionIndicator]). Only the glyph color changes; the circular glass background, count and
   * haptics are untouched.
   */
  fun setAccent(@androidx.annotation.ColorRes colorRes: Int) {
    val accent = ContextCompat.getColor(context, colorRes)
    val views = container
    if (views == null) return
    for (i in 0 until views.childCount) {
      (views.getChildAt(i) as? Button)?.setTextColor(accent)
    }
  }

  private companion object {
    // Magic-button glyph height as a fraction of the circle diameter (60%).
    const val MAGIC_GLYPH_SIZE_RATIO = 0.6f
    // Padding must be twice the ink offset to cancel it (`padTop - padBottom + 2 * offsetY == 0`
    // with gravity=CENTER: the line box is centered in the content box, so a `1 * offset` padding
    // would only move the line box by half the needed amount).
    const val GLYPH_CENTER_PADDING_FACTOR = 2f
  }
}
