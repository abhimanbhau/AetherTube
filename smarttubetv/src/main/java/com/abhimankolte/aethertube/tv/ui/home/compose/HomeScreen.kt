@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.abhimankolte.aethertube.tv.ui.home.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
// Lazy layouts are androidx.compose.foundation's. They were briefly androidx.tv.foundation's
// TvLazy* fork, which existed because older Compose could not move D-pad focus into
// not-yet-composed items - lists dead-ended at one screenful. Modern Compose handles TV focus
// natively, which is why androidx.tv 1.0.0 removed TvLazy* entirely.
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Carousel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.abhimankolte.aethertube.tv.ui.common.compose.CardTitle
import com.abhimankolte.aethertube.tv.ui.common.compose.VideoCard
import com.abhimankolte.aethertube.tv.ui.search.compose.HeroBackdrop
import com.abhimankolte.aethertube.tv.ui.search.compose.SearchVideoCardView
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData

private val FEATURED_CARD_WIDTH = 360.dp
private val FEATURED_CARD_HEIGHT = 202.dp
private val CARD_WIDTH = 240.dp
private val CARD_HEIGHT = 135.dp
private val CARD_SHAPE = RoundedCornerShape(16.dp)
private val TOP_NAV_HEIGHT = 84.dp
private const val FOCUS_ANIM_MS = 180
// Title metrics are explicit so the strip always fits exactly two lines - Compose 1.10's text
// metrics differ from 1.4's, and a hardcoded height silently clipped the second line.
private val CARD_TITLE_SIZE = 13.sp
private val CARD_TITLE_LINE_HEIGHT = 16.sp
private val CARD_TITLE_HEIGHT = 38.dp
private val FEATURED_TITLE_SIZE = 16.sp
private val FEATURED_TITLE_LINE_HEIGHT = 20.sp
private val FEATURED_TITLE_HEIGHT = 46.dp
// Apple TV-style focus scale: a light spring with a touch of overshoot instead of a rigid linear/cubic
// tween - snappy enough not to lag behind fast D-pad navigation, but feels alive rather than mechanical.
private val FocusScaleSpring = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
private const val BACKDROP_DEBOUNCE_MS = 220L
/** Breathing room inside the nav icons' focus chip. */
private val NAV_ICON_PADDING = 10.dp
// Mirrors leanback's ViewUtil.GRID_SCROLL_CONTINUE_NUM / ROW_SCROLL_CONTINUE_NUM: start fetching the
// next page once the viewport is within this many items of the end, rather than waiting to hit it.
private const val GRID_SCROLL_CONTINUE_NUM = 10
private const val ROW_SCROLL_CONTINUE_NUM = 4

/**
 * Apple TV-inspired Home layout: a plain-text top nav (no pill chrome, unlike Search), a large
 * ambient hero backdrop reacting to focus, a bigger "featured" first shelf, and standard shelves below.
 */
@Composable
fun HomeScreen(
    sections: List<BrowseSection>,
    selectedSectionId: Int,
    sectionType: Int,
    onSectionSelected: (Int) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    showProgress: Boolean,
    backdropUrl: String?,
    rows: List<HomeRow>,
    errorData: ErrorFragmentData?,
    onVideoClick: (Video) -> Unit,
    onVideoFocus: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    onScrollEnd: (Video) -> Unit,
    restoreFocusVideoId: String?,
    onFocusRestored: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Rapidly flicking focus across a dense grid fired a full-screen Glide crossfade on every single
    // card - visually reads as "the page refreshing". Debounce so the backdrop only updates once focus
    // actually settles somewhere, not on every intermediate card the cursor passes through.
    var debouncedBackdropUrl by remember { mutableStateOf(backdropUrl) }
    LaunchedEffect(backdropUrl) {
        delay(BACKDROP_DEBOUNCE_MS)
        debouncedBackdropUrl = backdropUrl
    }

    // Compose's default D-pad-up spatial search picks whatever focusable happens to be geometrically
    // nearest, which for a multi-column grid under a compact, variable-width tab strip is essentially
    // random - it can land on a tab that isn't even the one that's selected, and since tabs switch
    // sections the instant they're focused, that reads as "pressing up teleports me to a random tab".
    // Content areas explicitly redirect here instead once they're scrolled to the top, rather than
    // trusting that search - and TopNav attaches this to whichever tab is actually selected (see
    // activeTabFocusRequester below), so up always returns to the tab you're currently viewing, not
    // a fixed anchor like the search icon.
    val topNavFocusRequester = remember { FocusRequester() }
    // Fallback target for focusRestorer: the tab matching the section on screen. Only used when the
    // strip has no remembered previously-focused child (e.g. first entry into the app).
    val activeTabFocusRequester = remember { FocusRequester() }

    // Real leanback's BrowseSupportFragment has setHeadersTransitionOnBackEnabled(true) - BACK from
    // anywhere in a section's content returns to the header row first, only exiting (or the double-back
    // toast) once you're already there, same as Netflix. Nothing here replicated that: BACK from deep in
    // a grid/shelf fell straight through to the Activity's exit handling regardless of scroll position.
    var isTopNavFocused by remember { mutableStateOf(true) }
    androidx.activity.compose.BackHandler(enabled = !isTopNavFocused) {
        requestFocusSafely(topNavFocusRequester)
    }

    Box(modifier = modifier.fillMaxSize()) {
        HeroBackdrop(imageUrl = debouncedBackdropUrl)

        Column(modifier = Modifier.fillMaxSize()) {
            TopNav(
                sections = sections,
                selectedSectionId = selectedSectionId,
                onSectionSelected = onSectionSelected,
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                showProgress = showProgress,
                activeTabFocusRequester = activeTabFocusRequester,
                navRowFocusRequester = topNavFocusRequester,
                modifier = Modifier.onFocusChanged { state -> isTopNavFocused = state.hasFocus }
            )

            // Weighted, not just fillMaxSize(): a Column doesn't shrink a later, unweighted child's
            // max-height constraint by whatever an earlier sibling (TopNav, fixed height) already used -
            // each non-weighted child is measured against the Column's FULL incoming height. Without
            // weight() here, every content composable's own internal fillMaxSize() resolved to the full
            // screen height instead of "screen height minus TopNav", so the bottom TOP_NAV_HEIGHT worth
            // of content (its last row, or a whole extra row on a dense grid like Shorts) was laid out
            // past the visible screen edge - clipped, and in some cases genuinely unreachable by D-pad
            // since the LazyGrid's own internal viewport model considered it already "on screen".
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val showShimmer = showProgress && rows.isEmpty()

                if (errorData != null) {
                    ErrorContent(data = errorData)
                } else if (showShimmer) {
                    // Only for the first load of a section - once rows exist, showProgress toggling for
                    // pagination/refresh shouldn't blank out content the user is already looking at.
                    ShimmerContent()
                } else when (sectionType) {
                    BrowseSection.TYPE_ROW -> ShelvesContent(
                        rows = rows,
                        topNavFocusRequester = topNavFocusRequester,
                        restoreFocusVideoId = restoreFocusVideoId,
                        onFocusRestored = onFocusRestored,
                        onVideoClick = onVideoClick,
                        onVideoFocus = onVideoFocus,
                        onVideoLongClick = onVideoLongClick,
                        onScrollEnd = onScrollEnd
                    )
                    else -> GridContent(
                        rows = rows,
                        showRowHeaders = sectionType == BrowseSection.TYPE_MULTI_GRID,
                        topNavFocusRequester = topNavFocusRequester,
                        restoreFocusVideoId = restoreFocusVideoId,
                        onFocusRestored = onFocusRestored,
                        onVideoClick = onVideoClick,
                        onVideoFocus = onVideoFocus,
                        onVideoLongClick = onVideoLongClick,
                        onScrollEnd = onScrollEnd
                    )
                }
            }
        }
    }
}

/** Skeleton shelves shown while a section's first load is in flight, instead of a bare "..." label. */
@Composable
private fun ShimmerContent() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(700), repeatMode = RepeatMode.Reverse),
        label = "shimmerAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
    ) {
        repeat(3) { rowIndex ->
            Column(modifier = Modifier.padding(vertical = if (rowIndex == 0) 8.dp else 14.dp)) {
                Box(
                    modifier = Modifier
                        .padding(start = 40.dp, bottom = 14.dp)
                        .size(width = 160.dp, height = if (rowIndex == 0) 26.dp else 20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    val cardWidth = if (rowIndex == 0) FEATURED_CARD_WIDTH else CARD_WIDTH
                    val cardHeight = if (rowIndex == 0) FEATURED_CARD_HEIGHT else CARD_HEIGHT

                    repeat(5) {
                        Box(
                            modifier = Modifier
                                .size(width = cardWidth, height = cardHeight)
                                .clip(CARD_SHAPE)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                        )
                    }
                }
            }
        }
    }
}

/** e.g. "Sign in to see more" with an action button - see [com.liskovsoft.smartyoutubetv2.common.app.models.errors.SignInError]. */
@Composable
private fun ErrorContent(data: ErrorFragmentData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = data.message.orEmpty(),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        val actionText = data.actionText
        if (!actionText.isNullOrEmpty()) {
            ErrorActionButton(text = actionText, onClick = { data.onAction() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ErrorActionButton(text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, FocusScaleSpring, label = "errorActionScale")
    val backgroundColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        tween(FOCUS_ANIM_MS),
        label = "errorActionBackground"
    )
    val contentColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(FOCUS_ANIM_MS),
        label = "errorActionContent"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {}
            )
            .padding(horizontal = 28.dp, vertical = 14.dp)
    ) {
        Text(text = text, color = contentColor, fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun ShelvesContent(
    rows: List<HomeRow>,
    topNavFocusRequester: FocusRequester,
    restoreFocusVideoId: String?,
    onFocusRestored: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onVideoFocus: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    onScrollEnd: (Video) -> Unit
) {
    val listState = rememberLazyListState()
    val restRows = rows.drop(1)

    // Which shelf holds the video we're restoring focus to (if any) - only that shelf needs to scroll
    // its own row into position and hand off a FocusRequester; everything else renders as normal. The
    // featured (index 0) row is a Carousel now, not a shelf - it manages its own item focus internally,
    // so a restore target that lands there just isn't re-focused (a minor, accepted gap: Carousel
    // doesn't expose a way to inject an external FocusRequester per item like a plain LazyRow does).
    //
    // Deliberately NOT remember(rows, ...): `rows` is a SnapshotStateList mutated in place (pagination
    // appends), so a remember() keyed on it would compare the list to itself and never see a "changed"
    // key - if the restore target hadn't loaded yet on the first computation, it would never be found
    // even once its shelf's videos arrived later. Reading rows directly keeps this reactive.
    val targetRowIndex = if (restoreFocusVideoId == null) -1 else rows.indexOfFirst { row -> row.videos.any { it.videoId == restoreFocusVideoId } }

    LaunchedEffect(targetRowIndex) {
        if (targetRowIndex > 0) {
            listState.scrollToItem(targetRowIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            // The carousel is always the topmost row - D-pad up from within it has nothing else to
            // navigate to inside this screen, so redirecting to the nav bar is unconditionally correct
            // here (unlike a grid, there's no "is this really the top row" ambiguity to resolve).
            FeaturedCarousel(
                videos = rows.firstOrNull()?.videos.orEmpty(),
                onVideoClick = onVideoClick,
                onVideoFocus = onVideoFocus,
                onVideoLongClick = onVideoLongClick,
                topNavFocusRequester = topNavFocusRequester
            )
        }

        // LazyColumn item index here matches the row's original index in `rows` (the carousel above
        // occupies slot 0 in its place), so targetRowIndex needs no offset adjustment.
        itemsIndexed(restRows, key = { _, row -> row.id }) { index, row ->
            HomeShelf(
                row = row,
                featured = false,
                restoreFocusVideoId = if (index + 1 == targetRowIndex) restoreFocusVideoId else null,
                onFocusRestored = onFocusRestored,
                onVideoClick = onVideoClick,
                onVideoFocus = onVideoFocus,
                onVideoLongClick = onVideoLongClick,
                onScrollEnd = onScrollEnd
            )
        }
    }
}

/**
 * Auto-rotating hero banner for the first ("featured") row, using androidx.tv.material3's native
 * Carousel - a proper large-format rotating spotlight instead of just a bigger horizontal-scroll row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedCarousel(
    videos: List<Video>,
    onVideoClick: (Video) -> Unit,
    onVideoFocus: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    topNavFocusRequester: FocusRequester
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
            .clip(CARD_SHAPE)
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
                    onLongClick = { onVideoLongClick(video) }
                )
                .onFocusChanged { state ->
                    if (state.isFocused) onVideoFocus(video)
                }
        ) {
            CarouselBackdrop(video = video)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = video.title.orEmpty(),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun CarouselBackdrop(video: Video) {
    val context = LocalContext.current
    val mainUIData = remember(context) { MainUIData.instance(context) }
    var cardView by remember { mutableStateOf<SearchVideoCardView?>(null) }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { sizePx = it }
    ) {
        if (sizePx.width > 0 && sizePx.height > 0) {
            AndroidView(
                factory = { ctx -> SearchVideoCardView(ctx).also { cardView = it } },
                modifier = Modifier.fillMaxSize(),
                update = { view -> view.bind(video, mainUIData.thumbQuality, mainUIData.cardPreviewType, sizePx.width, sizePx.height) },
                onReset = { view ->
                    view.stopPreview()
                    view.unbind()
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = sizePx.height * 0.35f
                    )
                )
        )
    }
}

/**
 * TYPE_GRID / TYPE_SHORTS_GRID / TYPE_MULTI_GRID all deliver their content as a single row/group from
 * the presenter (see BrowsePresenter#updateVideoGrid) - flatten whatever rows arrived into one grid.
 * When [showRowHeaders] is set (TYPE_MULTI_GRID, e.g. Subscriptions grouped by channel), each row's
 * title renders as a full-width header above its videos, mirroring leanback's per-channel columns.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun GridContent(
    rows: List<HomeRow>,
    showRowHeaders: Boolean,
    topNavFocusRequester: FocusRequester,
    restoreFocusVideoId: String?,
    onFocusRestored: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onVideoFocus: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    onScrollEnd: (Video) -> Unit
) {
    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }

    // Deliberately NOT remember(rows, ...): `rows` is the same SnapshotStateList instance across
    // recompositions (mutated in place via row.videos.addAll(...) on pagination, never reassigned), so
    // a remember() keyed on it would always compare the list to itself and never see a "changed" key -
    // entries/lastVideo would be computed once on first composition and frozen forever, silently eating
    // every paginated append and permanently breaking infinite scroll past the first load. Reading
    // rows/row.videos directly here (not inside remember) is what makes GridContent's recomposition
    // scope actually depend on their snapshot state.
    val entries = if (showRowHeaders) {
        rows.flatMap { row -> listOf(GridEntry.Header(row.id, row.title)) + row.videos.map { GridEntry.VideoEntry(it) } }
    } else {
        rows.flatMap { it.videos }.map { GridEntry.VideoEntry(it) }
    }
    val lastVideo = rows.lastOrNull()?.videos?.lastOrNull()

    // Entries currently laid out in the topmost row that actually contains a video card - for a header
    // section (TYPE_MULTI_GRID) that's row 1, not row 0, since the header itself spans and owns row 0.
    // These get an explicit focusProperties redirect for D-pad up (see below) instead of trusting
    // Compose's default spatial search, which for a multi-column grid under a compact tab strip can
    // land on an unrelated, unpredictable tab.
    val topRowIndices by remember(entries) {
        derivedStateOf<Set<Int>> {
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

    // Pagination driven by how close the viewport is to the end, mirroring leanback's
    // VideoGridFragment#checkScrollEnd (ViewUtil.GRID_SCROLL_CONTINUE_NUM). The previous approach -
    // a LaunchedEffect on the *last* item, firing only when that item itself got composed - could only
    // ever trigger while the very end of the list was already on screen. Past the first page the last
    // item sits far off-screen and never composes, so it silently stopped requesting more.
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        gridItemsIndexed(
            entries,
            key = { _, entry ->
                when (entry) {
                    is GridEntry.Header -> "header_${entry.rowId}"
                    is GridEntry.VideoEntry -> entry.video.videoId ?: entry.video.hashCode().toString()
                }
            },
            span = { _, entry -> if (entry is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) }
        ) { index, entry ->
            when (entry) {
                is GridEntry.Header -> Text(
                    text = entry.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
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
                        onLongClick = { onVideoLongClick(video) }
                    )
                }
            }
        }
    }
}

private sealed class GridEntry {
    class Header(val rowId: Int, val title: String) : GridEntry()
    class VideoEntry(val video: Video) : GridEntry()
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopNav(
    sections: List<BrowseSection>,
    selectedSectionId: Int,
    onSectionSelected: (Int) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    showProgress: Boolean,
    activeTabFocusRequester: FocusRequester,
    navRowFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    // One single scrollable strip - Settings and Search are just its first two items, not pinned
    // outside it - matching how Apple TV/Google TV/Netflix top bars are actually structured. A plain
    // Row, not LazyRow: there are only ever a handful of section tabs (~8-13) plus two icons, far too
    // few to need virtualization, and LazyRow only composing visible items meant the selected tab's
    // FocusRequester could end up attached to nothing (e.g. cold-booting straight into a non-Home
    // default section whose tab hadn't scrolled into the lazy viewport yet). A plain Row keeps every
    // item permanently composed, so activeTabFocusRequester is always attached to something real.
    val tabScrollState = rememberScrollState()
    val activeTabBringIntoViewRequester = remember { BringIntoViewRequester() }

    // Deliberately NOT remember(sections): `sections` is a SnapshotStateList mutated in place (tabs
    // added/removed, e.g. ComposeHomeFragment#pruneDisabledSections dropping a section hidden from
    // Settings), so a remember() keyed on it would compare the list to itself and never see a "changed"
    // key - a just-disabled tab would stay visible here forever, even after it's gone from `sections`.
    val visibleSections = sections.filter { it.type != BrowseSection.TYPE_SETTINGS_GRID }

    // The tab that owns the fallback focus target. Normally the section being viewed; if that id
    // isn't in the strip (nothing selected yet, section just removed) it falls back to the first tab
    // so the requester below is ALWAYS attached to a real node. An unattached FocusRequester makes
    // focus search silently fall back to spatial search, which is how "up" ended up on arbitrary tabs.
    val activeTabId = remember(visibleSections, selectedSectionId) {
        if (visibleSections.any { it.id == selectedSectionId }) selectedSectionId else visibleSections.firstOrNull()?.id ?: -1
    }

    LaunchedEffect(activeTabId, visibleSections) {
        activeTabBringIntoViewRequester.bringIntoView()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TOP_NAV_HEIGHT) // fixed - a child's fontWeight/icon toggling on focus must never reflow this bar
            // Content redirects D-pad "up" at this Row rather than at a specific tab, and
            // focusRestorer hands focus back to whichever tab was focused when you left the strip -
            // which is exactly the tab you navigated down from. Previously the up-target was a
            // FocusRequester attached to whichever tab we *believed* was selected; whenever that
            // belief was wrong (or the tab wasn't attached) focus fell back to spatial search and
            // landed on an unrelated tab, and since focusing a tab switches section, that silently
            // navigated the user somewhere else. The active tab is now only the fallback.
            .focusRequester(navRowFocusRequester)
            .focusRestorer(activeTabFocusRequester)
            // Padding goes INSIDE the scroll, not before it. Applied before, it sits outside the
            // scroll viewport, so the leftmost item begins at the viewport's own edge - and the
            // focused nav icon, which scales up about its centre, grew straight past that edge and
            // was clipped down its left side. Inside the scroll it behaves as content padding and
            // leaves the chip room to grow.
            .horizontalScroll(tabScrollState)
            .padding(horizontal = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        NavIconButton(Icons.Filled.Settings, "Settings", onSettingsClick)
        NavIconButton(Icons.Filled.Search, "Search", onSearchClick)

        // Settings is its own dedicated screen (see ComposeSettingsActivity), reached via the gear
        // icon above like Search's magnifier - not a tab, so it's excluded from this tab strip.
        for ((tabIndex, section) in visibleSections.withIndex()) {
            val index = sections.indexOf(section)
            val isSelected = section.id == activeTabId

            SectionTab(
                title = section.title.orEmpty(),
                isSelected = isSelected,
                onSelected = { onSectionSelected(index) },
                // Redirecting D-pad up here always lands on whichever tab is actually active, not
                // a fixed anchor - so up from the 5th tab's grid returns to the 5th tab, not tab 1.
                focusRequester = if (isSelected) activeTabFocusRequester else null,
                bringIntoViewRequester = if (isSelected) activeTabBringIntoViewRequester else null,
                // Nothing to the right of the last tab has a natural focus target - without this,
                // default search wraps around to the first focusable on the row (settings icon),
                // which - since a tab switches its section the instant it's focused - reads as
                // "pressing right on the last tab teleports me back to the first tab".
                isLastTab = tabIndex == visibleSections.lastIndex
            )
        }

        if (showProgress) {
            Text(text = "...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Search and Settings in the nav strip.
 *
 * These sit at the far left of a horizontally scrollable strip, so reaching them produces no scroll
 * - unlike every tab, where the strip visibly moves. Combined with only a scale-and-tint focus state
 * (tabs get a weight change *and* an underline) it read as though the press had done nothing, and it
 * was hard to tell which of the two icons was focused. They now fill with the accent colour when
 * focused, which is unambiguous at viewing distance and makes moving between the two obvious.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NavIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.12f else 1f, FocusScaleSpring, label = "navIconScale")
    val background by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        tween(FOCUS_ANIM_MS),
        label = "navIconBackground"
    )
    val tint by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        tween(FOCUS_ANIM_MS),
        label = "navIconTint"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(CircleShape)
            .background(background)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {}
            )
            .padding(NAV_ICON_PADDING),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
    }
}

/**
 * Switches content as soon as D-pad focus lands on a tab (matching the original leanback header
 * row's behavior), not only on click/press - onSelected fires from onFocusChanged, click is just
 * a redundant secondary trigger for touch/pointer input.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionTab(
    title: String,
    isSelected: Boolean,
    onSelected: () -> Unit,
    focusRequester: FocusRequester? = null,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    isLastTab: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val textColor by animateColorAsState(
        when {
            isFocused -> MaterialTheme.colorScheme.onBackground
            isSelected -> MaterialTheme.colorScheme.onBackground
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        tween(FOCUS_ANIM_MS),
        label = "tabText"
    )
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, FocusScaleSpring, label = "tabScale")

    Column(
        modifier = Modifier
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (bringIntoViewRequester != null) Modifier.bringIntoViewRequester(bringIntoViewRequester) else Modifier)
            .then(
                if (isLastTab) {
                    // Nothing focusable sits to the right of the last tab, so default search wraps
                    // around to the first focusable on the row instead of just staying put. Cancel
                    // tells Compose's focus search not to move at all in that direction from here.
                    Modifier.focusProperties { right = FocusRequester.Cancel }
                } else {
                    Modifier
                }
            )
            .onFocusChanged { state ->
                if (state.isFocused) {
                    onSelected()
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelected,
                onLongClick = {}
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 18.sp,
            lineHeight = 18.sp, // pinned - Bold vs Normal have slightly different natural line heights,
            // which without this reflows the whole fixed-height TopNav bar by a pixel or two on every focus change
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
        )

        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(width = if (isSelected) 20.dp else 0.dp, height = 3.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun HomeShelf(
    row: HomeRow,
    featured: Boolean,
    restoreFocusVideoId: String?,
    onFocusRestored: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onVideoFocus: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    onScrollEnd: (Video) -> Unit
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    // Deliberately NOT remember(row.videos, ...): row.videos is a SnapshotStateList mutated in place
    // (pagination appends), so a remember() keyed on it would compare the list to itself and never see
    // a "changed" key - if the restore target hadn't loaded yet on first computation, it would never be
    // found even once it arrived later.
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

    // Same viewport-proximity pagination as the grid - see GridContent's note.
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
            modifier = Modifier.padding(start = 40.dp, bottom = 14.dp)
        )

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(row.videos, key = { video -> video.videoId ?: video.hashCode().toString() }) { video ->
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
                    onLongClick = { onVideoLongClick(video) }
                )
            }
        }
    }
}

/** [FocusRequester.requestFocus] throws if the target isn't composed yet (e.g. scroll hasn't settled). */
private fun requestFocusSafely(focusRequester: FocusRequester) {
    try {
        focusRequester.requestFocus()
    } catch (e: IllegalStateException) {
        // no-op: not composed yet, D-pad navigation just keeps whatever focus it already had
    }
}

