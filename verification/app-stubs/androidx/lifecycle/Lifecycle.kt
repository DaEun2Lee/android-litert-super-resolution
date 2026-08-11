package androidx.lifecycle
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
val ComponentActivity.lifecycleScope: CoroutineScope
    get() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
