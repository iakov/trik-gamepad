package com.trikset.gamepad2

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import java.net.Socket
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork

/**
 * A2: sockets must prefer the robot's Wi-Fi network when one is present (the default network can be
 * cellular), and fall back to the default network otherwise. The network lookup is the injectable
 * [WifiSocketBinder] seam, so these tests supply fixed [Network]s via [ShadowNetwork] instead of
 * faking `ConnectivityManager` capabilities (the `NetworkCapabilities.Builder` class is absent from
 * the SDK stub jars).
 */
@RunWith(RobolectricTestRunner::class)
class SocketBinderTest : RobolectricTestBase() {

  private val context: Context = RuntimeEnvironment.getApplication()

  private fun connectivityManager(): ConnectivityManager =
      context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

  @Test
  fun noWifiProviderFallsBackToTheGivenSocket() {
    val binder = WifiSocketBinder(context) { null }
    val socket = Socket()
    // No Wi-Fi network -> the socket is returned untouched (default-network fallback).
    assertSame(socket, binder.bind(socket))
    socket.close()
  }

  @Test
  fun wifiProviderBindsTheSocketToTheWifiNetwork() {
    val wifi = ShadowNetwork.newInstance(nextNetworkId())
    val binder = WifiSocketBinder(context) { wifi }
    val socket = binder.bind(Socket())
    assertTrue(shadowOf(wifi).isSocketBound(socket))
    socket.close()
  }

  @Test
  fun nullProviderAfterWifiLostLeavesTheSocketUnbound() {
    val wifi = ShadowNetwork.newInstance(nextNetworkId())
    val binder = WifiSocketBinder(context) { null }
    val socket = binder.bind(Socket())
    assertFalse(shadowOf(wifi).isSocketBound(socket))
    socket.close()
  }

  @Test
  fun trackerRegistersACallbackAndTracksWifiAvailability() {
    val binder = WifiSocketBinder(context)
    val callback =
        shadowOf(connectivityManager()).getNetworkCallbacks().first {
          it is ConnectivityManager.NetworkCallback
        }
    val wifi = ShadowNetwork.newInstance(nextNetworkId())

    callback.onAvailable(wifi)
    val bound = binder.bind(Socket())
    assertTrue(shadowOf(wifi).isSocketBound(bound))
    bound.close()

    // onLost for an untracked network must not clear the tracked one.
    callback.onLost(ShadowNetwork.newInstance(nextNetworkId()))
    val stillBound = binder.bind(Socket())
    assertTrue(shadowOf(wifi).isSocketBound(stillBound))
    stillBound.close()

    callback.onLost(wifi)
    val fallback = binder.bind(Socket())
    assertFalse(shadowOf(wifi).isSocketBound(fallback))
    fallback.close()
  }

  private companion object {
    var nextId = 1

    fun nextNetworkId(): Int = nextId++
  }
}
