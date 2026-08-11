package com.delee.srdemo.sr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/** Bitmap preprocessing and postprocessing for the bundled 4x SR model. */
object BitmapSrCodec {
    const val INPUT_WIDTH = 50
    const val INPUT_HEIGHT = 50
    const val OUTPUT_WIDTH = 200
    const val OUTPUT_HEIGHT = 200

    const val INPUT_FLOAT_COUNT = INPUT_WIDTH * INPUT_HEIGHT * 3
    const val OUTPUT_FLOAT_COUNT = OUTPUT_WIDTH * OUTPUT_HEIGHT * 3

    data class PreparedInput(
        val preview: Bitmap,
        val tensor: FloatArray,
    )

    /**
     * Center-crops the source to a square, scales it to 50x50, then emits
     * NHWC RGB Float32 values in the 0..255 range expected by ESRGAN.
     */
    fun prepareInput(source: Bitmap): PreparedInput {
        require(source.width > 0 && source.height > 0) { "Source bitmap is empty." }

        val cropSize = minOf(source.width, source.height)
        val left = (source.width - cropSize) / 2
        val top = (source.height - cropSize) / 2

        val prepared = Bitmap.createBitmap(
            INPUT_WIDTH,
            INPUT_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(prepared)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            source,
            Rect(left, top, left + cropSize, top + cropSize),
            Rect(0, 0, INPUT_WIDTH, INPUT_HEIGHT),
            paint,
        )

        val pixels = IntArray(INPUT_WIDTH * INPUT_HEIGHT)
        prepared.getPixels(
            pixels,
            0,
            INPUT_WIDTH,
            0,
            0,
            INPUT_WIDTH,
            INPUT_HEIGHT,
        )

        return PreparedInput(
            preview = prepared,
            tensor = ArgbTensorCodec.pixelsToRgbFloat(pixels),
        )
    }

    /** Converts NHWC RGB Float32 output in the 0..255 range to ARGB_8888. */
    fun outputToBitmap(output: FloatArray): Bitmap {
        require(output.size == OUTPUT_FLOAT_COUNT) {
            "Expected $OUTPUT_FLOAT_COUNT output values, but received ${output.size}."
        }

        val pixels = ArgbTensorCodec.rgbFloatToArgbPixels(
            data = output,
            pixelCount = OUTPUT_WIDTH * OUTPUT_HEIGHT,
        )

        return Bitmap.createBitmap(
            pixels,
            OUTPUT_WIDTH,
            OUTPUT_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
    }
}
