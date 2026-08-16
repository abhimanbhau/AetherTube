package com.abhimankolte.aethertube.tv.ui.home.compose

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.abhimankolte.aethertube.tv.ui.common.compose.AetherTubeTheme
import com.abhimankolte.aethertube.tv.ui.settings.compose.ComposeSettingsActivity
import com.abhimankolte.aethertube.tv.ui.shorts.ShortsPlayerActivity
import com.liskovsoft.mediaserviceinterfaces.oauth.Account
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager

/**
 * Compose-for-TV replacement for the app's leanback [com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseFragment]
 * (the launcher/root Home screen). Covers every [BrowseSection] type:
 *  - TYPE_ROW (Home, Trending, Kids Home, Sports, Live, News, Music, Playlists): horizontal shelves.
 *  - TYPE_GRID / TYPE_SHORTS_GRID (Subscriptions, History, My Videos, Blocked Channels, Notifications,
 *    Playback Queue): a single flat wrapping grid.
 *  - TYPE_MULTI_GRID (Channels) is downgraded to TYPE_GRID on arrival (see [addSection]) and rendered
 *    as a plain grid of channels; clicking one opens that channel's page.
 *  - TYPE_SETTINGS_GRID (Settings) isn't rendered here at all - it's excluded from the tab strip and
 *    opens its own dedicated screen (see [com.abhimankolte.aethertube.tv.ui.settings.compose.ComposeSettingsActivity])
 *    via the gear icon, like Search's magnifier icon.
 */
class ComposeHomeFragment :
    Fragment(),
    BrowseView {
    private lateinit var browsePresenter: BrowsePresenter
    private var isFragmentCreated = false

    private val sections = mutableStateListOf<BrowseSection>()
    private var selectedSectionIndex by mutableStateOf(0)
    private var currentSectionType by mutableStateOf(BrowseSection.TYPE_ROW)
    private var showProgress by mutableStateOf(false)
    private var focusedBackdropUrl by mutableStateOf<String?>(null)

    // Backs the TopNav avatar button (see onAccountClick below). SignInService.addOnAccountChange
    // has no matching remove method, so this listener - and the closure capturing this fragment -
    // outlives any one fragment instance; harmless here since it only ever writes into a
    // mutableStateOf, and account switches are a rare, user-driven event, not a hot path.
    private var currentAccount by mutableStateOf<Account?>(null)
    private val homeRows = mutableStateListOf<HomeRow>()
    private val rowsById = LinkedHashMap<Int, HomeRow>()

    // Every Compose Lazy layout keys its items (by videoId, falling back to channelId/hashCode) and
    // crashes outright (IllegalArgumentException: Key "..." was already used) on a repeat - unlike
    // leanback's RecyclerView adapters, which tolerated a duplicate item silently. A retried/duplicated
    // continuation (e.g. after a section gets disabled mid-load and the presenter's retry loop keeps
    // re-delivering overlapping results) can otherwise hand back the same video twice across two
    // different HomeRow objects, which neither row's own list can catch on its own. Track every identity
    // already present in this section/pane and drop repeats before they ever reach a row.
    private val seenVideoIds = HashSet<String>()

    private val settingsItems = mutableStateListOf<SettingsItem>()
    private var errorData by mutableStateOf<ErrorFragmentData?>(null)

    // BrowsePresenter.onViewInitialized() calls BrowseView.selectSection() more than once for the
    // same boot section (once from refreshSections(), once again right after) - real leanback headers
    // only fire onSectionFocused once, debounced by Android's async focus system settling. Naively
    // forwarding every selectSection call into onSectionFocused() disposes the still-in-flight RxJava
    // subscription from the first call before it can ever deliver rows, leaving Home permanently blank.
    // Compose state: the tab strip reads this to decide which tab is active (see TopNav), so a
    // plain var would leave the highlight and the D-pad up-target stale after a section change.
    private var lastFocusedSectionId by mutableStateOf(-1)

    // Focus restoration: remember the last-focused video per section so moving down from the top nav
    // into a tab you've already visited lands back where you left off, instead of always defaulting
    // to the first card. Session-only (not persisted), separate from BrowseView#selectSectionItem's
    // cold-boot restore, which stays a no-op here.
    private val sectionFocusMemory = HashMap<Int, String>()
    private var pendingFocusVideoId by mutableStateOf<String?>(null)

    // Real leanback keeps a separate retained fragment per header, so revisiting one is instant.
    // We're one fragment for every section, so cache each section's already-loaded content ourselves -
    // otherwise every tab switch clears and re-fetches from the network, even for a tab you just left.
    private val sectionCache = HashMap<Int, SectionSnapshot>()
    private lateinit var diskCache: SectionDiskCache

    // BrowsePresenter announces every load by sending ACTION_REPLACE *before* any data arrives.
    // Clearing on receipt blanks the screen for the whole round-trip, and would instantly wipe
    // anything seeded from disk. Instead the clear is deferred until the first real data lands - or
    // until the load settles empty-handed, which is what stops a section that genuinely went empty
    // from showing stale rows forever.
    private var pendingReplace = false

    // Fast tab-flicking disposes each section's in-flight RxJava load before it ever delivers data
    // (see the onSectionFocused dedup note above - same underlying disposeActions() mechanism). Only
    // cache a section once its load has actually settled (showProgressBar(false) fired for it); otherwise
    // leaving mid-load would cache a spuriously-empty snapshot and that tab would appear dead forever.
    private var isCurrentSectionSettled = false

    private class SectionSnapshot(
        val rows: List<HomeRow>,
        val settingsItems: List<SettingsItem>,
        val errorData: ErrorFragmentData?,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null) // Real restore takes place in the presenter

        isFragmentCreated = true
        diskCache = SectionDiskCache(requireContext())
        browsePresenter = BrowsePresenter.instance(requireContext())
        browsePresenter.setView(this)

        val signInService = YouTubeServiceManager.instance().signInService
        currentAccount = signInService.selectedAccount
        signInService.addOnAccountChange { account -> currentAccount = account }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = ComposeView(requireContext()).apply {
        setContent {
            AetherTubeTheme {
                HomeScreen(
                    sections = sections,
                    selectedSectionId = lastFocusedSectionId,
                    sectionType = currentSectionType,
                    onSectionSelected = ::onSectionSelected,
                    onSearchClick = { SearchPresenter.instance(requireContext()).startSearch(null) },
                    onSettingsClick = { startActivity(Intent(requireContext(), ComposeSettingsActivity::class.java)) },
                    accountAvatarUrl = currentAccount?.avatarImageUrl,
                    onAccountClick = { AccountSelectionPresenter.instance(requireContext()).show(true) },
                    showProgress = showProgress,
                    backdropUrl = focusedBackdropUrl,
                    rows = homeRows,
                    errorData = errorData,
                    onVideoClick = ::onVideoClicked,
                    onVideoFocus = { video ->
                        browsePresenter.onVideoItemSelected(video)
                        // Only the spacious shelf view gets the ambient backdrop-follows-focus
                        // treatment. A dense grid (My Videos, Subscriptions, History, etc.) has
                        // cards close enough together that even a debounced crossfade still fires
                        // constantly during normal browsing, reading as "the page refreshing".
                        if (currentSectionType == BrowseSection.TYPE_ROW) {
                            focusedBackdropUrl = video.cardImageUrl
                        }
                        video.videoId?.let { sectionFocusMemory[lastFocusedSectionId] = it }
                    },
                    onVideoLongClick = { video -> browsePresenter.onVideoItemLongClicked(video) },
                    onScrollEnd = { video -> browsePresenter.onScrollEnd(video) },
                    restoreFocusVideoId = pendingFocusVideoId,
                    onFocusRestored = { pendingFocusVideoId = null },
                )
            }
        }
    }

    /**
     * Shorts open the vertical feed; everything else goes through upstream's normal routing.
     *
     * Not done inside BrowsePresenter/VideoActionPresenter on purpose - that decision belongs to this
     * UI. The leanback UI has no vertical feed to send them to, and both UIs share those presenters.
     */
    private fun onVideoClicked(video: Video) {
        if (isShortsFeedCandidate(video)) {
            ShortsPlayerActivity.start(requireContext(), video)
            return
        }

        browsePresenter.onVideoItemClicked(video)
    }

    /**
     * `belongsToShortsGroup()` covers both places a scrollable run of shorts actually exists: the
     * Shorts section, and a Shorts shelf on Home. The section-type check is a fallback for the former,
     * because `Video.isShorts` is only populated where the API bothers to mark it - upstream notes the
     * same caveat in AutoFrameRateController.
     *
     * Anything else keeps the standard 16:9 player, including a portrait video that happens to turn up
     * in a normal row: without a feed of shorts around it there is nothing to scroll to.
     */
    private fun isShortsFeedCandidate(video: Video): Boolean {
        if (!video.hasVideo()) {
            return false
        }

        return video.belongsToShortsGroup() ||
            (currentSectionType == BrowseSection.TYPE_SHORTS_GRID && video.isShorts)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        browsePresenter.onViewInitialized()
    }

    override fun onDestroy() {
        super.onDestroy()
        browsePresenter.onViewDestroyed()
    }

    override fun onPause() {
        super.onPause()

        if (!isFragmentCreated) {
            browsePresenter.onViewPaused()
        }
    }

    override fun onResume() {
        super.onResume()

        if (!isFragmentCreated) {
            browsePresenter.onViewResumed()
            pruneDisabledSections()

            // Returning here (e.g. Back from playback) doesn't go through focusSection() - the tab
            // hasn't changed, so that's the only other place pendingFocusVideoId normally gets set.
            // Without this, focus would drop to whatever the D-pad's default anchor is instead of the
            // video card the user just came back from.
            pendingFocusVideoId = sectionFocusMemory[lastFocusedSectionId]
        }

        isFragmentCreated = false
    }

    /**
     * Hiding a section from General Settings only updates BrowsePresenter's own view (via
     * updateSections()/removeAllSections()+addSection()), and BrowsePresenter's view is whatever
     * screen last called setView() on it - while Settings is open that's ComposeSettingsFragment, not
     * this one, so the change never reached our `sections` list. The stale tab stayed visible, did
     * nothing on focus (its BrowseSection no longer has a valid data mapping), and crashed on click.
     * A full updateSections() round-trip here would also wipe sectionCache for every tab, not just the
     * hidden one - too heavy for something that runs on every single resume. Just re-check pinned
     * status directly against SidebarService (no network, no cache invalidation) and drop what's gone.
     */
    private fun pruneDisabledSections() {
        val sidebarService = SidebarService.instance(requireContext())
        val disabled = sections.filter { !sidebarService.isSectionPinned(it.id) }

        if (disabled.isEmpty()) {
            return
        }

        val wasViewingDisabledSection = disabled.any { it.id == lastFocusedSectionId }
        sections.removeAll(disabled)

        if (wasViewingDisabledSection) {
            sections.firstOrNull()?.let {
                selectedSectionIndex = 0
                focusSection(it)
            }
        } else {
            val newIndex = sections.indexOfFirst { it.id == lastFocusedSectionId }
            if (newIndex != -1) {
                selectedSectionIndex = newIndex
            }
        }
    }

    // ---- Compose-driven user interaction ----

    private fun onSectionSelected(index: Int) {
        val section = sections.getOrNull(index) ?: return
        selectedSectionIndex = index
        currentSectionType = section.type
        focusSection(section)
    }

    private fun focusSection(section: BrowseSection) {
        currentSectionType = section.type

        // Settings is its own dedicated screen (see onSettingsClick/ComposeSettingsActivity), not a
        // tab - if anything ever selects it as a section (e.g. a cold-boot restore to the last-viewed
        // tab), launch that screen instead of trying to render it inline here.
        if (section.type == BrowseSection.TYPE_SETTINGS_GRID) {
            startActivity(Intent(requireContext(), ComposeSettingsActivity::class.java))
            return
        }

        if (lastFocusedSectionId == section.id) {
            return
        }

        // Grid sections don't update the backdrop as focus moves (see onVideoFocus) - clear it here so
        // a grid doesn't inherit a frozen, unrelated backdrop image left over from the previous tab.
        if (section.type != BrowseSection.TYPE_ROW) {
            focusedBackdropUrl = null
        }

        // Snapshot what's currently loaded for the section we're leaving so coming back is instant -
        // but only if that load actually finished. Otherwise we'd cache an interrupted, empty result.
        if (lastFocusedSectionId != -1 && isCurrentSectionSettled) {
            sectionCache[lastFocusedSectionId] = SectionSnapshot(homeRows.toList(), settingsItems.toList(), errorData)
        }

        lastFocusedSectionId = section.id
        pendingFocusVideoId = sectionFocusMemory[section.id]

        // Pinned error/notification sections - the "Update" tab AppUpdatePresenter.pinUpdateSection()
        // adds, most visibly - carry their own ErrorFragmentData and have nothing to load. Upstream's
        // BrowsePresenter.updateSection() handles TYPE_ERROR by calling showProgressBar(false) and
        // nothing else, so it never pushes that data at the view: the tab appeared in the strip and
        // then rendered a completely empty page. Read it off the section directly instead.
        if (section.type == BrowseSection.TYPE_ERROR) {
            clearContent()
            errorData = section.data as? ErrorFragmentData
            isCurrentSectionSettled = true
            return
        }

        val cached = sectionCache[section.id]
        if (cached != null) {
            restoreContent(cached)
            isCurrentSectionSettled = true
            return
        }

        clearContent()

        // Paint last time's content immediately; the load already under way replaces it as soon as
        // it produces anything (see pendingReplace).
        diskCache.load(section.id)?.let { cached ->
            restoreContent(SectionSnapshot(cached, emptyList(), null))
        }

        isCurrentSectionSettled = false
        browsePresenter.onSectionFocused(section.id)
    }

    /** Performs a deferred ACTION_REPLACE, right before the data that supersedes the old rows. */
    private fun applyPendingReplace() {
        if (!pendingReplace) {
            return
        }
        pendingReplace = false
        rowsById.clear()
        homeRows.clear()
        seenVideoIds.clear()
    }

    private fun restoreContent(snapshot: SectionSnapshot) {
        rowsById.clear()
        homeRows.clear()
        homeRows.addAll(snapshot.rows)
        for (row in snapshot.rows) {
            rowsById[row.id] = row
        }

        settingsItems.clear()
        settingsItems.addAll(snapshot.settingsItems)
        errorData = snapshot.errorData
    }

    // ---- BrowseView ----

    override fun addSection(index: Int, section: BrowseSection) {
        // Channels arrives as TYPE_MULTI_GRID, upstream's two-pane "new look": a channel list on the
        // left driving an uploads grid on the right. We render it as an ordinary grid of channels
        // where clicking one opens that channel's page - the same thing upstream's "old look"
        // (isUploadsOldLookEnabled) does. Flipping the type here rather than reimplementing the
        // behaviour also switches off BrowsePresenter's multi-grid branching wholesale: no
        // position 0/1 split, no uploads auto-load, and onVideoItemClicked falls through to
        // VideoActionPresenter, which opens the channel page for a channel item.
        if (section.type == BrowseSection.TYPE_MULTI_GRID) {
            section.type = BrowseSection.TYPE_GRID
        }

        if (index in 0..sections.size) {
            sections.add(index, section)
        } else {
            sections.add(section)
        }
    }

    override fun removeSection(section: BrowseSection) {
        sections.remove(section)
    }

    override fun removeAllSections() {
        sections.clear()
        sectionCache.clear()
        lastFocusedSectionId = -1 // force the next selectSection to re-trigger content load
    }

    override fun selectSection(index: Int, focusOnContent: Boolean) {
        if (index < 0 || index >= sections.size) {
            return
        }

        selectedSectionIndex = index
        val section = sections[index]
        focusSection(section)
    }

    override fun updateSection(group: VideoGroup) {
        val targetRowsById = rowsById
        val targetRows = homeRows
        val targetSeenIds = seenVideoIds

        when (group.action) {
            VideoGroup.ACTION_REPLACE -> pendingReplace = true
            VideoGroup.ACTION_SYNC -> {
                syncRow(group, targetRowsById)
                return
            }
        }

        if (group.isEmpty) {
            return
        }

        errorData = null

        applyPendingReplace()

        val newVideos = group.videos.filter { video ->
            val id = video.videoId ?: video.channelId
            id == null || targetSeenIds.add(id)
        }

        if (newVideos.isEmpty()) {
            return
        }

        val existing = targetRowsById[group.id]

        if (existing == null) {
            val row = HomeRow(group.id, group.title.orEmpty())
            row.videos.addAll(newVideos)
            targetRowsById[group.id] = row
            targetRows.add(row)
        } else {
            existing.videos.addAll(newVideos)
        }
    }

    override fun updateSection(group: SettingsGroup) {
        // Unlike VideoGroup, settings are always delivered as one full replace - no incremental append.
        errorData = null
        settingsItems.clear()
        group.items?.let { settingsItems.addAll(it) }
    }

    override fun clearSection(section: BrowseSection) {
        pendingReplace = false
        diskCache.clear(section.id)
        clearRows()
        sectionCache.remove(section.id)
    }

    override fun selectSectionItem(index: Int) {
        // No-op: Compose rows don't track a persisted focus index yet.
    }

    override fun selectSectionItem(item: Video?) {
        // No-op: Compose rows don't track a persisted focus index yet.
    }

    override fun showError(data: ErrorFragmentData) {
        // e.g. SignInError for auth-only sections (Home when signed out, History, etc.) - the presenter
        // calls this instead of updateSection() when the section can't load without further action.
        errorData = data
        isCurrentSectionSettled = true
    }

    override fun showProgressBar(show: Boolean) {
        showProgress = show

        if (!show) {
            isCurrentSectionSettled = true
            // The load finished without ever sending data, so the pending clear has to happen now -
            // otherwise a section that legitimately went empty would keep showing seeded rows.
            applyPendingReplace()

            if (lastFocusedSectionId != -1 && homeRows.isNotEmpty()) {
                diskCache.save(lastFocusedSectionId, homeRows)
            }
        }
    }

    override fun isProgressBarShowing(): Boolean = showProgress

    override fun focusOnContent() {
        // No dedicated content FocusRequester yet; sections/rows manage their own focus.
    }

    override fun isEmpty(): Boolean = homeRows.isEmpty() && settingsItems.isEmpty()

    override fun updateBadge() {
        // No badge UI in the Compose header yet.
    }

    // ---- internals ----

    private fun clearRows() {
        rowsById.clear()
        homeRows.clear()
    }

    private fun clearContent() {
        pendingReplace = false
        clearRows()
        settingsItems.clear()
        errorData = null
    }

    private fun syncRow(group: VideoGroup, rowsById: LinkedHashMap<Int, HomeRow>) {
        val row = rowsById[group.id] ?: return

        for (updated in group.videos) {
            val index = row.videos.indexOfFirst { it.videoId == updated.videoId }
            if (index != -1) {
                row.videos[index] = updated
            }
        }
    }
}
