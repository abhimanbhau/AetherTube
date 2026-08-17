@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi::class,
)

package com.abhimankolte.aethertube.tv.ui.home.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.abhimankolte.aethertube.tv.ui.common.compose.MotionTokens
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection

@Composable
internal fun TopNav(
    sections: List<BrowseSection>,
    selectedSectionId: Int,
    onSectionSelected: (Int) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    accountAvatarUrl: String?,
    onAccountClick: () -> Unit,
    showProgress: Boolean,
    activeTabFocusRequester: FocusRequester,
    navRowFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    // Non-lazy Row keeps all tab items permanently composed for focusRestorer targets.
    val tabScrollState = rememberScrollState()
    val activeTabBringIntoViewRequester = remember { BringIntoViewRequester() }

    // Not remembered by identity to allow SnapshotStateList in-place mutation updates.
    val visibleSections = sections.filter { it.type != BrowseSection.TYPE_SETTINGS_GRID }

    // Fallback tab target ensuring the focusRequester is always attached to a valid node.
    val activeTabId = remember(visibleSections, selectedSectionId) {
        if (visibleSections.any { it.id == selectedSectionId }) selectedSectionId else visibleSections.firstOrNull()?.id ?: -1
    }

    LaunchedEffect(activeTabId, visibleSections) {
        activeTabBringIntoViewRequester.bringIntoView()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TOP_NAV_HEIGHT)
            // Restores focus to the last-focused tab when returning from lower screen content.
            .focusRequester(navRowFocusRequester)
            .focusRestorer(activeTabFocusRequester)
            // Content padding inside scroll avoids clipping the scaled focus highlight of the first item.
            .horizontalScroll(tabScrollState)
            .padding(horizontal = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        NavIconButton(Icons.Filled.Settings, "Settings", onSettingsClick, isFirstIcon = true)
        NavIconButton(Icons.Filled.Search, "Search", onSearchClick)
        AccountAvatarButton(avatarUrl = accountAvatarUrl, onClick = onAccountClick)

        // Excludes settings from tab strip as it opens a dedicated screen.
        for ((tabIndex, section) in visibleSections.withIndex()) {
            val index = sections.indexOf(section)
            val isSelected = section.id == activeTabId

            SectionTab(
                title = section.title.orEmpty(),
                isSelected = isSelected,
                onSelected = { onSectionSelected(index) },
                // Attaches active tab target for focus restoration from content below.
                focusRequester = if (isSelected) activeTabFocusRequester else null,
                bringIntoViewRequester = if (isSelected) activeTabBringIntoViewRequester else null,
                // Prevents D-pad Right from wrapping around from the last tab.
                isLastTab = tabIndex == visibleSections.lastIndex,
            )
        }

        if (showProgress) {
            Text(text = "...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Top nav icon button with accent highlight on focus. */
@Composable
private fun NavIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isFirstIcon: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.12f else 1f, MotionTokens.FocusScaleSpring, label = "navIconScale")
    val background by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        tween(MotionTokens.FOCUS_ANIM_MS),
        label = "navIconBackground",
    )
    val tint by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        tween(MotionTokens.FOCUS_ANIM_MS),
        label = "navIconTint",
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(CircleShape)
            .background(background)
            // Prevents D-pad Left from escaping or wrapping around from the first item.
            .then(if (isFirstIcon) Modifier.focusProperties { left = FocusRequester.Cancel } else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(NAV_ICON_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
    }
}

/** Account avatar button opening the quick-switcher dialog. */
@Composable
private fun AccountAvatarButton(avatarUrl: String?, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.12f else 1f, MotionTokens.FocusScaleSpring, label = "accountAvatarScale")
    val background by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        tween(MotionTokens.FOCUS_ANIM_MS),
        label = "accountAvatarBackground",
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(NAV_ICON_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            GlideImage(
                model = avatarUrl,
                contentDescription = "Account",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(24.dp).clip(CircleShape),
            ) { it.diskCacheStrategy(DiskCacheStrategy.ALL) }
        } else {
            val tint by animateColorAsState(
                if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                tween(MotionTokens.FOCUS_ANIM_MS),
                label = "accountAvatarTint",
            )
            Icon(imageVector = Icons.Filled.AccountCircle, contentDescription = "Account", tint = tint)
        }
    }
}

/**
 * Switches content as soon as D-pad focus lands on a tab, not only on click - onSelected fires from
 * onFocusChanged; the clickable's onClick is a deliberate second trigger for touch/pointer input,
 * not dead code to be deduplicated away.
 */
@Composable
private fun SectionTab(
    title: String,
    isSelected: Boolean,
    onSelected: () -> Unit,
    focusRequester: FocusRequester? = null,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    isLastTab: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val textColor by animateColorAsState(
        when {
            isFocused -> MaterialTheme.colorScheme.onBackground
            isSelected -> MaterialTheme.colorScheme.onBackground
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        tween(MotionTokens.FOCUS_ANIM_MS),
        label = "tabText",
    )
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, MotionTokens.FocusScaleSpring, label = "tabScale")

    Column(
        modifier = Modifier
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (bringIntoViewRequester != null) Modifier.bringIntoViewRequester(bringIntoViewRequester) else Modifier)
            .then(
                if (isLastTab) {
                    // Prevents D-pad Right from wrapping around from the last tab.
                    Modifier.focusProperties { right = FocusRequester.Cancel }
                } else {
                    Modifier
                },
            )
            .onFocusChanged { state ->
                if (state.isFocused) {
                    onSelected()
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelected,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 18.sp,
            lineHeight = 18.sp, // Pinned line height prevents layout jitter between Bold and Normal states.
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
        )

        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(width = if (isSelected) 20.dp else 0.dp, height = 3.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
