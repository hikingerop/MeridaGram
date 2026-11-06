package org.telegram.messenger.merida;

import android.content.Context;
import android.util.Base64;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Wallet.GramWallet;
import org.telegram.ui.Wallet.WalletActivity;
import org.telegram.utils.settings.MeridaSettings;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Objects;

public final class MeridaVisualWallet {
    private MeridaVisualWallet() {}
    private static final double[][] PLACES = {{47, 70, 27, 352}, {77, 68, 29, 10}, {20, 74, 22, 6}};
    private static TL_wallet.card cardCache;
    private static long cardVersion = -1, cardGrams = -1;
    private static String cardWorn;
    public static final ArrayList<TL_wallet.walletTransaction> visualTx = new ArrayList<>();

    static { installAll(); }

    public static void installAll() {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) install(i);
    }

    public static void install(int account) {
        try {
            final Field f = GramWallet.class.getDeclaredField("Instance");
            f.setAccessible(true);
            final GramWallet[] arr = (GramWallet[]) f.get(null);
            if (arr != null && account >= 0 && account < arr.length && !(arr[account] instanceof VisualGramWallet)) {
                arr[account] = new VisualGramWallet(account);
            }
        } catch (Throwable ignore) {}
    }

    public static BaseFragment createWalletFragment() {
        installAll();
        return new WalletActivity() {
            @Override public View createView(Context context) {
                install(currentAccount);
                final View v = super.createView(context);
                applyCard(this);
                return v;
            }
            @Override public void onResume() {
                install(currentAccount);
                super.onResume();
                applyCard(this);
            }
        };
    }

    public static void applyCard(WalletActivity activity) {
        if (activity == null || !MeridaVisual.gramsOn()) return;
        try {
            final TL_wallet.card c = card();
            if (c == null) return;
            final Field f = WalletActivity.class.getDeclaredField("card");
            f.setAccessible(true);
            final Object cardView = f.get(activity);
            if (cardView != null) {
                for (Method m : cardView.getClass().getMethods()) {
                    if ("setProfileCard".equals(m.getName()) && m.getParameterTypes().length == 3) {
                        m.invoke(cardView, activity.getCurrentAccount(), activity.getUserConfig().getCurrentUser(), c);
                        break;
                    }
                }
            }
        } catch (Throwable ignore) {}
    }

    public static String addressFor(long userId) {
        final byte[] raw = new byte[36];
        raw[0] = 0x51;
        raw[1] = 0x00;
        long x = userId ^ 0x544F4E43415244L;
        if (x == 0) x = 0x544F4E43415244L;
        for (int i = 2; i < 34; i++) {
            x ^= (x << 13); x ^= (x >>> 7); x ^= (x << 17);
            raw[i] = (byte) (x & 0xFF);
        }
        int crc = 0;
        for (int i = 0; i < 34; i++) {
            crc ^= (raw[i] & 0xFF) << 8;
            for (int b = 0; b < 8; b++) {
                crc = ((crc & 0x8000) != 0) ? (((crc << 1) ^ 0x1021) & 0xFFFF) : ((crc << 1) & 0xFFFF);
            }
        }
        raw[34] = (byte) ((crc >>> 8) & 0xFF);
        raw[35] = (byte) (crc & 0xFF);
        return Base64.encodeToString(raw, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    public static String address() {
        return addressFor(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());
    }

    public static ArrayList<TL_wallet.walletTransaction> getTransactions(int account) {
        if (visualTx.isEmpty() && MeridaSettings.visualGrams.get() > 0) {
            final long selfId = UserConfig.getInstance(account).getClientUserId();
            final TL_wallet.walletTransaction t = new TL_wallet.walletTransaction();
            t.incoming = true;
            t.amount = MeridaSettings.visualGrams.get() * 1_000_000_000L;
            t.date = (int) (System.currentTimeMillis() / 1000L) - 3600;
            t.comment = "";
            final TL_wallet.walletTransactionPeerUser p = new TL_wallet.walletTransactionPeerUser();
            p.user_id = selfId;
            p.address = addressFor(selfId);
            t.peer = p;
            visualTx.add(t);
        }
        return visualTx;
    }

    public static boolean sync(GramWallet wallet) {
        if (!MeridaVisual.gramsOn() || wallet == null) return false;
        final long targetBalance = MeridaSettings.visualGrams.get() * 1_000_000_000L;
        final String targetAddr = addressFor(UserConfig.getInstance(wallet.currentAccount).getClientUserId());
        if (wallet.getBalance() != targetBalance || !Objects.equals(wallet.getAddress(), targetAddr)) {
            final TL_wallet.TL_walletState st = new TL_wallet.TL_walletState();
            st.address = targetAddr;
            st.balance = targetBalance;
            st.backup_enabled = true;
            st.can_enable_backup = false;
            st.can_export_phrase = false;
            st.card = cardInternal();
            wallet.updateState(st);
        }
        return true;
    }

    public static boolean resolve(TLRPC.User user, Utilities.Callback2<TL_wallet.walletUserAddress, String> whenDone) {
        if (!MeridaVisual.gramsOn() || user == null || whenDone == null) return false;
        final long selfId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        final TL_wallet.walletUserAddress r = new TL_wallet.walletUserAddress();
        r.user_id = user.id;
        r.address = addressFor(user.id == selfId ? (user.id ^ 0x7FFF_FFFFL) : user.id);
        whenDone.run(r, null);
        return true;
    }

    public static boolean send(GramWallet wallet, TLRPC.User user, String addr, long amount, String comment, Utilities.Callback<String> whenSent, Utilities.Callback<TL_wallet.walletTransaction> whenQueued) {
        if (!MeridaVisual.gramsOn() || wallet == null) return false;
        final int account = wallet.currentAccount;
        final long selfId = UserConfig.getInstance(account).getClientUserId();
        final long uid = user != null ? user.id : 0;
        final boolean toSelf = (uid == selfId) || (addr != null && (addr.equalsIgnoreCase(addressFor(selfId)) || addr.equalsIgnoreCase(addressFor(selfId ^ 0x7FFF_FFFFL))));
        final TL_wallet.walletTransaction t = new TL_wallet.walletTransaction();
        t.incoming = toSelf;
        t.amount = amount;
        t.fee = 5_000_000L;
        t.date = (int) (System.currentTimeMillis() / 1000L);
        t.comment = comment != null ? comment : "";
        final TL_wallet.walletTransactionPeerUser p = new TL_wallet.walletTransactionPeerUser();
        p.user_id = uid != 0 ? uid : selfId;
        p.address = addr != null && !addr.isEmpty() ? addr : addressFor(p.user_id);
        t.peer = p;
        getTransactions(account).add(0, t);
        final int delta = (int) Math.max(0L, amount / 1_000_000_000L);
        MeridaSettings.visualGrams.set(Math.max(0, MeridaSettings.visualGrams.get() + (toSelf ? delta : -delta)));
        cardCache = null;
        final GramWallet.TransactionsList txList = wallet.getTransactions();
        if (txList != null) {
            txList.transactions.clear();
            txList.transactions.addAll(getTransactions(account));
            txList.allTransactions.clear();
            txList.allTransactions.addAll(txList.transactions);
            txList.notifyUpdate();
        }
        sync(wallet);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.walletUpdate);
        if (whenQueued != null) whenQueued.run(t);
        if (whenSent != null) whenSent.run(null);
        return true;
    }

    public static TL_wallet.card card() {
        installAll();
        return cardInternal();
    }

    private static TL_wallet.card cardInternal() {
        if (!MeridaVisual.gramsOn()) return null;
        final long grams = MeridaSettings.visualGrams.get();
        final String worn = MeridaSettings.visualWorn.get();
        final long v = MeridaVisualGifts.version();
        if (cardCache != null && cardVersion == v && cardGrams == grams && Objects.equals(cardWorn, worn)) return cardCache;
        final TL_wallet.card card = new TL_wallet.card();
        card.address = address();
        card.balance = grams * 1_000_000_000L;
        final ArrayList<TL_stars.SavedStarGift> gifts = MeridaVisualGifts.savedGifts();
        int placed = 0;
        for (TL_stars.SavedStarGift saved : gifts) {
            if (placed >= PLACES.length) break;
            if (!(saved.gift instanceof TL_stars.TL_starGiftUnique)) continue;
            final String key = MeridaVisualGifts.keyOf(saved);
            if (key == null || MeridaVisualGifts.isHidden(key)) continue;
            final TL_wallet.cardSticker sticker = new TL_wallet.cardSticker();
            sticker.nft = item(key, saved.gift);
            final TL_stories.TL_mediaAreaCoordinates c = new TL_stories.TL_mediaAreaCoordinates();
            c.x = PLACES[placed][0]; c.y = PLACES[placed][1]; c.w = PLACES[placed][2]; c.h = PLACES[placed][2]; c.rotation = PLACES[placed][3];
            sticker.coordinates = c;
            card.stickers.add(sticker);
            placed++;
        }
        if (worn != null) {
            final TL_stars.TL_starGiftUnique gift = MeridaVisualGifts.load(worn);
            if (gift != null) card.bg_nft_item = item(worn, gift);
        }
        cardCache = card; cardVersion = v; cardGrams = grams; cardWorn = worn;
        return card;
    }

    private static TL_wallet.nftItem item(String key, TL_stars.StarGift gift) {
        final TL_wallet.nftItem item = new TL_wallet.nftItem();
        item.address = "merida-visual-" + key;
        item.owner_address = ""; item.collection_address = "";
        item.index = gift instanceof TL_stars.TL_starGiftUnique ? String.valueOf(((TL_stars.TL_starGiftUnique) gift).num) : "";
        item.name = gift.title != null ? gift.title : "";
        item.description = "";
        item.gift = gift;
        return item;
    }

    public static class VisualGramWallet extends GramWallet {
        public VisualGramWallet(int account) { super(account); sync(this); }
        @Override public boolean isReady() { if (MeridaVisual.gramsOn()) { sync(this); return true; } return super.isReady(); }
        @Override public void whenReady(Runnable cb) { if (MeridaVisual.gramsOn()) { sync(this); if (cb != null) cb.run(); return; } super.whenReady(cb); }
        @Override public String getAddress() { return MeridaVisual.gramsOn() ? addressFor(UserConfig.getInstance(currentAccount).getClientUserId()) : super.getAddress(); }
        @Override public long getBalance() { return MeridaVisual.gramsOn() ? MeridaSettings.visualGrams.get() * 1_000_000_000L : super.getBalance(); }
        @Override public boolean isBackupEnabled() { return MeridaVisual.gramsOn() || super.isBackupEnabled(); }
        @Override public boolean canSend() { return MeridaVisual.gramsOn() || super.canSend(); }
        @Override public boolean cantAccessWallet() { return !MeridaVisual.gramsOn() && super.cantAccessWallet(); }
        @Override public void makeSureSecretPhraseBackedUpLocally(boolean s, boolean p, Utilities.Callback<String> cb) { if (MeridaVisual.gramsOn()) { if (cb != null) cb.run(null); return; } super.makeSureSecretPhraseBackedUpLocally(s, p, cb); }
        @Override public TransactionsList getTransactions() {
            final TransactionsList list = super.getTransactions();
            if (MeridaVisual.gramsOn() && list != null) {
                final ArrayList<TL_wallet.walletTransaction> vtx = MeridaVisualWallet.getTransactions(currentAccount);
                if (list.transactions.size() != vtx.size()) {
                    list.transactions.clear(); list.transactions.addAll(vtx);
                    list.allTransactions.clear(); list.allTransactions.addAll(vtx);
                }
            }
            return list;
        }
        @Override public void resolveWallet(TLRPC.User u, boolean f, Utilities.Callback2<TL_wallet.walletUserAddress, String> cb) { if (MeridaVisualWallet.resolve(u, cb)) return; super.resolveWallet(u, f, cb); }
        @Override public void resolveWallet(String a, Utilities.Callback2<TL_wallet.walletUserAddress, String> cb) {
            if (MeridaVisual.gramsOn() && a != null && cb != null) {
                final TL_wallet.walletUserAddress r = new TL_wallet.walletUserAddress();
                r.user_id = UserConfig.getInstance(currentAccount).getClientUserId(); r.address = a;
                cb.run(r, null); return;
            }
            super.resolveWallet(a, cb);
        }
        @Override public Runnable emulateSend(String a, long amt, String c, byte[] k, Utilities.Callback2<TL_wallet.walletTransaction, String> cb) {
            if (MeridaVisual.gramsOn()) {
                final TL_wallet.walletTransaction tx = new TL_wallet.walletTransaction();
                tx.amount = amt; tx.fee = 5_000_000L; tx.date = (int) (System.currentTimeMillis() / 1000L); tx.comment = c;
                AndroidUtilities.runOnUIThread(() -> { if (cb != null) cb.run(tx, null); });
                return () -> {};
            }
            return super.emulateSend(a, amt, c, k, cb);
        }
        @Override public void send(TLRPC.User u, String a, long amt, String c, byte[] k, Utilities.Callback<String> ws) { if (MeridaVisualWallet.send(this, u, a, amt, c, ws, null)) return; super.send(u, a, amt, c, k, ws); }
        @Override public void send(TLRPC.User u, String a, long amt, String c, byte[] k, String d, Utilities.Callback<String> ws) { if (MeridaVisualWallet.send(this, u, a, amt, c, ws, null)) return; super.send(u, a, amt, c, k, d, ws); }
        @Override public void send(TLRPC.User u, String a, long amt, String c, byte[] k, String d, Utilities.Callback<String> ws, Utilities.Callback<TL_wallet.walletTransaction> wq) { if (MeridaVisualWallet.send(this, u, a, amt, c, ws, wq)) return; super.send(u, a, amt, c, k, d, ws, wq); }
    }
}
