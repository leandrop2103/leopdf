package ai.storyteller.pdfcraft.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * High-performance, memory-bounded, zero-dependency PDF engine powered by Android's native PdfRenderer.
 * Built to be 100% resilient across all Android ContentProviders and storage schemes.
 */
class PdfEngine private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val tempCacheFile: File? = null
) : AutoCloseable {

    private val pageCountInternal: Int = renderer.pageCount
    val pageCount: Int get() = pageCountInternal

    private val renderLock = Any()
    private val dimensionsCache = arrayOfNulls<Pair<Int, Int>>(pageCountInternal)

    // LRU cache for 16 rendered page bitmaps to ensure instantaneous scroll recovery
    private val pageBitmapCache = object : LruCache<String, Bitmap>(16) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
            // Managed by Android GC
        }
    }

    /**
     * Gets page dimensions in points (1/72 inch), cached for O(1) instantaneous access.
     */
    fun getPageDimensions(pageIndex: Int): Pair<Int, Int> {
        if (pageIndex !in 0 until pageCountInternal) return Pair(595, 842) // Fallback standard A4
        dimensionsCache[pageIndex]?.let { return it }

        synchronized(renderLock) {
            dimensionsCache[pageIndex]?.let { return it }
            return try {
                val page = renderer.openPage(pageIndex)
                val dims = Pair(page.width.coerceAtLeast(1), page.height.coerceAtLeast(1))
                page.close()
                dimensionsCache[pageIndex] = dims
                dims
            } catch (e: Exception) {
                Pair(595, 842)
            }
        }
    }

    /**
     * Renders a page asynchronously to an Android Bitmap at target display density or width.
     */
    suspend fun renderPage(pageIndex: Int, targetWidth: Int = 0, densityDpi: Float = 2.0f): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex !in 0 until pageCountInternal) return@withContext null

        val cacheKey = "$pageIndex@$targetWidth@$densityDpi"
        pageBitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }

        synchronized(renderLock) {
            pageBitmapCache.get(cacheKey)?.let { cached ->
                if (!cached.isRecycled) return@withContext cached
            }

            try {
                val page = renderer.openPage(pageIndex)
                val pWidth = page.width.coerceAtLeast(1)
                val pHeight = page.height.coerceAtLeast(1)

                val (destW, destH) = if (targetWidth > 0) {
                    val scale = targetWidth.toFloat() / pWidth.toFloat()
                    Pair(targetWidth, (pHeight * scale).toInt().coerceAtLeast(1))
                } else {
                    Pair(
                        (pWidth * densityDpi).toInt().coerceAtLeast(1),
                        (pHeight * densityDpi).toInt().coerceAtLeast(1)
                    )
                }

                val bitmap = Bitmap.createBitmap(destW, destH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)

                val matrix = Matrix()
                val scaleX = destW.toFloat() / pWidth.toFloat()
                val scaleY = destH.toFloat() / pHeight.toFloat()
                matrix.postScale(scaleX, scaleY)

                page.render(
                    bitmap,
                    null,
                    matrix,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )
                page.close()

                pageBitmapCache.put(cacheKey, bitmap)
                bitmap
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun close() {
        synchronized(renderLock) {
            try {
                pageBitmapCache.evictAll()
                renderer.close()
                pfd.close()
                tempCacheFile?.delete()
            } catch (_: Exception) {}
        }
    }

    companion object {
        suspend fun open(context: Context, uri: Uri): PdfEngine = withContext(Dispatchers.IO) {
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            var tempFile: File? = null

            // Strategy 0: Check if we have an existing persistent local copy
            val savedLocal = ai.storyteller.pdfcraft.data.DocumentStorage.getLocalFile(context, uri.toString())
            if (savedLocal != null && savedLocal.exists() && savedLocal.canRead()) {
                try {
                    val directPfd = ParcelFileDescriptor.open(savedLocal, ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(directPfd)
                    pfd = directPfd
                } catch (_: Exception) {
                    pfd?.close()
                    pfd = null
                    renderer = null
                }
            }

            // Strategy 1: Direct File check if scheme is "file"
            if (renderer == null || pfd == null) {
                if (uri.scheme == "file") {
                    val filePath = uri.path
                    if (filePath != null) {
                        val file = File(filePath)
                        if (file.exists() && file.canRead()) {
                            try {
                                val directPfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                                renderer = PdfRenderer(directPfd)
                                pfd = directPfd
                            } catch (_: Exception) {
                                pfd?.close()
                                pfd = null
                                renderer = null
                            }
                        }
                    }
                }
            }

            // Strategy 2: Try direct contentResolver.openFileDescriptor
            if (renderer == null || pfd == null) {
                try {
                    val directPfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (directPfd != null) {
                        try {
                            renderer = PdfRenderer(directPfd)
                            pfd = directPfd
                        } catch (_: Exception) {
                            // File not seekable or unsupported direct descriptor
                            directPfd.close()
                            pfd = null
                            renderer = null
                        }
                    }
                } catch (_: Exception) {
                    pfd = null
                    renderer = null
                }
            }

            // Strategy 3: Save to persistent local document storage for offline & crash-free access
            if (renderer == null || pfd == null) {
                val savedFile = ai.storyteller.pdfcraft.data.DocumentStorage.savePdfLocally(context, uri)
                if (savedFile != null && savedFile.exists() && savedFile.length() > 0L) {
                    try {
                        val localPfd = ParcelFileDescriptor.open(savedFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        renderer = PdfRenderer(localPfd)
                        pfd = localPfd
                        tempFile = null // Keep persistent file, do not delete on close
                    } catch (_: Exception) {
                        pfd?.close()
                        pfd = null
                        renderer = null
                    }
                }
            }

            // Strategy 4: Fallback to cache directory copy
            if (renderer == null || pfd == null) {
                val cacheDir = File(context.cacheDir, "pdf_render_cache").apply { mkdirs() }
                val localTemp = File(cacheDir, "doc_${System.currentTimeMillis()}.pdf")

                val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Não foi possível acessar os dados do arquivo PDF selecionado. A permissão pode ter expirado.")

                inputStream.use { input ->
                    FileOutputStream(localTemp).use { output ->
                        input.copyTo(output)
                    }
                }

                if (!localTemp.exists() || localTemp.length() == 0L) {
                    localTemp.delete()
                    throw IllegalArgumentException("O arquivo selecionado está vazio ou não é um PDF válido.")
                }

                val localPfd = ParcelFileDescriptor.open(localTemp, ParcelFileDescriptor.MODE_READ_ONLY)
                    ?: throw IllegalArgumentException("Falha ao abrir descritor do arquivo.")

                try {
                    renderer = PdfRenderer(localPfd)
                    pfd = localPfd
                    tempFile = localTemp
                } catch (e: Exception) {
                    localPfd.close()
                    localTemp.delete()
                    throw IllegalStateException("O arquivo não pôde ser renderizado como PDF: ${e.message}")
                }
            }

            PdfEngine(pfd, renderer, tempFile)
        }
    }
}
