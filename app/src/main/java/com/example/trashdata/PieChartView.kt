package com.example.trashdata

import android.content.Context
import android.graphics.*
import android.view.View

class PieChartView(context: Context) : View(context) {

    private val paint = Paint()
    private var values = listOf<Float>()

    fun setData(data: List<Float>) {
        values = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (values.isEmpty()) return

        val total = values.sum()
        var startAngle = 0f

        val colors = listOf(
            Color.parseColor("#1A73E8"), // old
            Color.parseColor("#EA4335"), // large
            Color.parseColor("#FBBC05"), // apk
            Color.parseColor("#34A853"), // temp
            Color.GRAY                   // other
        )

        val rect = RectF(50f,50f,width-50f,height-50f)

        for(i in values.indices){

            val sweep = values[i] / total * 360f

            paint.color = colors[i % colors.size]

            canvas.drawArc(rect,startAngle,sweep,true,paint)

            startAngle += sweep
        }
    }
}