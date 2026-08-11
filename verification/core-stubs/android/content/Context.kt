package android.content
import android.content.res.AssetManager
open class Context {
    open val applicationContext: Context get() = this
    open val assets: AssetManager = AssetManager()
}
