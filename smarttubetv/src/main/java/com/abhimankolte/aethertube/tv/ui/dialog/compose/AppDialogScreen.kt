@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.abhimankolte.aethertube.tv.ui.dialog.compose

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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem

private val LEFT_PANEL_WIDTH = 240.dp
private const val FOCUS_ANIM_MS = 180

// Apple TV-style focus scale: a light spring with a touch of overshoot instead of a rigid linear/cubic
// tween - snappy enough not to lag behind fast D-pad navigation, but feels alive rather than mechanical.
private val FocusScaleSpring = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
private val PANEL_WIDTH = 480.dp

/** Matches the 220ms the old window transition used, but animated inside Compose - see AppDialogScreen. */
private const val PANEL_ENTER_MS = 220
private val PANEL_SHAPE = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)

/**
 * A right-anchored panel over a dimmed scrim, not a full-screen takeover - the hosting Activity
 * window is translucent (see ComposeAppDialogActivity's theme) so whatever's underneath stays
 * visible. Used for dialogs reached from outside the Settings screen (context menus, confirmations,
 * SponsorBlock, etc.) that don't have an already-visible Compose screen to render inline into.
 *
 * The dedicated Settings screen (see [com.abhimankolte.aethertube.tv.ui.settings.compose.ComposeSettingsFragment])
 * doesn't use this either - it renders [AppDialogPanelContent] directly as a permanent, opaque
 * two-pane layout instead of an overlay/flyout.
 */
@Composable
fun AppDialogScreen(
    title: String?,
    categories: List<OptionCategory>,
    showBackButton: Boolean,
    onBack: () -> Unit,
    isOverlay: Boolean = false,
) {
    // AppDialogPresenter#enableOverlay(true) (chapter notifications, SponsorBlock's in-player prompt)
    // means "float this over whatever's playing, don't block it" - those callers get a much lighter
    // scrim. Everything else (subtitles/speed/quality pickers, context menus) keeps the heavier one,
    // same as opening the dedicated Settings screen.
    val scrimAlpha = if (isOverlay) 0.15f else 0.5f

    // Entrance animation lives here, in Compose, rather than as a window transition on the Activity -
    // see the comment on App.Theme.Compose.Dialog in styles.xml. A window animation blacks out the
    // video playing underneath (SurfaceView can't composite into an activity animation layer); this
    // animates only our own content inside an already-composited translucent window, so playback below
    // is untouched and the dialog opens immediately.
    var isPanelVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isPanelVisible = true }

    val panelOffsetFraction by animateFloatAsState(
        targetValue = if (isPanelVisible) 0f else 1f,
        animationSpec = tween(PANEL_ENTER_MS),
        label = "dialogPanelSlide",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            // A flat scrim across the whole screen hid the video everywhere, not just behind the
            // panel - this is reached constantly *during active playback* (quality/subtitles/speed),
            // and the whole point of a right-anchored panel over a translucent window is to keep
            // watching while you adjust something. Fade it in from fully transparent on the left
            // (where the video is) to the target alpha only right at the panel's own edge.
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = scrimAlpha),
                ),
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        AppDialogPanelContent(
            title = title,
            categories = categories,
            showBackButton = showBackButton,
            onBack = onBack,
            modifier = Modifier
                .offset { IntOffset(x = (panelOffsetFraction * PANEL_WIDTH.roundToPx()).toInt(), y = 0) }
                .fillMaxHeight()
                .width(PANEL_WIDTH)
                .clip(PANEL_SHAPE)
                .background(MaterialTheme.colorScheme.surface),
        )
    }
}

/**
 * The actual dialog content (header + category list + options) with no positioning/background/scrim
 * of its own - callers decide how to place and frame it. Left = category list, right = the selected
 * category's options - like the real Android/Android TV Settings app, instead of leanback's
 * single-pane push/pop preference screens. When there's only one category (leanback's "expandable"
 * single-dialog case, e.g. a plain "pick one" prompt) the category list is skipped entirely.
 */
@Composable
fun AppDialogPanelContent(
    title: String?,
    categories: List<OptionCategory>,
    showBackButton: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // weight(1f), not just fillMaxSize(): a Column doesn't shrink a later, unweighted child's max-height
    // constraint by whatever DialogHeader (fixed height) already used - without weight, the option list
    // (or the whole categories Row) was measured against the full panel height, overflowing past the
    // header by the header's own height and clipping the bottom of the list.
    Column(modifier = modifier) {
        if (categories.size <= 1) {
            val category = categories.firstOrNull()
            // category?.title alone renders blank for OptionCategory.singleSwitch()/singleButton() -
            // neither factory sets it, only their one OptionItem's own title. categoryDisplayTitle()
            // already falls back to that (used a few lines down for the multi-category list); the
            // single-category header just wasn't using it.
            DialogHeader(
                title = title ?: category?.let { categoryDisplayTitle(it) },
                showBackButton = showBackButton,
                onBack = onBack,
            )
            if (category != null) {
                OptionList(category = category, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            var selectedIndex by remember(categories) { mutableStateOf(0) }
            var lastFocusedCategory by remember(categories) { mutableStateOf(0) }
            val categoryRequesters = remember(categories) { categories.indices.map { FocusRequester() } }

            DialogHeader(title = title, showBackButton = showBackButton, onBack = onBack)

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Plain Column for the same reason as the settings rail: focusRestorer can only
                // restore a child that is still attached, which a lazy layout does not guarantee.
                Column(
                    modifier = Modifier
                        .width(LEFT_PANEL_WIDTH)
                        .fillMaxHeight()
                        // Deterministic re-entry, same reasoning as the settings rail.
                        .focusProperties {
                            onEnter = {
                                categoryRequesters.getOrNull(lastFocusedCategory)?.let {
                                    try {
                                        it.requestFocus()
                                    } catch (e: IllegalStateException) { }
                                }
                            }
                        }
                        .focusGroup()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                ) {
                    categories.forEachIndexed { index, category ->
                        CategoryRow(
                            title = categoryDisplayTitle(category),
                            isSelected = index == selectedIndex,
                            onSelected = {
                                lastFocusedCategory = index
                                selectedIndex = index
                            },
                            focusRequester = categoryRequesters.getOrNull(index),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.border.copy(alpha = 0.5f)),
                )

                OptionList(
                    category = categories[selectedIndex],
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f),
                )
            }
        }
    }
}

/**
 * OptionCategory.singleSwitch()/singleButton() (used constantly throughout the settings presenters
 * for a single toggle or action mixed in among bigger categories) never set a category-level title -
 * only their one contained OptionItem has one. Without this fallback, every such category renders as
 * a blank, unlabeled row in the left rail - navigable and functional, but unreadable.
 */
private fun categoryDisplayTitle(category: OptionCategory): String {
    val ownTitle = category.title?.toString()
    if (!ownTitle.isNullOrBlank()) {
        return ownTitle
    }

    return category.options?.firstOrNull()?.title?.toString().orEmpty()
}

@Composable
private fun DialogHeader(title: String?, showBackButton: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (showBackButton) {
            BackButton(onClick = onBack)
        }

        if (!title.isNullOrEmpty()) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.15f else 1f, FocusScaleSpring, label = "backScale")
    val tint by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        tween(FOCUS_ANIM_MS),
        label = "backTint",
    )

    Icon(
        imageVector = Icons.Filled.ArrowBack,
        contentDescription = "Back",
        tint = tint,
        modifier = Modifier
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {},
            ),
    )
}

/** Plain text row, matching HomeScreen's SectionTab: weight/color shift + a thin accent underline - no fill. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    title: String,
    isSelected: Boolean,
    onSelected: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val textColor by animateColorAsState(
        if (isFocused || isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(FOCUS_ANIM_MS),
        label = "categoryText",
    )
    val barColor by animateColorAsState(
        when {
            isFocused -> MaterialTheme.colorScheme.primary
            isSelected -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> Color.Transparent
        },
        tween(FOCUS_ANIM_MS),
        label = "categoryBar",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { state -> if (state.isFocused) onSelected() }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelected,
                onLongClick = {},
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 18.dp)
                .background(barColor),
        )

        Text(
            text = title,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun OptionList(category: OptionCategory, modifier: Modifier = Modifier) {
    when (category.type) {
        OptionCategory.TYPE_LONG_TEXT -> LongTextContent(category)
        else -> {
            // TYPE_RADIO_LIST / TYPE_STRING_LIST: single-select; TYPE_CHECKBOX_LIST / TYPE_SINGLE_SWITCH:
            // independent toggles; TYPE_SINGLE_BUTTON: one plain action row, not checkable.
            val isSingleSelect = category.type == OptionCategory.TYPE_RADIO_LIST || category.type == OptionCategory.TYPE_STRING_LIST
            val isButton = category.type == OptionCategory.TYPE_SINGLE_BUTTON
            val options = category.options.orEmpty()

            // OptionItem#getId() defaults to 0 for almost every real item (most UiOptionItem factories
            // never set it) - never use it as a list/state key, it collides constantly. Index is safe:
            // each category's option list is rebuilt fresh per show() call and stable for its lifetime.
            var selectedIndex by remember(category) {
                mutableStateOf(options.indexOfFirst { it.isSelected }.let { if (it == -1) null else it })
            }
            val checkedState = remember(category) {
                mutableStateListOf<Boolean>().apply { options.forEach { add(it.isSelected) } }
            }

            // Deterministic entry, matching both rails. This list is the third focus level, and it
            // was the one still drifting: nested dialogs show a BackButton in the header (see
            // DialogHeader - it only appears once detailFrames.size > 1), so the panel gains an extra
            // focusable at nested levels and default focus ordering shifts by exactly one. Redirecting
            // entry at an explicit row makes the header's contents irrelevant to where focus lands.
            // Start on the currently-selected option rather than the top - for a radio list that is
            // the row the user most likely wants.
            var lastFocusedOption by remember(category) {
                mutableStateOf(options.indexOfFirst { it.isSelected }.coerceAtLeast(0))
            }
            val entryRequester = remember(category) { FocusRequester() }

            LazyColumn(
                modifier = modifier
                    .focusProperties {
                        onEnter = {
                            // Only attached while that row is composed; falling through to default
                            // entry is fine and is why this is not allowed to throw.
                            try {
                                entryRequester.requestFocus()
                            } catch (e: IllegalStateException) { }
                        }
                    }
                    .focusGroup(),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 20.dp),
            ) {
                itemsIndexed(options, key = { index, _ -> index }) { index, item ->
                    val isChecked = when {
                        isButton -> false
                        isSingleSelect -> index == selectedIndex
                        else -> checkedState.getOrElse(index) { item.isSelected }
                    }

                    OptionRow(
                        item = item,
                        isCheckable = !isButton,
                        isChecked = isChecked,
                        focusRequester = if (index == lastFocusedOption) entryRequester else null,
                        onFocused = { lastFocusedOption = index },
                        onClick = {
                            when {
                                isButton -> item.onSelect(true)
                                isSingleSelect -> {
                                    selectedIndex = index
                                    item.onSelect(true)
                                }
                                else -> {
                                    val newChecked = !checkedState.getOrElse(index) { item.isSelected }
                                    checkedState[index] = newChecked
                                    item.onSelect(newChecked)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LongTextContent(category: OptionCategory) {
    val item = category.options?.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 20.dp),
    ) {
        item {
            Text(
                text = item?.description?.toString() ?: item?.title?.toString().orEmpty(),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
            )
        }
    }
}

/** Plain text row with a small check/radio glyph - no card fill, matching the rest of the app's minimal chrome. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OptionRow(
    item: OptionItem,
    isCheckable: Boolean,
    isChecked: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val contentColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        tween(FOCUS_ANIM_MS),
        label = "optionContent",
    )
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f, FocusScaleSpring, label = "optionScale")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {},
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isCheckable) {
            CheckIndicator(checked = isChecked, tint = contentColor)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title?.toString().orEmpty(),
                color = contentColor,
                fontSize = 16.sp,
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
            )

            val description = item.description?.toString()
            if (!description.isNullOrEmpty()) {
                Text(text = description, color = contentColor.copy(alpha = 0.65f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CheckIndicator(checked: Boolean, tint: Color) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(if (checked) tint else Color.Transparent, CircleShape)
            .border(BorderStroke(1.5.dp, tint), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
