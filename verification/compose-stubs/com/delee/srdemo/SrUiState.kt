package com.delee.srdemo
import android.graphics.Bitmap
import com.delee.srdemo.sr.BackendPreference
import com.delee.srdemo.sr.ExecutionBackend
import com.delee.srdemo.sr.SrInferenceResult
data class SrUiState(
    val sourceBitmap: Bitmap,
    val modelInputPreview: Bitmap? = null,
    val outputBitmap: Bitmap? = null,
    val backendPreference: BackendPreference = BackendPreference.AUTO,
    val activeBackend: ExecutionBackend? = null,
    val initializationMs: Double? = null,
    val lastInference: SrInferenceResult? = null,
    val isBusy: Boolean = false,
    val isReady: Boolean = false,
    val statusMessage: String = "",
    val errorMessage: String? = null,
)
