package com.trikset.gamepad

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Schedules the keepalive command on a daemon [ScheduledExecutorService] while the TCP connection
 * is open. Extracted from [SenderService]'s private inner class so the timer plumbing has its own
 * file; it delegates command posting back to the owning [SenderService].
 */
internal class KeepAliveTimer(
    private val sender: SenderService,
    private val scheduler: ScheduledExecutorService,
) {
  private var task: ScheduledFuture<*>? = null

  fun restart() {
    stop()
    // '300' compensates ping
    val realTimeout = sender.getKeepaliveTimeout() - KEEPALIVE_COMPENSATION_MS
    task =
        scheduler.scheduleWithFixedDelay(
            { tick() },
            realTimeout.toLong(),
            realTimeout.toLong(),
            TimeUnit.MILLISECONDS,
        )
  }

  fun stop() {
    task?.cancel(false)
    task = null
  }

  private fun tick() {
    val out = sender.mOut
    if (out != null) {
      val command = "keepalive ${sender.getKeepaliveTimeout()}"
      if (android.util.Log.isLoggable(TCP_TAG, android.util.Log.DEBUG)) {
        android.util.Log.d(TCP_TAG, "Sending $command message")
      }
      sender.postCommand(command)
    } else {
      stop()
    }
  }

  private companion object {
    const val KEEPALIVE_COMPENSATION_MS = 300
    const val TCP_TAG = "TCP"
  }
}
