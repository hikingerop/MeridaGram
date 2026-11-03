package org.telegram.messenger.merida;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;

/**
 * MeridaGram «Visual»: Telegram's third-party verification («Account verified by the organization «Major»»)
 * for your own profile and for groups / channels you choose — only on this phone. The organization's real
 * icon and name are taken from its verifier bot (@major, @hold: bot_info.verifier_settings); nothing is sent to
 * Telegram, and the line under the profile carries the «VISUAL» watermark over its text.
 */
public final class MeridaVisualVerify {

    private MeridaVisualVerify() {
    }

    /** The verifier bots offered (their usernames). */
    public static final String[] ORGS = {"major", "hold"};

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("merida_visual_verify", Context.MODE_PRIVATE);
    }

    /** The verifier bot chosen for this dialog (your own id for the profile), or null. */
    public static String get(long dialogId) {
        if (!MeridaVisual.on()) return null;
        return prefs().getString("org_" + dialogId, null);
    }

    public static void set(int account, long dialogId, String org) {
        if (org == null) {
            prefs().edit().remove("org_" + dialogId).apply();
        } else {
            prefs().edit().putString("org_" + dialogId, org).apply();
            load(account, org);
        }
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
    }

    /** The organization's company name («Major»), once loaded from its bot. */
    public static String company(String org) {
        return prefs().getString("company_" + org, null);
    }

    public static long icon(String org) {
        return prefs().getLong("icon_" + org, 0);
    }

    private static long botId(String org) {
        return prefs().getLong("bot_" + org, 0);
    }

    /** The icon for the name in this dialog's profile (0 — none). */
    public static long iconFor(int account, long dialogId) {
        final String org = get(dialogId);
        if (org == null) return 0;
        final long icon = icon(org);
        if (icon == 0) load(account, org);
        return icon;
    }

    /**
     * Telegram's verification object for this dialog's profile (the line under the info card), or null.
     * Its description is plain text; the profile writes it with the VISUAL watermark (see descriptionFor).
     */
    public static TL_bots.BotVerification verificationFor(int account, long dialogId) {
        final String org = get(dialogId);
        if (org == null) return null;
        final long icon = icon(org);
        final String company = company(org);
        if (icon == 0 || company == null) {
            load(account, org);
            return null;
        }
        final TL_bots.BotVerification v = new TL_bots.BotVerification();
        v.bot_id = botId(org);
        v.icon = icon;
        v.description = new TLRPC.TL_textWithEntities();
        v.description.text = descriptionText(dialogId, company);
        return v;
    }

    /** «Account / Group / Channel verified by the organization «Major».» with the VISUAL watermark over it. */
    public static CharSequence descriptionFor(long dialogId, TL_bots.BotVerification v) {
        return MeridaVisual.watermark(v.description != null ? v.description.text : "");
    }

    private static String descriptionText(long dialogId, String company) {
        final int res;
        if (dialogId >= 0) {
            res = R.string.MeridaVVerifiedAccount;
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(org.telegram.messenger.UserConfig.selectedAccount).getChat(-dialogId);
            res = org.telegram.messenger.ChatObject.isChannelAndNotMegaGroup(chat) ? R.string.MeridaVVerifiedChannel : R.string.MeridaVVerifiedGroup;
        }
        return LocaleController.formatString(res, company);
    }

    private static boolean loading;

    /** Reads the organization's icon and name from its verifier bot (resolve @org → full user → verifier_settings). */
    public static void load(int account, String org) {
        if (loading) return;
        loading = true;
        final MessagesController mc = MessagesController.getInstance(account);
        mc.getUserNameResolver().resolve(org, peerId -> {
            if (peerId == null || peerId <= 0) {
                loading = false;
                return;
            }
            final TLRPC.User bot = mc.getUser(peerId);
            if (bot == null) {
                loading = false;
                return;
            }
            mc.loadFullUser(bot, 0, true, full -> AndroidUtilities.runOnUIThread(() -> {
                loading = false;
                if (full == null || full.bot_info == null || full.bot_info.verifier_settings == null) return;
                final TL_bots.BotVerifierSettings s = full.bot_info.verifier_settings;
                prefs().edit()
                        .putLong("icon_" + org, s.icon)
                        .putString("company_" + org, s.company)
                        .putLong("bot_" + org, peerId)
                        .apply();
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
            }));
        });
    }
}
