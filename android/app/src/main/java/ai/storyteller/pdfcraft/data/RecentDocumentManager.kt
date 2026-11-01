package ai.storyteller.pdfcraft.data

import android.content.Context
import android.content.SharedPreferences
import ai.storyteller.pdfcraft.model.RecentDocument
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages saved document progress and recent files list.
 */
class RecentDocumentManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getRecentDocuments(): List<RecentDocument> {
        val json = prefs.getString(KEY_RECENTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<RecentDocument>>() {}.type
            val list: List<RecentDocument>? = gson.fromJson(json, type)
            list?.sortedByDescending { it.lastOpenedTimestamp } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveOrUpdate(
        uriString: String,
        title: String,
        pageCount: Int,
        lastReadPage: Int,
        fileSizeBytes: Long = 0L,
        readingMode: String = "NORMAL"
    ) {
        val list = getRecentDocuments().toMutableList()
        val existingIndex = list.indexOfFirst { it.uriString == uriString }

        val updatedDoc = if (existingIndex >= 0) {
            val existing = list[existingIndex]
            existing.copy(
                lastReadPage = lastReadPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
                lastOpenedTimestamp = System.currentTimeMillis(),
                readingMode = readingMode
            )
        } else {
            RecentDocument(
                uriString = uriString,
                title = title,
                pageCount = pageCount,
                lastReadPage = lastReadPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
                lastOpenedTimestamp = System.currentTimeMillis(),
                fileSizeBytes = fileSizeBytes,
                readingMode = readingMode
            )
        }

        if (existingIndex >= 0) {
            list.removeAt(existingIndex)
        }
        list.add(0, updatedDoc)

        // Limit recents to 50 items for efficiency
        val trimmed = if (list.size > 50) list.subList(0, 50) else list
        prefs.edit().putString(KEY_RECENTS, gson.toJson(trimmed)).apply()
    }

    fun updateProgress(uriString: String, lastReadPage: Int, readingMode: String? = null) {
        val list = getRecentDocuments().toMutableList()
        val index = list.indexOfFirst { it.uriString == uriString }
        if (index >= 0) {
            val doc = list[index]
            doc.lastReadPage = lastReadPage.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0))
            doc.lastOpenedTimestamp = System.currentTimeMillis()
            if (readingMode != null) {
                doc.readingMode = readingMode
            }
            list.sortByDescending { it.lastOpenedTimestamp }
            prefs.edit().putString(KEY_RECENTS, gson.toJson(list)).apply()
        }
    }

    fun getDocument(uriString: String): RecentDocument? {
        return getRecentDocuments().firstOrNull { it.uriString == uriString }
    }

    fun removeDocument(uriString: String) {
        val list = getRecentDocuments().toMutableList()
        if (list.removeAll { it.uriString == uriString }) {
            prefs.edit().putString(KEY_RECENTS, gson.toJson(list)).apply()
        }
    }

    fun getBookmarks(uriString: String): List<Int> {
        val doc = getDocument(uriString) ?: return emptyList()
        return doc.getSafeBookmarks().sorted()
    }

    fun isBookmarked(uriString: String, pageIndex: Int): Boolean {
        val doc = getDocument(uriString) ?: return false
        return doc.getSafeBookmarks().contains(pageIndex)
    }

    fun toggleBookmark(uriString: String, pageIndex: Int): Boolean {
        val list = getRecentDocuments().toMutableList()
        val index = list.indexOfFirst { it.uriString == uriString }
        if (index < 0) return false

        val doc = list[index]
        val safeList = doc.getSafeBookmarks()
        val wasBookmarked = safeList.contains(pageIndex)
        if (wasBookmarked) {
            safeList.remove(pageIndex)
        } else {
            safeList.add(pageIndex)
            safeList.sort()
        }
        prefs.edit().putString(KEY_RECENTS, gson.toJson(list)).apply()
        return !wasBookmarked
    }

    fun clearAll() {
        prefs.edit().remove(KEY_RECENTS).apply()
    }

    companion object {
        private const val PREFS_NAME = "pdfcraft_reader_prefs"
        private const val KEY_RECENTS = "recent_documents_list"
    }
}
