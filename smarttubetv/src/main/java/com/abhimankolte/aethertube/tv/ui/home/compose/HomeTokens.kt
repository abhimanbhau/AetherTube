package com.abhimankolte.aethertube.tv.ui.home.compose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val FEATURED_CARD_WIDTH = 360.dp
internal val FEATURED_CARD_HEIGHT = 202.dp
internal val CARD_WIDTH = 240.dp
internal val CARD_HEIGHT = 135.dp
internal val CARD_SHAPE = RoundedCornerShape(16.dp)
internal val TOP_NAV_HEIGHT = 84.dp

// Pinned to guarantee exact two-line height across varying font engines.
internal val CARD_TITLE_SIZE = 13.sp
internal val CARD_TITLE_LINE_HEIGHT = 16.sp
internal val CARD_TITLE_HEIGHT = 38.dp
internal val FEATURED_TITLE_SIZE = 16.sp
internal val FEATURED_TITLE_LINE_HEIGHT = 20.sp
internal val FEATURED_TITLE_HEIGHT = 46.dp

/** Focus highlight padding for top nav icons. */
internal val NAV_ICON_PADDING = 10.dp

// Viewport-proximity threshold to prefetch the next page before reaching scroll boundary.
internal const val GRID_SCROLL_CONTINUE_NUM = 10
internal const val ROW_SCROLL_CONTINUE_NUM = 4

/** Focus requester with non-crashing guard for uncomposed layout targets. */
internal fun requestFocusSafely(focusRequester: FocusRequester) {
    try {
        focusRequester.requestFocus()
    } catch (e: IllegalStateException) {
        // Target not attached to composition hierarchy yet.
    }
}
