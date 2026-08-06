package com.trikset.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.rule.ActivityTestRule;
import java.util.Iterator;
import java.util.ListIterator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class KeepAliveTests {
  @Rule
  public final ActivityTestRule<MainActivity> mActivityTestRule =
      new ActivityTestRule<>(MainActivity.class);

  @Test
  public void keepAliveShouldBeReceivedAfterGivenTimePeriod() throws InterruptedException {
    final int timeout = 1000;

    DummyServer server = new DummyServer();
    SenderService client = mActivityTestRule.getActivity().getSenderService();

    client.setTarget(DummyServer.IP, DummyServer.DEFAULT_PORT);
    client.setKeepaliveTimeout(timeout);
    // In order to set connection up
    client.send("test");

    // Wait for the keepalive to arrive instead of guessing at a sleep budget;
    // the connect + first keepalive cycle can outlive a fixed Thread.sleep on a
    // slow emulator (see TESTING.md "Why awaits are required").
    assertTrue(server.awaitMessage(String.format("keepalive %d", timeout), 30000));
    server.stopListening();

    ListIterator<String> messages = server.getReceivedMessages().listIterator();
    while (messages.hasNext()) {
      messages.next();
    }
    assertEquals(String.format("keepalive %d", timeout), messages.previous());
  }

  @Test
  public void keepaliveMessagesShouldNotBeSentAfterDisconnect() throws InterruptedException {
    DummyServer server = new DummyServer();
    SenderService client = mActivityTestRule.getActivity().getSenderService();
    client.setTarget(DummyServer.IP, DummyServer.DEFAULT_PORT);
    client.setKeepaliveTimeout(2000);

    // In order to set connection up
    client.send("testtest");
    assertTrue(server.awaitMessage("testtest", 30000));
    client.disconnect("testtest");

    // Give any (incorrectly scheduled) keepalive a chance to appear.
    Thread.sleep(2000);
    server.stopListening();

    Iterator<String> messages = server.getReceivedMessages().iterator();
    assertTrue(messages.hasNext());
    assertEquals("testtest", messages.next());
    assertFalse(messages.hasNext());
  }
}
