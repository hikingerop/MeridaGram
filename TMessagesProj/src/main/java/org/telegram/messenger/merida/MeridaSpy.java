package org.telegram.messenger.merida;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.utils.settings.MeridaSettings;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * MeridaGram spy mode: when someone deletes a message, MeridaGram keeps it in the chat and marks it as
 * deleted. Only the marks are stored here (per account, on this phone); the messages themselves simply
 * stay in Telegram's own local database because the deletion is not applied.
 */
public final class MeridaSpy {

    /** Keeps the list from growing without end: the oldest marks are dropped first. */
    private static final int MAX_MARKS = 20000;

    private static final Object lock = new Object();
    private static final ArrayList<HashSet<String>> deleted = new ArrayList<>();

    private MeridaSpy() {
    }

    private static SharedPreferences prefs(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences("merida_spy_" + account, Context.MODE_PRIVATE);
    }

    private static HashSet<String> marks(int account) {
        while (deleted.size() <= account) {
            deleted.add(null);
        }
        HashSet<String> set = deleted.get(account);
        if (set == null) {
            set = new HashSet<>(prefs(account).getStringSet("deleted", new HashSet<>()));
            deleted.set(account, set);
        }
        return set;
    }

    /**
     * Called for a deletion that came from Telegram. {@code dialogKey} is the channel's dialog id for
     * channels and supergroups, 0 for private chats and basic groups (their message ids are unique per account).
     * Returns true when the deletion must be skipped because spy mode keeps the messages.
     */
    public static boolean keepDeleted(int account, long dialogKey, ArrayList<Integer> ids) {
        if (!MeridaSettings.spySaveDeleted.get() || ids == null || ids.isEmpty()) {
            return false;
        }
        synchronized (lock) {
            final HashSet<String> set = marks(account);
            for (Integer id : ids) {
                set.add(dialogKey + ":" + id);
            }
            if (set.size() > MAX_MARKS) {
                // order is not kept in a set; dropping arbitrary old marks is fine for a cap this large
                final ArrayList<String> all = new ArrayList<>(set);
                for (int i = 0; i < all.size() - MAX_MARKS; i++) {
                    set.remove(all.get(i));
                }
            }
            prefs(account).edit().putStringSet("deleted", new HashSet<>(set)).apply();
        }
        return true;
    }

    /** True when this message was deleted by its sender and kept by spy mode. */
    public static boolean isDeleted(int account, MessageObject message) {
        if (message == null || message.messageOwner == null || message.getId() <= 0) {
            return false;
        }
        final long dialogKey = message.messageOwner.peer_id != null && message.messageOwner.peer_id.channel_id != 0
                ? -message.messageOwner.peer_id.channel_id : 0;
        synchronized (lock) {
            return marks(account).contains(dialogKey + ":" + message.getId());
        }
    }

    /** Forgets every mark (the kept messages then look like normal ones). */
    public static void clear(int account) {
        synchronized (lock) {
            marks(account).clear();
            prefs(account).edit().remove("deleted").apply();
        }
    }
}
