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

    /** The visual card for your profile, or null when Visual is off or there is no visual GRAM. */
    public static TL_wallet.card card() {
        if (!MeridaVisual.gramsOn()) {
            return null;
        }
        final TL_wallet.card card = new TL_wallet.card();
        card.address = "";
        card.balance = MeridaSettings.visualGrams.get() * 1_000_000_000L;
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
        final String worn = MeridaSettings.visualWorn.get();
        if (worn != null) {
            final TL_stars.TL_starGiftUnique gift = MeridaVisualGifts.load(worn);
            if (gift != null) card.bg_nft_item = item(worn, gift);
        }
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
