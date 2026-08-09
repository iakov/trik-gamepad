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

/** Direct tests for [MainActivitySettingsController] (ROADMAP Phase 2-E, coverage push). */
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
  fun onPreferenceChangedShouldClampPadsAlphaLow() {
    setPref(SettingsFragment.SK_SHOW_PADS, "-50")
    assertEquals(0f, ui.lastAlpha, 0.001f)
  }

  @Test
  fun onPreferenceChangedShouldClampPadsAlphaHigh() {
    setPref(SettingsFragment.SK_SHOW_PADS, "999")
    assertEquals(1f, ui.lastAlpha, 0.001f)
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
  fun onPreferenceChangedShouldApplyStoredWheelStep() {
    setPref(SettingsFragment.SK_WHEEL_STEP, "42")
    assertEquals(42, ui.step)
  }

  @Test
  fun onPreferenceChangedWithGarbageWheelStepShouldKeepDefault() {
    setPref(SettingsFragment.SK_WHEEL_STEP, "not-a-number")
    assertEquals(7, ui.step)
  }

  @Test
  fun onPreferenceChangedShouldClampWheelStepToMax() {
    setPref(SettingsFragment.SK_WHEEL_STEP, "500")
    assertEquals(100, ui.step)
  }
}
