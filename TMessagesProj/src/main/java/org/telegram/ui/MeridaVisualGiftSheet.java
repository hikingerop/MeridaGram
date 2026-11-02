package org.telegram.ui;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.merida.MeridaVisual;
import org.telegram.messenger.merida.MeridaVisualGifts;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.utils.settings.MeridaSettings;

/**
 * MeridaGram «Visual»: the info window of a visual gift in your profile. The owner shown is you, written with
 * the «VISUAL» watermark over the name; there is no «Buy» and no server action. It can be worn as a visual
 * status, removed from the profile, or its real page opened (with its real owner).
 */
public final class MeridaVisualGiftSheet {

    private MeridaVisualGiftSheet() {
    }

    public static void show(BaseFragment fragment, TL_stars.SavedStarGift saved, Runnable onChanged) {
        if (fragment == null || fragment.getParentActivity() == null || saved == null || saved.gift == null) {
            return;
        }
        final Context context = fragment.getParentActivity();
        final int account = fragment.getCurrentAccount();
        final String key = MeridaVisualGifts.keyOf(saved);
        final TL_stars.StarGift gift = saved.gift;
        final boolean unique = gift instanceof TL_stars.TL_starGiftUnique;

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);

        final FrameLayout previewFrame = new FrameLayout(context);
        final GiftSheet.GiftCell cell = new GiftSheet.GiftCell(context, account, fragment.getResourceProvider());
        cell.setStarsGift(saved, true, false);
        previewFrame.addView(cell, LayoutHelper.createFrame(130, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        layout.addView(previewFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        final TLRPC.User me = UserConfig.getInstance(account).getCurrentUser();
        final String myName = me != null ? UserObject.getUserName(me) : "";
        final SpannableStringBuilder owner = new SpannableStringBuilder(LocaleController.getString(R.string.MeridaVisualOwner)).append(": ");
        owner.append(MeridaVisual.watermark(myName.isEmpty() ? "—" : myName));
        addLine(layout, owner, true);

        if (unique) {
            final TL_stars.TL_starGiftUnique u = (TL_stars.TL_starGiftUnique) gift;
            final TL_stars.starGiftAttributeModel model = StarsController.findAttribute(u.attributes, TL_stars.starGiftAttributeModel.class);
            final TL_stars.starGiftAttributePattern pattern = StarsController.findAttribute(u.attributes, TL_stars.starGiftAttributePattern.class);
            final TL_stars.starGiftAttributeBackdrop backdrop = StarsController.findAttribute(u.attributes, TL_stars.starGiftAttributeBackdrop.class);
            if (model != null) addLine(layout, LocaleController.getString(R.string.MeridaNftModel) + ": " + model.name, false);
            if (pattern != null) addLine(layout, LocaleController.getString(R.string.MeridaNftPattern) + ": " + pattern.name, false);
            if (backdrop != null) addLine(layout, LocaleController.getString(R.string.MeridaNftBackdrop) + ": " + backdrop.name, false);
        } else if (gift.stars > 0) {
            addLine(layout, LocaleController.formatString(R.string.MeridaVisualSendGift, MeridaVisual.formatNumber(gift.stars)), false);
        }
        final TextView note = new TextView(context);
        note.setText(LocaleController.getString(R.string.MeridaVisualGiftNote));
        note.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        note.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        layout.addView(note, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 4));

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(unique ? gift.title + " #" + ((TL_stars.TL_starGiftUnique) gift).num : LocaleController.getString(R.string.MeridaVisualGiftTitle));
        builder.setView(layout);
        if (unique && key != null) {
            final boolean worn = key.equals(MeridaSettings.visualWorn.get());
            builder.setPositiveButton(LocaleController.getString(worn ? R.string.MeridaVisualTakeOff : R.string.MeridaVisualWear), (d, w) -> {
                MeridaSettings.visualWorn.set(worn ? null : key);
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_EMOJI_STATUS);
            });
        } else {
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        }
        if (key != null) {
            builder.setNeutralButton(LocaleController.getString(R.string.MeridaVisualRemoveGift), (d, w) -> {
                MeridaVisualGifts.remove(key);
                if (key.equals(MeridaSettings.visualWorn.get())) {
                    MeridaSettings.visualWorn.set(null);
                    NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_EMOJI_STATUS);
                }
                if (onChanged != null) {
                    onChanged.run();
                }
            });
        }
        final String slug = MeridaVisualGifts.slugOf(saved);
        if (slug != null && !slug.isEmpty()) {
            builder.setNegativeButton(LocaleController.getString(R.string.MeridaVisualRealPage), (d, w) -> Browser.openUrl(context, "https://t.me/nft/" + slug));
        }
        fragment.showDialog(builder.create());
    }

    private static void addLine(LinearLayout layout, CharSequence text, boolean bold) {
        final TextView tv = new TextView(layout.getContext());
        tv.setText(text);
        tv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        if (bold) {
            tv.setTypeface(AndroidUtilities.bold());
        }
        layout.addView(tv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
    }
}
