package com.delee.srdemo.sr
import android.graphics.Bitmap
enum class BackendPreference { AUTO, CPU, GPU }
enum class ExecutionBackend { CPU, GPU }
data class SrInferenceResult(
    val inputPreview: Bitmap,
    val outputBitmap: Bitmap,
    val backend: ExecutionBackend,
    val preprocessingMs: Double,
    val inferenceAndReadbackMs: Double,
    val postprocessingMs: Double,
) { val totalMs: Double get() = preprocessingMs + inferenceAndReadbackMs + postprocessingMs }
