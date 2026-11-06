package com.astra.pdf.ui

object PageFitCalculator {

    data class PageLayout(
        val widthDp: Float,
        val heightDp: Float
    )

    fun calculateFit(
        pageWidthPt: Float,
        pageHeightPt: Float,
        rotationDegrees: Int,
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        marginHorizontalDp: Float = 16f,
        marginVerticalDp: Float = 16f
    ): PageLayout {
        val isSideways = rotationDegrees == 90 || rotationDegrees == 270
        val effectiveWidthPt = if (isSideways) pageHeightPt else pageWidthPt
        val effectiveHeightPt = if (isSideways) pageWidthPt else pageHeightPt
        val effectiveAspect = if (effectiveHeightPt > 0f) effectiveWidthPt / effectiveHeightPt else 0.707f

        val availableWidth = (viewportWidthDp - marginHorizontalDp * 2).coerceAtLeast(50f)
        val availableHeight = (viewportHeightDp - marginVerticalDp * 2).coerceAtLeast(50f)
        val viewportAspect = availableWidth / availableHeight

        return if (effectiveAspect > viewportAspect) {
            val w = availableWidth
            val h = availableWidth / effectiveAspect
            PageLayout(w, h)
        } else {
            val h = availableHeight
            val w = availableHeight * effectiveAspect
            PageLayout(w, h)
        }
    }
}
