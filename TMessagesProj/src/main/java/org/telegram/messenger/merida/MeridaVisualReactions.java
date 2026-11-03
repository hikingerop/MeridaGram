package org.telegram.messenger.merida;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.utils.settings.MeridaSettings;

/**
 * MeridaGram «Visual»: stars you put on posts from the visual balance. Kept only on this phone (per post);
 * the ⭐ reaction under the post shows them with the «VISUAL» mark. Telegram gets nothing and no real stars
 * are spent.
 */
public final class MeridaVisualReactions {

    private MeridaVisualReactions() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("merida_visual_reactions", Context.MODE_PRIVATE);
    }

    private static String key(long dialogId, int messageId) {
        return dialogId + "_" + messageId;
    }

    /** Visual stars on this post (0 when Visual star reactions are off). */
    public static long get(long dialogId, int messageId) {
        if (!MeridaVisual.starReactionsOn()) return 0;
        return prefs().getLong(key(dialogId, messageId), 0);
    }

    public static void add(long dialogId, int messageId, long amount) {
        if (amount <= 0) return;
        final String k = key(dialogId, messageId);
        prefs().edit().putLong(k, prefs().getLong(k, 0) + amount).apply();
    }

    /** Whether the visual balance covers this many stars (always, when «spend stars» is off). */
    public static boolean canSpend(long amount) {
        return !MeridaSettings.visualSpendStars.get() || MeridaSettings.visualStars.get() >= amount;
    }

    public static void spend(long amount) {
        if (MeridaSettings.visualSpendStars.get()) {
            MeridaSettings.visualStars.set((int) Math.max(0, MeridaSettings.visualStars.get() - amount));
        }
    }
}
