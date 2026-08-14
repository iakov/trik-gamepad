package com.trikset.gamepad

import org.robolectric.annotation.Config

/**
 * Common Robolectric test base: carries the 3-SDK [Config] triple. Robolectric resolves [Config]
 * (and `@LooperMode`) from superclasses (probed 2026-08-09), so subclasses only need
 * `@RunWith(RobolectricTestRunner::class)` plus their own `@GraphicsMode`/`@LooperMode` where they
 * differ. Note [Config.OLDEST_SDK] is the sentinel -4, resolved to the app minSdk (23).
 */
@Config(sdk = [Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK])
open class RobolectricTestBase {

  /** Walks a dialog's window tree for views matching [predicate] (appcompat dialog internals). */
  protected fun dialogViews(
      dialog: androidx.appcompat.app.AlertDialog,
      predicate: (android.view.View) -> Boolean,
  ): List<android.view.View> {
    val found = ArrayList<android.view.View>()
    fun collect(view: android.view.View) {
      if (predicate(view)) found.add(view)
      if (view is android.view.ViewGroup) {
        for (i in 0 until view.childCount) collect(view.getChildAt(i))
      }
    }
    collect(dialog.window?.decorView ?: return emptyList())
    return found
  }
}
