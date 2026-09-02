package com.trikset.gamepad2.glyphs

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * A horizontal row of [GlyphButton]s that share ONE visual center line and ONE target ink height:
 * every cell gets the same fixed size, and each glyph's text size is derived from its own
 * [GlyphMetrics.visualHeightEm] so different glyphs (the digits 1/2, the eye, the usb icon) render
 * at the same VISUAL height - the "glyphs in a row must be visually aligned" rule (DECISIONS.md
 * "Smart glyph alignment"). The 90%-of-ink-in-the-same-rows criterion is the suggested way to CHECK
 * the alignment on-device (scripts/measure_glyph_row.py); the view guarantees it by construction
 * from the em-relative metrics.
 *
 * Cells are configured via [Item] (glyph, contentDescription); the tap action is per-cell
 * [onClick]. Use for the video-source preset chips (Settings > Video) and anywhere else a row of
 * tappable glyphs must stay aligned.
 */
class GlyphRow
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

  data class Item(val glyph: String, val contentDescription: String)

  /**
   * Builds [items] as equal fixed-size cells, each glyph rendered at [targetVisualHeightPx] of
   * visual ink height (e.g. the reference glyph "1" measured on-device). [cellSizePx] is the square
   * cell edge (the touch target); [gapPx] the inter-cell margin.
   */
  fun populate(
      items: List<Item>,
      targetVisualHeightPx: Float,
      cellSizePx: Int,
      gapPx: Int,
      onClick: (Int) -> Unit,
  ) {
    orientation = HORIZONTAL
    removeAllViews()
    items.forEachIndexed { index, item ->
      val button =
          GlyphButton(context).apply {
            renderGlyph(item.glyph, targetVisualHeightPx)
            contentDescription = item.contentDescription
            layoutParams =
                MarginLayoutParams(cellSizePx, cellSizePx).apply {
                  if (index > 0) marginStart = gapPx
                }
            setOnClickListener { onClick(index) }
          }
      addView(button, ViewGroup.LayoutParams(cellSizePx, cellSizePx))
      val lp = button.layoutParams as MarginLayoutParams
      if (index > 0) lp.marginStart = gapPx
      button.layoutParams = lp
    }
  }
}
