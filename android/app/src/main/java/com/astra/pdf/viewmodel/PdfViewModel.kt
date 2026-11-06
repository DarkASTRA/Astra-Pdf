package com.astra.pdf.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astra.pdf.bridge.AstraNativeBridge
import com.astra.pdf.data.DocumentRepository
import com.astra.pdf.model.LoadedDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface DocumentUiState {
    object Idle : DocumentUiState
    data class Loading(val message: String = "Loading PDF…") : DocumentUiState
    data class Success(val document: LoadedDocument) : DocumentUiState
    data class Error(val message: String, val isProtected: Boolean = false) : DocumentUiState
}

class PdfViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "PdfViewModel"
    val repository = DocumentRepository(application.applicationContext)

    private val _uiState = MutableStateFlow<DocumentUiState>(DocumentUiState.Idle)
    val uiState: StateFlow<DocumentUiState> = _uiState.asStateFlow()

    private val _pageRotations = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val pageRotations: StateFlow<Map<Int, Int>> = _pageRotations.asStateFlow()

    private val _isFindActive = MutableStateFlow(false)
    val isFindActive: StateFlow<Boolean> = _isFindActive.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<AstraNativeBridge.SearchResult>>(emptyList())
    val searchResults: StateFlow<List<AstraNativeBridge.SearchResult>> = _searchResults.asStateFlow()

    private val _currentMatchIndex = MutableStateFlow(0)
    val currentMatchIndex: StateFlow<Int> = _currentMatchIndex.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null
    private var loadJob: Job? = null

    private var currentSessionGeneration = 0L
    private var currentHandle: Long = 0L

    private val bitmapCache = object : LruCache<String, Bitmap>(36 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    fun openDocumentUri(uri: Uri) {
        loadJob?.cancel()
        searchJob?.cancel()

        loadJob = viewModelScope.launch {
            _uiState.value = DocumentUiState.Loading("Loading PDF…")
            _isFindActive.value = false
            _searchResults.value = emptyList()
            _pageRotations.value = emptyMap()

            val generation = ++currentSessionGeneration

            try {
                val (fileName, bytes) = withContext(Dispatchers.IO) {
                    repository.readDocumentBytes(uri)
                }

                closeCurrentNativeHandle()

                val handle = withContext(Dispatchers.Default) {
                    AstraNativeBridge.openDocument(bytes)
                }

                if (handle == 0L) {
                    throw IllegalStateException("Failed to parse PDF document.")
                }

                currentHandle = handle

                val pageCount = withContext(Dispatchers.Default) {
                    AstraNativeBridge.getPageCount(handle)
                }

                if (pageCount <= 0) {
                    throw IllegalStateException("Document has no pages.")
                }

                val dims = withContext(Dispatchers.Default) {
                    val list = mutableListOf<Pair<Float, Float>>()
                    for (i in 0 until pageCount) {
                        val d = AstraNativeBridge.getPageDimensions(handle, i)
                        list.add(Pair(d[0], d[1]))
                    }
                    list
                }

                val loaded = LoadedDocument(
                    uri = uri,
                    name = fileName,
                    handle = handle,
                    pageCount = pageCount,
                    pageDimensions = dims,
                    sessionGeneration = generation
                )

                _uiState.value = DocumentUiState.Success(loaded)
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                Log.e(TAG, "Failed opening document from $uri", e)
                val msg = e.message ?: "Unable to open this PDF."
                val isProtected = msg.contains("password", ignoreCase = true) ||
                        msg.contains("encrypt", ignoreCase = true)
                _uiState.value = DocumentUiState.Error(
                    if (isProtected) "This document is password protected. Astra PDF viewer does not currently support encrypted PDFs."
                    else msg,
                    isProtected = isProtected
                )
            }
        }
    }

    suspend fun renderPage(pageIndex: Int, scale: Float = 1.0f, rotationDegrees: Int = 0): Bitmap? {
        val generation = currentSessionGeneration
        val handle = currentHandle
        if (handle == 0L) return null

        val cacheKey = "${generation}_${pageIndex}_${scale}_${rotationDegrees}"
        val cached = bitmapCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        return withContext(Dispatchers.Default) {
            try {
                val baseCacheKey = "${generation}_${pageIndex}_${scale}_0"
                val baseBitmap = bitmapCache.get(baseCacheKey)?.let {
                    if (!it.isRecycled) it else null
                } ?: run {
                    val rendered = AstraNativeBridge.renderPageToBitmap(handle, pageIndex, scale)
                    bitmapCache.put(baseCacheKey, rendered)
                    rendered
                }

                val finalBitmap = if (rotationDegrees != 0) {
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    val rotated = Bitmap.createBitmap(baseBitmap, 0, 0, baseBitmap.width, baseBitmap.height, matrix, true)
                    bitmapCache.put(cacheKey, rotated)
                    rotated
                } else {
                    baseBitmap
                }
                finalBitmap
            } catch (e: Exception) {
                Log.e(TAG, "Error rendering page $pageIndex", e)
                null
            }
        }
    }

    fun rotateCurrentPage(pageIndex: Int) {
        val currentRot = _pageRotations.value[pageIndex] ?: 0
        val nextRot = (currentRot + 90) % 360
        val updated = _pageRotations.value.toMutableMap()
        if (nextRot == 0) {
            updated.remove(pageIndex)
        } else {
            updated[pageIndex] = nextRot
        }
        _pageRotations.value = updated
    }

    fun openFind() {
        _isFindActive.value = true
    }

    fun closeFind() {
        _isFindActive.value = false
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _currentMatchIndex.value = 0
        searchJob?.cancel()
    }

    fun performSearch(query: String) {
        val handle = currentHandle
        _searchQuery.value = query
        searchJob?.cancel()

        if (handle == 0L || query.isBlank()) {
            _searchResults.value = emptyList()
            _currentMatchIndex.value = 0
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            try {
                val results = withContext(Dispatchers.Default) {
                    AstraNativeBridge.searchText(handle, query)
                }
                _searchResults.value = results
                _currentMatchIndex.value = if (results.isNotEmpty()) 0 else 0
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "Search error", e)
                    _searchResults.value = emptyList()
                }
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun nextMatch() {
        val count = _searchResults.value.size
        if (count > 0) {
            _currentMatchIndex.value = (_currentMatchIndex.value + 1) % count
        }
    }

    fun prevMatch() {
        val count = _searchResults.value.size
        if (count > 0) {
            _currentMatchIndex.value = if (_currentMatchIndex.value - 1 < 0) count - 1 else _currentMatchIndex.value - 1
        }
    }

    private fun closeCurrentNativeHandle() {
        if (currentHandle != 0L) {
            val h = currentHandle
            currentHandle = 0L
            try {
                AstraNativeBridge.closeDocument(h)
            } catch (e: Exception) {
                Log.w(TAG, "Error closing document handle $h", e)
            }
        }
        bitmapCache.evictAll()
    }

    fun closeDocument() {
        loadJob?.cancel()
        searchJob?.cancel()
        closeCurrentNativeHandle()
        repository.cleanupSessionFiles()
        _uiState.value = DocumentUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        closeDocument()
    }
}
