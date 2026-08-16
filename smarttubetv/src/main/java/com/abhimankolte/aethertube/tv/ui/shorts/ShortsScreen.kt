@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi::class,
)

package com.abhimankolte.aethertube.tv.ui.shorts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.abhimankolte.aethertube.tv.ui.common.compose.LocalLowEndDevice
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long a page change takes. Short enough to feel like a flick, long enough to read as motion. */
private const val SLIDE_MS = 260

/** Shorts are 9:16, but plenty of things in the Shorts feed are 1:1 or 4:5. This is only the seed. */
private const val DEFAULT_ASPECT = 9f / 16f

private const val SEEK_STEP_MS = 5_000L

/** Position polling interval. Only feeds the progress bar, so it can be lazy. */
private const val PROGRESS_POLL_MS = 300L

/** Ambient backdrop sample size - see the note at its call site. Tiny on purpose. */
private const val AMBIENT_SAMPLE_W = 64
private const val AMBIENT_SAMPLE_H = 36

/**
 * The vertical Shorts feed.
 *
 * <h4>Why the video surface never moves</h4>
 *
 * There is exactly one [ShortsPlayerView] and it stays put in the centre of the screen for the whole
 * session. A page change is drawn instead by two poster layers sliding past it, and the surface is
 * only re-pointed at a new video once the motion has settled. That is not a shortcut around Compose:
 * a `SurfaceView` cannot be duplicated, and moving one between two composable slots re-creates it -
 * which on a TV decoder means a visible black flash on every single press. So the illusion is built
 * out of the one thing that is cheap to have two of, the thumbnails.
 *
 * A press runs: cover the outgoing video with its own poster (it is a frame of the same video, so
 * nothing appears to change), slide that poster out while the incoming poster slides in, snap the
 * layers home, then hand the new video to the player. The poster only lifts once [readyVideoId]
 * says the engine has decoded a frame of the video now on screen, so the swap is never visible.
 *
 * @param readyVideoId id of the video the engine has actually painted, or null. Compared against the
 *   current video rather than tracked as a boolean so there is no ordering race between "new video
 *   requested" and "old video ready".
 */
@Composable
fun ShortsScreen(
    feed: ShortsFeed,
    playerView: ShortsPlayerView,
    readyVideoId: String?,
    aspectRatio: Float,
    channelIconUrl: String?,
    onPlayVideo: (Video) -> Unit,
    onExit: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenMenu: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = feed.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // -1 slides the current page up and out (moving to the next short); +1 slides it down and out.
    // An Animatable rather than animateFloatAsState because the video swap has to happen strictly
    // after the motion finishes, and that ordering is the whole trick.
    val slide = remember { Animatable(0f) }
    var incoming by remember { mutableStateOf<Video?>(null) }
    var direction by remember { mutableFloatStateOf(0f) }

    val stageAspect = if (aspectRatio > 0f) aspectRatio else DEFAULT_ASPECT

    // derivedStateOf, not a plain val: slide.value changes every animation frame during a page
    // transition (up to 60-120Hz), but the boolean it feeds into only actually flips at the very start
    // and end of that ~260ms motion. A plain val reading slide.value directly would recompose this
    // whole composable every one of those frames just to keep producing the same answer; derivedStateOf
    // still re-evaluates that often but only invalidates readers when the result actually changes.
    val showPoster by remember(readyVideoId, current?.videoId) {
        derivedStateOf { readyVideoId == null || readyVideoId != current?.videoId || slide.value != 0f }
    }

    // Tracks whichever video has already been handed to the player, so page()'s early dispatch
    // below and this effect settling onto the same video afterward don't both call onPlayVideo for
    // it - openVideo() is safe to call repeatedly, but a second call for the video already loading
    // would restart its format/decode pipeline from scratch instead of letting the first one finish,
    // which would throw away exactly the head start this is meant to create.
    var requestedVideoId by remember { mutableStateOf<String?>(null) }

    fun requestPlay(video: Video) {
        if (video.videoId != requestedVideoId) {
            requestedVideoId = video.videoId
            onPlayVideo(video)
        }
    }

    // Covers the initial video on entry and any change the feed didn't get through page() below -
    // upstream auto-advancing at end of video, most notably (see ShortsFeed.syncTo()).
    LaunchedEffect(current?.videoId) {
        current?.let(::requestPlay)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        feed.loadMoreIfNeeded()
    }

    fun page(delta: Int) {
        if (slide.isRunning) {
            // Presses arriving mid-slide are dropped rather than queued: a held-down D-pad would
            // otherwise build a backlog and keep paging long after the user let go.
            return
        }

        val target = feed.videos.getOrNull(feed.currentIndex + delta)

        if (target == null) {
            // Pressing down at the end of the loaded run is exactly when the next page is wanted.
            if (delta > 0) feed.loadMoreIfNeeded()
            return
        }

        // Start the target video's network/decode pipeline now, overlapping it with the slide
        // animation instead of waiting until the animation finishes to even begin - safe because
        // showPoster (above) stays true for the whole slide regardless of when this fires, so
        // nothing about the swap becomes visible any earlier than it already was.
        requestPlay(target)

        scope.launch {
            direction = delta.toFloat()
            incoming = target
            slide.snapTo(0f)
            slide.animateTo(-delta.toFloat(), tween(SLIDE_MS))

            if (delta > 0) feed.moveNext() else feed.movePrevious()

            // Snap home. The incoming poster is already what occupies this position on screen, and
            // showPoster stays true until the engine paints the new video, so nothing flashes.
            slide.snapTo(0f)
            incoming = null
            direction = 0f
        }
    }

    val lowEnd = LocalLowEndDevice.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    // Claim the matching key-ups too, or the framework hands them on to the leanback
                    // machinery underneath and a release can fire a second action.
                    return@onPreviewKeyEvent event.key in CONSUMED_KEYS
                }

                when (event.key) {
                    Key.DirectionDown -> page(1)
                    Key.DirectionUp -> page(-1)
                    Key.DirectionLeft -> onSeek(-SEEK_STEP_MS)
                    Key.DirectionRight -> onSeek(SEEK_STEP_MS)
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> onTogglePlay()
                    Key.Menu -> current?.let(onOpenMenu)
                    Key.Back -> onExit()
                    else -> return@onPreviewKeyEvent false
                }

                true
            },
    ) {
        val pageHeightPx = constraints.maxHeight.toFloat()

        // A 9:16 video on a 16:9 screen leaves two thirds of the width empty. Filling it with a
        // blurred, over-scaled copy of the artwork is what stops the feed reading as a small window on
        // black.
        //
        // The softness comes from the override() downsample below, not from Modifier.blur() - the
        // latter is a silent no-op below API 31, so this whole effect was invisible on anything older
        // than Android 12 with no way to tell from inside the app. Same fix as HeroBackdrop; see its
        // class comment. Rendered on every device now, since a 64x36 upscale is far cheaper than the
        // RenderEffect it replaces; blur() stays on top purely as polish where it's supported.
        current?.cardImageUrl?.let { url ->
            GlideImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.3f)
                    .then(if (lowEnd) Modifier else Modifier.blur(56.dp))
                    .graphicsLayer { alpha = 0.45f },
            ) {
                it.override(AMBIENT_SAMPLE_W, AMBIENT_SAMPLE_H).diskCacheStrategy(DiskCacheStrategy.ALL)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
        )

        // ---- The stage: the one and only video surface ------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(stageAspect)
                .align(Alignment.Center)
                .graphicsLayer { translationY = slide.value * pageHeightPx }
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { playerView },
                modifier = Modifier.fillMaxSize(),
                // No update lambda on purpose: it re-runs on every recomposition - roughly once a
                // frame while anything here animates - and this fork has already been bitten once by
                // driving image loading from one. Video changes go through LaunchedEffect instead.
            )

            // Composed only while actually needed, not kept around at alpha 0 - the flip is a raw
            // boolean with no fade to preserve (see showPoster above), so there's nothing an
            // always-present node buys here over composing it fresh each time it's needed.
            if (showPoster) {
                current?.let { video ->
                    GlideImage(
                        model = video.cardImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    ) { it.diskCacheStrategy(DiskCacheStrategy.ALL) }
                }
            }

            ShortsScrim()

            ShortsProgress(
                playerView = playerView,
                videoId = current?.videoId,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }

        // ---- The incoming page: poster only, no surface to give it ------------------------------
        incoming?.let { next ->
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(stageAspect)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        // Parked one full screen away in the direction of travel, riding in on the
                        // same offset that carries the current page out.
                        translationY = (slide.value + direction) * pageHeightPx
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
            ) {
                GlideImage(
                    model = next.cardImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                ) { it.diskCacheStrategy(DiskCacheStrategy.ALL) }

                ShortsScrim()
            }
        }

        // ---- Chrome -----------------------------------------------------------------------------
        current?.let { video ->
            ShortsMetadata(
                video = video,
                channelIconUrl = channelIconUrl,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 56.dp, bottom = 56.dp, end = 56.dp),
            )
        }

        ShortsPositionHint(
            index = feed.currentIndex,
            total = feed.videos.size,
            canGoUp = feed.hasPrevious(),
            canGoDown = feed.hasNext() || feed.isLoadingMore,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 40.dp),
        )

        ShortsPauseIndicator(
            playerView = playerView,
            videoId = current?.videoId,
            showPoster = showPoster,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** Keys the feed owns outright; their key-ups must not reach anything underneath. */
private val CONSUMED_KEYS = setOf(
    Key.DirectionDown, Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight,
    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.Menu, Key.Back,
)

@Composable
private fun ShortsScrim() {
    // Only the bottom half, and only enough to hold text. A full-height scrim on a short washes out
    // the exact part of the frame people are watching.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.75f),
                    ),
                )
            },
    )
}

/**
 * Owns its own position/duration poll so the 300ms tick only invalidates this bar, not the whole
 * [ShortsScreen] tree it lives in - the poll used to live at the top of [ShortsScreen] itself, which
 * meant every tick recomposed the stage, the ambient backdrop and the metadata along with it.
 */
@Composable
private fun ShortsProgress(
    playerView: ShortsPlayerView,
    videoId: String?,
    modifier: Modifier = Modifier,
) {
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(videoId) {
        while (true) {
            positionMs = playerView.positionMs
            durationMs = playerView.durationMs
            delay(PROGRESS_POLL_MS)
        }
    }

    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.25f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * Same isolation as [ShortsProgress], for the same reason: `playWhenReady` used to be polled
 * alongside position/duration at the top of [ShortsScreen], invalidating the whole tree every 300ms
 * just to know whether to show a pause glyph.
 */
@Composable
private fun ShortsPauseIndicator(
    playerView: ShortsPlayerView,
    videoId: String?,
    showPoster: Boolean,
    modifier: Modifier = Modifier,
) {
    var playing by remember { mutableStateOf(true) }

    LaunchedEffect(videoId) {
        while (true) {
            playing = playerView.playWhenReady
            delay(PROGRESS_POLL_MS)
        }
    }

    if (!playing && !showPoster) {
        // Paused. Worth marking explicitly: a still frame on a TV is otherwise indistinguishable
        // from a stalled stream.
        Box(
            modifier = modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Paused",
                tint = Color.White,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun ShortsMetadata(
    video: Video,
    channelIconUrl: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (channelIconUrl != null) {
                GlideImage(
                    model = channelIconUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                ) { it.diskCacheStrategy(DiskCacheStrategy.ALL) }
            }

            video.author?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        video.title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A minimal "there is more in both directions" cue. Touch UIs get that affordance for free from the
 * drag itself; on a D-pad the user has to be told which way the feed runs, and once told needs no
 * further chrome.
 */
@Composable
private fun ShortsPositionHint(
    index: Int,
    total: Int,
    canGoUp: Boolean,
    canGoDown: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (canGoUp) 0.85f else 0.2f),
            modifier = Modifier.size(28.dp),
        )

        Text(
            text = "${index + 1} / $total",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f),
        )

        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (canGoDown) 0.85f else 0.2f),
            modifier = Modifier.size(28.dp),
        )
    }
}
