package com.trikset.gamepad.diagnostics

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.trikset.gamepad.ConnectionState
import com.trikset.gamepad.R
import com.trikset.gamepad.SettingsFragment

/**
 * Owns the next-launch crash-report dialog: surfaces a captured crash exactly once, offering to
 * share the report via the text-editor flow (or the direct share sheet when "Share without editing"
 * is set), copy it, or dismiss. Extracted from MainActivity so the dialog logic stays out of the
 * activity and the [connectionState] provider is injectable for tests.
 */
class CrashReportDialog(
    private val activity: Activity,
    private val store: CrashLogStore,
    private val connectionState: () -> ConnectionState?,
) {

  fun showIfNeeded() {
    if (!store.shouldPrompt()) {
      return
    }
    store.markPrompted()
    val crash = store.latest() ?: return
    val shareWithoutEditing =
        PreferenceManager.getDefaultSharedPreferences(activity)
            .getBoolean(SettingsFragment.SK_SHARE_WITHOUT_EDITING, false)
    val reportText = buildReport(crash.stackTrace)
    AlertDialog.Builder(activity)
        .setTitle(R.string.crash_dialog_title)
        .setMessage(R.string.crash_dialog_message)
        .setPositiveButton(
            if (shareWithoutEditing) R.string.share else R.string.review_and_share
        ) { _, _ ->
          ReportSharer.share(activity, reportText)
        }
        .setNeutralButton(android.R.string.copy) { _, _ -> copyReport(reportText) }
        .setNegativeButton(R.string.dismiss, null)
        .show()
  }

  private fun buildReport(crashTrace: String): String {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    return DiagnosticsReport.build(
        activity,
        prefs,
        connectionState(),
        AppLog.tail(AppLog.BUFFER_CAPACITY),
        crashTrace,
    )
  }

  private fun copyReport(reportText: String) {
    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(
        ClipData.newPlainText(activity.getString(R.string.report_subject), reportText)
    )
    Toast.makeText(activity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
  }
}
