package com.abhimankolte.aethertube.tv.ui.shorts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liskovsoft.mediaserviceinterfaces.ContentService
import com.liskovsoft.sharedutils.mylogger.Log
import com.liskovsoft.sharedutils.rx.RxHelper
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import io.reactivex.disposables.Disposable

/**
 * The ordered list of shorts behind the vertical feed, and the cursor into it.
 *
 * Seeded from the group the user clicked in - so entering the feed on the twelfth short starts on the
 * twelfth short with the preceding eleven still reachable upwards - and extended by continuing that
 * same [VideoGroup] as the cursor nears the end.
 *
 * Continuation deliberately mutates the original group rather than building a detached list.
 * [VideoGroup.from] appends in place and re-parents each new [Video] onto it, which keeps
 * `video.group` valid; upstream reads that in several places that matter here, most importantly
 * `VideoLoaderController.getPlaybackMode()`, which only honours the loop-shorts preference for a video
 * that still reports `belongsToShortsGroup()`.
 */
class ShortsFeed(seed: Video) {
    companion object {
        private const val TAG = "ShortsFeed"

        /**
         * Start fetching this many items from the end. Shorts pages are small and a viewer moves
         * through them one press at a time, so there is no need for the wider margin the grids use.
         */
        private const val PREFETCH_MARGIN = 5
    }

    /** Backing list for the UI. Read directly, never captured in a `remember(key)`. */
    val videos = mutableStateListOf<Video>()

    var currentIndex by mutableIntStateOf(0)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    private val contentService: ContentService = YouTubeServiceManager.instance().contentService
    private val group: VideoGroup? = seed.group
    private val seenIds = mutableSetOf<String>()
    private var continuation: Disposable? = null

    /** No further pages: either the group is exhausted or it never supported continuation. */
    private var exhausted = false

    init {
        val seeded = group?.videos?.filter { it.hasVideo() } ?: emptyList()

        if (seeded.isEmpty()) {
            // Opened from somewhere without a group behind it (a deep link, a single pinned short).
            // Still a valid one-item feed; it just cannot be extended.
            addAll(listOf(seed))
            exhausted = true
        } else {
            addAll(seeded)
            currentIndex = videos.indexOfFirst { it.videoId == seed.videoId }.coerceAtLeast(0)
        }
    }

    val current: Video?
        get() = videos.getOrNull(currentIndex)

    fun hasNext(): Boolean = currentIndex < videos.lastIndex

    fun hasPrevious(): Boolean = currentIndex > 0

    /** Moves the cursor and returns the newly current video, or null if the edge was reached. */
    fun moveNext(): Video? {
        if (!hasNext()) {
            loadMoreIfNeeded()
            return null
        }

        currentIndex++
        loadMoreIfNeeded()
        return current
    }

    fun movePrevious(): Video? {
        if (!hasPrevious()) {
            return null
        }

        currentIndex--
        return current
    }

    /**
     * Follows a video change the feed did not initiate - upstream's controllers auto-advancing at the
     * end of a video, say. If it is something already in the list the cursor just moves; anything else
     * gets appended so the feed stays a truthful record of what was played.
     */
    fun syncTo(video: Video?) {
        if (video?.videoId == null || video.videoId == current?.videoId) {
            return
        }

        val existing = videos.indexOfFirst { it.videoId == video.videoId }

        if (existing >= 0) {
            currentIndex = existing
        } else {
            addAll(listOf(video))
            currentIndex = videos.lastIndex
        }

        loadMoreIfNeeded()
    }

    fun loadMoreIfNeeded() {
        if (exhausted || isLoadingMore || group == null) {
            return
        }

        if (currentIndex < videos.size - PREFETCH_MARGIN) {
            return
        }

        val mediaGroup = group.mediaGroup

        if (mediaGroup == null) {
            exhausted = true
            return
        }

        isLoadingMore = true

        continuation = contentService.continueGroupObserve(mediaGroup)
            .subscribe(
                { continued ->
                    isLoadingMore = false

                    val before = group.videos.size
                    // Appends onto `group` in place and re-parents the new videos onto it.
                    VideoGroup.from(group, continued)
                    val appended = group.videos.drop(before).filter { it.hasVideo() }

                    if (appended.isEmpty()) {
                        exhausted = true
                    } else {
                        addAll(appended)
                    }
                },
                { error ->
                    isLoadingMore = false
                    // A failed page is not fatal - the user keeps what is already loaded. Don't retry
                    // automatically or a dead group turns into a request loop on every press.
                    exhausted = true
                    Log.e(TAG, "Could not continue shorts group: %s", error.message)
                }
            )
    }

    fun release() {
        RxHelper.disposeActions(continuation)
        continuation = null
    }

    /**
     * Duplicate ids are a fact of life in these feeds - the same short legitimately appears in more
     * than one page - and they are worth filtering here for their own sake, not only because Compose's
     * lazy layouts treat a repeated key as fatal.
     */
    private fun addAll(items: List<Video>) {
        for (item in items) {
            val id = item.videoId ?: continue

            if (seenIds.add(id)) {
                videos.add(item)
            }
        }
    }
}
