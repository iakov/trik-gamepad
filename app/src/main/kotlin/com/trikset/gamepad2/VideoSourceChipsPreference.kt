package com.trikset.gamepad2

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.InsetDrawable
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import com.trikset.gamepad2.glyphs.GlyphRow
import kotlin.math.roundToInt

/**
 * A single preference row in Robot settings (Video category) that renders the four video-source
 * preset chips ([VideoSourceChip]) as a glyph-only [GlyphRow]. The row is not itself a tap target;
 * each chip carries its own click listener wired to [onChipSelected]. The tap action (writing the
 * target URI + refreshing the video-URI row) lives in SettingsFragment, which keeps this preference
 * dumb and the behavior testable without a bound list row.
 *
 * Geometry: each chip's tappable cell stays [CHIP_CELL_DP] wide (the Android touch-target floor);
 * the visible circle chrome is smaller ([CHIP_CIRCLE_DP]) and centered in the cell, so the chip
 * reads as a compact ring hugging its glyph with a transparent 8dp tap halo on each side. Cells
 * abut (no inter-cell gap): the two halos between neighbours produce 16dp of visible air between
 * the rings, and the layout centers the whole cluster in the row.
 *
 * Live re-render: the glyph alignment follows the global "Smart glyph alignment" toggle, which
 * lives on the App-settings screen (a sibling activity). That screen can sit on top of Robot
 * settings in back stack, so this row registers a change listener while attached and re-populates
 * its bound row when [SettingsFragment.SK_RECENTER_GLYPHS] flips — the settings RecyclerView does
 * not re-bind rows on resume, so without the listener a returned-to Robot screen would keep the old
 * alignment.
 */
class VideoSourceChipsPreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

  /** Invoked with the tapped chip; set by SettingsFragment when the screen initializes. */
  var onChipSelected: ((VideoSourceChip) -> Unit)? = null

  /**
   * The row view most recently handed to [onBindViewHolder] (re-populated on a recenter toggle).
   */
  private var boundRow: GlyphRow? = null

  /**
   * Re-renders the bound row when the "Smart glyph alignment" toggle flips while this preference is
   * attached (see the class doc). Registered/unregistered against the fragment screen's view
   * lifetime via [onAttached]/[onDetached], so the listener stays live while Robot settings is
   * merely paused under the App-settings screen in the back stack.
   */
  private val recenterChangeListener =
      SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SettingsFragment.SK_RECENTER_GLYPHS) {
          boundRow?.let { row -> populateRow(row) }
        }
      }

  init {
    layoutResource = R.layout.pref_video_source_chips
    isSelectable = false
  }

  override fun onAttached() {
    super.onAttached()
    PreferenceManager.getDefaultSharedPreferences(context)
        .registerOnSharedPreferenceChangeListener(recenterChangeListener)
  }

  override fun onDetached() {
    PreferenceManager.getDefaultSharedPreferences(context)
        .unregisterOnSharedPreferenceChangeListener(recenterChangeListener)
    boundRow = null
    super.onDetached()
  }

  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    super.onBindViewHolder(holder)
    // The chips layout's root can be a wrapper (the preference framework can strip the root's
    // android:id during inflation — observed: itemView.id == 0 at bind), so resolve the row by id
    // with a direct-cast fallback for the row-as-root case.
    val row =
        holder.itemView as? GlyphRow
            ?: holder.itemView.findViewById<GlyphRow>(R.id.videoSourceChipsRow)
            ?: return
    boundRow = row
    populateRow(row)
  }

  /**
   * Populates [row] with the four chips from current preferences. Shared by [onBindViewHolder] and
   * the recenter change listener, so a toggle re-render and an initial bind always agree.
   */
  private fun populateRow(row: GlyphRow) {
    val resources = row.resources
    val density = resources.displayMetrics.density
    val chips = VideoSourceChip.entries
    val recenter =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(
                SettingsFragment.SK_RECENTER_GLYPHS,
                SettingsFragment.DEFAULT_RECENTER_GLYPHS,
            )
    // Visible ring chrome per chip: a [CHIP_CIRCLE_DP] ring centered in the [CHIP_CELL_DP] cell
    // (the InsetDrawable insets the ring by half the difference), plus an explicit glyph color. The
    // glyph color must be set explicitly (not the themed Button default): the chips sit on the
    // DayNight settings list, where an inherited color is never contrast-verified and can vanish on
    // one theme (the tester's "no glyph visible" report). WcagContrastTest covers the chip_glyph
    // day/night pairs.
    val ringInsetPx = ((CHIP_CELL_DP - CHIP_CIRCLE_DP) / 2f * density).roundToInt()
    val ring = ResourcesCompat.getDrawable(resources, R.drawable.hud_chip_ring, null)
    val glyphColor = ContextCompat.getColor(row.context, R.color.chip_glyph)
    row.populate(
        items = chips.map { GlyphRow.Item(it.glyph, resources.getString(it.descriptionRes)) },
        targetVisualHeightPx = CHIP_GLYPH_DP * density,
        cellSizePx = (CHIP_CELL_DP * density).roundToInt(),
        gapPx = 0,
        recenter = recenter,
        // The ring is an InsetDrawable whose intrinsic insets the View applies as padding, so the
        // chrome must be set BEFORE renderGlyph — a background applied afterwards wipes the glyph's
        // asymmetric ink-centering padding with the uniform inset and the chips fall back to plain
        // line-box centering (hit 2026-09-04: the tester's "chips look off / bad padding" report;
        // the magic buttons are immune because their circle drawable has no intrinsic padding).
        chrome = { chip ->
          chip.background = ring?.let { InsetDrawable(it, ringInsetPx) }
          chip.setTextColor(glyphColor)
        },
        onClick = { index -> onChipSelected?.invoke(chips[index]) },
    )
  }

  companion object {
    /** Glyph VISUAL ink height inside the ring (≈63% of [CHIP_CIRCLE_DP]). */
    private const val CHIP_GLYPH_DP = 20f
    /** Tappable cell edge (touch-target floor); the ring inside it is smaller. */
    private const val CHIP_CELL_DP = 48f
    /** Visible ring diameter (the cell minus the two 8dp tap halos). */
    private const val CHIP_CIRCLE_DP = 32f
  }
}
