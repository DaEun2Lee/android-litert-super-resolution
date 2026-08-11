package com.delee.srdemo.sr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ArgbTensorCodecTest {
    @Test
    fun pixelsToRgbFloat_preservesRgbChannelOrder() {
        val input = intArrayOf(0xFF112233.toInt(), 0xFFA0B0C0.toInt())

        val result = ArgbTensorCodec.pixelsToRgbFloat(input)

        assertArrayEquals(
            floatArrayOf(17f, 34f, 51f, 160f, 176f, 192f),
            result,
            0f,
        )
    }

    @Test
    fun rgbFloatToArgbPixels_clampsRoundsAndSetsOpaqueAlpha() {
        val result = ArgbTensorCodec.rgbFloatToArgbPixels(
            data = floatArrayOf(-1f, 127.6f, 300f, Float.NaN, 1.4f, 2.6f),
            pixelCount = 2,
        )

        assertEquals(0xFF0080FF.toInt(), result[0])
        assertEquals(0xFF000103.toInt(), result[1])
    }
}
