package com.trikset.gamepad

import android.os.Handler
import android.os.Looper
import com.trikset.gamepad.diagnostics.AppLog
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Maintains one TCP connection to the robot and sends newline-terminated plain-text commands (`pad1
 * x y`, `btn N down`, `wheel <angle>`, `keepalive <ms>`).
 *
 * All network/keepalive collaborators are injected via the constructor (defaults preserved) so
 * tests substitute a
 * [PausedExecutorService][org.robolectric.android.util.concurrent.PausedExecutorService] for
 * [executor] without touching static state (the former `@JvmField` statics
 * `mExecutor`/`keepaliveTimeout`/`mConnectTask` are gone). The connection and keepalive helpers
 * live in [ConnectRunnable] / [KeepAliveTimer].
 */
class SenderService(
    executor: Executor = Executors.newSingleThreadExecutor(),
    initialKeepaliveTimeout: Int = DEFAULT_KEEPALIVE,
    keepAliveScheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
          Thread(runnable, "SenderServiceKeepAlive").apply { isDaemon = true }
        },
) {

  fun interface OnEventListener<ArgT> {
    fun onEvent(arg: ArgT)
  }

  private val syncFlag = Any()
  private var executor: Executor = executor
  private var keepaliveTimeout: Int = initialKeepaliveTimeout
  private var mConnectTask: Runnable? = null
  // internal (not private) so ConnectRunnable / KeepAliveTimer can reach them.
  internal val mainHandler = Handler(Looper.getMainLooper())
  internal var showTextCallback: OnEventListener<String>? = null
  internal var onDisconnectedListener: OnEventListener<String>? = null
  internal var mOut: PrintWriter? = null
  private var mHostAddr: String? = null
  private var mHostPort = 0
  private val keepAliveTimer = KeepAliveTimer(this, keepAliveScheduler)
  private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected(""))
  val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

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
      _connectionState.value = ConnectionState.Connecting
      val task = ConnectRunnable(this)
      mConnectTask = task
      executor.execute(task)
    }
  }

  // socket is closed from PrintWriter.close()
  @Suppress("TooGenericExceptionCaught") // keep the Java recovery cleanup around the PrintWriter
  internal fun connectToTRIK() {
    synchronized(syncFlag) {
      try {
        AppLog.i(TCP_TAG, "Connecting to $mHostAddr:$mHostPort")
        val socket = Socket()
        socket.connect(InetSocketAddress(mHostAddr, mHostPort), TIMEOUT)
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.setSoLinger(true, 0)
        socket.trafficClass = TRAFFIC_CLASS // high priority, no-delay
        socket.oobInline = true
        socket.shutdownInput()
        val osw = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
        keepAliveTimer.restart()
        try {
          mOut = PrintWriter(osw, true)
          _connectionState.value = ConnectionState.Connected
        } catch (e: Exception) {
          AppLog.e(TCP_TAG, "GetStream: Error", e)
          socket.close()
          osw.close()
          // Failed to finish the handshake: leave the state machine at Disconnected, not stuck at
          // Connecting (a later connect attempt retries cleanly).
          _connectionState.value = ConnectionState.Disconnected("")
        }
      } catch (e: IOException) {
        AppLog.e(TCP_TAG, "Connect: Error", e)
        // A refused/unresolvable target must not leave the pill stuck at "Connecting…".
        // Empty reason -> MainActivity does not double-notify (the "Connection to X error."
        // Snackbar from onConnectionFinished is the single notification).
        _connectionState.value = ConnectionState.Disconnected("")
      }
    }
  }

  internal fun onConnectionFinished() {
    showTextCallback?.onEvent(
        "Connection to $mHostAddr:$mHostPort" + if (mOut != null) " established." else " error."
    )
    mConnectTask = null
  }

  internal fun postCommand(command: String) {
    executor.execute {
      synchronized(syncFlag) {
        mOut?.println(command)
      }
      mainHandler.post {
        val out = mOut
        if (out == null || out.checkError()) {
          AppLog.e(TCP_TAG, "NotSent: $command")
          disconnect("Send failed.")
        }
      }
    }
  }

  fun disconnect(reason: String) {
    keepAliveTimer.stop()
    val out = mOut
    if (out != null) {
      out.close()
      mOut = null
      AppLog.i(TCP_TAG, "Disconnected.")
      onDisconnectedListener?.onEvent(reason)
      _connectionState.value = ConnectionState.Disconnected(reason)
    }
  }

  fun getHostAddr(): String? = mHostAddr

  fun send(command: String) {
    if (mOut == null) {
      connectAsync() // synchronized on the same object as postCommand
    }
    AppLog.d(TCP_TAG, "Sending '$command'")
    postCommand(command)
    keepAliveTimer.restart()
  }

  /**
   * Establishes the TCP connection without sending a command (the "tap to connect" entry point).
   * No-ops when no target is configured (a blank host is a valid video-only configuration — there
   * is nothing to connect to).
   */
  fun connect() {
    if (mOut == null && !mHostAddr.isNullOrBlank()) {
      connectAsync()
    }
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
      keepaliveTimeout = timeout
      keepAliveTimer.restart()
    }
  }

  fun getKeepaliveTimeout(): Int = keepaliveTimeout

  companion object {
    const val DEFAULT_KEEPALIVE = 5000
    const val MINIMAL_KEEPALIVE = 1000
    const val TIMEOUT = 5000

    private const val TRAFFIC_CLASS = 0x0F
    private const val TCP_TAG = "TCP"
  }
}
