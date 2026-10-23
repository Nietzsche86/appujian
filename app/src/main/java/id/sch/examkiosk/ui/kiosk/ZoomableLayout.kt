package id.sch.examkiosk.ui.kiosk

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

class ZoomableLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ScaleGestureDetector.OnScaleGestureListener {

    private var scaleFactor = 1.0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var translationXOffset = 0f
    private var translationYOffset = 0f

    private val scaleDetector = ScaleGestureDetector(context, this)

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    init {
        setWillNotDraw(false)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = ev.x
                lastTouchY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleFactor > 1.05f && ev.pointerCount == 1) {
                    val dx = Math.abs(ev.x - lastTouchX)
                    val dy = Math.abs(ev.y - lastTouchY)
                    if (dx > 10f || dy > 10f) {
                        return true
                    }
                } else if (ev.pointerCount >= 2) {
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && scaleFactor > 1.0f && !scaleDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY

                    translationXOffset += dx
                    translationYOffset += dy
                    clampTranslations()
                    invalidate()

                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val prevScale = scaleFactor
        scaleFactor *= detector.scaleFactor
        scaleFactor = max(MIN_SCALE, min(scaleFactor, MAX_SCALE))

        val focusX = detector.focusX
        val focusY = detector.focusY

        val scaleDelta = scaleFactor / prevScale
        translationXOffset = focusX - (focusX - translationXOffset) * scaleDelta
        translationYOffset = focusY - (focusY - translationYOffset) * scaleDelta

        clampTranslations()
        invalidate()
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        lastFocusX = detector.focusX
        lastFocusY = detector.focusY
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        clampTranslations()
        invalidate()
    }

    private fun clampTranslations() {
        val maxTransX = (width * (scaleFactor - 1.0f)) / 2f
        val maxTransY = (height * (scaleFactor - 1.0f)) / 2f

        translationXOffset = max(-maxTransX, min(translationXOffset, maxTransX))
        translationYOffset = max(-maxTransY, min(translationYOffset, maxTransY))
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(translationXOffset, translationYOffset)
        canvas.scale(scaleFactor, scaleFactor, width / 2f, height / 2f)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    fun resetZoom() {
        scaleFactor = 1.0f
        translationXOffset = 0f
        translationYOffset = 0f
        invalidate()
    }

    companion object {
        private const val MIN_SCALE = 1.0f
        private const val MAX_SCALE = 3.0f
    }
}
