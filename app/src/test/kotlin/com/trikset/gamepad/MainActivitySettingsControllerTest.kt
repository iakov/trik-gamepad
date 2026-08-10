package com.trikset.gamepad

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.util.concurrent.PausedExecutorService

/** Direct tests for [MainActivitySettingsController]. */
@RunWith(RobolectricTestRunner::class)
class MainActivitySettingsControllerTest : RobolectricTestBase() {

  private class FakeUi : MainActivitySettingsController.SettingsUi {
    var title: String? = null
    var titleSet = true
    var toasts = mutableListOf<String>()
    var url: URL? = null
    var lastAlpha = 0f
    var previousAlpha = 0f
    var step = 7

    override fun setActionBarTitle(title: String): Boolean {
      this.title = title
      return titleSet
    }

    override fun toast(text: String) {
      toasts.add(text)
    }

    override fun animatePadsAlpha(alpha: Float, previousAlpha: Float) {
      lastAlpha = alpha
      this.previousAlpha = previousAlpha
    }

    override fun setVideoUrl(url: URL?) {
      this.url = url
    }

    override fun getWheelStep(): Int = step

    override fun setWheelStep(step: Int) {
      this.step = step
    }

    var wheelEnabledState = false

    override fun isWheelEnabled(): Boolean = wheelEnabledState

    override fun setWheelEnabled(enabled: Boolean) {
      wheelEnabledState = enabled
    }

    var keepScreenOnState = true

    override fun setKeepScreenOn(enabled: Boolean) {
      keepScreenOnState = enabled
    }

    var magicCount = -1
    var magicSymbols = listOf<String>()
    var controlsVisibleState = true
    var showFpsState = false

    override fun setMagicButtons(count: Int, symbols: List<String>) {
      magicCount = count
      magicSymbols = symbols
    }

    override fun setControlsVisible(visible: Boolean) {
      controlsVisibleState = visible
    }

    override fun setShowFps(enabled: Boolean) {
      showFpsState = enabled
    }
  }

  private lateinit var context: Context
  private lateinit var prefs: SharedPreferences
  private lateinit var sender: SenderService
  private lateinit var ui: FakeUi
  private lateinit var controller: MainActivitySettingsController

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    prefs = PreferenceManager.getDefaultSharedPreferences(context)
    prefs.edit().clear().commit()
    sender = SenderService(PausedExecutorService())
    sender.setKeepaliveTimeout(10000000) // disable keepalive noise
    ui = FakeUi()
    controller = MainActivitySettingsController(context, sender, ui)
  }

  /** Stores [value] under [key] and notifies the controller (the common act step). */
  private fun setPref(key: String, value: String) {
    prefs.edit().putString(key, value).commit()
    controller.onPreferenceChanged(prefs)
  }

  @Test
  fun onPreferenceChangedWithTitleFailureShouldToast() {
    ui.titleSet = false
    controller.onPreferenceChanged(prefs)
    assertTrue(ui.toasts.any { it.contains("title") })
  }

  @Test
  fun onPreferenceChangedWithUnchangedAddrShouldNotRewriteVideoUri() {
    // First pass establishes the address; a second identical pass keeps the
    // address the same -> the video-URI rewrite is skipped.
    controller.onPreferenceChanged(prefs)
    setPref(SettingsFragment.SK_VIDEO_URI, "http://10.0.0.7:8080/?action=stream")
    assertEquals(
        "http://10.0.0.7:8080/?action=stream",
        prefs.getString(SettingsFragment.SK_VIDEO_URI, ""),
    )
  }

  @Test
  fun onPreferenceChangedShouldClampPadsAlpha() {
    val cases = listOf("-50" to 0f, "999" to 1f)
    for ((value, expected) in cases) {
      setPref(SettingsFragment.SK_SHOW_PADS, value)
      assertEquals("pads alpha for '$value' must clamp", expected, ui.lastAlpha, 0.001f)
    }
  }

  @Test
  fun onPreferenceChangedWithIntSliderValuesShouldClamp() {
    // SeekBarPreference stores Int — the readInt helper must handle it.
    prefs.edit().putInt(SettingsFragment.SK_SHOW_PADS, 255).commit()
    controller.onPreferenceChanged(prefs)
    assertEquals("max alpha", 1f, ui.lastAlpha, 0.001f)

    prefs.edit().putInt(SettingsFragment.SK_WHEEL_STEP, 999).commit()
    controller.onPreferenceChanged(prefs)
    assertEquals("wheel step clamped to 100", 100, ui.step)
  }

  @Test
  fun onPreferenceChangedWithWheelEnabledSwitchShouldApply() {
    prefs.edit().putBoolean(SettingsFragment.SK_WHEEL_ENABLED, true).commit()
    controller.onPreferenceChanged(prefs)
    assertTrue(ui.wheelEnabledState)
  }

  @Test
  fun onPreferenceChangedWithKeepScreenOnSwitchShouldApply() {
    prefs.edit().putBoolean(SettingsFragment.SK_KEEP_SCREEN_ON, false).commit()
    controller.onPreferenceChanged(prefs)
    assertTrue(!ui.keepScreenOnState)
  }

  @Test
  fun onPreferenceChangedWithEmptyVideoUriShouldNullTheUrl() {
    // Establish the default address first so the video-URI rewrite-on-host-change
    // does not overwrite the empty value we set next.
    controller.onPreferenceChanged(prefs)
    setPref(SettingsFragment.SK_VIDEO_URI, "")
    assertNull(ui.url)
  }

  @Test
  fun onPreferenceChangedWithVideoUriShouldSetUrl() {
    controller.onPreferenceChanged(prefs)
    setPref(SettingsFragment.SK_VIDEO_URI, "http://10.0.0.7:8080/?action=stream")
    assertEquals("http://10.0.0.7:8080/?action=stream", ui.url.toString())
  }

  @Test
  fun registerShouldApplyDefaultPreferences() {
    controller.register()
    try {
      // register() calls onPreferenceChanged with the (empty) prefs -> the
      // defaults are applied: default address + default port.
      assertEquals("192.168.77.1", sender.getHostAddr())
      assertEquals(7, ui.step)
    } finally {
      // Do not leak the shared-prefs listener into later tests in this JVM.
      controller.unregister()
    }
  }

  @Test
  fun doubleRegisterShouldNotDoubleApplyOrLeak() {
    controller.register()
    controller.register() // idempotent: second call is a no-op
    try {
      assertEquals("192.168.77.1", sender.getHostAddr())
      // A single unregister still releases the listener (no double-register leak).
      controller.unregister()
      controller.unregister() // idempotent no-op
    } finally {
      controller.unregister()
    }
  }

  @Test
  fun onPreferenceChangedShouldApplyOrClampWheelStep() {
    val cases = listOf("42" to 42, "not-a-number" to 7, "500" to 100)
    for ((value, expected) in cases) {
      // A non-numeric value keeps the CURRENT step, so reset the fake's default
      // per row to reproduce the fresh-state condition each case expects.
      ui.step = 7
      setPref(SettingsFragment.SK_WHEEL_STEP, value)
      assertEquals("wheel step for '$value'", expected, ui.step)
    }
  }

  @Test
  fun onPreferenceChangedWithDefaultMagicButtonsShouldApplyDefaults() {
    controller.onPreferenceChanged(prefs)
    assertEquals(3, ui.magicCount)
    assertEquals(listOf("▲", "■", "●", "✕", "◆"), ui.magicSymbols)
  }

  @Test
  fun onPreferenceChangedWithMagicButtonCountShouldClamp() {
    prefs.edit().putInt(SettingsFragment.SK_MAGIC_BUTTON_COUNT, 5).commit()
    controller.onPreferenceChanged(prefs)
    assertEquals(5, ui.magicCount)

    prefs.edit().putInt(SettingsFragment.SK_MAGIC_BUTTON_COUNT, 999).commit()
    controller.onPreferenceChanged(prefs)
    assertEquals("clamped to max", 5, ui.magicCount)

    prefs.edit().putString(SettingsFragment.SK_MAGIC_BUTTON_COUNT, "0").commit()
    controller.onPreferenceChanged(prefs)
    assertEquals("zero hides the row", 0, ui.magicCount)
  }

  @Test
  fun onPreferenceChangedShouldResolveStoredSymbols() {
    prefs.edit().putString(SettingsFragment.magicSymbolKey(1), "★").commit()
    controller.onPreferenceChanged(prefs)
    assertEquals("★", ui.magicSymbols[0])
    assertEquals("■", ui.magicSymbols[1]) // untouched -> default glyph
  }

  @Test
  fun onPreferenceChangedWithHideControlsShouldDriveVisibility() {
    controller.onPreferenceChanged(prefs)
    assertTrue(ui.controlsVisibleState)
    prefs.edit().putBoolean(SettingsFragment.SK_HIDE_CONTROLS, true).commit()
    controller.onPreferenceChanged(prefs)
    assertTrue(!ui.controlsVisibleState)
  }

  @Test
  fun onPreferenceChangedWithShowFpsShouldToggle() {
    controller.onPreferenceChanged(prefs)
    assertTrue(!ui.showFpsState)
    prefs.edit().putBoolean(SettingsFragment.SK_SHOW_FPS, true).commit()
    controller.onPreferenceChanged(prefs)
    assertTrue(ui.showFpsState)
  }

  @Test
  fun readMagicButtonCountShouldHonorIntStringAndDefaults() {
    assertEquals(
        "empty prefs -> default",
        3,
        MainActivitySettingsController.readMagicButtonCount(prefs),
    )
    prefs.edit().putInt(SettingsFragment.SK_MAGIC_BUTTON_COUNT, 2).commit()
    assertEquals(2, MainActivitySettingsController.readMagicButtonCount(prefs))
    prefs.edit().putString(SettingsFragment.SK_MAGIC_BUTTON_COUNT, "7").commit()
    assertEquals("clamped to max", 5, MainActivitySettingsController.readMagicButtonCount(prefs))
    prefs.edit().putString(SettingsFragment.SK_MAGIC_BUTTON_COUNT, "garbage").commit()
    assertEquals(
        "garbage -> default",
        3,
        MainActivitySettingsController.readMagicButtonCount(prefs),
    )
  }
}
