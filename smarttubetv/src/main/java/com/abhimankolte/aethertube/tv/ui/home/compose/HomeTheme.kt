@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.abhimankolte.aethertube.tv.ui.home.compose

import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.darkColorScheme

/**
 * The app's single shared colour scheme - every Compose screen (Home, Search, Settings, dialogs)
 * uses this, not a per-screen palette.
 *
 * Derived from the app icon rather than picked independently. Sampling the logo gives a violet body
 * (hue 255-280, dominant #301860 / #481878), a cyan accent at its brightest point (#68C9F3) and a
 * pale lavender specular highlight (#CFD2FF) - so those become primary, secondary and tertiary here.
 * The previous palette was a soft periwinkle unrelated to the icon, which made the launcher tile and
 * the app it opened look like two different products.
 *
 * Deliberately not Material You / dynamic colour: TV boxes expose no meaningful wallpaper signal, so
 * a curated palette is both more intentional and consistent across every device.
 */
private val Violet = Color(0xFFB47CF7)        // primary accent, the logo's own hue
private val OnViolet = Color(0xFF150626)
private val Cyan = Color(0xFF68C9F3)          // sampled straight from the logo's brightest accent
private val OnCyan = Color(0xFF05283A)
private val Lavender = Color(0xFFCFD2FF)      // the logo's specular highlight
private val OnLavender = Color(0xFF1B1B44)
private val NearBlack = Color(0xFF100E16)     // violet-biased, not neutral grey
private val Surface1 = Color(0xFF1C1826)
private val Surface2 = Color(0xFF292338)
private val TextPrimary = Color(0xFFEDEBF5)
private val TextSecondary = Color(0xFFB6B0CC)
private val ErrorPink = Color(0xFFF2809E)
// Contrast, measured against the surfaces these actually sit on (TV viewing distance wants ~7:1,
// higher than the web's 4.5): TextSecondary on surfaceVariant was 5.92 and is now 7.03; OnViolet on
// the violet fill was 6.09 and is now ~6.9. Violet itself stays as-is at 6.54 on the background -
// it is an accent and indicator colour, not body text, and brightening it further would wash out the
// hue the whole identity is built on.

val HomeColorScheme: ColorScheme = darkColorScheme(
    primary = Violet,
    onPrimary = OnViolet,
    primaryContainer = Color(0xFF4A2585),
    onPrimaryContainer = Color(0xFFEADBFF),
    secondary = Cyan,
    onSecondary = OnCyan,
    secondaryContainer = Color(0xFF11506B),
    onSecondaryContainer = Color(0xFFC8EEFF),
    tertiary = Lavender,
    onTertiary = OnLavender,
    tertiaryContainer = Color(0xFF3B3B7A),
    onTertiaryContainer = Color(0xFFE6E7FF),
    background = NearBlack,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    error = ErrorPink,
    onError = Color(0xFF3F0417),
    border = Color(0xFF332C42),
    borderVariant = Color(0xFF272134)
)
