package com.example.pullupchecker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateTransformerTest {
    @Test
    fun shouldMapImageCropRectToPreviewBounds() {
        val cropRect = CoordinateTransformer.Rect(100f, 50f, 500f, 350f)
        val transformer = CoordinateTransformer.fromImageToView(
            imageWidth = 640f,
            imageHeight = 480f,
            cropRectInImage = cropRect,
            rotationDegrees = 0,
            viewWidth = 800f,
            viewHeight = 600f
        )

        val mapped = transformer.mapRect(cropRect)

        assertEquals(0f, mapped.left, EPSILON)
        assertEquals(0f, mapped.top, EPSILON)
        assertEquals(800f, mapped.right, EPSILON)
        assertEquals(600f, mapped.bottom, EPSILON)
    }

    @Test
    fun shouldHandle90DegreeRotationWithCenterCropAssumptions() {
        val cropRect = CoordinateTransformer.Rect(0f, 0f, 640f, 480f)
        val transformer = CoordinateTransformer.fromImageToView(
            imageWidth = 640f,
            imageHeight = 480f,
            cropRectInImage = cropRect,
            rotationDegrees = 90,
            viewWidth = 480f,
            viewHeight = 640f
        )

        val mappedBounds = transformer.mapRect(cropRect)
        assertEquals(0f, mappedBounds.left, EPSILON)
        assertEquals(0f, mappedBounds.top, EPSILON)
        assertEquals(480f, mappedBounds.right, EPSILON)
        assertEquals(640f, mappedBounds.bottom, EPSILON)

        val topCenter = transformer.mapPoint(CoordinateTransformer.Point(320f, 0f))
        assertEquals(0f, topCenter.x, EPSILON)
        assertEquals(320f, topCenter.y, EPSILON)
    }

    companion object {
        private const val EPSILON = 0.001f
    }
}
