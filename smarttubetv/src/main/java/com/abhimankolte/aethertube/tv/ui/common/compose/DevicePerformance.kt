package com.abhimankolte.aethertube.tv.ui.common.compose

import android.app.ActivityManager
import android.content.Context
import com.abhimankolte.aethertube.common.prefs.AetherTubePrefs
import androidx.compose.runtime.staticCompositionLocalOf
import com.liskovsoft.sharedutils.mylogger.Log

/**
 * Whether this device should skip the purely decorative, GPU-expensive effects.
 *
 * Android TV spans an enormous hardware range: the same build runs on a current mid-range TV SoC and
 * on a sub-$25 box with a couple of slow cores and ~1GB of RAM. The effects gated by this - full
 * screen backdrop blur, per-card glow blur, video card previews - are pure eye candy with no
 * functional value, and they are exactly what turns scrolling on those boxes into a slideshow.
 *
 * Detection is deliberately conservative: it is better to keep the effects on a device that could
 * have coped than to drop them on one that needed them. Evaluated once and cached, since none of
 * these inputs change while the app is running.
 */
object DevicePerformance {
    private const val TAG = "DevicePerformance"

    /**
     * Below this per-app heap ceiling (MB) a device is too tight for fullscreen blur work.
     *
     * Deliberately low. An earlier 128MB bar was wrong for this platform: Android TV devices
     * routinely report a 128MB heap while being perfectly capable, so it flagged normal TVs as
     * low-end. isLowRamDevice below is the canonical signal; this is only a backstop for OEMs that
     * never set it.
     */
    private const val LOW_MEMORY_CLASS_MB = 96

    /** Blur and preview decoding both want threads the box may not have. */
    private const val LOW_CORE_COUNT = 2

    @Volatile
    private var cached: Boolean? = null

    /**
     * True when the decorative effects should be skipped.
     *
     * The user's explicit choice wins over the heuristic in both directions - detection is only a
     * guess and is wrong in both: a 2-core Android TV emulator reports as low-end while being fine,
     * and some cheap boxes report capable hardware and still struggle.
     */
    fun shouldSkipEffects(context: Context): Boolean {
        return when (AetherTubePrefs.instance(context).visualEffectsMode) {
            AetherTubePrefs.EFFECTS_ALWAYS -> false
            AetherTubePrefs.EFFECTS_NEVER -> true
            else -> isLowEnd(context)
        }
    }

    fun isLowEnd(context: Context): Boolean {
        cached?.let { return it }

        val result = synchronized(this) {
            cached ?: compute(context).also { cached = it }
        }
        return result
    }

    private fun compute(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRam = am?.isLowRamDevice == true
        val memoryClass = am?.memoryClass ?: Int.MAX_VALUE
        val cores = Runtime.getRuntime().availableProcessors()

        val lowEnd = lowRam || memoryClass <= LOW_MEMORY_CLASS_MB || cores <= LOW_CORE_COUNT
        Log.d(
            TAG,
            "low-end=%s (isLowRamDevice=%s, memoryClass=%sMB, cores=%s)",
            lowEnd, lowRam, memoryClass, cores
        )
        return lowEnd
    }
}

/**
 * Read with `LocalLowEndDevice.current` anywhere below [AetherTubeTheme]. Static, because it never
 * changes for the lifetime of the process - so reading it costs nothing and never invalidates.
 */
val LocalLowEndDevice = staticCompositionLocalOf { false }
