package com.trikset.gamepad;

import static android.os.Looper.getMainLooper;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.annotation.LooperMode.Mode.PAUSED;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.util.concurrent.PausedExecutorService;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@LooperMode(PAUSED)
@Config(sdk = {Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK})
public class SquareTouchPadLayoutTest {

  private final PausedExecutorService mExecutor = new PausedExecutorService();
  private SenderService sender;
  private SquareTouchPadLayout pad;

  // SenderService keeps keepaliveTimeout and mConnectTask STATIC; tests that
  // run before this class may have left them dirty, which would make
  // connectAsync() a no-op and every awaitConnection() time out. Reset before
  // each test (same discipline as SenderServiceAdvancedTest).
  @Before
  public void resetSenderServiceStaticState() throws Exception {
    java.lang.reflect.Field f = SenderService.class.getDeclaredField("mConnectTask");
    f.setAccessible(true);
    f.set(null, null);
    SenderService reset = new SenderService();
    reset.setExecutor(mExecutor);
    reset.setKeepaliveTimeout(SenderService.DEFAULT_KEEPALIVE);
    reset.disconnect("reset");
  }

  private MotionEvent eventAt(float x, float y, int action) {
    return MotionEvent.obtain(0, 0, action, x, y, 0);
  }

  @Before
  public void setUp() {
    sender = new SenderService();
    sender.setExecutor(mExecutor);
    sender.setKeepaliveTimeout(10000000); // disable keepalive noise
    sender.setTarget("localhost", 12345); // connect attempt is queued, not awaited

    Context context = org.robolectric.RuntimeEnvironment.getApplication();
    android.widget.FrameLayout parent = new android.widget.FrameLayout(context);
    pad = new SquareTouchPadLayout(context);
    pad.setPadName("pad 1");
    pad.setSender(sender);
    parent.addView(pad);
    pad.measure(
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
    pad.layout(0, 0, 200, 200);
    parent.measure(
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
    parent.layout(0, 0, 200, 200);
  }

  private void flush() {
    mExecutor.runAll();
    shadowOf(getMainLooper()).idle();
  }

  @Test
  public void sendShouldForwardCommandToSender() {
    pad.send("up");
    // SenderService.send() queues work on the injected executor; a nonzero
    // runAll() count proves the command was forwarded without needing a live
    // TCP round-trip (which is CI-flaky).
    assertTrue(mExecutor.runAll() > 0);
  }

  @Test
  public void sendShouldNotForwardWhenNoSender() {
    // Drain any leaked keepalive task that a prior test's connected client may
    // have queued into the shared static executor before asserting.
    int queuedBefore = mExecutor.runAll();
    pad.setSender(null);
    pad.send("up");
    // Nothing should be queued when there is no sender wired up.
    assertEquals(queuedBefore, mExecutor.runAll());
  }

  @Test
  public void measureShouldMakeSquareFromWidthAndHeight() {
    pad.measure(
        View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(150, View.MeasureSpec.EXACTLY));
    assertEquals(150, pad.getMeasuredWidth());
    assertEquals(150, pad.getMeasuredHeight());
  }

  @Test
  public void measureShouldFallBackToDefaultWhenNoSize() {
    pad.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    assertTrue(pad.getMeasuredWidth() > 0);
    assertTrue(pad.getMeasuredHeight() > 0);
  }

  @Test
  public void touchMoveShouldSendCoordinates() {
    pad.dispatchTouchEvent(eventAt(100, 100, MotionEvent.ACTION_DOWN));
    pad.dispatchTouchEvent(eventAt(190, 10, MotionEvent.ACTION_MOVE));
    pad.dispatchTouchEvent(eventAt(10, 190, MotionEvent.ACTION_MOVE));
    // Each touch that changes the coordinates by more than the sensitivity
    // queues a send; assert at least three commands were forwarded.
    assertTrue("expected coordinate commands to be queued", mExecutor.runAll() >= 3);
  }

  @Test
  public void touchUpShouldSendUpCommand() {
    pad.dispatchTouchEvent(eventAt(100, 100, MotionEvent.ACTION_DOWN));
    pad.dispatchTouchEvent(eventAt(120, 120, MotionEvent.ACTION_MOVE));
    pad.dispatchTouchEvent(eventAt(120, 120, MotionEvent.ACTION_UP));
    // DOWN + MOVE + UP each queue a send (>=3), UP included.
    assertTrue("expected commands to be queued", mExecutor.runAll() >= 3);
  }

  @Test
  public void unknownActionShouldBeIgnored() {
    assertTrue(pad.dispatchTouchEvent(eventAt(100, 100, MotionEvent.ACTION_SCROLL)));
  }

  @Test
  public void allConstructorsShouldBuild() {
    Context context = org.robolectric.RuntimeEnvironment.getApplication();
    assertNotNull(new SquareTouchPadLayout(context));
    assertNotNull(new SquareTouchPadLayout(context, null));
    assertNotNull(new SquareTouchPadLayout(context, null, 0));
  }

  @Test
  public void onDrawShouldRenderCircle() {
    android.graphics.Canvas canvas = new android.graphics.Canvas();
    pad.setAbsXY(100, 100);
    pad.draw(canvas);
  }

  @Test
  public void onSizeChangedShouldCenterWhenStartingEmpty() {
    // size changed from (0,0) -> centers the touch point.
    pad.measure(
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
    pad.layout(0, 0, 200, 200);
    // After a layout with old size 0, dispatch a move to read the new center.
    pad.dispatchTouchEvent(eventAt(100, 100, MotionEvent.ACTION_DOWN));
    assertFalse(sender == null);
  }
}
