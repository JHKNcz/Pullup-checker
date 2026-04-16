package com.example.pullupchecker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.pullupchecker.ui.CoordinateTransformer
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private var pointPaint = Paint()
    private var linePaint = Paint()
    private var textPaint = Paint()
    private var angleTextPaint = Paint()
    
    // Status & Data
    private var currentStatus: FormStatus = FormStatus.NEUTRAL
    private var exerciseType: ExerciseType = ExerciseType.DETECTING
    
    // Pre-allocated buffers for rendering to avoid GC in onDraw
    private val lineBuffer = FloatArray(32) // 4 lines * 4 coords? We have ~8 lines. 8*4=32.
    private var pointBuffer = FloatArray(66) // 33 landmarks * 2 coords
    private var elbowAngleString: String = ""
    private var elbowPositionX: Float = 0f
    private var elbowPositionY: Float = 0f
    private var hasResults = false
    private var activeLineFloatCount = 0
    private var sourceFrameWidth = 1
    private var sourceFrameHeight = 1
    private var coordinateTransformer: CoordinateTransformer? = null

    init {
        initPaints()
    }

    private fun initPaints() {
        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = 10f
        pointPaint.style = Paint.Style.FILL
        pointPaint.strokeCap = Paint.Cap.ROUND

        linePaint.color = Color.CYAN
        linePaint.strokeWidth = 5f
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.isAntiAlias = true

        textPaint.color = Color.WHITE
        textPaint.textSize = 50f
        textPaint.style = Paint.Style.FILL
        
        angleTextPaint.color = Color.WHITE
        angleTextPaint.textSize = 40f
        angleTextPaint.style = Paint.Style.FILL
        angleTextPaint.setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    fun setResults(
        landmarks: List<NormalizedLandmark>,
        angle: Double,
        type: ExerciseType,
        frameWidth: Int,
        frameHeight: Int
    ) {
        hasResults = true
        exerciseType = type
        sourceFrameWidth = frameWidth.coerceAtLeast(1)
        sourceFrameHeight = frameHeight.coerceAtLeast(1)
        coordinateTransformer = CoordinateTransformer.fromImageToView(
            imageWidth = sourceFrameWidth.toFloat(),
            imageHeight = sourceFrameHeight.toFloat(),
            cropRectInImage = CoordinateTransformer.Rect(
                0f,
                0f,
                sourceFrameWidth.toFloat(),
                sourceFrameHeight.toFloat()
            ),
            rotationDegrees = 0,
            viewWidth = width.coerceAtLeast(1).toFloat(),
            viewHeight = height.coerceAtLeast(1).toFloat()
        )
        
        // Dynamic color
        linePaint.color = if (type == ExerciseType.CHIN_UP) Color.MAGENTA else Color.CYAN
        
        // Pre-format text
        elbowAngleString = "%.0f°".format(angle)
        
        // Fill Buffers
        // Lines: [x0,y0,x1,y1, x2,y2,x3,y3, ...]
        var lineIndex = 0
        
        // Function to add line to buffer
        fun addLine(startIdx: Int, endIdx: Int) {
            val s = landmarks[startIdx]
            val e = landmarks[endIdx]
            val (sx, sy) = mapToOverlayCoordinates(s.x(), s.y())
            val (ex, ey) = mapToOverlayCoordinates(e.x(), e.y())
            lineBuffer[lineIndex++] = sx
            lineBuffer[lineIndex++] = sy
            lineBuffer[lineIndex++] = ex
            lineBuffer[lineIndex++] = ey
        }
        
        // Define Skeleton Lines (Indices: 11=LSh, 12=RSh, 13=LEl, 14=REl, 15=LWr, 16=RWr, 23=LHip, 24=RHip)
        if (landmarks.size > 24) {
            lineIndex = 0
            addLine(11, 13) // L Arm
            addLine(13, 15)
            addLine(12, 14) // R Arm
            addLine(14, 16)
            addLine(11, 12) // Shoulders
            addLine(23, 24) // Hips
            addLine(11, 23) // L Body
            addLine(12, 24) // R Body
            activeLineFloatCount = lineIndex

            // Elbow Text Position
            val (elbowX, elbowY) = mapToOverlayCoordinates(landmarks[13].x(), landmarks[13].y())
            elbowPositionX = elbowX + 20f
            elbowPositionY = elbowY
        } else {
            activeLineFloatCount = 0
        }
        
        invalidate()
    }
    
    fun updateStatus(status: FormStatus) {
        currentStatus = status
        invalidate()
    }

    fun clear() {
        hasResults = false
        currentStatus = FormStatus.NEUTRAL
        activeLineFloatCount = 0
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        
        // Traffic Light
        pointPaint.color = currentStatus.color
        canvas.drawCircle(width - 80f, 80f, 40f, pointPaint)
        
        if (hasResults) {
            // Batch Draw Lines
            if (activeLineFloatCount > 0) {
                canvas.drawLines(lineBuffer, 0, activeLineFloatCount, linePaint)
            }
            
            // Batch Draw Key Points (re-using line logic or similar)
            // For simplicity, sticking to drawPoint for points is okay as there are few, 
            // but for max efficiency we could use drawPoints.
            // Let's just draw the key joints involved in the lines to keep visuals clean.
            pointPaint.color = Color.YELLOW
            // Iterate coords from line buffer? No, duplicate points.
            // Just raw draw for now, optimization of lines is the big win.
            
            // Draw Angle Text
            canvas.drawText(elbowAngleString, elbowPositionX, elbowPositionY, angleTextPaint)
        }
    }

    private fun mapToOverlayCoordinates(nx: Float, ny: Float): Pair<Float, Float> {
        val srcW = sourceFrameWidth.toFloat().coerceAtLeast(1f)
        val srcH = sourceFrameHeight.toFloat().coerceAtLeast(1f)
        val imagePoint = CoordinateTransformer.Point(
            x = nx.coerceIn(0f, 1f) * srcW,
            y = ny.coerceIn(0f, 1f) * srcH
        )
        val mapped = coordinateTransformer?.mapPoint(imagePoint) ?: imagePoint
        return mapped.x to mapped.y
    }
}
