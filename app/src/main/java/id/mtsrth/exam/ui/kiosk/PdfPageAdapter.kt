package id.mtsrth.exam.ui.kiosk

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.pdf.PdfRenderer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.RecyclerView
import id.mtsrth.exam.databinding.ItemPdfPageBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun computePdfFilterColorArgb(isComfortMode: Boolean, isDarkMode: Boolean): Int {
    return when {
        isComfortMode && isDarkMode -> 0x3CD49B42
        isComfortMode -> 0x2EE6B86A
        isDarkMode -> 0x1C08110E
        else -> 0
    }
}

class PdfPageAdapter(
    private val renderer: PdfRenderer,
    private val scope: CoroutineScope,
    @ColorInt initialFilterColor: Int = 0
) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

    private val rendererLock = Any()

    @ColorInt
    private var overlayFilterColor: Int = initialFilterColor

    fun setOverlayFilterColor(@ColorInt filterColor: Int) {
        if (overlayFilterColor == filterColor) return
        overlayFilterColor = filterColor
        val count = itemCount
        if (count > 0) {
            notifyItemRangeChanged(0, count, PAYLOAD_FILTER_CHANGED)
        }
    }

    override fun getItemCount(): Int {
        return synchronized(rendererLock) {
            renderer.pageCount
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemPdfPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PageViewHolder(binding, renderer, rendererLock, scope)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(position, overlayFilterColor)
    }

    override fun onBindViewHolder(
        holder: PageViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_FILTER_CHANGED }) {
            holder.applyFilter(overlayFilterColor)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onViewAttachedToWindow(holder: PageViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.applyFilter(overlayFilterColor)
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        holder.recycleBitmap()
    }

    class PageViewHolder(
        private val binding: ItemPdfPageBinding,
        private val renderer: PdfRenderer,
        private val rendererLock: Any,
        private val scope: CoroutineScope
    ) : RecyclerView.ViewHolder(binding.root) {

        private var renderJob: Job? = null
        private var currentBitmap: Bitmap? = null
        @ColorInt
        private var activeFilterColor: Int = 0

        fun applyFilter(@ColorInt filterColor: Int) {
            activeFilterColor = filterColor
            if (filterColor != 0) {
                binding.ivPdfPage.colorFilter = PorterDuffColorFilter(filterColor, PorterDuff.Mode.SRC_ATOP)
                binding.root.setBackgroundColor(blendFilterOverWhite(filterColor))
            } else {
                binding.ivPdfPage.clearColorFilter()
                binding.root.setBackgroundColor(Color.WHITE)
            }
        }

        fun bind(pageIndex: Int, @ColorInt filterColor: Int) {
            recycleBitmap()
            applyFilter(filterColor)
            binding.pbPageLoading.visibility = View.VISIBLE

            // Berikan minimum height estimasi agar layout tidak collapsible saat loading
            val displayMetrics = binding.root.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val estimatedHeight = (screenWidth * 1.35f).toInt().coerceAtLeast(320)
            binding.ivPdfPage.minimumHeight = estimatedHeight

            renderJob = scope.launch(Dispatchers.Default) {
                val bitmap = renderPage(pageIndex)
                if (!isActive) {
                    bitmap?.let { if (!it.isRecycled) it.recycle() }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    if (!isActive) {
                        bitmap?.let { if (!it.isRecycled) it.recycle() }
                        return@withContext
                    }
                    binding.pbPageLoading.visibility = View.GONE
                    if (bitmap != null) {
                        currentBitmap = bitmap
                        binding.ivPdfPage.minimumHeight = 0
                        binding.ivPdfPage.setImageBitmap(bitmap)
                        applyFilter(activeFilterColor)
                    } else {
                        Log.e("PdfPageAdapter", "Gagal merender halaman $pageIndex, bitmap null")
                    }
                }
            }
        }

        private fun renderPage(pageIndex: Int): Bitmap? {
            return synchronized(rendererLock) {
                try {
                    val page = renderer.openPage(pageIndex)
                    val displayMetrics = binding.root.resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels

                    // Lebar target render (maksimal 1280 agar tetap tajam di mode landscape namun aman memori)
                    val targetWidth = screenWidth.coerceIn(1, 1280)
                    val pageAspectRatio = page.height.toFloat() / page.width.toFloat()
                    val targetHeight = (targetWidth * pageAspectRatio).toInt().coerceAtLeast(1)

                    // PENTING: Android PdfRenderer.Page.render() WAJIB menggunakan Bitmap.Config.ARGB_8888.
                    // Jika menggunakan RGB_565, Android akan melempar IllegalArgumentException ("Unsupported pixel format").
                    val bitmap = Bitmap.createBitmap(
                        targetWidth,
                        targetHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(Color.WHITE)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap
                } catch (e: Exception) {
                    Log.e("PdfPageAdapter", "Exception pada renderPage $pageIndex: ${e.message}", e)
                    null
                }
            }
        }

        fun recycleBitmap() {
            renderJob?.cancel()
            renderJob = null
            binding.ivPdfPage.setImageBitmap(null)
            currentBitmap?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }
            currentBitmap = null
        }

        companion object {
            @ColorInt
            private fun blendFilterOverWhite(@ColorInt filterColor: Int): Int {
                val alpha = Color.alpha(filterColor) / 255f
                val invAlpha = 1f - alpha
                val r = (Color.red(filterColor) * alpha + 255f * invAlpha).toInt().coerceIn(0, 255)
                val g = (Color.green(filterColor) * alpha + 255f * invAlpha).toInt().coerceIn(0, 255)
                val b = (Color.blue(filterColor) * alpha + 255f * invAlpha).toInt().coerceIn(0, 255)
                return Color.rgb(r, g, b)
            }
        }
    }

    companion object {
        private const val PAYLOAD_FILTER_CHANGED = "payload_filter_changed"
    }
}
