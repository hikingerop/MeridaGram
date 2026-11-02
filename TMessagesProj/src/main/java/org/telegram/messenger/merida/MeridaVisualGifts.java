package org.telegram.messenger.merida;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MeridaGram «Visual», stage 2: NFT gifts shown in your own profile, only on this phone, each with an orange
 * «VISUAL» ribbon. A gift is added by its link (t.me/nft/Name-123); its real model is loaded from Telegram,
 * stored here and never sent anywhere. Tapping it opens the real gift page with its real owner.
 */
public final class MeridaVisualGifts {

    private static final Pattern SLUG = Pattern.compile("(?:nft/)?([A-Za-z0-9]+-\\d+)\\s*$");
    private static final int MAX_GIFTS = 60;
    /** Gift objects made by this class, so the profile list can tell them from real ones. */
    private static final Set<TL_stars.SavedStarGift> made = Collections.newSetFromMap(new WeakHashMap<>());
    private static final WeakHashMap<TL_stars.SavedStarGift, String> keys = new WeakHashMap<>();

    /** The storage key of a visual gift shown in the profile, or null. */
    public static String keyOf(TL_stars.SavedStarGift gift) {
        synchronized (made) {
            return keys.get(gift);
        }
    }

    /** The model of the NFT worn as your visual status, or null. */
    public static org.telegram.tgnet.TLRPC.Document wornModel() {
        if (!MeridaVisual.on()) {
            return null;
        }
        final String key = org.telegram.utils.settings.MeridaSettings.visualWorn.get();
        final TL_stars.TL_starGiftUnique gift = key == null ? null : load(key);
        if (gift == null) {
            return null;
        }
        final TL_stars.starGiftAttributeModel model = org.telegram.ui.Stars.StarsController.findAttribute(gift.attributes, TL_stars.starGiftAttributeModel.class);
        return model == null ? null : model.document;
    }

    private MeridaVisualGifts() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("merida_visual_gifts", Context.MODE_PRIVATE);
    }

    /** «https://t.me/nft/PlushPepe-1» or «PlushPepe-1» → «PlushPepe-1»; null when it is not a gift link. */
    public static String parseSlug(String text) {
        if (text == null) {
            return null;
        }
        final Matcher m = SLUG.matcher(text.trim());
        return m.find() ? m.group(1) : null;
    }

    public static List<String> slugs() {
        final String all = prefs().getString("slugs", "");
        final ArrayList<String> result = new ArrayList<>();
        for (String s : all.split("\n")) {
            if (!s.isEmpty()) {
                result.add(s);
            }
        }
        return result;
    }

    private static void saveSlugs(List<String> slugs) {
        prefs().edit().putString("slugs", android.text.TextUtils.join("\n", slugs)).apply();
    }

    public static int count() {
        return MeridaVisual.on() ? slugs().size() : 0;
    }

    /** How many visual gifts to add to this profile's gift count: only for your own profile. */
    public static int extraFor(TLRPC.UserFull userFull) {
        if (userFull == null || !MeridaVisual.on()) {
            return 0;
        }
        return userFull.id == UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId() ? slugs().size() : 0;
    }

    public static TL_stars.TL_starGiftUnique load(String key) {
        final TL_stars.StarGift gift = loadAny(key);
        return gift instanceof TL_stars.TL_starGiftUnique ? (TL_stars.TL_starGiftUnique) gift : null;
    }

    /** Any stored visual gift: an NFT (key = its slug) or an ordinary gift bought visually (key = «g<id>-<time>»). */
    public static TL_stars.StarGift loadAny(String key) {
        final String stored = prefs().getString("gift_" + key, null);
        if (stored == null) {
            return null;
        }
        try {
            final SerializedData data = new SerializedData(Base64.decode(stored, Base64.NO_WRAP));
            final TL_stars.StarGift gift = TL_stars.StarGift.TLdeserialize(data, data.readInt32(false), false);
            data.cleanup();
            return gift;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    /** Keeps a gift object as a visual gift; returns false when it could not be stored. */
    public static boolean store(String key, TL_stars.StarGift gift) {
        try {
            final SerializedData data = new SerializedData(gift.getObjectSize());
            gift.serializeToStream(data);
            final String encoded = Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP);
            data.cleanup();
            final List<String> slugs = slugs();
            slugs.remove(key);
            slugs.add(0, key);
            while (slugs.size() > MAX_GIFTS) {
                prefs().edit().remove("gift_" + slugs.remove(slugs.size() - 1)).apply();
            }
            prefs().edit().putString("gift_" + key, encoded).apply();
            saveSlugs(slugs);
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    /** The price of a gift in stars: the resale price for an NFT, the shop price for an ordinary gift. */
    public static long priceInStars(TL_stars.StarGift gift) {
        if (gift == null) {
            return 0;
        }
        if (gift.resell_amount != null) {
            for (TL_stars.StarsAmount a : gift.resell_amount) {
                if (a instanceof TL_stars.TL_starsAmount) {
                    return a.amount;
                }
            }
        }
        return gift.stars;
    }

    /**
     * «Visual purchase» for yourself: the visual stars balance pays, the gift goes to your visual gifts.
     * Telegram is not asked and real stars are never spent. Returns a message for the user.
     */
    public static String buyForSelf(TL_stars.StarGift gift) {
        final long price = priceInStars(gift);
        final int balance = org.telegram.utils.settings.MeridaSettings.visualStars.get();
        if (price > balance) {
            return org.telegram.messenger.LocaleController.formatString(org.telegram.messenger.R.string.MeridaVisualBuyNoStars, MeridaVisual.formatNumber(price), MeridaVisual.formatNumber(balance));
        }
        final String key = gift instanceof TL_stars.TL_starGiftUnique && ((TL_stars.TL_starGiftUnique) gift).slug != null
                ? ((TL_stars.TL_starGiftUnique) gift).slug
                : "g" + gift.id + "-" + System.currentTimeMillis();
        if (!store(key, gift)) {
            return org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.MeridaVisualBuyFailed);
        }
        org.telegram.utils.settings.MeridaSettings.visualStars.set((int) (balance - price));
        return org.telegram.messenger.LocaleController.formatString(org.telegram.messenger.R.string.MeridaVisualBought, MeridaVisual.formatNumber(price));
    }

    public interface Result {
        /** title is the gift's name and number on success, null when the gift was not found. */
        void done(String title);
    }

    /** Loads the gift from Telegram by its link and keeps it for the profile. */
    public static void add(int account, String slug, Result result) {
        final TL_stars.getUniqueStarGift req = new TL_stars.getUniqueStarGift();
        req.slug = slug;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!(response instanceof TL_stars.TL_payments_uniqueStarGift)
                    || !(((TL_stars.TL_payments_uniqueStarGift) response).gift instanceof TL_stars.TL_starGiftUnique)) {
                result.done(null);
                return;
            }
            final TL_stars.TL_payments_uniqueStarGift r = (TL_stars.TL_payments_uniqueStarGift) response;
            MessagesController.getInstance(account).putUsers(r.users, false);
            MessagesController.getInstance(account).putChats(r.chats, false);
            final TL_stars.TL_starGiftUnique gift = (TL_stars.TL_starGiftUnique) r.gift;
            result.done(store(slug, gift) ? gift.title + " #" + gift.num : null);
        }));
    }

    public static void remove(String slug) {
        final List<String> slugs = slugs();
        slugs.remove(slug);
        saveSlugs(slugs);
        prefs().edit().remove("gift_" + slug).apply();
    }

    /** The visual gifts as profile gift entries, newest first. */
    public static ArrayList<TL_stars.SavedStarGift> savedGifts() {
        final ArrayList<TL_stars.SavedStarGift> result = new ArrayList<>();
        if (!MeridaVisual.on()) {
            return result;
        }
        final int now = ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime();
        for (String slug : slugs()) {
            final TL_stars.StarGift gift = loadAny(slug);
            if (gift == null) {
                continue;
            }
            gift.resell_amount = null; // never shown as «on sale» in your profile
            final TL_stars.TL_savedStarGift saved = new TL_stars.TL_savedStarGift();
            saved.gift = gift;
            saved.date = now;
            saved.name_hidden = true;
            synchronized (made) {
                made.add(saved);
                keys.put(saved, slug);
            }
            result.add(saved);
        }
        return result;
    }

    /** A gift wrapped for the constructor's preview, so it is drawn with the «VISUAL» ribbon. */
    public static TL_stars.SavedStarGift preview(TL_stars.StarGift gift) {
        final TL_stars.TL_savedStarGift saved = new TL_stars.TL_savedStarGift();
        saved.gift = gift;
        saved.name_hidden = true;
        synchronized (made) {
            made.add(saved);
        }
        return saved;
    }

    public static boolean isVisual(TL_stars.SavedStarGift gift) {
        if (gift == null) {
            return false;
        }
        synchronized (made) {
            return made.contains(gift);
        }
    }

    public static String slugOf(TL_stars.SavedStarGift gift) {
        return gift != null && gift.gift instanceof TL_stars.TL_starGiftUnique ? ((TL_stars.TL_starGiftUnique) gift.gift).slug : null;
    }
}
