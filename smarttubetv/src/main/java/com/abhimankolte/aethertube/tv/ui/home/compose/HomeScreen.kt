@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi::class,
)

package com.abhimankolte.aethertube.tv.ui.home.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.abhimankolte.aethertube.tv.ui.common.compose.MotionTokens
import com.abhimankolte.aethertube.tv.ui.search.compose.HeroBackdrop
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData
import kotlinx.coroutines.delay

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
    accountAvatarUrl: String?,
    onAccountClick: () -> Unit,
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
    modifier: Modifier = Modifier,
) {
    // Debounce backdrop updates to avoid rapid crossfades during fast D-pad navigation.
    var debouncedBackdropUrl by remember { mutableStateOf(backdropUrl) }
    LaunchedEffect(backdropUrl) {
        delay(MotionTokens.BACKDROP_DEBOUNCE_MS)
        debouncedBackdropUrl = backdropUrl
    }

    // Directs D-pad Up explicitly to the active section tab, preventing geometric spatial search from jumping to wrong tabs.
    val topNavFocusRequester = remember { FocusRequester() }
    // Fallback focus target for initial navigation before any tab has been focused.
    val activeTabFocusRequester = remember { FocusRequester() }

    // Intercepts BACK from deep in content to return focus to TopNav before exiting the screen.
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
                accountAvatarUrl = accountAvatarUrl,
                onAccountClick = onAccountClick,
                showProgress = showProgress,
                activeTabFocusRequester = activeTabFocusRequester,
                navRowFocusRequester = topNavFocusRequester,
                modifier = Modifier.onFocusChanged { state -> isTopNavFocused = state.hasFocus },
            )

            // Uses weight(1f) to ensure content viewport accounts for TopNav height and prevents bottom-edge clipping.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val showShimmer = showProgress && rows.isEmpty()

                if (errorData != null) {
                    ErrorContent(data = errorData)
                } else if (showShimmer) {
                    // Only show shimmer on initial empty load, not during pagination.
                    ShimmerContent()
                } else {
                    when (sectionType) {
                        BrowseSection.TYPE_ROW -> ShelvesContent(
                            rows = rows,
                            topNavFocusRequester = topNavFocusRequester,
                            restoreFocusVideoId = restoreFocusVideoId,
                            onFocusRestored = onFocusRestored,
                            onVideoClick = onVideoClick,
                            onVideoFocus = onVideoFocus,
                            onVideoLongClick = onVideoLongClick,
                            onScrollEnd = onScrollEnd,
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
                            onScrollEnd = onScrollEnd,
                        )
                    }
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
        label = "shimmerAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
    ) {
        repeat(3) { rowIndex ->
            Column(modifier = Modifier.padding(vertical = if (rowIndex == 0) 8.dp else 14.dp)) {
                Box(
                    modifier = Modifier
                        .padding(start = 40.dp, bottom = 14.dp)
                        .size(width = 160.dp, height = if (rowIndex == 0) 26.dp else 20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    val cardWidth = if (rowIndex == 0) FEATURED_CARD_WIDTH else CARD_WIDTH
                    val cardHeight = if (rowIndex == 0) FEATURED_CARD_HEIGHT else CARD_HEIGHT

                    repeat(5) {
                        Box(
                            modifier = Modifier
                                .size(width = cardWidth, height = cardHeight)
                                .clip(CARD_SHAPE)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
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
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = data.message.orEmpty(),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 20.dp),
        )

        val actionText = data.actionText
        if (!actionText.isNullOrEmpty()) {
            ErrorActionButton(text = actionText, onClick = { data.onAction() })
        }
    }
}

@Composable
private fun ErrorActionButton(text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, MotionTokens.FocusScaleSpring, label = "errorActionScale")
    val backgroundColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        tween(MotionTokens.FOCUS_ANIM_MS),
        label = "errorActionBackground",
    )
    val contentColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(MotionTokens.FOCUS_ANIM_MS),
        label = "errorActionContent",
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 28.dp, vertical = 14.dp),
    ) {
        Text(text = text, color = contentColor, fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal)
    }
}
