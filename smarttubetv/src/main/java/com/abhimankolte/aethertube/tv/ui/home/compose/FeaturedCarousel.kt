@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi::class,
)

package com.abhimankolte.aethertube.tv.ui.home.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Carousel
import androidx.tv.material3.Text
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.liskovsoft.smartyoutubetv2.common.R
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover

/**
 * Auto-rotating hero banner for the first ("featured") row, using androidx.tv.material3's native
 * Carousel - a proper large-format rotating spotlight instead of just a bigger horizontal-scroll row.
 */
@Composable
internal fun FeaturedCarousel(
    videos: List<Video>,
    onVideoClick: (Video) -> Unit,
    onVideoFocus: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    topNavFocusRequester: FocusRequester,
) {
    if (videos.isEmpty()) {
        return
    }

    Carousel(
        itemCount = videos.size,
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .padding(horizontal = 40.dp, vertical = 8.dp)
            .clip(CARD_SHAPE),
    ) { index ->
        val video = videos[index]

        // tv-material 1.0.0 removed CarouselItem: the content lambda now renders the slide directly,
        // so the background and the overlay are just stacked here instead of being passed as slots.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { up = topNavFocusRequester }
                .combinedClickable(
                    onClick = { onVideoClick(video) },
                    onLongClick = { onVideoLongClick(video) },
                )
                .onFocusChanged { state ->
                    if (state.isFocused) onVideoFocus(video)
                },
        ) {
            CarouselBackdrop(video = video)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = video.title.orEmpty(),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun CarouselBackdrop(video: Video) {
    val context = LocalContext.current
    val mainUIData = remember(context) { MainUIData.instance(context) }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { sizePx = it },
    ) {
        // Static hero thumbnail matching VideoCard's override and placeholder logic, omitting
        // video preview playback since this slot is timer-rotated.
        if (sizePx.width > 0 && sizePx.height > 0) {
            GlideImage(
                model = ClickbaitRemover.updateThumbnail(video, mainUIData.thumbQuality),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) { request ->
                request
                    .override(sizePx.width, sizePx.height)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .error(
                        Glide.with(context)
                            .load(video.cardImageUrl)
                            .error(R.drawable.card_placeholder),
                    )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = sizePx.height * 0.35f,
                    ),
                ),
        )
    }
}
