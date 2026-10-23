package id.sch.examkiosk.ui.kiosk

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.util.Log
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

            // Berikan minimum height estimasi (rasio A4) agar layout tidak collapsible saat loading
            val displayMetrics = binding.root.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val estimatedHeight = (screenWidth * 1.414f).toInt()
            binding.ivPdfPage.minimumHeight = estimatedHeight

            renderJob = scope.launch(Dispatchers.Default) {
                val bitmap = renderPage(pageIndex)
                withContext(Dispatchers.Main) {
                    binding.pbPageLoading.visibility = View.GONE
                    if (bitmap != null) {
                        currentBitmap = bitmap
                        binding.ivPdfPage.setImageBitmap(bitmap)
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

                    // Lebar target render (dibatasi maksimal 1080 untuk hemat memori)
                    val targetWidth = if (screenWidth in 1..1080) screenWidth else 1080
                    val pageAspectRatio = page.height.toFloat() / page.width.toFloat()
                    val targetHeight = (targetWidth * pageAspectRatio).toInt()

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
    }
}
