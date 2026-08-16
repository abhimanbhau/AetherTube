package com.abhimankolte.aethertube.tv.ui.settings.compose

import android.os.Bundle
import com.liskovsoft.smartyoutubetv2.tv.R
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity

/**
 * Hosts [ComposeSettingsFragment] - reached from Home's settings icon exactly like Search is (a
 * real separate screen, not an overlay on top of Home). A first Back press pops one level of a
 * nested category dialog if one's open; otherwise it's a normal Activity finish back to Home.
 */
class ComposeSettingsActivity : LeanbackActivity() {
    private lateinit var fragment: ComposeSettingsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compose_settings)
        fragment = supportFragmentManager.findFragmentById(R.id.compose_settings_fragment) as ComposeSettingsFragment
    }

    override fun onBackPressed() {
        if (fragment.canGoBackInDetail()) {
            fragment.goBack()
            return
        }

        super.onBackPressed()
    }
}
