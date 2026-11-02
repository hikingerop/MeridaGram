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

    private static String wornStatusKey;
    private static TLRPC.TL_emojiStatusCollectible wornStatus;

    /**
     * The worn visual NFT as a «collectible status» for your own profile on this phone, so the profile header takes
     * the NFT's backdrop colours and pattern, as when wearing a real one. Never sent to Telegram.
     */
    public static TLRPC.TL_emojiStatusCollectible wornStatus(int account) {
        if (!MeridaVisual.on()) {
            return null;
        }
        final String key = org.telegram.utils.settings.MeridaSettings.visualWorn.get();
        if (key == null) {
            return null;
        }
        if (key.equals(wornStatusKey) && wornStatus != null) {
            return wornStatus;
        }
        final TL_stars.TL_starGiftUnique gift = load(key);
        if (gift == null) {
            return null;
        }
        final TL_stars.starGiftAttributeModel model = org.telegram.ui.Stars.StarsController.findAttribute(gift.attributes, TL_stars.starGiftAttributeModel.class);
        final TL_stars.starGiftAttributePattern pattern = org.telegram.ui.Stars.StarsController.findAttribute(gift.attributes, TL_stars.starGiftAttributePattern.class);
        final TL_stars.starGiftAttributeBackdrop backdrop = org.telegram.ui.Stars.StarsController.findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
        if (backdrop == null) {
            return null;
        }
        final TLRPC.TL_emojiStatusCollectible status = new TLRPC.TL_emojiStatusCollectible();
        status.collectible_id = key.hashCode();
        status.title = gift.title + " #" + gift.num;
        status.slug = gift.slug == null ? "" : gift.slug;
        status.center_color = backdrop.center_color;
        status.edge_color = backdrop.edge_color;
        status.pattern_color = backdrop.pattern_color;
        status.text_color = backdrop.text_color;
        try {
            final org.telegram.ui.Components.AnimatedEmojiDrawable.EmojiDocumentFetcher fetcher = org.telegram.ui.Components.AnimatedEmojiDrawable.getDocumentFetcher(account);
            if (model != null && model.document != null) {
                status.document_id = model.document.id;
                fetcher.putDocument(model.document);
            }
            if (pattern != null && pattern.document != null) {
                status.pattern_document_id = pattern.document.id;
                fetcher.putDocument(pattern.document);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        wornStatusKey = key;
        wornStatus = status;
        return status;
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
        if (userFull.id != UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()) {
            return 0;
        }
        int n = 0;
        for (String key : slugs()) {
            if (!isHidden(key)) {
                n++;
            }
        }
        return n;
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
    /** Keeps an estimated value (in stars) inside a visual NFT, for its «Value» row; only on this phone. */
    public static void setValueStars(TL_stars.TL_starGiftUnique gift, long stars) {
        if (gift == null || stars <= 0) return;
        gift.flags |= 256; // FLAG_8: value_amount / value_currency are stored with the gift
        gift.value_amount = stars;
        gift.value_currency = "XTR";
        gift.value_usd_amount = 0;
    }

    /** The NFT's value for the «Value» row: the stored estimate, else its price. */
    public static long valueInStars(TL_stars.StarGift gift) {
        if (gift instanceof TL_stars.TL_starGiftUnique && "XTR".equals(gift.value_currency) && gift.value_amount > 0) {
            return gift.value_amount;
        }
        return priceInStars(gift);
    }

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
        final boolean spend = org.telegram.utils.settings.MeridaSettings.visualSpendStars.get();
        final long price = spend ? priceInStars(gift) : 0;
        final int balance = org.telegram.utils.settings.MeridaSettings.visualStars.get();
        if (price > balance) {
            return org.telegram.messenger.LocaleController.formatString(org.telegram.messenger.R.string.MeridaVisualBuyNoStars, MeridaVisual.formatNumber(price), MeridaVisual.formatNumber(balance));
        }
        final String key = gift instanceof TL_stars.TL_starGiftUnique && ((TL_stars.TL_starGiftUnique) gift).slug != null
                ? ((TL_stars.TL_starGiftUnique) gift).slug
                : "g" + gift.id + "-" + System.currentTimeMillis();
        if (gift instanceof TL_stars.TL_starGiftUnique && !"XTR".equals(gift.value_currency)) {
            setValueStars((TL_stars.TL_starGiftUnique) gift, priceInStars(gift));
        }
        if (!store(key, gift)) {
            return org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.MeridaVisualBuyFailed);
        }
        org.telegram.utils.settings.MeridaSettings.visualStars.set((int) (balance - price));
        // like Telegram: the purchase shows up in Saved Messages as a gift card (with the VISUAL mark), with confetti
        MeridaVisualChat.afterPurchase(gift);
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
        prefs().edit().remove("gift_" + slug).remove("hidden_" + slug).remove("sale_" + slug).remove("pinned_" + slug).remove("coll_" + slug).apply();
    }

    /** Visual «on sale» price in stars (0 — not on sale); shown on the card and in the gift window only here. */
    public static long salePrice(String key) {
        return key == null ? 0 : prefs().getLong("sale_" + key, 0);
    }

    public static void setSalePrice(String key, long stars) {
        if (key != null) {
            if (stars > 0) {
                prefs().edit().putLong("sale_" + key, stars).apply();
            } else {
                prefs().edit().remove("sale_" + key).apply();
            }
        }
    }

    public static int onSaleCount() {
        int n = 0;
        for (String key : slugs()) {
            if (salePrice(key) > 0) n++;
        }
        return n;
    }

    private static final Pattern TIME = Pattern.compile("(1\\d{12})");

    /** When the gift was added (from its key), or 0. */
    public static long addedAt(String key) {
        if (key == null) return 0;
        final Matcher m = TIME.matcher(key);
        return m.find() ? Long.parseLong(m.group(1)) : 0;
    }

    /** The sale price as Telegram's «resell amount», so gift cards show the price pill. */
    private static void applySale(String key, TL_stars.StarGift gift) {
        final long price = salePrice(key);
        if (price > 0) {
            final TL_stars.TL_starsAmount amount = new TL_stars.TL_starsAmount();
            amount.amount = price;
            gift.resell_amount = new ArrayList<>();
            gift.resell_amount.add(amount);
        } else {
            gift.resell_amount = null;
        }
    }

    /** A hidden visual gift stays in «My gifts» but is not shown in your profile. */
    public static boolean isHidden(String key) {
        return key != null && prefs().getBoolean("hidden_" + key, false);
    }

    public static void setHidden(String key, boolean hidden) {
        if (key != null) {
            prefs().edit().putBoolean("hidden_" + key, hidden).apply();
        }
    }

    /** Adds several copies of a gift (constructor «create several»); returns how many were stored. */
    public static int storeMany(String keyPrefix, TL_stars.StarGift gift, int count) {
        int stored = 0;
        final long time = System.currentTimeMillis();
        for (int i = 0; i < count && i < MAX_GIFTS; i++) {
            if (gift instanceof TL_stars.TL_starGiftUnique) {
                ((TL_stars.TL_starGiftUnique) gift).num = Math.max(1, ((TL_stars.TL_starGiftUnique) gift).num);
            }
            if (store(keyPrefix + "-" + time + "-" + i, gift)) {
                stored++;
                if (gift instanceof TL_stars.TL_starGiftUnique) {
                    ((TL_stars.TL_starGiftUnique) gift).num++;
                }
            }
        }
        return stored;
    }

    /** «Pin» from the long-press menu: pinned visual gifts go first in your profile (only on this phone). */
    public static boolean isPinned(String key) {
        return key != null && prefs().getBoolean("pinned_" + key, false);
    }

    public static void setPinned(String key, boolean pinned) {
        if (key != null) {
            if (pinned) {
                prefs().edit().putBoolean("pinned_" + key, true).apply();
            } else {
                prefs().edit().remove("pinned_" + key).apply();
            }
        }
    }

    /** Your real gift collections this visual gift is shown in (kept only on this phone, Telegram is not told). */
    public static java.util.Set<Integer> collectionsOf(String key) {
        final java.util.HashSet<Integer> result = new java.util.HashSet<>();
        if (key == null) return result;
        for (String id : prefs().getString("coll_" + key, "").split(",")) {
            try {
                if (!id.isEmpty()) result.add(Integer.parseInt(id));
            } catch (NumberFormatException ignore) {
            }
        }
        return result;
    }

    public static void setInCollection(String key, int collectionId, boolean in) {
        if (key == null) return;
        final java.util.Set<Integer> ids = collectionsOf(key);
        if (in) ids.add(collectionId);
        else ids.remove(collectionId);
        if (ids.isEmpty()) {
            prefs().edit().remove("coll_" + key).apply();
        } else {
            prefs().edit().putString("coll_" + key, android.text.TextUtils.join(",", ids)).apply();
        }
    }

    /** The visual gifts as profile gift entries: pinned first, then newest first. */
    public static ArrayList<TL_stars.SavedStarGift> savedGifts() {
        return savedGiftsIn(0);
    }

    /** The same for one of your gift collections (collectionId 0 — the whole profile). */
    public static ArrayList<TL_stars.SavedStarGift> savedGiftsIn(int collectionId) {
        final ArrayList<TL_stars.SavedStarGift> result = new ArrayList<>();
        if (!MeridaVisual.on()) {
            return result;
        }
        final int now = ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime();
        int pinnedCount = 0;
        for (String slug : slugs()) {
            if (isHidden(slug)) {
                continue;
            }
            if (collectionId != 0 && !collectionsOf(slug).contains(collectionId)) {
                continue;
            }
            final TL_stars.StarGift gift = loadAny(slug);
            if (gift == null) {
                continue;
            }
            applySale(slug, gift); // «on sale» only when you put it on visual sale
            gift.resale_ton_only = false;
            final TL_stars.TL_savedStarGift saved = new TL_stars.TL_savedStarGift();
            saved.gift = gift;
            saved.date = now;
            saved.name_hidden = false; // no «anonymous sender» icon on the card
            saved.pinned_to_top = collectionId == 0 && isPinned(slug);
            synchronized (made) {
                made.add(saved);
                keys.put(saved, slug);
            }
            if (saved.pinned_to_top) {
                result.add(pinnedCount++, saved);
            } else {
                result.add(saved);
            }
        }
        return result;
    }

    /** One stored gift wrapped for «My gifts», with its key, so it is drawn with the «VISUAL» ribbon. */
    public static TL_stars.SavedStarGift wrap(String key) {
        final TL_stars.StarGift gift = loadAny(key);
        if (gift == null) {
            return null;
        }
        applySale(key, gift);
        gift.resale_ton_only = false;
        final TL_stars.TL_savedStarGift saved = new TL_stars.TL_savedStarGift();
        saved.gift = gift;
        saved.name_hidden = false; // no «anonymous sender» icon on the card
        saved.unsaved = false;
        synchronized (made) {
            made.add(saved);
            keys.put(saved, key);
        }
        return saved;
    }

    /** A gift wrapped for the constructor's preview, so it is drawn with the «VISUAL» ribbon. */
    public static TL_stars.SavedStarGift preview(TL_stars.StarGift gift) {
        final TL_stars.TL_savedStarGift saved = new TL_stars.TL_savedStarGift();
        saved.gift = gift;
        saved.name_hidden = false; // no «anonymous sender» icon on the card
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
