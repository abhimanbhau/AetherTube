package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.os.Build.VERSION;

import androidx.multidex.MultiDexApplication;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AddDeviceView;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.app.views.WebBrowserView;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.NetworkData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.tv.ui.adddevice.AddDeviceActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.channel.ChannelActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.channeluploads.ChannelUploadsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.AppDialogActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.AppDialogActivityOpaque;
import com.abhimankolte.aethertube.tv.ui.dialog.compose.ComposeAppDialogActivity;
import com.abhimankolte.aethertube.tv.ui.home.compose.ComposeHomeActivity;
import com.abhimankolte.aethertube.tv.ui.home.compose.LegacyBrowseView;
import com.abhimankolte.aethertube.tv.ui.search.compose.ComposeSearchActivity;
import com.abhimankolte.aethertube.tv.ui.settings.compose.ComposeSettingsActivity;
import com.abhimankolte.aethertube.tv.ui.shorts.ShortsPlayerActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.SearchTagsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.signin.SignInActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.webbrowser.WebBrowserActivity;

import android.app.Activity;

import org.conscrypt.Conscrypt;

import java.lang.Thread.UncaughtExceptionHandler;
import java.security.Provider;
import java.security.Security;

public class MainApplication extends MultiDexApplication { // fix: Didn't find class "com.google.firebase.provider.FirebaseInitProvider"
    static {
        // fix youtube bandwidth throttling (best - false)???
        // false is better for streams (less buffering)
        System.setProperty("http.keepAlive", "false");
        // fix ipv6 infinite video buffering???
        // Better to remove this fix at all. Users complain about infinite loading.
        //System.setProperty("java.net.preferIPv6Addresses", "true");
        // Another IPv6 fix (no effect)
        // https://stackoverflow.com/questions/1920623/sometimes-httpurlconnection-getinputstream-executes-too-slowly
        //System.setProperty("java.net.preferIPv4Stack" , "true");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // ByeByeDPI fix
        // https://android-review.googlesource.com/c/platform/external/conscrypt/+/89408/
        // NOTE: Android 10+ (API 29+) uses system Conscrypt TLS; custom Security providers are unnecessary
        // NOTE: May cause 'Unexpected playback error null'
        //if (Build.VERSION.SDK_INT < 29 && Conscrypt.isAvailable()) {
        //    Security.insertProviderAt(Conscrypt.newProvider(), 1);
        //}

        // Important: Initialize the native Conscrypt provider BEFORE reading any configs/SharedPreferences.
        // Otherwise, early disk I/O shifts the ClassLoader on some Android TV devices, causing silent JNI linking errors.
        Provider conscryptProvider = null;
        try {
            conscryptProvider = Conscrypt.newProvider();
        } catch (Throwable e) {
            // UnsatisfiedLinkError
        }

        if (conscryptProvider != null && NetworkData.instance(this).isConscryptEnabled()) {
            try {
                Security.insertProviderAt(conscryptProvider, 1);
            } catch (Throwable e) {
                // UnsatisfiedLinkError
            }
        }

        setupGlobalExceptionHandler();
        setupViewManager();
    }

    private void setupViewManager() {
        ViewManager viewManager = ViewManager.instance(this);

        // New UI (beta) setting (MainUISettingsPresenter, "Look" category) opts into the Compose-for-TV
        // Home/Search/Dialogs rewrite. Off by default: everyone keeps the original leanback experience
        // until they explicitly turn it on (requires an app restart, same as other routing-affecting settings).
        boolean newUiEnabled = MainUIData.instance(this).isNewUiEnabled();
        Class<? extends Activity> homeActivityClass = newUiEnabled ? ComposeHomeActivity.class : BrowseActivity.class;
        Class<? extends Activity> searchActivityClass = newUiEnabled ? ComposeSearchActivity.class : SearchTagsActivity.class;

        Class<? extends Activity> dialogClazz;

        if (newUiEnabled) {
            // AppDialogView is shared app-wide (settings, context menus, confirmations - anything that
            // goes through AppDialogPresenter), not just the Settings screen. See ComposeAppDialogFragment
            // for the two-pane master-detail redesign and its (rare) chat/comments fallback.
            dialogClazz = ComposeAppDialogActivity.class;
        } else if (VERSION.SDK_INT == 26
                && Helpers.equalsAny(Helpers.getCrashlyticsDeviceName(), "4S806_Z51S1 (Panasonic)")) {
            // The fix: Only fullscreen opaque activities can request orientation
            dialogClazz = AppDialogActivityOpaque.class;
        } else {
            dialogClazz = AppDialogActivity.class;
        }

        viewManager.setRoot(homeActivityClass);
        viewManager.register(SplashView.class, SplashActivity.class); // no parent, because it's root activity
        viewManager.register(BrowseView.class, homeActivityClass); // no parent, because it's root activity
        viewManager.register(PlaybackView.class, PlaybackActivity.class, homeActivityClass);
        viewManager.register(AppDialogView.class, dialogClazz, homeActivityClass);
        viewManager.register(SearchView.class, searchActivityClass, homeActivityClass);
        viewManager.register(SignInView.class, SignInActivity.class, homeActivityClass);
        viewManager.register(AddDeviceView.class, AddDeviceActivity.class, homeActivityClass);
        viewManager.register(ChannelView.class, ChannelActivity.class, homeActivityClass);
        viewManager.register(ChannelUploadsView.class, ChannelUploadsActivity.class, homeActivityClass);
        viewManager.register(WebBrowserView.class, WebBrowserActivity.class, homeActivityClass);

        if (newUiEnabled) {
            // BrowseActivity is launched directly (not via ViewManager.startView) as ComposeHomeFragment's
            // fallback for section types not ported to Compose yet; this registration only exists so
            // ViewManager knows its parent and doesn't clear the back-stack on resume (see LegacyBrowseView).
            viewManager.register(LegacyBrowseView.class, BrowseActivity.class, homeActivityClass);

            // Same story for the dedicated Settings screen: launched directly via startActivity() from
            // Home's gear icon (not through ViewManager.startView), so without this registration
            // ViewManager treats it as a parent-less root screen on resume and Back triggers the
            // app-exit double-back prompt instead of returning to Home.
            viewManager.register(ComposeSettingsActivity.class, ComposeSettingsActivity.class, homeActivityClass);

            // And the same again for the vertical Shorts feed. It can't go through
            // ViewManager.startView(PlaybackView.class) - that mapping belongs to the standard 16:9
            // player - so it's launched directly and registered here purely to give it a parent.
            viewManager.register(ShortsPlayerActivity.class, ShortsPlayerActivity.class, homeActivityClass);
        }
    }

    private void setupGlobalExceptionHandler() {
        UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();

        if (defaultHandler == null) {
            return;
        }

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            if (shouldIgnore(e)) {
                return;
            }

            applyCrashFixes(e);
            //e = wrapWithAdditionalInfo(e);

            defaultHandler.uncaughtException(t, e);
        });
    }

    private boolean shouldIgnore(Throwable e) {
        if (Helpers.containsAny(e.getMessage(), "KatnissVoiceInteractionService", "ListenableFuture", "Missing android.support.FILE_PROVIDER_PATHS meta-data")
                || e.getClass().getName().startsWith("org.chromium")) {
            // IllegalStateException: Not allowed to start service Intent { act=android.service.voice.VoiceInteractionService
            // cmp=com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService (has extras) }:
            // app is in background uid UidRecord{40e7240 u0a19 CEM idle change:cached procs:1 seq(0,0,0)}

            // java.lang.NoSuchMethodError: No interface method addListener(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)V
            // in class Lcom/google/common/util/concurrent/ListenableFuture; or its super classes
            // (declaration of 'com.google.common.util.concurrent.ListenableFuture' appears in /system/framework/libsetting.jar)

            // Fatal Exception: org.chromium.base.JniAndroid$UncaughtExceptionException
            // 1) Caused by java.lang.InterruptedException
            // 2) Caused by java.lang.SecurityException: The calling process has already registered an InputDevicesChangedListener.

            // IllegalArgumentException: Missing android.support.FILE_PROVIDER_PATHS meta-data (Shield Android TV 95%, MiTV-AFKR0 5%)
            return true;
        }

        return false;
    }

    private Throwable wrapWithAdditionalInfo(Throwable e) {
        if (Helpers.equalsAny(e.getMessage(),
                "parameter must be a descendant of this view",
                "Attempt to invoke virtual method 'android.view.ViewGroup$LayoutParams android.view.View.getLayoutParams()' on a null object reference")) {
            Class<?> view = ViewManager.instance(getApplicationContext()).getTopView();
            BrowseSection section = null;

            if (view == BrowseView.class) {
                section = BrowsePresenter.instance(getApplicationContext()).getCurrentSection();
            }

            e = new RuntimeException("A crash in the view " + view.getSimpleName() + ", section id " + (section != null ? section.getId() : "-1"), e);
        }
        return e;
    }

    private void applyCrashFixes(Throwable e) {
        if (e instanceof OutOfMemoryError || e.getCause() instanceof OutOfMemoryError) {
            Class<?> view = ViewManager.instance(getApplicationContext()).getTopView();
            if (view == PlaybackView.class) {
                PlayerTweaksData tweaksData = PlayerTweaksData.instance(getApplicationContext());
                PlayerData playerData = PlayerData.instance(getApplicationContext());
                int playerDataSource = tweaksData.getPlayerDataSource();
                int videoBufferType = playerData.getVideoBufferType();
                if (playerDataSource == PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP) {
                    tweaksData.setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT);
                    tweaksData.persistNow();
                } else if (videoBufferType == PlayerData.BUFFER_HIGH || videoBufferType == PlayerData.BUFFER_HIGHEST) {
                    playerData.setVideoBufferType(PlayerData.BUFFER_MEDIUM);
                    playerData.persistNow();
                }
            }
        }
    }
}
