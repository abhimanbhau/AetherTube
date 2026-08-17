@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.abhimankolte.aethertube.tv.ui.dialog.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.abhimankolte.aethertube.tv.ui.common.compose.MotionTokens
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem

private val LEFT_PANEL_WIDTH = 240.dp
private val PANEL_WIDTH = 480.dp

/** Enter transition duration for the side panel. */
private const val PANEL_ENTER_MS = 220
private val PANEL_SHAPE = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)

/** Right-anchored side panel dialog for playback options, context menus, and player settings. */
@Composable
fun AppDialogScreen(
    title: String?,
    categories: List<OptionCategory>,
    showBackButton: Boolean,
    onBack: () -> Unit,
    isOverlay: Boolean = false,
    initialCategoryIndex: Int = 0,
    onCategoryIndexChange: (Int) -> Unit = {},
) {
    // Lighter scrim for active playback overlay prompts to maintain video visibility.
    val scrimAlpha = if (isOverlay) 0.15f else 0.5f

    // In-compose slide animation ensures smooth rendering over translucent playback windows.
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
            // Gradient scrim darkens the area behind the panel while leaving the video un-obscured on the left.
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
            initialCategoryIndex = initialCategoryIndex,
            onCategoryIndexChange = onCategoryIndexChange,
            modifier = Modifier
                .offset { IntOffset(x = (panelOffsetFraction * PANEL_WIDTH.roundToPx()).toInt(), y = 0) }
                .fillMaxHeight()
                .width(PANEL_WIDTH)
                .clip(PANEL_SHAPE)
                .background(MaterialTheme.colorScheme.surface),
        )
    }
}

/** Two-pane dialog panel layout displaying categories on the left and options on the right. */
@Composable
fun AppDialogPanelContent(
    title: String?,
    categories: List<OptionCategory>,
    showBackButton: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // Hoisted category index to preserve scroll position when popping back from nested dialog frames.
    initialCategoryIndex: Int = 0,
    onCategoryIndexChange: (Int) -> Unit = {},
) {
    // Uses weight(1f) to prevent list content from overflowing past the fixed-height header.
    Column(modifier = modifier) {
        if (categories.size <= 1) {
            val category = categories.firstOrNull()
            // Falls back to option title if the single-option category lacks its own header title.
            DialogHeader(
                title = title ?: category?.let { categoryDisplayTitle(it) },
                showBackButton = showBackButton,
                onBack = onBack,
            )
            if (category != null) {
                OptionList(category = category, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            var selectedIndex by remember(categories) { mutableStateOf(initialCategoryIndex.coerceIn(categories.indices)) }
            var lastFocusedCategory by remember(categories) { mutableStateOf(initialCategoryIndex.coerceIn(categories.indices)) }
            val categoryRequesters = remember(categories) { categories.indices.map { FocusRequester() } }

            DialogHeader(title = title, showBackButton = showBackButton, onBack = onBack)

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Non-lazy column ensures category items remain attached for deterministic focus restoration.
                Column(
                    modifier = Modifier
                        .width(LEFT_PANEL_WIDTH)
                        .fillMaxHeight()
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
                                // Reports category selection to preserve navigation state on dialog frame transitions.
                                onCategoryIndexChange(index)
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

/** Extracts display title from category or falls back to its first contained option title. */
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

@Composable
private fun BackButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.15f else 1f, MotionTokens.FocusScaleSpring, label = "backScale")
    val tint by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        tween(MotionTokens.FOCUS_ANIM_MS),
        label = "backTint",
    )

    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        tint = tint,
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    )
}

/** Category row displaying title with accent bar indicator on focus/selection. */
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
        tween(MotionTokens.FOCUS_ANIM_MS),
        label = "categoryText",
    )
    val barColor by animateColorAsState(
        when {
            isFocused -> MaterialTheme.colorScheme.primary
            isSelected -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> Color.Transparent
        },
        tween(MotionTokens.FOCUS_ANIM_MS),
        label = "categoryBar",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { state -> if (state.isFocused) onSelected() }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelected,
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
            val isSingleSelect = category.type == OptionCategory.TYPE_RADIO_LIST || category.type == OptionCategory.TYPE_STRING_LIST
            val isButton = category.type == OptionCategory.TYPE_SINGLE_BUTTON
            val options = category.options.orEmpty()

            // Index, not OptionItem#getId() - that defaults to 0 for almost every real item, so
            // using it as a key/selection index collides constantly.
            var selectedIndex by remember(category) {
                mutableStateOf(options.indexOfFirst { it.isSelected }.let { if (it == -1) null else it })
            }
            val checkedState = remember(category) {
                mutableStateListOf<Boolean>().apply { options.forEach { add(it.isSelected) } }
            }

            // Focus requester targeting the active option upon entering the right options panel.
            var lastFocusedOption by remember(category) {
                mutableStateOf(options.indexOfFirst { it.isSelected }.coerceAtLeast(0))
            }
            val entryRequester = remember(category) { FocusRequester() }

            LazyColumn(
                modifier = modifier
                    .focusProperties {
                        onEnter = {
                            // Only attached while that row is composed; falling through to default
                            // entry on failure is fine, which is why this must not throw.
                            try {
                                entryRequester.requestFocus()
                            } catch (e: IllegalStateException) { }
                        }
                    }
                    .focusGroup(),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 20.dp),
            ) {
                // Title-based key allows Compose to maintain item identities when options reorder.
                itemsIndexed(options, key = { index, item -> item.title?.toString() ?: "option_${item.id}_$index" }) { index, item ->
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

/** Option row supporting checkable radio/checkbox indicators and action clicks. */
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
        tween(MotionTokens.FOCUS_ANIM_MS),
        label = "optionContent",
    )
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f, MotionTokens.FocusScaleSpring, label = "optionScale")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
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
