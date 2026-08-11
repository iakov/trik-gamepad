package com.trikset.gamepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Direct tests for [ConnectionAnnouncer]: state→text mapping, target gating, repeat dedup. */
@RunWith(RobolectricTestRunner::class)
class ConnectionAnnouncerTest : RobolectricTestBase() {

  private val context = org.robolectric.RuntimeEnvironment.getApplication()

  private fun announcer(targetConfigured: Boolean = true): ConnectionAnnouncer =
      ConnectionAnnouncer(context) { targetConfigured }

  @Test
  fun textForShouldMapConnecting() {
    assertEquals("Connecting…", announcer().textFor(ConnectionState.Connecting))
  }

  @Test
  fun textForShouldMapConnected() {
    assertEquals("Connected", announcer().textFor(ConnectionState.Connected))
  }

  @Test
  fun textForShouldMapDisconnectedWithTarget() {
    assertEquals("Tap to connect…", announcer().textFor(ConnectionState.Disconnected("x")))
  }

  @Test
  fun textForShouldBeSilentForDisconnectedWithoutTarget() {
    assertNull(announcer(targetConfigured = false).textFor(ConnectionState.Disconnected("x")))
  }

  @Test
  fun nextAnnouncementShouldDedupeConsecutiveRepeats() {
    val a = announcer()
    assertEquals("Connected", a.nextAnnouncement(ConnectionState.Connected))
    assertNull(
        "a repeat of the same state must be silent",
        a.nextAnnouncement(ConnectionState.Connected),
    )
  }

  @Test
  fun nextAnnouncementShouldAnnounceAgainAfterStateChanges() {
    val a = announcer()
    a.nextAnnouncement(ConnectionState.Connected)
    assertEquals("Connecting…", a.nextAnnouncement(ConnectionState.Connecting))
    assertEquals("Connected", a.nextAnnouncement(ConnectionState.Connected))
  }

  @Test
  fun nextAnnouncementShouldStaySilentWithoutTarget() {
    val a = announcer(targetConfigured = false)
    assertNull(a.nextAnnouncement(ConnectionState.Disconnected("x")))
  }
}
