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
        // like Telegram's gift window: the coloured header starts at the sheet's very top, rounded 12 dp
        sheet.setApplyTopPadding(false);
        sheet.setBackgroundColor(MeridaVisualUi.SHEET_BG);
        sheet.fixNavigationBar(MeridaVisualUi.SHEET_BG);
        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        // ---- header, with Telegram's own sizes (StarGiftSheet.TopView): model 160 dp at the top, title 20 sp,
        //      subtitle 14 sp, buttons 56 dp with 11 dp gaps; the header is as tall as its content
        final FrameLayout header = new FrameLayout(context);
        header.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + AndroidUtilities.dp(12), AndroidUtilities.dp(12));
            }
        });
        header.setClipToOutline(true);
        if (backdrop != null) {
            final MeridaGiftRoll.HeaderBackground bg = new MeridaGiftRoll.HeaderBackground(header, 8 + 80);
            bg.setBackdrop(backdrop, false);
            bg.setPattern(pattern, false);
            header.setBackground(bg);
        }
        final int textColor = backdrop != null ? backdrop.text_color | 0xFF000000 : 0xFF8E8E93;

        final BackupImageView image = new BackupImageView(context);
        // like Telegram's gift window: the model belongs to this sheet's layer, so it plays at full speed
        // (otherwise the sheet opening pauses / throttles «heavy» animations under it)
        image.setLayerNum(4 | 6656);
        final TLRPC.Document doc = model != null ? model.document : gift.sticker;
        if (doc != null) {
            final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, AndroidUtilities.dp(160));
            image.setImage(ImageLocation.getForDocument(doc), "160_160", ImageLocation.getForDocument(thumb, doc), "160_160", null, gift);
            image.getImageReceiver().setAutoRepeat(1);
        }
        header.addView(image, LayoutHelper.createFrame(160, 160, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));

        final ImageView more = new ImageView(context);
        more.setImageResource(R.drawable.media_more);
        more.setScaleType(ImageView.ScaleType.CENTER);
        if (backdrop == null) {
            more.setColorFilter(new PorterDuffColorFilter(0xFF8E8E93, PorterDuff.Mode.SRC_IN));
        }
        more.setBackground(Theme.createSelectorDrawable(0x20FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        header.addView(more, LayoutHelper.createFrame(42, 42, Gravity.TOP | Gravity.RIGHT, 0, 5, 5, 0));

        final LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(0, 0, 0, AndroidUtilities.dp(backdrop != null ? 18 : 3));
        header.addView(info, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 16, backdrop != null ? 24 + 160 : 8 + 160 + 2, 16, 0));

        final TextView title = new TextView(context);
        final SpannableStringBuilder titleText = new SpannableStringBuilder(gift.title != null ? gift.title : s(R.string.MeridaVisualGiftTitle));
        if (unique) {
            final int start = titleText.length();
            titleText.append(" #").append(LocaleController.formatNumber(((TL_stars.TL_starGiftUnique) gift).num, ','));
            titleText.setSpan(new ForegroundColorSpan(0xB3FFFFFF), start, titleText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        titleText.append(" ").append(MeridaVisual.mark());
        title.setText(titleText);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        info.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 0));
        final TextView subtitle = new TextView(context);
        final long salePrice = key != null ? MeridaVisualGifts.salePrice(key) : 0;
        if (salePrice > 0) {
            // like Telegram: «On sale for ⭐ N» in a pill under the name
            subtitle.setText(org.telegram.ui.Stars.StarsIntroActivity.replaceStarsWithPlain(LocaleController.formatString(R.string.MeridaVOnSaleFor, LocaleController.formatNumber(salePrice, ',')), .8f));
            subtitle.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
            subtitle.setBackground(MeridaVisualUi.round(0x33000000, 10));
            subtitle.setTypeface(AndroidUtilities.bold());
            subtitle.setTextColor(0xFFFFFFFF);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        } else {
            subtitle.setText(model != null ? model.name : gift.stars > 0 ? LocaleController.formatString(R.string.MeridaVisualSendGift, MeridaVisual.formatNumber(gift.stars)) : "");
            subtitle.setTextColor(textColor);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        }
        subtitle.setGravity(Gravity.CENTER);
        info.addView(subtitle, LayoutHelper.createLinear(salePrice > 0 ? LayoutHelper.WRAP_CONTENT : LayoutHelper.MATCH_PARENT, salePrice > 0 ? 20 : LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, backdrop != null ? 1.66f : 5, 24, 0));

        final LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        // Telegram paints the buttons in the backdrop's colour: edge mixed with the pattern colour
        buttons.setTag(backdrop != null ? androidx.core.graphics.ColorUtils.blendARGB(backdrop.edge_color | 0xFF000000, backdrop.pattern_color | 0xFF000000, .25f) : 0x10FFFFFF);
        if (unique && key != null) {
            info.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 0, 15, 0, 0));
        }
        root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

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
        body.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
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
        final long price = unique ? MeridaVisualGifts.valueInStars(gift) : 0;
        final String priceText = price > 0 ? MeridaVisualSheets.localPrice(fragment, price) : null;
        if (priceText != null) {
            // like Telegram: «Value  ~6 047 ₽  learn more»
            table.addRow(s(R.string.GiftValue2), priceText.replace("≈ ", "~"), s(R.string.GiftValue2LearnMore), () -> {
                final AlertDialog.Builder b = new AlertDialog.Builder(context);
                b.setTitle(s(R.string.GiftValue2));
                b.setMessage(LocaleController.formatString(R.string.MeridaVValueInfo, LocaleController.formatNumber(price, ',')));
                b.setPositiveButton(s(R.string.OK), null);
                b.show();
            });
        }
        body.addView(table, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        if (key != null) {
            final boolean hidden = MeridaVisualGifts.isHidden(key);
            final TextView footer = new TextView(context);
            final SpannableStringBuilder f = new SpannableStringBuilder(s(hidden ? R.string.MeridaVGiftHiddenNote : R.string.MeridaVGiftVisibleNote)).append(' ');
            final int start = f.length();
            f.append(s(hidden ? R.string.MeridaVShow : R.string.MeridaVHide)).append(" ›");
            f.setSpan(new ForegroundColorSpan(MeridaVisualUi.BLUE), start, f.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            footer.setText(f);
            footer.setTextColor(0xFF8E8E93);
            footer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            footer.setLineSpacing(AndroidUtilities.dp(2), 1f);
            footer.setGravity(Gravity.CENTER);
            footer.setOnClickListener(v -> {
                MeridaVisualGifts.setHidden(key, !hidden);
                changed.run();
            });
            body.addView(footer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 2, 4, 0));
        }
        final TextView note = new TextView(context);
        note.setText(s(R.string.MeridaVisualGiftNote));
        note.setTextColor(0xFF6D6D72);
        note.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        note.setGravity(Gravity.CENTER);
        body.addView(note, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 4, 4, 0));
        final org.telegram.ui.Stories.recorder.ButtonWithCounterView ok = new org.telegram.ui.Stories.recorder.ButtonWithCounterView(context, fragment.getResourceProvider()).setRound();
        ok.setText(upgradable ? "⬆ " + s(R.string.MeridaVUpgrade) : s(R.string.OK), false);
        ok.setOnClickListener(v -> {
            sheet.dismiss();
            if (upgradable) {
                MeridaVisualUpgradeSheet.show(fragment, key, onChanged);
            }
        });
        body.addView(ok, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 12, 0, 0));
        root.addView(body);

        final String slug = MeridaVisualGifts.slugOf(saved);
        // «⋮» — a pop-up menu like Telegram's: pin, link, share, hide, transfer (all only on this phone)
        more.setOnClickListener(v -> {
            final String link = slug != null && !slug.isEmpty() ? MessagesController.getInstance(account).linkPrefix + "/nft/" + slug : null;
            final boolean pinned = key != null && MeridaVisualGifts.isPinned(key);
            final boolean hiddenNow = key != null && MeridaVisualGifts.isHidden(key);
            org.telegram.ui.Components.ItemOptions.makeOptions(sheet.getContainer(), fragment.getResourceProvider(), more)
                    .addIf(unique && key != null, pinned ? R.drawable.msg_unpin : R.drawable.msg_pin, s(pinned ? R.string.Gift2Unpin : R.string.Gift2Pin), () -> {
                        MeridaVisualGifts.setPinned(key, !pinned);
                        changed.run();
                        org.telegram.ui.Components.BulletinFactory.of(fragment).createSimpleBulletin(pinned ? R.raw.ic_unpin : R.raw.ic_pin,
                                s(pinned ? R.string.Gift2Unpinned : R.string.Gift2PinnedTitle), pinned ? "" : s(R.string.MeridaVPinnedLocal)).show();
                    })
                    .addIf(link != null, R.drawable.msg_link, s(R.string.CopyLink), () -> {
                        AndroidUtilities.addToClipboard(link);
                        org.telegram.ui.Components.BulletinFactory.of(sheet.getContainer(), fragment.getResourceProvider()).createCopyLinkBulletin(false).show();
                    })
                    .addIf(link != null, R.drawable.msg_share, s(R.string.ShareFile), () ->
                            fragment.showDialog(org.telegram.ui.Components.ShareAlert.createShareAlert(context, null, link, false, link, false)))
                    .addIf(link != null, R.drawable.msg_view_file, s(R.string.MeridaVisualRealPage), () -> Browser.openUrl(context, link))
                    .addIf(key != null, hiddenNow ? R.drawable.menu_show_on_profile : R.drawable.menu_hide_from_profile, s(hiddenNow ? R.string.Gift2ShowOnProfile : R.string.Gift2HideFromProfile), () -> {
                        MeridaVisualGifts.setHidden(key, !hiddenNow);
                        changed.run();
                    })
                    .addIf(unique && key != null, R.drawable.menu_transfer, s(R.string.Gift2TransferOption), () -> {
                        sheet.dismiss();
                        transfer(fragment, (TL_stars.TL_starGiftUnique) gift);
                    })
                    .addIf(key != null, R.drawable.msg_delete, s(R.string.MeridaVisualRemoveGift), true, () -> {
                        MeridaVisualGifts.remove(key);
                        if (key.equals(MeridaSettings.visualWorn.get())) {
                            MeridaSettings.visualWorn.set(null);
                            notifyStatus(account);
                        }
                        changed.run();
                    })
                    .setDrawScrim(false)
                    .setOnTopOfScrim()
                    .setDimAlpha(0)
                    .translate(0, -AndroidUtilities.dp(2))
                    .show();
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
        final Context context = fragment.getParentActivity();
        if (context == null) return;
        final int account = fragment.getCurrentAccount();
        // the same «Transfer gift» picker as Telegram (search, frequent contacts), without «Send via Fragment»
        final org.telegram.ui.Components.Premium.boosts.UserSelectorBottomSheet picker = new org.telegram.ui.Components.Premium.boosts.UserSelectorBottomSheet(context, account, 0,
                org.telegram.messenger.BirthdayController.getInstance(account).getState(), org.telegram.ui.Components.Premium.boosts.UserSelectorBottomSheet.TYPE_TRANSFER, true, fragment.getResourceProvider());
        picker.setTitle(s(R.string.Gift2TransferShort));
        picker.setOnUserSelector(dialogId -> {
            if (dialogId == null || dialogId == 0) return;
            final String name = org.telegram.messenger.DialogObject.getShortName(account, dialogId);
            new AlertDialog.Builder(context, fragment.getResourceProvider())
                    .setTitle(s(R.string.Gift2TransferShort))
                    .setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.MeridaVTransferConfirm, name)))
                    .setPositiveButton(s(R.string.Send), (d, w) -> {
                        final String text = org.telegram.messenger.merida.MeridaVisualSend.build(
                                org.telegram.messenger.merida.MeridaVisualSend.nftItem(gift.title, gift.num, gift.slug == null ? "" : gift.slug));
                        org.telegram.messenger.SendMessagesHelper.getInstance(account).sendMessage(
                                org.telegram.messenger.SendMessagesHelper.SendMessageParams.of(text, dialogId));
                        picker.dismiss();
                        AndroidUtilities.runOnUIThread(() -> {
                            try {
                                org.telegram.ui.Components.BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, s(R.string.MeridaVTransferred)).show();
                            } catch (Throwable ignore) {
                            }
                        }, 300);
                    })
                    .setNegativeButton(s(R.string.Cancel), null)
                    .show();
        });
        picker.show();
    }

    private static void notifyStatus(int account) {
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_EMOJI_STATUS);
    }

    /** A header button like Telegram's: 56 dp, icon 24 dp at the top, 12 sp bold text, filled with the backdrop colour. */
    private static void addHeaderButton(LinearLayout buttons, int icon, CharSequence text, Runnable click) {
        final Context context = buttons.getContext();
        final FrameLayout b = new FrameLayout(context);
        final int color = buttons.getTag() instanceof Integer ? (Integer) buttons.getTag() : 0x10FFFFFF;
        b.setBackground(Theme.createRadSelectorDrawable(color, 0x10FFFFFF, 16, 16));
        final ImageView iv = new ImageView(context);
        iv.setImageResource(icon);
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        b.addView(iv, LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 8, 0, 0));
        final TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        b.addView(tv, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 4, 35, 4, 0));
        b.setOnClickListener(v -> click.run());
        org.telegram.ui.Components.ScaleStateListAnimator.apply(b, .075f, 1.5f);
        buttons.addView(b, LayoutHelper.createLinear(0, 56, 1f, Gravity.FILL, 0, 0, buttons.getChildCount() < 2 ? 11 : 0, 0));
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
