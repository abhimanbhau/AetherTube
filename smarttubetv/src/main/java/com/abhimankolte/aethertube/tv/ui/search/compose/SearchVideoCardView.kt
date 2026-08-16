package com.abhimankolte.aethertube.tv.ui.search.compose

import android.content.Context
import android.graphics.Color
import android.os.Build.VERSION
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover
import com.liskovsoft.smartyoutubetv2.common.utils.Utils
import com.liskovsoft.smartyoutubetv2.tv.R
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.embedplayer.EmbedPlayerView
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil

/**
 * Compose-hostable port of the leanback [com.liskovsoft.smartyoutubetv2.tv.ui.widgets.complexcardview.ComplexImageView]
 * preview behavior: a static thumbnail that's swapped for an animated preview image (Video#previewUrl)
 * or a small looping [EmbedPlayerView] a couple seconds after the card gains focus.
 */
class SearchVideoCardView(context: Context) : FrameLayout(context) {
    companion object {
        private const val PLAYER_START_DELAY_MS = 2_000L
    }

    private val mainImage = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
    private val previewContainer = FrameLayout(context)

    private var video: Video? = null
    private var cardPreviewType = MainUIData.CARD_PREVIEW_DISABLED
    private var widthPx = 0
    private var heightPx = 0

    private var previewImage: ImageView? = null
    private var previewPlayer: EmbedPlayerView? = null
    private val startPlayer = Runnable { createAndStartPlayer() }

    init {
        addView(mainImage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(previewContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
        previewContainer.visibility = View.GONE
    }

    fun bind(video: Video, thumbQuality: Int, cardPreviewType: Int, widthPx: Int, heightPx: Int) {
        this.cardPreviewType = cardPreviewType

        // AndroidView's update lambda re-runs on every recomposition of the enclosing card, which
        // happens continuously (up to 60/s) while any of its focus animations (scale, glow, etc.) are
        // mid-animation - not just when the bound video actually changes. Without this guard, Glide's
        // into() re-triggers on every one of those frames even for an identical, already-loaded image,
        // which resets the target's drawable state and reads as flicker.
        val alreadyBound = this.video?.videoId == video.videoId && this.widthPx == widthPx && this.heightPx == heightPx
        this.video = video
        this.widthPx = widthPx
        this.heightPx = heightPx

        if (alreadyBound) {
            return
        }

        Glide.with(context)
            .load(ClickbaitRemover.updateThumbnail(video, thumbQuality))
            .apply(ViewUtil.glideOptions())
            .override(widthPx, heightPx)
            .diskCacheStrategy(if (VERSION.SDK_INT > 21) DiskCacheStrategy.ALL else DiskCacheStrategy.NONE)
            .error(
                Glide.with(context)
                    .load(video.cardImageUrl)
                    .apply(ViewUtil.glideOptions())
                    .error(R.drawable.card_placeholder)
            )
            .into(mainImage)
    }

    fun unbind() {
        stopPlayback(stopImmediately = true)
        Glide.with(context.applicationContext).clear(mainImage)
        video = null
    }

    fun startPreview() {
        val video = video ?: return

        if (cardPreviewType == MainUIData.CARD_PREVIEW_DISABLED) {
            return
        }

        if (video.previewUrl != null) {
            if (previewImage == null) {
                val image = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    adjustViewBounds = true
                }
                previewImage = image
                previewContainer.addView(image, LayoutParams(widthPx, heightPx))
                previewContainer.visibility = View.VISIBLE
            }

            Glide.with(context.applicationContext)
                .load(video.previewUrl)
                .apply(ViewUtil.glideOptions())
                .into(previewImage!!)
        } else if (video.videoId != null) {
            Utils.postDelayed(startPlayer, PLAYER_START_DELAY_MS)
        }
    }

    fun stopPreview() {
        stopPlayback(stopImmediately = false)
    }

    private fun createAndStartPlayer() {
        val video = video ?: return

        var player = previewPlayer
        if (player == null) {
            player = EmbedPlayerView(context).apply {
                setQuality(if (minOf(widthPx, heightPx) < 300) EmbedPlayerView.QUALITY_LOW else EmbedPlayerView.QUALITY_NORMAL)
                setUseController(false)
                setMute(cardPreviewType == MainUIData.CARD_PREVIEW_MUTED)
                setBackgroundColor(Color.BLACK)
            }
            previewPlayer = player
            previewContainer.addView(player, LayoutParams(widthPx, heightPx))
            previewContainer.visibility = View.VISIBLE
        }

        player.openVideo(video)
    }

    private fun stopPlayback(stopImmediately: Boolean) {
        Utils.removeCallbacks(startPlayer)

        previewImage?.let {
            previewContainer.removeView(it)
            it.setImageDrawable(null)
            Glide.with(context.applicationContext).clear(it)
            previewImage = null
        }

        previewPlayer?.let { player ->
            if (stopImmediately) {
                player.finish()
                previewContainer.removeView(player)
                previewPlayer = null
            } else {
                player.setMute(true)
                Utils.postDelayed({
                    player.finish()
                    previewContainer.removeView(player)
                }, 500)
                previewPlayer = null
            }
        }

        if (previewContainer.childCount == 0) {
            previewContainer.visibility = View.GONE
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopPlayback(stopImmediately = true)
    }
}
