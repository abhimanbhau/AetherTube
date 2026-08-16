package com.abhimankolte.aethertube.common.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.GeneralSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.MainUISettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.PlayerSettingsPresenter;

/**
 * Landing menu for settings that are real but obscure - per-device firmware compatibility fixes,
 * per-button/per-menu-item visibility toggles, and other things almost nobody customizes item by
 * item. Mirrors the main Settings categories (General, Main UI, Player) so nothing moved here is
 * harder to *find*, it's just out of the way of the everyday settings flow.
 *
 * Content Block (SponsorBlock) has no entry here - its per-segment-type action/color settings moved
 * back into the main Content Block screen, since SponsorBlock is one of the most-used settings areas
 * and hiding that behind a second menu made it harder to find, not easier.
 *
 * Each entry just calls the same category's existing {@code showLegacy()} method - no settings
 * logic lives here, this is purely a second, less-visible front door to it.
 */
public class LegacySettingsPresenter extends BasePresenter<Void> {
    private LegacySettingsPresenter(Context context) {
        super(context);
    }

    public static LegacySettingsPresenter instance(Context context) {
        return new LegacySettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        settingsPresenter.appendSingleButton(button(R.string.settings_general, () -> GeneralSettingsPresenter.instance(getContext()).showLegacy()));
        settingsPresenter.appendSingleButton(button(R.string.settings_main_ui, () -> MainUISettingsPresenter.instance(getContext()).showLegacy()));
        settingsPresenter.appendSingleButton(button(R.string.settings_player, () -> PlayerSettingsPresenter.instance(getContext()).showLegacy()));

        settingsPresenter.showDialog(getContext().getString(R.string.settings_legacy));
    }

    private OptionItem button(int titleResId, Runnable onClick) {
        return UiOptionItem.from(getContext().getString(titleResId), optionItem -> onClick.run());
    }
}
