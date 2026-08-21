package com.trikset.gamepad2

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Activity-scoped owner of the [SenderService]. Survives configuration changes so the connection
 * and keepalive timer are not torn down and rebuilt on rotation, and closes the socket in
 * [onCleared] instead of relying on the Activity's onDestroy. Settings already live in
 * SharedPreferences, so a process death re-derives the target for free. No DI framework: the
 * service is created here and injected into the views by [MainActivity]. The [AndroidViewModel]
 * [Application] gives the [SenderService] the `ACCESS_NETWORK_STATE`-backed [WifiSocketBinder] so
 * sockets route over the robot's Wi-Fi even when cellular is the system default network (A2).
 */
class SenderViewModel(application: Application) : AndroidViewModel(application) {

  var sender: SenderService =
      SenderService(
          transportFactory = { mode ->
            when (mode) {
              TransportMode.TCP -> TcpTransport(socketBinder = WifiSocketBinder(application))
              TransportMode.UDP -> UdpTransport(datagramBinder = WifiDatagramBinder(application))
            }
          }
      )
    internal set

  val connectionState: StateFlow<ConnectionState> = sender.connectionState

  override fun onCleared() {
    sender.disconnect("ViewModel cleared")
    super.onCleared()
  }
}
