package org.telegram.messenger.merida;

import org.telegram.messenger.UserConfig;
import org.telegram.utils.settings.MeridaSettings;

/**
 * MeridaGram «Chat translation» (Settings → MeridaGram → General): Telegram's own «Translate chat» bar and
 * per-message «Translate», without Premium. Without Premium the texts are NOT sent to Telegram's server
 * translator (that is a Premium feature): they go to the free translator built into Telegram itself
 * (TranslateAlert2.alternativeTranslate, Google Translate). Nothing pretends you have Premium.
 */
public final class MeridaTranslate {

    private MeridaTranslate() {
    }

    /** The chat translation bar works: you have Premium, or MeridaGram's free translation is on. */
    public static boolean allowed(int account) {
        return UserConfig.getInstance(account).isPremium() || MeridaSettings.freeTranslate.get();
    }

    /** Translate through the free built-in translator instead of Telegram's Premium one. */
    public static boolean free(int account) {
        return MeridaSettings.freeTranslate.get() && !UserConfig.getInstance(account).isPremium();
    }
}
