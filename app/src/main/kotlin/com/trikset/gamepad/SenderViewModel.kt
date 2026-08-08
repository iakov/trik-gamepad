package com.trikset.gamepad

import androidx.lifecycle.ViewModel

/**
 * Activity-scoped owner of the [SenderService] (Campaign 3 P2). Survives configuration changes so
 * the TCP connection and keepalive timer are not torn down and rebuilt on rotation, and closes the
 * socket in [onCleared] instead of relying on the Activity's onDestroy. Settings already live in
 * SharedPreferences, so a process death re-derives the target for free. No DI framework: the
 * service is created here and injected into the views by [MainActivity].
 */
class SenderViewModel : ViewModel() {

  var sender: SenderService = SenderService()
    internal set

  override fun onCleared() {
    sender.disconnect("ViewModel cleared")
    super.onCleared()
  }
}
