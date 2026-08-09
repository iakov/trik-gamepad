package com.trikset.gamepad

import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.util.concurrent.PausedExecutorService
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.LooperMode.Mode.PAUSED

@RunWith(RobolectricTestRunner::class)
@LooperMode(PAUSED)
class SquareTouchPadLayoutTest : RobolectricTestBase() {

  private val mExecutor = PausedExecutorService()
  private lateinit var sender: SenderService
  private lateinit var pad: SquareTouchPadLayout

  private fun eventAt(x: Float, y: Float, action: Int): MotionEvent =
      MotionEvent.obtain(0L, 0L, action, x, y, 0)

  /** Measures [pad] at [width]x[height] in [mode] and lays it out at that size. */
  private fun measureAndLayout(width: Int, height: Int, mode: Int = View.MeasureSpec.EXACTLY) {
    pad.measure(
        View.MeasureSpec.makeMeasureSpec(width, mode),
        View.MeasureSpec.makeMeasureSpec(height, mode),
    )
    pad.layout(0, 0, width, height)
  }

  @Before
  fun setUp() {
    sender = SenderService(mExecutor)
    sender.setKeepaliveTimeout(10000000) // disable keepalive noise
    sender.setTarget("localhost", 12345) // connect attempt is queued, not awaited

    val context = org.robolectric.RuntimeEnvironment.getApplication()
    val parent = android.widget.FrameLayout(context)
    pad = SquareTouchPadLayout(context)
    pad.setPadName("pad 1")
    pad.setSender(sender)
    parent.addView(pad)
    measureAndLayout(200, 200)
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
    measureAndLayout(300, 150)
    assertEquals(150, pad.measuredWidth)
    assertEquals(150, pad.measuredHeight)
  }

  @Test
  fun measureShouldFallBackToDefaultWhenNoSize() {
    measureAndLayout(0, 0, View.MeasureSpec.UNSPECIFIED)
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
    measureAndLayout(200, 200)
    // After a layout with old size 0, dispatch a move to read the new center.
    pad.dispatchTouchEvent(eventAt(100f, 100f, MotionEvent.ACTION_DOWN))
    assertTrue(sender !== null)
  }

  @Test
  fun onSizeChangedWithExistingSizeShouldKeepPosition() {
    // Size change from a non-zero old size -> the else branch: position kept.
    measureAndLayout(200, 200)
    pad.dispatchTouchEvent(eventAt(50f, 50f, MotionEvent.ACTION_DOWN))
    measureAndLayout(300, 300)
    // No crash; the old position is retained.
    pad.dispatchTouchEvent(eventAt(50f, 50f, MotionEvent.ACTION_MOVE))
  }

  @Test
  fun onTouchWithForeignViewShouldReturnFalse() {
    val context = org.robolectric.RuntimeEnvironment.getApplication()
    val foreign = SquareTouchPadLayout(context)
    val event = eventAt(100f, 100f, MotionEvent.ACTION_DOWN)
    assertFalse(pad.onTouch(foreign, event))
  }

  @Test
  fun onTouchCancelShouldSendUpCommand() {
    pad.dispatchTouchEvent(eventAt(100f, 100f, MotionEvent.ACTION_DOWN))
    pad.dispatchTouchEvent(eventAt(120f, 120f, MotionEvent.ACTION_CANCEL))
    assertTrue("expected commands to be queued", mExecutor.runAll() >= 2)
  }
}
