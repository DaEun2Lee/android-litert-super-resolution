package android.os
open class Bundle
object Build {
    object VERSION { const val SDK_INT: Int = 36 }
    object VERSION_CODES { const val P: Int = 28 }
}
object SystemClock { fun elapsedRealtimeNanos(): Long = System.nanoTime() }
