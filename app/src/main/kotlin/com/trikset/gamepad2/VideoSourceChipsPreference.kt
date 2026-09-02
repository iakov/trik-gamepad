package com.trikset.gamepad2

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.trikset.gamepad2.glyphs.GlyphRow
import kotlin.math.roundToInt

/**
 * A single preference row in Robot settings (Video category) that renders the four video-source
 * preset chips ([VideoSourceChip]) as a glyph-only [GlyphRow]. The row is not itself a tap target;
 * each chip carries its own click listener wired to [onChipSelected]. The tap action (writing the
 * target URI + refreshing the video-URI row) lives in SettingsFragment, which keeps this preference
 * dumb and the behavior testable without a bound list row.
 */
class VideoSourceChipsPreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

  /** Invoked with the tapped chip; set by SettingsFragment when the screen initializes. */
  var onChipSelected: ((VideoSourceChip) -> Unit)? = null

  init {
    layoutResource = R.layout.pref_video_source_chips
    isSelectable = false
  }

  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    super.onBindViewHolder(holder)
    val row = holder.itemView.findViewById<GlyphRow>(R.id.videoSourceChipsRow) ?: return
    val resources = holder.itemView.resources
    val density = resources.displayMetrics.density
    val chips = VideoSourceChip.entries
    row.populate(
        items = chips.map { GlyphRow.Item(it.glyph, resources.getString(it.descriptionRes)) },
        targetVisualHeightPx = CHIP_GLYPH_DP * density,
        cellSizePx = (CHIP_CELL_DP * density).roundToInt(),
        gapPx = (CHIP_GAP_DP * density).roundToInt(),
        onClick = { index -> onChipSelected?.invoke(chips[index]) },
    )
    // Visible pill chrome per chip; the glyph tint stays neutral so the chip reads as a button.
    for (i in 0 until row.childCount) {
      val chip = row.getChildAt(i)
      chip.background = resources.getDrawable(R.drawable.hud_pill_bg, null)
    }
  }

  companion object {
    private const val CHIP_GLYPH_DP = 20f
    private const val CHIP_CELL_DP = 48f
    private const val CHIP_GAP_DP = 10f
  }
}
