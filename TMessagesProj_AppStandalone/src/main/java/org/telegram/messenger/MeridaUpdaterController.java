package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.merida.MeridaBuild;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.web.HttpGetFileTask;
import org.telegram.ui.web.HttpGetTask;
import org.telegram.utils.settings.MeridaSettings;

import java.io.File;

/**
 * MeridaGram updates, like exteraGram's: every build is published on GitHub as the release «build-N» with the APK.
 * This checks the latest release, and when its number is higher than this build's, the bottom bar of the main
 * screen offers «Update MeridaGram» → «Downloading N%» → «Update now» (Telegram's own custom-update bar). The
 * file is downloaded from GitHub; nothing about the user is sent anywhere.
 * Based on Telegram's BetaUpdaterController.
 */
public class MeridaUpdaterController {

    private static final String RELEASES_URL = "https://api.github.com/repos/hikingerop/MeridaGram/releases/latest";

    private static MeridaUpdaterController instance;

    public static MeridaUpdaterController getInstance() {
        if (instance == null) {
            instance = new MeridaUpdaterController();
        }
        return instance;
    }

    /** This build's number (from the build workflow); 0 — a local build, which never updates itself. */
    public static int currentBuild() {
        try {
            return Integer.parseInt(MeridaBuild.NUMBER);
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean enabled() {
        return currentBuild() > 0 && MeridaSettings.githubUpdates.get();
    }

    private int build;
    private String changelog;
    private String path;
    private String fileUrl;
    private long lastCheck;

    private MeridaUpdaterController() {
        final SharedPreferences prefs = prefs();
        build = prefs.getInt("build", 0);
        changelog = prefs.getString("changelog", null);
        path = prefs.getString("path", null);
        fileUrl = prefs.getString("fileUrl", null);
        lastCheck = prefs.getLong("lastCheck", 0);
        if (build <= currentBuild() || !TextUtils.isEmpty(path) && !new File(path).exists()) {
            forget();
        }
    }

    private SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("merida_updates", Activity.MODE_PRIVATE);
    }

    private void save() {
        prefs().edit()
                .putInt("build", build)
                .putString("changelog", changelog)
                .putString("path", path)
                .putString("fileUrl", fileUrl)
                .putLong("lastCheck", lastCheck)
                .apply();
    }

    private void deleteFile() {
        if (!TextUtils.isEmpty(path)) {
            try {
                new File(path).delete();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        path = null;
    }

    private void forget() {
        deleteFile();
        build = 0;
        changelog = null;
        fileUrl = null;
        save();
    }

    private static final long CHECK_INTERVAL = 1000L * 60 * 60; // the GitHub API allows 60 requests an hour

    private boolean firstCheck = true;
    private boolean checking;
    private final Runnable scheduledCheck = () -> checkForUpdate(false, null);

    public void checkForUpdate(boolean force, Runnable whenDone) {
        if (!enabled()) {
            if (build != 0) forget();
            if (whenDone != null) whenDone.run();
            return;
        }
        if (checking) return;
        if (firstCheck) force = true;
        if (!force && System.currentTimeMillis() - lastCheck < CHECK_INTERVAL) {
            if (whenDone != null) whenDone.run();
            return;
        }
        checking = true;
        firstCheck = false;
        new HttpGetTask(str -> AndroidUtilities.runOnUIThread(() -> {
            checking = false;
            final int oldBuild = build;
            try {
                final JSONObject json = new JSONObject(str);
                final String tag = json.optString("tag_name", "");
                final int remote = tag.startsWith("build-") ? Integer.parseInt(tag.substring(6)) : 0;
                String url = null;
                final JSONArray assets = json.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        final JSONObject a = assets.getJSONObject(i);
                        if (a.optString("name", "").endsWith(".apk")) {
                            url = a.optString("browser_download_url", null);
                            break;
                        }
                    }
                }
                if (remote > currentBuild() && url != null) {
                    if (remote != build) {
                        deleteFile(); // an older downloaded build is no longer wanted
                    }
                    build = remote;
                    fileUrl = url;
                    changelog = json.optString("body", null);
                } else {
                    forget();
                }
                lastCheck = System.currentTimeMillis();
                save();
            } catch (Exception e) {
                FileLog.e("MeridaGram update check failed: " + str, e);
            }
            if (build != oldBuild) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
            }
            AndroidUtilities.cancelRunOnUIThread(scheduledCheck);
            AndroidUtilities.runOnUIThread(scheduledCheck, CHECK_INTERVAL);
            if (whenDone != null) {
                whenDone.run();
            } else if (build != oldBuild && build != 0 && !ApplicationLoader.mainInterfacePaused) {
                final Context context = LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext;
                final BetaUpdate update = getUpdate();
                if (context != null && update != null) {
                    ApplicationLoader.applicationLoaderInstance.showCustomUpdateAppPopup(context, update, UserConfig.selectedAccount);
                }
            }
        })).setHeader("Accept", "application/vnd.github+json").execute(RELEASES_URL);
    }

    public BetaUpdate getUpdate() {
        if (!enabled() || build <= currentBuild()) return null;
        return new BetaUpdate("MeridaGram " + build, build, changelog);
    }

    private boolean downloading;
    private float progress;
    private HttpGetFileTask task;

    public void downloadUpdate() {
        if (downloading || getDownloadedFile() != null || TextUtils.isEmpty(fileUrl)) return;
        downloading = true;
        progress = 0;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateLoading);
        task = new HttpGetFileTask(
                file -> AndroidUtilities.runOnUIThread(() -> {
                    downloading = false;
                    if (file != null) {
                        deleteFile();
                        path = file.getAbsolutePath();
                        progress = 1;
                        save();
                    }
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
                }),
                p -> {
                    progress = p;
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateLoading);
                }
        ).setOverrideExtension("apk");
        task.execute(fileUrl);
    }

    public void cancelDownloadingUpdate() {
        if (!downloading) return;
        if (task != null) task.cancel(false);
        downloading = false;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    public boolean isDownloading() {
        return downloading;
    }

    public float getDownloadingProgress() {
        return progress;
    }

    public File getDownloadedFile() {
        if (path == null) return null;
        final File f = new File(path);
        if (!f.exists()) {
            path = null;
            save();
            return null;
        }
        return f;
    }
}
