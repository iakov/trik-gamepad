package com.trikset.gamepad2.diagnostics

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.preference.PreferenceManager
import com.trikset.gamepad2.R
import com.trikset.gamepad2.RobolectricTestBase
import com.trikset.gamepad2.SettingsFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ReportSharerTest : RobolectricTestBase() {

  private lateinit var activity: Activity
  private val reportUri =
      Uri.parse("content://com.trikset.gamepad2.fileprovider/diagnostics/probe.md")

  @Before
  fun setUp() {
    activity = Robolectric.buildActivity(Activity::class.java).setup().get()
  }

  private fun startedInnerIntent(): Intent {
    val chooser = shadowOf(activity).nextStartedActivity
    assertNotNull("a chooser must have been started", chooser)
    // The typed getParcelableExtra(String, Class) overload is API 33+; these tests run at
    // minSdk (23), so the deprecated single-arg form is the only one available.
    @Suppress("DEPRECATION")
    return chooser.getParcelableExtra(Intent.EXTRA_INTENT)!!
  }

  @Test
  fun editingPathOpensTextEditorWithFileUri() {
    ReportSharer.share(activity, "report", reportUri, editorAvailable = true)

    val inner = startedInnerIntent()
    assertEquals(Intent.ACTION_EDIT, inner.action)
    assertEquals("text/plain", inner.type)
    assertEquals(reportUri, inner.data)
  }

  @Test
  fun editingChooserTitleGuidesToReview() {
    ReportSharer.share(activity, "report", reportUri, editorAvailable = true)

    val chooser = shadowOf(activity).nextStartedActivity
    assertEquals(
        activity.getString(R.string.report_editor_chooser_title),
        chooser.getStringExtra(Intent.EXTRA_TITLE),
    )
  }

  @Test
  fun directSharePathUsesSendSheetWithAttachment() {
    PreferenceManager.getDefaultSharedPreferences(activity)
        .edit()
        .putBoolean(SettingsFragment.SK_SHARE_WITHOUT_EDITING, true)
        .commit()
    ReportSharer.share(activity, "report", reportUri, editorAvailable = true)

    val inner = startedInnerIntent()
    assertEquals(Intent.ACTION_SEND, inner.action)
    assertEquals("text/plain", inner.type)
    // The typed getParcelableExtra(String, Class) overload is API 33+; these tests run at
    // minSdk (23), so the deprecated single-arg form is the only one available.
    @Suppress("DEPRECATION")
    assertEquals(reportUri, inner.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    assertEquals("report", inner.getStringExtra(Intent.EXTRA_TEXT))
    assertEquals(
        activity.getString(R.string.report_subject),
        inner.getStringExtra(Intent.EXTRA_SUBJECT),
    )
  }

  @Test
  fun noEditorFallsBackToShareSheet() {
    ReportSharer.share(activity, "report", reportUri, editorAvailable = false)

    val inner = startedInnerIntent()
    assertEquals(Intent.ACTION_SEND, inner.action)
  }
}
