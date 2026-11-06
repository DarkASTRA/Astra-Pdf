package com.astra.pdf.model

import android.net.Uri

data class LoadedDocument(
    val uri: Uri?,
    val name: String,
    val handle: Long,
    val pageCount: Int,
    val pageDimensions: List<Pair<Float, Float>>,
    val sessionGeneration: Long
)
