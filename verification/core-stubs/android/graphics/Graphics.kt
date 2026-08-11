package android.graphics

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
}
class Paint(flags: Int = 0) {
    companion object {
        const val ANTI_ALIAS_FLAG: Int = 1
        const val FILTER_BITMAP_FLAG: Int = 2
    }
}
class Rect(left: Int, top: Int, right: Int, bottom: Int)
