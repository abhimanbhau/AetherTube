package com.abhimankolte.aethertube.tv.ui.shorts;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.ExoPlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.ExoPlayerInitializer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer.CustomOverridesRenderersFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector.RestoreTrackSelector;

import java.io.InputStream;
import java.util.List;

/**
 * The playback engine behind the vertical Shorts feed.
 *
 * <h3>Why this exists rather than reusing an upstream player</h3>
 *
 * Upstream has exactly two {@link PlaybackView} implementations and neither fits:
 *
 * <ul>
 *   <li>{@code PlaybackFragment} is the real player, but it is a leanback {@code VideoSupportFragment}
 *       carrying a rows adapter, a transport-control glue, suggestions and seek preview. A vertical
 *       full-bleed feed would spend all its effort suppressing that UI.
 *   <li>{@code EmbedPlayerView} is the right <em>shape</em> - a bare ExoPlayer {@code PlayerView} that
 *       implements {@code PlaybackView} by stubbing every UI call - but it is deliberately crippled
 *       for card previews: it pins the format to SD, treats {@code setFormat} as a no-op, stubs
 *       {@code setAspectRatio}, and reports {@link #isEmbed()} {@code true}.
 * </ul>
 *
 * That last flag is the decisive one. Upstream branches on it in a dozen controllers, and every branch
 * is a "this is only a thumbnail preview, don't bother" shortcut: no watch-history reporting, no
 * suggestions, no next-video prefetch, and {@code VideoLoaderController} forces
 * {@code PLAYBACK_MODE_CLOSE} so playback simply stops at the end instead of looping or advancing.
 * A Shorts feed wants all of that behaviour, so it has to report itself as a real player.
 *
 * So this is EmbedPlayerView's scaffolding with the preview-specific compromises removed. It is a copy
 * rather than a subclass because the parts that need changing - format selection, mute, aspect - all
 * happen inside its private {@code createPlayerObjects()}. Copying a stub-heavy adapter into this
 * fork's own package costs nothing at merge time; editing upstream's would not.
 *
 * <h3>What upstream still drives</h3>
 *
 * Everything except pixels. {@link PlaybackPresenter} and its controller stack own video loading,
 * format selection, watch state, SponsorBlock and the next/previous walk through the group; this class
 * only supplies a surface and forwards the metadata the controllers push at it
 * ({@link #setTitle}, {@link #setChannelIcon}, {@link #setButtonState}) to whatever Compose UI is
 * listening.
 */
public class ShortsPlayerView extends PlayerView implements PlaybackView {
    private static final String TAG = ShortsPlayerView.class.getSimpleName();

    /**
     * How the Compose layer hears about state it cannot poll for. Everything here arrives on the main
     * thread, pushed by upstream's controllers.
     */
    public interface Listener {
        /** First frame is decoded and the surface has something real on it - safe to drop the poster. */
        void onVideoLoaded();

        /** Buffering/spinner state, straight from {@code showProgressBar}. */
        void onLoadingChanged(boolean loading);

        /** Title as resolved by the metadata round-trip, which is later than the card's own title. */
        void onTitleChanged(String title);

        /** Channel avatar URL, or null before metadata lands. */
        void onChannelIconChanged(String iconUrl);

        /** True video aspect, reported once the format is known. Shorts are ~0.5625 (9:16). */
        void onAspectRatioChanged(float ratio);

        /**
         * Playback moved to another video without the feed asking - end-of-video auto-advance, or a
         * controller deciding to skip. The feed follows rather than fights it.
         */
        void onVideoChanged(Video video);

        /** The engine wants the player gone (unplayable video, fatal error). */
        void onFinishRequested();
    }

    private SimpleExoPlayer mPlayer;
    private ExoPlayerInitializer mPlayerInitializer;
    private ExoPlayerController mExoPlayerController;
    private PlaybackPresenter mPlaybackPresenter;
    private Video mVideo;
    private Listener mListener;

    public ShortsPlayerView(Context context) {
        super(context);
        init();
    }

    public ShortsPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ShortsPlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // All chrome is drawn in Compose on top of this view. ExoPlayer's own controller would both
        // duplicate it and steal D-pad focus from the feed.
        setUseController(false);
        // The stage Compose hands us is already shaped to the video, so FIT is a no-op in the common
        // case and correctly letterboxes the occasional 1:1 or 4:5 short instead of cropping it.
        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        // No shutter: the poster layer in Compose covers the swap, and ExoPlayer's own black shutter
        // would flash over the poster during it.
        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    /**
     * Starts (or switches to) a video. Safe to call repeatedly; the engine is created once and reused
     * for the life of the feed, which is what keeps paging cheap.
     */
    public void openVideo(@Nullable Video video) {
        if (video == null) {
            return;
        }

        if (mPlaybackPresenter == null) {
            mPlaybackPresenter = PlaybackPresenter.instance(getContext());
        }

        Log.d(TAG, "openVideo: %s (engine=%s)", video.videoId, isEngineInitialized());

        // A card preview may well be running when the user clicks into the feed. It holds its own
        // ExoPlayer and its own claim on PlaybackPresenter, and upstream's own comment on the matter is
        // that it "doesn't dispose properly" - so evict it explicitly before taking over, exactly as
        // PlaybackPresenter.openVideo() does when promoting a preview to the fullscreen player.
        PlaybackView current = mPlaybackPresenter.getView();
        if (current != null && current != this && current.isEmbed()) {
            current.finishReally();
            mPlaybackPresenter.setView(null);
        }

        initPlayer();
        createPlayerObjects();
        mPlaybackPresenter.onNewVideo(video);
    }

    private void initPlayer() {
        if (isEngineInitialized()) {
            // Re-assert ownership only when something else has taken the presenter - a dialog (quality,
            // comments) can hand it to another view and back while the feed is still alive.
            //
            // Deliberately not unconditional: setView() re-runs a "switching players" fixup that
            // re-reads the *current* view's video and pushes it back into the presenter and the
            // playlist. Called on every page change that would resurrect the video we are leaving,
            // right before we ask for the new one.
            if (mPlaybackPresenter.getView() != this) {
                mPlaybackPresenter.setView(this);
            }
            return;
        }

        mPlayerInitializer = new ExoPlayerInitializer(getContext());
        mPlaybackPresenter.setView(this);
        mExoPlayerController = new ExoPlayerController(getContext(), mPlaybackPresenter);
        mExoPlayerController.setOnVideoLoaded(this::onVideoLoadedInt);
        mPlaybackPresenter.onViewInitialized();
    }

    private void createPlayerObjects() {
        if (isEngineInitialized()) {
            setPlayer(mPlayer);
            return;
        }

        DefaultTrackSelector trackSelector = new RestoreTrackSelector(new AdaptiveTrackSelection.Factory());
        mExoPlayerController.setTrackSelector(trackSelector);

        DefaultRenderersFactory renderersFactory = new CustomOverridesRenderersFactory(getContext());
        mPlayer = mPlayerInitializer.createPlayer(getContext(), renderersFactory, trackSelector);
        mPlayer.setPlayWhenReady(true);

        mExoPlayerController.setPlayer(mPlayer);

        setPlayer(mPlayer);

        // Deliberately no selectFormat() here. EmbedPlayerView pins SD because a preview is a few
        // hundred pixels wide; a short fills the screen height, so it gets the user's configured
        // quality like any other video - which VideoLoaderController applies through setFormat() below
        // now that isEmbed() no longer short-circuits it.
        mPlaybackPresenter.onEngineInitialized();
    }

    private void onVideoLoadedInt() {
        if (mListener != null) {
            mListener.onVideoLoaded();
        }
    }

    private void destroyPlayerObjects() {
        if (!isEngineInitialized()) {
            return;
        }

        // Don't tear down the presenter if it has already moved on to another view.
        if (mPlaybackPresenter.getView() == null || mPlaybackPresenter.getView() == this) {
            mPlaybackPresenter.onEngineReleased();
        }

        mPlayerInitializer.release();
        mExoPlayerController.setOnVideoLoaded(null);
        mExoPlayerController.release();
        mPlayer = null;
        setPlayer(null);
    }

    /** Called by the host when the feed is closing for good. */
    public void release() {
        mListener = null;
        destroyPlayerObjects();

        if (mPlaybackPresenter != null && mPlaybackPresenter.getView() == this) {
            mPlaybackPresenter.setView(null);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PlayerManager
    // ---------------------------------------------------------------------------------------------

    @Override
    public void setVideo(Video item) {
        boolean changed = mVideo != null && item != null && !mVideo.equals(item);
        Log.d(TAG, "setVideo: %s (changed=%s)", item != null ? item.videoId : null, changed);
        mVideo = item;

        if (mExoPlayerController != null) {
            mExoPlayerController.setVideo(item);
        }

        if (changed && mListener != null) {
            mListener.onVideoChanged(item);
        }
    }

    @Override
    public Video getVideo() {
        return mVideo;
    }

    @Override
    public void finish() {
        if (mListener != null) {
            mListener.onFinishRequested();
        }
    }

    @Override
    public void finishReally() {
        destroyPlayerObjects();

        if (mListener != null) {
            mListener.onFinishRequested();
        }
    }

    @Override
    public void showBackground(String url) {
        // The Compose poster layer already covers the gap between videos.
    }

    @Override
    public void showBackgroundColor(int colorResId) {
        // As above - the feed is always on black.
    }

    @Override
    public void resetPlayerState() {
        // No transient UI state to reset; the feed owns its own.
    }

    /**
     * False, unlike EmbedPlayerView. This is a real player: it should report watch history, load
     * suggestions, prefetch the next video and honour the user's loop/auto-advance preference. See the
     * class comment for why this single flag was the reason not to subclass EmbedPlayerView.
     */
    @Override
    public boolean isEmbed() {
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // PlayerUI - the feed draws its own chrome, so most of this is intentionally empty. The handful
    // that are implemented are metadata the controllers push at us and the overlay wants.
    // ---------------------------------------------------------------------------------------------

    @Override
    public void setTitle(String title) {
        if (mListener != null) {
            mListener.onTitleChanged(title);
        }
    }

    @Override
    public void setChannelIcon(String iconUrl) {
        if (mListener != null) {
            mListener.onChannelIconChanged(iconUrl);
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        if (mListener != null) {
            mListener.onLoadingChanged(show);
        }
    }

    @Override
    public void updateSuggestions(VideoGroup group) { }

    @Override
    public void removeSuggestions(VideoGroup group) { }

    @Override
    public int getSuggestionsIndex(VideoGroup group) {
        return 0;
    }

    @Override
    public VideoGroup getSuggestionsByIndex(int index) {
        return null;
    }

    @Override
    public void focusSuggestedItem(int index) { }

    @Override
    public void focusSuggestedItem(Video video) { }

    @Override
    public void resetSuggestedPosition() { }

    @Override
    public boolean isSuggestionsEmpty() {
        return true;
    }

    @Override
    public void clearSuggestions() { }

    @Override
    public void showOverlay(boolean show) { }

    /**
     * Always false. Upstream reads this to decide whether a key press belongs to a visible overlay;
     * the feed has no such overlay and handles its own keys, so claiming one would suppress the
     * controllers' own left/right skip handling for no reason.
     */
    @Override
    public boolean isOverlayShown() {
        return false;
    }

    @Override
    public void showSuggestions(boolean show) { }

    /**
     * True, permanently. Read literally this means "the suggestions UI is currently occupying the
     * screen, so don't rebuild it", and for this view that is simply always the case: the feed owns
     * the whole screen and there is no suggestions surface to rebuild.
     *
     * It is also load-bearing. Reporting false made {@code SuggestionsController.appendSuggestions()}
     * walk every suggestion row in the metadata and call {@code continueGroupIfNeeded} on each - and
     * because a short's suggestion rows are small, {@code shouldContinueRowGroup} said yes to all of
     * them. That was nine parallel continuation requests per page change, every one of them fetching
     * rows for a UI this view does not have. Answering truthfully makes appendSuggestions return
     * immediately, while {@code syncCurrentVideo} and the metadata listeners - which is where the
     * channel icon, the next-video link and the like counts come from - still run.
     *
     * The other four callers were checked and all behave correctly under this answer: the menu key is
     * handled here in Compose anyway, SponsorBlock's interactive skip dialog stays suppressed (right
     * for a feed), clearSuggestions() is a no-op here, and the only VideoLoaderController branch that
     * reads it is PLAYBACK_MODE_CLOSE - where suppressing "close the player at end of video" is what
     * we want, since a feed should not exit itself because one short finished.
     */
    @Override
    public boolean isSuggestionsShown() {
        return true;
    }

    @Override
    public void showControls(boolean show) { }

    @Override
    public boolean isControlsShown() {
        return false;
    }

    @Override
    public int getButtonState(int buttonId) {
        return -1;
    }

    @Override
    public void setButtonState(int buttonId, int buttonState) { }

    @Override
    public void setSeekPreviewTitle(String title) { }

    @Override
    public void setNextTitle(Video nextVideo) { }

    @Override
    public void showDebugInfo(boolean show) { }

    @Override
    public void showSubtitles(boolean show) { }

    @Override
    public void loadStoryboard() {
        // Seek previews need a scrubber. There isn't one.
    }

    @Override
    public void setSeekBarSegments(List<SeekBarSegment> segments) { }

    @Override
    public void updateEndingTime() { }

    @Override
    public void setChatReceiver(ChatReceiver chatReceiver) { }

    // ---------------------------------------------------------------------------------------------
    // PlayerEngine - straight delegation to ExoPlayerController
    // ---------------------------------------------------------------------------------------------

    @Override
    public void openSabr(MediaItemFormatInfo formatInfo) {
        mExoPlayerController.openSabr(formatInfo);
    }

    @Override
    public void openDash(MediaItemFormatInfo formatInfo) {
        mExoPlayerController.openDash(formatInfo);
    }

    @Override
    public void openDash(InputStream dashManifest) {
        mExoPlayerController.openDash(dashManifest);
    }

    @Override
    public void openDashUrl(String dashManifestUrl) {
        mExoPlayerController.openDashUrl(dashManifestUrl);
    }

    @Override
    public void openHlsUrl(String hlsPlaylistUrl) {
        mExoPlayerController.openHlsUrl(hlsPlaylistUrl);
    }

    @Override
    public void openUrlList(List<String> urlList) {
        mExoPlayerController.openUrlList(urlList);
    }

    @Override
    public void openMerged(MediaItemFormatInfo formatInfo, String hlsPlaylistUrl) {
        mExoPlayerController.openMerged(formatInfo, hlsPlaylistUrl);
    }

    @Override
    public void openMerged(InputStream dashManifest, String hlsPlaylistUrl) {
        mExoPlayerController.openMerged(dashManifest, hlsPlaylistUrl);
    }

    @Override
    public long getPositionMs() {
        return mExoPlayerController != null ? mExoPlayerController.getPositionMs() : 0;
    }

    @Override
    public void setPositionMs(long positionMs) {
        if (mExoPlayerController != null) {
            mExoPlayerController.setPositionMs(positionMs);
        }
    }

    @Override
    public long getDurationMs() {
        return mExoPlayerController != null ? mExoPlayerController.getDurationMs() : 0;
    }

    @Override
    public void setPlayWhenReady(boolean play) {
        if (mExoPlayerController != null) {
            mExoPlayerController.setPlayWhenReady(play);
        }
    }

    @Override
    public boolean getPlayWhenReady() {
        return mExoPlayerController != null && mExoPlayerController.getPlayWhenReady();
    }

    @Override
    public boolean isPlaying() {
        return mExoPlayerController != null && mExoPlayerController.isPlaying();
    }

    @Override
    public boolean isLoading() {
        return mExoPlayerController != null && mExoPlayerController.isLoading();
    }

    @Override
    public List<FormatItem> getVideoFormats() {
        return mExoPlayerController.getVideoFormats();
    }

    @Override
    public List<FormatItem> getAudioFormats() {
        return mExoPlayerController.getAudioFormats();
    }

    @Override
    public List<FormatItem> getSubtitleFormats() {
        return mExoPlayerController.getSubtitleFormats();
    }

    /** Honoured, unlike EmbedPlayerView's no-op: the user's quality preference applies here. */
    @Override
    public void setFormat(FormatItem option) {
        if (mExoPlayerController != null) {
            mExoPlayerController.selectFormat(option);
        }
    }

    @Override
    public FormatItem getVideoFormat() {
        return mExoPlayerController.getVideoFormat();
    }

    @Override
    public FormatItem getAudioFormat() {
        return mExoPlayerController.getAudioFormat();
    }

    @Override
    public FormatItem getSubtitleFormat() {
        return mExoPlayerController.getSubtitleFormat();
    }

    @Override
    public boolean isEngineInitialized() {
        return mPlayer != null;
    }

    @Override
    public void restartEngine() {
        destroyPlayerObjects();
        createPlayerObjects();
    }

    @Override
    public void reloadPlayback() {
        if (mPlaybackPresenter != null) {
            mPlaybackPresenter.onNewVideo(mVideo);
        }
    }

    @Override
    public void blockEngine(boolean block) { }

    @Override
    public boolean isEngineBlocked() {
        return false;
    }

    @Override
    public boolean isInPIPMode() {
        return false;
    }

    @Override
    public boolean containsMedia() {
        return mExoPlayerController != null && mExoPlayerController.containsMedia();
    }

    @Override
    public void setSpeed(float speed) {
        if (mExoPlayerController != null) {
            mExoPlayerController.setSpeed(speed);
        }
    }

    @Override
    public float getSpeed() {
        return mExoPlayerController != null ? mExoPlayerController.getSpeed() : 1f;
    }

    @Override
    public void setPitch(float pitch) {
        if (mExoPlayerController != null) {
            mExoPlayerController.setPitch(pitch);
        }
    }

    @Override
    public float getPitch() {
        return mExoPlayerController != null ? mExoPlayerController.getPitch() : 1f;
    }

    @Override
    public void setVolume(float volume) {
        if (mExoPlayerController != null) {
            mExoPlayerController.setVolume(volume);
        }
    }

    @Override
    public float getVolume() {
        return mExoPlayerController != null ? mExoPlayerController.getVolume() : 1f;
    }

    @Override
    public int getResizeMode() {
        return super.getResizeMode();
    }

    @Override
    public void setZoomPercents(int percents) {
        // Zoom is a 16:9-letterboxing remedy. The stage is already cut to the video's own shape.
    }

    /**
     * Forwarded to the feed rather than applied here. {@code VideoLoaderController} reports the true
     * format ratio as soon as it knows it (~0.5625 for a real short, but plenty of "shorts" are 1:1 or
     * 4:5), and the Compose stage resizes to match - so the video fills its box exactly and RESIZE_MODE_FIT
     * never has anything to letterbox.
     */
    @Override
    public void setAspectRatio(float ratio) {
        if (ratio > 0 && mListener != null) {
            mListener.onAspectRatioChanged(ratio);
        }
    }

    @Override
    public void setRotationAngle(int angle) { }

    @Override
    public void setVideoFlipEnabled(boolean enabled) { }

    @Override
    public void setVideoGravity(int gravity) { }
}
