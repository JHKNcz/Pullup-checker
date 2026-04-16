package com.example.pullupchecker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
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

    fun setResults(landmarks: List<NormalizedLandmark>, angle: Double, type: ExerciseType) {
        hasResults = true
        exerciseType = type
        
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
            lineBuffer[lineIndex++] = s.x() * width
            lineBuffer[lineIndex++] = s.y() * height
            lineBuffer[lineIndex++] = e.x() * width
            lineBuffer[lineIndex++] = e.y() * height
        }
        
        // Define Skeleton Lines (Indices: 11=LSh, 12=RSh, 13=LEl, 14=REl, 15=LWr, 16=RWr, 23=LHip, 24=RHip)
        if (landmarks.size > 24) {
            try {
                // Reset index or handle fixed size
                lineIndex = 0
                addLine(11, 13) // L Arm
                addLine(13, 15)
                addLine(12, 14) // R Arm
                addLine(14, 16)
                addLine(11, 12) // Shoulders
                addLine(23, 24) // Hips
                addLine(11, 23) // L Body
                addLine(12, 24) // R Body
                
                // Points
                // We only care about drawn points, but let's just pointify all of them or the active ones
                // Drawing 33 points individually is slow. batchPoints is better.
                // Or just draw key ones.
                // Actually canvas.drawPoints takes a float array too.
            } catch (e: Exception) {
                // Safety catch for index oob
            }
            
            // Elbow Text Position
            elbowPositionX = landmarks[13].x() * width + 20
            elbowPositionY = landmarks[13].y() * height
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
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        
        // Traffic Light
        pointPaint.color = currentStatus.color
        canvas.drawCircle(width - 80f, 80f, 40f, pointPaint)
        
        if (hasResults) {
            // Batch Draw Lines
            canvas.drawLines(lineBuffer, linePaint)
            
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
}
