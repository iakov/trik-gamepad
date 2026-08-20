package com.trikset.gamepad

import java.io.IOException

/**
 * One control-channel transport to the robot. Extracted from [SenderService] so the command
 * protocol (DESIGN.md "Gamepad protocol (source of truth)") is independent of the underlying
 * socket: [TcpTransport] is the persistent TCP stream today; a UDP transport shares the same
 * [CommandTransport] contract.
 *
 * The send/error check must run on the caller's executor thread, never the main thread — the old
 * TCP path flushed the `PrintWriter` (a socket write) from a `mainHandler.post` block, measured as
 * the C24 "main thread 30% socket I/O".
 */
interface CommandTransport {

  /** Opens the connection. [IOException] propagates to the caller (drives `Disconnected`). */
  @Throws(IOException::class) fun open(host: String, port: Int)

  /**
   * Sends one command (newline-terminated). Returns `false` when the send failed so the caller can
   * drive a disconnect; `true` otherwise.
   */
  fun send(command: String): Boolean

  /** Closes the transport and its underlying socket. */
  fun close()
}
