package org.telegram.messenger;

import static org.telegram.messenger.AndroidUtilities.isInAirplaneMode;
import static org.telegram.ui.PremiumPreviewFragment.applyNewSpan;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.view.ViewGroup;

import androidx.core.content.FileProvider;

import org.json.JSONObject;
import org.telegram.messenger.web.BuildConfig;
import org.telegram.messenger.web.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.UpdateAppAlertDialog;
import org.telegram.ui.Components.UpdateLayout;
import org.telegram.ui.IUpdateLayout;
import org.telegram.ui.LaunchActivity;

import java.io.File;

public class ApplicationLoaderImpl extends ApplicationLoader {
    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    @Override
    protected boolean isStandalone() {
        return true;
    }

    @Override
    protected void startAppCenterInternal(Activity context) {

    }

    @Override
    protected void checkForUpdatesInternal() {

    }

    protected void appCenterLogInternal(Throwable e) {

    }

    protected void logDualCameraInternal(boolean success, boolean vendor) {

    }

    @Override
    public boolean checkApkInstallPermissions(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ApplicationLoader.applicationContext.getPackageManager().canRequestPackageInstalls()) {
            AlertsCreator.createApkRestrictedDialog(context, null).show();
            return false;
        }
        return true;
    }

    @Override
    public boolean openApkInstall(Activity activity, TLRPC.Document document) {
        boolean exists = false;
        try {
            String fileName = FileLoader.getAttachFileName(document);
            File f = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(document, true);
            if (exists = f.exists()) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                if (Build.VERSION.SDK_INT >= 24) {
                    intent.setDataAndType(FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", f), "application/vnd.android.package-archive");
                } else {
                    intent.setDataAndType(Uri.fromFile(f), "application/vnd.android.package-archive");
                }
                try {
                    activity.startActivityForResult(intent, 500);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return exists;
    }

    @Override
    public boolean showUpdateAppPopup(Context context, TLRPC.TL_help_appUpdate update, int account) {
        try {
            (new UpdateAppAlertDialog(context, update, account)).show();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }

    @Override
    public IUpdateLayout takeUpdateLayout(Activity activity, ViewGroup sideMenuContainer) {
        // MeridaGram: the bottom «Update MeridaGram / Downloading N%» bar for builds from GitHub
        if (isCustomUpdate()) {
            return new org.telegram.ui.Components.MeridaUpdateLayout(activity, sideMenuContainer);
        }
        return new UpdateLayout(activity, sideMenuContainer);
    }

    // ---- MeridaGram updates from GitHub releases (MeridaUpdaterController), like exteraGram's updater ----

    @Override
    public boolean isCustomUpdate() {
        return MeridaUpdaterController.enabled();
    }

    @Override
    public BetaUpdate getUpdate() {
        return isCustomUpdate() ? MeridaUpdaterController.getInstance().getUpdate() : null;
    }

    @Override
    public void checkUpdate(boolean force, Runnable whenDone) {
        MeridaUpdaterController.getInstance().checkForUpdate(force, whenDone);
    }

    @Override
    public void downloadUpdate() {
        if (isCustomUpdate()) MeridaUpdaterController.getInstance().downloadUpdate();
    }

    @Override
    public void cancelDownloadingUpdate() {
        if (isCustomUpdate()) MeridaUpdaterController.getInstance().cancelDownloadingUpdate();
    }

    @Override
    public boolean isDownloadingUpdate() {
        return isCustomUpdate() && MeridaUpdaterController.getInstance().isDownloading();
    }

    @Override
    public float getDownloadingUpdateProgress() {
        return isCustomUpdate() ? MeridaUpdaterController.getInstance().getDownloadingProgress() : 0;
    }

    @Override
    public File getDownloadedUpdateFile() {
        return isCustomUpdate() ? MeridaUpdaterController.getInstance().getDownloadedFile() : null;
    }

    @Override
    public boolean showCustomUpdateAppPopup(Context context, BetaUpdate update, int account) {
        // the window once per new build; after that only the bottom bar reminds about it
        final android.content.SharedPreferences prefs = context.getSharedPreferences("merida_updates", Context.MODE_PRIVATE);
        if (update == null || prefs.getInt("shownBuild", 0) == update.versionCode) return true;
        prefs.edit().putInt("shownBuild", update.versionCode).apply();
        try {
            new org.telegram.ui.Components.MeridaUpdateAppAlertDialog(context, update, account).show();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }
}
