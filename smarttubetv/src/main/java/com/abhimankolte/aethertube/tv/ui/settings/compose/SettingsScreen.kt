@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.abhimankolte.aethertube.tv.ui.settings.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import com.abhimankolte.aethertube.tv.ui.dialog.compose.AppDialogPanelContent
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory

private val LEFT_PANEL_WIDTH = 380.dp
private const val FOCUS_ANIM_MS = 180

/**
 * A permanent, always-visible two-pane settings screen - left = every top-level settings category,
 * right = whichever one is selected - matching the classic Android/Android TV Settings app layout
 * (see the reference screenshots this was built from) instead of a flyout/overlay over other content.
 * Both panes are opaque and full-height; nothing here animates in over anything else.
 */
@Composable
fun SettingsScreen(
    items: List<SettingsItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    detailTitle: String?,
    detailCategories: List<OptionCategory>?,
    showDetailBackButton: Boolean,
    onDetailBack: () -> Unit,
) {
    // Nothing here requested initial system focus, so on cold entry the screen showed a category
    // visually "selected" but nothing was actually D-pad-focused - the first press did nothing until
    // the user clicked it manually. Grab focus onto the selected row explicitly once it's known
    // (selectedIndex starts at -1 until the fragment's onResume picks a default category).
    val focusRequesters = remember(items) { items.indices.map { FocusRequester() } }
    // One-shot. This used to re-run on every selectedIndex change, and because each row selects
    // itself on focus, arrowing down the rail fired a programmatic focus request that raced the
    // D-pad movement already in flight - focus would land on whichever won, which is what made
    // Settings jump to an unrelated row.
    var didInitialFocus by rememberSaveable { mutableStateOf(false) }
    // The row focus should return to when focus re-enters this rail. Tracked explicitly rather than
    // left to focusRestorer, which restores by matching an attached child node and gives up silently
    // when it cannot - falling back to spatial search, i.e. an arbitrary row. Landing on a different
    // row than you left is not just cosmetic here: rows select on focus, so it switched the whole
    // settings category out from under you.
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
        // A plain Column, not a LazyColumn. focusRestorer restores by matching an *attached* child
        // node (see FocusRestorer.restoreFocusedChild), and a lazy layout does not keep off-screen
        // children attached - so restoration silently failed and fell back to spatial search, which
        // is what made returning to this rail land on an arbitrary row. There are only ever a dozen
        // or so settings categories, far too few to need virtualisation, and keeping them all
        // composed makes restoration deterministic. Same reasoning as the Home tab strip.
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

@OptIn(ExperimentalFoundationApi::class)
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
        tween(FOCUS_ANIM_MS),
        label = "settingsCategoryBackground",
    )
    val contentColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        tween(FOCUS_ANIM_MS),
        label = "settingsCategoryContent",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            // Matches every other list in the app (TopNav tabs, the dialog category rail): moving
            // focus onto a category previews it immediately, click is just a redundant secondary
            // trigger for touch/pointer input - not the only way in, like it was before.
            .onFocusChanged { state ->
                if (state.isFocused) {
                    onFocused()
                    onSelected()
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelected,
                onLongClick = {},
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
