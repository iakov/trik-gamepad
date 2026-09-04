package com.trikset.gamepad2.glyphs

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.trikset.gamepad2.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shared rendering core for every centered bundled-glyph (the pill ⏻/↺, the gear ⚙, the magic
 * buttons, the video-source preset chips). One code path owns the three things that make a glyph
 * read as "placed in a row":
 *
 * * the bundled [R.font.symbols_mono] typeface + `includeFontPadding=false` (only the glyph's real
 *   ink box counts — the font's internal ascent/descent padding would offset everything);
 * * per-glyph **size equalization** from [GlyphMetrics]: `textSize = targetVisualHeightPx /
 *   visualHeightEm`, so a row of different glyphs all render at the SAME visual ink height (the
 *   magic buttons' ▲ and the chips' eye look the same size despite very different em-fills);
 * * ink **centering via asymmetric padding, never a translation** (moving the whole view would
 *   shift its background off-center — the magic-button regression from 2026-08-15). The padding
 *   cancels the glyph's weighted-ink median offset from the line-box center, taken from
 *   [GlyphMetrics.medianBiasEm] when the glyph is bundled, else measured at runtime from the
 *   paint's ink bounds (the fallback for user-typed magic symbols).
 *
 * Consumers are thin [GlyphTextView] / [GlyphButton] views that just forward to [render]; a
 * [GlyphRow] composes them into an equalized, aligned row. This core is deliberately Every centered
 * glyph in the app renders through this core (see DECISIONS.md "Smart glyph alignment").
 */
object GlyphRendering {

  /** Bundled symbol font (the single typeface for all centered glyphs). */
  fun typeface(context: Context): Typeface =
      ResourcesCompat.getFont(context, R.font.symbols_mono) ?: Typeface.MONOSPACE

  /**
   * Applies the bundled typeface, drops the font's internal line padding and centers the content;
   * the shared pre-conditions for [render] / [centerExisting].
   */
  fun configure(view: TextView) {
    view.typeface = typeface(view.context)
    view.includeFontPadding = false
    view.gravity = Gravity.CENTER
  }

  /**
   * Renders [glyph] sized so its VISUAL ink height equals [targetVisualHeightPx], then centers it.
   * For a bundled glyph the metric's [GlyphMetrics.visualHeightEm] yields the text size; for an
   * unknown glyph (user-typed magic symbol) [DEFAULT_VISUAL_HEIGHT_EM] is assumed.
   *
   * The text size is capped so the glyph's FULL ink box plus the centering padding fit the view at
   * ANY font scale (TextView multiplies the set px text size by the user's font scale, so the cap
   * divides by it): the 90%-mass band under-reports a glyph with a thin tail (a triangle's point
   * carries little mass, so its band is ~0.45em while its box is ~0.6em) and a band-equalized size
   * alone would clip such glyphs (hit 2026-09-01: ▲ rendered zero ink, ■/● clipped to the lower
   * half).
   *
   * [recenter] selects WHICH center is aimed at (see the "Re-center symbols" setting,
   * pref_app.xml): false = the metric table's weighted-ink median (the pixel-verified default);
   * true = the glyph's actual runtime ink-box center, measured from the paint at render time (the
   * same fallback already used for user-typed glyphs). Both modes deliver the offset as asymmetric
   * padding (a translation would move the glyph's own circular background — hit 2026-08-15 and
   * 2026-09-03), so flipping the toggle only changes the target, never the vehicle.
   */
  fun render(
      view: TextView,
      glyph: String,
      targetVisualHeightPx: Float,
      recenter: Boolean = false,
  ) {
    view.text = glyph
    configure(view)
    val metric = GlyphMetrics.metricFor(glyph)
    val visualHeightEm = metric?.visualHeightEm ?: DEFAULT_VISUAL_HEIGHT_EM
    var textSizePx = targetVisualHeightPx / visualHeightEm
    val viewHeight = view.layoutParams?.height
    if (metric != null && viewHeight != null && viewHeight > 0) {
      // The glyph must fit the fixed view at ANY font scale. Android lays out the LINE BOX
      // (ascent + descent), not the ink box, and a line box taller than the view clips even
      // when the ink alone would fit. So cap at viewHeight / (fontScale * (LINE_BOX_EM +
      // 2*reserveEm)): line box + the 2*|offset| centering padding <= viewHeight. The reserve is
      // the median bias when aiming at the weighted median, else the ink-box offset actually
      // applied by [center] (measured here — bounds scale linearly, so the em value is
      // size-invariant). The 90%-mass band alone under-reports glyphs with thin tails (▲ renders
      // zero ink, ■/● clip - hit 2026-09-01).
      val fontScale = view.resources.configuration.fontScale
      val reserveEm =
          if (recenter) {
            view.paint.textSize = textSizePx
            val fitBounds = Rect()
            view.paint.getTextBounds(glyph, 0, glyph.length, fitBounds)
            val fitMetrics = view.paint.fontMetrics
            abs(
                    (fitBounds.top + fitBounds.bottom) / 2f -
                        (fitMetrics.ascent + fitMetrics.descent) / 2f
                )
                .div(textSizePx)
          } else {
            abs(metric.medianBiasEm)
          }
      val maxFit = viewHeight / (fontScale * (GlyphMetrics.LINE_BOX_EM + 2 * reserveEm))
      if (textSizePx > maxFit) textSizePx = maxFit
    }
    view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
    center(view, metric, textSizePx, recenter)
  }

  /**
   * Centers the glyph already set on [view] at its current text size (pill / gear / error text).
   * [recenter] has the same meaning as in [render]; note the pill/gear chrome uses XML padding and
   * never goes through this method, so only glyph-tile consumers use it today.
   */
  fun centerExisting(view: TextView, recenter: Boolean = false) {
    configure(view)
    val glyph = view.text.toString()
    val metric = GlyphMetrics.metricFor(glyph)
    center(view, metric, view.textSize, recenter)
  }

  /**
   * Centers the glyph's ink on the view center. Vertical: cancels the glyph's offset from the
   * line-box center — the metric table's weighted-ink median ([GlyphMetrics.medianBiasEm]) when the
   * glyph is bundled and [recenter] is false, else the ink-box center measured from the paint at
   * render time (the [recenter] mode for bundled glyphs and the always-mode for user-typed glyphs,
   * which have no metric). Horizontal: always the paint-measured ink box (the metric table is
   * vertical-only; the mono advance is symmetric by construction).
   *
   * The offset is ALWAYS cancelled as asymmetric [android.view.View.setPaddingRelative] — never a
   * view translation, which moves the glyph together with its own background and shifts the circle
   * off-center instead of the ink (the magic-button regression from 2026-08-15 and again on
   * 2026-09-03, when [recenter] was first wired as a translation). [recenter] only picks WHICH
   * center is aimed at, never the vehicle.
   */
  private fun center(
      view: TextView,
      metric: GlyphMetrics.GlyphMetric?,
      textSizePx: Float,
      recenter: Boolean,
  ) {
    val paint = view.paint
    val text = view.text.toString()
    val bounds = Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    val fm = paint.fontMetrics
    // Line-box center vs ink center, both relative to the baseline (paint metrics: ascent
    // negative, descent positive).
    val lineCenter = (fm.ascent + fm.descent) / 2f
    val inkCenterY = (bounds.top + bounds.bottom) / 2f
    val inkCenterX = (bounds.left + bounds.right) / 2f
    // metric.medianBiasEm is positive when the median sits ABOVE the line center, so its
    // pixel offset is negative in the paint y-down frame (ink above -> smaller y).
    val offsetY =
        if (!recenter && metric != null) -metric.medianBiasEm * textSizePx
        else inkCenterY - lineCenter
    val offsetX = inkCenterX - (paint.measureText(text) / 2f)
    // Round to the nearest int (not truncate): truncation leaves up to a full px of un-cancelled
    // offset, which a 0.5-tolerance centering test would see.
    val paddingTop = (-PADDING_FACTOR * offsetY).coerceAtLeast(0f).roundToInt()
    val paddingBottom = (PADDING_FACTOR * offsetY).coerceAtLeast(0f).roundToInt()
    val paddingStart = (-PADDING_FACTOR * offsetX).coerceAtLeast(0f).roundToInt()
    val paddingEnd = (PADDING_FACTOR * offsetX).coerceAtLeast(0f).roundToInt()
    view.setPaddingRelative(paddingStart, paddingTop, paddingEnd, paddingBottom)
  }

  private const val PADDING_FACTOR = 2f

  /**
   * Visual-height/em assumed for a glyph absent from [GlyphMetrics] (user-typed magic symbols): a
   * typical bundled glyph fills ~70% of its em, so textSize = target / 0.7 keeps the row's
   * equalized height plausible for unknown symbols too.
   */
  const val DEFAULT_VISUAL_HEIGHT_EM = 0.7f
}
