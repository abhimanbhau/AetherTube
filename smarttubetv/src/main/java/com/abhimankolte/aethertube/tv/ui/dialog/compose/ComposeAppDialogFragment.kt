package com.abhimankolte.aethertube.tv.ui.dialog.compose

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
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.AppDialogActivity

/**
 * Compose-for-TV replacement for [com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.AppDialogFragment].
 * Renders [AppDialogPresenter]'s plain-data [OptionCategory]/OptionItem model as a two-pane
 * master-detail layout (left = category list, right = the selected category's options), like the
 * real Android/Android TV Settings app - instead of leanback's single-pane push/pop preference screens.
 *
 * Each [show] call is one "frame" on an internal stack, mirroring the original's child-fragment-manager
 * back stack: an OptionItem's onSelect() can itself trigger another showDialog() call (e.g. opening a
 * sub-dialog) while this one is still up, which should push a new level rather than replace the current
 * one. [goBack]/[canGoBack] pop that stack; only running out of frames actually finishes the dialog.
 *
 * TYPE_CHAT / TYPE_COMMENTS categories (live chat/comments overlays during playback, not real "settings")
 * aren't rendered here - they're specialized streaming UIs, out of scope for a settings redesign and too
 * risky to half-implement. Any frame containing one falls back to the original leanback [AppDialogActivity].
 */
class ComposeAppDialogFragment :
    Fragment(),
    AppDialogView {
    private val frames = mutableStateListOf<DialogFrame>()
    private var dialogViewId by mutableStateOf(0)
    private var isPausedFlag = false

    // Set right before handing off to the legacy AppDialogActivity for TYPE_CHAT/TYPE_COMMENTS (see
    // show() below). AppDialogPresenter is a singleton with one set of "backup" fields it hands to
    // whichever view registers next - our onDestroy() firing (from requireActivity().finish()) would
    // otherwise call onViewDestroyed(), which explicitly nulls those backup fields "mem leak fix" - a
    // race that nulled the categories out from under AppDialogActivity's own fragment before it got a
    // chance to register and consume them, so Comments/Chat opened blank.
    private var isRedirectingToLegacyDialog = false

    private class DialogFrame(
        val categories: List<OptionCategory>,
        val title: CharSequence?,
        val isExpandable: Boolean,
        val isTransparent: Boolean,
        val isOverlay: Boolean,
    ) {
        /** Category open when a nested dialog was pushed from this frame - restored on pop. */
        var categoryIndex: Int = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)

        AppDialogPresenter.instance(requireContext()).setView(this)
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = ComposeView(requireContext()).apply {
        setContent {
            AetherTubeTheme {
                val frame = frames.lastOrNull()
                if (frame != null) {
                    AppDialogScreen(
                        title = frame.title?.toString(),
                        categories = frame.categories,
                        showBackButton = frames.size > 1,
                        onBack = { goBack() },
                        isOverlay = frame.isOverlay,
                        initialCategoryIndex = frame.categoryIndex,
                        onCategoryIndexChange = { index -> frame.categoryIndex = index },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AppDialogPresenter.instance(requireContext()).onViewInitialized()
    }

    override fun onPause() {
        super.onPause()
        isPausedFlag = true
    }

    override fun onResume() {
        super.onResume()
        isPausedFlag = false
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isRedirectingToLegacyDialog) {
            return
        }

        val presenter = AppDialogPresenter.instance(requireContext())
        if (presenter.view === this) {
            presenter.onViewDestroyed()
        }
    }

    fun onFinishDialog() {
        // Mid-handoff to the legacy dialog (see show()): our Activity is being finished only so the
        // legacy one can take over, which is NOT the user closing the dialog. AppDialogPresenter#onFinish
        // calls clear(), nulling the very backup categories the legacy fragment is about to read - the
        // real cause of Comments/Chat opening blank. (onDestroy is guarded too, but finish() gets here
        // first via ComposeAppDialogActivity#finish, so guarding onDestroy alone was not enough.)
        if (isRedirectingToLegacyDialog) {
            return
        }

        AppDialogPresenter.instance(requireContext()).onFinish()
    }

    // ---- AppDialogView ----

    override fun show(
        categories: MutableList<OptionCategory>?,
        title: CharSequence?,
        isExpandable: Boolean,
        isTransparent: Boolean,
        isOverlay: Boolean,
        id: Int,
    ) {
        if (categories == null) {
            return
        }

        if (categories.any { it.type == OptionCategory.TYPE_CHAT || it.type == OptionCategory.TYPE_COMMENTS }) {
            isRedirectingToLegacyDialog = true
            startActivity(Intent(requireContext(), AppDialogActivity::class.java))
            requireActivity().finish()
            return
        }

        dialogViewId = id
        frames.add(DialogFrame(categories, title, isExpandable, isTransparent, isOverlay))
    }

    override fun finish() {
        requireActivity().finish()
    }

    override fun goBack() {
        if (frames.size > 1) {
            frames.removeAt(frames.size - 1)
        } else {
            finish()
        }
    }

    override fun clearBackstack() {
        val top = frames.lastOrNull() ?: return
        frames.clear()
        frames.add(top)
    }

    override fun canGoBack(): Boolean = frames.size > 1

    override fun isShown(): Boolean = frames.isNotEmpty()

    override fun isTransparent(): Boolean = frames.lastOrNull()?.isTransparent ?: false

    override fun isOverlay(): Boolean = frames.lastOrNull()?.isOverlay ?: false

    override fun isPaused(): Boolean = isPausedFlag

    override fun getViewId(): Int = dialogViewId
}
