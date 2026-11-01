package ai.storyteller.pdfcraft

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * JNI bridge and high-level Kotlin API for the PdfCraft engine on Android.
 */
object PdfCraftCore {

    init {
        try {
            System.loadLibrary("pdfcraft_android")
        } catch (e: UnsatisfiedLinkError) {
            System.err.println("Failed to load libpdfcraft_android.so: ${e.message}")
        }
    }

    // Native JNI C-bindings
    @JvmStatic
    external fun nativeVersion(): Int

    @JvmStatic
    external fun nativeOpen(bytes: ByteArray, password: String?): Long

    @JvmStatic
    external fun nativeClose(handle: Long)

    @JvmStatic
    external fun nativePageCount(handle: Long): Int

    @JvmStatic
    external fun nativePageDimensions(handle: Long, pageIndex: Int): FloatArray?

    @JvmStatic
    external fun nativeRenderPageToBytes(handle: Long, pageIndex: Int, scale: Float): ByteArray?

    @JvmStatic
    external fun nativeRenderPageInto(
        handle: Long,
        pageIndex: Int,
        scale: Float,
        outBuffer: ByteArray
    ): Boolean

    /**
     * Managed PDF Document reference.
     */
    class Document internal constructor(private val handle: Long) : AutoCloseable {
        private var isClosed = false

        val pageCount: Int
            get() {
                checkNotClosed()
                return nativePageCount(handle)
            }

        fun getPageDimensions(pageIndex: Int): Pair<Float, Float>? {
            checkNotClosed()
            val dims = nativePageDimensions(handle, pageIndex) ?: return null
            if (dims.size < 2) return null
            return Pair(dims[0], dims[1])
        }

        /**
         * Renders a specific page at the given zoom scale to an Android ARGB_8888 [Bitmap].
         */
        fun renderPage(pageIndex: Int, scale: Float = 1.0f): Bitmap? {
            checkNotClosed()
            if (scale <= 0.0f) return null

            val dims = getPageDimensions(pageIndex) ?: return null
            val width = (dims.first * scale).toInt().coerceAtLeast(1)
            val height = (dims.second * scale).toInt().coerceAtLeast(1)

            val bytes = nativeRenderPageToBytes(handle, pageIndex, scale) ?: return null
            if (bytes.size < width * height * 4) return null

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
            return bitmap
        }

        private fun checkNotClosed() {
            if (isClosed) {
                throw IllegalStateException("Document has already been closed")
            }
        }

        override fun close() {
            if (!isClosed) {
                isClosed = true
                nativeClose(handle)
            }
        }
    }

    /**
     * Opens a PDF document from raw in-memory bytes.
     */
    fun open(bytes: ByteArray, password: String? = null): Document {
        val handle = nativeOpen(bytes, password)
        if (handle <= 0L) {
            throw IllegalArgumentException("Could not parse or open PDF document")
        }
        return Document(handle)
    }

    /**
     * Opens a PDF document from an [InputStream].
     */
    fun open(stream: InputStream, password: String? = null): Document {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(16384)
        var read: Int
        while (stream.read(chunk).also { read = it } != -1) {
            buffer.write(chunk, 0, read)
        }
        return open(buffer.toByteArray(), password)
    }
}
