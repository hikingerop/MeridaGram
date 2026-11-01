package org.telegram.messenger.merida;

import android.text.SpannableStringBuilder;
import android.text.Spanned;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.TypefaceSpan;

/**
 * MeridaGram «Visual» gifts sent to a chat. What really goes to Telegram is an ordinary text message that
 * says in plain words that the gift is not real, so in official Telegram the other person reads exactly
 * that. MeridaGram (yours and theirs) shows the same message as a gift card with the orange «VISUAL» mark.
 * Nothing is paid or credited: stars, GRAM and Premium are not touched.
 *
 * Message format (three lines): «🎁 <not-real title>», «<item>», «#MeridaVisual».
 */
public final class MeridaVisualSend {

    public static final String TAG = "#MeridaVisual";

    private MeridaVisualSend() {
    }

    public static String build(String item) {
        return "🎁 " + LocaleController.getString(R.string.MeridaVisualSendTitle) + "\n" + item + "\n" + TAG;
    }

    public static String nftItem(String title, int num, String slug) {
        return title + " #" + num + " · t.me/nft/" + slug;
    }

    public static String giftItem(long stars) {
        return "🎁 " + LocaleController.formatString(R.string.MeridaVisualSendGift, MeridaVisual.formatNumber(stars));
    }

    public static String starsItem(int stars) {
        return "⭐ " + LocaleController.formatString(R.string.MeridaVisualSendStars, MeridaVisual.formatNumber(stars));
    }

    public static String gramsItem(int grams) {
        return "💎 " + MeridaVisual.formatNumber(grams) + " GRAM";
    }

    public static String premiumItem(int months) {
        return "⭐ " + LocaleController.formatString(R.string.MeridaVisualSendPremium, months);
    }

    public static boolean isVisual(CharSequence text) {
        if (text == null) {
            return false;
        }
        final String s = text.toString();
        return s.startsWith("🎁 ") && s.endsWith("\n" + TAG) && s.split("\n").length >= 3;
    }

    public static CharSequence render(CharSequence text) {
        return render(text, false);
    }

    /** The NFT's real animated model for the sender's own copy of the message; null for stars, GRAM, Premium. */
    private static org.telegram.tgnet.TLRPC.Document modelOf(String itemLine) {
        final int i = itemLine.indexOf("t.me/nft/");
        if (i < 0) {
            return null;
        }
        final String slug = MeridaVisualGifts.parseSlug(itemLine.substring(i));
        final org.telegram.tgnet.tl.TL_stars.TL_starGiftUnique gift = slug == null ? null : MeridaVisualGifts.load(slug);
        if (gift == null) {
            return null;
        }
        final org.telegram.tgnet.tl.TL_stars.starGiftAttributeModel model =
                org.telegram.ui.Stars.StarsController.findAttribute(gift.attributes, org.telegram.tgnet.tl.TL_stars.starGiftAttributeModel.class);
        return model == null ? null : model.document;
    }

    /**
     * How MeridaGram shows such a message: a title, the orange mark, the item and a note that it is not real.
     * The sender's own copy also shows the NFT's animated model; the other person always has the plain-words text.
     */
    public static CharSequence render(CharSequence text, boolean out) {
        final String[] lines = text.toString().split("\n");
        final StringBuilder item = new StringBuilder();
        for (int i = 1; i < lines.length - 1; i++) {
            if (item.length() > 0) {
                item.append('\n');
            }
            item.append(lines[i]);
        }
        final SpannableStringBuilder sb = new SpannableStringBuilder();
        if (out) {
            try {
                final org.telegram.tgnet.TLRPC.Document model = modelOf(item.toString());
                if (model != null) {
                    sb.append("🎁");
                    sb.setSpan(new org.telegram.ui.Components.AnimatedEmojiSpan(model, 3.5f, null), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.append('\n');
                }
            } catch (Throwable ignore) {
            }
        }
        sb.append("🎁 ");
        final int titleStart = sb.length();
        sb.append(LocaleController.getString(R.string.MeridaVisualSendCardTitle));
        sb.setSpan(new TypefaceSpan(AndroidUtilities.bold()), titleStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(' ').append(MeridaVisual.mark());
        sb.append('\n');
        final int itemStart = sb.length();
        sb.append(item);
        sb.setSpan(new TypefaceSpan(AndroidUtilities.bold()), itemStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append('\n');
        final int noteStart = sb.length();
        sb.append(LocaleController.getString(R.string.MeridaVisualSendNote));
        sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), noteStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }
}
