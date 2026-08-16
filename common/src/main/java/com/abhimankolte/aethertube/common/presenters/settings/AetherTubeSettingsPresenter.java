package com.abhimankolte.aethertube.common.presenters.settings;

import android.content.Context;
import com.abhimankolte.aethertube.common.prefs.AetherTubePrefs;
import com.abhimankolte.aethertube.common.settings.SettingsCode;
import com.abhimankolte.aethertube.common.settings.SettingsRegistry;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings specific to this fork, gathered in one place instead of scattered through upstream's
 * categories.
 *
 * <p>The New UI switch previously sat near the bottom of Main UI -> Misc, which is a strange home
 * for the toggle that decides which entire interface the app uses. The visual-effects control lives
 * directly under it because it only has any meaning once the new UI is on.
 */
public class AetherTubeSettingsPresenter extends BasePresenter<Void> {
    private final MainUIData mMainUIData;
    private final AetherTubePrefs mPrefs;
    private boolean mRestartApp;

    private AetherTubeSettingsPresenter(Context context) {
        super(context);
        mMainUIData = MainUIData.instance(context);
        mPrefs = AetherTubePrefs.instance(context);
    }

    public static AetherTubeSettingsPresenter instance(Context context) {
        return new AetherTubeSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendNewUi(settingsPresenter);
        appendVisualEffects(settingsPresenter);
        appendSettingsCode(settingsPresenter);

        // Matches how upstream handles restart-requiring settings: prompt rather than
        // force-restart.
        settingsPresenter.showDialog(
                "AetherTube",
                () -> {
                    if (mRestartApp) {
                        mRestartApp = false;
                        MessageHelpers.showLongMessage(getContext(), R.string.msg_restart_app);
                    }
                });
    }

    private void appendNewUi(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(
                UiOptionItem.from(
                        getContext().getString(R.string.new_ui_beta),
                        optionItem -> {
                            mMainUIData.setNewUiEnabled(optionItem.isSelected());
                            mRestartApp = true;
                        },
                        mMainUIData.isNewUiEnabled()));
    }

    /**
     * Backdrop blur and the focus glow are decorative and cost real GPU time on weak hardware, so
     * by default they follow the device. The override exists because that detection is a heuristic
     * and will sometimes be wrong in both directions - a 2-core Android TV emulator looks low-end
     * while being perfectly usable, and some cheap boxes report capable hardware and still
     * struggle.
     *
     * <p>Card previews are deliberately NOT covered here: that is its own explicit setting under
     * Main UI, and a performance heuristic should not quietly override something the user chose.
     *
     * <p>{@code mRestartApp} matters here specifically: {@code AetherTubeTheme} resolves
     * {@code LocalLowEndDevice} once per Activity via {@code remember(context)}, so a change made
     * here has no visible effect in an Activity that was already alive when the user made it -
     * without the restart prompt this looked like the setting silently doing nothing.
     */
    private void appendVisualEffects(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();
        int current = mPrefs.getVisualEffectsMode();

        options.add(
                UiOptionItem.from(
                        "Auto (match device)",
                        option -> {
                            mPrefs.setVisualEffectsMode(AetherTubePrefs.EFFECTS_AUTO);
                            mRestartApp = true;
                        },
                        current == AetherTubePrefs.EFFECTS_AUTO));
        options.add(
                UiOptionItem.from(
                        "Always on",
                        option -> {
                            mPrefs.setVisualEffectsMode(AetherTubePrefs.EFFECTS_ALWAYS);
                            mRestartApp = true;
                        },
                        current == AetherTubePrefs.EFFECTS_ALWAYS));
        options.add(
                UiOptionItem.from(
                        "Always off",
                        option -> {
                            mPrefs.setVisualEffectsMode(AetherTubePrefs.EFFECTS_NEVER);
                            mRestartApp = true;
                        },
                        current == AetherTubePrefs.EFFECTS_NEVER));

        settingsPresenter.appendRadioCategory("Visual effects (blur, glow)", options);
    }

    /**
     * Move a whole configuration between devices by reading out twelve characters.
     *
     * <p>Setting this app up on a TV means walking a D-pad through several dozen options across
     * five settings screens, and doing it again on the next TV, and again after a reinstall. A code
     * is short enough to write on a sticky note and carries the settings that make the difference.
     *
     * <p>Deliberately not a cloud backup: no account, no server, nothing to breach or keep running.
     * The trade is that it holds a curated set rather than everything - see SettingsRegistry.
     */
    private void appendSettingsCode(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from("Show this device's code", option -> showCode()));
        options.add(UiOptionItem.from("Enter a code", option -> promptForCode()));

        settingsPresenter.appendStringsCategory("Transfer settings", options);
    }

    private void showCode() {
        String code = SettingsCode.INSTANCE.encode(SettingsRegistry.INSTANCE.capture(getContext()));

        // Read-only text rather than an edit dialog: this code is to be copied down, and an
        // editable
        // field summons the on-screen keyboard straight over the thing you are trying to read.
        // The code goes in the body, not the category title: the two-pane dialog renders the title
        // as the panel header and the header is already taken by showDialog(), so a code passed
        // there simply never appears on screen.
        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());
        dialog.appendLongTextCategory(
                "Your settings code",
                UiOptionItem.from(
                        code
                                + "\n\n"
                                + "Enter it on the other device under Settings -> AetherTube -> Transfer settings.\n\n"
                                + "It carries "
                                + SettingsRegistry.INSTANCE.getFieldCount()
                                + " settings: video quality, "
                                + "playback and frame-rate behaviour, the player overlay, SponsorBlock, and the "
                                + "interface options.\n\n"
                                + "Not included: your account, subscriptions or watch history."));
        dialog.showDialog("Your settings code");
    }

    private void promptForCode() {
        SimpleEditDialog.show(
                getContext(), "Enter settings code", "XXXX-XXXX-XXXX", "", this::applyCode);
    }

    private boolean applyCode(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        try {
            long payload = SettingsCode.INSTANCE.decode(input);
            int applied = SettingsRegistry.INSTANCE.apply(getContext(), payload);

            mRestartApp = true;
            MessageHelpers.showLongMessage(
                    getContext(), "Applied " + applied + " settings. Restart to finish.");
            return true;
        } catch (SettingsCode.InvalidCodeException e) {
            // The message is written for the person holding the remote, not for a log.
            MessageHelpers.showLongMessage(getContext(), e.getMessage());
            return false;
        }
    }
}
