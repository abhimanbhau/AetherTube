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
import androidx.compose.ui.draw.alpha
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

private const val FOCUS_ANIM_MS = 180
private const val PLAYER_START_DELAY_MS = 2_000L

/** Focus ring: chunky bright outer + thin dark inner, so it reads over any thumbnail. */
private val RING_WIDTH = 4.dp
private val RING_INNER_WIDTH = 1.5.dp

/** Unfocused cards are dimmed - the one cue a bright thumbnail can't camouflage. */
private const val UNFOCUSED_DIM = 0.35f

/**
 * The single video card used by every Compose screen.
 *
 * Previously Home and Search each carried their own near-identical copy, and each one hosted a
 * [com.abhimankolte.aethertube.tv.ui.search.compose.SearchVideoCardView] through [AndroidView] - a
 * FrameLayout, an ImageView and a Glide target inflated for *every* card in a lazy layout, which is
 * the most expensive thing on a grid frame. Here the thumbnail and the animated preview are pure
 * Compose; only the video preview still needs a real View, and only while it is actually playing.
 *
 * Card preview parity with the leanback card is preserved - see [MainUIData.getCardPreviewType]:
 *  - CARD_PREVIEW_DISABLED: thumbnail only.
 *  - CARD_PREVIEW_MUTED / CARD_PREVIEW_FULL: on focus, either the animated preview image
 *    ([Video.previewUrl]) immediately, or a muted/unmuted [EmbedPlayerView] after a short delay -
 *    matching what the leanback card did.
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
    // Held as State and read inside the draw lambdas below rather than unwrapped with `by`. Reading
    // an animated value during composition recomposes the card on every animation frame; reading it
    // in the draw phase only redraws. Focus animation therefore costs no recomposition at all.
    val glowAlpha = animateFloatAsState(if (isFocused) 1f else 0f, tween(FOCUS_ANIM_MS), label = "cardGlow")
    val dimAlpha = animateFloatAsState(if (isFocused) 0f else UNFOCUSED_DIM, tween(FOCUS_ANIM_MS), label = "cardDim")
    val scrimAlpha = animateFloatAsState(if (isFocused) 0.9f else 0.7f, tween(FOCUS_ANIM_MS), label = "cardScrim")
    val ringColor = Color.White
    val ringInnerColor = Color.Black.copy(alpha = 0.55f)

    // Dynamic palette color-sync: the focus glow tints itself from the focused card's own thumbnail
    // instead of always being the same static accent, closer to the ambient-lighting feel of a
    // modern TV OS. Extraction only ever runs for the card that's actually focused right now - a
    // grid can hold dozens of cards, and Palette scanning pixel data on all of them at once would be
    // a real, measurable cost, unlike the flat accent this replaces. Skipped on low-end devices
    // alongside the blur glow itself, and falls back to the flat accent while nothing's extracted
    // yet (or extraction fails) so there's never a missing/blank glow.
    // Not unwrapped with `by`, same reasoning as glowAlpha/dimAlpha/scrimAlpha below: this is read
    // inside the drawBehind draw phase, not during composition, so an in-flight color transition
    // (e.g. focus landing on a card with a very different thumbnail) redraws without recomposing.
    val paletteColor = if (!lowEnd) rememberFocusPaletteColor(video.cardImageUrl, isFocused) else null
    val glowColor = animateColorAsState(
        (paletteColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.55f),
        tween(FOCUS_ANIM_MS),
        label = "cardGlowColor",
    )

    // Preview only ever runs for the focused card. The leanback card showed an animated preview image
    // straight away when the video has one, and otherwise waited a couple of seconds before spinning
    // up a player - so passing through cards never starts playback.
    val previewType = mainUIData.cardPreviewType
    var previewMode by remember(video.videoId) { mutableStateOf(PreviewMode.None) }
    LaunchedEffect(isFocused, previewType, video.videoId) {
        if (!isFocused || previewType == MainUIData.CARD_PREVIEW_DISABLED) {
            previewMode = PreviewMode.None
            return@LaunchedEffect
        }
        when {
            video.previewUrl != null -> previewMode = PreviewMode.AnimatedImage
            // Deliberately NOT gated on device class. Card preview is an explicit user setting with
            // three states (off / muted / with sound); a device heuristic quietly overriding what
            // the user asked for is the wrong trade, and it silently broke previews on perfectly
            // capable TVs. Only the decorative blurs below adapt automatically.
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
                    // Forward focus gain to the caller, not just to this card's own visual state.
                    // This was silently dropped when Home's and Search's separate card
                    // implementations were merged into this one: every call site still passed
                    // onFocus (it drives the ambient backdrop and the presenter's selected-item
                    // tracking), but nothing here ever called it - so the backdrop stopped following
                    // focus across shelves and grids and appeared frozen on whatever loaded first.
                    // FeaturedCarousel kept working only because it wires onVideoFocus itself
                    // instead of going through VideoCard.
                    if (it.isFocused) {
                        onFocus()
                    }
                }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                // Dim veil and both focus rings used to be three more full-card child Boxes stacked
                // on top of the artwork. Drawn here instead: three fewer layout nodes per card (27 on
                // a 9-card grid), and because the alphas are read in the draw phase the whole focus
                // transition is redraw-only.
                .drawWithContent {
                    drawContent()
                    val d = dimAlpha.value
                    if (d > 0f) drawRect(Color.Black, alpha = d)
                    if (isFocused) {
                        val r = shape.topStart.toPx(size, this)
                        val outer = RING_WIDTH.toPx()
                        val inner = RING_INNER_WIDTH.toPx()
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
            // Static thumbnail. Deliberately NOT ViewUtil.glideOptions(): that sets
            // skipMemoryCache(true) so animated previews restart from frame one, and the leanback card
            // applied it to the still thumbnail too - meaning every thumbnail was re-decoded from disk
            // on every bind, which is exactly the wrong trade for a grid you scroll through.
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
                        // No caching here, unlike the thumbnail: it's what makes the animation start from
                        // its first frame each time rather than resuming mid-loop.
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

/**
 * The focused card's dominant/vibrant color, or null while nothing's been extracted yet.
 *
 * A separate, small Glide bitmap fetch rather than hooking into the display [GlideImage] above -
 * `glide-compose` doesn't expose the decoded bitmap directly, and the thumbnail is already on
 * [DiskCacheStrategy.ALL], so this second request decodes from the cached bytes instead of hitting
 * the network again. Downscaled to 96x96 before Palette even sees it: full-resolution extraction
 * would cost far more than the visual result needs.
 */
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
        // Pinned: Compose 1.10 changed default text metrics, and a title box sized without an explicit
        // line height silently clips the second line.
        lineHeight = lineHeight,
        maxLines = 2,
        modifier = modifier,
    )
}

/**
 * The one part that still has to be a real View: [EmbedPlayerView] extends ExoPlayer's PlayerView and
 * needs a Surface, which no Compose image can provide. Composed only while this card is focused and
 * actually previewing, so unfocused cards cost nothing.
 */
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

    // Opening from an effect rather than AndroidView's update lambda: update re-runs on every
    // recomposition of this card, which would restart playback repeatedly.
    LaunchedEffect(player, video.videoId) {
        player?.openVideo(video)
    }
}
