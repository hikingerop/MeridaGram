package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.GiftAuctionController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.merida.MeridaVisual;
import org.telegram.messenger.merida.MeridaVisualGifts;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Gifts.ResaleGiftsFragment;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.utils.settings.MeridaSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * MeridaGram «Visual»: crafting a visual NFT in Telegram's own «Craft Gift» window — the anvil with the chance
 * in the middle, four slots (25% each), the odds of the models, the bronze / green button and the cube
 * animation. Slots take your visual NFTs of the same collection, or gifts on sale (paid with the visual stars
 * when you craft). The craft itself happens on this phone: Telegram is not asked, nothing real is spent;
 * the gifts in the slots are used up and, on success, a new visual NFT appears (with the VISUAL mark).
 */
public final class MeridaVisualCraftSheet {

    private MeridaVisualCraftSheet() {
    }

    /** Gifts on sale put into a slot: they are paid with visual stars only when you craft. */
    private static final Set<TL_stars.StarGift> market = Collections.newSetFromMap(new WeakHashMap<>());

    private static String s(int res) {
        return LocaleController.getString(res);
    }

    private static long marketTotal() {
        long sum = 0;
        synchronized (market) {
            for (TL_stars.StarGift g : market) sum += MeridaVisualGifts.priceInStars(g);
        }
        return sum;
    }

    /** A gift on sale fits only if the visual balance covers it together with the ones already chosen. */
    public static boolean canPickFromMarket(Context context, TL_stars.StarGift gift) {
        if (!MeridaSettings.visualSpendStars.get()) return true;
        final long price = MeridaVisualGifts.priceInStars(gift) + marketTotal();
        final int balance = MeridaSettings.visualStars.get();
        if (price > balance) {
            Toast.makeText(context, LocaleController.formatString(R.string.MeridaVisualBuyNoStars, MeridaVisual.formatNumber(price), MeridaVisual.formatNumber(balance)), Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    public static void pickFromMarket(TL_stars.StarGift gift) {
        synchronized (market) {
            market.add(gift);
        }
    }

    public static void show(BaseFragment fragment, String key, TL_stars.TL_starGiftUnique gift, Runnable onChanged) {
        final Context context = fragment != null ? fragment.getParentActivity() : null;
        if (context == null || key == null || gift == null) return;
        final int account = fragment.getCurrentAccount();
        synchronized (market) {
            market.clear();
        }

        final BottomSheet sheet = new BottomSheet(context, false);
        sheet.setApplyTopPadding(false);
        sheet.setBackgroundColor(0xFF0F192C);
        sheet.fixNavigationBar(0xFF0F192C);
        final FrameLayout holder = new FrameLayout(context);
        holder.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + AndroidUtilities.dp(12), AndroidUtilities.dp(12));
            }
        });
        holder.setClipToOutline(true);
        final StarGiftSheet.CraftTopView craft = new StarGiftSheet.CraftTopView(context, fragment.getResourceProvider());
        holder.addView(craft, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        sheet.setCustomView(holder);

        craft.setup(account, gift.gift_id, gift.getDocument(), gift.title);
        // the gift you opened is already in the first slot, like Telegram
        final TL_stars.SavedStarGift own = MeridaVisualGifts.wrap(key);
        if (own != null && own.gift != null) {
            MeridaVisualGifts.prepareForCraft(own.gift, key);
            craft.selectGift(own.gift);
        }

        final ResaleGiftsFragment.SelectGiftSheet.State state = new ResaleGiftsFragment.SelectGiftSheet.State(account, gift.gift_id, true);
        state.attach();
        final boolean[] changed = new boolean[1];
        final boolean[] crafting = new boolean[1];
        sheet.setOnDismissListener(d -> {
            state.detach();
            synchronized (market) {
                market.clear();
            }
            if (changed[0] && onChanged != null) onChanged.run();
        });
        craft.setOnClose(() -> {
            if (!crafting[0]) sheet.dismiss();
        });

        craft.setOnAddGift((onGift, first) -> {
            final HashSet<Long> ids = new HashSet<>();
            for (TL_stars.StarGift g : craft.meridaSelectedGifts()) ids.add(g.id);
            // a gift taken out of a slot no longer counts against the balance
            synchronized (market) {
                market.retainAll(craft.meridaSelectedGifts());
            }
            new ResaleGiftsFragment.SelectGiftSheet(context, gift.title, state)
                    .setMeridaVisual(true)
                    .without(ids)
                    .setWillBeFirst(first)
                    .setActionText(AndroidUtilities.replaceTags(LocaleController.formatPluralString("GiftCraftSelect", 4 - ids.size())))
                    .setOnSelect(onGift::run)
                    .show();
        });

        craft.setOnCraft((gifts, whenDone, onError) -> {
            crafting[0] = true;
            // gifts on sale in the slots are paid now, with the visual stars
            long cost = 0;
            synchronized (market) {
                for (TL_stars.StarGift g : gifts) {
                    if (market.contains(g)) cost += MeridaVisualGifts.priceInStars(g);
                }
            }
            if (MeridaSettings.visualSpendStars.get() && cost > 0) {
                final int balance = MeridaSettings.visualStars.get();
                if (cost > balance) {
                    crafting[0] = false;
                    Toast.makeText(context, LocaleController.formatString(R.string.MeridaVisualBuyNoStars, MeridaVisual.formatNumber(cost), MeridaVisual.formatNumber(balance)), Toast.LENGTH_LONG).show();
                    onError.run();
                    return;
                }
                MeridaSettings.visualStars.set((int) (balance - cost));
            }
            // your visual NFTs in the slots are used up, whatever the result (like Telegram)
            boolean tookOffWorn = false;
            for (TL_stars.StarGift g : gifts) {
                final String k = MeridaVisualGifts.keyOfGift(g);
                if (k == null) continue;
                MeridaVisualGifts.remove(k);
                if (k.equals(MeridaSettings.visualWorn.get())) {
                    MeridaSettings.visualWorn.set(null);
                    tookOffWorn = true;
                }
            }
            if (tookOffWorn) {
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_EMOJI_STATUS);
            }
            changed[0] = true;

            int chancePercent = 0;
            for (TL_stars.StarGift g : gifts) chancePercent += Math.max(0, g.craft_chance_permille);
            chancePercent /= 10;
            final int fixed = MeridaSettings.visualCraftChance.get();
            if (fixed >= 0) chancePercent = fixed;
            final Random random = new Random();
            final boolean success = MeridaSettings.visualCraftAlways.get() || random.nextInt(100) < chancePercent;
            if (!success) {
                AndroidUtilities.runOnUIThread(() -> {
                    crafting[0] = false;
                    whenDone.run(null, null);
                }, 900);
                return;
            }
            // the new gift: a model from the collection's craftable ones (Telegram's list), backdrop and pattern
            // from the gifts used
            GiftAuctionController.getInstance(account).requestAuctionUpgrades(gift.gift_id, attrs -> {
                final TL_stars.TL_starGiftUnique result = craftResult(gift, gifts, attrs, random);
                final String newKey = "craft-" + System.currentTimeMillis();
                MeridaVisualGifts.store(newKey, result);
                AndroidUtilities.runOnUIThread(() -> {
                    crafting[0] = false;
                    whenDone.run(result, () -> {
                        sheet.dismiss();
                        final TL_stars.SavedStarGift saved = MeridaVisualGifts.wrap(newKey);
                        if (saved != null) MeridaVisualGiftSheet.show(fragment, saved, null, onChanged, true);
                    });
                }, 400);
            });
        });
        sheet.show();
    }

    private static TL_stars.TL_starGiftUnique craftResult(TL_stars.TL_starGiftUnique base, ArrayList<TL_stars.StarGift> used,
                                                           ArrayList<TL_stars.StarGiftAttribute> attrs, Random random) {
        final TL_stars.TL_starGiftUnique result = new TL_stars.TL_starGiftUnique();
        result.id = base.gift_id;
        result.gift_id = base.gift_id;
        result.title = base.title;
        result.slug = "";
        result.crafted = true;
        result.availability_total = base.availability_total;
        result.availability_issued = base.availability_issued;
        result.num = Math.max(1, base.availability_total > 0 ? 1 + random.nextInt(base.availability_total) : 1 + random.nextInt(99999));

        TL_stars.starGiftAttributeModel model = null;
        if (attrs != null) {
            // Telegram's craftable models first (the ones shown in «Models you can get»), else any model
            final ArrayList<TL_stars.starGiftAttributeModel> models = new ArrayList<>();
            for (TL_stars.StarGiftAttribute a : GiftAuctionController.filterAttributes(attrs, true)) {
                if (a instanceof TL_stars.starGiftAttributeModel) models.add((TL_stars.starGiftAttributeModel) a);
            }
            if (models.isEmpty()) {
                for (TL_stars.StarGiftAttribute a : attrs) {
                    if (a instanceof TL_stars.starGiftAttributeModel) models.add((TL_stars.starGiftAttributeModel) a);
                }
            }
            if (!models.isEmpty()) model = models.get(random.nextInt(models.size()));
        }
        final TL_stars.StarGift m = used.get(random.nextInt(used.size()));
        final TL_stars.StarGift b = used.get(random.nextInt(used.size()));
        final TL_stars.StarGift p = used.get(random.nextInt(used.size()));
        if (model == null) model = StarsController.findAttribute(m.attributes, TL_stars.starGiftAttributeModel.class);
        final TL_stars.starGiftAttributePattern pattern = StarsController.findAttribute(p.attributes, TL_stars.starGiftAttributePattern.class);
        final TL_stars.starGiftAttributeBackdrop backdrop = StarsController.findAttribute(b.attributes, TL_stars.starGiftAttributeBackdrop.class);
        if (model != null) result.attributes.add(model);
        if (pattern != null) result.attributes.add(pattern);
        if (backdrop != null) result.attributes.add(backdrop);
        return result;
    }
}
