package org.telegram.messenger.merida;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.utils.settings.MeridaSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * «Backup» of the «Visual» section like Chimera's: snapshots of the visual gifts, usernames, numbers, rating,
 * balance and worn NFT, kept in the app's own folder on this phone (nothing leaves the phone). Up to 20 copies.
 */
public final class MeridaVisualBackup {

    private static final int MAX = 20;
    private static final String[] PREFS = {"merida_visual_gifts", "merida_visual_meta"};

    private MeridaVisualBackup() {
    }

    private static File dir() {
        final File d = new File(ApplicationLoader.applicationContext.getFilesDir(), "merida_visual_backups");
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    /** Copies, newest first; the file name is the time in ms. */
    public static List<File> list() {
        final File[] files = dir().listFiles((f, name) -> name.endsWith(".json"));
        final ArrayList<File> out = new ArrayList<>(files == null ? new ArrayList<>() : Arrays.asList(files));
        out.sort((a, b) -> Long.compare(time(b), time(a)));
        return out;
    }

    public static long time(File f) {
        try {
            return Long.parseLong(f.getName().replace(".json", ""));
        } catch (Exception e) {
            return f.lastModified();
        }
    }

    /** A new copy unless the newest one is younger than minAgeMs. Returns true when one was written. */
    public static boolean snapshot(long minAgeMs) {
        final List<File> all = list();
        if (!all.isEmpty() && System.currentTimeMillis() - time(all.get(0)) < minAgeMs) {
            return false;
        }
        try {
            final JSONObject root = new JSONObject();
            for (String name : PREFS) {
                final JSONObject p = new JSONObject();
                for (Map.Entry<String, ?> e : prefs(name).getAll().entrySet()) {
                    final Object v = e.getValue();
                    final JSONObject item = new JSONObject();
                    item.put("t", v instanceof Boolean ? "b" : v instanceof Integer ? "i" : v instanceof Long ? "l" : v instanceof Float ? "f" : "s");
                    item.put("v", String.valueOf(v));
                    p.put(e.getKey(), item);
                }
                root.put(name, p);
            }
            final JSONObject st = new JSONObject();
            st.put("stars", MeridaSettings.visualStars.get());
            st.put("grams", MeridaSettings.visualGrams.get());
            st.put("username", MeridaSettings.visualUsername.get());
            st.put("usernames", MeridaSettings.visualUsernames.get());
            st.put("number", MeridaSettings.visualNumber.get());
            st.put("numbers", MeridaSettings.visualNumbers.get());
            st.put("rating", MeridaSettings.visualRating.get());
            st.put("ratingPoints", MeridaSettings.visualRatingPoints.get());
            st.put("worn", MeridaSettings.visualWorn.get());
            st.put("premium", MeridaSettings.visualPremium.get());
            root.put("settings", st);
            final File f = new File(dir(), System.currentTimeMillis() + ".json");
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(root.toString().getBytes("UTF-8"));
            }
            final List<File> now = list();
            for (int i = MAX; i < now.size(); i++) {
                now.get(i).delete();
            }
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    /** How many gifts a copy holds (for the list). */
    public static int giftCount(File f) {
        try {
            final JSONObject p = read(f).optJSONObject("merida_visual_gifts");
            if (p == null || !p.has("slugs")) return 0;
            final String all = p.getJSONObject("slugs").getString("v");
            return all.isEmpty() ? 0 : all.split("\n").length;
        } catch (Throwable e) {
            return 0;
        }
    }

    public static boolean restore(File f) {
        try {
            final JSONObject root = read(f);
            for (String name : PREFS) {
                final JSONObject p = root.optJSONObject(name);
                if (p == null) continue;
                final SharedPreferences.Editor ed = prefs(name).edit().clear();
                final java.util.Iterator<String> keys = p.keys();
                while (keys.hasNext()) {
                    final String k = keys.next();
                    final JSONObject item = p.getJSONObject(k);
                    final String v = item.getString("v");
                    switch (item.getString("t")) {
                        case "b": ed.putBoolean(k, Boolean.parseBoolean(v)); break;
                        case "i": ed.putInt(k, Integer.parseInt(v)); break;
                        case "l": ed.putLong(k, Long.parseLong(v)); break;
                        case "f": ed.putFloat(k, Float.parseFloat(v)); break;
                        default: ed.putString(k, v); break;
                    }
                }
                ed.commit();
            }
            final JSONObject st = root.optJSONObject("settings");
            if (st != null) {
                MeridaSettings.visualStars.set(st.optInt("stars"));
                MeridaSettings.visualGrams.set(st.optInt("grams"));
                MeridaSettings.visualUsername.set(str(st, "username"));
                MeridaSettings.visualUsernames.set(str(st, "usernames"));
                MeridaSettings.visualNumber.set(str(st, "number"));
                MeridaSettings.visualNumbers.set(str(st, "numbers"));
                MeridaSettings.visualRating.set(st.optInt("rating"));
                MeridaSettings.visualRatingPoints.set(st.optInt("ratingPoints"));
                MeridaSettings.visualWorn.set(str(st, "worn"));
                MeridaSettings.visualPremium.set(st.optBoolean("premium"));
            }
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    private static String str(JSONObject o, String k) {
        return o.isNull(k) ? null : o.optString(k, null);
    }

    private static JSONObject read(File f) throws Exception {
        final byte[] data = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) {
            int off = 0;
            while (off < data.length) {
                final int n = in.read(data, off, data.length - off);
                if (n <= 0) break;
                off += n;
            }
        }
        return new JSONObject(new String(data, "UTF-8"));
    }

    private static SharedPreferences prefs(String name) {
        return ApplicationLoader.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE);
    }
}
