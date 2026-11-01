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

    /** Colours for the «deleted» mark; 0 means the colour of the message time. */
    public static final int[] MARK_COLORS = {0, 0xFFE53935, 0xFFE91E63, 0xFF8E24AA, 0xFF5E35B1, 0xFF1E88E5, 0xFF00897B, 0xFF43A047, 0xFFFB8C00};

    public static int markColor() {
        final int i = MeridaSettings.spyMarkColor.get();
        return i > 0 && i < MARK_COLORS.length ? MARK_COLORS[i] : 0;
    }

    /** The mark shown before the time of a kept message: a bin icon and/or the word «deleted». */
    public static CharSequence mark() {
        final int style = MeridaSettings.spyMarkStyle.get();
        final int color = markColor();
        final android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        if (style != 2) {
            sb.append("d");
            final org.telegram.ui.Components.ColoredImageSpan icon = new org.telegram.ui.Components.ColoredImageSpan(org.telegram.messenger.R.drawable.msg_delete);
            icon.setSize(org.telegram.messenger.AndroidUtilities.dp(14));
            if (color != 0) {
                icon.setOverrideColor(color);
            }
            sb.setSpan(icon, 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (style != 1) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            final int start = sb.length();
            sb.append(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.MeridaDeletedWord));
            if (color != 0) {
                sb.setSpan(new android.text.style.ForegroundColorSpan(color), start, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return sb;
    }

    /** The icon is wider than the letter it replaces when the time is measured. */
    public static int markExtraWidth() {
        return MeridaSettings.spyMarkStyle.get() != 2 ? org.telegram.messenger.AndroidUtilities.dp(8) : 0;
    }

    /** Forgets every mark (the kept messages then look like normal ones). */
    public static void clear(int account) {
        synchronized (lock) {
            marks(account).clear();
            prefs(account).edit().remove("deleted").apply();
        }
    }
}
