package org.telegram.messenger.merida;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;

/**
 * MeridaGram «Visual» in chats, only on this phone. What goes to Telegram stays the honest text message
 * (MeridaVisualSend: «visual gift, not real»). Here, on your own phone, your own such message about a visual
 * NFT is DRAWN as Telegram's gift card: the stored message is untouched, MeridaGram swaps in a local copy —
 * a service message with the gift action — only for showing it. The card and its line keep the «VISUAL» mark.
 */
public final class MeridaVisualChat {

    private MeridaVisualChat() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("merida_visual_gifts", Context.MODE_PRIVATE);
    }

    /** Confetti over the whole screen, like Telegram when a gift arrives. */
    private static long lastCelebrate;

    public static void celebrate() {
        final long now = System.currentTimeMillis();
        if (now - lastCelebrate < 4000) return; // once per transfer / purchase
        lastCelebrate = now;
        try {
            if (org.telegram.ui.LaunchActivity.instance != null) {
                org.telegram.ui.LaunchActivity.instance.getFireworksOverlay().start(true);
            }
        } catch (Throwable ignore) {
        }
    }

    /** Opens MeridaGram's own window of the visual NFT a card is about (Telegram's window would ask the server). */
    public static boolean openCard(MessageObject messageObject) {
        final String key = keyOf(messageObject);
        final org.telegram.ui.ActionBar.BaseFragment fragment = org.telegram.ui.LaunchActivity.getSafeLastFragment();
        if (key == null || fragment == null) return false;
        final TL_stars.SavedStarGift saved = MeridaVisualGifts.wrap(key);
        if (saved == null) return false;
        org.telegram.ui.MeridaVisualGiftSheet.show(fragment, saved, null);
        return true;
    }

    /**
     * After a visual purchase of an NFT for yourself: the honest visual-gift message goes to your Saved Messages
     * (only you see it), which opens; on this phone it is drawn as Telegram's «Purchased gift» card with the mark.
     */
    public static void afterPurchase(TL_stars.StarGift gift, String key) {
        if (gift == null) return;
        final int account = UserConfig.selectedAccount;
        final long self = UserConfig.getInstance(account).getClientUserId();
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
            try {
                final String item;
                if (gift instanceof TL_stars.TL_starGiftUnique) {
                    final TL_stars.TL_starGiftUnique u = (TL_stars.TL_starGiftUnique) gift;
                    item = MeridaVisualSend.nftItem(u.title, u.num, u.slug == null ? "" : u.slug);
                } else {
                    item = MeridaVisualSend.savedGiftItem(MeridaVisualGifts.priceInStars(gift), key);
                }
                final String text = MeridaVisualSend.build(item);
                org.telegram.messenger.SendMessagesHelper.getInstance(account).sendMessage(
                        org.telegram.messenger.SendMessagesHelper.SendMessageParams.of(text, self));
                final org.telegram.ui.ActionBar.BaseFragment fragment = org.telegram.ui.LaunchActivity.getSafeLastFragment();
                if (fragment != null) {
                    fragment.presentFragment(org.telegram.ui.ChatActivity.of(self));
                }
                org.telegram.messenger.AndroidUtilities.runOnUIThread(MeridaVisualChat::celebrate, 700);
            } catch (Throwable ignore) {
            }
        }, 350);
    }

    /** «Transfer»: who got the visual NFT (its window then shows that owner); 0 — still yours. */
    public static long transferredTo(String key) {
        return key == null ? 0 : prefs().getLong("to_" + key, 0);
    }

    public static void setTransferredTo(String key, long dialogId) {
        if (key != null) prefs().edit().putLong("to_" + key, dialogId).apply();
    }

    /** The stored key of a visual NFT by its name and number (the honest message carries only those and a link). */
    public static String findKey(String title, int num, String slug) {
        if (slug != null && !slug.isEmpty() && MeridaVisualGifts.load(slug) != null) {
            return slug;
        }
        for (String key : MeridaVisualGifts.slugs()) {
            final TL_stars.StarGift g = MeridaVisualGifts.loadAny(key);
            if (g instanceof TL_stars.TL_starGiftUnique && ((TL_stars.TL_starGiftUnique) g).num == num
                    && title != null && title.equals(g.title)) {
                return key;
            }
        }
        return null;
    }

    /** The key of the visual NFT a message (or its card) is about; null if it is not one of ours. */
    public static String keyOf(MessageObject messageObject) {
        if (messageObject == null) return null;
        TLRPC.Message m = messageObject.meridaOriginal != null ? messageObject.meridaOriginal : messageObject.messageOwner;
        return m == null ? null : keyOfText(m.message);
    }

    private static String keyOfText(String text) {
        if (!MeridaVisualSend.isVisual(text)) return null;
        final String[] lines = text.split("\n");
        if (lines.length < 3) return null;
        final String item = lines[1];
        final int g = item.indexOf(" · gift:");
        if (g >= 0) {
            final String key = item.substring(g + " · gift:".length()).trim();
            return MeridaVisualGifts.loadAny(key) != null ? key : null;
        }
        final int at = item.indexOf(" · t.me/nft/");
        final int hash = item.lastIndexOf(" #", at < 0 ? item.length() : at);
        if (at < 0 || hash < 0) return null;
        final String title = item.substring(0, hash);
        final int num;
        try {
            num = Integer.parseInt(item.substring(hash + 2, at).replace(",", "").replace(" ", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
        final String slug = item.substring(at + " · t.me/nft/".length()).trim();
        return findKey(title, num, slug);
    }

    /**
     * Your own sent visual-NFT message, already delivered, with the NFT stored on this phone: a local copy as a
     * service message with Telegram's gift action, so ChatActionCell draws the gift card. Otherwise — the message.
     * In your Saved Messages it reads as a purchase, elsewhere as a transfer.
     */
    public static TLRPC.Message asGiftCard(int account, TLRPC.Message m) {
        try {
            if (!(m instanceof TLRPC.TL_message) || !m.out || m.id <= 0 || m.send_state != 0 || !MeridaVisual.on()) {
                return m;
            }
            final String key = keyOfText(m.message);
            if (key == null) return m;
            final TL_stars.StarGift stored = MeridaVisualGifts.loadAny(key);
            if (stored == null) return m;
            final long self = UserConfig.getInstance(account).getClientUserId();
            final long dialogId = MessageObject.getDialogId(m);

            final TLRPC.TL_messageService s = new TLRPC.TL_messageService();
            s.id = m.id;
            s.flags = m.flags;
            s.out = true;
            s.unread = m.unread;
            s.media_unread = m.media_unread;
            s.from_id = m.from_id;
            s.peer_id = m.peer_id;
            s.saved_peer_id = m.saved_peer_id;
            s.reply_to = m.reply_to;
            s.date = m.date;
            s.dialog_id = m.dialog_id;
            s.send_state = m.send_state;

            if (!(stored instanceof TL_stars.TL_starGiftUnique)) {
                // an ordinary visual gift bought for yourself: Telegram's «Saved gift» card in Saved Messages
                if (dialogId != self) return m;
                final TLRPC.TL_messageActionStarGift og = new TLRPC.TL_messageActionStarGift();
                og.gift = stored;
                og.saved = true;
                og.can_upgrade = false;
                og.convert_stars = 0;
                s.action = og;
                return s;
            }
            final TL_stars.TL_starGiftUnique gift = (TL_stars.TL_starGiftUnique) stored;
            final TLRPC.TL_messageActionStarGiftUnique a = new TLRPC.TL_messageActionStarGiftUnique();
            a.gift = gift;
            final long value = MeridaVisualGifts.valueInStars(gift);
            if (dialogId == self && value > 0) {
                // «You bought a gift for N stars» in Saved Messages
                final TL_stars.TL_starsAmount amount = new TL_stars.TL_starsAmount();
                amount.amount = value;
                a.resale_amount = amount;
            } else {
                a.transferred = true;
            }
            s.action = a;
            return s;
        } catch (Throwable ignore) {
            return m;
        }
    }
}
