@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.abhimankolte.aethertube.tv.ui.settings.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.abhimankolte.aethertube.tv.ui.common.compose.MotionTokens
import com.abhimankolte.aethertube.tv.ui.dialog.compose.AppDialogPanelContent
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory

private val LEFT_PANEL_WIDTH = 380.dp

/** Two-pane settings screen displaying top-level categories on the left and options on the right. */
@Composable
fun SettingsScreen(
    items: List<SettingsItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    detailTitle: String?,
    detailCategories: List<OptionCategory>?,
    showDetailBackButton: Boolean,
    onDetailBack: () -> Unit,
    detailCategoryIndex: Int = 0,
    onDetailCategoryIndexChange: (Int) -> Unit = {},
) {
    // Focus requesters for category rail navigation.
    val focusRequesters = remember(items) { items.indices.map { FocusRequester() } }
    // Must stay one-shot: re-running on every selectedIndex change (each row selects itself on
    // focus) raced a programmatic focus request against D-pad movement already in flight, landing
    // focus on whichever won - an arbitrary row.
    var didInitialFocus by rememberSaveable { mutableStateOf(false) }
    // Tracked explicitly rather than left to focusRestorer, which restores by matching an attached
    // child and silently falls back to spatial search when it can't - landing on a different row
    // than you left, which switches the whole settings category out from under you.
    var lastFocusedRow by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(selectedIndex, didInitialFocus) {
        if (!didInitialFocus && selectedIndex >= 0) {
            focusRequesters.getOrNull(selectedIndex)?.let { requestFocusSafely(it) }
            didInitialFocus = true
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Non-lazy scrollable column keeps all categories composed for deterministic focus restoration.
        Column(
            modifier = Modifier
                .width(LEFT_PANEL_WIDTH)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .focusProperties {
                    onEnter = {
                        focusRequesters.getOrNull(lastFocusedRow)?.let { requestFocusSafely(it) }
                    }
                }
                .focusGroup()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 20.dp),
        ) {
            items.forEachIndexed { index, item ->
                SettingsCategoryRow(
                    item = item,
                    isSelected = index == selectedIndex,
                    onSelected = { onItemSelected(index) },
                    onFocused = { lastFocusedRow = index },
                    focusRequester = focusRequesters.getOrNull(index),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(MaterialTheme.colorScheme.border.copy(alpha = 0.4f)),
        )

        Box(modifier = Modifier.weight(1f).fillMaxHeight().focusGroup()) {
            if (detailCategories != null) {
                AppDialogPanelContent(
                    title = detailTitle,
                    categories = detailCategories,
                    showBackButton = showDetailBackButton,
                    onBack = onDetailBack,
                    modifier = Modifier.fillMaxSize(),
                    initialCategoryIndex = detailCategoryIndex,
                    onCategoryIndexChange = onDetailCategoryIndexChange,
                )
            }
        }
    }
}

/** Focus requester with non-crashing guard for uncomposed layout targets. */
private fun requestFocusSafely(focusRequester: FocusRequester) {
    try {
        focusRequester.requestFocus()
    } catch (e: IllegalStateException) {
        // Target not attached to composition hierarchy yet.
    }
}

@Composable
private fun SettingsCategoryRow(
    item: SettingsItem,
    isSelected: Boolean,
    onSelected: () -> Unit,
    onFocused: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val backgroundColor by animateColorAsState(
        when {
            isFocused -> MaterialTheme.colorScheme.primary
            isSelected -> MaterialTheme.colorScheme.surfaceVariant
            else -> androidx.compose.ui.graphics.Color.Transparent
        },
        tween(MotionTokens.FOCUS_ANIM_MS),
        label = "settingsCategoryBackground",
    )
    val contentColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        tween(MotionTokens.FOCUS_ANIM_MS),
        label = "settingsCategoryContent",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            // Focus previews category immediately; click provides deliberate touch/pointer selection.
            .onFocusChanged { state ->
                if (state.isFocused) {
                    onFocused()
                    onSelected()
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelected,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (item.imageResId > 0) {
            Icon(
                painter = painterResource(id = item.imageResId),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }

        Text(
            text = item.title,
            color = contentColor,
            fontSize = 16.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}
