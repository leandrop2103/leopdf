package ai.storyteller.pdfcraft.model

/**
 * Model representing a recently opened PDF document, reading progress, and bookmarks.
 * Tolerant of nulls from legacy JSON deserialization.
 */
data class RecentDocument(
    val uriString: String,
    val title: String,
    val pageCount: Int,
    var lastReadPage: Int,
    var lastOpenedTimestamp: Long,
    val fileSizeBytes: Long = 0L,
    var readingMode: String = "NORMAL",
    var bookmarks: MutableList<Int>? = mutableListOf()
) {
    fun getSafeBookmarks(): MutableList<Int> {
        if (bookmarks == null) {
            bookmarks = mutableListOf()
        }
        return bookmarks!!
    }

    val progressPercent: Int
        get() = if (pageCount > 0) {
            (((lastReadPage + 1).toFloat() / pageCount.toFloat()) * 100).toInt().coerceIn(1, 100)
        } else {
            0
        }
}
