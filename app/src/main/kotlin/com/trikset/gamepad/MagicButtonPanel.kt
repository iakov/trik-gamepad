package com.trikset.gamepad

import android.content.Context
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.Button

/**
 * Builds the magic buttons (`1..count`) into a container and sends `btn N down` with haptic
 * feedback on tap. Extracted from MainActivity (ROADMAP Phase B1) so the button construction and
 * command mapping are directly testable without reflection.
 */
class MagicButtonPanel(
    private val context: Context,
    private val send: (String) -> Unit,
) {

  fun populate(container: ViewGroup, count: Int) {
    container.removeAllViews()
    for (num in 1..count) {
      val name = num.toString()
      val btn = Button(context)
      btn.isHapticFeedbackEnabled = true
      btn.gravity = Gravity.CENTER
      btn.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
      btn.text = name
      btn.setBackgroundResource(R.drawable.button_shape)
      btn.setOnClickListener {
        send("btn $name down")
        // Respect the system haptics setting (Campaign 9 B): no FLAG_IGNORE_GLOBAL_SETTING.
        btn.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
