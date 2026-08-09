package com.trikset.gamepad

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK])
class SenderViewModelTest {

  @Test
  fun onClearedDisconnectsTheSenderWithoutThrowing() {
    val viewModel = SenderViewModel()
    val sender = SenderService()
    viewModel.sender = sender
    assertTrue(viewModel.connectionState.value is ConnectionState.Disconnected)
    // onCleared is the protected socket-close path (ViewModel contract); it must
    // run the disconnect without throwing even when nothing is connected.
    SenderViewModel::class
        .java
        .getDeclaredMethod("onCleared")
        .apply { isAccessible = true }
        .invoke(viewModel)
    assertTrue(viewModel.connectionState.value is ConnectionState.Disconnected)
  }
}
