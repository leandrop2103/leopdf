package ai.storyteller.pdfcraft.adapter

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ai.storyteller.pdfcraft.R
import ai.storyteller.pdfcraft.model.RecentDocument
import com.google.android.material.progressindicator.LinearProgressIndicator

class RecentDocumentsAdapter(
    private val onItemClick: (RecentDocument) -> Unit,
    private val onDeleteClick: (RecentDocument) -> Unit
) : ListAdapter<RecentDocument, RecentDocumentsAdapter.ViewHolder>(DiffCallback) {

    private var allDocuments: List<RecentDocument> = emptyList()

    fun updateAllDocuments(list: List<RecentDocument>) {
        allDocuments = list
        submitList(list)
    }

    fun filter(query: String) {
        if (query.isBlank()) {
            submitList(allDocuments)
        } else {
            val filtered = allDocuments.filter {
                it.title.contains(query, ignoreCase = true)
            }
            submitList(filtered)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvProgress: TextView = itemView.findViewById(R.id.tvProgress)
        val progressBar: LinearProgressIndicator = itemView.findViewById(R.id.progressBar)
        val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_document, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val doc = getItem(position)
        val context = holder.itemView.context

        holder.tvTitle.text = doc.title
        val pageNum = doc.lastReadPage + 1
        val percent = doc.progressPercent
        holder.tvProgress.text = "${context.getString(R.string.page_counter_template, pageNum, doc.pageCount)} ($percent%)"
        holder.progressBar.progress = percent

        val relativeTime = DateUtils.getRelativeTimeSpanString(
            doc.lastOpenedTimestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        holder.tvTimestamp.text = context.getString(R.string.last_opened_prefix, relativeTime)

        holder.itemView.setOnClickListener { onItemClick(doc) }
        holder.btnDelete.setOnClickListener { onDeleteClick(doc) }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<RecentDocument>() {
        override fun areItemsTheSame(oldItem: RecentDocument, newItem: RecentDocument): Boolean {
            return oldItem.uriString == newItem.uriString
        }

        override fun areContentsTheSame(oldItem: RecentDocument, newItem: RecentDocument): Boolean {
            return oldItem == newItem
        }
    }
}
