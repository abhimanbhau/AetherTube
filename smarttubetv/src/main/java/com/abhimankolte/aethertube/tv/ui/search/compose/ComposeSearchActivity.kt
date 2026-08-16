package com.abhimankolte.aethertube.tv.ui.search.compose

import android.os.Bundle
import android.view.KeyEvent
import com.liskovsoft.smartyoutubetv2.tv.R
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity

/**
 * Compose-for-TV replacement for [com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.SearchTagsActivity].
 * Only used on API 21+ (see MainApplication#setupViewManager); older devices keep the leanback screen.
 */
class ComposeSearchActivity : LeanbackActivity() {
    private lateinit var fragment: ComposeSearchFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compose_search)
        fragment = supportFragmentManager.findFragmentById(R.id.compose_search_fragment) as ComposeSearchFragment
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // If there are no results found, press the left key to reselect the mic/search field
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !fragment.hasResults()) {
            fragment.focusOnSearchField()
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun finishReally() {
        super.finishReally()
        fragment.onFinish()
    }
}
