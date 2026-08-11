package android.graphics
import android.content.ContentResolver
import android.net.Uri
import java.io.InputStream

open class Bitmap(val width: Int, val height: Int) {
    enum class Config { ARGB_8888 }
    fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {}
    companion object {
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap = Bitmap(width, height)
        fun createBitmap(pixels: IntArray, width: Int, height: Int, config: Config): Bitmap = Bitmap(width, height)
    }
}
class Canvas(bitmap: Bitmap) {
    fun drawBitmap(bitmap: Bitmap, src: Rect, dst: Rect, paint: Paint) {}
    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {}
    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {}
    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {}
    fun drawText(text: String, x: Float, y: Float, paint: Paint) {}
}
open class Shader { enum class TileMode { CLAMP } }
class LinearGradient(
    x0: Float, y0: Float, x1: Float, y1: Float,
    color0: Int, color1: Int, tileMode: Shader.TileMode,
) : Shader()
class Paint(flags: Int = 0) {
    var shader: Shader? = null
    var color: Int = 0
    var strokeWidth: Float = 0f
    var style: Style = Style.FILL
    var textAlign: Align = Align.LEFT
    var textSize: Float = 0f
    var isFakeBoldText: Boolean = false
    enum class Style { FILL, STROKE }
    enum class Align { LEFT, CENTER, RIGHT }
    companion object {
        const val ANTI_ALIAS_FLAG: Int = 1
        const val FILTER_BITMAP_FLAG: Int = 2
    }
}
class Rect(left: Int, top: Int, right: Int, bottom: Int)
object Color {
    const val WHITE: Int = -1
    const val BLACK: Int = -16777216
    fun rgb(red: Int, green: Int, blue: Int): Int = 0
    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int = 0
}
object BitmapFactory {
    class Options {
        var inJustDecodeBounds: Boolean = false
        var outWidth: Int = 320
        var outHeight: Int = 320
        var inSampleSize: Int = 1
        var inPreferredConfig: Bitmap.Config? = null
    }
    fun decodeStream(input: InputStream?, outPadding: Rect?, opts: Options?): Bitmap? = Bitmap(320, 320)
}
class ImageDecoder {
    var allocator: Int = 0
    fun setTargetSize(width: Int, height: Int) {}
    class Source
    class ImageInfo(val size: Size = Size(320, 320))
    class Size(val width: Int, val height: Int)
    companion object {
        const val ALLOCATOR_SOFTWARE: Int = 1
        fun createSource(resolver: ContentResolver, uri: Uri): Source = Source()
        fun decodeBitmap(source: Source, listener: (ImageDecoder, ImageInfo, Source) -> Unit): Bitmap {
            listener(ImageDecoder(), ImageInfo(), source)
            return Bitmap(320, 320)
        }
    }
}
