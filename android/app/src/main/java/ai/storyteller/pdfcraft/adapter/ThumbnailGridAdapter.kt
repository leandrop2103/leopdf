package ai.storyteller.pdfcraft.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ai.storyteller.pdfcraft.R
import ai.storyteller.pdfcraft.engine.PdfEngine
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ThumbnailGridAdapter(
    private val engine: PdfEngine,
    private val coroutineScope: CoroutineScope,
    private var currentPageIndex: Int,
    private val onPageSelected: (Int) -> Unit
) : RecyclerView.Adapter<ThumbnailGridAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.cardThumbnail)
        val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
        val tvPageNum: TextView = itemView.findViewById(R.id.tvThumbnailPageNum)
        var renderJob: Job? = null
    }

    override fun getItemCount(): Int = engine.pageCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_thumbnail_page, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.renderJob?.cancel()

        val displayPage = position + 1
        holder.tvPageNum.text = displayPage.toString()

        if (position == currentPageIndex) {
            holder.card.strokeColor = Color.parseColor("#2563EB") // Active blue border
            holder.card.strokeWidth = 4
        } else {
            holder.card.strokeColor = Color.TRANSPARENT
            holder.card.strokeWidth = 0
        }

        holder.ivThumbnail.setImageBitmap(null)
        holder.renderJob = coroutineScope.launch {
            val bitmap = engine.renderPage(position, targetWidth = 240)
            if (bitmap != null) {
                holder.ivThumbnail.setImageBitmap(bitmap)
            }
        }

        holder.itemView.setOnClickListener {
            onPageSelected(position)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.renderJob?.cancel()
        holder.ivThumbnail.setImageBitmap(null)
    }

    fun setCurrentPage(index: Int) {
        val old = currentPageIndex
        currentPageIndex = index
        notifyItemChanged(old)
        notifyItemChanged(index)
    }
}
