package ai.storyteller.pdfcraft.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ai.storyteller.pdfcraft.R
import ai.storyteller.pdfcraft.engine.PdfEngine
import ai.storyteller.pdfcraft.view.PdfPageView
import ai.storyteller.pdfcraft.view.PdfReadingView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Adapter that provides continuous vertical scrolling of PDF pages.
 * Pre-computes page aspect ratios for jitter-free scrolling and renders asynchronously.
 */
class ContinuousPdfAdapter(
    private val engine: PdfEngine,
    private val coroutineScope: CoroutineScope,
    private var readingMode: PdfReadingView.ReadingMode = PdfReadingView.ReadingMode.NORMAL
) : RecyclerView.Adapter<ContinuousPdfAdapter.PageViewHolder>() {

    private var targetDisplayWidth: Int = 1080
    var isCropMarginsEnabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    fun setTargetDisplayWidth(width: Int) {
        if (width > 0 && targetDisplayWidth != width) {
            targetDisplayWidth = width
            notifyDataSetChanged()
        }
    }

    fun setReadingMode(mode: PdfReadingView.ReadingMode) {
        if (readingMode != mode) {
            readingMode = mode
            notifyDataSetChanged()
        }
    }

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val pdfPageView: PdfPageView = itemView.findViewById(R.id.pdfPageView)
        var renderJob: Job? = null
    }

    override fun getItemCount(): Int = engine.pageCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_continuous_pdf_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.renderJob?.cancel()

        val dims = engine.getPageDimensions(position)
        val ratio = dims.first.toFloat() / dims.second.toFloat()
        holder.pdfPageView.setAspectRatio(ratio)
        holder.pdfPageView.setReadingMode(readingMode)
        holder.pdfPageView.isCropMarginsEnabled = isCropMarginsEnabled
        holder.pdfPageView.setPageBitmap(null) // Clear while loading

        holder.renderJob = coroutineScope.launch {
            val bitmap = engine.renderPage(position, targetDisplayWidth)
            if (bitmap != null) {
                holder.pdfPageView.setPageBitmap(bitmap)
            }
        }
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        holder.renderJob?.cancel()
        holder.pdfPageView.setPageBitmap(null)
    }
}
