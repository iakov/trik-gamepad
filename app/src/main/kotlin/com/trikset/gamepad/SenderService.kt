package com.trikset.gamepad

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Maintains one TCP connection to the robot and sends newline-terminated plain-text commands (`pad1
 * x y`, `btn N down`, `wheel <angle>`, `keepalive <ms>`).
 *
 * The two network fields [mExecutor] and [mConnectTask] stay STATIC (as in the Java) so all
 * instances share one network thread; tests inject a
 * [PausedExecutorService][org.robolectric.android.util.concurrent.PausedExecutorService] via
 * [setExecutor] and reset the statics via reflection.
 */
class SenderService {

  fun interface OnEventListener<ArgT> {
    fun onEvent(arg: ArgT)
  }

  private val syncFlag = Any()
  // internal (not private) so the inner network/keepalive classes reach them
  // without synthetic accessors (lint SyntheticAccessor, previously baselined
  // on the Java source).
  internal val mainHandler = Handler(Looper.getMainLooper())
  internal var showTextCallback: OnEventListener<String>? = null
  internal var onDisconnectedListener: OnEventListener<String>? = null
  internal var mOut: PrintWriter? = null
  private var mHostAddr: String? = null
  private var mHostPort = 0
  private val keepAliveTimer = KeepAliveTimer()

  fun setExecutor(executor: Executor?) {
    if (executor != null) {
      mExecutor = executor
    }
  }

  fun setShowTextCallback(showTextCallback: OnEventListener<String>?) {
    this.showTextCallback = showTextCallback
  }

  fun setOnDisconnectedListener(onDisconnectedListener: OnEventListener<String>?) {
    this.onDisconnectedListener = onDisconnectedListener
  }

  private fun connectAsync() {
    synchronized(syncFlag) {
      if (mConnectTask != null) {
        return
      }
      val task = ConnectRunnable()
      mConnectTask = task
      mExecutor?.execute(task)
    }
  }

  // socket is closed from PrintWriter.close()
  @Suppress("TooGenericExceptionCaught") // keep the Java recovery cleanup around the PrintWriter
  internal fun connectToTRIK() {
    synchronized(syncFlag) {
      try {
        Log.e("TCP Client", "C: Connecting...")
        val socket = Socket()
        socket.connect(InetSocketAddress(mHostAddr, mHostPort), TIMEOUT)
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.setSoLinger(true, 0)
        socket.trafficClass = TRAFFIC_CLASS // high priority, no-delay
        socket.oobInline = true
        socket.shutdownInput()
        val osw = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
        keepAliveTimer.restartKeepAliveTimer()
        try {
          mOut = PrintWriter(osw, true)
        } catch (e: Exception) {
          Log.e("TCP", "GetStream: Error", e)
          socket.close()
          osw.close()
        }
      } catch (e: IOException) {
        Log.e("TCP", "Connect: Error", e)
      }
    }
  }

  internal fun postCommand(command: String) {
    mExecutor?.execute {
      synchronized(syncFlag) {
        mOut?.println(command)
      }
      mainHandler.post {
        val out = mOut
        if (out == null || out.checkError()) {
          Log.e("TCP", "NotSent: $command")
          disconnect("Send failed.")
        }
      }
    }
  }

  fun disconnect(reason: String) {
    keepAliveTimer.stopKeepAliveTimer()
    val out = mOut
    if (out != null) {
      out.close()
      mOut = null
      if (Log.isLoggable(TCP_TAG, Log.DEBUG)) {
        Log.d(TCP_TAG, "Disconnected.")
      }
      onDisconnectedListener?.onEvent(reason)
    }
  }

  fun getHostAddr(): String? = mHostAddr

  fun send(command: String) {
    if (mOut == null) {
      connectAsync() // synchronized on the same object as postCommand
    }
    if (Log.isLoggable(TCP_TAG, Log.DEBUG)) {
      Log.d(TCP_TAG, "Sending '$command'")
    }
    postCommand(command)
    keepAliveTimer.restartKeepAliveTimer()
  }

  fun setTarget(hostAddr: String, hostPort: Int) {
    if (!hostAddr.equals(mHostAddr, ignoreCase = true) || mHostPort != hostPort) {
      disconnect("Target changed.")
    }
    mHostAddr = hostAddr
    mHostPort = hostPort
  }

  fun setKeepaliveTimeout(timeout: Int) {
    if (timeout != keepaliveTimeout) {
      keepAliveTimer.restartKeepAliveTimer()
      keepaliveTimeout = timeout
    }
  }

  fun getKeepaliveTimeout(): Int = keepaliveTimeout

  private inner class ConnectRunnable : Runnable {
    override fun run() {
      connectToTRIK()
      mainHandler.post {
        showTextCallback?.onEvent(
            "Connection to $mHostAddr:$mHostPort" + if (mOut != null) " established." else " error."
        )
        mConnectTask = null
      }
    }
  }

  private inner class KeepAliveTimer : Timer() {
    private var task = KeepAliveTimerTask()

    fun restartKeepAliveTimer() {
      stopKeepAliveTimer()
      task = KeepAliveTimerTask()
      // '300' compensates ping
      val realTimeout = keepaliveTimeout - KEEPALIVE_COMPENSATION_MS
      schedule(task, realTimeout.toLong(), realTimeout.toLong())
    }

    fun stopKeepAliveTimer() {
      task.cancel()
      purge()
    }

    private inner class KeepAliveTimerTask : TimerTask() {
      override fun run() {
        val out = mOut
        if (out != null) {
          val command = "keepalive $keepaliveTimeout"
          if (Log.isLoggable(TCP_TAG, Log.DEBUG)) {
            Log.d(TCP_TAG, "Sending $command message")
          }
          postCommand(command)
        } else {
          stopKeepAliveTimer()
        }
      }
    }
  }

  companion object {
    const val DEFAULT_KEEPALIVE = 5000
    const val MINIMAL_KEEPALIVE = 1000
    const val TIMEOUT = 5000

    private const val TRAFFIC_CLASS = 0x0F
    private const val KEEPALIVE_COMPENSATION_MS = 300
    private const val TCP_TAG = "TCP"

    @JvmField var mExecutor: Executor? = Executors.newSingleThreadExecutor()
    @JvmField var keepaliveTimeout: Int = DEFAULT_KEEPALIVE
    @JvmField var mConnectTask: Runnable? = null
  }
}
