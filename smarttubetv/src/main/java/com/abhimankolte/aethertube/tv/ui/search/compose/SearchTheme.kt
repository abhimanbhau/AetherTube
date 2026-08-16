@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.abhimankolte.aethertube.tv.ui.search.compose

import androidx.compose.runtime.Composable
import androidx.tv.material3.ColorScheme
import com.abhimankolte.aethertube.tv.ui.home.compose.HomeColorScheme

/**
 * Search used to carry its own vivid violet/cyan/magenta palette, distinct from every other Compose
 * screen - jumping into Search felt like a different app. Now shares [HomeColorScheme] with
 * Home/Settings/dialogs so the whole app reads as one consistent theme.
 */
@Composable
fun rememberSearchColorScheme(): ColorScheme = HomeColorScheme
