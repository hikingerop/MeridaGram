package org.telegram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
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
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TableView;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.utils.settings.MeridaSettings;

/**
 * MeridaGram «Visual»: the window of a visual gift, laid out like Telegram's gift window — a header in the
 * NFT's backdrop and pattern with the model, «Wear / Hide / Remove» buttons and a table (owner, model, pattern,
 * backdrop, value). The owner is you, written with the «VISUAL» watermark; there is no «Buy», nothing is sent.
 */
public final class MeridaVisualGiftSheet {

    private MeridaVisualGiftSheet() {
    }

    private static String s(int res) {
        return LocaleController.getString(res);
    }

    public static void show(BaseFragment fragment, TL_stars.SavedStarGift saved, Runnable onChanged) {
        show(fragment, saved, onChanged, false);
    }

    /** celebrate: just upgraded — confetti and «Gift upgraded» over the window. */
    public static void show(BaseFragment fragment, TL_stars.SavedStarGift saved, Runnable onChanged, boolean celebrate) {
        if (fragment == null || fragment.getParentActivity() == null || saved == null || saved.gift == null) {
            return;
        }
        final Context context = fragment.getParentActivity();
        final int account = fragment.getCurrentAccount();
        final String key = MeridaVisualGifts.keyOf(saved);
        final TL_stars.StarGift gift = saved.gift;
        final boolean unique = gift instanceof TL_stars.TL_starGiftUnique;
        final TL_stars.starGiftAttributeModel model = unique ? StarsController.findAttribute(gift.attributes, TL_stars.starGiftAttributeModel.class) : null;
        final TL_stars.starGiftAttributePattern pattern = unique ? StarsController.findAttribute(gift.attributes, TL_stars.starGiftAttributePattern.class) : null;
        final TL_stars.starGiftAttributeBackdrop backdrop = unique ? StarsController.findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class) : null;

        final BottomSheet sheet = new BottomSheet(context, false);
        sheet.setBackgroundColor(MeridaVisualUi.SHEET_BG);
        sheet.fixNavigationBar(MeridaVisualUi.SHEET_BG);
        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        // ---- header: backdrop + pattern, model, title, buttons
        final FrameLayout header = new FrameLayout(context);
        if (backdrop != null) {
            // like Telegram's gift window: the glow and the pattern are centred on the model
            final MeridaGiftRoll.HeaderBackground bg = new MeridaGiftRoll.HeaderBackground(header, 22 + 90);
            bg.setBackdrop(backdrop, false);
            bg.setPattern(pattern, false);
            header.setBackground(bg);
        } else {
            header.setBackgroundColor(0xFF2A2A2C);
        }
        final int textColor = backdrop != null ? backdrop.text_color | 0xFF000000 : 0xFFAAAAAA;

        final BackupImageView image = new BackupImageView(context);
        final TLRPC.Document doc = model != null ? model.document : gift.sticker;
        if (doc != null) {
            final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, AndroidUtilities.dp(160));
            image.setImage(ImageLocation.getForDocument(doc), "200_200", ImageLocation.getForDocument(thumb, doc), "200_200", null, gift);
            image.getImageReceiver().setAutoRepeat(1);
        }
        header.addView(image, LayoutHelper.createFrame(180, 180, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 22, 0, 0));

        final ImageView more = new ImageView(context);
        more.setImageResource(R.drawable.ic_ab_other);
        more.setScaleType(ImageView.ScaleType.CENTER);
        more.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        more.setBackground(Theme.createSelectorDrawable(0x33FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        header.addView(more, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.RIGHT, 0, 12, 10, 0));

        final TextView title = new TextView(context);
        final SpannableStringBuilder titleText = new SpannableStringBuilder(gift.title != null ? gift.title : s(R.string.MeridaVisualGiftTitle));
        if (unique) {
            final int start = titleText.length();
            titleText.append(" #").append(String.valueOf(((TL_stars.TL_starGiftUnique) gift).num));
            titleText.setSpan(new ForegroundColorSpan(0xB3FFFFFF), start, titleText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        titleText.append(" ").append(MeridaVisual.mark());
        title.setText(titleText);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        header.addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 16, 210, 16, 0));
        final TextView subtitle = new TextView(context);
        final long salePrice = key != null ? MeridaVisualGifts.salePrice(key) : 0;
        if (salePrice > 0) {
            // like Telegram: «On sale for ⭐ N» in a pill under the name
            subtitle.setText(org.telegram.ui.Stars.StarsIntroActivity.replaceStarsWithPlain(LocaleController.formatString(R.string.MeridaVOnSaleFor, LocaleController.formatNumber(salePrice, ',')), .8f));
            subtitle.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(2), AndroidUtilities.dp(10), AndroidUtilities.dp(2));
            subtitle.setBackground(MeridaVisualUi.round(0x33000000, 12));
            subtitle.setTextColor(0xFFFFFFFF);
        } else {
            subtitle.setText(model != null ? model.name : gift.stars > 0 ? LocaleController.formatString(R.string.MeridaVisualSendGift, MeridaVisual.formatNumber(gift.stars)) : "");
        }
        subtitle.setTextColor(textColor);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        subtitle.setGravity(Gravity.CENTER);
        header.addView(subtitle, LayoutHelper.createFrame(salePrice > 0 ? LayoutHelper.WRAP_CONTENT : LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 16, 246, 16, 0));

        final LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(buttons, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 64, Gravity.TOP, 12, 284, 12, 16));
        final FrameLayout headerWrap = new FrameLayout(context);
        headerWrap.addView(header, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, unique ? 364 : 290));
        root.addView(headerWrap);

        final Runnable changed = () -> {
            sheet.dismiss();
            if (onChanged != null) onChanged.run();
        };
        final boolean upgradable = key != null && MeridaVisualUpgradeSheet.canUpgrade(gift);
        if (unique && key != null) {
            // like Telegram's gift window: Transfer / Wear / Sell
            addHeaderButton(buttons, R.drawable.filled_gift_transfer, s(R.string.Gift2ActionTransfer), () -> {
                sheet.dismiss();
                transfer(fragment, (TL_stars.TL_starGiftUnique) gift);
            });
            final boolean worn = key.equals(MeridaSettings.visualWorn.get());
            addHeaderButton(buttons, worn ? R.drawable.filled_crown_off : R.drawable.filled_crown_on, s(worn ? R.string.Gift2ActionWearOff : R.string.Gift2ActionWear), () -> {
                MeridaSettings.visualWorn.set(worn ? null : key);
                notifyStatus(account);
                changed.run();
            });
            if (salePrice > 0) {
                addHeaderButton(buttons, R.drawable.filled_gift_sell_off, s(R.string.MeridaVUnlist), () -> {
                    final AlertDialog.Builder b = new AlertDialog.Builder(context);
                    b.setTitle(LocaleController.formatString(R.string.MeridaVUnlistTitle, gift.title + " #" + ((TL_stars.TL_starGiftUnique) gift).num));
                    b.setMessage(s(R.string.MeridaVUnlistText));
                    b.setPositiveButton(s(R.string.MeridaVUnlist), (d, w) -> {
                        MeridaVisualGifts.setSalePrice(key, 0);
                        changed.run();
                    });
                    b.setNegativeButton(s(R.string.Cancel), null);
                    b.show();
                });
            } else {
                addHeaderButton(buttons, R.drawable.filled_gift_sell_on, s(R.string.MeridaVSell), () -> MeridaVisualSheets.askNumber(fragment, s(R.string.MeridaVSellPrice),
                        (int) Math.max(1, MeridaVisualGifts.priceInStars(gift)), value -> {
                            if (value > 0) {
                                MeridaVisualGifts.setSalePrice(key, value);
                                changed.run();
                            }
                        }));
            }
        }

        // ---- table
        final LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(14));
        final TableView table = new TableView(context, fragment.getResourceProvider());
        table.addRow(s(R.string.Gift2Owner), ownerView(context, account));
        final long addedAt = key != null ? MeridaVisualGifts.addedAt(key) : 0;
        if (!unique && addedAt > 0) {
            table.addRow(s(R.string.MeridaVDate), LocaleController.formatDateTime(addedAt / 1000, true));
        }
        if (!unique && gift.stars > 0) {
            table.addRow(s(R.string.MeridaVCost), org.telegram.ui.Stars.StarsIntroActivity.replaceStarsWithPlain("⭐️ " + LocaleController.formatNumber(gift.stars, ','), .8f));
        }
        if (model != null) StarGiftSheet.addAttributeRow(table, model);
        if (pattern != null) StarGiftSheet.addAttributeRow(table, pattern);
        if (backdrop != null) StarGiftSheet.addAttributeRow(table, backdrop);
        if (gift.availability_total > 0 && gift.availability_issued > 0) {
            table.addRow(s(R.string.Gift2Quantity), LocaleController.formatPluralStringComma("Gift2QuantityIssued1", gift.availability_issued)
                    + LocaleController.formatPluralStringComma("Gift2QuantityIssued2", gift.availability_total));
        }
        final long price = unique ? MeridaVisualGifts.priceInStars(gift) : 0;
        if (price > 0) {
            table.addRow(s(R.string.Gift2Value), MeridaVisualSheets.localPrice(fragment, price).replace("≈ ", "~"));
        }
        body.addView(table, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (key != null) {
            final boolean hidden = MeridaVisualGifts.isHidden(key);
            final TextView footer = new TextView(context);
            final SpannableStringBuilder f = new SpannableStringBuilder(s(hidden ? R.string.MeridaVGiftHiddenNote : R.string.MeridaVGiftVisibleNote)).append(' ');
            final int start = f.length();
            f.append(s(hidden ? R.string.MeridaVShow : R.string.MeridaVHide)).append(" ›");
            f.setSpan(new ForegroundColorSpan(MeridaVisualUi.BLUE), start, f.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            footer.setText(f);
            footer.setTextColor(0xFF8E8E93);
            footer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            footer.setGravity(Gravity.CENTER);
            footer.setOnClickListener(v -> {
                MeridaVisualGifts.setHidden(key, !hidden);
                changed.run();
            });
            body.addView(footer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));
        }
        final TextView note = new TextView(context);
        note.setText(s(R.string.MeridaVisualGiftNote));
        note.setTextColor(0xFF6D6D72);
        note.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        note.setGravity(Gravity.CENTER);
        body.addView(note, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
        final TextView ok = MeridaVisualUi.bigButton(context, upgradable ? "⬆ " + s(R.string.MeridaVUpgrade) : s(R.string.OK));
        ok.setOnClickListener(v -> {
            sheet.dismiss();
            if (upgradable) {
                MeridaVisualUpgradeSheet.show(fragment, key, onChanged);
            }
        });
        body.addView(ok, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52, 0, 14, 0, 0));
        root.addView(body);

        final String slug = MeridaVisualGifts.slugOf(saved);
        more.setOnClickListener(v -> {
            final java.util.ArrayList<CharSequence> items = new java.util.ArrayList<>();
            final java.util.ArrayList<Runnable> actions = new java.util.ArrayList<>();
            if (key != null) {
                final boolean hiddenNow = MeridaVisualGifts.isHidden(key);
                items.add(s(hiddenNow ? R.string.MeridaVShowInProfileAction : R.string.MeridaVHideFromProfile));
                actions.add(() -> {
                    MeridaVisualGifts.setHidden(key, !hiddenNow);
                    changed.run();
                });
            }
            if (slug != null && !slug.isEmpty()) {
                items.add(s(R.string.CopyLink));
                actions.add(() -> AndroidUtilities.addToClipboard("https://t.me/nft/" + slug));
                items.add(s(R.string.MeridaVisualRealPage));
                actions.add(() -> Browser.openUrl(context, "https://t.me/nft/" + slug));
            }
            if (key != null) {
                items.add(s(R.string.MeridaVisualRemoveGift));
                actions.add(() -> {
                    MeridaVisualGifts.remove(key);
                    if (key.equals(MeridaSettings.visualWorn.get())) {
                        MeridaSettings.visualWorn.set(null);
                        notifyStatus(account);
                    }
                    changed.run();
                });
            }
            if (items.isEmpty()) {
                return;
            }
            final AlertDialog.Builder b = new AlertDialog.Builder(context);
            b.setItems(items.toArray(new CharSequence[0]), (d, which) -> actions.get(which).run());
            b.show();
        });

        final ScrollView scroll = new ScrollView(context);
        scroll.addView(root);
        // the window is as tall as its content, like Telegram's (the confetti layer must not stretch it)
        final org.telegram.ui.Components.FireworksOverlay fireworks = new org.telegram.ui.Components.FireworksOverlay(context);
        sheet.setCustomView(MeridaVisualUi.withOverlay(context, scroll, fireworks));
        sheet.show();
        if (celebrate) {
            // like Telegram after an upgrade: confetti and «Gift upgraded»
            AndroidUtilities.runOnUIThread(() -> {
                fireworks.start(true);
                final String product = gift.title + (unique ? " #" + LocaleController.formatNumber(((TL_stars.TL_starGiftUnique) gift).num, ',') : "");
                try {
                    org.telegram.ui.Components.BulletinFactory.of(sheet.getContainer(), null)
                            .createSimpleBulletin(R.raw.gift_upgrade, s(R.string.Gift2UpgradedTitle), AndroidUtilities.replaceTags(LocaleController.formatString(R.string.Gift2UpgradedText, product)))
                            .show();
                } catch (Throwable ignore) {
                }
            }, 250);
        }
    }

    /**
     * «Transfer»: choose a chat; it gets the honest visual-gift message (plain text saying it is not a real gift,
     * shown as a card with the VISUAL mark in MeridaGram). Nothing real is transferred.
     */
    public static void transfer(BaseFragment fragment, TL_stars.TL_starGiftUnique gift) {
        final android.os.Bundle args = new android.os.Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        args.putInt("messagesCount", 1);
        final DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((dialogsFragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            final String text = org.telegram.messenger.merida.MeridaVisualSend.build(
                    org.telegram.messenger.merida.MeridaVisualSend.nftItem(gift.title, gift.num, gift.slug == null ? "" : gift.slug));
            final int account = dialogsFragment.getCurrentAccount();
            for (org.telegram.messenger.MessagesStorage.TopicKey key : dids) {
                org.telegram.messenger.SendMessagesHelper.getInstance(account).sendMessage(
                        org.telegram.messenger.SendMessagesHelper.SendMessageParams.of(text, key.dialogId));
            }
            dialogsFragment.finishFragment();
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    org.telegram.ui.Components.BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, s(R.string.MeridaVTransferred)).show();
                } catch (Throwable ignore) {
                }
            }, 350);
            return true;
        });
        fragment.presentFragment(picker);
    }

    private static void notifyStatus(int account) {
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_EMOJI_STATUS);
    }

    private static void addHeaderButton(LinearLayout buttons, int icon, CharSequence text, Runnable click) {
        final Context context = buttons.getContext();
        final LinearLayout b = new LinearLayout(context);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setBackground(MeridaVisualUi.round(0x29000000, 14));
        final ImageView iv = new ImageView(context);
        iv.setImageResource(icon);
        iv.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        b.addView(iv, LayoutHelper.createLinear(24, 24));
        final TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        b.addView(tv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        b.setOnClickListener(v -> click.run());
        buttons.addView(b, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, 4, 0, 4, 0));
    }

    /** «Owner»: your avatar and name with «VISUAL» written over the name. */
    static View ownerView(Context context, int account) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final TLRPC.User me = UserConfig.getInstance(account).getCurrentUser();
        final BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(AndroidUtilities.dp(12));
        if (me != null) {
            final AvatarDrawable ad = new AvatarDrawable();
            ad.setInfo(account, me);
            avatar.setForUserOrChat(me, ad);
        }
        row.addView(avatar, LayoutHelper.createLinear(24, 24, 0, 0, 8, 0));
        final TextView name = new TextView(context);
        final String n = me != null ? UserObject.getUserName(me) : "";
        name.setText(MeridaVisual.watermark(n.isEmpty() ? "—" : n));
        name.setTextColor(MeridaVisualUi.BLUE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        name.setSingleLine(true);
        row.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        return row;
    }
}
