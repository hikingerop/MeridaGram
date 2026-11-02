package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.merida.MeridaVisual;
import org.telegram.messenger.merida.MeridaVisualGifts;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.utils.settings.MeridaSettings;

import java.util.ArrayList;
import java.util.Random;

/**
 * MeridaGram «Visual» NFT constructor, laid out like the owner's reference video: a «NFT catalog» grid of
 * collections with prices, then a collection page with a big header in the chosen backdrop and pattern,
 * the model, three chips (model / backdrop / pattern with rarity), a grid of variants and a floating
 * «Models / Backdrops / Patterns» bar. The result goes to your own profile as a visual gift with the
 * «VISUAL» ribbon; nothing is bought or sent.
 */
public class MeridaNftConstructorActivity extends BaseFragment {

    public static final int MODE_NFT = 0;
    public static final int MODE_GIFTS = 1;

    private final int mode;
    private int multiplier = 1;
    private int number = 1;
    private boolean randomTraits;
    private long collectionPrice;
    private TextView multiplierButton;

    public MeridaNftConstructorActivity() {
        this(MODE_NFT);
    }

    public MeridaNftConstructorActivity(int mode) {
        this.mode = mode;
    }

    private static final int TAB_MODELS = 0;
    private static final int TAB_BACKDROPS = 1;
    private static final int TAB_PATTERNS = 2;

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_COUNT = 1;
    private static final int TYPE_TILE = 2;

    private static final int GOLD = 0xFFF1A73A;
    private static final int BLUE = 0xFF3E9BFF;
    private static final int TILE = 0xFF161616;
    private static final int PAGE = 0xFF212121;

    private FrameLayout root;
    private RecyclerListView listView;
    private Adapter adapter;
    private LinearLayout tabBar;
    private final TextView[] tabLabels = new TextView[3];
    private final ImageView[] tabIcons = new ImageView[3];
    private final LinearLayout[] tabItems = new LinearLayout[3];

    private final ArrayList<TL_stars.StarGift> collections = new ArrayList<>();
    private TL_stars.StarGift collection;
    private final ArrayList<TL_stars.starGiftAttributeModel> models = new ArrayList<>();
    private final ArrayList<TL_stars.starGiftAttributeBackdrop> backdrops = new ArrayList<>();
    private final ArrayList<TL_stars.starGiftAttributePattern> patterns = new ArrayList<>();
    private TL_stars.starGiftAttributeModel model;
    private TL_stars.starGiftAttributeBackdrop backdrop;
    private TL_stars.starGiftAttributePattern pattern;
    private int tab = TAB_MODELS;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setBackgroundColor(PAGE);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        root = new FrameLayout(context);
        root.setBackgroundColor(PAGE);
        fragmentView = root;

        listView = new RecyclerListView(context);
        final GridLayoutManager layoutManager = new GridLayoutManager(context, 3);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.getItemViewType(position) == TYPE_TILE ? 1 : 3;
            }
        });
        listView.setLayoutManager(layoutManager);
        listView.setClipToPadding(false);
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter = new Adapter());
        listView.setOnItemClickListener((view, position) -> onTileClick(position));
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        // the bar lies over the list (not above it), so the collection page can use the whole screen
        actionBar.setAddToContainer(false);
        root.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        MeridaVisualUi.roundBackButton(actionBar);

        buildTabBar(context);
        multiplierButton = new TextView(context);
        multiplierButton.setTextColor(0xFFFFFFFF);
        multiplierButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        multiplierButton.setGravity(Gravity.CENTER);
        final GradientDrawable mbg = new GradientDrawable();
        mbg.setCornerRadius(AndroidUtilities.dp(22));
        mbg.setColor(0xFF2F8FE8);
        multiplierButton.setBackground(mbg);
        multiplierButton.setElevation(AndroidUtilities.dp(4));
        multiplierButton.setOnClickListener(v -> askCount(LocaleController.getString(R.string.MeridaVHowMany), count -> {
            multiplier = count;
            updateMultiplier();
        }));
        root.addView(multiplierButton, LayoutHelper.createFrame(64, 44, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 16, 24));
        updateMultiplier();
        showCatalog();
        return fragmentView;
    }

    @Override
    public int getNavigationBarColor() {
        return PAGE;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (collection != null) {
            if (invoked) {
                showCatalog();
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    // ---- catalog -----------------------------------------------------------------------------------

    private void showCatalog() {
        collection = null;
        actionBar.setVisibility(View.VISIBLE);
        tabBar.setVisibility(View.GONE);
        if (multiplierButton != null) {
            multiplierButton.setVisibility(View.VISIBLE);
        }
        actionBar.setTitle(LocaleController.getString(mode == MODE_GIFTS ? R.string.MeridaVGiftCatalog : R.string.MeridaNftCatalog));
        listView.setPadding(AndroidUtilities.dp(3), ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight + AndroidUtilities.dp(6), AndroidUtilities.dp(3), AndroidUtilities.dp(16));
        addBalanceView();

        final StarsController c = StarsController.getInstance(currentAccount);
        c.loadStarGifts();
        collections.clear();
        for (TL_stars.StarGift g : c.gifts) {
            if (g == null || g instanceof TL_stars.TL_starGiftUnique) {
                continue;
            }
            if (mode == MODE_GIFTS || g.can_upgrade || g.upgrade_stars > 0 || g.availability_resale > 0) {
                collections.add(g);
            }
        }
        if (mode == MODE_GIFTS) {
            // like Telegram's shop: sold-out gifts after the ones still on sale
            final ArrayList<TL_stars.StarGift> sold = new ArrayList<>();
            for (int i = collections.size() - 1; i >= 0; i--) {
                if (collections.get(i).sold_out) {
                    sold.add(0, collections.remove(i));
                }
            }
            collections.addAll(0, sold);
        }
        actionBar.setSubtitle(collections.isEmpty() ? LocaleController.getString(R.string.MeridaNftConstructorLoading)
                : mode == MODE_GIFTS ? collections.size() + " " + MeridaVisualUi.plural(collections.size(), "подарок", "подарка", "подарков") : collections.size() + " NFT");
        if (collections.isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> {
                if (collection == null && fragmentView != null && !c.gifts.isEmpty()) {
                    showCatalog();
                }
            }, 1500);
        }
        adapter.notifyDataSetChanged();
        listView.scrollToPosition(0);
    }

    private TextView balanceView;

    /** «Balance» on the right of the catalog's top bar: the visual stars and GRAM with the VISUAL mark. */
    private void addBalanceView() {
        if (balanceView == null) {
            balanceView = new TextView(getContext());
            balanceView.setTextColor(0xFFFFFFFF);
            balanceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            balanceView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            actionBar.addView(balanceView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 14, 0));
        }
        final int stars = MeridaSettings.visualStars.get();
        final int grams = MeridaSettings.visualGrams.get();
        if (!MeridaVisual.on()) {
            balanceView.setVisibility(View.GONE);
            return;
        }
        final android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(LocaleController.getString(R.string.MeridaNftBalance));
        sb.append(' ').append(MeridaVisual.mark()).append('\n');
        sb.append("💎 ").append(MeridaVisual.formatNumber(grams)).append("  ");
        sb.append(org.telegram.ui.Stars.StarsIntroActivity.replaceStarsWithPlain("⭐️ " + MeridaVisual.formatNumber(stars), .85f));
        balanceView.setText(sb);
        balanceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        balanceView.setVisibility(View.VISIBLE);
    }

    // ---- collection page ---------------------------------------------------------------------------

    private void openCollection(TL_stars.StarGift g) {
        collection = g;
        models.clear();
        backdrops.clear();
        patterns.clear();
        actionBar.setSubtitle(LocaleController.getString(R.string.MeridaNftConstructorLoading));
        final TL_stars.getResaleStarGifts req = new TL_stars.getResaleStarGifts();
        req.flags |= 1;
        req.attributes_hash = 0;
        req.gift_id = g.id;
        req.offset = "";
        req.limit = 1;
        req.sort_by_price = true;
        collectionPrice = g.resell_min_stars > 0 ? g.resell_min_stars : g.stars + Math.max(0, g.upgrade_stars);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (collection != g || fragmentView == null) {
                return;
            }
            if (res instanceof TL_stars.resaleStarGifts) {
                final TL_stars.resaleStarGifts r = (TL_stars.resaleStarGifts) res;
                takeAttributes(r.attributes);
                if (!r.gifts.isEmpty()) {
                    final long p = MeridaVisualGifts.priceInStars(r.gifts.get(0));
                    if (p > 0) {
                        collectionPrice = p;
                    }
                }
            }
            if (models.isEmpty() || backdrops.isEmpty() || patterns.isEmpty()) {
                loadPreviewAttributes(g);
            } else {
                showConstructor();
            }
        }));
    }

    /** Collections with no market yet: Telegram's upgrade preview still gives a few real variants. */
    private void loadPreviewAttributes(TL_stars.StarGift g) {
        final TL_stars.getStarGiftUpgradePreview req = new TL_stars.getStarGiftUpgradePreview();
        req.gift_id = g.id;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (collection != g || fragmentView == null) {
                return;
            }
            if (res instanceof TL_stars.starGiftUpgradePreview) {
                takeAttributes(((TL_stars.starGiftUpgradePreview) res).sample_attributes);
            }
            if (models.isEmpty() || backdrops.isEmpty() || patterns.isEmpty()) {
                Toast.makeText(getContext(), LocaleController.getString(R.string.MeridaNftConstructorNoData), Toast.LENGTH_LONG).show();
                showCatalog();
            } else {
                showConstructor();
            }
        }));
    }

    private void takeAttributes(ArrayList<TL_stars.StarGiftAttribute> list) {
        for (TL_stars.StarGiftAttribute a : list) {
            if (a instanceof TL_stars.starGiftAttributeModel && !models.contains(a)) {
                models.add((TL_stars.starGiftAttributeModel) a);
            } else if (a instanceof TL_stars.starGiftAttributeBackdrop && !backdrops.contains(a)) {
                backdrops.add((TL_stars.starGiftAttributeBackdrop) a);
            } else if (a instanceof TL_stars.starGiftAttributePattern && !patterns.contains(a)) {
                patterns.add((TL_stars.starGiftAttributePattern) a);
            }
        }
    }

    private void showConstructor() {
        model = models.get(0);
        backdrop = backdrops.get(0);
        pattern = patterns.get(0);
        tab = TAB_MODELS;
        number = 1;
        randomTraits = false;
        sortByRarity(models);
        sortByRarity(backdrops);
        sortByRarity(patterns);
        final Random random = new Random();
        model = models.get(random.nextInt(models.size()));
        backdrop = backdrops.get(random.nextInt(backdrops.size()));
        pattern = patterns.get(random.nextInt(patterns.size()));
        randomTraits = true;
        actionBar.setVisibility(View.GONE);
        tabBar.setVisibility(View.VISIBLE);
        multiplierButton.setVisibility(View.GONE);
        if (balanceView != null) {
            balanceView.setVisibility(View.GONE);
        }
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(96));
        updateTabBar();
        adapter.notifyDataSetChanged();
        listView.scrollToPosition(0);
    }

    private void randomize() {
        final Random r = new Random();
        model = models.get(r.nextInt(models.size()));
        backdrop = backdrops.get(r.nextInt(backdrops.size()));
        pattern = patterns.get(r.nextInt(patterns.size()));
        randomTraits = true;
        adapter.notifyDataSetChanged();
    }

    /** Rarest first, as in Telegram's list of traits. */
    private static <T extends TL_stars.StarGiftAttribute> void sortByRarity(ArrayList<T> list) {
        java.util.Collections.sort(list, (a, b) -> Integer.compare(rank(a), rank(b)));
    }

    private static int rank(TL_stars.StarGiftAttribute a) {
        if (a.rarity instanceof TL_stars.TL_starGiftAttributeRarityLegendary) return -4;
        if (a.rarity instanceof TL_stars.TL_starGiftAttributeRarityEpic) return -3;
        if (a.rarity instanceof TL_stars.TL_starGiftAttributeRarityRare) return -2;
        if (a.rarity instanceof TL_stars.TL_starGiftAttributeRarityUncommon) return -1;
        final int p = a.getRarityPermille();
        return p > 0 ? p : 100000;
    }

    private TL_stars.TL_starGiftUnique buildGift() {
        final TL_stars.TL_starGiftUnique u = new TL_stars.TL_starGiftUnique();
        u.id = collection.id;
        u.gift_id = collection.id;
        u.title = collection.title != null ? collection.title : "NFT";
        u.slug = "";
        u.num = Math.max(1, number);
        u.attributes.add(model);
        u.attributes.add(pattern);
        u.attributes.add(backdrop);
        return u;
    }

    /** Saves the chosen NFT(s) to your visual gifts, closes the constructor and says «NFT saved ✓», like Chimera. */
    private void addToVisual(int count) {
        if (!MeridaVisual.on()) {
            Toast.makeText(getContext(), LocaleController.getString(R.string.MeridaNftConstructorTurnOn), Toast.LENGTH_LONG).show();
            return;
        }
        final TL_stars.TL_starGiftUnique gift = buildGift();
        final int stored = MeridaVisualGifts.storeMany("c" + collection.id, gift, count);
        if (stored <= 0) {
            Toast.makeText(getContext(), LocaleController.getString(R.string.MeridaVisualBuyFailed), Toast.LENGTH_LONG).show();
            return;
        }
        final String text = stored == 1 ? LocaleController.getString(R.string.MeridaVNftSaved) : LocaleController.formatString(R.string.MeridaVNftSavedN, stored);
        final BaseFragment previous = getParentLayout() != null ? getParentLayout().getBackgroundFragment() : null;
        finishFragment();
        if (previous != null) {
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    org.telegram.ui.Components.BulletinFactory.of(previous).createSimpleBulletin(R.raw.contact_check, text).show();
                } catch (Throwable ignore) {
                }
            }, 350);
        }
    }

    private void updateMultiplier() {
        if (multiplierButton != null) {
            multiplierButton.setText(multiplier + "×");
        }
    }

    /** «1 / 5 / 10 / 25 / 50 / 100 / 150 / 200 gifts / Own number…». */
    private void askCount(String title, java.util.function.IntConsumer onChosen) {
        if (getParentActivity() == null) {
            return;
        }
        final int[] counts = {1, 5, 10, 25, 50, 100, 150, 200};
        final CharSequence[] items = new CharSequence[counts.length + 1];
        for (int i = 0; i < counts.length; i++) {
            items[i] = LocaleController.formatString(R.string.MeridaVNGifts, counts[i]);
        }
        items[counts.length] = LocaleController.getString(R.string.MeridaVOwnCount);
        final org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        b.setTitle(title);
        b.setItems(items, (d, which) -> {
            if (which < counts.length) {
                onChosen.accept(counts[which]);
            } else {
                MeridaVisualSheets.askNumber(this, LocaleController.getString(R.string.MeridaVOwnCount), multiplier, n -> onChosen.accept(Math.max(1, Math.min(200, n))));
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        b.show();
    }

    // ---- ordinary gifts: visual purchase and visual auction ------------------------------------------

    private void buyOrdinary(TL_stars.StarGift g) {
        if (!MeridaVisual.on()) {
            Toast.makeText(getContext(), LocaleController.getString(R.string.MeridaNftConstructorTurnOn), Toast.LENGTH_LONG).show();
            return;
        }
        if (g.auction) {
            startAuction(g);
            return;
        }
        final int count = multiplier;
        final boolean spend = MeridaSettings.visualSpendStars.get();
        final long total = spend ? g.stars * count : 0;
        final String message = LocaleController.formatString(R.string.MeridaVBuyConfirm, count, g.title != null ? g.title : LocaleController.getString(R.string.MeridaVisualGiftTitle),
                MeridaVisual.formatNumber(total)) + (spend ? "" : "\n" + LocaleController.getString(R.string.MeridaVFreeNote));
        MeridaVisualActivity.confirm(this, LocaleController.getString(R.string.MeridaVBuyTitle), message, LocaleController.getString(R.string.MeridaVBuy), () -> {
            if (total > MeridaSettings.visualStars.get()) {
                Toast.makeText(getContext(), LocaleController.formatString(R.string.MeridaVisualBuyNoStars, MeridaVisual.formatNumber(total), MeridaVisual.formatNumber(MeridaSettings.visualStars.get())), Toast.LENGTH_LONG).show();
                return;
            }
            final int stored = MeridaVisualGifts.storeMany("g" + g.id, g, count);
            MeridaSettings.visualStars.set((int) Math.max(0, MeridaSettings.visualStars.get() - (spend ? g.stars * stored : 0)));
            addBalanceView();
            Toast.makeText(getContext(), LocaleController.formatString(R.string.MeridaVBoughtN, stored), Toast.LENGTH_LONG).show();
        });
    }

    /** A visual auction: one bid, a countdown of a few seconds, then the gifts are yours (only here). */
    private void startAuction(TL_stars.StarGift g) {
        final int seconds = Math.max(1, MeridaSettings.visualAuctionSeconds.get());
        final int gifts = Math.max(1, MeridaSettings.visualAuctionGifts.get());
        final boolean spend = MeridaSettings.visualSpendStars.get();
        final long bid = spend ? Math.max(g.stars, 1) : 0;
        MeridaVisualActivity.confirm(this, LocaleController.getString(R.string.MeridaVAuctionTitle),
                LocaleController.formatString(R.string.MeridaVAuctionConfirm, g.title != null ? g.title : "", MeridaVisual.formatNumber(bid), gifts, seconds),
                LocaleController.getString(R.string.MeridaVBid), () -> {
                    if (bid > MeridaSettings.visualStars.get()) {
                        Toast.makeText(getContext(), LocaleController.formatString(R.string.MeridaVisualBuyNoStars, MeridaVisual.formatNumber(bid), MeridaVisual.formatNumber(MeridaSettings.visualStars.get())), Toast.LENGTH_LONG).show();
                        return;
                    }
                    MeridaSettings.visualStars.set((int) (MeridaSettings.visualStars.get() - bid));
                    addBalanceView();
                    final org.telegram.ui.ActionBar.AlertDialog progress = new org.telegram.ui.ActionBar.AlertDialog(getParentActivity(), org.telegram.ui.ActionBar.AlertDialog.ALERT_TYPE_MESSAGE);
                    progress.setTitle(LocaleController.getString(R.string.MeridaVAuctionTitle));
                    progress.setCanceledOnTouchOutside(false);
                    progress.setCancelable(false);
                    final int[] left = {seconds};
                    progress.setMessage(LocaleController.formatString(R.string.MeridaVAuctionLeft, left[0]));
                    progress.show();
                    final Runnable[] tick = new Runnable[1];
                    tick[0] = () -> {
                        left[0]--;
                        if (left[0] > 0) {
                            progress.setMessage(LocaleController.formatString(R.string.MeridaVAuctionLeft, left[0]));
                            AndroidUtilities.runOnUIThread(tick[0], 1000);
                        } else {
                            progress.dismiss();
                            final int stored = MeridaVisualGifts.storeMany("a" + g.id, g, gifts);
                            Toast.makeText(getContext(), LocaleController.formatString(R.string.MeridaVAuctionWon, stored), Toast.LENGTH_LONG).show();
                        }
                    };
                    AndroidUtilities.runOnUIThread(tick[0], 1000);
                });
    }

    private int tileCount() {
        if (collection == null) {
            return collections.size();
        }
        return tab == TAB_MODELS ? models.size() : tab == TAB_BACKDROPS ? backdrops.size() : patterns.size();
    }

    /** In the constructor the list starts with the header and the «N unique …» line. */
    private int tileOffset() {
        return collection == null ? 0 : 2;
    }

    private void onTileClick(int position) {
        final int i = position - tileOffset();
        if (i < 0 || i >= tileCount()) {
            return;
        }
        if (collection == null) {
            if (mode == MODE_GIFTS) {
                buyOrdinary(collections.get(i));
            } else {
                openCollection(collections.get(i));
            }
            return;
        }
        randomTraits = false;
        if (tab == TAB_MODELS) {
            model = models.get(i);
        } else if (tab == TAB_BACKDROPS) {
            backdrop = backdrops.get(i);
        } else {
            pattern = patterns.get(i);
        }
        adapter.notifyDataSetChanged();
    }

    // ---- floating tab bar ----------------------------------------------------------------------------

    private void buildTabBar(Context context) {
        tabBar = new LinearLayout(context);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(32));
        bg.setColor(0xF01C1C1E);
        tabBar.setBackground(bg);
        final int[] icons = {R.drawable.msg_emoji_gem, R.drawable.msg_palette, R.drawable.msg_fave};
        final int[] names = {R.string.MeridaNftModels, R.string.MeridaNftBackdrops, R.string.MeridaNftPatterns};
        for (int i = 0; i < 3; i++) {
            final int t = i;
            final LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            final ImageView icon = new ImageView(context);
            icon.setImageResource(icons[i]);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            item.addView(icon, LayoutHelper.createLinear(24, 24));
            final TextView label = new TextView(context);
            label.setText(LocaleController.getString(names[i]));
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            label.setTypeface(AndroidUtilities.bold());
            label.setGravity(Gravity.CENTER);
            item.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            item.setOnClickListener(v -> {
                tab = t;
                updateTabBar();
                adapter.notifyDataSetChanged();
                listView.scrollToPosition(0);
            });
            tabItems[i] = item;
            tabIcons[i] = icon;
            tabLabels[i] = label;
            tabBar.addView(item, LayoutHelper.createLinear(84, 54));
        }
        root.addView(tabBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 22));
    }

    private void updateTabBar() {
        for (int i = 0; i < 3; i++) {
            final boolean selected = i == tab;
            final int color = selected ? BLUE : 0xFFFFFFFF;
            tabLabels[i].setTextColor(color);
            tabIcons[i].setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            final GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(AndroidUtilities.dp(28));
            bg.setColor(selected ? 0x333E9BFF : 0);
            tabItems[i].setBackground(bg);
        }
    }

    // ---- views ---------------------------------------------------------------------------------------

    private static final int SOLD = 0xFF7A4636;
    private static final int RARE = 0xFF34507A;

    private static String rarityText(TL_stars.StarGiftAttribute a) {
        final int permille = a == null ? 0 : a.getRarityPermille();
        if (permille <= 0) {
            return null;
        }
        final float percent = permille / 10f;
        return (percent == (int) percent ? String.valueOf((int) percent) : String.valueOf(percent)) + "%";
    }

    /** «legendary / epic / rare / uncommon» like Telegram's trait list, with its colour; null for a percent rarity. */
    private static String rarityName(TL_stars.StarGiftAttribute a) {
        if (a == null) return null;
        if (a.rarity instanceof TL_stars.TL_starGiftAttributeRarityLegendary) return LocaleController.getString(R.string.MeridaVLegendary);
        if (a.rarity instanceof TL_stars.TL_starGiftAttributeRarityEpic) return LocaleController.getString(R.string.MeridaVEpic);
        if (a.rarity instanceof TL_stars.TL_starGiftAttributeRarityRare) return LocaleController.getString(R.string.MeridaVRareTrait);
        if (a.rarity instanceof TL_stars.TL_starGiftAttributeRarityUncommon) return LocaleController.getString(R.string.MeridaVUncommon);
        return null;
    }

    private static int rarityColor(TL_stars.StarGiftAttribute a) {
        if (a.rarity instanceof TL_stars.TL_starGiftAttributeRarityLegendary) return 0xFFE89A2C;
        if (a.rarity instanceof TL_stars.TL_starGiftAttributeRarityEpic) return 0xFFB57CF0;
        if (a.rarity instanceof TL_stars.TL_starGiftAttributeRarityRare) return 0xFF3FB6C8;
        if (a.rarity instanceof TL_stars.TL_starGiftAttributeRarityUncommon) return 0xFF5BBF5B;
        return 0xFFAAAAAA;
    }

    private static void setDocument(BackupImageView image, TLRPC.Document document, Object parent) {
        if (document == null) {
            image.clearImage();
            return;
        }
        final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, AndroidUtilities.dp(100));
        image.setImage(ImageLocation.getForDocument(document), "120_120", ImageLocation.getForDocument(thumb, document), "120_120", null, parent);
        image.getImageReceiver().setAutoRepeat(1);
    }

    private static TextView pill(Context context, int textColor, int bgColor, float textSizeDp) {
        final TextView tv = new TextView(context);
        tv.setTextColor(textColor);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSizeDp);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(1), AndroidUtilities.dp(8), AndroidUtilities.dp(1));
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(12));
        bg.setColor(bgColor);
        tv.setBackground(bg);
        return tv;
    }

    /** The golden price pill with little sparkles, like the gift shop's. */
    private static class PricePill extends TextView {
        private final Paint sparkle = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] dots = new float[36];

        PricePill(Context context) {
            super(context);
            setTextColor(0xFFF2B33D);
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            setGravity(Gravity.CENTER);
            setSingleLine(true);
            setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
            final GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(AndroidUtilities.dp(16));
            bg.setColor(0xFF3A2C1A);
            setBackground(bg);
            sparkle.setColor(0xCCF2B33D);
            final Random r = new Random(7);
            for (int i = 0; i < dots.length; i += 3) {
                dots[i] = r.nextFloat();
                dots[i + 1] = r.nextFloat();
                dots[i + 2] = 0.6f + r.nextFloat();
            }
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            for (int i = 0; i < dots.length; i += 3) {
                canvas.drawCircle(dots[i] * getWidth(), dots[i + 1] * getHeight(), AndroidUtilities.dp(dots[i + 2]), sparkle);
            }
            super.onDraw(canvas);
        }
    }

    /** A diagonal ribbon in the top right corner («rare», «premium», «sold»). */
    private static class CornerRibbon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.text.TextPaint text = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private String label = "";
        private int color;

        CornerRibbon(Context context) {
            super(context);
            text.setColor(0xFFFFFFFF);
            text.setTextSize(AndroidUtilities.dp(11));
            text.setTypeface(AndroidUtilities.bold());
        }

        void set(String label, int color) {
            this.label = label;
            this.color = color;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (TextUtils.isEmpty(label)) {
                return;
            }
            final float w = getWidth();
            canvas.save();
            canvas.clipRect(0, 0, w, w);
            canvas.rotate(45, w / 2f, w / 2f);
            paint.setColor(color);
            final float bandH = AndroidUtilities.dp(20);
            final float cy = w / 2f - AndroidUtilities.dp(10);
            canvas.drawRect(-w, cy - bandH / 2f, 2 * w, cy + bandH / 2f, paint);
            final float tw = text.measureText(label);
            final Paint.FontMetrics fm = text.getFontMetrics();
            canvas.drawText(label, w / 2f - tw / 2f, cy - (fm.ascent + fm.descent) / 2f, text);
            canvas.restore();
        }
    }

    /** One grid tile: picture, name, price or rarity, ribbon, selection border. */
    private class TileView extends FrameLayout {
        final BackupImageView image;
        final BackupImageView bigPattern;
        final TextView name;
        final PricePill price;
        final TextView rarity;
        final TextView rarityTop;
        final CornerRibbon ribbon;
        final GiftSheet.CardBackground card;
        final GradientDrawable plain = new GradientDrawable();
        final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF rect = new RectF();
        boolean selected;
        int borderColor;

        TileView(Context context) {
            super(context);
            setWillNotDraw(false);
            card = new GiftSheet.CardBackground(this, getResourceProvider(), false);
            card.setPadding(false);
            card.setRoundRadius(AndroidUtilities.dp(16));
            plain.setCornerRadius(AndroidUtilities.dp(16));
            plain.setColor(TILE);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(AndroidUtilities.dp(2.5f));

            bigPattern = new BackupImageView(context);
            addView(bigPattern, LayoutHelper.createFrame(58, 58, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 26, 0, 0));
            image = new BackupImageView(context);
            addView(image, LayoutHelper.createFrame(84, 84, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 14, 0, 0));
            name = new TextView(context);
            name.setTextColor(0xFFFFFFFF);
            name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            name.setTypeface(AndroidUtilities.bold());
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            name.setShadowLayer(AndroidUtilities.dp(2), 0, AndroidUtilities.dp(1), 0x55000000);
            addView(name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 6, 0, 6, 12));
            price = new PricePill(context);
            addView(price, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));
            rarity = pill(context, 0xFFFFFFFF, 0x33000000, 12);
            addView(rarity, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 22, Gravity.TOP | Gravity.RIGHT, 0, 8, 8, 0));
            rarityTop = pill(context, 0xFFE89A2C, 0x33E89A2C, 12);
            addView(rarityTop, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 22, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
            ribbon = new CornerRibbon(context);
            addView(ribbon, LayoutHelper.createFrame(64, 64, Gravity.TOP | Gravity.RIGHT));
        }

        boolean modelTile;

        /** Sizes follow the tile width, like Chimera: the gift on top, the price pill below it, never overlapping. */
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int w = MeasureSpec.getSize(widthMeasureSpec);
            final FrameLayout.LayoutParams ip = (FrameLayout.LayoutParams) image.getLayoutParams();
            ip.width = ip.height = (int) (w * (modelTile ? 0.56f : 0.6f));
            ip.topMargin = (int) (w * (modelTile ? 0.2f : 0.08f));
            final FrameLayout.LayoutParams pp = (FrameLayout.LayoutParams) bigPattern.getLayoutParams();
            pp.width = pp.height = (int) (w * 0.42f);
            pp.topMargin = (int) (w * 0.2f);
            final FrameLayout.LayoutParams rp = (FrameLayout.LayoutParams) ribbon.getLayoutParams();
            rp.width = rp.height = (int) (w * 0.46f);
            final FrameLayout.LayoutParams pr = (FrameLayout.LayoutParams) price.getLayoutParams();
            pr.bottomMargin = (int) (w * 0.08f);
            final FrameLayout.LayoutParams nm = (FrameLayout.LayoutParams) name.getLayoutParams();
            nm.bottomMargin = (int) (w * 0.08f);
            super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (w * 1.1f), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (selected) {
                final float inset = AndroidUtilities.dp(1.25f);
                rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
                border.setColor(borderColor);
                canvas.drawRoundRect(rect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), border);
            }
        }

        private void reset() {
            modelTile = false;
            bigPattern.setVisibility(GONE);
            bigPattern.setColorFilter(null);
            image.setVisibility(VISIBLE);
            name.setVisibility(GONE);
            price.setVisibility(GONE);
            rarity.setVisibility(GONE);
            rarityTop.setVisibility(GONE);
            ribbon.set("", 0);
            selected = false;
        }

        void bindCatalog(TL_stars.StarGift g) {
            reset();
            setBackground(plain);
            setDocument(image, g.sticker, g);
            price.setVisibility(VISIBLE);
            if (g.stars > 0 && !g.auction) {
                price.setText(org.telegram.ui.Stars.StarsIntroActivity.replaceStarsWithPlain("⭐️ " + LocaleController.formatNumber(g.stars, ','), .8f));
            } else {
                price.setText(LocaleController.getString(R.string.MeridaVView));
            }
            selected = g.require_premium;
            borderColor = GOLD;
            if (mode == MODE_GIFTS) {
                if (g.sold_out) {
                    ribbon.set(LocaleController.getString(R.string.MeridaVSold), SOLD);
                } else if (g.limited) {
                    ribbon.set(LocaleController.getString(R.string.MeridaNftRare), RARE);
                }
            } else if (g.require_premium) {
                ribbon.set("premium", 0xFFD98F2B);
            } else {
                ribbon.set(LocaleController.getString(R.string.MeridaNftRare), RARE);
            }
            invalidate();
        }

        void bindModel(TL_stars.starGiftAttributeModel m) {
            reset();
            setBackground(plain);
            setDocument(image, m.document, collection);
            modelTile = true;
            requestLayout();
            name.setText(m.name);
            name.setVisibility(VISIBLE);
            selected = m == model;
            borderColor = BLUE;
            final String rn = rarityName(m);
            if (rn != null) {
                rarityTop.setText(rn);
                rarityTop.setTextColor(rarityColor(m));
                ((GradientDrawable) rarityTop.getBackground()).setColor((rarityColor(m) & 0x00FFFFFF) | 0x33000000);
                rarityTop.setVisibility(VISIBLE);
            } else {
                setRarity(rarityText(m), 0x33FFFFFF, 0xFFAAAAAA);
            }
            invalidate();
        }

        void bindBackdrop(TL_stars.starGiftAttributeBackdrop b, int index) {
            reset();
            card.setBackdrop(b);
            card.setPattern(patterns.isEmpty() ? pattern : patterns.get(index % patterns.size()));
            setBackground(card);
            image.setVisibility(GONE);
            bigPattern.setVisibility(VISIBLE);
            final TL_stars.starGiftAttributePattern p = patterns.isEmpty() ? pattern : patterns.get(index % patterns.size());
            setDocument(bigPattern, p.document, collection);
            bigPattern.setColorFilter(new PorterDuffColorFilter((b.pattern_color & 0x00FFFFFF) | 0x99000000, PorterDuff.Mode.SRC_IN));
            name.setText(b.name);
            name.setVisibility(VISIBLE);
            selected = b == backdrop;
            borderColor = 0xFFFFFFFF;
            setRarity(rarityText(b), 0x33000000, 0xFFFFFFFF);
            invalidate();
        }

        void bindPattern(TL_stars.starGiftAttributePattern p, int index) {
            reset();
            final TL_stars.starGiftAttributeBackdrop b = backdrops.isEmpty() ? backdrop : backdrops.get(index % backdrops.size());
            card.setBackdrop(b);
            card.setPattern(p);
            setBackground(card);
            image.setVisibility(GONE);
            bigPattern.setVisibility(VISIBLE);
            setDocument(bigPattern, p.document, collection);
            bigPattern.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
            name.setText(p.name);
            name.setVisibility(VISIBLE);
            selected = p == pattern;
            borderColor = 0xFFFFFFFF;
            setRarity(rarityText(p), 0x33000000, 0xFFFFFFFF);
            invalidate();
        }

        private void setRarity(String text, int bg, int color) {
            if (text == null) {
                rarity.setVisibility(GONE);
                return;
            }
            rarity.setText(text);
            rarity.setTextColor(color);
            ((GradientDrawable) rarity.getBackground()).setColor(bg);
            rarity.setVisibility(VISIBLE);
        }
    }

    /** The collection page header: backdrop with pattern, the model, title and the three chips. */
    private class HeaderView extends FrameLayout {
        final GiftSheet.CardBackground card;
        final BackupImageView modelImage;
        final TextView title;
        final TextView subtitle;
        final TextView num;
        final TextView priceView;
        final TextView[] chipName = new TextView[3];
        final TextView[] chipRarity = new TextView[3];

        HeaderView(Context context) {
            super(context);
            card = new GiftSheet.CardBackground(this, getResourceProvider(), false);
            card.setPadding(false);
            card.setRoundRadius(AndroidUtilities.dp(26));
            setBackground(card);
            final float top = AndroidUtilities.statusBarHeight / AndroidUtilities.density;

            final ImageView back = roundButton(context, R.drawable.ic_ab_back);
            back.setOnClickListener(v -> showCatalog());
            addView(back, LayoutHelper.createFrame(46, 46, Gravity.TOP | Gravity.LEFT, 14, top + 14, 0, 0));
            num = pill(context, 0xFFFFFFFF, 0x33000000, 16);
            num.setOnClickListener(v -> MeridaVisualSheets.askNumber(MeridaNftConstructorActivity.this, LocaleController.getString(R.string.MeridaVNftNumber), number, n -> {
                number = Math.max(1, n);
                adapter.notifyItemChanged(0);
            }));
            addView(num, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.TOP | Gravity.LEFT, 70, top + 17, 0, 0));
            final ImageView send = roundButton(context, R.drawable.msg_send);
            // the arrow puts this NFT into your profile at once (like Chimera)
            send.setOnClickListener(v -> addToVisual(1));
            addView(send, LayoutHelper.createFrame(46, 46, Gravity.TOP | Gravity.RIGHT, 0, top + 14, 14, 0));
            priceView = pill(context, 0xFFFFFFFF, 0x33000000, 16);
            priceView.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
            addView(priceView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.TOP | Gravity.RIGHT, 0, top + 17, 68, 0));
            // the round button under the arrow: «create several NFTs»
            final ImageView dice = roundButton(context, R.drawable.outline_craft);
            dice.setOnClickListener(v -> askCount(LocaleController.getString(R.string.MeridaVCreateSeveral), MeridaNftConstructorActivity.this::addToVisual));
            addView(dice, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT, 0, top + 76, 14, 0));

            modelImage = new BackupImageView(context);
            addView(modelImage, LayoutHelper.createFrame(170, 170, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, top + 40, 0, 0));
            title = new TextView(context);
            title.setTextColor(0xFFFFFFFF);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 27);
            title.setTypeface(AndroidUtilities.bold());
            title.setGravity(Gravity.CENTER);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 16, top + 214, 16, 0));
            subtitle = new TextView(context);
            subtitle.setTextColor(0xB3FFFFFF);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            subtitle.setGravity(Gravity.CENTER);
            addView(subtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 16, top + 252, 16, 0));

            final LinearLayout chips = new LinearLayout(context);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            final int[] labels = {R.string.MeridaNftChipModel, R.string.MeridaNftChipBackdrop, R.string.MeridaNftChipPattern};
            for (int i = 0; i < 3; i++) {
                final FrameLayout chip = new FrameLayout(context);
                final GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(AndroidUtilities.dp(14));
                bg.setColor(0x1F000000);
                chip.setBackground(bg);
                final LinearLayout texts = new LinearLayout(context);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setGravity(Gravity.CENTER);
                chipName[i] = new TextView(context);
                chipName[i].setTextColor(0xFFFFFFFF);
                chipName[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                chipName[i].setTypeface(AndroidUtilities.bold());
                chipName[i].setSingleLine(true);
                chipName[i].setEllipsize(TextUtils.TruncateAt.END);
                chipName[i].setGravity(Gravity.CENTER);
                texts.addView(chipName[i], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                final TextView label = new TextView(context);
                label.setText(LocaleController.getString(labels[i]));
                label.setTextColor(0x99FFFFFF);
                label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                label.setGravity(Gravity.CENTER);
                texts.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                chip.addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 6, 8, 6, 6));
                chipRarity[i] = pill(context, 0xFF0F3F44, 0x66FFFFFF, 13);
                chip.addView(chipRarity[i], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 22, Gravity.TOP | Gravity.RIGHT, 0, -11, 6, 0));
                chip.setClipChildren(false);
                final int t = i;
                chip.setOnClickListener(v -> {
                    tab = t;
                    updateTabBar();
                    adapter.notifyDataSetChanged();
                });
                chips.addView(chip, LayoutHelper.createLinear(0, 60, 1f, 5, 0, 5, 0));
            }
            chips.setClipChildren(false);
            setClipChildren(false);
            addView(chips, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 72, Gravity.TOP, 9, top + 292, 9, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.statusBarHeight + AndroidUtilities.dp(380), MeasureSpec.EXACTLY));
        }

        void bind() {
            card.setBackdrop(backdrop);
            card.setPattern(pattern);
            setDocument(modelImage, model.document, collection);
            title.setText(collection.title != null ? collection.title : "NFT");
            subtitle.setText(LocaleController.getString(randomTraits ? R.string.MeridaVRandomTraits : R.string.MeridaNftChosen));
            num.setText("#" + number);
            final String p = MeridaVisualSheets.localPrice(MeridaNftConstructorActivity.this, collectionPrice);
            priceView.setText(p == null ? "" : p);
            priceView.setVisibility(p == null ? GONE : VISIBLE);
            final TL_stars.StarGiftAttribute[] parts = {model, backdrop, pattern};
            for (int i = 0; i < 3; i++) {
                chipName[i].setText(parts[i].name);
                final String r = rarityText(parts[i]);
                final String rn = r != null ? r : rarityName(parts[i]);
                chipRarity[i].setText(rn == null ? "" : rn);
                chipRarity[i].setVisibility(rn == null ? GONE : VISIBLE);
            }
            invalidate();
        }
    }

    private static ImageView roundButton(Context context, int icon) {
        final ImageView b = new ImageView(context);
        b.setImageResource(icon);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x33000000);
        b.setBackground(bg);
        return b;
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_TILE;
        }

        @Override
        public int getItemCount() {
            return tileOffset() + tileCount();
        }

        @Override
        public int getItemViewType(int position) {
            if (collection != null) {
                if (position == 0) return TYPE_HEADER;
                if (position == 1) return TYPE_COUNT;
            }
            return TYPE_TILE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View view;
            if (viewType == TYPE_HEADER) {
                view = new HeaderView(parent.getContext());
                view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            } else if (viewType == TYPE_COUNT) {
                final TextView tv = new TextView(parent.getContext());
                tv.setTextColor(0xFF8E8E93);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(12));
                view = tv;
                view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            } else {
                view = new TileView(parent.getContext());
                final RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
                lp.setMargins(AndroidUtilities.dp(5), AndroidUtilities.dp(5), AndroidUtilities.dp(5), AndroidUtilities.dp(5));
                view.setLayoutParams(lp);
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final int type = holder.getItemViewType();
            if (type == TYPE_HEADER) {
                ((HeaderView) holder.itemView).bind();
            } else if (type == TYPE_COUNT) {
                final int res = tab == TAB_MODELS ? R.string.MeridaNftCountModels : tab == TAB_BACKDROPS ? R.string.MeridaNftCountBackdrops : R.string.MeridaNftCountPatterns;
                ((TextView) holder.itemView).setText(LocaleController.formatString(res, tileCount()));
            } else {
                final TileView tile = (TileView) holder.itemView;
                final int i = position - tileOffset();
                if (collection == null) {
                    tile.bindCatalog(collections.get(i));
                } else if (tab == TAB_MODELS) {
                    tile.bindModel(models.get(i));
                } else if (tab == TAB_BACKDROPS) {
                    tile.bindBackdrop(backdrops.get(i), i);
                } else {
                    tile.bindPattern(patterns.get(i), i);
                }
            }
        }
    }
}
