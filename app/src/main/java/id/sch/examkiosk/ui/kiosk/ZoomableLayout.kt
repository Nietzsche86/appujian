package id.sch.examkiosk.ui.kiosk

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs

class ZoomableLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var scaleFactor = 1.0f
    private var translationXOffset = 0f
    private var translationYOffset = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private var isScaling = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            val newScale = (scaleFactor * factor).coerceIn(MIN_SCALE, MAX_SCALE)
            if (newScale != scaleFactor) {
                val focusX = detector.focusX
                val focusY = detector.focusY
                val scaleChange = newScale / scaleFactor

                // Perbesaran berpusat langsung pada titik jari pengguna (focusX, focusY)
                translationXOffset = focusX - (focusX - translationXOffset) * scaleChange
                translationYOffset = focusY - (focusY - translationYOffset) * scaleChange
                scaleFactor = newScale

                clampTranslations()
                applyTransform()
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            if (scaleFactor < 1.05f) {
                resetZoomAnimated()
            }
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Ketuk ganda untuk memperbesar cepat ke 2.2x pada posisi ketukan atau kembali normal
            if (scaleFactor > 1.2f) {
                resetZoomAnimated()
            } else {
                zoomToAnimated(2.2f, e.x, e.y)
            }
            return true
        }
    })

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        gestureDetector.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = ev.getPointerId(0)
                lastTouchX = ev.x
                lastTouchY = ev.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Dua jari menyentuh: aktifkan mode cubit (pinch zoom)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleFactor > 1.05f) {
                    val pointerIndex = ev.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val x = ev.getX(pointerIndex)
                        val y = ev.getY(pointerIndex)
                        val dx = abs(x - lastTouchX)
                        val dy = abs(y - lastTouchY)
                        // Saat diperbesar, cegat usapan agar layar bisa digeser ke segala arah
                        if (dx > 6f || dy > 6f) {
                            lastTouchX = x
                            lastTouchY = y
                            parent?.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                    }
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                lastTouchY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex != -1) {
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)

                    if (!isScaling && scaleFactor > 1.05f) {
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY

                        // Geser posisi horizontal dan vertikal
                        translationXOffset += dx
                        translationYOffset += dy
                        clampTranslations()
                        applyTransform()
                    }

                    lastTouchX = x
                    lastTouchY = y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = event.getX(newPointerIndex)
                    lastTouchY = event.getY(newPointerIndex)
                    activePointerId = event.getPointerId(newPointerIndex)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return true
    }

    private fun clampTranslations() {
        val child = getChildAt(0) ?: return
        val w = child.width.toFloat()
        val h = child.height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (scaleFactor <= 1.01f) {
            translationXOffset = 0f
            translationYOffset = 0f
            return
        }

        // Batasi pergeseran agar halaman tidak melayang keluar dari layar
        val minTransX = w * (1.0f - scaleFactor)
        val maxTransX = 0f
        translationXOffset = translationXOffset.coerceIn(minTransX, maxTransX)

        val minTransY = h * (1.0f - scaleFactor)
        val maxTransY = 0f
        translationYOffset = translationYOffset.coerceIn(minTransY, maxTransY)
    }

    private fun applyTransform() {
        val child = getChildAt(0) ?: return
        child.pivotX = 0f
        child.pivotY = 0f
        child.scaleX = scaleFactor
        child.scaleY = scaleFactor
        child.translationX = translationXOffset
        child.translationY = translationYOffset
    }

    fun resetZoomAnimated() {
        if (scaleFactor <= 1.01f) return
        val startScale = scaleFactor
        val startX = translationXOffset
        val startY = translationYOffset

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                scaleFactor = startScale + (1.0f - startScale) * fraction
                translationXOffset = startX * (1.0f - fraction)
                translationYOffset = startY * (1.0f - fraction)
                applyTransform()
            }
            start()
        }
    }

    fun zoomToAnimated(targetScale: Float, focusX: Float, focusY: Float) {
        val startScale = scaleFactor
        val clampedTarget = targetScale.coerceIn(MIN_SCALE, MAX_SCALE)
        val targetX = (focusX - (focusX - translationXOffset) * (clampedTarget / startScale))
        val targetY = (focusY - (focusY - translationYOffset) * (clampedTarget / startScale))

        val startX = translationXOffset
        val startY = translationYOffset

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                scaleFactor = startScale + (clampedTarget - startScale) * fraction
                translationXOffset = startX + (targetX - startX) * fraction
                translationYOffset = startY + (targetY - startY) * fraction
                clampTranslations()
                applyTransform()
            }
            start()
        }
    }

    fun resetZoom() {
        scaleFactor = 1.0f
        translationXOffset = 0f
        translationYOffset = 0f
        applyTransform()
    }

    companion object {
        private const val MIN_SCALE = 1.0f
        private const val MAX_SCALE = 3.5f
    }
}
