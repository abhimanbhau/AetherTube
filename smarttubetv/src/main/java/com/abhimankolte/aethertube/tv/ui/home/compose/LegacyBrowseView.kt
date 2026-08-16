package com.abhimankolte.aethertube.tv.ui.home.compose

/**
 * Marker interface used only to register [com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity]'s
 * parent with [com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager] (see MainApplication).
 *
 * BrowseActivity is launched directly (plain Intent) from [ComposeHomeFragment] as a fallback for
 * section types not yet ported to Compose - it no longer owns the real `BrowseView` registration
 * (ComposeHomeActivity does). Without *some* parent registration, ViewManager.addTop() treats any
 * activity with no known parent as a new root and clears the whole back-stack, which breaks Back
 * navigation from the fallback screen. Nothing calls startView(LegacyBrowseView::class.java); this
 * interface only exists to populate that parent mapping.
 */
interface LegacyBrowseView
