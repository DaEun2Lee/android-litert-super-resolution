package android.content
import android.content.res.AssetManager
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.InputStream
open class ContentResolver {
    open fun openInputStream(uri: Uri): InputStream? = ByteArrayInputStream(byteArrayOf())
}
open class Context {
    open val applicationContext: Context get() = this
    open val assets: AssetManager = AssetManager()
    open val contentResolver: ContentResolver = ContentResolver()
}
