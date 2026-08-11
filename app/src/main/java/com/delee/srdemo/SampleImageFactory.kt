package com.delee.srdemo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader

/** Creates an offline sample image, so the app can run without media permissions. */
object SampleImageFactory {
    fun create(size: Int = 320): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                size.toFloat(),
                size.toFloat(),
                Color.rgb(20, 42, 76),
                Color.rgb(235, 196, 92),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), background)

        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 255, 255, 255)
            strokeWidth = 2f
        }
        val step = size / 10f
        for (index in 1 until 10) {
            val position = index * step
            canvas.drawLine(position, 0f, position, size.toFloat(), grid)
            canvas.drawLine(0f, position, size.toFloat(), position, grid)
        }

        val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 45, 62)
        }
        canvas.drawCircle(size * 0.30f, size * 0.32f, size * 0.17f, circle)

        val outlinedCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = size * 0.025f
        }
        canvas.drawCircle(size * 0.71f, size * 0.33f, size * 0.17f, outlinedCircle)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = size * 0.18f
            isFakeBoldText = true
        }
        canvas.drawText("SR x4", size / 2f, size * 0.78f, text)

        val detail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 1f
        }
        for (index in 0 until 28) {
            val x = size * 0.12f + index * size * 0.028f
            canvas.drawLine(x, size * 0.86f, x + size * 0.06f, size * 0.94f, detail)
        }

        return bitmap
    }
}
