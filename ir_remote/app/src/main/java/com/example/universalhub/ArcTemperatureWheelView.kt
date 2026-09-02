package com.example.universalhub

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.*

class ArcTemperatureWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Temperature range
    var minTemp: Int = 16
    var maxTemp: Int = 30

    // Current continuous temperature value (e.g. 24.0)
    var currentTemp: Float = 24.0f
        private set

    // Granularity & Angular Geometry for Full 90° Sweep (+90° to -90° = 180° total semi-circle)
    private val subdivisionsPerDegree = 4 // 4 ticks per 1°C
    private val anglePerDegree = 34.0f // 34 degrees per 1°C for wide distribution
    private val pixelsPerDegree = 46f * resources.displayMetrics.density // Natural drag sensitivity
    private val maxVisibleAngle = 90f // Full 90° sweep up and 90° sweep down

    // Visual Dimensions
    private val majorTickLength = 28f * resources.displayMetrics.density
    private val minorTickLength = 16f * resources.displayMetrics.density
    private val pointerLineWidth = 22f * resources.displayMetrics.density

    // Paint Objects
    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334155") // Slate 700
        strokeWidth = 2.4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    private val minorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8") // Slate 400
        strokeWidth = 1.4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A") // Slate 900
        strokeWidth = 2.4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    private val dialTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A") // Slate 900
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }

    private val textBounds = Rect()

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    // Touch & Animation Physics
    private var velocityTracker: VelocityTracker? = null
    private var lastTouchY = 0f
    private var isDragging = false
    private var touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var activeAnimator: ValueAnimator? = null
    private var lastHapticTickIndex = (currentTemp * subdivisionsPerDegree).roundToInt()

    // Callbacks
    var onTempChangeListener: ((temp: Float) -> Unit)? = null
    var onTempSettledListener: ((temp: Int) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
    }

    /**
     * Programmatically set the temperature with optional smooth spring animation
     */
    fun setTemperature(temp: Int, animate: Boolean = true) {
        val clamped = temp.coerceIn(minTemp, maxTemp).toFloat()
        if (!animate) {
            currentTemp = clamped
            lastHapticTickIndex = (currentTemp * subdivisionsPerDegree).roundToInt()
            invalidate()
            onTempChangeListener?.invoke(currentTemp)
            return
        }

        activeAnimator?.cancel()
        activeAnimator = ValueAnimator.ofFloat(currentTemp, clamped).apply {
            duration = 320
            interpolator = OvershootInterpolator(1.15f)
            addUpdateListener { anim ->
                currentTemp = anim.animatedValue as Float
                checkAndTriggerHaptic(currentTemp)
                invalidate()
                onTempChangeListener?.invoke(currentTemp)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentTemp = clamped
                    invalidate()
                    onTempSettledListener?.invoke(clamped.roundToInt())
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Geometry: Circle center is anchored at the right edge of the card
        // The arc sweeps a full 90 degrees above and below from the center apex
        val minApexX = 72f * resources.displayMetrics.density
        val apexX = max(minApexX, w * 0.36f)
        val cx = w - 6f * resources.displayMetrics.density
        val arcRadius = max(cx - apexX, 100f * resources.displayMetrics.density)
        val cy = h / 2f

        // Center pointer is at 180 degrees (pointing directly left from (cx, cy))
        val centerAngle = 180f

        // Calculate tick ranges based on visible angle (90 degrees above and below)
        val visibleDegreesRange = (maxVisibleAngle / anglePerDegree) + 2f
        val minVisibleTickIndex = ((currentTemp - visibleDegreesRange) * subdivisionsPerDegree).toInt()
        val maxVisibleTickIndex = ((currentTemp + visibleDegreesRange) * subdivisionsPerDegree).toInt()

        for (tickIndex in minVisibleTickIndex..maxVisibleTickIndex) {
            val tickTemp = tickIndex.toFloat() / subdivisionsPerDegree
            val deltaAngle = -(tickTemp - currentTemp) * (anglePerDegree / subdivisionsPerDegree)

            if (abs(deltaAngle) > maxVisibleAngle) continue

            val angleDeg = centerAngle + deltaAngle
            val angleRad = Math.toRadians(angleDeg.toDouble())

            // Gentle falloff curve so ticks remain visible all the way to 90 degrees
            val normalizedDist = (abs(deltaAngle) / maxVisibleAngle).coerceIn(0f, 1f)
            val fadeFactor = (1f - normalizedDist).pow(0.7f)
            val alpha = (fadeFactor * 255).toInt().coerceIn(0, 255)
            if (alpha < 4) continue

            val isMajor = (tickIndex % subdivisionsPerDegree == 0)

            val tickLength = if (isMajor) {
                majorTickLength * (0.82f + 0.18f * fadeFactor)
            } else {
                minorTickLength * (0.82f + 0.18f * fadeFactor)
            }

            val paint = if (isMajor) majorTickPaint else minorTickPaint
            paint.alpha = if (isMajor) alpha else (alpha * 0.85f).toInt()

            val cosA = cos(angleRad).toFloat()
            val sinA = sin(angleRad).toFloat()

            // Outer point on circular arc (curves to 90° vertical at top and bottom)
            val x1 = cx + arcRadius * cosA
            val y1 = cy + arcRadius * sinA

            // Inner point pointing radially towards center (cx, cy)
            val x2 = cx + (arcRadius - tickLength) * cosA
            val y2 = cy + (arcRadius - tickLength) * sinA

            canvas.drawLine(x1, y1, x2, y2, paint)
        }

        // Draw Center Needle Indicator & Reading: "24°C —"
        val needleEndX = apexX
        val needleStartX = apexX - pointerLineWidth
        val needleY = cy

        val tempInt = currentTemp.roundToInt().coerceIn(minTemp, maxTemp)
        val textStr = "$tempInt°C"
        dialTextPaint.getTextBounds(textStr, 0, textStr.length, textBounds)
        val textX = needleStartX - 6f * resources.displayMetrics.density
        val textY = needleY + (textBounds.height() / 2f) - 1.5f

        canvas.drawText(textStr, textX, textY, dialTextPaint)
        canvas.drawLine(needleStartX, needleY, needleEndX, needleY, pointerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeAnimator?.cancel()
                downX = event.x
                downY = event.y
                lastTouchY = event.y
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastTouchY
                if (!isDragging && abs(event.y - downY) > touchSlop) {
                    isDragging = true
                }

                if (isDragging) {
                    var dTemp = -dy / pixelsPerDegree

                    if (currentTemp < minTemp && dTemp < 0) {
                        dTemp *= 0.25f
                    } else if (currentTemp > maxTemp && dTemp > 0) {
                        dTemp *= 0.25f
                    }

                    currentTemp += dTemp
                    checkAndTriggerHaptic(currentTemp)
                    invalidate()
                    onTempChangeListener?.invoke(currentTemp)
                }
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                velocityTracker?.computeCurrentVelocity(1000)
                val yVelocity = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null

                if (!isDragging && abs(event.x - downX) < touchSlop && abs(event.y - downY) < touchSlop) {
                    performClick()
                    val centerHalf = height / 2f
                    val step = if (event.y < centerHalf) 1 else -1
                    setTemperature(currentTemp.roundToInt() + step, true)
                    return true
                }

                finishDragWithPhysics(yVelocity)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun finishDragWithPhysics(yVelocity: Float) {
        val minFlingVelocity = 350f

        val flingDistance = if (abs(yVelocity) > minFlingVelocity) {
            -yVelocity * 0.0012f
        } else {
            0f
        }

        val estimatedTarget = (currentTemp + flingDistance).roundToInt().coerceIn(minTemp, maxTemp)

        activeAnimator?.cancel()
        activeAnimator = ValueAnimator.ofFloat(currentTemp, estimatedTarget.toFloat()).apply {
            duration = if (abs(yVelocity) > minFlingVelocity) 420 else 260
            interpolator = if (abs(yVelocity) > minFlingVelocity) DecelerateInterpolator(1.6f) else OvershootInterpolator(1.15f)
            addUpdateListener { anim ->
                currentTemp = anim.animatedValue as Float
                checkAndTriggerHaptic(currentTemp)
                invalidate()
                onTempChangeListener?.invoke(currentTemp)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentTemp = estimatedTarget.toFloat()
                    invalidate()
                    onTempSettledListener?.invoke(estimatedTarget)
                }
            })
            start()
        }
    }

    private fun checkAndTriggerHaptic(temp: Float) {
        val tickIndex = (temp * subdivisionsPerDegree).roundToInt()
        if (tickIndex != lastHapticTickIndex) {
            lastHapticTickIndex = tickIndex
            triggerMicroHaptic()
        }
    }

    private fun triggerMicroHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(10, 40))
            } else {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        } catch (e: Exception) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        activeAnimator?.cancel()
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
