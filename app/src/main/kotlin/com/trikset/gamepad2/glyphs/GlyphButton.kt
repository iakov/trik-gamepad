package com.trikset.gamepad2.glyphs

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton

/**
 * A [Button] that renders a bundled glyph through [GlyphRendering] - the tappable variant of
 * [GlyphTextView] (magic buttons, video-source preset chips). AppCompatButton so the theme/tint
 * pipeline matches the rest of the HUD (lint AppCompatCustomView). Same visual alignment contract:
 * symbols_mono typeface, no font-line padding, size-to-visual-ink-height and asymmetric-padding
 * centering, so the whole row reads one alignment (see DECISIONS.md "Smart glyph alignment").
 */
class GlyphButton
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatButton(context, attrs, defStyleAttr) {

  /** Sets [glyph] sized to [targetVisualHeightPx] of visual ink height, then centers it. */
  fun renderGlyph(glyph: String, targetVisualHeightPx: Float) {
    GlyphRendering.render(this, glyph, targetVisualHeightPx)
  }

  /** Centers the already-set text at its current size (single-glyph pill / gear). */
  fun centerExistingGlyph() {
    GlyphRendering.centerExisting(this)
  }
}
