import com.delee.srdemo.sr.ArgbTensorCodec

fun main() {
    val rgb = ArgbTensorCodec.pixelsToRgbFloat(
        intArrayOf(0xFF112233.toInt(), 0xFFA0B0C0.toInt())
    )
    check(rgb.contentEquals(floatArrayOf(17f, 34f, 51f, 160f, 176f, 192f))) {
        "RGB extraction failed: ${rgb.contentToString()}"
    }

    val argb = ArgbTensorCodec.rgbFloatToArgbPixels(
        floatArrayOf(-1f, 127.6f, 300f, Float.NaN, 1.4f, 2.6f),
        pixelCount = 2,
    )
    check(argb[0] == 0xFF0080FF.toInt()) { "Clamp/round failed: ${argb[0].toUInt().toString(16)}" }
    check(argb[1] == 0xFF000103.toInt()) { "NaN handling failed: ${argb[1].toUInt().toString(16)}" }

    println("PASS: ArgbTensorCodec host test")
}
