package com.abhimankolte.aethertube.tv.ui.home.compose

import android.os.Bundle
import com.liskovsoft.smartyoutubetv2.tv.R
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity

/**
 * Compose-for-TV replacement for [com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity] as the
 * app's root/launcher screen. See [ComposeHomeFragment] for what's ported vs still falling back to the
 * original leanback screen.
 */
class ComposeHomeActivity : LeanbackActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compose_home)
    }
}
