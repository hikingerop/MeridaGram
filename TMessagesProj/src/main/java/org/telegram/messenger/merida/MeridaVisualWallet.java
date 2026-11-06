package org.telegram.messenger.merida;

import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.utils.settings.MeridaSettings;

import java.util.ArrayList;

/**
 * MeridaGram «Visual»: Telegram's GRAM card for your own profile — the «Balance: 💎 N» pill under the header
 * buttons and the card that flies out of it (Telegram's own ProfileWalletCardDialog) — built from the visual
 * GRAM balance and your visual NFTs as stickers. Only on this phone: nothing is sent or saved to Telegram, the
 * card has no address and says «VISUAL» on it.
 */
public final class MeridaVisualWallet {

    private MeridaVisualWallet() {
    }

    /** Where the stickers sit on the card (centre x / y in %, width in %, rotation), like Telegram's examples. */
    private static final double[][] PLACES = {
            {47, 70, 27, 352},
            {77, 68, 29, 10},
            {20, 74, 22, 6},
    };

    /** The built card, kept until the gifts, the balance or the worn NFT change (the profile asks on every refresh). */
    private static TL_wallet.card cardCache;
    private static long cardVersion = -1;
    private static long cardGrams = -1;
    private static String cardWorn;
    public static final ArrayList<TL_wallet.walletTransaction> visualTx = new ArrayList<>();
    public static String addressFor(long uid) { final String cs = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-"; final java.util.Random r = new java.util.Random(uid ^ 0x544F4E43415244L); final StringBuilder sb = new StringBuilder("UQ"); for (int i = 0; i < 46; i++) sb.append(cs.charAt(r.nextInt(cs.length()))); return sb.toString(); }
    public static String address() { return addressFor(org.telegram.messenger.UserConfig.getInstance(org.telegram.messenger.UserConfig.selectedAccount).getClientUserId()); }
    public static ArrayList<TL_wallet.walletTransaction> getTransactions(int acc) { if (visualTx.isEmpty() && MeridaSettings.visualGrams.get() > 0) { long id = org.telegram.messenger.UserConfig.getInstance(acc).getClientUserId(); TL_wallet.walletTransaction t = new TL_wallet.walletTransaction(); t.incoming = true; t.amount = MeridaSettings.visualGrams.get() * 1_000_000_000L; t.date = (int)(System.currentTimeMillis() / 1000L) - 3600; t.comment = ""; TL_wallet.walletTransactionPeerUser p = new TL_wallet.walletTransactionPeerUser(); p.user_id = id; p.address = addressFor(id); t.peer = p; visualTx.add(t); } return visualTx; }
    public static boolean sync(org.telegram.ui.Wallet.GramWallet w) { if (!MeridaVisual.gramsOn() || w == null) return false; long tb = MeridaSettings.visualGrams.get() * 1_000_000_000L; String ta = addressFor(org.telegram.messenger.UserConfig.getInstance(w.currentAccount).getClientUserId()); if (w.getBalance() != tb || !java.util.Objects.equals(w.getAddress(), ta)) { TL_wallet.TL_walletState st = new TL_wallet.TL_walletState(); st.address = ta; st.balance = tb; st.backup_enabled = true; st.card = card(); w.updateState(st); } if (w.transactions != null) { ArrayList<TL_wallet.walletTransaction> l = getTransactions(w.currentAccount); if (w.transactions.transactions.size() != l.size()) { w.transactions.transactions.clear(); w.transactions.transactions.addAll(l); w.transactions.allTransactions.clear(); w.transactions.allTransactions.addAll(l); } } return true; }
    public static boolean resolve(org.telegram.tgnet.TLRPC.User u, org.telegram.messenger.Utilities.Callback2<TL_wallet.walletUserAddress, String> cb) { if (!MeridaVisual.gramsOn() || u == null || cb == null) return false; TL_wallet.walletUserAddress r = new TL_wallet.walletUserAddress(); r.user_id = u.id; r.address = addressFor(u.id); cb.run(r, null); return true; }
    public static boolean send(org.telegram.ui.Wallet.GramWallet w, org.telegram.tgnet.TLRPC.User u, String addr, long amt, String cmt, org.telegram.messenger.Utilities.Callback<String> ws, org.telegram.messenger.Utilities.Callback<TL_wallet.walletTransaction> wq) { if (!MeridaVisual.gramsOn() || w == null) return false; int acc = w.currentAccount; long self = org.telegram.messenger.UserConfig.getInstance(acc).getClientUserId(); long uid = u != null ? u.id : 0; boolean toSelf = (uid == self) || (addr != null && addr.equalsIgnoreCase(addressFor(self))); TL_wallet.walletTransaction t = new TL_wallet.walletTransaction(); t.incoming = toSelf; t.amount = amt; t.date = (int)(System.currentTimeMillis() / 1000L); t.comment = cmt != null ? cmt : ""; TL_wallet.walletTransactionPeerUser p = new TL_wallet.walletTransactionPeerUser(); p.user_id = uid != 0 ? uid : self; p.address = addr != null && !addr.isEmpty() ? addr : addressFor(p.user_id); t.peer = p; getTransactions(acc).add(0, t); int d = (int)Math.max(0L, amt / 1_000_000_000L); MeridaSettings.visualGrams.set(Math.max(0, MeridaSettings.visualGrams.get() + (toSelf ? d : -d))); cardCache = null; if (w.transactions != null) { w.transactions.transactions.clear(); w.transactions.allTransactions.clear(); } sync(w); if (wq != null) wq.run(t); if (ws != null) ws.run(null); return true; }
    /** The visual card for your profile, or null when Visual is off or there is no visual GRAM. */
    public static TL_wallet.card card() {
        if (!MeridaVisual.gramsOn()) {
            return null;
        }
        final long grams = MeridaSettings.visualGrams.get();
        final String worn = MeridaSettings.visualWorn.get();
        final long v = MeridaVisualGifts.version();
        if (cardCache != null && cardVersion == v && cardGrams == grams && java.util.Objects.equals(cardWorn, worn)) {
            return cardCache;
        }
        final TL_wallet.card card = new TL_wallet.card();
        final String charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-";
        final java.util.Random rng = new java.util.Random(org.telegram.messenger.UserConfig.getInstance(org.telegram.messenger.UserConfig.selectedAccount).getClientUserId() ^ 0x544F4E43415244L);
        final StringBuilder sb = new StringBuilder("UQ");
        for (int i = 0; i < 46; i++) {
            sb.append(charset.charAt(rng.nextInt(charset.length())));
        }
        card.address = sb.toString();
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
            c.x = PLACES[placed][0];
            c.y = PLACES[placed][1];
            c.w = PLACES[placed][2];
            c.h = PLACES[placed][2];
            c.rotation = PLACES[placed][3];
            sticker.coordinates = c;
            card.stickers.add(sticker);
            placed++;
        }
        // the NFT you wear gives the card its colours (when Telegram allows that collection as a card style)
        if (worn != null) {
            final TL_stars.TL_starGiftUnique gift = MeridaVisualGifts.load(worn);
            if (gift != null) card.bg_nft_item = item(worn, gift);
        }
        cardCache = card;
        cardVersion = v;
        cardGrams = grams;
        cardWorn = worn;
        return card;
    }

    private static TL_wallet.nftItem item(String key, TL_stars.StarGift gift) {
        final TL_wallet.nftItem item = new TL_wallet.nftItem();
        item.address = "merida-visual-" + key;
        item.owner_address = "";
        item.collection_address = "";
        item.index = gift instanceof TL_stars.TL_starGiftUnique ? String.valueOf(((TL_stars.TL_starGiftUnique) gift).num) : "";
        item.name = gift.title != null ? gift.title : "";
        item.description = "";
        item.gift = gift;
        return item;
    }
}
