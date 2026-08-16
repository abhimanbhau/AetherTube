package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.DeArrowData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class DeArrowSettingsPresenter extends BasePresenter<Void> {
    private final MainUIData mMainUIData;
    private final DeArrowData mDeArrowData;

    private DeArrowSettingsPresenter(Context context) {
        super(context);
        mMainUIData = MainUIData.instance(context);
        mDeArrowData = DeArrowData.instance(context);
    }

    public static DeArrowSettingsPresenter instance(Context context) {
        return new DeArrowSettingsPresenter(context);
    }

    public void show(Runnable onFinish) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendSwitches(settingsPresenter);
        appendLinks(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.dearrow_provider), onFinish);
    }

    public void show() {
        show(null);
    }

    private void appendSwitches(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.crowdsoursed_titles),
                optionItem -> {
                    mDeArrowData.setReplaceTitlesEnabled(optionItem.isSelected());
                    mMainUIData.setUnlocalizedTitlesEnabled(false);
                },
                mDeArrowData.isReplaceTitlesEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.crowdsourced_thumbnails),
                optionItem -> mDeArrowData.setReplaceThumbnailsEnabled(optionItem.isSelected()),
                mDeArrowData.isReplaceThumbnailsEnabled()));

        for (OptionItem item : options) {
            settingsPresenter.appendSingleSwitch(item);
        }
    }

    private void appendLinks(AppDialogPresenter settingsPresenter) {
        OptionItem statsCheckOption = UiOptionItem.from(getContext().getString(R.string.dearrow_status),
                option -> Utils.openLink(getContext(), getContext().getString(R.string.dearrow_status_url)));

        OptionItem webSiteOption = UiOptionItem.from(getContext().getString(R.string.about_dearrow),
                option -> Utils.openLink(getContext(), getContext().getString(R.string.dearrow_provider_url)));

        settingsPresenter.appendSingleButton(statsCheckOption);
        settingsPresenter.appendSingleButton(webSiteOption);
    }
}
