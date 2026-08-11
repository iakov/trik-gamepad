package com.trikset.gamepad

import android.content.Context
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.Button
import androidx.core.content.ContextCompat

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

  fun populate(container: ViewGroup, count: Int, symbols: List<String>) {
    container.removeAllViews()
    val touchTarget = context.resources.getDimensionPixelSize(R.dimen.touch_target_min)
    for (num in 1..count) {
      val name = num.toString()
      val btn = Button(context)
      btn.isHapticFeedbackEnabled = true
      btn.gravity = Gravity.CENTER
      btn.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
      // 48dp minimum touch target (WCAG/Material); the row is 50dp tall already.
      btn.minimumWidth = touchTarget
      btn.minimumHeight = touchTarget
      btn.text = symbols.getOrNull(num - 1) ?: name
      // Explicit light text on the dark fills (contrast verified by WcagContrastTest).
      btn.setTextColor(ContextCompat.getColor(context, R.color.magic_button_text))
      // Accessibility: the glyph is part of the description so a screen-reader user can map the
      // symbol to its meaning ("Button 1 · ▲"), not just its index.
      btn.contentDescription =
          context.getString(R.string.button_number_description, name, btn.text.toString())
      btn.setBackgroundResource(R.drawable.button_ripple)
      btn.setOnClickListener {
        send("btn $name down")
        // Respect the system haptics setting: no FLAG_IGNORE_GLOBAL_SETTING. KEYBOARD_TAP is the
        // light tick for a plain tap (LONG_PRESS felt heavy).
        btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
      }
      container.addView(btn)
    }
  }

  fun clearListeners(container: ViewGroup) {
    for (i in 0 until container.childCount) {
      container.getChildAt(i).setOnClickListener(null)
    }
  }
}
