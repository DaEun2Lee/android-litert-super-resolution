package androidx.compose.foundation
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
@Composable fun Image(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: Any? = null,
    alignment: Any? = null,
) {}
class ScrollState
fun rememberScrollState(): ScrollState = ScrollState()
fun Modifier.verticalScroll(state: ScrollState): Modifier = this
