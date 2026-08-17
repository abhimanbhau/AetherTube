@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi::class,
)

package com.abhimankolte.aethertube.tv.ui.common.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.palette.graphics.Palette
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover
import com.liskovsoft.smartyoutubetv2.tv.R
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.embedplayer.EmbedPlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.graphics.Color as AndroidColor

/** Where the video title sits relative to the artwork. */
enum class CardTitle {
    /** Below the artwork, outside it (Home). */
    Below,

    /** Overlaid on the artwork's bottom edge (Search). */
    Overlay,
}

private const val PLAYER_START_DELAY_MS = 2_000L

/**
 * Universal video card composable supporting configurable title placement, dynamic glow highlights,
 * and animated video preview escalation.
 */
@Composable
fun VideoCard(
    video: Video,
    width: Dp,
    height: Dp,
    titleFontSize: TextUnit,
    titleLineHeight: TextUnit,
    titleHeight: Dp,
    shape: RoundedCornerShape,
    titlePlacement: CardTitle,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
    focusRequester: FocusRequester? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val mainUIData = remember(context) { MainUIData.instance(context) }
    val widthPx = with(density) { width.roundToPx() }
    val heightPx = with(density) { height.roundToPx() }

    val lowEnd = LocalLowEndDevice.current
    var isFocused by remember { mutableStateOf(false) }

    // Read in draw phase to avoid full card recomposition during focus animations.
    val glowAlpha = animateFloatAsState(if (isFocused) 1f else 0f, tween(MotionTokens.FOCUS_ANIM_MS), label = "cardGlow")
    val dimAlpha = animateFloatAsState(if (isFocused) 0f else FocusTokens.UnfocusedDim, tween(MotionTokens.FOCUS_ANIM_MS), label = "cardDim")
    val scrimAlpha = animateFloatAsState(if (isFocused) 0.9f else 0.7f, tween(MotionTokens.FOCUS_ANIM_MS), label = "cardScrim")
    val ringColor = Color.White
    val ringInnerColor = Color.Black.copy(alpha = 0.55f)

    // Focus glow tints dynamically from the thumbnail palette; skipped on low-end hardware for performance.
    val paletteColor = if (!lowEnd) rememberFocusPaletteColor(video.cardImageUrl, isFocused) else null
    val glowColor = animateColorAsState(
        (paletteColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.55f),
        tween(MotionTokens.FOCUS_ANIM_MS),
        label = "cardGlowColor",
    )

    // Preview playback only activates after dwelling on the focused card to prevent thrashing during fast navigation.
    val previewType = mainUIData.cardPreviewType
    var previewMode by remember(video.videoId) { mutableStateOf(PreviewMode.None) }
    LaunchedEffect(isFocused, previewType, video.videoId) {
        if (!isFocused || previewType == MainUIData.CARD_PREVIEW_DISABLED) {
            previewMode = PreviewMode.None
            return@LaunchedEffect
        }
        when {
            video.previewUrl != null -> previewMode = PreviewMode.AnimatedImage
            // Respects user preview preference directly without heuristic overrides.
            video.videoId != null -> {
                delay(PLAYER_START_DELAY_MS)
                previewMode = PreviewMode.Player
            }
        }
    }

    val outerHeight = if (titlePlacement == CardTitle.Below) height + titleHeight else height

    Box(modifier = modifier.padding(contentPadding).size(width = width, height = outerHeight)) {
        if (!lowEnd) {
            Box(
                modifier = Modifier
                    .size(width = width, height = height)
                    .scale(1.08f)
                    .blur(24.dp)
                    .clip(shape)
                    .drawBehind {
                        val a = glowAlpha.value
                        if (a > 0f) {
                            drawRect(Brush.radialGradient(listOf(glowColor.value, Color.Transparent)), alpha = a)
                        }
                    },
            )
        }

        Box(
            modifier = Modifier
                .size(width = width, height = height)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged {
                    isFocused = it.isFocused
                    // Callers depend on this for the ambient backdrop and presenter selected-item
                    // tracking, even though nothing inside VideoCard itself uses it - don't drop it
                    // just because it looks unused here.
                    if (it.isFocused) {
                        onFocus()
                    }
                }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                // Drawn directly in draw phase to reduce layout node allocations.
                .drawWithContent {
                    drawContent()
                    val d = dimAlpha.value
                    if (d > 0f) drawRect(Color.Black, alpha = d)
                    if (isFocused) {
                        val r = shape.topStart.toPx(size, this)
                        val outer = FocusTokens.RingWidth.toPx()
                        val inner = FocusTokens.RingInnerWidth.toPx()
                        drawRoundRect(
                            color = ringColor,
                            topLeft = Offset(outer / 2f, outer / 2f),
                            size = Size(size.width - outer, size.height - outer),
                            cornerRadius = CornerRadius(r),
                            style = Stroke(width = outer),
                        )
                        val i = outer + inner / 2f
                        drawRoundRect(
                            color = ringInnerColor,
                            topLeft = Offset(i, i),
                            size = Size(size.width - i * 2f, size.height - i * 2f),
                            cornerRadius = CornerRadius((r - outer).coerceAtLeast(0f)),
                            style = Stroke(width = inner),
                        )
                    }
                },
        ) {
            // DiskCacheStrategy.ALL, deliberately not ViewUtil.glideOptions() (which sets
            // skipMemoryCache(true)): that's for the animated preview below, where a restart-from-
            // frame-one is the point. Applied to this static thumbnail it would just re-decode from
            // disk on every bind.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = scrimAlpha.value)),
                                startY = 0.4f * size.height,
                                endY = size.height,
                            ),
                        )
                    },
            ) {
                GlideImage(
                    model = ClickbaitRemover.updateThumbnail(video, mainUIData.thumbQuality),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                ) { request ->
                    request
                        .override(widthPx, heightPx)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .error(
                            Glide.with(context)
                                .load(video.cardImageUrl)
                                .error(R.drawable.card_placeholder),
                        )
                }

                when (previewMode) {
                    PreviewMode.AnimatedImage -> GlideImage(
                        model = video.previewUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    ) { request ->
                        // Skips cache to ensure animated previews restart from frame one.
                        request.diskCacheStrategy(DiskCacheStrategy.NONE).skipMemoryCache(true)
                    }
                    PreviewMode.Player -> VideoPreviewPlayer(
                        video = video,
                        muted = previewType == MainUIData.CARD_PREVIEW_MUTED,
                        lowQuality = minOf(widthPx, heightPx) < 300,
                    )
                    PreviewMode.None -> Unit
                }
            }

            if (titlePlacement == CardTitle.Overlay) {
                CardTitleText(
                    video,
                    titleFontSize,
                    titleLineHeight,
                    isFocused,
                    Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }

        if (titlePlacement == CardTitle.Below) {
            CardTitleText(
                video,
                titleFontSize,
                titleLineHeight,
                isFocused,
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp)
                    .size(width = width - 20.dp, height = titleHeight),
            )
        }
    }
}

private enum class PreviewMode { None, AnimatedImage, Player }

/** Extracts dominant/vibrant color asynchronously from a low-res thumbnail sample for focus glow. */
@Composable
private fun rememberFocusPaletteColor(imageUrl: String?, active: Boolean): Color? {
    var color by remember { mutableStateOf<Color?>(null) }
    val context = LocalContext.current

    LaunchedEffect(imageUrl, active) {
        if (!active || imageUrl == null) {
            return@LaunchedEffect
        }

        color = withContext(Dispatchers.IO) {
            try {
                val bitmap = Glide.with(context).asBitmap().load(imageUrl).submit(96, 96).get()
                val swatch = Palette.from(bitmap).generate().let { it.vibrantSwatch ?: it.dominantSwatch }
                swatch?.let { Color(it.rgb) }
            } catch (e: Exception) {
                null
            }
        }
    }

    return color
}

@Composable
private fun CardTitleText(
    video: Video,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    isFocused: Boolean,
    modifier: Modifier,
) {
    Text(
        text = video.title.orEmpty(),
        color = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f),
        fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
        fontSize = fontSize,
        // Pinned line height prevents vertical clipping on multi-line titles.
        lineHeight = lineHeight,
        maxLines = 2,
        modifier = modifier,
    )
}

/** Embedded ExoPlayer view for active in-card video previews. */
@Composable
private fun VideoPreviewPlayer(video: Video, muted: Boolean, lowQuality: Boolean) {
    var player by remember { mutableStateOf<EmbedPlayerView?>(null) }

    AndroidView(
        factory = { ctx ->
            EmbedPlayerView(ctx).apply {
                setQuality(if (lowQuality) EmbedPlayerView.QUALITY_LOW else EmbedPlayerView.QUALITY_NORMAL)
                setUseController(false)
                setMute(muted)
                setBackgroundColor(AndroidColor.BLACK)
            }.also { player = it }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = { it.finish() },
    )

    // Starts video playback in an effect to prevent repeated restarts across recompositions.
    LaunchedEffect(player, video.videoId) {
        player?.openVideo(video)
    }
}
