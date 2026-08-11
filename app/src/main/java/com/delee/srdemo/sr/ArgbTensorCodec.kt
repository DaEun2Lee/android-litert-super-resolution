package com.delee.srdemo.sr

import kotlin.math.roundToInt

/**
 * Pure Kotlin RGB/ARGB conversion helpers.
 *
 * Keeping this code free of Android classes makes the channel order and
 * clamping behavior testable on the host JVM.
 */
object ArgbTensorCodec {
    fun pixelsToRgbFloat(pixels: IntArray): FloatArray {
        val output = FloatArray(pixels.size * 3)
        var destination = 0

        for (pixel in pixels) {
            output[destination++] = ((pixel ushr 16) and 0xFF).toFloat()
            output[destination++] = ((pixel ushr 8) and 0xFF).toFloat()
            output[destination++] = (pixel and 0xFF).toFloat()
        }

        return output
    }

    fun rgbFloatToArgbPixels(data: FloatArray, pixelCount: Int): IntArray {
        require(pixelCount >= 0) { "pixelCount must be non-negative." }
        require(data.size == pixelCount * 3) {
            "Expected ${pixelCount * 3} RGB values, but received ${data.size}."
        }

        val pixels = IntArray(pixelCount)
        var source = 0

        for (index in pixels.indices) {
            val red = toByteValue(data[source++])
            val green = toByteValue(data[source++])
            val blue = toByteValue(data[source++])

            pixels[index] =
                (0xFF shl 24) or
                    (red shl 16) or
                    (green shl 8) or
                    blue
        }

        return pixels
    }

    private fun toByteValue(value: Float): Int {
        if (!value.isFinite()) return 0
        return value.coerceIn(0f, 255f).roundToInt()
    }
}
