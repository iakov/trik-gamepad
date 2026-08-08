package com.trikset.gamepad

import android.os.Handler
import android.os.Looper
import android.util.Log
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
) {

  fun load(url: URL?) {
    executor.execute {
      val stream = openStream(url)
      mainHandler.post {
        view.stopPlayback()
        view.setSource(stream)
        view.startPlayback()
      }
    }
  }

  internal fun openStream(url: URL?): MjpegInputStream? {
    if (url == null) return null
    return try {
      val connection = url.openConnection() as HttpURLConnection
      connection.connectTimeout = CONNECT_TIMEOUT_MS
      connection.readTimeout = READ_TIMEOUT_MS
      val stream = MjpegInputStream(connection.inputStream)
      Log.i("JPGReader", "Restarted connection.")
      stream
    } catch (e: IOException) {
      Log.e("JPGReader", "Failed to open MJPEG stream.", e)
      null
    }
  }

  private companion object {
    const val CONNECT_TIMEOUT_MS = 5000
    const val READ_TIMEOUT_MS = 5000
  }
}
