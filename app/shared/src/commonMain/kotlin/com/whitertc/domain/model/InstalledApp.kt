package com.whitertc.domain.model

import androidx.compose.ui.graphics.ImageBitmap

data class InstalledApp(
    val name: String,
    val packageName: String,
    val icon: ImageBitmap?,
    val isSystem: Boolean
)
