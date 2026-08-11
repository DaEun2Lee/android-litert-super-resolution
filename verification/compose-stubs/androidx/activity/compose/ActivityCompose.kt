package androidx.activity.compose
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
class ManagedActivityResultLauncher<I, O> { fun launch(input: I) {} }
fun rememberLauncherForActivityResult(
    contract: ActivityResultContracts.OpenDocument,
    onResult: (Uri?) -> Unit,
): ManagedActivityResultLauncher<Array<String>, Uri?> = ManagedActivityResultLauncher()
