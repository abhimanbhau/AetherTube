package com.abhimankolte.aethertube.tv.ui.settings.compose

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
import com.abhimankolte.aethertube.tv.ui.common.compose.AetherTubeTheme
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager

/**
 * A dedicated, permanent two-pane settings screen (left = categories, right = the selected
 * category's options) - a real separate screen reached like Search is, not an overlay/flyout
 * grafted onto Home. See [SettingsScreen] for the actual layout.
 *
 * Implements [AppDialogView] directly on the Fragment (like [com.abhimankolte.aethertube.tv.ui.dialog.compose.ComposeAppDialogFragment])
 * so [AppDialogPresenter]'s Fragment/Activity-only view check accepts it - a plain object doesn't
 * pass that check and setView() silently becomes a no-op. Installed with setSkipViewLaunch(true)
 * while a category is open, so its nested sub-dialogs (e.g. screen dimming, per-category pickers)
 * land in the right pane here too instead of popping the app-wide dialog Activity on top of us.
 */
class ComposeSettingsFragment : Fragment(), AppDialogView {
    private val items = mutableStateListOf<SettingsItem>()
    private var selectedIndex by mutableStateOf(-1)
    private val detailFrames = mutableStateListOf<DetailFrame>()
    private var detailViewId = 0
    private var isPausedFlag = false

    private class DetailFrame(val categories: List<OptionCategory>, val title: CharSequence?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        items.addAll(AppDataSourceManager.instance().getSettingItems(requireContext()))
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AetherTubeTheme {
                    val frame = detailFrames.lastOrNull()
                    SettingsScreen(
                        items = items,
                        selectedIndex = selectedIndex,
                        onItemSelected = ::selectItem,
                        detailTitle = frame?.title?.toString(),
                        detailCategories = frame?.categories,
                        showDetailBackButton = detailFrames.size > 1,
                        onDetailBack = { goBack() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isPausedFlag = false

        if (selectedIndex == -1 && items.isNotEmpty()) {
            selectItem(0)
        }
    }

    override fun onPause() {
        super.onPause()
        isPausedFlag = true

        // Leaving this screen (Home button, task switch, etc.) without necessarily destroying it -
        // clear the bypass immediately so any dialog opened elsewhere in the meantime still gets the
        // real Activity. selectItem() turns it back on the moment a category is picked again here.
        //
        // Unconditional, NOT gated on `presenter.view === this`: AppDialogPresenter is a singleton
        // shared by every dialog caller in the app (HQ/subtitles/speed during playback, context menus,
        // confirmations...), and setSkipViewLaunch(true) is a bare boolean with no per-view scoping -
        // there is no case where leaving Settings while it's true should leave it true. The previous
        // identity check was fragile (e.g. Activity/Fragment teardown ordering during a fast exit could
        // make the check fail) and left it stuck true, which silently no-ops every dialog launch
        // app-wide afterwards - HQ/comments/subtitles buttons would visibly do nothing.
        AppDialogPresenter.instance(requireContext()).setSkipViewLaunch(false)
    }

    override fun onDestroy() {
        super.onDestroy()

        val presenter = AppDialogPresenter.instance(requireContext())
        if (presenter.view === this) {
            presenter.onViewDestroyed()
        }
    }

    private fun selectItem(index: Int) {
        val item = items.getOrNull(index) ?: return

        // SettingsCategoryRow now previews on focus alone (not just click), so refocusing the row
        // that's already selected - e.g. D-pad left back out of a category's detail pane - would
        // otherwise re-run item.onClick and reset that category's own back-stack every time.
        if (index == selectedIndex) {
            return
        }

        selectedIndex = index
        detailFrames.clear()

        val presenter = AppDialogPresenter.instance(requireContext())
        presenter.setView(this)
        presenter.setSkipViewLaunch(true)
        item.onClick.run()
    }

    fun canGoBackInDetail(): Boolean = detailFrames.size > 1

    // ---- AppDialogView ----

    override fun show(
        categories: MutableList<OptionCategory>?,
        title: CharSequence?,
        isExpandable: Boolean,
        isTransparent: Boolean,
        isOverlay: Boolean,
        id: Int
    ) {
        if (categories == null) {
            return
        }

        detailViewId = id
        detailFrames.add(DetailFrame(categories, title))
    }

    override fun finish() {
        // A "close this dialog" from deep within a category (e.g. a confirmation action) should just
        // drop back to that category's own top level, not leave the whole permanent split screen.
        if (detailFrames.isNotEmpty()) {
            val top = detailFrames.first()
            detailFrames.clear()
            detailFrames.add(top)
        }
    }

    override fun goBack() {
        if (detailFrames.size > 1) {
            detailFrames.removeAt(detailFrames.size - 1)
        }
    }

    override fun clearBackstack() {
        val top = detailFrames.lastOrNull() ?: return
        detailFrames.clear()
        detailFrames.add(top)
    }

    override fun canGoBack(): Boolean = detailFrames.size > 1

    override fun isShown(): Boolean = detailFrames.isNotEmpty()

    override fun isTransparent(): Boolean = false

    override fun isOverlay(): Boolean = false

    override fun isPaused(): Boolean = isPausedFlag

    override fun getViewId(): Int = detailViewId
}
