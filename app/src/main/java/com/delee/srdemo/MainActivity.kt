package com.delee.srdemo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.delee.srdemo.sr.BackendPreference
import com.delee.srdemo.sr.SrRunner
import com.delee.srdemo.ui.SrScreen
import com.delee.srdemo.ui.theme.SrdemoTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var srRunner: SrRunner
    private var uiState by mutableStateOf(
        SrUiState(
            sourceBitmap = SampleImageFactory.create(),
            statusMessage = "LiteRT model is not initialized yet.",
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        srRunner = SrRunner(this)

        setContent {
            SrdemoTheme {
                SrScreen(
                    state = uiState,
                    onImageSelected = ::loadSelectedImage,
                    onBackendSelected = ::initializeModel,
                    onRunClicked = ::runSuperResolution,
                )
            }
        }

        initializeModel(BackendPreference.AUTO)
    }

    private fun initializeModel(preference: BackendPreference) {
        if (uiState.isBusy) return

        val previousPreference = uiState.backendPreference
        val previousBackend = uiState.activeBackend
        uiState = uiState.copy(
            backendPreference = preference,
            isBusy = true,
            statusMessage = "Initializing ${preference.name} backend and warming up...",
            errorMessage = null,
        )

        lifecycleScope.launch {
            try {
                val result = srRunner.initialize(preference)
                uiState = uiState.copy(
                    activeBackend = result.backend,
                    initializationMs = result.initializationMs,
                    isBusy = false,
                    isReady = true,
                    statusMessage = result.fallbackMessage
                        ?: "${result.backend.name} backend is ready.",
                    errorMessage = null,
                )
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                uiState = uiState.copy(
                    backendPreference = previousPreference,
                    activeBackend = previousBackend,
                    isBusy = false,
                    isReady = previousBackend != null,
                    statusMessage = "Backend initialization failed.",
                    errorMessage = failure.userMessage(),
                )
            }
        }
    }

    private fun runSuperResolution() {
        if (uiState.isBusy || !uiState.isReady) return

        val source = uiState.sourceBitmap
        uiState = uiState.copy(
            isBusy = true,
            statusMessage = "Running 4x super-resolution...",
            errorMessage = null,
        )

        lifecycleScope.launch {
            try {
                val result = srRunner.run(source)
                uiState = uiState.copy(
                    modelInputPreview = result.inputPreview,
                    outputBitmap = result.outputBitmap,
                    activeBackend = result.backend,
                    lastInference = result,
                    isBusy = false,
                    isReady = true,
                    statusMessage = "Inference completed on ${result.backend.name}.",
                )
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                uiState = uiState.copy(
                    isBusy = false,
                    statusMessage = "Inference failed.",
                    errorMessage = failure.userMessage(),
                )
            }
        }
    }

    private fun loadSelectedImage(uri: Uri?) {
        if (uri == null || uiState.isBusy) return

        uiState = uiState.copy(
            isBusy = true,
            statusMessage = "Loading selected image...",
            errorMessage = null,
        )

        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { decodeBitmap(uri) }
                uiState = uiState.copy(
                    sourceBitmap = bitmap,
                    modelInputPreview = null,
                    outputBitmap = null,
                    lastInference = null,
                    isBusy = false,
                    statusMessage = "Image loaded. Press Run SR.",
                )
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                uiState = uiState.copy(
                    isBusy = false,
                    statusMessage = "Image loading failed.",
                    errorMessage = failure.userMessage(),
                )
            }
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val largest = maxOf(width, height)
                if (largest > MAX_DECODE_SIZE) {
                    val scale = MAX_DECODE_SIZE.toFloat() / largest
                    decoder.setTargetSize(
                        (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            check(bounds.outWidth > 0 && bounds.outHeight > 0) {
                "Unable to read image dimensions."
            }

            var sampleSize = 1
            while (
                bounds.outWidth / sampleSize > MAX_DECODE_SIZE ||
                bounds.outHeight / sampleSize > MAX_DECODE_SIZE
            ) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: error("Unable to decode the selected image.")
        }
    }

    override fun onDestroy() {
        if (::srRunner.isInitialized) {
            srRunner.shutdownBlocking()
        }
        super.onDestroy()
    }

    private fun Throwable.userMessage(): String =
        message?.takeIf { it.isNotBlank() }
            ?: this::class.java.simpleName

    companion object {
        private const val MAX_DECODE_SIZE = 2048
    }
}
