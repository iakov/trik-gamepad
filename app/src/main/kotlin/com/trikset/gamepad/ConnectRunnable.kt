package com.trikset.gamepad

/**
 * Opens the TCP connection to the robot and posts the connection result to the main thread.
 * Extracted from [SenderService]'s private inner class (ROADMAP Phase B3) so it has its own file;
 * it delegates the socket work and the result notification back to the owning [SenderService].
 */
internal class ConnectRunnable(
    private val sender: SenderService,
) : Runnable {

  override fun run() {
    sender.connectToTRIK()
    sender.mainHandler.post { sender.onConnectionFinished() }
  }
}
