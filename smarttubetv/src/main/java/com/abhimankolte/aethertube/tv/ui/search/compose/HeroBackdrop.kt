@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.abhimankolte.aethertube.tv.ui.search.compose

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.MaterialTheme
import com.abhimankolte.aethertube.tv.ui.common.compose.LocalLowEndDevice
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

/**
 * Deliberately tiny. 16:9 at this size still carries the artwork's colour composition - which is all
 * an out-of-focus background needs - while being small enough that the upscale is unmistakably soft
 * on any panel size.
 */
private const val BACKDROP_SAMPLE_W = 64
private const val BACKDROP_SAMPLE_H = 36

/**
 * Google TV-style ambient background: a heavily blurred, dimmed crossfade of whatever video/tag
 * currently has focus, sitting behind the whole screen.
 *
 * The blur comes from **downsampling**, not from [androidx.compose.ui.draw.blur]: the artwork is
 * fetched at [BACKDROP_SAMPLE_W]x[BACKDROP_SAMPLE_H] and stretched across the viewport, so the
 * GPU's own bilinear filtering does the smoothing for free. That matters for two reasons.
 * `Modifier.blur()` is a silent no-op below API 31, which meant the app's signature look simply
 * never appeared on any device older than Android 12 - and there was no way to tell from inside the
 * app, since nothing errors, the effect just isn't there. It's also the more expensive option where
 * it does work: a full-viewport RenderEffect re-runs on every image change, whereas a 64x36 texture
 * upscale costs essentially nothing. `Modifier.blur()` is still applied on top where available, but
 * only as extra polish - the ambient look no longer depends on it.
 */
@Composable
fun HeroBackdrop(imageUrl: String?, modifier: Modifier = Modifier) {
    val backgroundColor = MaterialTheme.colorScheme.background
    // A 48dp blur across the whole screen is the most expensive effect in the app - it is a
    // full-viewport RenderEffect that re-runs whenever the image changes. Weak boxes drop it and get
    // the dimmed, unblurred backdrop instead, which is also what pre-API-31 devices already saw.
    val lowEnd = LocalLowEndDevice.current

    Box(modifier = modifier.fillMaxSize().background(backgroundColor)) {
        if (imageUrl != null) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    // Polish only, not the source of the effect (see the class comment). Still skipped
                    // on weak hardware, where the downsampled upscale alone already reads as blurred.
                    .then(if (lowEnd) Modifier else Modifier.blur(48.dp))
                    .alpha(0.55f),
                update = { imageView ->
                    // update runs on every recomposition of whatever hosts this backdrop, not just when
                    // imageUrl actually changes - guard against Glide re-triggering (and resetting the
                    // target's drawable state, reading as a flicker) for an image it's already showing.
                    if (imageView.tag == imageUrl) {
                        return@AndroidView
                    }
                    imageView.tag = imageUrl

                    Glide.with(imageView.context)
                        .load(imageUrl)
                        .override(BACKDROP_SAMPLE_W, BACKDROP_SAMPLE_H)
                        .transition(DrawableTransitionOptions.withCrossFade(400))
                        .into(imageView)
                },
            )
        }

        // Both scrims used to be their own full-screen Box, i.e. two extra viewport-sized alpha
        // blends and two layout nodes on top of the backdrop image. Drawn together in one pass here.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(backgroundColor.copy(alpha = 0.35f), backgroundColor.copy(alpha = 0.92f)),
                        ),
                    )
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(backgroundColor.copy(alpha = 0.75f), Color.Transparent),
                        ),
                    )
                },
        )
    }
}
