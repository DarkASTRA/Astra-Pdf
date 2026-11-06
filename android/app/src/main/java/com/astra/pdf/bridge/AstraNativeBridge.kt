package com.astra.pdf.bridge

import android.graphics.Bitmap
import android.util.Log
import org.json.JSONArray
import java.nio.ByteBuffer

object AstraNativeBridge {
    private const val TAG = "AstraNativeBridge"

    init {
        try {
            System.loadLibrary("astra_pdf_bridge")
            Log.i(TAG, "Native library 'astra_pdf_bridge' successfully loaded.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library 'astra_pdf_bridge'", e)
            throw e
        }
    }

    data class SearchResult(
        val pageIndex: Int,
        val matchCount: Int,
        val contextSnippet: String
    )

    @JvmStatic
    private external fun nativeOpenDocument(bytes: ByteArray, password: String?): Long

    @JvmStatic
    private external fun nativeCloseDocument(handle: Long)

    @JvmStatic
    private external fun nativeGetPageCount(handle: Long): Int

    @JvmStatic
    private external fun nativeGetPageDimensions(handle: Long, pageIndex: Int): FloatArray

    @JvmStatic
    private external fun nativeGetMetadataJson(handle: Long): String

    @JvmStatic
    private external fun nativeRenderPage(handle: Long, pageIndex: Int, scale: Float): ByteArray

    @JvmStatic
    private external fun nativeSearchTextJson(handle: Long, query: String): String

    fun openDocument(bytes: ByteArray, password: String? = null): Long {
        return nativeOpenDocument(bytes, password)
    }

    fun closeDocument(handle: Long) {
        if (handle != 0L) {
            nativeCloseDocument(handle)
        }
    }

    fun getPageCount(handle: Long): Int {
        return nativeGetPageCount(handle)
    }

    fun getPageDimensions(handle: Long, pageIndex: Int): FloatArray {
        return nativeGetPageDimensions(handle, pageIndex)
    }

    fun getMetadataJson(handle: Long): String {
        return nativeGetMetadataJson(handle)
    }

    fun renderPage(handle: Long, pageIndex: Int, scale: Float = 1.0f): ByteArray {
        return nativeRenderPage(handle, pageIndex, scale)
    }

    fun searchText(handle: Long, query: String): List<SearchResult> {
        val list = mutableListOf<SearchResult>()
        if (query.isBlank()) return list
        return try {
            val json = nativeSearchTextJson(handle, query)
            if (json.isNotBlank()) {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        SearchResult(
                            pageIndex = obj.getInt("page_index"),
                            matchCount = obj.getInt("match_count"),
                            contextSnippet = obj.getString("snippet")
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Search parsing failed", e)
            emptyList()
        }
    }

    fun renderPageToBitmap(handle: Long, pageIndex: Int, scale: Float = 1.0f): Bitmap {
        val raw = renderPage(handle, pageIndex, scale)
        if (raw.size < 8) {
            throw IllegalStateException("Invalid pixel buffer returned from native render (size: ${raw.size})")
        }

        val width = ((raw[0].toInt() and 0xFF) shl 24) or
                ((raw[1].toInt() and 0xFF) shl 16) or
                ((raw[2].toInt() and 0xFF) shl 8) or
                (raw[3].toInt() and 0xFF)

        val height = ((raw[4].toInt() and 0xFF) shl 24) or
                ((raw[5].toInt() and 0xFF) shl 16) or
                ((raw[6].toInt() and 0xFF) shl 8) or
                (raw[7].toInt() and 0xFF)

        if (width <= 0 || height <= 0) {
            throw IllegalStateException("Invalid dimensions received: ${width}x${height}")
        }

        val pixelByteCount = raw.size - 8
        val expectedPixelBytes = width * height * 4
        if (pixelByteCount != expectedPixelBytes) {
            Log.w(TAG, "Pixel byte count mismatch: got $pixelByteCount, expected $expectedPixelBytes")
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val byteBuffer = ByteBuffer.wrap(raw, 8, pixelByteCount)
        bitmap.copyPixelsFromBuffer(byteBuffer)
        return bitmap
    }
}
