package androidx.compose.ui.res
import androidx.compose.runtime.Composable
@Composable fun stringResource(id: Int): String = "string-$id"
@Composable fun stringResource(id: Int, vararg formatArgs: Any): String = "string-$id"
