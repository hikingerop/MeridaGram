package org.telegram.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.merida.MeridaVisual;
import org.telegram.messenger.merida.MeridaVisualGifts;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.bots.AffiliateProgramFragment;
import org.telegram.utils.settings.MeridaSettings;

import java.util.ArrayList;
import java.util.Random;

/**
 * MeridaGram «Visual»: upgrading a visual ordinary gift into a visual NFT, like Telegram's «Upgrade gift»:
 * the possible models, backdrops and patterns cycle in the header; «Upgrade» spins them like a slot machine,
 * slows down and stops on the result, then confetti and «Gift upgraded». Only on this phone: Telegram is not asked,
 * real stars are not spent (visual stars are, if «spend stars» is on).
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
            open(fragment, key, gift, models, backdrops, patterns, onChanged);
        }));
    }

    private static void open(BaseFragment fragment, String key, TL_stars.StarGift gift,
                             ArrayList<TL_stars.starGiftAttributeModel> models,
                             ArrayList<TL_stars.starGiftAttributeBackdrop> backdrops,
                             ArrayList<TL_stars.starGiftAttributePattern> patterns, Runnable onChanged) {
        final Context context = fragment.getParentActivity();
        final BottomSheet sheet = new BottomSheet(context, false);
        sheet.setBackgroundColor(MeridaVisualUi.SHEET_BG);
        sheet.fixNavigationBar(MeridaVisualUi.SHEET_BG);
        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        final FrameLayout header = new FrameLayout(context);
        final GiftSheet.CardBackground card = new GiftSheet.CardBackground(header, fragment.getResourceProvider(), false);
        card.setPadding(false);
        card.setRoundRadius(0);
        header.setBackground(card);
        final BackupImageView image = new BackupImageView(context);
        header.addView(image, LayoutHelper.createFrame(160, 160, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));
        final ImageView close = MeridaVisualUi.iconButton(context, R.drawable.ic_close_white, 0xFFFFFFFF);
        close.setOnClickListener(v -> sheet.dismiss());
        header.addView(close, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.RIGHT, 0, 10, 10, 0));
        final TextView title = new TextView(context);
        final android.text.SpannableStringBuilder t = new android.text.SpannableStringBuilder(s(R.string.Gift2UpgradeTitle)).append(' ').append(MeridaVisual.mark());
        title.setText(t);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        header.addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 16, 188, 16, 0));
        final TextView text = new TextView(context);
        text.setText(s(R.string.Gift2UpgradeText));
        text.setTextColor(0xCCFFFFFF);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        text.setGravity(Gravity.CENTER);
        header.addView(text, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 32, 218, 32, 18));
        root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 290));

        final LinearLayout features = new LinearLayout(context);
        features.setOrientation(LinearLayout.VERTICAL);
        features.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(4));
        final AffiliateProgramFragment.FeatureCell f1 = new AffiliateProgramFragment.FeatureCell(context, fragment.getResourceProvider());
        f1.set(R.drawable.menu_feature_unique, s(R.string.Gift2UpgradeFeature1Title), s(R.string.GiftsFeature1Text));
        features.addView(f1);
        final AffiliateProgramFragment.FeatureCell f2 = new AffiliateProgramFragment.FeatureCell(context, fragment.getResourceProvider());
        f2.set(R.drawable.menu_feature_tradable, s(R.string.Gift2UpgradeFeature3Title), s(R.string.GiftsFeature2Text));
        features.addView(f2);
        final AffiliateProgramFragment.FeatureCell f3 = new AffiliateProgramFragment.FeatureCell(context, fragment.getResourceProvider());
        f3.set(R.drawable.menu_wear, s(R.string.GiftsFeature3Title), s(R.string.GiftsFeature3Text));
        features.addView(f3);
        root.addView(features);

        final long price = Math.max(0, gift.upgrade_stars);
        final ButtonWithCounterView button = new ButtonWithCounterView(context, fragment.getResourceProvider()).setRound();
        final CharSequence buyText = price > 0
                ? StarsIntroActivity.replaceStars(LocaleController.formatString(R.string.Gift2UpgradeButton, (int) price), 1.13f)
                : s(R.string.MeridaVUpgrade);
        button.setText(buyText, false);
        root.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 14, 6, 14, 14));

        final Random random = new Random();
        final int[] index = {0};
        final boolean[] rolling = {false};
        final boolean[] done = {false};
        final TL_stars.StarGiftAttribute[] shown = new TL_stars.StarGiftAttribute[3];
        final Runnable[] apply = new Runnable[1];
        apply[0] = () -> {
            card.setBackdrop((TL_stars.starGiftAttributeBackdrop) shown[1]);
            card.setPattern((TL_stars.starGiftAttributePattern) shown[2]);
            header.invalidate();
            final TLRPC.Document doc = ((TL_stars.starGiftAttributeModel) shown[0]).document;
            if (doc != null) {
                final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, AndroidUtilities.dp(160));
                image.setImage(ImageLocation.getForDocument(doc), "160_160", ImageLocation.getForDocument(thumb, doc), "160_160", null, gift);
                image.getImageReceiver().setAutoRepeat(1);
            }
        };
        final Runnable next = () -> {
            final int i = index[0]++;
            shown[0] = models.get(i % models.size());
            shown[1] = backdrops.get(i % backdrops.size());
            shown[2] = patterns.get(i % patterns.size());
            apply[0].run();
        };
        next.run();

        // the preview cycles slowly while the sheet is open, like Telegram's
        final Runnable[] cycle = new Runnable[1];
        cycle[0] = () -> {
            if (!sheet.isShowing() || rolling[0] || done[0]) return;
            image.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(160).withEndAction(() -> {
                next.run();
                image.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start();
            }).start();
            AndroidUtilities.runOnUIThread(cycle[0], 2200);
        };
        AndroidUtilities.runOnUIThread(cycle[0], 2200);

        final Runnable finish = () -> {
            if (done[0]) return;
            done[0] = true;
            final TL_stars.starGiftAttributeModel model = (TL_stars.starGiftAttributeModel) shown[0];
            final TL_stars.starGiftAttributeBackdrop backdrop = (TL_stars.starGiftAttributeBackdrop) shown[1];
            final TL_stars.starGiftAttributePattern pattern = (TL_stars.starGiftAttributePattern) shown[2];
            final TL_stars.TL_starGiftUnique unique = new TL_stars.TL_starGiftUnique();
            unique.id = gift.id;
            unique.gift_id = gift.id;
            unique.title = gift.title != null ? gift.title : "NFT";
            unique.slug = "";
            final int total = gift.availability_total > 0 ? gift.availability_total : 10000;
            unique.availability_total = total;
            unique.availability_issued = total;
            unique.num = 1 + random.nextInt(total);
            unique.attributes.add(model);
            unique.attributes.add(pattern);
            unique.attributes.add(backdrop);
            MeridaVisualGifts.store(key, unique);
            if (MeridaSettings.visualSpendStars.get()) {
                MeridaSettings.visualStars.set((int) Math.max(0, MeridaSettings.visualStars.get() - price));
            }
            sheet.dismiss();
            final TL_stars.SavedStarGift saved = MeridaVisualGifts.wrap(key);
            if (saved != null) {
                MeridaVisualGiftSheet.show(fragment, saved, onChanged, true);
            }
            if (onChanged != null) {
                onChanged.run();
            }
        };

        button.setOnClickListener(v -> {
            if (rolling[0]) {
                finish.run();
                return;
            }
            if (MeridaSettings.visualSpendStars.get() && price > MeridaSettings.visualStars.get()) {
                Toast.makeText(context, LocaleController.formatString(R.string.MeridaVisualBuyNoStars, MeridaVisual.formatNumber(price), MeridaVisual.formatNumber(MeridaSettings.visualStars.get())), Toast.LENGTH_LONG).show();
                return;
            }
            rolling[0] = true;
            button.setText(s(R.string.GiftSkipAnimation), true);
            image.animate().cancel();
            image.setAlpha(1f);
            // slot-machine roll: quick changes that slow down, then the result
            final long[] delay = {55};
            final Runnable[] spin = new Runnable[1];
            spin[0] = () -> {
                if (done[0] || !sheet.isShowing()) return;
                shown[0] = models.get(random.nextInt(models.size()));
                shown[1] = backdrops.get(random.nextInt(backdrops.size()));
                shown[2] = patterns.get(random.nextInt(patterns.size()));
                apply[0].run();
                image.setScaleX(0.86f);
                image.setScaleY(0.86f);
                image.animate().scaleX(1f).scaleY(1f).setDuration(Math.min(delay[0], 240)).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
                delay[0] = (long) (delay[0] * 1.13f);
                if (delay[0] < 520) {
                    AndroidUtilities.runOnUIThread(spin[0], delay[0]);
                } else {
                    image.animate().scaleX(1.12f).scaleY(1.12f).setDuration(180).withEndAction(() ->
                            image.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK).withEndAction(finish).start()).start();
                }
            };
            AndroidUtilities.runOnUIThread(spin[0]);
        });

        sheet.setCustomView(root);
        sheet.show();
    }
}
