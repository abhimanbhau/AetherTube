@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.abhimankolte.aethertube.tv.ui.common.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.liskovsoft.smartyoutubetv2.tv.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val WORDMARK = "AetherTube"

private const val MANIFEST_MS = 300
private const val KINETIC_MS = 450
private const val SHEEN_MS = 300
private const val EXIT_MS = 200

/** Never hold the brand lockup hostage to a slow cold-start network - see [AnimatedSplashScreen]. */
private const val MAX_HOLD_MS = 3000L

private val EMBLEM_SIZE = 176.dp

/**
 * How far the emblem slides left (dp) to make room for the wordmark, and where the wordmark then
 * sits (dp, from the lockup's own center). Tuned by eye against [EMBLEM_SIZE] and [WORDMARK_FONT_SIZE]
 * below - nudge both together if either of those changes.
 */
private const val EMBLEM_SLIDE_X = -70f
private val WORDMARK_OFFSET_X = 150.dp
private val WORDMARK_FONT_SIZE = 40.sp
private val LOCKUP_SIZE_WIDTH = 560.dp
private val LOCKUP_SIZE_HEIGHT = 220.dp

/**
 * The cinematic brand intro shown while [com.abhimankolte.aethertube.tv.ui.home.compose.ComposeHomeFragment]
 * warms up the home feed underneath. See `NEW_FEATURES.md` (repo root, not tracked here) for the
 * original choreography this implements.
 *
 * Deliberately NOT hosted in `SplashActivity`/`SplashPresenter` (upstream, `com.liskovsoft.*`) - this
 * lives purely as an overlay inside our own Compose fragment instead, which gets the deep-link/
 * instant-play bypass for free: a video deep link or voice "play" command routes straight to
 * `PlaybackActivity` without ever creating this fragment, so this composable simply never runs for
 * those cases. No separate bypass check needed.
 *
 * Runs unconditionally through the sheen sweep (0-1050ms) since that's the actual brand moment, then
 * holds on the finished lockup until [contentReady] flips true (or [MAX_HOLD_MS] elapses, whichever
 * first) before playing the 200ms exit and calling [onFinished]. [onFinished] should remove this
 * composable from composition - it does not remove itself.
 */
@Composable
fun AnimatedSplashScreen(
    contentReady: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lowEnd = LocalLowEndDevice.current

    val background = MaterialTheme.colorScheme.background
    val onBackground = MaterialTheme.colorScheme.onBackground
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val emblemScale = remember { Animatable(0.6f) }
    val glowAlpha = remember { Animatable(0f) }
    val emblemOffsetX = remember { Animatable(0f) }
    val wordmarkReveal = remember { Animatable(0f) }
    val sheenProgress = remember { Animatable(-0.4f) }
    val exitScale = remember { Animatable(1f) }
    val exitAlpha = remember { Animatable(1f) }

    // Steals focus from whatever HomeScreen underneath auto-focused on its own first composition, so
    // an eager D-pad press in the first frames can't click through to a video card before this is done.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(Unit) {
        // 0-300ms: emblem rises with an organic overshoot and its ambient glow blooms in. Low-end
        // devices skip the glow (a blurred radial gradient) and the spring's bounce - a plain scale is
        // still a real, quick motion, just without the two GPU-heavier parts.
        if (lowEnd) {
            emblemScale.animateTo(1f, tween(MANIFEST_MS))
        } else {
            coroutineScope {
                launch { emblemScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) }
                launch { glowAlpha.animateTo(1f, tween(MANIFEST_MS)) }
            }
        }

        // 300-750ms: emblem slides left to make room, wordmark reveals into the space it opens up.
        coroutineScope {
            launch { emblemOffsetX.animateTo(EMBLEM_SLIDE_X, tween(KINETIC_MS)) }
            launch { wordmarkReveal.animateTo(1f, tween(KINETIC_MS)) }
        }

        // 750-1050ms: a specular highlight sweeps the finished lockup. Purely decorative - skipped
        // outright on low-end rather than simplified, unlike the phases above.
        if (lowEnd) {
            delay(SHEEN_MS.toLong())
        } else {
            sheenProgress.animateTo(1.4f, tween(SHEEN_MS))
        }

        // Hold the finished lockup until the home feed underneath has actually settled (data arrived,
        // or the load concluded empty/errored - see ComposeHomeFragment's isCurrentSectionSettled).
        // Capped so a slow or stalled network never turns the brand moment into a hang.
        withTimeoutOrNull(MAX_HOLD_MS) {
            snapshotFlow { contentReady }.first { it }
        }

        // 1050-1250ms: expand and fade out, exposing HomeScreen underneath. Both screens share the
        // same background token, so even mid-fade there is no colour seam, let alone a black frame.
        if (lowEnd) {
            exitAlpha.animateTo(0f, tween(EXIT_MS))
        } else {
            coroutineScope {
                launch { exitScale.animateTo(1.10f, tween(EXIT_MS)) }
                launch { exitAlpha.animateTo(0f, tween(EXIT_MS)) }
            }
        }

        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .graphicsLayer {
                scaleX = exitScale.value
                scaleY = exitScale.value
                alpha = exitAlpha.value
            }
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(LOCKUP_SIZE_WIDTH, LOCKUP_SIZE_HEIGHT)
                .then(
                    if (lowEnd) {
                        Modifier
                    } else {
                        Modifier.drawWithContent {
                            drawContent()
                            val p = sheenProgress.value
                            if (p in -0.3f..1.3f) {
                                val bandHalfWidth = size.width * 0.18f
                                val centerX = size.width * p
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color.Transparent, tertiary.copy(alpha = 0.65f), Color.Transparent),
                                        start = Offset(centerX - bandHalfWidth, 0f),
                                        end = Offset(centerX + bandHalfWidth, size.height),
                                    ),
                                )
                            }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!lowEnd) {
                // Ambient glow + kinetic trail, both tied to the same offset as the emblem so they
                // travel with it. The trail is a soft horizontal streak that peaks mid-slide and fades
                // at both ends, drawn from the glow's own colours rather than a third animatable.
                Box(
                    modifier = Modifier
                        .size(EMBLEM_SIZE * 1.7f)
                        .align(Alignment.Center)
                        .offset(x = emblemOffsetX.value.dp)
                        .blur(48.dp)
                        .drawBehind {
                            val glow = glowAlpha.value
                            if (glow > 0f) {
                                drawRect(Brush.radialGradient(listOf(primary.copy(alpha = glow), Color.Transparent)), alpha = 0.9f)
                                drawRect(Brush.radialGradient(listOf(secondary.copy(alpha = glow * 0.6f), Color.Transparent)), alpha = 0.6f)
                            }
                            val trail = (wordmarkReveal.value * (1f - wordmarkReveal.value) * 4f).coerceIn(0f, 1f)
                            if (trail > 0f) {
                                drawRect(
                                    brush = Brush.horizontalGradient(listOf(Color.Transparent, tertiary.copy(alpha = trail * 0.5f))),
                                )
                            }
                        },
                )
            }

            Image(
                painter = painterResource(id = R.mipmap.app_icon_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(EMBLEM_SIZE)
                    .align(Alignment.Center)
                    .offset(x = emblemOffsetX.value.dp)
                    .graphicsLayer {
                        scaleX = emblemScale.value
                        scaleY = emblemScale.value
                    },
            )

            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = WORDMARK_OFFSET_X)
                    .drawWithContent {
                        clipRect(right = size.width * wordmarkReveal.value) { this@drawWithContent.drawContent() }
                    },
            ) {
                val reveal = wordmarkReveal.value
                val letterCount = WORDMARK.length
                WORDMARK.forEachIndexed { index, char ->
                    // Staggered per-letter opacity computed from one shared reveal value rather than a
                    // separate Animatable per character - cheap, and keeps every letter's fade in lockstep
                    // with the same spring/tween timing instead of drifting independently.
                    val letterAlpha = (reveal * letterCount - index).coerceIn(0f, 1f)
                    Text(
                        text = char.toString(),
                        color = onBackground,
                        fontSize = WORDMARK_FONT_SIZE,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.graphicsLayer { alpha = letterAlpha },
                    )
                }
            }
        }
    }
}
