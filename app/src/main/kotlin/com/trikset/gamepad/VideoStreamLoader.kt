package com.trikset.gamepad

import android.os.Handler
import android.os.Looper
import com.trikset.gamepad.diagnostics.AppLog
import com.trikset.gamepad.mjpeg.MjpegInputStream
import com.trikset.gamepad.mjpeg.MjpegView
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Opens an MJPEG HTTP stream and wires it into an [MjpegView], off the main thread (the AsyncTask
 * successor). The [executor] and [mainHandler] are injectable so tests can drive it
 * deterministically without real sockets.
 */
class VideoStreamLoader
constructor(
    private val view: MjpegView,
    private val executor: Executor = Executors.newSingleThreadExecutor(),
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val socketBinder: SocketBinder = WifiSocketBinder(view.context),
) {

  /**
   * Opens the stream and wires it into the view. [onResult] is invoked on the main thread once the
   * attempt settles: `true` when a stream was opened and playback started, `false` when the URL was
   * null or the stream could not be opened (the retry loop keys off this — today a failed open was
   * silent, so the video never recovered after the robot came back into range).
   */
  fun load(url: URL?, onResult: (Boolean) -> Unit = {}) {
    executor.execute {
      val stream = openStream(url)
      mainHandler.post {
        view.stopPlayback()
        view.setSource(stream)
        view.startPlayback()
        onResult(stream != null)
      }
    }
  }

  internal fun openStream(url: URL?): MjpegInputStream? {
    if (url == null) return null
    return try {
      // Plain-HTTP MJPEG streams go over a raw socket so any user-configured
      // robot host works regardless of the NSC cleartext whitelist.
      val stream =
          if (url.protocol.equals("http", ignoreCase = true)) {
            RawSocketHttpStream.open(url, socketBinder)
          } else {
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.inputStream
          }
      val mjpeg = MjpegInputStream(stream)
      AppLog.i(TAG, "Restarted connection.")
      mjpeg
    } catch (e: IOException) {
      AppLog.e(TAG, "Failed to open MJPEG stream.", e)
      null
    }
  }

  private companion object {
    const val CONNECT_TIMEOUT_MS = 5000
    const val READ_TIMEOUT_MS = 5000
    const val TAG = "JPGReader"
  }
}
