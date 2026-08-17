@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.abhimankolte.aethertube.tv.ui.home.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.abhimankolte.aethertube.tv.ui.common.compose.CardTitle
import com.abhimankolte.aethertube.tv.ui.common.compose.VideoCard
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video

/** Grid layout for multi-column video feeds, supporting optional full-width section headers. */
@Composable
internal fun GridContent(
    rows: List<HomeRow>,
    showRowHeaders: Boolean,
    topNavFocusRequester: FocusRequester,
    restoreFocusVideoId: String?,
    onFocusRestored: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onVideoFocus: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    onScrollEnd: (Video) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }

    // Flattens rows into grid entries; derivedStateOf ensures reactivity to in-place SnapshotStateList mutations.
    val entries by remember(showRowHeaders) {
        derivedStateOf {
            if (showRowHeaders) {
                rows.flatMap { row -> listOf(GridEntry.Header(row.id, row.title)) + row.videos.map { GridEntry.VideoEntry(it) } }
            } else {
                rows.flatMap { it.videos }.map { GridEntry.VideoEntry(it) }
            }
        }
    }
    val lastVideo = rows.lastOrNull()?.videos?.lastOrNull()

    // Topmost video row items explicitly redirect D-pad Up to TopNav to avoid unpredictable spatial jumps.
    val topRowIndices by remember(entries) {
        derivedStateOf<Set<Int>> {
            // Only the row that's actually first in the list should redirect Up to TopNav - without
            // this guard, scrolling to row 10 would inherit the row-0 redirect and Up would skip
            // rows 1-9 entirely instead of moving within the grid.
            if (gridState.firstVisibleItemIndex > 0) return@derivedStateOf emptySet()
            val visible = gridState.layoutInfo.visibleItemsInfo
            val topVideoRow = visible
                .filter { entries.getOrNull(it.index) is GridEntry.VideoEntry }
                .minOfOrNull { it.row }
                ?: return@derivedStateOf emptySet()
            visible.filter { it.row == topVideoRow }.mapTo(mutableSetOf()) { it.index }
        }
    }

    val targetIndex = remember(entries, restoreFocusVideoId) {
        if (restoreFocusVideoId == null) {
            -1
        } else {
            entries.indexOfFirst { it is GridEntry.VideoEntry && it.video.videoId == restoreFocusVideoId }
        }
    }

    LaunchedEffect(targetIndex) {
        if (targetIndex > 0) {
            gridState.scrollToItem(targetIndex)
        }
        if (targetIndex >= 0) {
            requestFocusSafely(focusRequester)
            onFocusRestored()
        }
    }

    // Viewport-proximity pagination prefetching the next page before reaching scroll boundary.
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= total - GRID_SCROLL_CONTINUE_NUM
        }
    }

    LaunchedEffect(shouldLoadMore, lastVideo?.videoId) {
        if (shouldLoadMore && lastVideo != null) {
            onScrollEnd(lastVideo)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = CARD_WIDTH + 16.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItemsIndexed(
            entries,
            key = { index, entry ->
                when (entry) {
                    is GridEntry.Header -> "header_${entry.rowId}"
                    // Fallback key ensuring uniqueness across placeholder or duplicate items.
                    is GridEntry.VideoEntry -> entry.video.videoId ?: "video_$index"
                }
            },
            span = { _, entry -> if (entry is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
        ) { index, entry ->
            when (entry) {
                is GridEntry.Header -> Text(
                    text = entry.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                is GridEntry.VideoEntry -> {
                    val video = entry.video

                    VideoCard(
                        video = video,
                        width = CARD_WIDTH,
                        height = CARD_HEIGHT,
                        titleFontSize = CARD_TITLE_SIZE,
                        titleLineHeight = CARD_TITLE_LINE_HEIGHT,
                        titleHeight = CARD_TITLE_HEIGHT,
                        shape = CARD_SHAPE,
                        titlePlacement = CardTitle.Below,
                        contentPadding = 6.dp,
                        focusRequester = if (video.videoId == restoreFocusVideoId) focusRequester else null,
                        modifier = if (index in topRowIndices) {
                            Modifier.focusProperties { up = topNavFocusRequester }
                        } else {
                            Modifier
                        },
                        onClick = { onVideoClick(video) },
                        onFocus = { onVideoFocus(video) },
                        onLongClick = { onVideoLongClick(video) },
                    )
                }
            }
        }
    }
}

internal sealed class GridEntry {
    class Header(val rowId: Int, val title: String) : GridEntry()
    class VideoEntry(val video: Video) : GridEntry()
}
