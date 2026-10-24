package id.sch.examkiosk.ui.kiosk

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class ZoomableLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var scaleFactor = 1.0f
    private var translationXOffset = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private var isScaling = false
    private var accumulatedScrollY = 0f

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

                // Perbesaran horizontal berpusat pada focusX
                translationXOffset = focusX - (focusX - translationXOffset) * scaleChange
                scaleFactor = newScale

                clampTranslations()
                applyTransform()

                // Sesuaikan posisi vertikal RecyclerView sesuai titik fokus cubitan jari (focusY)
                val child = getChildAt(0)
                if (child is RecyclerView) {
                    val scrollDelta = (focusY * (scaleChange - 1.0f) / newScale).toInt()
                    if (scrollDelta != 0) {
                        child.scrollBy(0, scrollDelta)
                    }
                }
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
            if (scaleFactor > 1.2f) {
                resetZoomAnimated()
            } else {
                zoomToAnimated(2.2f, e.x, e.y)
            }
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (scaleFactor > 1.05f) {
                val child = getChildAt(0)
                if (child is RecyclerView) {
                    val vy = (-velocityY / scaleFactor).toInt()
                    child.fling(0, vy)
                    return true
                }
            }
            return false
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
                accumulatedScrollY = 0f
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
                accumulatedScrollY = 0f
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
                accumulatedScrollY = 0f
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

                        // 1. Geser posisi horizontal (panning margin kiri - kanan halaman)
                        translationXOffset += dx
                        clampTranslations()
                        applyTransform()

                        // 2. Scroll vertikal LANGSUNG pada RecyclerView
                        // Memungkinkan siswa terus menggulir halaman ke atas / ke bawah tanpa batas (tembus semua halaman)
                        val child = getChildAt(0)
                        if (child is RecyclerView) {
                            accumulatedScrollY += (-dy / scaleFactor)
                            val toScroll = accumulatedScrollY.toInt()
                            if (toScroll != 0) {
                                child.scrollBy(0, toScroll)
                                accumulatedScrollY -= toScroll
                            }
                        }
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
                accumulatedScrollY = 0f
            }
        }
        return true
    }

    private fun clampTranslations() {
        val child = getChildAt(0) ?: return
        val w = child.width.toFloat()
        if (w <= 0f) return

        if (scaleFactor <= 1.01f) {
            translationXOffset = 0f
            return
        }

        // Batasi pergeseran horizontal agar halaman tidak melayang keluar dari layar
        val minTransX = w * (1.0f - scaleFactor)
        val maxTransX = 0f
        translationXOffset = translationXOffset.coerceIn(minTransX, maxTransX)
    }

    private fun applyTransform() {
        val child = getChildAt(0) ?: return
        child.pivotX = 0f
        child.pivotY = 0f
        child.scaleX = scaleFactor
        child.scaleY = scaleFactor
        child.translationX = translationXOffset
        child.translationY = 0f
    }

    fun resetZoomAnimated() {
        if (scaleFactor <= 1.01f) return
        val startScale = scaleFactor
        val startX = translationXOffset

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                scaleFactor = startScale + (1.0f - startScale) * fraction
                translationXOffset = startX * (1.0f - fraction)
                applyTransform()
            }
            start()
        }
    }

    fun zoomToAnimated(targetScale: Float, focusX: Float, focusY: Float) {
        val startScale = scaleFactor
        val clampedTarget = targetScale.coerceIn(MIN_SCALE, MAX_SCALE)
        val targetX = (focusX - (focusX - translationXOffset) * (clampedTarget / startScale))
        val startX = translationXOffset

        val child = getChildAt(0)
        val scrollDelta = if (child is RecyclerView && clampedTarget > startScale) {
            (focusY * (clampedTarget / startScale - 1f) / clampedTarget).toInt()
        } else 0

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                scaleFactor = startScale + (clampedTarget - startScale) * fraction
                translationXOffset = startX + (targetX - startX) * fraction
                clampTranslations()
                applyTransform()
            }
            start()
        }

        if (scrollDelta != 0 && child is RecyclerView) {
            child.scrollBy(0, scrollDelta)
        }
    }

    fun resetZoom() {
        scaleFactor = 1.0f
        translationXOffset = 0f
        applyTransform()
    }

    companion object {
        private const val MIN_SCALE = 1.0f
        private const val MAX_SCALE = 3.5f
    }
}
