package com.abhimankolte.aethertube.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

/**
 * Preferences owned by this fork.
 *
 * Deliberately separate from {@link com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData} rather
 * than adding fields to it: MainUIData is upstream's, and every field added there is a merge
 * conflict waiting to happen in a file that upstream edits often. This uses AppPrefs' generic
 * profile-scoped key/value store, so it costs upstream nothing.
 */
public class AetherTubePrefs {
    private static final String AETHERTUBE_DATA = "aethertube_data";

    /** Match the device: drop the expensive effects only on hardware that looks like it needs it. */
    public static final int EFFECTS_AUTO = 0;
    /** Always draw them, whatever the device reports. */
    public static final int EFFECTS_ALWAYS = 1;
    /** Never draw them, even on capable hardware - the cheapest possible rendering path. */
    public static final int EFFECTS_NEVER = 2;

    @SuppressLint("StaticFieldLeak")
    private static AetherTubePrefs sInstance;
    private final AppPrefs mPrefs;
    private int mVisualEffectsMode;

    private AetherTubePrefs(Context context) {
        mPrefs = AppPrefs.instance(context.getApplicationContext());
        restoreState();
    }

    public static AetherTubePrefs instance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new AetherTubePrefs(context);
        }

        return sInstance;
    }

    /** One of EFFECTS_AUTO / EFFECTS_ALWAYS / EFFECTS_NEVER. */
    public int getVisualEffectsMode() {
        return mVisualEffectsMode;
    }

    public void setVisualEffectsMode(int mode) {
        mVisualEffectsMode = mode;
        persistState();
    }

    private void restoreState() {
        String[] split = Helpers.splitData(mPrefs.getProfileData(AETHERTUBE_DATA));

        mVisualEffectsMode = Helpers.parseInt(split, 0, EFFECTS_AUTO);
    }

    private void persistState() {
        mPrefs.setProfileData(AETHERTUBE_DATA, Helpers.mergeData(mVisualEffectsMode));
    }
}
