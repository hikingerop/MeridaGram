package org.telegram.messenger.merida;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.widget.Toast;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.utils.settings.MeridaSettings;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MeridaGram plugins (stage 1): Python files with the .plugin extension, run by the Python built into the
 * app (Chaquopy). A plugin is installed by tapping a .plugin file in any chat, and switched on or off in
 * MeridaGram Settings → Plugins. The Python side is src/main/python/merida_plugins.py.
 */
public final class MeridaPlugins {

    public static final String EXTENSION = ".plugin";
    private static final int MAX_FILE_SIZE = 2 * 1024 * 1024;
    private static final Pattern META = Pattern.compile("^__(id|name|description|author|version)__\\s*=\\s*([\"'])(.*?)\\2\\s*$", Pattern.MULTILINE);
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_]{1,64}");

    public static final class Info {
        public String id;
        public String name;
        public String description = "";
        public String author = "";
        public String version = "";
        public File file;
    }

    private static final Object lock = new Object();
    private static final HashMap<String, String> errors = new HashMap<>();
    private static volatile boolean started;
    private static volatile boolean sendHooks;
    /** Called on the UI thread after plugins were (re)loaded, so the settings page can refresh. */
    public static Runnable listener;

    private MeridaPlugins() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("merida_plugins", Context.MODE_PRIVATE);
    }

    private static File dir() {
        final File dir = new File(ApplicationLoader.getFilesDirFixed(), "merida_plugins");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    // ---- list and metadata ------------------------------------------------------------------

    public static boolean isFile(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(EXTENSION);
    }

    /** Reads the plugin's name, author and so on without running its code; null when it is not a plugin. */
    public static Info readInfo(File file) {
        if (file == null || !file.isFile() || file.length() > MAX_FILE_SIZE) {
            return null;
        }
        final String text;
        try (InputStream in = new FileInputStream(file)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            text = out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
        final Info info = new Info();
        final Matcher m = META.matcher(text);
        while (m.find()) {
            final String key = m.group(1);
            final String value = m.group(3);
            if ("id".equals(key)) {
                info.id = value;
            } else if ("name".equals(key)) {
                info.name = value;
            } else if ("description".equals(key)) {
                info.description = value;
            } else if ("author".equals(key)) {
                info.author = value;
            } else if ("version".equals(key)) {
                info.version = value;
            }
        }
        if (info.id == null || !VALID_ID.matcher(info.id).matches()) {
            return null;
        }
        if (TextUtils.isEmpty(info.name)) {
            info.name = info.id;
        }
        info.file = file;
        return info;
    }

    public static List<Info> list() {
        final ArrayList<Info> result = new ArrayList<>();
        final File[] files = dir().listFiles();
        if (files != null) {
            for (File f : files) {
                if (isFile(f.getName())) {
                    final Info info = readInfo(f);
                    if (info != null) {
                        result.add(info);
                    }
                }
            }
        }
        Collections.sort(result, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }

    public static boolean isEnabled(String id) {
        return prefs().getBoolean("on_" + id, true);
    }

    public static void setEnabled(String id, boolean on) {
        prefs().edit().putBoolean("on_" + id, on).apply();
        reloadAsync();
    }

    /** The last error of this plugin (when loading it failed), or null. */
    public static String error(String id) {
        synchronized (lock) {
            return errors.get(id);
        }
    }

    // ---- install and remove -----------------------------------------------------------------

    public static Info install(File source) {
        final Info info = readInfo(source);
        if (info == null) {
            return null;
        }
        final File target = new File(dir(), info.id + EXTENSION);
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(target)) {
            final byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
        prefs().edit().putBoolean("on_" + info.id, true).apply();
        info.file = target;
        reloadAsync();
        return info;
    }

    public static void remove(String id) {
        new File(dir(), id + EXTENSION).delete();
        prefs().edit().remove("on_" + id).apply();
        reloadAsync();
    }

    /** Shown when a .plugin file is tapped in a chat: what the plugin is, a warning, and «Install». */
    public static void askInstall(Activity activity, File file) {
        if (activity == null) {
            return;
        }
        final Info info = readInfo(file);
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        if (info == null) {
            builder.setTitle(LocaleController.getString(R.string.MeridaPluginInstallTitle));
            builder.setMessage(LocaleController.getString(R.string.MeridaPluginNotPlugin));
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
            builder.show();
            return;
        }
        final SpannableStringBuilder text = new SpannableStringBuilder();
        text.append(AndroidUtilities.replaceTags("**" + info.name + "**"));
        if (!TextUtils.isEmpty(info.version)) {
            text.append(" ").append(info.version);
        }
        if (!TextUtils.isEmpty(info.author)) {
            text.append(" · ").append(info.author);
        }
        if (!TextUtils.isEmpty(info.description)) {
            text.append("\n\n").append(info.description);
        }
        text.append("\n\n").append(LocaleController.getString(R.string.MeridaPluginWarning));
        if (!MeridaSettings.pluginsEnabled.get()) {
            text.append("\n\n").append(LocaleController.getString(R.string.MeridaPluginsOffHint));
        }
        builder.setTitle(LocaleController.getString(R.string.MeridaPluginInstallTitle));
        builder.setMessage(text);
        builder.setPositiveButton(LocaleController.getString(R.string.MeridaPluginInstall), (dialog, which) -> {
            final Info installed = install(file);
            Toast.makeText(activity, installed != null
                    ? LocaleController.formatString(R.string.MeridaPluginInstalled, installed.name)
                    : LocaleController.getString(R.string.MeridaPluginInstallFailed), Toast.LENGTH_LONG).show();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    // ---- running ----------------------------------------------------------------------------

    /** Called once at app start: loads the switched-on plugins in the background when plugins are on. */
    public static void init() {
        if (MeridaSettings.pluginsEnabled.get()) {
            reloadAsync();
        }
    }

    public static void reloadAsync() {
        Utilities.globalQueue.postRunnable(MeridaPlugins::reload);
    }

    private static PyObject runtime() {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(ApplicationLoader.applicationContext));
        }
        started = true;
        return Python.getInstance().getModule("merida_plugins");
    }

    private static void reload() {
        synchronized (lock) {
            try {
                if (!MeridaSettings.pluginsEnabled.get()) {
                    sendHooks = false;
                    if (started) {
                        runtime().callAttr("unload_all");
                    }
                    errors.clear();
                } else {
                    final PyObject rt = runtime();
                    rt.callAttr("unload_all");
                    errors.clear();
                    for (Info info : list()) {
                        if (!isEnabled(info.id)) {
                            continue;
                        }
                        final PyObject result = rt.callAttr("load", info.id, info.file.getAbsolutePath());
                        final String error = result == null ? "" : result.toString();
                        if (!error.isEmpty()) {
                            errors.put(info.id, error);
                        }
                    }
                    sendHooks = rt.callAttr("has_send_hooks").toBoolean();
                }
            } catch (Throwable e) {
                FileLog.e(e);
                sendHooks = false;
            }
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (listener != null) {
                listener.run();
            }
        });
    }

    public static boolean hasSendHooks() {
        return started && sendHooks && MeridaSettings.pluginsEnabled.get();
    }

    /**
     * Passes a text message through the plugins before it is sent. Returns the text to send, or null
     * when a plugin asked not to send it. If anything goes wrong, the original text is sent.
     */
    public static String onSendMessage(String text, long dialogId) {
        if (!hasSendHooks() || text == null) {
            return text;
        }
        try {
            final PyObject result = Python.getInstance().getModule("merida_plugins").callAttr("on_send_message", text, dialogId);
            final String changed = result == null ? text : result.toString();
            return changed.isEmpty() ? null : changed;
        } catch (Throwable e) {
            FileLog.e(e);
            return text;
        }
    }
}
