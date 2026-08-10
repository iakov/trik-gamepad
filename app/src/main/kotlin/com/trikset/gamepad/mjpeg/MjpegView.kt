package com.trikset.gamepad.mjpeg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.io.IOException
import org.apache.commons.io.input.BoundedInputStream

class MjpegView : SurfaceView, SurfaceHolder.Callback {

  /** Invoked from the render thread when the MJPEG stream fails (e.g. socket closed). */
  fun interface OnStreamErrorListener {
    fun onStreamError()
  }

  /** Invoked from the render thread once per playback cycle when the first frame is decoded. */
  fun interface OnFirstFrameListener {
    fun onFirstFrame()
  }

  private val fpsTextPaint = Paint()
  private var viewThread: MjpegViewThread? = null
  @Volatile private var input: MjpegInputStream? = null
  @Volatile private var running = false
  @Volatile private var stopping = false
  @Volatile private var surfaceDone = false
  @Volatile private var dispWidth = 0
  @Volatile private var dispHeight = 0
  private var onStreamErrorListener: OnStreamErrorListener? = null
  private var onFirstFrameListener: OnFirstFrameListener? = null
  @Volatile private var frameReported = false

  constructor(context: Context) : super(context) {
    init()
  }

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
    init()
  }

  fun setOnStreamErrorListener(listener: OnStreamErrorListener?) {
    onStreamErrorListener = listener
  }

  fun setOnFirstFrameListener(listener: OnFirstFrameListener?) {
    onFirstFrameListener = listener
    frameReported = false
  }

  private fun init() {
    holder.addCallback(this)
    viewThread = MjpegViewThread()
    // Decorative video surface: never focusable (the XML sets focusable=false; the code must not
    // contradict it, or TalkBack/keyboard navigation stop on a surface with no interaction).
    isFocusable = false
    isFocusableInTouchMode = false
    fpsTextPaint.textAlign = Paint.Align.RIGHT
    fpsTextPaint.textSize = FPS_TEXT_SIZE
    fpsTextPaint.typeface = Typeface.DEFAULT
    fpsTextPaint.color = Color.WHITE
    dispWidth = width
    dispHeight = height
  }

  fun setSource(source: MjpegInputStream?) {
    input = source
  }

  /** True while the render thread is consuming frames (started and not stopped). */
  fun isPlaying(): Boolean = running

  @Synchronized
  fun startPlayback() {
    if (input != null) {
      running = true
      frameReported = false
      viewThread?.start()
    }
  }

  @Synchronized
  fun stopPlayback() {
    if (running) {
      running = false
      stopping = true
      // The render thread may be blocked in a non-interruptible
      // InputStream.read inside readMjpegFrame(); close() from this thread
      // unblocks it (the documented unblock pattern for a blocked read) so
      // the join below returns instead of timing out and leaking the thread
      // and its HTTP connection across pause/resume cycles.
      val current = input
      if (current != null) {
        try {
          current.close()
        } catch (e: IOException) {
          Log.e(TAG, "Failed to close MJPEG stream on stop", e)
        }
      }
      viewThread?.join()
      stopping = false
    }
  }

  override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
    viewThread?.setSurfaceSize(w, h)
  }

  override fun surfaceCreated(holder: SurfaceHolder) {
    surfaceDone = true
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    surfaceDone = false
    stopPlayback()
  }

  private companion object {
    const val FPS_TEXT_SIZE = 12f
    const val JOIN_TIMEOUT_MS = 3000L
    const val TAG = "MjpegView"
  }

  private inner class MjpegViewThread {
    private var thread: MjpegRenderThread? = null

    fun join() {
      val current = thread ?: return
      var retry = true
      while (retry) {
        try {
          current.join(JOIN_TIMEOUT_MS)
          retry = false
        } catch (e: InterruptedException) {
          Log.e(javaClass.simpleName, Log.getStackTraceString(e))
        }
      }
      thread = null
    }

    fun setSurfaceSize(width: Int, height: Int) {
      dispWidth = width
      dispHeight = height
    }

    fun start() {
      join()
      thread = MjpegRenderThread()
      thread?.start()
    }
  }

  private inner class MjpegRenderThread : Thread() {
    private val renderer = MjpegFrameRenderer()

    override fun run() {
      renderer.onRenderStarted(System.currentTimeMillis())
      while (running) {
        if (surfaceDone) {
          renderNextFrame()
        }
      }
    }

    @Suppress("SwallowedException") // a broken stream stops the loop and reports the error
    private fun renderNextFrame() {
      var canvas: Canvas? = null
      var frame: BoundedInputStream? = null
      try {
        val stream = input?.readMjpegFrame()
        if (stream != null) {
          frame = stream
          val destRect = renderer.extractFrame(stream, dispWidth, dispHeight)
          if (destRect != null) {
            // A frame is decoded: report the first one of this playback cycle
            // (the loading indicator hides here, before the canvas draw).
            if (!frameReported) {
              frameReported = true
              onFirstFrameListener?.onFirstFrame()
            }
            canvas = holder.lockCanvas()
            if (canvas != null) {
              renderer.drawFrame(canvas, destRect, dispWidth, fpsTextPaint)
            }
          }
        }
      } catch (e: IOException) {
        running = false
        if (!stopping) {
          onStreamErrorListener?.onStreamError()
        }
      } finally {
        if (canvas != null) {
          holder.unlockCanvasAndPost(canvas)
        }
        if (frame != null) {
          try {
            frame.close()
          } catch (e: IOException) {
            running = false
          }
        }
      }
    }
  }
}
