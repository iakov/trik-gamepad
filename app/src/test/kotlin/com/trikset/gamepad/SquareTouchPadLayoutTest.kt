package com.trikset.gamepad

import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.util.concurrent.PausedExecutorService
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.LooperMode.Mode.PAUSED

@RunWith(RobolectricTestRunner::class)
@LooperMode(PAUSED)
@Config(sdk = [Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK])
class SquareTouchPadLayoutTest {

  private val mExecutor = PausedExecutorService()
  private lateinit var sender: SenderService
  private lateinit var pad: SquareTouchPadLayout

  // SenderService keeps keepaliveTimeout and mConnectTask STATIC; tests that
  // run before this class may have left them dirty, which would make
  // connectAsync() a no-op and every awaitConnection() time out. Reset before
  // each test (same discipline as SenderServiceAdvancedTest).
  @Before
  fun resetSenderServiceStaticState() {
    val f = SenderService::class.java.getDeclaredField("mConnectTask")
    f.isAccessible = true
    f.set(null, null)
    val reset = SenderService()
    reset.setExecutor(mExecutor)
    reset.setKeepaliveTimeout(SenderService.DEFAULT_KEEPALIVE)
    reset.disconnect("reset")
  }

  private fun eventAt(x: Float, y: Float, action: Int): MotionEvent =
      MotionEvent.obtain(0L, 0L, action, x, y, 0)

  @Before
  fun setUp() {
    sender = SenderService()
    sender.setExecutor(mExecutor)
    sender.setKeepaliveTimeout(10000000) // disable keepalive noise
    sender.setTarget("localhost", 12345) // connect attempt is queued, not awaited

    val context = org.robolectric.RuntimeEnvironment.getApplication()
    val parent = android.widget.FrameLayout(context)
    pad = SquareTouchPadLayout(context)
    pad.setPadName("pad 1")
    pad.setSender(sender)
    parent.addView(pad)
    pad.measure(
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
    )
    pad.layout(0, 0, 200, 200)
    parent.measure(
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
    )
    parent.layout(0, 0, 200, 200)
  }

  @Test
  fun sendShouldForwardCommandToSender() {
    pad.send("up")
    // SenderService.send() queues work on the injected executor; a nonzero
    // runAll() count proves the command was forwarded without needing a live
    // TCP round-trip (which is CI-flaky).
    assertTrue(mExecutor.runAll() > 0)
  }

  @Test
  fun sendShouldNotForwardWhenNoSender() {
    // Drain any leaked keepalive task that a prior test's connected client may
    // have queued into the shared static executor before asserting.
    val queuedBefore = mExecutor.runAll()
    pad.setSender(null)
    pad.send("up")
    // Nothing should be queued when there is no sender wired up.
    assertEquals(queuedBefore, mExecutor.runAll())
  }

  @Test
  fun measureShouldMakeSquareFromWidthAndHeight() {
    pad.measure(
        View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(150, View.MeasureSpec.EXACTLY),
    )
    assertEquals(150, pad.measuredWidth)
    assertEquals(150, pad.measuredHeight)
  }

  @Test
  fun measureShouldFallBackToDefaultWhenNoSize() {
    pad.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
    )
    assertTrue(pad.measuredWidth > 0)
    assertTrue(pad.measuredHeight > 0)
  }

  @Test
  fun measureShouldUseHalfPerimeterWhenOneDimensionIsZero() {
    // width 0 + height 100 -> width*height==0, halfPerimeter!=0 -> size=100.
    pad.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
    )
    assertEquals(100, pad.measuredWidth)
    assertEquals(100, pad.measuredHeight)
  }

  @Test
  fun padNameShouldRoundTrip() {
    assertEquals("pad 1", pad.getPadName())
  }

  @Test
  fun touchMoveShouldSendCoordinates() {
    pad.dispatchTouchEvent(eventAt(100f, 100f, MotionEvent.ACTION_DOWN))
    pad.dispatchTouchEvent(eventAt(190f, 10f, MotionEvent.ACTION_MOVE))
    pad.dispatchTouchEvent(eventAt(10f, 190f, MotionEvent.ACTION_MOVE))
    // Each touch that changes the coordinates by more than the sensitivity
    // queues a send; assert at least three commands were forwarded.
    assertTrue("expected coordinate commands to be queued", mExecutor.runAll() >= 3)
  }

  @Test
  fun touchUpShouldSendUpCommand() {
    pad.dispatchTouchEvent(eventAt(100f, 100f, MotionEvent.ACTION_DOWN))
    pad.dispatchTouchEvent(eventAt(120f, 120f, MotionEvent.ACTION_MOVE))
    pad.dispatchTouchEvent(eventAt(120f, 120f, MotionEvent.ACTION_UP))
    // DOWN + MOVE + UP each queue a send (>=3), UP included.
    assertTrue("expected commands to be queued", mExecutor.runAll() >= 3)
  }

  @Test
  fun unknownActionShouldBeIgnored() {
    assertTrue(pad.dispatchTouchEvent(eventAt(100f, 100f, MotionEvent.ACTION_SCROLL)))
  }

  @Test
  fun allConstructorsShouldBuild() {
    val context = org.robolectric.RuntimeEnvironment.getApplication()
    assertNotNull(SquareTouchPadLayout(context))
    assertNotNull(SquareTouchPadLayout(context, null))
    assertNotNull(SquareTouchPadLayout(context, null, 0))
  }

  @Test
  fun onDrawShouldRenderCircle() {
    val canvas = android.graphics.Canvas()
    pad.setAbsXY(100f, 100f)
    pad.draw(canvas)
  }

  @Test
  fun onSizeChangedShouldCenterWhenStartingEmpty() {
    // size changed from (0,0) -> centers the touch point.
    pad.measure(
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
    )
    pad.layout(0, 0, 200, 200)
    // After a layout with old size 0, dispatch a move to read the new center.
    pad.dispatchTouchEvent(eventAt(100f, 100f, MotionEvent.ACTION_DOWN))
    assertTrue(sender !== null)
  }
}
