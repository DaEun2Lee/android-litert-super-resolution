package androidx.compose.foundation.layout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

class PaddingValues
interface RowScope { fun Modifier.weight(weight: Float): Modifier = this }
object Arrangement {
    fun spacedBy(space: Dp): Any = Any()
}
@Composable fun Column(
    modifier: Modifier = Modifier,
    verticalArrangement: Any? = null,
    content: @Composable () -> Unit,
) { content() }
@Composable fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Any? = null,
    content: @Composable RowScope.() -> Unit,
) { content(object : RowScope {}) }
@Composable fun Spacer(modifier: Modifier = Modifier) {}
fun Modifier.fillMaxSize(): Modifier = this
fun Modifier.fillMaxWidth(): Modifier = this
fun Modifier.height(height: Dp): Modifier = this
fun Modifier.padding(all: Dp): Modifier = this
fun Modifier.padding(paddingValues: PaddingValues): Modifier = this
