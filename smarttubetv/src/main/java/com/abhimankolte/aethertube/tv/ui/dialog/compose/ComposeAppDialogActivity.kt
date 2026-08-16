package com.abhimankolte.aethertube.tv.ui.dialog.compose

import android.os.Bundle
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity
import com.liskovsoft.smartyoutubetv2.tv.R

/**
 * Compose-for-TV replacement for [com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.AppDialogActivity].
 * Mirrors its back-key/finish semantics exactly (see [ComposeAppDialogFragment.canGoBack]/[goBack]) -
 * a "Back" press should pop one level of a nested dialog before actually closing the whole thing.
 *
 * This is the single shared dialog surface for the *whole* app (settings, context menus, confirmations,
 * etc. - anything that goes through AppDialogPresenter), not just the Settings screen. Extends
 * MotherActivity directly (not LeanbackActivity) to match the original AppDialogActivity, since dialogs
 * aren't part of the screen back-stack/exit-shortcut logic.
 */
class ComposeAppDialogActivity : MotherActivity() {
    private lateinit var fragment: ComposeAppDialogFragment
    private var isBackPressed = false

    /**
     * Deliberately does NOT call super. [MotherActivity.initTheme] applies the color scheme's
     * *browse* theme - a fully opaque, full-screen app theme - via setTheme() during onCreate, which
     * silently overrides the translucent `App.Theme.Compose.Dialog` this Activity declares in the
     * manifest. The result: an opaque window, so anything behind it (most importantly the video still
     * playing underneath when this is opened from the player via HQ/subtitles/speed) is replaced by a
     * black background. The legacy [com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.AppDialogActivity]
     * avoids this by overriding initTheme() to apply the *settings* theme, which is translucent.
     *
     * We keep the manifest theme untouched instead: it's purpose-built for this screen, and the
     * Compose content supplies its own colors (HomeColorScheme) rather than reading theme attributes.
     */
    override fun initTheme() {
        // no-op: keep the translucent manifest theme
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compose_app_dialog)
        fragment = supportFragmentManager.findFragmentById(R.id.compose_app_dialog_fragment) as ComposeAppDialogFragment
    }

    override fun onBackPressed() {
        isBackPressed = true
        super.onBackPressed()
    }

    override fun onResume() {
        isBackPressed = false
        super.onResume()
    }

    override fun finish() {
        if (isBackPressed && fragment.canGoBack()) {
            // Reset here, not just in onResume(): this branch returns without the activity actually
            // finishing, so onResume() never re-fires to clear it. A finish() called programmatically
            // afterward (not via a back-key press) would otherwise still see the stale true and
            // mistakenly pop a dialog level instead of finishing.
            isBackPressed = false
            fragment.goBack()
            return
        }

        fragment.onFinishDialog()
        super.finish()
    }
}
