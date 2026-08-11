package com.delee.srdemo

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delee.srdemo.sr.BackendPreference
import com.delee.srdemo.sr.BitmapSrCodec
import com.delee.srdemo.sr.ExecutionBackend
import com.delee.srdemo.sr.SrRunner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SrModelInstrumentedTest {
    @Test
    fun bundledModel_runsOnCpuAndReturnsExpectedBitmap() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val runner = SrRunner(context)

        try {
            val initialized = runner.initialize(BackendPreference.CPU)
            assertEquals(ExecutionBackend.CPU, initialized.backend)

            val result = runner.run(SampleImageFactory.create(size = 128))
            assertEquals(BitmapSrCodec.INPUT_WIDTH, result.inputPreview.width)
            assertEquals(BitmapSrCodec.INPUT_HEIGHT, result.inputPreview.height)
            assertEquals(BitmapSrCodec.OUTPUT_WIDTH, result.outputBitmap.width)
            assertEquals(BitmapSrCodec.OUTPUT_HEIGHT, result.outputBitmap.height)
            assertNotNull(result.outputBitmap)
        } finally {
            runner.shutdown()
        }
    }
}
