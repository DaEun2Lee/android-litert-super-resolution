package androidx.compose.material3
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

class TextStyle
class Typography {
    val headlineSmall = TextStyle()
    val bodyMedium = TextStyle()
    val titleMedium = TextStyle()
    val bodySmall = TextStyle()
}
class Color
class ColorScheme { val error = Color() }
class Shape
class Shapes { val medium = Shape() }
object MaterialTheme {
    val typography = Typography()
    val colorScheme = ColorScheme()
    val shapes = Shapes()
}
@Composable fun Scaffold(
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) { content(PaddingValues()) }
@Composable fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
    fontWeight: Any? = null,
    color: Color? = null,
) {}
@Composable fun Button(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) { content() }
@Composable fun OutlinedButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) { content() }
@Composable fun Card(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) { content() }
@Composable fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    enabled: Boolean = true,
) { label() }
@Composable fun HorizontalDivider() {}
@Composable fun LinearProgressIndicator(modifier: Modifier = Modifier) {}
@Composable fun Surface(
    modifier: Modifier = Modifier,
    tonalElevation: Dp? = null,
    shape: Shape? = null,
    content: @Composable () -> Unit,
) { content() }
