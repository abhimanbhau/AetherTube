@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.abhimankolte.aethertube.tv.ui.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import com.abhimankolte.aethertube.tv.ui.home.compose.HomeColorScheme

/**
 * The single entry point every Compose screen wraps its content in.
 *
 * Supplies the shared colour scheme (previously each fragment called
 * `MaterialTheme(colorScheme = HomeColorScheme)` itself, so a palette change had to be repeated in
 * four places) and resolves [LocalLowEndDevice] once, here, rather than having individual
 * composables poke at ActivityManager.
 */
@Composable
fun AetherTubeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lowEnd = remember(context) { DevicePerformance.shouldSkipEffects(context) }

    CompositionLocalProvider(LocalLowEndDevice provides lowEnd) {
        MaterialTheme(colorScheme = HomeColorScheme, content = content)
    }
}
