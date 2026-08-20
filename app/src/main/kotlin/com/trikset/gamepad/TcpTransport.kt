package com.trikset.gamepad

import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * [CommandTransport] over one persistent TCP connection (the current robot protocol: one socket,
 * newline-terminated plain-text commands, default `192.168.77.1:4444`). The socket setup and the
 * write/error-check are the pieces moved out of [SenderService] so the transport contract is shared
 * with a future UDP transport. The socket is bound by [SocketBinder] (Wi-Fi preference, A2) before
 * it connects.
 */
class TcpTransport(
    private val socketBinder: SocketBinder = SocketBinder.identity,
) : CommandTransport {

  private var writer: PrintWriter? = null
  private var socket: Socket? = null

  // TCP control is write-only (input half-closed): the robot never replies on the control socket,
  // so there are no inbound messages to report (the protocol doc: the app never reads it).
  override var onMessage: ((String) -> Unit)? = null

  @Throws(IOException::class)
  override fun open(host: String, port: Int) {
    val socket = socketBinder.bind(Socket())
    socket.connect(InetSocketAddress(host, port), TIMEOUT)
    socket.tcpNoDelay = true
    socket.keepAlive = true
    socket.setSoLinger(true, 0)
    socket.trafficClass = TRAFFIC_CLASS // high priority, no-delay
    socket.oobInline = true
    // The app is write-only on the control channel: the robot never replies, so the input half is
    // closed (the app detects a dead connection via write errors / Wi-Fi drop, never by reading).
    socket.shutdownInput()
    this.socket = socket
    writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
  }

  override fun send(command: String): Boolean {
    val writer = writer ?: return false
    writer.println(command)
    return !writer.checkError()
  }

  override fun close() {
    // The PrintWriter.close() also closes the socket.
    writer?.close()
    writer = null
    socket = null
  }

  private companion object {
    const val TIMEOUT = 5000
    const val TRAFFIC_CLASS = 0x0F
  }
}
