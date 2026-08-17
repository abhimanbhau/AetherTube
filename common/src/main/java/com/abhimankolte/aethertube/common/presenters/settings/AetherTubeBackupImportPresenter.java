package com.abhimankolte.aethertube.common.presenters.settings;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;

import com.liskovsoft.googleapi.oauth2.impl.GoogleSignInService;
import com.liskovsoft.googleapi.service.DriveService;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.GoogleSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.ZipHelper;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Restores a Google Drive backup that was written under SmartTube's own "SmartTubeBackup" root -
 * upstream's own {@code GDriveBackupManager} only ever looks under "AetherTubeBackup" (this
 * fork's rename, commit 034bffe0), so a backup made in SmartTube is invisible to it even though
 * both apps share the exact same OAuth client and Drive scope (verified: {@code drive.file}, not
 * the app-scoped {@code appDataFolder}).
 *
 * <p>No equivalent needed for a local (non-Drive) SmartTube backup file: "Open with" from any
 * file manager already hands a zip straight to {@code BackupReceiverActivity} (upstream, already
 * wired) regardless of where it sits, so there was nothing left to add there.
 *
 * <p>Standalone reimplementation rather than a call into {@code GDriveBackupManager}: its restore
 * pipeline (folder browse -> download -> unzip -> restart) is entirely private with no way to
 * point it at a different root folder, so reaching it would mean editing that upstream file.
 * Built from the same public {@code DriveService}/{@code ZipHelper} building blocks it uses
 * internally, so it tracks the same backup format.
 */
public class AetherTubeBackupImportPresenter {
    private static final String SMARTTUBE_BACKUP_ROOT = "SmartTubeBackup";
    private static final String BACKUP_NAME = "backup.zip";
    private final Context mContext;
    private Disposable mAction;

    private AetherTubeBackupImportPresenter(Context context) {
        mContext = context;
    }

    public static AetherTubeBackupImportPresenter instance(Context context) {
        return new AetherTubeBackupImportPresenter(context);
    }

    public void appendOptions(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(
                UiOptionItem.from(
                        "Import from Google Drive (SmartTube)...",
                        "Restore a backup SmartTube saved to its own Google Drive folder",
                        option -> restoreFromDrive()));
    }

    private void restoreFromDrive() {
        if (RxHelper.isAnyActionRunning(mAction)) {
            MessageHelpers.showMessage(mContext, R.string.wait_data_loading);
            return;
        }

        if (GoogleSignInService.instance().isSigned()) {
            showFolderChooser();
        } else {
            GoogleSignInPresenter.instance(mContext).start(this::showFolderChooser);
        }
    }

    private void showFolderChooser() {
        mAction = DriveService.getFolderList(Uri.parse(SMARTTUBE_BACKUP_ROOT))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showFolderSelectorDialog,
                        error -> MessageHelpers.showLongMessage(mContext, error.getMessage()));
    }

    private void showFolderSelectorDialog(List<String> backups) {
        if (backups == null || backups.isEmpty()) {
            MessageHelpers.showLongMessage(mContext, R.string.nothing_found);
            return;
        }

        AppDialogPresenter dialog = AppDialogPresenter.instance(mContext);
        List<OptionItem> options = new ArrayList<>();

        for (String name : backups) {
            options.add(UiOptionItem.from(name, optionItem ->
                    AppDialogUtil.showConfirmationDialog(mContext, mContext.getString(R.string.app_restore),
                            () -> restoreFolder(name))));
        }

        dialog.appendStringsCategory(mContext.getString(R.string.app_restore), options);
        dialog.showDialog();
    }

    private void restoreFolder(String name) {
        String backupDir = String.format("%s/%s", SMARTTUBE_BACKUP_ROOT, name);

        MessageHelpers.showLongMessage(mContext, mContext.getString(R.string.app_restore));

        mAction = DriveService.getFile(Uri.parse(String.format("%s/%s", backupDir, BACKUP_NAME)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(inputStream -> {
                    File zipFile = new File(mContext.getCacheDir(), BACKUP_NAME);
                    FileHelpers.copy(inputStream, zipFile);

                    String rootDir = mContext.getApplicationInfo().dataDir;
                    File sharedPrefs = new File(rootDir, "shared_prefs");
                    File filesDir = new File(rootDir, "files");

                    FileHelpers.delete(sharedPrefs);
                    FileHelpers.delete(filesDir);

                    if (ZipHelper.hasDirectories(zipFile)) { // new format: /files /shared_prefs
                        ZipHelper.unzipToFolder(zipFile, new File(rootDir));
                    } else { // old format: only xml files
                        ZipHelper.unzipToFolder(zipFile, sharedPrefs);
                    }

                    fixFileNames(sharedPrefs);

                    // Don't soft-restart: stale in-memory prefs would only half-apply the
                    // restored data. Every other restore path here does the same.
                    new Handler(mContext.getMainLooper()).postDelayed(() -> Runtime.getRuntime().exit(0), 1_000);
                }, error -> MessageHelpers.showLongMessage(mContext, error.getMessage()),
                        () -> MessageHelpers.showMessage(mContext, R.string.msg_done));
    }

    /** Renames *_preferences.xml from SmartTube's package name to this app's. */
    private void fixFileNames(File dataDir) {
        Collection<File> files = FileHelpers.listFileTree(dataDir);

        String suffix = "_preferences.xml";
        String targetName = mContext.getPackageName() + suffix;

        for (File file : files) {
            if (file.getName().endsWith(suffix) && !file.getName().endsWith(targetName)) {
                FileHelpers.copy(file, new File(file.getParentFile(), targetName));
                FileHelpers.delete(file);
            }
        }
    }
}
