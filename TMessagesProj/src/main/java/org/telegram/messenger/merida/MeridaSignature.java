package org.telegram.messenger.merida;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.security.MessageDigest;

/**
 * MeridaGram: checks that this copy is signed with the owner's own key. Someone could take MeridaGram,
 * add a virus and hand it out under the same name; such a copy is signed with another key, and then a
 * window says it is not the official MeridaGram and points to the official channel.
 */
public final class MeridaSignature {

    /** SHA-256 of the owner's signing certificate (public, safe to keep in the code). */
    private static final String OFFICIAL_SHA256 = "8BFCECAC2C67CC023261B3343067555EEC1A4E2D26B095B9226D48E1684D7BD9";

    private static boolean checked;

    private MeridaSignature() {
    }

    /** Called when MeridaGram comes to the screen; looks once per launch. */
    public static void check() {
        if (checked) {
            return;
        }
        checked = true;
        new Thread(() -> {
            final boolean official = isOfficial(ApplicationLoader.applicationContext);
            if (!official) {
                AndroidUtilities.runOnUIThread(MeridaSignature::warn, 2500);
            }
        }, "MeridaSignature").start();
    }

    static boolean isOfficial(Context context) {
        try {
            final PackageManager pm = context.getPackageManager();
            final String pkg = context.getPackageName();
            final Signature[] signatures;
            if (Build.VERSION.SDK_INT >= 28) {
                final PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) {
                    return false;
                }
                signatures = info.signingInfo.hasMultipleSigners()
                        ? info.signingInfo.getApkContentsSigners()
                        : info.signingInfo.getSigningCertificateHistory();
            } else {
                final PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
                signatures = info.signatures;
            }
            if (signatures == null) {
                return false;
            }
            for (Signature signature : signatures) {
                if (OFFICIAL_SHA256.equalsIgnoreCase(sha256(signature.toByteArray()))) {
                    return true;
                }
            }
            return false;
        } catch (Throwable e) {
            FileLog.e(e);
            // never scare people because of an error in the check itself
            return true;
        }
    }

    private static String sha256(byte[] data) throws Exception {
        final byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
        final StringBuilder s = new StringBuilder();
        for (byte b : hash) {
            s.append(String.format("%02X", b));
        }
        return s.toString();
    }

    private static void warn() {
        final BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.MeridaSignatureTitle));
        builder.setMessage(LocaleController.getString(R.string.MeridaSignatureText));
        builder.setPositiveButton(LocaleController.getString(R.string.MeridaSignatureOpenChannel), (dialog, which) ->
                Browser.openUrl(fragment.getParentActivity(), "https://t.me/MeridaGram"));
        builder.setNegativeButton(LocaleController.getString(R.string.MeridaSignatureLater), null);
        fragment.showDialog(builder.create());
    }
}
