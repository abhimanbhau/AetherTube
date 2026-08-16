package com.abhimankolte.aethertube.tv.ui.shorts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.abhimankolte.aethertube.tv.ui.common.compose.AetherTubeTheme
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData
import com.liskovsoft.smartyoutubetv2.tv.R
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity

/**
 * Hosts the vertical Shorts feed.
 *
 * Launched directly with [start] rather than through `ViewManager.startView`, because the ordinary
 * route into playback (`PlaybackPresenter.openVideo`) is hard-wired to `PlaybackView.class` and would
 * open the standard 16:9 player instead. It is still registered with ViewManager (see MainApplication)
 * so Back lands on Home rather than triggering the app-exit prompt.
 */
class ShortsPlayerActivity : LeanbackActivity() {
    companion object {
        /**
         * The video to open, handed over out-of-band.
         *
         * Not an Intent extra: the feed is seeded from `video.getGroup()`, and a [VideoGroup] is a live
         * object holding the MediaGroup continuation token that makes paging possible. Serialising the
         * Video through an Intent would arrive with that group stripped, leaving a one-item feed. The
         * hop is same-process and immediate, so a static handoff is honest about what is happening.
         */
        private var pendingVideo: Video? = null

        fun start(context: Context, video: Video) {
            pendingVideo = video
            context.startActivity(Intent(context, ShortsPlayerActivity::class.java))
        }

        private fun take(): Video? = pendingVideo.also { pendingVideo = null }
    }

    private lateinit var playerView: ShortsPlayerView
    private var feed: ShortsFeed? = null

    private var readyVideoId by mutableStateOf<String?>(null)
    private var aspectRatio by mutableFloatStateOf(0f)
    private var channelIconUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A short is ~30s of someone not touching the remote; without this the screensaver can land
        // mid-feed.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val seed = take()

        if (seed == null) {
            // Nothing to play - most likely the process was restarted and the handoff didn't survive.
            finish()
            return
        }

        // Inflated rather than constructed: the surface type is a constructor-time attribute and this
        // feed needs a TextureView. See shorts_player_view.xml.
        playerView = layoutInflater.inflate(R.layout.shorts_player_view, null) as ShortsPlayerView
        playerView.setListener(listener)

        val shortsFeed = ShortsFeed(seed)
        feed = shortsFeed

        setContent {
            AetherTubeTheme {
                ShortsScreen(
                    feed = shortsFeed,
                    playerView = playerView,
                    readyVideoId = readyVideoId,
                    aspectRatio = aspectRatio,
                    channelIconUrl = channelIconUrl,
                    onPlayVideo = { video ->
                        // Metadata is per-video; drop the previous video's before the new one lands or
                        // the wrong avatar/aspect ratio shows for a moment - e.g. a 1:1 short's box
                        // otherwise holds until the incoming 9:16 short's ratio decodes, ShortsScreen's
                        // 0f fallback (DEFAULT_ASPECT) is the standard vertical box every short starts as.
                        channelIconUrl = null
                        aspectRatio = 0f
                        playerView.openVideo(video)
                    },
                    onExit = { finish() },
                    onTogglePlay = { playerView.playWhenReady = !playerView.playWhenReady },
                    onSeek = { delta ->
                        val target = (playerView.positionMs + delta)
                            .coerceIn(0L, playerView.durationMs.coerceAtLeast(0L))
                        playerView.positionMs = target
                    },
                    onOpenMenu = { video ->
                        VideoMenuPresenter.instance(this).showMenu(video)
                    },
                )
            }
        }
    }

    private val listener = object : ShortsPlayerView.Listener {
        override fun onVideoLoaded() {
            // Tagged with the id rather than a bare flag so the UI can tell "this video is painted"
            // from "some earlier video was painted".
            readyVideoId = playerView.video?.videoId
        }

        override fun onLoadingChanged(loading: Boolean) {
            if (loading) {
                readyVideoId = null
            }
        }

        override fun onTitleChanged(title: String?) {
            // The card's own title is already on screen and is the same string in practice. Taking the
            // metadata one as well would only make the overlay flicker as it re-lays out.
        }

        override fun onChannelIconChanged(iconUrl: String?) {
            channelIconUrl = iconUrl
        }

        override fun onAspectRatioChanged(ratio: Float) {
            aspectRatio = ratio
        }

        override fun onVideoChanged(video: Video?) {
            // Playback moved on without the feed asking - end-of-video auto-advance, or a controller
            // skipping something unplayable. Follow it rather than fight it.
            feed?.syncTo(video)
        }

        override fun onFinishRequested() {
            finish()
        }
    }

    override fun initTheme() {
        val playerThemeResId = MainUIData.instance(this).colorScheme.playerThemeResId
        if (playerThemeResId > 0) {
            setTheme(playerThemeResId)
        }
    }

    override fun onPause() {
        super.onPause()
        // Shorts are short: pausing on background and resuming where it left off is less jarring than
        // carrying on playing audio behind whatever the user switched to.
        if (::playerView.isInitialized) {
            playerView.playWhenReady = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (::playerView.isInitialized) {
            playerView.playWhenReady = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        feed?.release()
        if (::playerView.isInitialized) {
            playerView.release()
        }
    }
}
