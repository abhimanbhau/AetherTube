package com.abhimankolte.aethertube.tv.ui.home.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video

/** Compose-observable mirror of one row (one [com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup]). */
class HomeRow(val id: Int, title: String) {
    var title: String by mutableStateOf(title)
    val videos = mutableStateListOf<Video>()
}
