package com.delee.srdemo.ui
import android.net.Uri
import com.delee.srdemo.SrUiState
import com.delee.srdemo.sr.BackendPreference
fun SrScreen(
    state: SrUiState,
    onImageSelected: (Uri?) -> Unit,
    onBackendSelected: (BackendPreference) -> Unit,
    onRunClicked: () -> Unit,
) {}
