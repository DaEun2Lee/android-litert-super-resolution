package com.delee.srdemo.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.delee.srdemo.R
import com.delee.srdemo.SrUiState
import com.delee.srdemo.sr.BackendPreference
import java.util.Locale

@Composable
fun SrScreen(
    state: SrUiState,
    onImageSelected: (android.net.Uri?) -> Unit,
    onBackendSelected: (BackendPreference) -> Unit,
    onRunClicked: () -> Unit,
) {
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = onImageSelected,
    )

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.app_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            StatusCard(state)

            Text(
                text = stringResource(R.string.accelerator),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BackendPreference.entries.forEach { preference ->
                    FilterChip(
                        selected = state.backendPreference == preference,
                        onClick = { onBackendSelected(preference) },
                        label = { Text(preference.name) },
                        enabled = !state.isBusy,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { imagePicker.launch(arrayOf("image/*")) },
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.choose_image))
                }
                Button(
                    onClick = onRunClicked,
                    enabled = state.isReady && !state.isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.run_sr))
                }
            }

            ImagePanel(
                title = stringResource(R.string.source_image),
                bitmap = state.sourceBitmap,
            )

            state.modelInputPreview?.let {
                ImagePanel(
                    title = stringResource(R.string.model_input),
                    bitmap = it,
                )
            }

            state.outputBitmap?.let {
                ImagePanel(
                    title = stringResource(R.string.sr_output),
                    bitmap = it,
                )
            }

            state.lastInference?.let { result ->
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.timing_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        R.string.timing_format,
                        formatMs(result.preprocessingMs),
                        formatMs(result.inferenceAndReadbackMs),
                        formatMs(result.postprocessingMs),
                        formatMs(result.totalMs),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                text = stringResource(R.string.model_notice),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StatusCard(state: SrUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.status),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(state.statusMessage)
            Text(
                text = stringResource(
                    R.string.active_backend,
                    state.activeBackend?.name ?: "-",
                ),
            )
            state.initializationMs?.let {
                Text(stringResource(R.string.initialization_time, formatMs(it)))
            }
            state.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ImagePanel(
    title: String,
    bitmap: Bitmap,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "$title (${bitmap.width} x ${bitmap.height})",
            style = MaterialTheme.typography.titleMedium,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
            )
        }
    }
}

private fun formatMs(value: Double): String =
    String.format(Locale.US, "%.2f ms", value)
