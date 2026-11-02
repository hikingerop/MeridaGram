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
 * the NFT card: models roll by in a carousel (the big one in the middle, smaller ones at the sides), the
 * backdrop, pattern and the table's model / pattern / backdrop change with them, slow down and stop; confetti,
 * «Gift upgraded», OK. Only on this phone: Telegram is not asked, real stars are not spent.
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
        GiftSheet.CardBackground card;
        final BackupImageView[] carousel = new BackupImageView[3];
        TextView title, subtitle;
        ImageView close, more;
        LinearLayout buttons, upgradePage, infoPage;
        ButtonWithCounterView button;
        FireworksOverlay fireworks;
        ButtonSpan.TextViewButtons modelValue, patternValue, backdropValue;

        TL_stars.starGiftAttributeModel model;
        TL_stars.starGiftAttributeBackdrop backdrop;
        TL_stars.starGiftAttributePattern pattern;
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
            sheet.setBackgroundColor(MeridaVisualUi.SHEET_BG);
            sheet.fixNavigationBar(MeridaVisualUi.SHEET_BG);
            final FrameLayout container = new FrameLayout(context);
            final LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            container.addView(root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            fireworks = new FireworksOverlay(context);
            container.addView(fireworks, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            // ---- header (shared by both pages)
            header = new FrameLayout(context);
            card = new GiftSheet.CardBackground(header, fragment.getResourceProvider(), false);
            card.setPadding(false);
            card.setRoundRadius(0);
            header.setBackground(card);
            header.setClipChildren(false);
            for (int i = 0; i < 3; i++) {
                carousel[i] = new BackupImageView(context);
                header.addView(carousel[i], LayoutHelper.createFrame(i == 1 ? 160 : 96, i == 1 ? 160 : 96, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, i == 1 ? 22 : 54, 0, 0));
                if (i != 1) {
                    carousel[i].setAlpha(0f);
                    carousel[i].setTranslationX(AndroidUtilities.dp(i == 0 ? -128 : 128));
                }
            }
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
            header.addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 16, 192, 16, 0));
            subtitle = new TextView(context);
            subtitle.setTextColor(0xCCFFFFFF);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setGravity(Gravity.CENTER);
            header.addView(subtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 28, 222, 28, 16));
            buttons = new LinearLayout(context);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setAlpha(0f);
            buttons.setVisibility(View.GONE);
            header.addView(buttons, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.TOP, 12, 252, 12, 14));
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

            // ---- page 2: the NFT card with its table (shown while rolling and after)
            infoPage = new LinearLayout(context);
            infoPage.setOrientation(LinearLayout.VERTICAL);
            infoPage.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14), 0);
            infoPage.setVisibility(View.GONE);
            final TableView table = new TableView(context, fragment.getResourceProvider());
            table.addRow(s(R.string.Gift2Owner), MeridaVisualGiftSheet.ownerView(context, fragment.getCurrentAccount()));
            modelValue = valueView();
            patternValue = valueView();
            backdropValue = valueView();
            table.addRow(s(R.string.Gift2AttributeModel), modelValue);
            table.addRow(s(R.string.Gift2AttributeSymbol), patternValue);
            table.addRow(s(R.string.Gift2AttributeBackdrop), backdropValue);
            final int total = gift.availability_total > 0 ? gift.availability_total : 10000;
            table.addRow(s(R.string.Gift2Quantity), LocaleController.formatPluralStringComma("Gift2QuantityIssued1", total)
                    + LocaleController.formatPluralStringComma("Gift2QuantityIssued2", total));
            infoPage.addView(table, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            final TextView note = new TextView(context);
            note.setText(s(R.string.MeridaVisualGiftNote));
            note.setTextColor(0xFF6D6D72);
            note.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            note.setGravity(Gravity.CENTER);
            infoPage.addView(note, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));
            root.addView(infoPage);

            button = new ButtonWithCounterView(context, fragment.getResourceProvider()).setRound();
            button.setText(price > 0
                    ? StarsIntroActivity.replaceStars(LocaleController.formatString(R.string.Gift2UpgradeButton, (int) price), 1.13f)
                    : s(R.string.MeridaVUpgrade), false);
            button.setOnClickListener(v -> onButton());
            root.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 14, 12, 14, 14));

            // first look: «Upgrade gift» with the possible models cycling
            final SpannableStringBuilder t = new SpannableStringBuilder(s(R.string.Gift2UpgradeTitle)).append(' ').append(MeridaVisual.mark());
            title.setText(t);
            subtitle.setText(s(R.string.Gift2UpgradeText));
            nextPreview(false);
            AndroidUtilities.runOnUIThread(this::cycle, 2200);

            sheet.setCustomView(container);
            sheet.show();
        }

        ButtonSpan.TextViewButtons valueView() {
            final ButtonSpan.TextViewButtons tv = new ButtonSpan.TextViewButtons(context);
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            tv.setSingleLine(true);
            return tv;
        }

        CharSequence attrText(TL_stars.StarGiftAttribute attr) {
            final Integer[] color = new Integer[1];
            final CharSequence rarity = StarGiftSheet.getRarityName(attr.rarity, color);
            final SpannableStringBuilder sb = new SpannableStringBuilder(attr.name != null ? attr.name : "");
            if (!TextUtils.isEmpty(rarity)) {
                sb.append(' ').append(ButtonSpan.make(rarity, null, fragment.getResourceProvider(), color[0]));
            }
            return sb;
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

        void applyTraits() {
            card.setBackdrop(backdrop);
            card.setPattern(pattern);
            header.invalidate();
            if (rolling || done) {
                subtitle.setText(model.name);
                subtitle.setTextColor(backdrop.text_color | 0xFF000000);
                modelValue.setText(attrText(model));
                patternValue.setText(attrText(pattern));
                backdropValue.setText(attrText(backdrop));
            }
        }

        void nextPreview(boolean animated) {
            final int i = previewIndex++;
            model = models.get(i % models.size());
            backdrop = backdrops.get(i % backdrops.size());
            pattern = patterns.get(i % patterns.size());
            if (!animated) {
                setDoc(carousel[1], model.document);
                applyTraits();
                return;
            }
            carousel[1].animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(160).withEndAction(() -> {
                setDoc(carousel[1], model.document);
                applyTraits();
                carousel[1].animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start();
            }).start();
        }

        void cycle() {
            if (sheet == null || !sheet.isShowing() || rolling || done) return;
            nextPreview(true);
            AndroidUtilities.runOnUIThread(this::cycle, 2200);
        }

        void onButton() {
            if (done) {
                sheet.dismiss();
                return;
            }
            if (rolling) {
                finish();
                return;
            }
            if (MeridaSettings.visualSpendStars.get() && price > MeridaSettings.visualStars.get()) {
                Toast.makeText(context, LocaleController.formatString(R.string.MeridaVisualBuyNoStars, MeridaVisual.formatNumber(price), MeridaVisual.formatNumber(MeridaSettings.visualStars.get())), Toast.LENGTH_LONG).show();
                return;
            }
            startRoll();
        }

        /** The page turns into the NFT card and the carousel starts rolling. */
        void startRoll() {
            rolling = true;
            button.setText(s(R.string.GiftSkipAnimation), true);
            // title: «Name #number» (+VISUAL), the close button gives way to ⋮, the action buttons appear
            final SpannableStringBuilder t = new SpannableStringBuilder(gift.title != null ? gift.title : "NFT");
            final int start = t.length();
            t.append(" #").append(LocaleController.formatNumber(number, ','));
            t.setSpan(new ForegroundColorSpan(0xB3FFFFFF), start, t.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            t.append(' ').append(MeridaVisual.mark());
            title.setText(t);
            close.animate().alpha(0f).setDuration(200).withEndAction(() -> close.setVisibility(View.GONE)).start();
            more.animate().alpha(1f).setDuration(200).start();
            addHeaderButton(R.drawable.filled_crown_on, s(R.string.Gift2ActionWear));
            addHeaderButton(R.drawable.menu_hide_gift, s(R.string.MeridaVHide));
            addHeaderButton(R.drawable.msg_delete, s(R.string.MeridaVisualRemoveGift));
            buttons.setVisibility(View.VISIBLE);
            buttons.animate().alpha(0.5f).setDuration(250).start();
            ((LinearLayout.LayoutParams) header.getLayoutParams()).height = AndroidUtilities.dp(324);
            header.requestLayout();
            upgradePage.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                upgradePage.setVisibility(View.GONE);
                infoPage.setAlpha(0f);
                infoPage.setVisibility(View.VISIBLE);
                infoPage.animate().alpha(1f).setDuration(220).start();
            }).start();
            // side models appear
            carousel[1].setTag(model);
            for (int i : new int[]{0, 2}) {
                final TL_stars.starGiftAttributeModel side = models.get(random.nextInt(models.size()));
                carousel[i].setTag(side);
                setDoc(carousel[i], side.document);
                carousel[i].animate().alpha(0.45f).setDuration(250).start();
            }
            applyTraits();
            roll(70);
        }

        /** One carousel step: everything slides left, the right model comes to the middle; steps slow down. */
        void roll(long step) {
            if (done || sheet == null || !sheet.isShowing()) return;
            final float shift = AndroidUtilities.dp(128);
            final long duration = Math.min(step, 420);
            for (int i = 0; i < 3; i++) {
                carousel[i].animate().cancel();
            }
            carousel[1].animate().translationX(-shift).scaleX(0.6f).scaleY(0.6f).alpha(0.45f).setDuration(duration).start();
            carousel[2].animate().translationX(0).scaleX(1.66f).scaleY(1.66f).alpha(1f).setDuration(duration).start();
            carousel[0].animate().translationX(-shift * 2).alpha(0f).setDuration(duration).withEndAction(() -> {
                if (done) return;
                // the right one is now in the middle: shift the pictures and reset positions
                final TL_stars.starGiftAttributeModel next = models.get(random.nextInt(models.size()));
                model = (TL_stars.starGiftAttributeModel) carousel[2].getTag();
                if (model == null) model = next;
                carousel[0].setTag(carousel[1].getTag());
                carousel[1].setTag(model);
                carousel[2].setTag(next);
                setDoc(carousel[0], ((TL_stars.starGiftAttributeModel) (carousel[0].getTag() != null ? carousel[0].getTag() : next)).document);
                setDoc(carousel[1], model.document);
                setDoc(carousel[2], next.document);
                resetCarousel();
                backdrop = backdrops.get(random.nextInt(backdrops.size()));
                pattern = patterns.get(random.nextInt(patterns.size()));
                applyTraits();
                final long nextStep = (long) (step * 1.14f);
                if (nextStep < 560) {
                    roll(nextStep);
                } else {
                    finish();
                }
            }).start();
        }

        void resetCarousel() {
            for (int i = 0; i < 3; i++) {
                carousel[i].animate().cancel();
                carousel[i].setTranslationX(i == 0 ? -AndroidUtilities.dp(128) : i == 2 ? AndroidUtilities.dp(128) : 0);
                carousel[i].setScaleX(1f);
                carousel[i].setScaleY(1f);
                carousel[i].setAlpha(i == 1 ? 1f : 0.45f);
            }
        }

        /** Stop on the result, keep it, confetti and «Gift upgraded». */
        void finish() {
            if (done) return;
            done = true;
            rolling = false;
            resetCarousel();
            if (model == null) model = models.get(random.nextInt(models.size()));
            setDoc(carousel[1], model.document);
            for (int i : new int[]{0, 2}) {
                carousel[i].animate().alpha(0f).setDuration(250).start();
            }
            carousel[1].setScaleX(1.12f);
            carousel[1].setScaleY(1.12f);
            carousel[1].animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK).start();
            rollingDone();
        }

        void rollingDone() {
            final boolean keepRolling = rolling;
            rolling = true; // so applyTraits fills the table
            applyTraits();
            rolling = keepRolling;

            final TL_stars.TL_starGiftUnique unique = new TL_stars.TL_starGiftUnique();
            unique.id = gift.id;
            unique.gift_id = gift.id;
            unique.title = gift.title != null ? gift.title : "NFT";
            unique.slug = "";
            final int total = gift.availability_total > 0 ? gift.availability_total : 10000;
            unique.availability_total = total;
            unique.availability_issued = total;
            unique.num = number;
            unique.attributes.add(model);
            unique.attributes.add(pattern);
            unique.attributes.add(backdrop);
            MeridaVisualGifts.store(key, unique);
            if (MeridaSettings.visualSpendStars.get()) {
                MeridaSettings.visualStars.set((int) Math.max(0, MeridaSettings.visualStars.get() - price));
            }
            if (onChanged != null) {
                onChanged.run();
            }

            // the buttons work now
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

            fireworks.start(true);
            final String product = unique.title + " #" + LocaleController.formatNumber(number, ',');
            try {
                org.telegram.ui.Components.BulletinFactory.of(sheet.getContainer(), null)
                        .createSimpleBulletin(R.raw.gift_upgrade, s(R.string.Gift2UpgradedTitle), AndroidUtilities.replaceTags(LocaleController.formatString(R.string.Gift2UpgradedText, product)))
                        .show();
            } catch (Throwable ignore) {
            }
        }

        void addHeaderButton(int icon, CharSequence text) {
            addHeaderButton(icon, text, null);
        }

        void addHeaderButton(int icon, CharSequence text, Runnable click) {
            final LinearLayout b = new LinearLayout(context);
            b.setOrientation(LinearLayout.VERTICAL);
            b.setGravity(Gravity.CENTER);
            b.setBackground(MeridaVisualUi.round(0x29000000, 14));
            final ImageView iv = new ImageView(context);
            iv.setImageResource(icon);
            iv.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
            b.addView(iv, LayoutHelper.createLinear(22, 22));
            final TextView tv = new TextView(context);
            tv.setText(text);
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            tv.setTypeface(AndroidUtilities.bold());
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            b.addView(tv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            if (click != null) {
                b.setOnClickListener(v -> click.run());
            }
            buttons.addView(b, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, 4, 0, 4, 0));
        }
    }
}
