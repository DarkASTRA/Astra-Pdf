package com.astra.pdf

import com.astra.pdf.bridge.AstraNativeBridge
import com.astra.pdf.model.LoadedDocument
import com.astra.pdf.ui.PageFitCalculator
import org.junit.Assert.*
import org.junit.Test

class ViewerLogicTest {

    @Test
    fun testPageRotationCycling() {
        var rot = 0
        rot = (rot + 90) % 360
        assertEquals(90, rot)

        rot = (rot + 90) % 360
        assertEquals(180, rot)

        rot = (rot + 90) % 360
        assertEquals(270, rot)

        rot = (rot + 90) % 360
        assertEquals(0, rot)
    }

    @Test
    fun testRotatedAspectRatioCalculation() {
        val width = 595f
        val height = 842f
        val rawAspect = width / height

        val aspect0 = rawAspect
        assertEquals(0.7066f, aspect0, 0.001f)

        val isSideways90 = true
        val effectiveAspect90 = if (isSideways90) 1.0f / rawAspect else rawAspect
        val expectedLandscapeAspect = height / width
        assertEquals(expectedLandscapeAspect, effectiveAspect90, 0.001f)
    }

    @Test
    fun testFitPagePortraitPhoneMode() {
        val layout = PageFitCalculator.calculateFit(
            pageWidthPt = 595f,
            pageHeightPt = 842f,
            rotationDegrees = 0,
            viewportWidthDp = 400f,
            viewportHeightDp = 800f,
            marginHorizontalDp = 16f,
            marginVerticalDp = 16f
        )
        val maxAvailableW = 400f - 32f
        val maxAvailableH = 800f - 32f

        assertTrue("Width ${layout.widthDp} must be <= $maxAvailableW", layout.widthDp <= maxAvailableW + 0.01f)
        assertTrue("Height ${layout.heightDp} must be <= $maxAvailableH", layout.heightDp <= maxAvailableH + 0.01f)

        val expectedAspect = 595f / 842f
        val actualAspect = layout.widthDp / layout.heightDp
        assertEquals(expectedAspect, actualAspect, 0.01f)
    }

    @Test
    fun testFitPageLandscapePhoneMode() {
        val layout = PageFitCalculator.calculateFit(
            pageWidthPt = 595f,
            pageHeightPt = 842f,
            rotationDegrees = 0,
            viewportWidthDp = 800f,
            viewportHeightDp = 400f,
            marginHorizontalDp = 16f,
            marginVerticalDp = 16f
        )
        val maxAvailableW = 800f - 32f
        val maxAvailableH = 400f - 32f

        assertTrue("Width ${layout.widthDp} must be <= $maxAvailableW", layout.widthDp <= maxAvailableW + 0.01f)
        assertTrue("Height ${layout.heightDp} must be <= $maxAvailableH", layout.heightDp <= maxAvailableH + 0.01f)
        assertEquals(maxAvailableH, layout.heightDp, 0.01f)

        val expectedAspect = 595f / 842f
        val actualAspect = layout.widthDp / layout.heightDp
        assertEquals(expectedAspect, actualAspect, 0.01f)
    }

    @Test
    fun testFitPageWith90DegreeRotation() {
        val layout = PageFitCalculator.calculateFit(
            pageWidthPt = 595f,
            pageHeightPt = 842f,
            rotationDegrees = 90,
            viewportWidthDp = 400f,
            viewportHeightDp = 800f,
            marginHorizontalDp = 16f,
            marginVerticalDp = 16f
        )
        val maxAvailableW = 400f - 32f
        val maxAvailableH = 800f - 32f

        assertTrue("Width ${layout.widthDp} must be <= $maxAvailableW", layout.widthDp <= maxAvailableW + 0.01f)
        assertTrue("Height ${layout.heightDp} must be <= $maxAvailableH", layout.heightDp <= maxAvailableH + 0.01f)

        val expectedAspect = 842f / 595f
        val actualAspect = layout.widthDp / layout.heightDp
        assertEquals(expectedAspect, actualAspect, 0.01f)
    }

    @Test
    fun testSearchResultModel() {
        val result = AstraNativeBridge.SearchResult(
            pageIndex = 2,
            matchCount = 4,
            contextSnippet = "Sample text match preview"
        )
        assertEquals(2, result.pageIndex)
        assertEquals(4, result.matchCount)
        assertEquals("Sample text match preview", result.contextSnippet)
    }

    @Test
    fun testLoadedDocumentSessionTracking() {
        val doc1 = LoadedDocument(
            uri = null,
            name = "first.pdf",
            handle = 1L,
            pageCount = 3,
            pageDimensions = listOf(Pair(595f, 842f)),
            sessionGeneration = 1L
        )

        val doc2 = LoadedDocument(
            uri = null,
            name = "second.pdf",
            handle = 2L,
            pageCount = 5,
            pageDimensions = listOf(Pair(612f, 792f)),
            sessionGeneration = 2L
        )

        assertNotEquals(doc1.sessionGeneration, doc2.sessionGeneration)
        assertNotEquals(doc1.handle, doc2.handle)
        assertEquals("first.pdf", doc1.name)
        assertEquals("second.pdf", doc2.name)
    }
}
