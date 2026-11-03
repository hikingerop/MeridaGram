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
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.merida.MeridaVisual;
import org.telegram.messenger.merida.MeridaVisualGifts;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ButtonSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FireworksOverlay;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TableView;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.bots.AffiliateProgramFragment;
import org.telegram.utils.settings.MeridaSettings;

import java.util.ArrayList;
import java.util.Random;

/**
 * MeridaGram «Visual»: upgrading a visual ordinary gift into a visual NFT, laid out and animated like Telegram's
 * (and Chimera's) upgrade: the «Upgrade gift» page with the possible models cycling, then — in the same window —
 * the NFT card: the reels roll frame by frame like Telegram's (MeridaGiftRoll — models glide by, backdrops slide,
 * the table values roll), slow down and stop; confetti, «Gift upgraded», OK. The window is as tall as its content. Only on this phone: Telegram is not asked, real stars are not spent.
 */
final class MeridaVisualUpgradeSheet {

    private MeridaVisualUpgradeSheet() {
    }

    private static String s(int res) {
        return LocaleController.getString(res);
    }

    static boolean canUpgrade(TL_stars.StarGift gift) {
        return gift != null && !(gift instanceof TL_stars.TL_starGiftUnique) && (gift.can_upgrade || gift.upgrade_stars > 0);
    }

    static void show(BaseFragment fragment, String key, Runnable onChanged) {
        final Context context = fragment.getParentActivity();
        final TL_stars.StarGift gift = key == null ? null : MeridaVisualGifts.loadAny(key);
        if (context == null || !canUpgrade(gift)) {
            return;
        }
        StarsController.getInstance(fragment.getCurrentAccount()).getStarGiftPreview(gift.id, preview -> AndroidUtilities.runOnUIThread(() -> {
            final ArrayList<TL_stars.starGiftAttributeModel> models = new ArrayList<>();
            final ArrayList<TL_stars.starGiftAttributeBackdrop> backdrops = new ArrayList<>();
            final ArrayList<TL_stars.starGiftAttributePattern> patterns = new ArrayList<>();
            if (preview != null && preview.sample_attributes != null) {
                for (TL_stars.StarGiftAttribute a : preview.sample_attributes) {
                    if (a instanceof TL_stars.starGiftAttributeModel) models.add((TL_stars.starGiftAttributeModel) a);
                    else if (a instanceof TL_stars.starGiftAttributeBackdrop) backdrops.add((TL_stars.starGiftAttributeBackdrop) a);
                    else if (a instanceof TL_stars.starGiftAttributePattern) patterns.add((TL_stars.starGiftAttributePattern) a);
                }
            }
            if (models.isEmpty() || backdrops.isEmpty() || patterns.isEmpty()) {
                Toast.makeText(context, s(R.string.MeridaNftConstructorNoData), Toast.LENGTH_LONG).show();
                return;
            }
            new Flow(fragment, key, gift, models, backdrops, patterns, onChanged).open();
        }));
    }


    private static final class Flow {
        final BaseFragment fragment;
        final Context context;
        final String key;
        final TL_stars.StarGift gift;
        final ArrayList<TL_stars.starGiftAttributeModel> models;
        final ArrayList<TL_stars.starGiftAttributeBackdrop> backdrops;
        final ArrayList<TL_stars.starGiftAttributePattern> patterns;
        final Runnable onChanged;
        final Random random = new Random();
        final long price;
        final int number;

        BottomSheet sheet;
        FrameLayout header;
        MeridaGiftRoll.HeaderBackground background;
        BackupImageView image;
        MeridaGiftRoll.StickersView stickers;
        MeridaGiftRoll.Roller roller;
        TextView title, subtitle;
        ImageView close, more;
        LinearLayout buttons, upgradePage, infoPage;
        ButtonWithCounterView button;
        TextView nextLink;
        FireworksOverlay fireworks;
        /** The possible looks change every ~1.7 s on the «Upgrade gift» page, like Telegram's. */
        static final int CYCLE_MS = 1700;

        TL_stars.starGiftAttributeModel model;
        TL_stars.starGiftAttributeBackdrop backdrop;
        TL_stars.starGiftAttributePattern pattern;
        TL_stars.starGiftAttributeModel resultModel;
        TL_stars.starGiftAttributeBackdrop resultBackdrop;
        TL_stars.starGiftAttributePattern resultPattern;
        int previewIndex;
        boolean rolling, done;

        Flow(BaseFragment fragment, String key, TL_stars.StarGift gift, ArrayList<TL_stars.starGiftAttributeModel> models,
             ArrayList<TL_stars.starGiftAttributeBackdrop> backdrops, ArrayList<TL_stars.starGiftAttributePattern> patterns, Runnable onChanged) {
            this.fragment = fragment;
            this.context = fragment.getParentActivity();
            this.key = key;
            this.gift = gift;
            this.models = models;
            this.backdrops = backdrops;
            this.patterns = patterns;
            this.onChanged = onChanged;
            this.price = Math.max(0, gift.upgrade_stars);
            final int total = gift.availability_total > 0 ? gift.availability_total : 10000;
            this.number = 1 + random.nextInt(total);
        }

        void open() {
            sheet = new BottomSheet(context, false);
            sheet.setApplyTopPadding(false); // the coloured header reaches the rounded top, like Telegram
            sheet.setBackgroundColor(MeridaVisualUi.SHEET_BG);
            sheet.fixNavigationBar(MeridaVisualUi.SHEET_BG);
            final LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            fireworks = new FireworksOverlay(context);

            // ---- header (shared by both pages): glow + pattern centred on the model
            header = new FrameLayout(context);
            header.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + AndroidUtilities.dp(12), AndroidUtilities.dp(12));
                }
            });
            header.setClipToOutline(true);
            background = new MeridaGiftRoll.HeaderBackground(header, 22 + 80);
            header.setBackground(background);
            image = new BackupImageView(context);
            image.setLayerNum(4 | 6656); // full speed inside the sheet, like Telegram's gift window
            header.addView(image, LayoutHelper.createFrame(160, 160, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 22, 0, 0));
            stickers = new MeridaGiftRoll.StickersView(context);
            header.addView(stickers, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 160, Gravity.TOP, 0, 22, 0, 0));
            roller = new MeridaGiftRoll.Roller(stickers, background);
            roller.preload(models, backdrops, patterns); // all models load while the first page is shown

            close = MeridaVisualUi.iconButton(context, R.drawable.ic_close_white, 0xFFFFFFFF);
            close.setBackground(MeridaVisualUi.round(0x33000000, 18));
            close.setOnClickListener(v -> sheet.dismiss());
            header.addView(close, LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.RIGHT, 0, 12, 12, 0));
            more = new ImageView(context);
            more.setImageResource(R.drawable.ic_ab_other);
            more.setScaleType(ImageView.ScaleType.CENTER);
            more.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
            more.setAlpha(0f);
            header.addView(more, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.RIGHT, 0, 10, 8, 0));
            title = new TextView(context);
            title.setTextColor(0xFFFFFFFF);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setTypeface(AndroidUtilities.bold());
            title.setGravity(Gravity.CENTER);
            header.addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 40, 184, 40, 0));
            subtitle = new TextView(context);
            subtitle.setTextColor(0xCCFFFFFF);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setGravity(Gravity.CENTER);
            header.addView(subtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 40, 212, 40, 18));
            buttons = new LinearLayout(context);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setAlpha(0f);
            buttons.setVisibility(View.GONE);
            header.addView(buttons, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP, 16, 246, 16, 18));
            root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 300));

            // ---- page 1: «Upgrade gift»
            upgradePage = new LinearLayout(context);
            upgradePage.setOrientation(LinearLayout.VERTICAL);
            upgradePage.setPadding(0, AndroidUtilities.dp(10), 0, 0);
            final AffiliateProgramFragment.FeatureCell f1 = new AffiliateProgramFragment.FeatureCell(context, fragment.getResourceProvider());
            f1.set(R.drawable.menu_feature_unique, s(R.string.Gift2UpgradeFeature1Title), s(R.string.GiftsFeature1Text));
            upgradePage.addView(f1);
            final AffiliateProgramFragment.FeatureCell f2 = new AffiliateProgramFragment.FeatureCell(context, fragment.getResourceProvider());
            f2.set(R.drawable.menu_feature_tradable, s(R.string.Gift2UpgradeFeature3Title), s(R.string.GiftsFeature2Text));
            upgradePage.addView(f2);
            final AffiliateProgramFragment.FeatureCell f3 = new AffiliateProgramFragment.FeatureCell(context, fragment.getResourceProvider());
            f3.set(R.drawable.menu_wear, s(R.string.GiftsFeature3Title), s(R.string.GiftsFeature3Text));
            upgradePage.addView(f3);
            root.addView(upgradePage);

            // ---- page 2: the NFT card; model / pattern / backdrop roll like reels
            infoPage = new LinearLayout(context);
            infoPage.setOrientation(LinearLayout.VERTICAL);
            infoPage.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14), 0);
            infoPage.setVisibility(View.GONE);
            final TableView table = new TableView(context, fragment.getResourceProvider());
            table.addRow(s(R.string.Gift2Owner), MeridaVisualGiftSheet.ownerView(context, fragment.getCurrentAccount()));
            roller.modelText = new MeridaGiftRoll.TextRoll(context, fragment.getResourceProvider());
            roller.patternText = new MeridaGiftRoll.TextRoll(context, fragment.getResourceProvider());
            roller.backdropText = new MeridaGiftRoll.TextRoll(context, fragment.getResourceProvider());
            table.addRowUnpadded(s(R.string.Gift2AttributeModel), roller.modelText);
            table.addRowUnpadded(s(R.string.Gift2AttributeSymbol), roller.patternText);
            table.addRowUnpadded(s(R.string.Gift2AttributeBackdrop), roller.backdropText);
            final int total = gift.availability_total > 0 ? gift.availability_total : 10000;
            table.addRow(s(R.string.Gift2Quantity), LocaleController.formatPluralStringComma("Gift2QuantityIssued1", total)
                    + LocaleController.formatPluralStringComma("Gift2QuantityIssued2", total));
            infoPage.addView(table, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            root.addView(infoPage);

            button = new ButtonWithCounterView(context, fragment.getResourceProvider()).setRound();
            button.setText(price > 0
                    ? StarsIntroActivity.replaceStars(LocaleController.formatString(R.string.Gift2UpgradeButton, (int) price), 1.13f)
                    : s(R.string.MeridaVUpgrade), false);
            button.setOnClickListener(v -> onButton());
            root.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 14, 12, 14, 14));
            // like Telegram after an upgrade: «Upgrade Next Gift» under the button, when there is one
            nextLink = new TextView(context);
            nextLink.setTextColor(MeridaVisualUi.BLUE);
            nextLink.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            nextLink.setGravity(Gravity.CENTER);
            nextLink.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(10));
            nextLink.setVisibility(View.GONE);
            ((LinearLayout.LayoutParams) button.getLayoutParams()).bottomMargin = AndroidUtilities.dp(8);
            root.addView(nextLink, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

            // first look: «Upgrade gift» with the possible models changing
            final SpannableStringBuilder t = new SpannableStringBuilder(s(R.string.Gift2UpgradeTitle)).append(' ').append(MeridaVisual.mark());
            title.setText(t);
            subtitle.setText(s(R.string.Gift2UpgradeText));
            nextPreview(false);
            AndroidUtilities.runOnUIThread(this::cycle, CYCLE_MS);

            // the window is as tall as its content (the confetti layer must not stretch it to the whole screen)
            sheet.setCustomView(MeridaVisualUi.withOverlay(context, root, fireworks));
            sheet.show();
        }

        void setDoc(BackupImageView view, TLRPC.Document doc) {
            if (doc == null) {
                view.clearImage();
                return;
            }
            final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, AndroidUtilities.dp(160));
            view.setImage(ImageLocation.getForDocument(doc), "160_160", ImageLocation.getForDocument(thumb, doc), "160_160", null, gift);
            view.getImageReceiver().setAutoRepeat(1);
        }

        void nextPreview(boolean animated) {
            final int i = previewIndex++;
            model = models.get(i % models.size());
            backdrop = backdrops.get(i % backdrops.size());
            pattern = patterns.get(i % patterns.size());
            background.setBackdrop(backdrop, animated);
            background.setPattern(pattern, animated);
            if (!animated) {
                setDoc(image, model.document);
                return;
            }
            final TL_stars.starGiftAttributeModel m = model;
            image.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(180).withEndAction(() -> {
                if (rolling || done) return;
                setDoc(image, m.document);
                image.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK).start();
            }).start();
        }

        void cycle() {
            if (sheet == null || !sheet.isShowing() || rolling || done) return;
            nextPreview(true);
            AndroidUtilities.runOnUIThread(this::cycle, CYCLE_MS);
        }

        void onButton() {
            if (done) {
                sheet.dismiss();
                return;
            }
            if (rolling) {
                roller.skip(); // «Skip»: the reels jump to the result
                return;
            }
            if (MeridaSettings.visualSpendStars.get() && price > MeridaSettings.visualStars.get()) {
                Toast.makeText(context, LocaleController.formatString(R.string.MeridaVisualBuyNoStars, MeridaVisual.formatNumber(price), MeridaVisual.formatNumber(MeridaSettings.visualStars.get())), Toast.LENGTH_LONG).show();
                return;
            }
            startRoll();
        }

        /** The page turns into the NFT card and the reels start rolling (Telegram's own roll maths). */
        void startRoll() {
            rolling = true;
            resultModel = models.get(random.nextInt(models.size()));
            resultBackdrop = backdrops.get(random.nextInt(backdrops.size()));
            resultPattern = patterns.get(random.nextInt(patterns.size()));
            save(); // the result is kept at once, even if the window is closed during the roll

            button.setText(s(R.string.GiftSkipAnimation), true);
            // title: «Name #number» (+VISUAL), the close button gives way to ⋮, the action buttons appear
            final SpannableStringBuilder t = new SpannableStringBuilder(gift.title != null ? gift.title : "NFT");
            final int start = t.length();
            t.append(" #").append(LocaleController.formatNumber(number, ','));
            t.setSpan(new ForegroundColorSpan(0xB3FFFFFF), start, t.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            t.append(' ').append(MeridaVisual.mark());
            title.setText(t);
            subtitle.setText(resultModel.name);
            subtitle.setTextColor(resultBackdrop.text_color | 0xFF000000);
            close.animate().alpha(0f).setDuration(200).withEndAction(() -> close.setVisibility(View.GONE)).start();
            more.animate().alpha(1f).setDuration(200).start();
            addHeaderButton(R.drawable.filled_crown_on, s(R.string.Gift2ActionWear));
            addHeaderButton(R.drawable.menu_hide_gift, s(R.string.MeridaVHide));
            addHeaderButton(R.drawable.msg_delete, s(R.string.MeridaVisualRemoveGift));
            buttons.setVisibility(View.VISIBLE);
            buttons.animate().alpha(0.5f).setDuration(250).start();
            ((LinearLayout.LayoutParams) header.getLayoutParams()).height = AndroidUtilities.dp(320); // Telegram: 184 + title + subtitle + 15 + 56 + 18
            header.requestLayout();
            upgradePage.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                upgradePage.setVisibility(View.GONE);
                infoPage.setAlpha(0f);
                infoPage.setVisibility(View.VISIBLE);
                infoPage.animate().alpha(1f).setDuration(200).start();
            }).start();

            // the shown model becomes the first frame of the roll (same picture, so nothing blinks)
            image.animate().cancel();
            image.setScaleX(1f);
            image.setScaleY(1f);
            image.setAlpha(1f);
            image.setVisibility(View.INVISIBLE);
            stickers.animate().translationY(-AndroidUtilities.dp(14)).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            roller.start(image.getImageReceiver(), model, pattern, backdrop, resultModel, resultPattern, resultBackdrop,
                    () -> {
                    }, this::rollingDone);
        }

        /** Keeps the visual NFT on this phone and takes the visual stars. */
        void save() {
            final TL_stars.TL_starGiftUnique unique = new TL_stars.TL_starGiftUnique();
            unique.id = gift.id;
            unique.gift_id = gift.id;
            unique.title = gift.title != null ? gift.title : "NFT";
            unique.slug = "";
            final int total = gift.availability_total > 0 ? gift.availability_total : 10000;
            unique.availability_total = total;
            unique.availability_issued = total;
            unique.num = number;
            unique.attributes.add(resultModel);
            unique.attributes.add(resultPattern);
            unique.attributes.add(resultBackdrop);
            MeridaVisualGifts.setValueStars(unique, Math.max(0, gift.stars) + price);
            MeridaVisualGifts.store(key, unique);
            if (MeridaSettings.visualSpendStars.get()) {
                MeridaSettings.visualStars.set((int) Math.max(0, MeridaSettings.visualStars.get() - price));
            }
            if (onChanged != null) {
                onChanged.run();
            }
        }

        /** The reels are stopping on the result: confetti, «Gift upgraded», the buttons work. */
        void rollingDone() {
            if (done) return;
            done = true;
            rolling = false;
            buttons.removeAllViews();
            addHeaderButton(R.drawable.filled_crown_on, s(R.string.Gift2ActionWear), () -> {
                MeridaSettings.visualWorn.set(key);
                NotificationCenter.getInstance(fragment.getCurrentAccount()).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_EMOJI_STATUS);
                sheet.dismiss();
            });
            addHeaderButton(R.drawable.menu_hide_gift, s(R.string.MeridaVHide), () -> {
                MeridaVisualGifts.setHidden(key, true);
                sheet.dismiss();
                if (onChanged != null) onChanged.run();
            });
            addHeaderButton(R.drawable.msg_delete, s(R.string.MeridaVisualRemoveGift), () -> {
                MeridaVisualGifts.remove(key);
                sheet.dismiss();
                if (onChanged != null) onChanged.run();
            });
            buttons.animate().alpha(1f).setDuration(200).start();
            button.setText(s(R.string.OK), true);
            final String nextKey = nextUpgradable();
            if (nextKey != null) {
                nextLink.setText(AndroidUtilities.replaceArrows(s(R.string.Gift2UpgradeNext) + " >", false, AndroidUtilities.dp(1), AndroidUtilities.dp(1)));
                nextLink.setAlpha(0f);
                nextLink.setVisibility(View.VISIBLE);
                nextLink.animate().alpha(1f).setDuration(220).start();
                nextLink.setOnClickListener(v -> {
                    sheet.dismiss();
                    AndroidUtilities.runOnUIThread(() -> MeridaVisualUpgradeSheet.show(fragment, nextKey, onChanged), 250);
                });
            }

            fireworks.start(true);
            final String product = (gift.title != null ? gift.title : "NFT") + " #" + LocaleController.formatNumber(number, ',');
            try {
                org.telegram.ui.Components.BulletinFactory.of(sheet.getContainer(), null)
                        .createSimpleBulletin(R.raw.gift_upgrade, s(R.string.Gift2UpgradedTitle), AndroidUtilities.replaceTags(LocaleController.formatString(R.string.Gift2UpgradedText, product)))
                        .show();
            } catch (Throwable ignore) {
            }
        }

        /** Another ordinary visual gift that can still be upgraded, or null. */
        String nextUpgradable() {
            for (TL_stars.SavedStarGift g : MeridaVisualGifts.savedGifts()) {
                final String k = MeridaVisualGifts.keyOf(g);
                if (k != null && !k.equals(key) && g.gift != null && canUpgrade(g.gift)) return k;
            }
            return null;
        }

        void addHeaderButton(int icon, CharSequence text) {
            addHeaderButton(icon, text, null);
        }

        /** Like Telegram's header buttons: 56 dp, icon at the top, 12 sp bold, filled with the result backdrop's colour. */
        void addHeaderButton(int icon, CharSequence text, Runnable click) {
            final FrameLayout b = new FrameLayout(context);
            final int color = resultBackdrop != null
                    ? androidx.core.graphics.ColorUtils.blendARGB(resultBackdrop.edge_color | 0xFF000000, resultBackdrop.pattern_color | 0xFF000000, .25f) : 0x29000000;
            b.setBackground(org.telegram.ui.ActionBar.Theme.createRadSelectorDrawable(color, 0x10FFFFFF, 16, 16));
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
            if (click != null) {
                b.setOnClickListener(v -> click.run());
            }
            buttons.addView(b, LayoutHelper.createLinear(0, 56, 1f, Gravity.FILL, 0, 0, buttons.getChildCount() < 2 ? 11 : 0, 0));
        }
    }
}
