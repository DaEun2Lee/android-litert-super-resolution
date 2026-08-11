package com.delee.srdemo.sr

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** Requested accelerator policy. AUTO tries GPU first and then CPU. */
enum class BackendPreference {
    AUTO,
    CPU,
    GPU,
}

enum class ExecutionBackend {
    CPU,
    GPU,
}

data class InitializationResult(
    val backend: ExecutionBackend,
    val initializationMs: Double,
    val fallbackMessage: String? = null,
)

data class SrInferenceResult(
    val inputPreview: Bitmap,
    val outputBitmap: Bitmap,
    val backend: ExecutionBackend,
    val preprocessingMs: Double,
    val inferenceAndReadbackMs: Double,
    val postprocessingMs: Double,
) {
    val totalMs: Double
        get() = preprocessingMs + inferenceAndReadbackMs + postprocessingMs
}

/**
 * Owns the LiteRT CompiledModel and reusable TensorBuffers.
 *
 * All create/run/close operations are confined to one dispatcher. The measured
 * inference duration includes output readback because readFloat() is the GPU
 * synchronization point.
 */
class SrRunner(
    context: Context,
    private val modelAssetName: String = MODEL_ASSET_NAME,
) {
    companion object {
        const val MODEL_ASSET_NAME = "sr_x4.tflite"
    }

    private val assetManager = context.applicationContext.assets
    private val modelDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SrModelDispatcher").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private var session: ModelSession? = null
    @Volatile private var isShutdown = false

    suspend fun initialize(preference: BackendPreference): InitializationResult =
        withContext(modelDispatcher) {
            check(!isShutdown) { "SrRunner has already been shut down." }
            val start = SystemClock.elapsedRealtimeNanos()
            val opened = when (preference) {
                BackendPreference.CPU -> OpenedSession(
                    session = openAndWarmUp(ExecutionBackend.CPU),
                    fallbackMessage = null,
                )

                BackendPreference.GPU -> OpenedSession(
                    session = openAndWarmUp(ExecutionBackend.GPU),
                    fallbackMessage = null,
                )

                BackendPreference.AUTO -> openAutomatically()
            }

            val previous = session
            session = opened.session
            previous?.close()

            InitializationResult(
                backend = opened.session.backend,
                initializationMs = nanosToMs(SystemClock.elapsedRealtimeNanos() - start),
                fallbackMessage = opened.fallbackMessage,
            )
        }

    suspend fun run(source: Bitmap): SrInferenceResult = withContext(modelDispatcher) {
        check(!isShutdown) { "SrRunner has already been shut down." }
        val activeSession = checkNotNull(session) {
            "The model is not initialized. Call initialize() first."
        }

        val preprocessingStart = SystemClock.elapsedRealtimeNanos()
        val prepared = BitmapSrCodec.prepareInput(source)
        check(prepared.tensor.size == BitmapSrCodec.INPUT_FLOAT_COUNT)
        val preprocessingEnd = SystemClock.elapsedRealtimeNanos()

        activeSession.inputBuffers.single().writeFloat(prepared.tensor)

        val inferenceStart = SystemClock.elapsedRealtimeNanos()
        activeSession.model.run(
            activeSession.inputBuffers,
            activeSession.outputBuffers,
        )
        val output = activeSession.outputBuffers.single().readFloat()
        val inferenceEnd = SystemClock.elapsedRealtimeNanos()

        val postprocessingStart = SystemClock.elapsedRealtimeNanos()
        val outputBitmap = BitmapSrCodec.outputToBitmap(output)
        val postprocessingEnd = SystemClock.elapsedRealtimeNanos()

        SrInferenceResult(
            inputPreview = prepared.preview,
            outputBitmap = outputBitmap,
            backend = activeSession.backend,
            preprocessingMs = nanosToMs(preprocessingEnd - preprocessingStart),
            inferenceAndReadbackMs = nanosToMs(inferenceEnd - inferenceStart),
            postprocessingMs = nanosToMs(postprocessingEnd - postprocessingStart),
        )
    }

    suspend fun shutdown() {
        if (isShutdown) return
        withContext(modelDispatcher) {
            session?.close()
            session = null
            isShutdown = true
        }
        modelDispatcher.close()
    }

    /** Safe to call from Activity.onDestroy() for deterministic native cleanup. */
    fun shutdownBlocking() {
        runBlocking { shutdown() }
    }

    private fun openAutomatically(): OpenedSession {
        return try {
            OpenedSession(
                session = openAndWarmUp(ExecutionBackend.GPU),
                fallbackMessage = null,
            )
        } catch (gpuFailure: Throwable) {
            if (gpuFailure is CancellationException) throw gpuFailure
            val message = buildString {
                append("GPU initialization failed; CPU was selected")
                gpuFailure.message?.takeIf { it.isNotBlank() }?.let {
                    append(": ")
                    append(it)
                }
            }
            OpenedSession(
                session = openAndWarmUp(ExecutionBackend.CPU),
                fallbackMessage = message,
            )
        }
    }

    private fun openAndWarmUp(backend: ExecutionBackend): ModelSession {
        val candidate = createSession(backend)
        return try {
            candidate.warmUp()
            candidate
        } catch (failure: Throwable) {
            candidate.close()
            throw failure
        }
    }

    private fun createSession(backend: ExecutionBackend): ModelSession {
        val accelerator = when (backend) {
            ExecutionBackend.CPU -> Accelerator.CPU
            ExecutionBackend.GPU -> Accelerator.GPU
        }

        val model = CompiledModel.create(
            assetManager,
            modelAssetName,
            CompiledModel.Options(accelerator),
            null,
        )

        var inputBuffers: List<TensorBuffer>? = null
        var outputBuffers: List<TensorBuffer>? = null

        try {
            inputBuffers = model.createInputBuffers()
            outputBuffers = model.createOutputBuffers()
            require(inputBuffers.size == 1) {
                "Expected one model input, but found ${inputBuffers.size}."
            }
            require(outputBuffers.size == 1) {
                "Expected one model output, but found ${outputBuffers.size}."
            }

            return ModelSession(
                model = model,
                inputBuffers = inputBuffers,
                outputBuffers = outputBuffers,
                backend = backend,
            )
        } catch (failure: Throwable) {
            inputBuffers?.forEach { runCatching { it.close() } }
            outputBuffers?.forEach { runCatching { it.close() } }
            runCatching { model.close() }
            throw failure
        }
    }

    private data class OpenedSession(
        val session: ModelSession,
        val fallbackMessage: String?,
    )

    private class ModelSession(
        val model: CompiledModel,
        val inputBuffers: List<TensorBuffer>,
        val outputBuffers: List<TensorBuffer>,
        val backend: ExecutionBackend,
    ) : AutoCloseable {
        fun warmUp() {
            inputBuffers.single().writeFloat(FloatArray(BitmapSrCodec.INPUT_FLOAT_COUNT))
            model.run(inputBuffers, outputBuffers)
            val output = outputBuffers.single().readFloat()
            require(output.size == BitmapSrCodec.OUTPUT_FLOAT_COUNT) {
                "Model output shape mismatch: expected ${BitmapSrCodec.OUTPUT_FLOAT_COUNT} " +
                    "values, received ${output.size}."
            }
        }

        override fun close() {
            inputBuffers.forEach { runCatching { it.close() } }
            outputBuffers.forEach { runCatching { it.close() } }
            runCatching { model.close() }
        }
    }

    private fun nanosToMs(nanos: Long): Double = nanos / 1_000_000.0
}
