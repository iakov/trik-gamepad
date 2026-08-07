package com.trikset.gamepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.RelativeLayout
import androidx.core.content.res.ResourcesCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SquareTouchPadLayout : RelativeLayout {

  private val paint = Paint()
  private var absX = 0f
  private var absY = 0f
  private var padName: String? = null
  private var sender: SenderService? = null
  private var prevX = 0
  private var prevY = 0
  private var maxX = 0f
  private var maxY = 0f

  constructor(context: Context) : super(context) {
    init()
  }

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
    init()
  }

  constructor(
      context: Context,
      attrs: AttributeSet?,
      defStyle: Int,
  ) : super(context, attrs, defStyle) {
    init()
  }

  // public (not internal) so Java callers (MainActivity, the Java tests) can
  // reach them — Kotlin mangles internal member names on the JVM.
  fun getPadName(): String? = padName

  fun setPadName(padName: String?) {
    this.padName = padName
  }

  private fun init() {
    paint.color = Color.RED
    paint.strokeWidth = 0f
    paint.style = Paint.Style.STROKE
    paint.alpha = OPAQUE_ALPHA
    setOnTouchListener(TouchPadListener())
    setOnClickListener {
      performHapticFeedback(
          HapticFeedbackConstants.LONG_PRESS,
          HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
      )
    }
    setWillNotDraw(false)
    isHapticFeedbackEnabled = true
    background =
        ResourcesCompat.getDrawable(
            getResources(),
            R.drawable.oxygen_actions_transform_move_icon,
            null,
        )
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    canvas.drawCircle(absX, absY, maxX / CIRCLE_RADIUS_DIVISOR, paint)
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val width = MeasureSpec.getSize(widthMeasureSpec)
    val height = MeasureSpec.getSize(heightMeasureSpec)
    val halfPerimeter = width + height
    val size =
        if ((width * height) != 0) {
          min(width, height)
        } else if (halfPerimeter != 0) {
          halfPerimeter
        } else {
          DEFAULT_SIZE
        }
    setMeasuredDimension(size, size)
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    maxX = w.toFloat()
    maxY = h.toFloat()
    if (oldw == 0 && oldh == 0) {
      setAbsXY(w / 2.0f, h / 2.0f)
    }
  }

  override fun performClick(): Boolean = super.performClick()

  fun send(command: String) {
    val currentSender = sender
    if (currentSender != null) {
      currentSender.send("$padName $command")
      performHapticFeedback(
          HapticFeedbackConstants.VIRTUAL_KEY,
          HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
      )
    }
  }

  fun setAbsXY(x: Float, y: Float) {
    absX = x
    absY = y
    invalidate()
  }

  fun setSender(sender: SenderService?) {
    this.sender = sender
  }

  // internal so the inner TouchPadListener reaches it without a synthetic
  // accessor (lint SyntheticAccessor)
  internal fun onTouch(view: View, event: MotionEvent): Boolean {
    if (view !== this) {
      return false
    }

    return when (event.action) {
      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        parent?.requestDisallowInterceptTouchEvent(false)
        send("up")
        performClick()
        true
      }
      MotionEvent.ACTION_DOWN,
      MotionEvent.ACTION_MOVE -> {
        parent?.requestDisallowInterceptTouchEvent(true)
        performClick()
        setAbsXY(
            max(0f, min(event.x, maxX)),
            max(0f, min(event.y, maxY)),
        )

        val rX = (COORDINATE_SCALE * SCALE * (absX / maxX - CENTER_OFFSET).toDouble()).toInt()
        val rY = -(COORDINATE_SCALE * SCALE * (absY / maxY - CENTER_OFFSET).toDouble()).toInt()
        val curY = max(-MAX_COORDINATE, min(rY, MAX_COORDINATE))
        val curX = max(-MAX_COORDINATE, min(rX, MAX_COORDINATE))

        if (abs(curX - prevX) > SENSITIVITY || abs(curY - prevY) > SENSITIVITY) {
          prevX = curX
          prevY = curY
          send(String.format(Locale.US, "%d %d", curX, curY))
        }

        true
      }
      else -> {
        Log.e("TouchEvent", "Unknown:$event")
        true
      }
    }
  }

  private inner class TouchPadListener : OnTouchListener {
    override fun onTouch(view: View, event: MotionEvent): Boolean =
        this@SquareTouchPadLayout.onTouch(view, event)
  }

  private companion object {
    const val DEFAULT_SIZE = 100
    const val CIRCLE_RADIUS_DIVISOR = 20
    const val SENSITIVITY = 3
    const val SCALE = 1.15
    const val OPAQUE_ALPHA = 255
    const val COORDINATE_SCALE = 200.0
    const val CENTER_OFFSET = 0.5
    const val MAX_COORDINATE = 100
  }
}
