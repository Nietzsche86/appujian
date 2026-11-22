package id.mtsrth.exam.ui.kiosk

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.OverScroller
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class ZoomableLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onTransformChangedListener: (() -> Unit)? = null

    private var scaleFactor = 1.0f
    private var translationXOffset = 0f
    private var translationYOffset = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private var isScaling = false
    private var accumulatedScrollY = 0f

    private val flingScroller = OverScroller(context)
    private var lastFlingX = 0
    private var lastFlingY = 0
    private var activeZoomAnimator: ValueAnimator? = null

    private val flingRunnable = object : Runnable {
        override fun run() {
            if (!flingScroller.computeScrollOffset()) return
            if (scaleFactor <= 1.01f) {
                flingScroller.forceFinished(true)
                return
            }

            val currX = flingScroller.currX
            val currY = flingScroller.currY
            val dx = (currX - lastFlingX).toFloat()
            val dy = (currY - lastFlingY).toFloat()
            lastFlingX = currX
            lastFlingY = currY

            val prevX = translationXOffset
            translationXOffset += dx
            clampTranslations()
            val movedX = translationXOffset != prevX
            val movedY = panOrScrollVertically(dy)
            applyTransform()

            val noDeltaYet = dx == 0f && dy == 0f
            if (!flingScroller.isFinished && (movedX || movedY || noDeltaYet)) {
                postOnAnimation(this)
            } else {
                flingScroller.forceFinished(true)
            }
        }
    }

    init {
        clipChildren = true
        clipToPadding = true
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            stopFling()
            cancelZoomAnimator()
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val targetScale = (scaleFactor * detector.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
            zoomAroundPoint(targetScale, detector.focusX, detector.focusY)
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
            stopFling()
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
                stopFling()
                lastFlingX = 0
                lastFlingY = 0
                flingScroller.fling(
                    0,
                    0,
                    velocityX.toInt(),
                    velocityY.toInt(),
                    Int.MIN_VALUE / 2,
                    Int.MAX_VALUE / 2,
                    Int.MIN_VALUE / 2,
                    Int.MAX_VALUE / 2
                )
                postOnAnimation(flingRunnable)
                return true
            }
            return false
        }
    })

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clampTranslations()
        applyTransform()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopFling()
        cancelZoomAnimator()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        gestureDetector.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopFling()
                activePointerId = ev.getPointerId(0)
                lastTouchX = ev.x
                lastTouchY = ev.y
                accumulatedScrollY = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Dua jari menyentuh: aktifkan mode cubit (pinch zoom)
                stopFling()
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
                stopFling()
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

                        // 2. Scroll vertikal pada RecyclerView dan lanjut panning translationY
                        // saat mencapai ujung bawah agar soal paling bawah selalu terlihat penuh
                        panOrScrollVertically(dy)
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
                accumulatedScrollY = 0f
            }
        }
        return true
    }

    private fun zoomAroundPoint(targetScale: Float, focusX: Float, focusY: Float) {
        val newScale = targetScale.coerceIn(MIN_SCALE, MAX_SCALE)
        if (newScale == scaleFactor) return

        val scaleChange = newScale / scaleFactor

        // Perbesaran horizontal berpusat pada focusX
        translationXOffset = focusX - (focusX - translationXOffset) * scaleChange

        // Perbesaran vertikal berpusat pada focusY: prioritaskan scroll RecyclerView,
        // lalu gunakan translationYOffset saat sudah menyentuh ujung bawah/atas agar titik fokus tetap akurat
        val desiredTransY = focusY - (focusY - translationYOffset) * scaleChange
        scaleFactor = newScale

        val child = getChildAt(0)
        if (child is RecyclerView) {
            val scrollNeeded = (-desiredTransY / newScale).toInt()
            val consumed = if (scrollNeeded != 0) {
                scrollRecyclerViewAndMeasure(child, scrollNeeded)
            } else {
                0
            }
            translationYOffset = desiredTransY + consumed * newScale
        } else {
            translationYOffset = desiredTransY
        }

        clampTranslations()
        applyTransform()
    }

    private fun panOrScrollVertically(screenDy: Float): Boolean {
        if (screenDy == 0f) return false
        val child = getChildAt(0) ?: return false

        if (child !is RecyclerView) {
            val prevY = translationYOffset
            translationYOffset += screenDy
            clampTranslations()
            return translationYOffset != prevY
        }

        var remainingScreenDy = screenDy
        var moved = false

        if (remainingScreenDy > 0f) {
            // Jari menggeser ke BAWAH (gulir ke atas):
            // 1. Kembalikan dulu translationYOffset negatif menuju 0f
            if (translationYOffset < 0f) {
                val absorb = remainingScreenDy.coerceAtMost(-translationYOffset)
                translationYOffset += absorb
                remainingScreenDy -= absorb
                moved = true
            }
            // 2. Sisa geseran menggulir RecyclerView ke atas
            if (remainingScreenDy > 0f) {
                accumulatedScrollY += (-remainingScreenDy / scaleFactor)
                val toScroll = accumulatedScrollY.toInt()
                if (toScroll != 0) {
                    val consumed = scrollRecyclerViewAndMeasure(child, toScroll)
                    accumulatedScrollY -= toScroll
                    if (consumed != 0) moved = true
                    if (consumed != toScroll) {
                        accumulatedScrollY = 0f
                    }
                }
            }
        } else {
            // Jari menggeser ke ATAS (gulir ke bawah menuju soal paling bawah):
            // 1. Gulir RecyclerView ke bawah selama masih ada ruang scroll
            if (child.canScrollVertically(1)) {
                accumulatedScrollY += (-remainingScreenDy / scaleFactor)
                val toScroll = accumulatedScrollY.toInt()
                if (toScroll != 0) {
                    val consumed = scrollRecyclerViewAndMeasure(child, toScroll)
                    accumulatedScrollY -= toScroll
                    if (consumed != 0) moved = true
                    val unconsumedRv = toScroll - consumed
                    remainingScreenDy = if (unconsumedRv > 0) {
                        accumulatedScrollY = 0f
                        -(unconsumedRv * scaleFactor)
                    } else {
                        0f
                    }
                } else {
                    remainingScreenDy = 0f
                }
            } else {
                accumulatedScrollY = 0f
            }

            // 2. Setelah RecyclerView mencapai dasar, lanjutkan panning translationYOffset
            // agar area [H/scaleFactor .. H] pada halaman terakhir tetap bisa dilihat utuh
            if (remainingScreenDy < 0f) {
                val prevY = translationYOffset
                translationYOffset += remainingScreenDy
                clampTranslations()
                if (translationYOffset != prevY) moved = true
            }
        }

        return moved
    }

    private fun scrollRecyclerViewAndMeasure(rv: RecyclerView, requestedDy: Int): Int {
        if (requestedDy == 0) return 0
        if (requestedDy > 0 && !rv.canScrollVertically(1)) return 0
        if (requestedDy < 0 && !rv.canScrollVertically(-1)) return 0

        val childCount = rv.childCount
        if (childCount == 0) return 0

        val anchorViews = arrayOfNulls<View>(childCount)
        val anchorTops = IntArray(childCount)
        for (i in 0 until childCount) {
            val v = rv.getChildAt(i)
            anchorViews[i] = v
            anchorTops[i] = v.top
        }

        rv.scrollBy(0, requestedDy)

        for (i in 0 until childCount) {
            val v = anchorViews[i] ?: continue
            if (v.parent === rv) {
                return anchorTops[i] - v.top
            }
        }

        return requestedDy
    }

    private fun getMinTranslationY(child: View): Float {
        val viewHeight = height.toFloat().takeIf { it > 0f } ?: child.height.toFloat()
        if (viewHeight <= 0f || scaleFactor <= 1.01f) return 0f

        if (child is RecyclerView) {
            val canScroll = child.canScrollVertically(1) || child.canScrollVertically(-1)
            val effectiveContentHeight = if (!canScroll && child.childCount > 0) {
                var maxBottom = 0
                for (i in 0 until child.childCount) {
                    val item = child.getChildAt(i)
                    if (item.bottom > maxBottom) {
                        maxBottom = item.bottom
                    }
                }
                (maxBottom + child.paddingBottom).toFloat().coerceAtMost(viewHeight)
            } else {
                viewHeight
            }
            return (viewHeight - effectiveContentHeight * scaleFactor).coerceAtMost(0f)
        }

        return viewHeight * (1.0f - scaleFactor)
    }

    private fun clampTranslations() {
        val child = getChildAt(0) ?: return
        val w = width.toFloat().takeIf { it > 0f } ?: child.width.toFloat()
        if (w <= 0f) return

        if (scaleFactor <= 1.01f) {
            translationXOffset = 0f
            translationYOffset = 0f
            return
        }

        // Batasi pergeseran horizontal dan vertikal agar halaman tidak melayang keluar dari layar
        val minTransX = w * (1.0f - scaleFactor)
        translationXOffset = translationXOffset.coerceIn(minTransX, 0f)

        val minTransY = getMinTranslationY(child)
        translationYOffset = translationYOffset.coerceIn(minTransY, 0f)
    }

    private fun applyTransform() {
        val child = getChildAt(0) ?: return
        child.pivotX = 0f
        child.pivotY = 0f
        child.scaleX = scaleFactor
        child.scaleY = scaleFactor
        child.translationX = translationXOffset
        child.translationY = translationYOffset
        onTransformChangedListener?.invoke()
    }

    private fun stopFling() {
        removeCallbacks(flingRunnable)
        if (!flingScroller.isFinished) {
            flingScroller.forceFinished(true)
        }
    }

    private fun cancelZoomAnimator() {
        activeZoomAnimator?.cancel()
        activeZoomAnimator = null
    }

    fun getVisibleChildVerticalBounds(child: View, outBounds: IntArray) {
        val paddingTop = child.paddingTop
        val maxBottom = child.height - child.paddingBottom
        if (scaleFactor <= 1.01f || height <= 0) {
            outBounds[0] = paddingTop
            outBounds[1] = maxBottom
            return
        }
        val visibleTop = ((-translationYOffset) / scaleFactor).toInt().coerceIn(paddingTop, maxBottom)
        val visibleBottom = ((height - translationYOffset) / scaleFactor).toInt().coerceIn(visibleTop, maxBottom)
        outBounds[0] = visibleTop
        outBounds[1] = visibleBottom
    }

    fun isZoomedIn(): Boolean = scaleFactor > 1.01f

    fun isAtBottomPanLimit(): Boolean {
        val child = getChildAt(0) ?: return true
        val minTransY = getMinTranslationY(child)
        return translationYOffset <= minTransY + 2f
    }

    fun resetZoomAnimated() {
        stopFling()
        cancelZoomAnimator()
        if (scaleFactor <= 1.01f && translationXOffset == 0f && translationYOffset == 0f) return
        val startScale = scaleFactor
        val startX = translationXOffset
        val startY = translationYOffset

        activeZoomAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
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
        stopFling()
        cancelZoomAnimator()
        val startScale = scaleFactor
        val clampedTarget = targetScale.coerceIn(MIN_SCALE, MAX_SCALE)

        activeZoomAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                val stepScale = startScale + (clampedTarget - startScale) * fraction
                zoomAroundPoint(stepScale, focusX, focusY)
            }
            start()
        }
    }

    fun resetZoom() {
        stopFling()
        cancelZoomAnimator()
        scaleFactor = 1.0f
        translationXOffset = 0f
        translationYOffset = 0f
        accumulatedScrollY = 0f
        applyTransform()
    }

    companion object {
        private const val MIN_SCALE = 1.0f
        private const val MAX_SCALE = 3.5f
    }
}
