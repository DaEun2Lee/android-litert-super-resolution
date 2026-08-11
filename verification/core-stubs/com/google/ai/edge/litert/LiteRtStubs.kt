package com.google.ai.edge.litert

import android.content.res.AssetManager

enum class Accelerator { CPU, GPU, NPU }

open class TensorBuffer : AutoCloseable {
    fun writeFloat(data: FloatArray) {}
    fun readFloat(): FloatArray = FloatArray(120000)
    override fun close() {}
}

open class CompiledModel : AutoCloseable {
    class Options(vararg accelerators: Accelerator)
    fun createInputBuffers(): List<TensorBuffer> = listOf(TensorBuffer())
    fun createOutputBuffers(): List<TensorBuffer> = listOf(TensorBuffer())
    fun run(inputs: List<TensorBuffer>, outputs: List<TensorBuffer>) {}
    override fun close() {}
    companion object {
        fun create(
            assetManager: AssetManager,
            assetName: String,
            options: Options,
            optionalEnv: Any?,
        ): CompiledModel = CompiledModel()
    }
}
