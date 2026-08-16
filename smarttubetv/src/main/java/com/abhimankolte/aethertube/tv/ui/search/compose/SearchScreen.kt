@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.abhimankolte.aethertube.tv.ui.search.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.abhimankolte.aethertube.tv.ui.common.compose.CardTitle
import com.abhimankolte.aethertube.tv.ui.common.compose.VideoCard
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData

private val CARD_WIDTH = 220.dp
private val CARD_HEIGHT = 124.dp
private val CARD_SHAPE = RoundedCornerShape(14.dp)
private val CARD_TITLE_SIZE = 14.sp
private val CARD_TITLE_LINE_HEIGHT = 18.sp
private const val FOCUS_ANIM_MS = 180
// Apple TV-style focus scale: a light spring with a touch of overshoot instead of a rigid linear/cubic
// tween - snappy enough not to lag behind fast D-pad navigation, but feels alive rather than mechanical.
private val FocusScaleSpring = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
private const val BACKDROP_DEBOUNCE_MS = 220L
// Mirrors leanback's ViewUtil.ROW_SCROLL_CONTINUE_NUM - start fetching the next page once the viewport
// is within this many items of the end, rather than waiting for the last item to actually be reached.
private const val ROW_SCROLL_CONTINUE_NUM = 4

/**
 * Compose-for-TV replacement for the leanback SearchSupportFragment UI.
 */
@Composable
fun SearchScreen(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    showProgress: Boolean,
    backdropUrl: String?,
    tags: List<Tag>,
    onTagClick: (Tag) -> Unit,
    onTagLongClick: (Tag) -> Unit,
    resultRows: List<SearchResultRow>,
    onVideoClick: (Video) -> Unit,
    onVideoFocus: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    onScrollEnd: (Video) -> Unit,
    onSearchSettingsClick: () -> Unit,
    searchFieldFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    // See HomeScreen's identical debounce - flicking focus across the result grid fired a full-screen
    // Glide crossfade per card without this, reading as "the page refreshing".
    var debouncedBackdropUrl by remember { mutableStateOf(backdropUrl) }
    LaunchedEffect(backdropUrl) {
        delay(BACKDROP_DEBOUNCE_MS)
        debouncedBackdropUrl = backdropUrl
    }

    Box(modifier = modifier.fillMaxSize()) {
        HeroBackdrop(imageUrl = debouncedBackdropUrl)

        // Shared so Down from the search field and Up from any tag chip land on the same target
        // deterministically, instead of Compose's default spatial search picking whichever chip
        // is geometrically closest to the (wide, full-row) search field's center - which is
        // rarely the first suggestion once there are more than two or three tags.
        val firstTagFocusRequester = remember { FocusRequester() }

        Column(modifier = Modifier.fillMaxSize()) {
            SearchBar(
                searchText = searchText,
                onSearchTextChange = onSearchTextChange,
                onSearchSubmit = onSearchSubmit,
                showProgress = showProgress,
                onSettingsClick = onSearchSettingsClick,
                focusRequester = searchFieldFocusRequester,
                hasTags = tags.isNotEmpty(),
                firstTagFocusRequester = firstTagFocusRequester
            )

            if (tags.isNotEmpty()) {
                TagsRow(
                    tags = tags,
                    onTagClick = onTagClick,
                    onTagLongClick = onTagLongClick,
                    firstTagFocusRequester = firstTagFocusRequester,
                    upFocusRequester = searchFieldFocusRequester
                )
            }

            // weight(1f), not just fillMaxSize(): a Column doesn't shrink a later, unweighted child's
            // max-height constraint by whatever SearchBar/TagsRow already used above it - without
            // weight, this list was measured against the full screen height and its bottom overflowed
            // past the visible area by however tall those fixed-height siblings are.
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(resultRows, key = { it.id }) { row ->
                    VideoResultRow(
                        row = row,
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

@Composable
private fun SearchBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    showProgress: Boolean,
    onSettingsClick: () -> Unit,
    focusRequester: FocusRequester,
    hasTags: Boolean,
    firstTagFocusRequester: FocusRequester
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.border,
        tween(FOCUS_ANIM_MS),
        label = "searchBarBorder"
    )
    val iconColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(FOCUS_ANIM_MS),
        label = "searchIconColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                .border(BorderStroke(2.dp, borderColor), CircleShape)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = iconColor)

            BasicTextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        isFocused = state.isFocused
                        if (state.isFocused) {
                            keyboardController?.show()
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            false
                        } else if (event.key == Key.DirectionDown) {
                            // BasicTextField can swallow DPAD Down as a cursor-navigation key on some TV
                            // devices/keyboards instead of letting it fall through to TV focus navigation -
                            // force it down to whatever's below (tags row or results) explicitly.
                            //
                            // moveFocus(Down) alone used Compose's default spatial search: it picks
                            // whichever focusable is geometrically closest to this field's center, and this
                            // field spans nearly the full row width while the tag row starts at the left
                            // edge - past two or three tags, that's rarely tag #0. Go straight to the first
                            // tag when there are any; runCatching covers the one frame where it's requested
                            // before TagsRow has composed it (tags flips true and the LazyRow needs a beat).
                            if (hasTags) {
                                runCatching { firstTagFocusRequester.requestFocus() }
                                    .onFailure { focusManager.moveFocus(FocusDirection.Down) }
                            } else {
                                focusManager.moveFocus(FocusDirection.Down)
                            }
                            true
                        } else if (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter) {
                            // KeyboardActions.onSearch only fires through an actual bound software IME's
                            // action button - on a remote-only TV session with no soft keyboard shown,
                            // the OK/center key never reaches it. Submit directly from the raw key event too.
                            keyboardController?.hide()
                            onSearchSubmit()
                            true
                        } else {
                            false
                        }
                    },
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp),
                cursorBrush = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() })
            )

            if (showProgress) {
                // NOTE: androidx.tv.material3 has no CircularProgressIndicator (that's a
                // compose-material/material3 component); a plain label avoids pulling in that dependency.
                Text(text = "...", color = iconColor)
            }

            Icon(imageVector = Icons.Filled.Mic, contentDescription = null, tint = iconColor)
        }

        SearchSettingsIcon(onClick = onSettingsClick)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchSettingsIcon(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val tint by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(FOCUS_ANIM_MS),
        label = "searchSettingsTint"
    )

    Icon(
        imageVector = Icons.Filled.Settings,
        contentDescription = "Search settings",
        tint = tint,
        modifier = Modifier
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {}
            )
    )
}

@Composable
private fun TagsRow(
    tags: List<Tag>,
    onTagClick: (Tag) -> Unit,
    onTagLongClick: (Tag) -> Unit,
    firstTagFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(tags, key = { _, tag -> if (tag.tagId != 0L) tag.tagId else tag.tag.hashCode().toLong() }) { index, tag ->
            TagChip(
                tag = tag,
                onClick = { onTagClick(tag) },
                onLongClick = { onTagLongClick(tag) },
                focusRequester = if (index == 0) firstTagFocusRequester else null,
                upFocusRequester = upFocusRequester
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagChip(
    tag: Tag,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    focusRequester: FocusRequester?,
    upFocusRequester: FocusRequester
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.06f else 1f, FocusScaleSpring, label = "tagScale")
    val backgroundColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        tween(FOCUS_ANIM_MS),
        label = "tagBackground"
    )
    val textColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(FOCUS_ANIM_MS),
        label = "tagText"
    )

    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            // Up is otherwise unspecified spatial search again - same failure mode as the search
            // field's Down key, just in reverse. Every chip (not only the first) gets this, since
            // the row scrolls and whichever chip is focused when Up is pressed should return to
            // the field it came from, not wherever geometry happens to land.
            .focusProperties { up = upFocusRequester }
            .scale(scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Text(
            text = tag.tag.orEmpty(),
            color = textColor,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun VideoResultRow(
    row: SearchResultRow,
    onVideoClick: (Video) -> Unit,
    onVideoFocus: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    onScrollEnd: (Video) -> Unit
) {
    val listState = rememberLazyListState()
    val lastVideo = row.videos.lastOrNull()

    // Pagination driven by viewport proximity to the end rather than the last item's composition -
    // see HomeScreen's GridContent for why the composition-keyed approach can't page past screen one.
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

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = row.title,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            modifier = Modifier.padding(start = 32.dp, bottom = 12.dp)
        )

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // index fallback, not hashCode(): Video.hashCode() is content-based, so two null-videoId
            // entries with otherwise-identical fields collide and crash the LazyRow.
            itemsIndexed(row.videos, key = { index, video -> video.videoId ?: "video_$index" }) { _, video ->
                VideoCard(
                    video = video,
                    width = CARD_WIDTH,
                    height = CARD_HEIGHT,
                    titleFontSize = CARD_TITLE_SIZE,
                    titleLineHeight = CARD_TITLE_LINE_HEIGHT,
                    titleHeight = 0.dp, // overlaid on the artwork, so it needs no strip of its own
                    shape = CARD_SHAPE,
                    titlePlacement = CardTitle.Overlay,
                    contentPadding = 4.dp,
                    onClick = { onVideoClick(video) },
                    onFocus = { onVideoFocus(video) },
                    onLongClick = { onVideoLongClick(video) }
                )
            }
        }
    }
}

