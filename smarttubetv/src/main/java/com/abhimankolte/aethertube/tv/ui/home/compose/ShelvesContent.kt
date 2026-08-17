@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.abhimankolte.aethertube.tv.ui.home.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.abhimankolte.aethertube.tv.ui.common.compose.CardTitle
import com.abhimankolte.aethertube.tv.ui.common.compose.VideoCard
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video

@Composable
internal fun ShelvesContent(
    rows: List<HomeRow>,
    topNavFocusRequester: FocusRequester,
    restoreFocusVideoId: String?,
    onFocusRestored: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onVideoFocus: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    onScrollEnd: (Video) -> Unit,
) {
    val listState = rememberLazyListState()
    val restRows = rows.drop(1)

    // Identifies the shelf containing the focus-restoration target; unremembered to observe SnapshotStateList mutations.
    val targetRowIndex = if (restoreFocusVideoId == null) -1 else rows.indexOfFirst { row -> row.videos.any { it.videoId == restoreFocusVideoId } }

    LaunchedEffect(targetRowIndex) {
        if (targetRowIndex > 0) {
            listState.scrollToItem(targetRowIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            // Topmost carousel redirects D-pad Up directly to TopNav.
            FeaturedCarousel(
                videos = rows.firstOrNull()?.videos.orEmpty(),
                onVideoClick = onVideoClick,
                onVideoFocus = onVideoFocus,
                onVideoLongClick = onVideoLongClick,
                topNavFocusRequester = topNavFocusRequester,
            )
        }

        // Carousel occupies index 0; restRows start at index 1.
        itemsIndexed(restRows, key = { _, row -> row.id }) { index, row ->
            HomeShelf(
                row = row,
                featured = false,
                restoreFocusVideoId = if (index + 1 == targetRowIndex) restoreFocusVideoId else null,
                onFocusRestored = onFocusRestored,
                onVideoClick = onVideoClick,
                onVideoFocus = onVideoFocus,
                onVideoLongClick = onVideoLongClick,
                onScrollEnd = onScrollEnd,
            )
        }
    }
}

@Composable
internal fun HomeShelf(
    row: HomeRow,
    featured: Boolean,
    restoreFocusVideoId: String?,
    onFocusRestored: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onVideoFocus: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    onScrollEnd: (Video) -> Unit,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    // Finds target item index within shelf; unremembered to stay reactive to in-place pagination updates.
    val targetItemIndex = if (restoreFocusVideoId == null) -1 else row.videos.indexOfFirst { it.videoId == restoreFocusVideoId }

    LaunchedEffect(targetItemIndex) {
        if (targetItemIndex > 0) {
            listState.scrollToItem(targetItemIndex)
        }
        if (targetItemIndex >= 0) {
            requestFocusSafely(focusRequester)
            onFocusRestored()
        }
    }

    // Prefetches next page when scrolling close to the horizontal row end.
    val lastVideo = row.videos.lastOrNull()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= total - ROW_SCROLL_CONTINUE_NUM
        }
    }

    LaunchedEffect(shouldLoadMore, lastVideo?.videoId) {
        if (shouldLoadMore && lastVideo != null) {
            onScrollEnd(lastVideo)
        }
    }

    Column(modifier = Modifier.padding(vertical = if (featured) 8.dp else 14.dp)) {
        Text(
            text = row.title,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = if (featured) 28.sp else 20.sp,
            modifier = Modifier.padding(start = 40.dp, bottom = 14.dp),
        )

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Index fallback key prevents crash if placeholder items produce colliding hashes.
            itemsIndexed(row.videos, key = { index, video -> video.videoId ?: "video_$index" }) { _, video ->
                VideoCard(
                    video = video,
                    width = if (featured) FEATURED_CARD_WIDTH else CARD_WIDTH,
                    height = if (featured) FEATURED_CARD_HEIGHT else CARD_HEIGHT,
                    titleFontSize = if (featured) FEATURED_TITLE_SIZE else CARD_TITLE_SIZE,
                    titleLineHeight = if (featured) FEATURED_TITLE_LINE_HEIGHT else CARD_TITLE_LINE_HEIGHT,
                    titleHeight = if (featured) FEATURED_TITLE_HEIGHT else CARD_TITLE_HEIGHT,
                    shape = CARD_SHAPE,
                    titlePlacement = CardTitle.Below,
                    contentPadding = 6.dp,
                    focusRequester = if (video.videoId == restoreFocusVideoId) focusRequester else null,
                    onClick = { onVideoClick(video) },
                    onFocus = { onVideoFocus(video) },
                    onLongClick = { onVideoLongClick(video) },
                )
            }
        }
    }
}
