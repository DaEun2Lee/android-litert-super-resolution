package androidx.activity
import android.content.Context
import android.os.Bundle
open class ComponentActivity : Context() {
    open fun onCreate(savedInstanceState: Bundle?) {}
    open fun onDestroy() {}
}
fun ComponentActivity.enableEdgeToEdge() {}
