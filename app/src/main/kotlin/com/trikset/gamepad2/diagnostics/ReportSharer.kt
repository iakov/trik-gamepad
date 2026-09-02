package com.trikset.gamepad2.diagnostics

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.trikset.gamepad2.R
import com.trikset.gamepad2.SettingsFragment

/**
 * Launches the report-sharing flow. The default path opens the report file in a **text editor** so
 * the user can review/edit it before sharing (mail/IM) from the editor's own share menu; the
 * chooser title states exactly that. With the "Share without editing" switch set — or when no
 * editor handles the file — it falls back to a direct share sheet with the file attached unchanged.
 */
object ReportSharer {

  fun share(context: Context, reportText: String, crash: Boolean = false) {
    val file = ReportDiagnosticsWriter.write(context, reportText)
    val uri =
        FileProvider.getUriForFile(
            context,
            ReportDiagnosticsWriter.fileProviderAuthority(context),
            file,
        )
    share(context, reportText, uri, editorAvailable = hasEditHandler(context), crash = crash)
  }

  internal fun share(
      context: Context,
      reportText: String,
      uri: Uri,
      editorAvailable: Boolean,
      crash: Boolean = false,
  ) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val shareWithoutEditing = prefs.getBoolean(SettingsFragment.SK_SHARE_WITHOUT_EDITING, true)
    val sendDirect = shareWithoutEditing || !editorAvailable
    val intent =
        if (sendDirect) {
          sendIntent(context, reportText, uri, crash)
        } else {
          Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(uri, "text/plain")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
        }
    val title =
        context.getString(
            if (sendDirect) R.string.share_report else R.string.report_editor_chooser_title
        )
    context.startActivity(Intent.createChooser(intent, title))
  }

  internal fun hasEditHandler(context: Context): Boolean {
    val probe =
        Intent(Intent.ACTION_EDIT).apply {
          setDataAndType(
              "content://${ReportDiagnosticsWriter.fileProviderAuthority(context)}/probe.md"
                  .toUri(),
              "text/plain",
          )
        }
    return context.packageManager.queryIntentActivities(probe, 0).isNotEmpty()
  }

  private fun sendIntent(context: Context, reportText: String, uri: Uri, crash: Boolean): Intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_EMAIL, arrayOf(ReportShareContent.SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_TEXT, ReportShareContent.messageBody(reportText, crash))
        putExtra(Intent.EXTRA_SUBJECT, ReportShareContent.subject(crash))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
}
