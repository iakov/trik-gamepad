package com.trikset.gamepad

import android.os.Handler
import android.os.Looper
import com.trikset.gamepad.diagnostics.AppLog
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Maintains the control channel to the robot and sends newline-terminated plain-text commands (`pad
 * 1 x y`, `btn N down`, `wheel <angle>`, `keepalive <ms>`). The transport ([CommandTransport], a
 * persistent TCP connection today via [TcpTransport]) is injected via [transportFactory] so the
 * protocol is independent of the socket.
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
    private val transportFactory: () -> CommandTransport = { TcpTransport() },
) {

  fun interface OnEventListener<ArgT> {
    fun onEvent(arg: ArgT)
  }

  private val syncFlag = Any()
  private var executor: Executor = executor
  var keepaliveTimeout: Int = initialKeepaliveTimeout
    set(value) {
      if (value != field) {
        field = value
        keepAliveTimer.restart()
      }
    }

  private var connectTask: Runnable? = null
  // internal (not private) so ConnectRunnable / KeepAliveTimer can reach them.
  internal val mainHandler = Handler(Looper.getMainLooper())
  internal var showTextCallback: OnEventListener<String>? = null
  internal var onDisconnectedListener: OnEventListener<String>? = null
  internal var transport: CommandTransport? = null
  var hostAddr: String? = null
    private set

  var hostPort: Int = 0
    private set

  private val keepAliveTimer = KeepAliveTimer(this, keepAliveScheduler)
  private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected(""))
  val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

  // Listener-registration setters (Android style, like setOnClickListener): Kotlin does not
  // SAM-convert a lambda assigned to a fun-interface *property*, so these stay methods.
  fun setShowTextCallback(showTextCallback: OnEventListener<String>?) {
    this.showTextCallback = showTextCallback
  }

  fun setOnDisconnectedListener(onDisconnectedListener: OnEventListener<String>?) {
    this.onDisconnectedListener = onDisconnectedListener
  }

  private fun connectAsync() {
    synchronized(syncFlag) {
      if (connectTask != null) {
        return
      }
      _connectionState.value = ConnectionState.Connecting
      val task = ConnectRunnable(this)
      connectTask = task
      executor.execute(task)
    }
  }

  // socket is closed from CommandTransport.close()
  internal fun connectToTRIK() {
    synchronized(syncFlag) {
      try {
        AppLog.i(TCP_TAG, "Connecting to $hostAddr:$hostPort")
        val transport = transportFactory()
        transport.open(hostAddr.orEmpty(), hostPort)
        this.transport = transport
        keepAliveTimer.restart()
        _connectionState.value = ConnectionState.Connected
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
        "Connection to $hostAddr:$hostPort" + if (transport != null) " established." else " error."
    )
    connectTask = null
  }

  internal fun postCommand(command: String) {
    executor.execute {
      var sendFailed = false
      synchronized(syncFlag) {
        val transport = transport
        if (transport == null || !transport.send(command)) {
          sendFailed = true
        }
      }
      if (sendFailed) {
        AppLog.e(TCP_TAG, "NotSent: $command")
        mainHandler.post { disconnect("Send failed.") }
      }
    }
  }

  fun disconnect(reason: String) {
    keepAliveTimer.stop()
    val transport = transport
    if (transport != null) {
      transport.close()
      this.transport = null
      AppLog.i(TCP_TAG, "Disconnected.")
      onDisconnectedListener?.onEvent(reason)
      _connectionState.value = ConnectionState.Disconnected(reason)
    }
  }

  fun send(command: String) {
    if (transport == null) {
      connectAsync() // synchronized on the same object as postCommand
    }
    AppLog.d(TCP_TAG, "Sending '$command'")
    postCommand(command)
    keepAliveTimer.restart()
  }

  /**
   * Establishes the connection without sending a command (the "tap to connect" entry point). No-ops
   * when no target is configured (a blank host is a valid video-only configuration — there is
   * nothing to connect to).
   */
  fun connect() {
    if (transport == null && !hostAddr.isNullOrBlank()) {
      connectAsync()
    }
  }

  fun setTarget(hostAddr: String, hostPort: Int) {
    if (!hostAddr.equals(this.hostAddr, ignoreCase = true) || this.hostPort != hostPort) {
      disconnect("Target changed.")
    }
    this.hostAddr = hostAddr
    this.hostPort = hostPort
  }

  companion object {
    const val DEFAULT_KEEPALIVE = 5000
    const val MINIMAL_KEEPALIVE = 1000

    private const val TCP_TAG = "TCP"
  }
}
