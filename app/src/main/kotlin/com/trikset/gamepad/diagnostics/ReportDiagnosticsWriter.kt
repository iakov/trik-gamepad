package com.trikset.gamepad.diagnostics

import android.content.Context
import java.io.File

/**
 * Writes the diagnostic report to the app's cache as a single markdown file so it can be opened in
 * a text editor (for review/edit) or shared as an attachment. The file lives in a dedicated
 * `cacheDir/diagnostics` directory so it can be exposed through the FileProvider paths without
 * granting anything broader; it is ephemeral and regenerated on every tap.
 */
object ReportDiagnosticsWriter {
  const val DIAGNOSTICS_DIR = "diagnostics"

  fun write(context: Context, reportText: String): File {
    val dir = File(context.cacheDir, DIAGNOSTICS_DIR)
    if (!dir.exists()) {
      dir.mkdirs()
    }
    val file = File(dir, "trik-gamepad-report-${System.currentTimeMillis()}.md")
    file.writeText(reportText, Charsets.UTF_8)
    return file
  }

  fun fileProviderAuthority(context: Context): String = context.packageName + ".fileprovider"
}
