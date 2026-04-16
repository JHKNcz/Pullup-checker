package com.example.pullupchecker.ui

import kotlin.math.max
import kotlin.math.min

/**
 * Maps points and rectangles from image coordinates into preview view coordinates.
 *
 * The transform pipeline is:
 * 1) Move from full image space into crop-local space.
 * 2) Rotate around the crop center.
 * 3) Normalize rotated bounds to origin.
 * 4) Apply center-crop scale and offset into the view bounds.
 */
class CoordinateTransformer private constructor(
    private val matrix: Matrix3
) {
    data class Point(val x: Float, val y: Float)

    data class Rect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val centerX: Float get() = left + width / 2f
        val centerY: Float get() = top + height / 2f
    }

    fun mapPoint(point: Point): Point = matrix.mapPoint(point)

    fun mapRect(rect: Rect): Rect {
        val p1 = mapPoint(Point(rect.left, rect.top))
        val p2 = mapPoint(Point(rect.right, rect.top))
        val p3 = mapPoint(Point(rect.right, rect.bottom))
        val p4 = mapPoint(Point(rect.left, rect.bottom))

        return Rect(
            left = min(min(p1.x, p2.x), min(p3.x, p4.x)),
            top = min(min(p1.y, p2.y), min(p3.y, p4.y)),
            right = max(max(p1.x, p2.x), max(p3.x, p4.x)),
            bottom = max(max(p1.y, p2.y), max(p3.y, p4.y))
        )
    }

    companion object {
        fun fromImageToView(
            imageWidth: Float,
            imageHeight: Float,
            cropRectInImage: Rect,
            rotationDegrees: Int,
            viewWidth: Float,
            viewHeight: Float
        ): CoordinateTransformer {
            require(imageWidth > 0f && imageHeight > 0f) { "Image size must be > 0." }
            require(cropRectInImage.width > 0f && cropRectInImage.height > 0f) {
                "Crop rect must have positive width and height."
            }
            require(viewWidth > 0f && viewHeight > 0f) { "View size must be > 0." }
            require(rotationDegrees in setOf(0, 90, 180, 270)) {
                "Rotation must be 0, 90, 180, or 270 degrees."
            }

            val toCropLocal = Matrix3.translation(-cropRectInImage.left, -cropRectInImage.top)

            val cropCenterX = cropRectInImage.width / 2f
            val cropCenterY = cropRectInImage.height / 2f
            val rotateAroundCenter = Matrix3.translation(cropCenterX, cropCenterY)
                .multiply(Matrix3.rotation(rotationDegrees))
                .multiply(Matrix3.translation(-cropCenterX, -cropCenterY))

            val cropCorners = listOf(
                Point(0f, 0f),
                Point(cropRectInImage.width, 0f),
                Point(cropRectInImage.width, cropRectInImage.height),
                Point(0f, cropRectInImage.height)
            )
            val rotatedCorners = cropCorners.map(rotateAroundCenter::mapPoint)
            val rotatedBounds = boundsOf(rotatedCorners)

            val normalizeRotated = Matrix3.translation(-rotatedBounds.left, -rotatedBounds.top)

            val contentWidth = rotatedBounds.width
            val contentHeight = rotatedBounds.height
            val scale = max(viewWidth / contentWidth, viewHeight / contentHeight)
            val scaledWidth = contentWidth * scale
            val scaledHeight = contentHeight * scale
            val offsetX = (viewWidth - scaledWidth) / 2f
            val offsetY = (viewHeight - scaledHeight) / 2f

            val centerCropToView = Matrix3.translation(offsetX, offsetY)
                .multiply(Matrix3.scale(scale, scale))

            val finalMatrix = centerCropToView
                .multiply(normalizeRotated)
                .multiply(rotateAroundCenter)
                .multiply(toCropLocal)

            return CoordinateTransformer(finalMatrix)
        }

        private fun boundsOf(points: List<Point>): Rect {
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY

            for (point in points) {
                minX = min(minX, point.x)
                minY = min(minY, point.y)
                maxX = max(maxX, point.x)
                maxY = max(maxY, point.y)
            }

            return Rect(minX, minY, maxX, maxY)
        }
    }

    private data class Matrix3(private val m: FloatArray) {
        fun multiply(other: Matrix3): Matrix3 {
            val result = FloatArray(9)
            for (row in 0..2) {
                for (col in 0..2) {
                    var value = 0f
                    for (k in 0..2) {
                        value += m[row * 3 + k] * other.m[k * 3 + col]
                    }
                    result[row * 3 + col] = value
                }
            }
            return Matrix3(result)
        }

        fun mapPoint(point: Point): Point {
            val x = point.x
            val y = point.y
            val mappedX = m[0] * x + m[1] * y + m[2]
            val mappedY = m[3] * x + m[4] * y + m[5]
            val w = m[6] * x + m[7] * y + m[8]
            val normalizedW = if (w == 0f) 1f else w
            return Point(mappedX / normalizedW, mappedY / normalizedW)
        }

        companion object {
            fun translation(tx: Float, ty: Float): Matrix3 = Matrix3(
                floatArrayOf(
                    1f, 0f, tx,
                    0f, 1f, ty,
                    0f, 0f, 1f
                )
            )

            fun scale(sx: Float, sy: Float): Matrix3 = Matrix3(
                floatArrayOf(
                    sx, 0f, 0f,
                    0f, sy, 0f,
                    0f, 0f, 1f
                )
            )

            fun rotation(degrees: Int): Matrix3 {
                // Clockwise rotations for image-to-view mapping.
                return when (degrees) {
                    0 -> identity()
                    90 -> Matrix3(
                        floatArrayOf(
                            0f, 1f, 0f,
                            -1f, 0f, 0f,
                            0f, 0f, 1f
                        )
                    )
                    180 -> Matrix3(
                        floatArrayOf(
                            -1f, 0f, 0f,
                            0f, -1f, 0f,
                            0f, 0f, 1f
                        )
                    )
                    270 -> Matrix3(
                        floatArrayOf(
                            0f, -1f, 0f,
                            1f, 0f, 0f,
                            0f, 0f, 1f
                        )
                    )
                    else -> throw IllegalArgumentException("Unsupported rotation: $degrees")
                }
            }

            fun identity(): Matrix3 = Matrix3(
                floatArrayOf(
                    1f, 0f, 0f,
                    0f, 1f, 0f,
                    0f, 0f, 1f
                )
            )
        }
    }
}
