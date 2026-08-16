package com.abhimankolte.aethertube.common.settings

import android.content.Context
import com.abhimankolte.aethertube.common.prefs.AetherTubePrefs
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData
import com.liskovsoft.smartyoutubetv2.common.prefs.SponsorBlockData

/**
 * What a portable settings code actually carries, and in what order.
 *
 * ## The append-only rule
 *
 * The layout is positional - there are no field tags, because at 48 bits a tag costs more than the
 * value it labels. That makes the ordering load-bearing:
 *
 *  - **Never reorder a field. Never remove one. Never change a field's width.**
 *  - New fields go at the end, and only at the end.
 *
 * Follow that and compatibility works in both directions for free: an older code simply runs out of
 * bits and the newer fields keep whatever the device already had, while an older app reads the
 * prefix it understands and ignores the rest. Break it and every code anyone has written down
 * silently decodes into the wrong settings - which is why [totalBits] is asserted in a test rather
 * than left to reviewer attention.
 *
 * If a field genuinely has to change, retire it (leave it in place, ignore its value) and append a
 * replacement. Only a change to the *meaning* of the layout as a whole needs a version bump.
 *
 * ## What is not here
 *
 * Anything unbounded or personal: account tokens, subscriptions, pinned channels, search history,
 * per-channel overrides. Those are either server-side, far too large for a typed code, or
 * credentials that have no business travelling in something you might read aloud. This is the
 * "make a new TV behave like my old one" set, not a backup.
 */
object SettingsRegistry {

    /**
     * Video quality presets, indexed by their position. Append-only like everything else here.
     *
     * Matched on resolution and frame rate only. [FormatItem] exposes width, height and frame rate
     * but not the codec, so a code cannot faithfully round-trip a codec choice; each preset pairs a
     * resolution with the codec that is the sensible partner for it. In practice people choose
     * "1080p60", not "1080p60 specifically in VP9", so the loss is theoretical.
     */
    private val VIDEO_PRESETS: List<Triple<Int, Int, String>?> = listOf(
        null,                                  // 0 - auto, let the player decide
        Triple(426, 240, "avc"),
        Triple(640, 360, "avc"),
        Triple(854, 480, "avc"),
        Triple(1280, 720, "avc"),
        Triple(1280, 720, "avc"),              // 5 - 720p60, fps differentiates it
        Triple(1920, 1080, "avc"),
        Triple(1920, 1080, "avc"),             // 7 - 1080p60
        Triple(1920, 1080, "vp9"),             // 8 - 1080p60 vp9
        Triple(2560, 1440, "vp9"),
        Triple(2560, 1440, "vp9"),             // 10 - 1440p60
        Triple(3840, 2160, "vp9"),
        Triple(3840, 2160, "vp9"),             // 12 - 2160p60
        Triple(1920, 1080, "av01"),
        Triple(3840, 2160, "av01")             // 14 - 2160p60 av01
    )

    /** Frame rate per preset index, parallel to [VIDEO_PRESETS]. */
    private val VIDEO_FPS = intArrayOf(0, 30, 30, 30, 30, 60, 30, 60, 60, 30, 60, 30, 60, 60, 60)

    /** Seek step presets in milliseconds. Encoding snaps to the nearest. */
    private val SEEK_STEPS = intArrayOf(1_000, 5_000, 10_000, 15_000, 20_000, 30_000, 60_000, 120_000)

    /**
     * The layout. Order is the wire format - see the class comment before touching it.
     *
     * Every entry reads and writes through upstream's existing public accessors, so this adds no
     * divergence to any upstream file.
     */
    private val FIELDS: List<Field> = listOf(
        // --- Picture -----------------------------------------------------------------------------
        Field("video_quality", 4, { videoPresetIndex(it) }, { ctx, v -> applyVideoPreset(ctx, v) }),
        Field("video_buffer", 2, { player(it).videoBufferType }, { c, v -> player(c).videoBufferType = v }),
        Field("legacy_codecs", 1, { player(it).isLegacyCodecsForced.b() }, { c, v -> player(c).setLegacyCodecsForced(v.t()) }),

        // --- Playback ----------------------------------------------------------------------------
        Field("playback_mode", 3, { player(it).playbackMode }, { c, v -> player(c).playbackMode = v }),
        Field("background_mode", 2, { player(it).backgroundMode }, { c, v -> player(c).backgroundMode = v }),
        Field("ok_button", 2, { player(it).okButtonBehavior }, { c, v -> player(c).okButtonBehavior = v }),
        Field("seek_step", 3, { seekStepIndex(it) }, { c, v -> player(c).seekIncrementMs = SEEK_STEPS[v] }),
        Field("number_key_seek", 1, { player(it).isNumberKeySeekEnabled.b() }, { c, v -> player(c).setNumberKeySeekEnabled(v.t()) }),
        Field("skip_shorts", 1, { player(it).isSkipShortsEnabled.b() }, { c, v -> player(c).setSkipShortsEnabled(v.t()) }),

        // --- Frame rate matching -----------------------------------------------------------------
        Field("afr", 1, { player(it).isAfrEnabled.b() }, { c, v -> player(c).setAfrEnabled(v.t()) }),
        Field("afr_fps_correction", 1, { player(it).isAfrFpsCorrectionEnabled.b() }, { c, v -> player(c).setAfrFpsCorrectionEnabled(v.t()) }),
        Field("afr_res_switch", 1, { player(it).isAfrResSwitchEnabled.b() }, { c, v -> player(c).setAfrResSwitchEnabled(v.t()) }),
        Field("skip_24_rate", 1, { player(it).isSkip24RateEnabled.b() }, { c, v -> player(c).setSkip24RateEnabled(v.t()) }),

        // --- Player overlay ----------------------------------------------------------------------
        Field("clock", 1, { player(it).isClockEnabled.b() }, { c, v -> player(c).setClockEnabled(v.t()) }),
        Field("remaining_time", 1, { player(it).isRemainingTimeEnabled.b() }, { c, v -> player(c).setRemainingTimeEnabled(v.t()) }),
        Field("ending_time", 1, { player(it).isEndingTimeEnabled.b() }, { c, v -> player(c).setEndingTimeEnabled(v.t()) }),
        Field("quality_info", 1, { player(it).isQualityInfoEnabled.b() }, { c, v -> player(c).setQualityInfoEnabled(v.t()) }),

        // --- SponsorBlock ------------------------------------------------------------------------
        Field("sponsorblock", 1, { sponsor(it).isSponsorBlockEnabled.b() }, { c, v -> sponsor(c).setSponsorBlockEnabled(v.t()) }),
        Field("sb_dont_skip_again", 1, { sponsor(it).isDontSkipSegmentAgainEnabled.b() }, { c, v -> sponsor(c).setDontSkipSegmentAgainEnabled(v.t()) }),
        Field("sb_paid_content", 1, { sponsor(it).isPaidContentNotificationEnabled.b() }, { c, v -> sponsor(c).setPaidContentNotificationEnabled(v.t()) }),

        // --- General behaviour ---------------------------------------------------------------------
        Field("history", 1, { general(it).isHistoryEnabled.b() }, { c, v -> general(c).setHistoryEnabled(v.t()) }),
        Field("screensaver_off", 1, { general(it).isScreensaverDisabled.b() }, { c, v -> general(c).setScreensaverDisabled(v.t()) }),
        Field("return_to_launcher", 1, { general(it).isReturnToLauncherEnabled.b() }, { c, v -> general(c).setReturnToLauncherEnabled(v.t()) }),
        Field("clock_24h", 1, { general(it).is24HourLocaleEnabled.b() }, { c, v -> general(c).set24HourLocaleEnabled(v.t()) }),
        Field("fullscreen", 1, { general(it).isFullscreenModeEnabled.b() }, { c, v -> general(c).setFullscreenModeEnabled(v.t()) }),
        Field("dpad_left_volume", 1, { general(it).isRemapDpadLeftToVolumeEnabled.b() }, { c, v -> general(c).setRemapDpadLeftToVolumeEnabled(v.t()) }),
        Field("app_exit_shortcut", 2, { general(it).appExitShortcut }, { c, v -> general(c).appExitShortcut = v }),

        // --- Search ------------------------------------------------------------------------------
        Field("instant_voice_search", 1, { search(it).isInstantVoiceSearchEnabled.b() }, { c, v -> search(c).setInstantVoiceSearchEnabled(v.t()) }),

        // --- Interface ---------------------------------------------------------------------------
        Field("new_ui", 1, { ui(it).isNewUiEnabled.b() }, { c, v -> ui(c).setNewUiEnabled(v.t()) }),
        Field("visual_effects", 2, { AetherTubePrefs.instance(it).visualEffectsMode }, { c, v -> AetherTubePrefs.instance(c).visualEffectsMode = v }),
        Field("card_preview", 2, { ui(it).cardPreviewType }, { c, v -> ui(c).cardPreviewType = v }),
        Field("thumb_quality", 2, { ui(it).thumbQuality }, { c, v -> ui(c).thumbQuality = v }),
        Field("card_multiline_title", 1, { ui(it).isCardMultilineTitleEnabled.b() }, { c, v -> ui(c).setCardMultilineTitleEnabled(v.t()) })
    )

    /** Total width of the layout. Asserted against the budget in SettingsRegistryTest. */
    val totalBits: Int get() = FIELDS.sumOf { it.bits }

    val fieldCount: Int get() = FIELDS.size

    /** Reads every registered setting off the device into a payload. */
    fun capture(context: Context): Long {
        val writer = BitWriter()

        for (field in FIELDS) {
            val raw = runCatching { field.read(context) }.getOrDefault(0)
            writer.write(field.bits, raw.coerceIn(0, (1 shl field.bits) - 1))
        }

        return writer.payload()
    }

    /**
     * Applies a payload to the device.
     *
     * A field whose bits are missing - because the code predates it - is left alone rather than
     * reset, so restoring an old code never silently clears something it never knew about.
     *
     * Each write is guarded individually: one upstream setter throwing must not abandon the other
     * thirty-odd settings half-applied.
     *
     * @return how many fields were applied.
     */
    fun apply(context: Context, payload: Long): Int {
        val reader = BitReader(payload)
        var applied = 0

        for (field in FIELDS) {
            val raw = reader.read(field.bits) ?: break

            if (runCatching { field.write(context, raw) }.isSuccess) {
                applied++
            }
        }

        return applied
    }

    private class Field(
        val name: String,
        val bits: Int,
        val read: (Context) -> Int,
        val write: (Context, Int) -> Unit
    )

    // -- accessors --------------------------------------------------------------------------------

    private fun player(c: Context) = PlayerData.instance(c)
    private fun general(c: Context) = GeneralData.instance(c)
    private fun ui(c: Context) = MainUIData.instance(c)
    private fun search(c: Context) = SearchData.instance(c)
    private fun sponsor(c: Context) = SponsorBlockData.instance(c)

    private fun Boolean.b() = if (this) 1 else 0
    private fun Int.t() = this != 0

    // -- video quality ----------------------------------------------------------------------------

    private fun videoPresetIndex(context: Context): Int {
        val current = player(context).getFormat(FormatItem.TYPE_VIDEO) ?: return 0
        val height = current.height
        val fps = current.frameRate

        if (height <= 0) {
            return 0 // auto
        }

        // Nearest preset by height, then frame rate. Exact matching would fail the moment YouTube
        // serves 1082 lines instead of 1080.
        var best = 0
        var bestScore = Int.MAX_VALUE

        for (i in 1 until VIDEO_PRESETS.size) {
            val preset = VIDEO_PRESETS[i] ?: continue
            val score = Math.abs(preset.second - height) * 4 + Math.abs(VIDEO_FPS[i] - fps.toInt())

            if (score < bestScore) {
                bestScore = score
                best = i
            }
        }

        return best
    }

    private fun applyVideoPreset(context: Context, index: Int) {
        if (index == 0 || index >= VIDEO_PRESETS.size) {
            player(context).setFormat(FormatItem.VIDEO_AUTO)
            return
        }

        val preset = VIDEO_PRESETS[index] ?: return
        val spec = "${preset.first},${preset.second},${VIDEO_FPS[index]},${preset.third}"
        player(context).setFormat(ExoFormatItem.fromVideoSpec(spec, false))
    }

    // -- seek step --------------------------------------------------------------------------------

    private fun seekStepIndex(context: Context): Int {
        val current = player(context).seekIncrementMs
        var best = 0

        for (i in SEEK_STEPS.indices) {
            if (Math.abs(SEEK_STEPS[i] - current) < Math.abs(SEEK_STEPS[best] - current)) {
                best = i
            }
        }

        return best
    }
}
