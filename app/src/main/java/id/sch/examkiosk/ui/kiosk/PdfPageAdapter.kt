package id.sch.examkiosk.ui.kiosk

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.sch.examkiosk.databinding.ItemPdfPageBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfPageAdapter(
    private val renderer: PdfRenderer,
    private val scope: CoroutineScope
) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

    private val rendererLock = Any()

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
        holder.bind(position)
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

        fun bind(pageIndex: Int) {
            recycleBitmap()
            binding.pbPageLoading.visibility = View.VISIBLE

            renderJob = scope.launch(Dispatchers.Default) {
                val bitmap = renderPage(pageIndex)
                withContext(Dispatchers.Main) {
                    binding.pbPageLoading.visibility = View.GONE
                    if (bitmap != null) {
                        currentBitmap = bitmap
                        binding.ivPdfPage.setImageBitmap(bitmap)
                    }
                }
            }
        }

        private fun renderPage(pageIndex: Int): Bitmap? {
            return synchronized(rendererLock) {
                try {
                    val page = renderer.openPage(pageIndex)
                    // Standarisasi resolusi render berbasis density
                    val displayMetrics = binding.root.resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val pageAspectRatio = page.height.toFloat() / page.width.toFloat()
                    val targetWidth = screenWidth
                    val targetHeight = (screenWidth * pageAspectRatio).toInt()

                    // Wajib RGB_565 untuk hemat 50% RAM heap
                    val bitmap = Bitmap.createBitmap(
                        targetWidth,
                        targetHeight,
                        Bitmap.Config.RGB_565
                    )
                    bitmap.eraseColor(Color.WHITE)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap
                } catch (e: Exception) {
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
    }
}
