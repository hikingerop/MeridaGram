package org.telegram.messenger.merida;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;
import org.telegram.utils.settings.MeridaSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * MeridaGram's guard against strangers in the account. While the app is open it asks Telegram for the
 * list of devices logged into the account every few minutes. When a device appears that the owner has
 * not seen yet, a big window shows what it is and where it is from, with buttons to end that login or
 * log out everywhere except this phone. Telegram's own «new login» message can be deleted by whoever
 * got in; this window cannot.
 */
public final class MeridaSessions {

    private static final long CHECK_EVERY_MS = 5 * 60_000L;
    private static final long MIN_GAP_MS = 60_000L;

    private static final long[] lastCheck = new long[UserConfig.MAX_ACCOUNT_COUNT];
    private static final boolean[] dialogShown = new boolean[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Runnable periodic = MeridaSessions::checkAll;

    private MeridaSessions() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("merida_sessions", Context.MODE_PRIVATE);
    }

    /** Called when MeridaGram comes to the screen; repeats itself while the app stays open. */
    public static void checkAll() {
        AndroidUtilities.cancelRunOnUIThread(periodic);
        if (!MeridaSettings.sessionGuard.get()) {
            return;
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                check(a);
            }
        }
        AndroidUtilities.runOnUIThread(periodic, CHECK_EVERY_MS);
    }

    private static void check(int account) {
        final long now = System.currentTimeMillis();
        if (now - lastCheck[account] < MIN_GAP_MS || dialogShown[account]) {
            return;
        }
        lastCheck[account] = now;
        final TL_account.getAuthorizations req = new TL_account.getAuthorizations();
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (response instanceof TL_account.authorizations) {
                final ArrayList<TLRPC.TL_authorization> list = ((TL_account.authorizations) response).authorizations;
                AndroidUtilities.runOnUIThread(() -> onList(account, list));
            }
        });
    }

    private static void onList(int account, ArrayList<TLRPC.TL_authorization> list) {
        if (list == null) {
            return;
        }
        final String key = "known_" + account;
        final boolean firstTime = !prefs().contains(key);
        final Set<String> known = new HashSet<>(prefs().getStringSet(key, new HashSet<>()));
        final Set<String> now = new HashSet<>();
        final ArrayList<TLRPC.TL_authorization> fresh = new ArrayList<>();
        for (TLRPC.TL_authorization auth : list) {
            if (auth == null || auth.current) {
                continue;
            }
            final String hash = String.valueOf(auth.hash);
            now.add(hash);
            if (!known.contains(hash)) {
                fresh.add(auth);
            }
        }
        // logins that are gone are forgotten, so the list does not grow forever
        known.retainAll(now);
        if (firstTime || fresh.isEmpty()) {
            // on the very first check every existing device is taken as the owner's own
            known.addAll(now);
            prefs().edit().putStringSet(key, known).apply();
            return;
        }
        prefs().edit().putStringSet(key, known).apply();
        showAlert(account, fresh);
    }

    private static void showAlert(int account, ArrayList<TLRPC.TL_authorization> fresh) {
        final BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        final StringBuilder text = new StringBuilder(LocaleController.getString(R.string.MeridaSessionsText));
        for (TLRPC.TL_authorization auth : fresh) {
            text.append("\n\n• ").append(describe(auth));
        }
        dialogShown[account] = true;
        final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.MeridaSessionsTitle));
        builder.setMessage(text.toString());
        builder.setPositiveButton(LocaleController.getString(R.string.MeridaSessionsItsMe), (dialog, which) -> remember(account, fresh));
        builder.setNeutralButton(LocaleController.getString(R.string.MeridaSessionsEndThese), (dialog, which) -> {
            for (TLRPC.TL_authorization auth : fresh) {
                final TL_account.resetAuthorization req = new TL_account.resetAuthorization();
                req.hash = auth.hash;
                ConnectionsManager.getInstance(account).sendRequest(req, null);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.MeridaSessionsEndAll), (dialog, which) ->
                ConnectionsManager.getInstance(account).sendRequest(new TLRPC.TL_auth_resetAuthorizations(), null));
        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(d -> dialogShown[account] = false);
        fragment.showDialog(dialog);
    }

    private static void remember(int account, ArrayList<TLRPC.TL_authorization> fresh) {
        final String key = "known_" + account;
        final Set<String> known = new HashSet<>(prefs().getStringSet(key, new HashSet<>()));
        for (TLRPC.TL_authorization auth : fresh) {
            known.add(String.valueOf(auth.hash));
        }
        prefs().edit().putStringSet(key, known).apply();
    }

    private static String describe(TLRPC.TL_authorization auth) {
        final StringBuilder s = new StringBuilder();
        s.append(nonEmpty(auth.device_model, "?"));
        if (!TextUtils.isEmpty(auth.platform) || !TextUtils.isEmpty(auth.system_version)) {
            s.append(", ").append(nonEmpty(auth.platform, "")).append(' ').append(nonEmpty(auth.system_version, ""));
        }
        if (!TextUtils.isEmpty(auth.app_name)) {
            s.append("\n").append(auth.app_name).append(' ').append(nonEmpty(auth.app_version, ""));
        }
        if (!TextUtils.isEmpty(auth.country) || !TextUtils.isEmpty(auth.ip)) {
            s.append("\n").append(nonEmpty(auth.country, "")).append(TextUtils.isEmpty(auth.region) ? "" : ", " + auth.region)
                    .append(TextUtils.isEmpty(auth.ip) ? "" : " · IP " + auth.ip);
        }
        if (auth.date_created > 0) {
            s.append("\n").append(LocaleController.formatDateTime(auth.date_created, true));
        }
        if (auth.password_pending) {
            s.append("\n").append(LocaleController.getString(R.string.MeridaSessionsPasswordPending));
        }
        return s.toString().trim();
    }

    private static String nonEmpty(String s, String fallback) {
        return TextUtils.isEmpty(s) ? fallback : s.trim();
    }
}
