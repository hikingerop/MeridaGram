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

    private static final int TAB_MODELS = 0;
    private static final int TAB_BACKDROPS = 1;
    private static final int TAB_PATTERNS = 2;

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_COUNT = 1;
    private static final int TYPE_TILE = 2;

    private static final int GOLD = 0xFFF1A73A;
    private static final int BLUE = 0xFF3E9BFF;
    private static final int TILE = 0xFF1C1C1E;

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
        actionBar.setBackgroundColor(0xFF000000);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        root = new FrameLayout(context);
        root.setBackgroundColor(0xFF000000);
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

        buildTabBar(context);
        showCatalog();
        return fragmentView;
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
        actionBar.setTitle(LocaleController.getString(R.string.MeridaNftCatalog));
        listView.setPadding(AndroidUtilities.dp(3), ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight + AndroidUtilities.dp(6), AndroidUtilities.dp(3), AndroidUtilities.dp(16));
        addBalanceView();

        final StarsController c = StarsController.getInstance(currentAccount);
        c.loadStarGifts();
        collections.clear();
        for (TL_stars.StarGift g : c.gifts) {
            if (g != null && !(g instanceof TL_stars.TL_starGiftUnique) && (g.can_upgrade || g.upgrade_stars > 0 || g.availability_resale > 0)) {
                collections.add(g);
            }
        }
        actionBar.setSubtitle(collections.isEmpty() ? LocaleController.getString(R.string.MeridaNftConstructorLoading) : collections.size() + " NFT");
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
        if (!MeridaVisual.on() || stars <= 0 && grams <= 0) {
            balanceView.setVisibility(View.GONE);
            return;
        }
        final android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(LocaleController.getString(R.string.MeridaNftBalance));
        sb.append(' ').append(MeridaVisual.mark()).append('\n');
        if (grams > 0) {
            sb.append("💎 ").append(MeridaVisual.formatNumber(grams)).append("  ");
        }
        sb.append("⭐ ").append(MeridaVisual.formatNumber(stars));
        balanceView.setText(sb);
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
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (collection != g || fragmentView == null) {
                return;
            }
            if (res instanceof TL_stars.resaleStarGifts) {
                takeAttributes(((TL_stars.resaleStarGifts) res).attributes);
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
        actionBar.setVisibility(View.GONE);
        tabBar.setVisibility(View.VISIBLE);
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
        adapter.notifyDataSetChanged();
    }

    private TL_stars.TL_starGiftUnique buildGift() {
        final TL_stars.TL_starGiftUnique u = new TL_stars.TL_starGiftUnique();
        u.id = collection.id;
        u.gift_id = collection.id;
        u.title = collection.title != null ? collection.title : "NFT";
        u.slug = "";
        u.num = 1;
        u.attributes.add(model);
        u.attributes.add(pattern);
        u.attributes.add(backdrop);
        return u;
    }

    private void addToVisual() {
        if (!MeridaVisual.on()) {
            Toast.makeText(getContext(), LocaleController.getString(R.string.MeridaNftConstructorTurnOn), Toast.LENGTH_LONG).show();
            return;
        }
        final TL_stars.TL_starGiftUnique gift = buildGift();
        final boolean ok = MeridaVisualGifts.store("c" + collection.id + "-" + System.currentTimeMillis(), gift);
        Toast.makeText(getContext(), ok
                ? LocaleController.formatString(R.string.MeridaVisualGiftAdded, gift.title + " · " + model.name)
                : LocaleController.getString(R.string.MeridaVisualBuyFailed), Toast.LENGTH_LONG).show();
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
            openCollection(collections.get(i));
            return;
        }
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

    private static String rarityText(TL_stars.StarGiftAttribute a) {
        final int permille = a == null ? 0 : a.getRarityPermille();
        if (permille <= 0) {
            return null;
        }
        final float percent = permille / 10f;
        return (percent == (int) percent ? String.valueOf((int) percent) : String.valueOf(percent)) + "%";
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
        tv.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(1), AndroidUtilities.dp(7), AndroidUtilities.dp(1));
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(10));
        bg.setColor(bgColor);
        tv.setBackground(bg);
        return tv;
    }

    /** A diagonal ribbon in the top right corner («rare», «premium»). */
    private static class CornerRibbon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.text.TextPaint text = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private String label = "";
        private int color;

        CornerRibbon(Context context) {
            super(context);
            text.setColor(0xFFFFFFFF);
            text.setTextSize(AndroidUtilities.dp(10));
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
            canvas.rotate(45, w / 2f, w / 2f);
            paint.setColor(color);
            final float bandH = AndroidUtilities.dp(18);
            canvas.drawRect(-w, w / 2f - bandH / 2f - AndroidUtilities.dp(8), 2 * w, w / 2f + bandH / 2f - AndroidUtilities.dp(8), paint);
            final float tw = text.measureText(label);
            final Paint.FontMetrics fm = text.getFontMetrics();
            canvas.drawText(label, w / 2f - tw / 2f, w / 2f - AndroidUtilities.dp(8) - (fm.ascent + fm.descent) / 2f, text);
            canvas.restore();
        }
    }

    /** One grid tile: picture, name, price or rarity, ribbon, selection border. */
    private class TileView extends FrameLayout {
        final BackupImageView image;
        final BackupImageView bigPattern;
        final TextView name;
        final TextView price;
        final TextView rarity;
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
            card.setRoundRadius(AndroidUtilities.dp(14));
            plain.setCornerRadius(AndroidUtilities.dp(14));
            plain.setColor(TILE);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(AndroidUtilities.dp(2));

            bigPattern = new BackupImageView(context);
            addView(bigPattern, LayoutHelper.createFrame(54, 54, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 22, 0, 0));
            image = new BackupImageView(context);
            addView(image, LayoutHelper.createFrame(76, 76, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 12, 0, 0));
            name = new TextView(context);
            name.setTextColor(0xFFFFFFFF);
            name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            name.setTypeface(AndroidUtilities.bold());
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            addView(name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 6, 0, 6, 10));
            price = pill(context, 0xFFFFFFFF, 0x55000000, 14);
            addView(price, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 26, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));
            rarity = pill(context, 0xFFFFFFFF, 0x33FFFFFF, 11);
            addView(rarity, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 18, Gravity.TOP | Gravity.RIGHT, 0, 8, 8, 0));
            ribbon = new CornerRibbon(context);
            addView(ribbon, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int w = MeasureSpec.getSize(widthMeasureSpec);
            super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (w * 1.12f), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final int inset = AndroidUtilities.dp(3);
            if (getBackground() == null) {
                // nothing: the background drawable draws itself
            }
            super.onDraw(canvas);
            if (selected) {
                rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
                border.setColor(borderColor);
                canvas.drawRoundRect(rect, AndroidUtilities.dp(14), AndroidUtilities.dp(14), border);
            }
        }

        void bindCatalog(TL_stars.StarGift g) {
            setBackground(plain);
            plain.setStroke(g.require_premium ? AndroidUtilities.dp(2) : 0, GOLD);
            bigPattern.setVisibility(GONE);
            image.setVisibility(VISIBLE);
            setDocument(image, g.sticker, g);
            name.setVisibility(GONE);
            rarity.setVisibility(GONE);
            if (g.stars > 0) {
                price.setText("⭐ " + MeridaVisual.formatNumber(g.stars));
                price.setVisibility(VISIBLE);
            } else {
                price.setVisibility(GONE);
            }
            if (g.require_premium) {
                ribbon.set("premium", GOLD);
            } else if (g.limited || g.availability_total > 0) {
                ribbon.set(LocaleController.getString(R.string.MeridaNftRare), 0xFF2E4A78);
            } else {
                ribbon.set("", 0);
            }
            selected = false;
        }

        void bindModel(TL_stars.starGiftAttributeModel m) {
            setBackground(plain);
            plain.setStroke(0, 0);
            bigPattern.setVisibility(GONE);
            image.setVisibility(VISIBLE);
            setDocument(image, m.document, collection);
            name.setText(m.name);
            name.setVisibility(VISIBLE);
            price.setVisibility(GONE);
            ribbon.set("", 0);
            selected = m == model;
            borderColor = BLUE;
            setRarity(rarityText(m), selected);
        }

        void bindBackdrop(TL_stars.starGiftAttributeBackdrop b) {
            card.setBackdrop(b);
            card.setPattern(pattern);
            setBackground(card);
            bigPattern.setVisibility(GONE);
            image.setVisibility(GONE);
            name.setText(b.name);
            name.setVisibility(VISIBLE);
            price.setVisibility(GONE);
            ribbon.set("", 0);
            selected = b == backdrop;
            borderColor = 0xFFFFFFFF;
            setRarity(rarityText(b), false);
        }

        void bindPattern(TL_stars.starGiftAttributePattern p) {
            card.setBackdrop(backdrop);
            card.setPattern(p);
            setBackground(card);
            image.setVisibility(GONE);
            bigPattern.setVisibility(VISIBLE);
            setDocument(bigPattern, p.document, collection);
            bigPattern.setColorFilter(new PorterDuffColorFilter(0xB3FFFFFF, PorterDuff.Mode.SRC_IN));
            name.setText(p.name);
            name.setVisibility(VISIBLE);
            price.setVisibility(GONE);
            ribbon.set("", 0);
            selected = p == pattern;
            borderColor = 0xFFFFFFFF;
            setRarity(rarityText(p), false);
        }

        private void setRarity(String text, boolean blue) {
            if (text == null) {
                rarity.setVisibility(GONE);
                return;
            }
            rarity.setText(text);
            ((GradientDrawable) rarity.getBackground()).setColor(blue ? BLUE : 0x33FFFFFF);
            rarity.setVisibility(VISIBLE);
            invalidate();
        }
    }

    /** The collection page header: backdrop with pattern, the model, title and the three chips. */
    private class HeaderView extends FrameLayout {
        final GiftSheet.CardBackground card;
        final BackupImageView modelImage;
        final TextView title;
        final TextView[] chipName = new TextView[3];
        final TextView[] chipRarity = new TextView[3];

        HeaderView(Context context) {
            super(context);
            card = new GiftSheet.CardBackground(this, getResourceProvider(), false);
            card.setPadding(false);
            card.setRoundRadius(0);
            setBackground(card);
            final int top = AndroidUtilities.statusBarHeight;

            final ImageView back = roundButton(context, R.drawable.ic_ab_back);
            back.setOnClickListener(v -> showCatalog());
            addView(back, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.LEFT, 10, top / AndroidUtilities.density + 8, 0, 0));
            final TextView num = pill(context, 0xFFFFFFFF, 0x33000000, 14);
            num.setText("#1");
            addView(num, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.LEFT, 58, top / AndroidUtilities.density + 14, 0, 0));
            final TextView add = pill(context, 0xFFFFFFFF, 0x55000000, 14);
            add.setText(LocaleController.getString(R.string.MeridaNftToProfile));
            add.setOnClickListener(v -> addToVisual());
            addView(add, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, Gravity.TOP | Gravity.RIGHT, 0, top / AndroidUtilities.density + 12, 12, 0));
            final ImageView dice = roundButton(context, R.drawable.msg_media);
            dice.setOnClickListener(v -> randomize());
            addView(dice, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.RIGHT, 0, top / AndroidUtilities.density + 60, 14, 0));

            modelImage = new BackupImageView(context);
            addView(modelImage, LayoutHelper.createFrame(150, 150, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, top / AndroidUtilities.density + 30, 0, 0));
            title = new TextView(context);
            title.setTextColor(0xFFFFFFFF);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
            title.setTypeface(AndroidUtilities.bold());
            title.setGravity(Gravity.CENTER);
            addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 16, top / AndroidUtilities.density + 184, 16, 0));
            final TextView subtitle = new TextView(context);
            subtitle.setText(LocaleController.getString(R.string.MeridaNftChosen));
            subtitle.setTextColor(0xB3FFFFFF);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setGravity(Gravity.CENTER);
            addView(subtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 16, top / AndroidUtilities.density + 216, 16, 0));

            final LinearLayout chips = new LinearLayout(context);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            final int[] labels = {R.string.MeridaNftChipModel, R.string.MeridaNftChipBackdrop, R.string.MeridaNftChipPattern};
            for (int i = 0; i < 3; i++) {
                final FrameLayout chip = new FrameLayout(context);
                final GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(AndroidUtilities.dp(12));
                bg.setColor(0x26FFFFFF);
                chip.setBackground(bg);
                final LinearLayout texts = new LinearLayout(context);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setGravity(Gravity.CENTER);
                chipName[i] = new TextView(context);
                chipName[i].setTextColor(0xFFFFFFFF);
                chipName[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                chipName[i].setTypeface(AndroidUtilities.bold());
                chipName[i].setSingleLine(true);
                chipName[i].setEllipsize(TextUtils.TruncateAt.END);
                chipName[i].setGravity(Gravity.CENTER);
                texts.addView(chipName[i], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                final TextView label = new TextView(context);
                label.setText(LocaleController.getString(labels[i]));
                label.setTextColor(0xB3FFFFFF);
                label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                label.setGravity(Gravity.CENTER);
                texts.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                chip.addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 6, 6, 6, 6));
                chipRarity[i] = pill(context, 0xFFFFFFFF, 0x40FFFFFF, 11);
                chip.addView(chipRarity[i], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 18, Gravity.TOP | Gravity.RIGHT, 0, -6, 4, 0));
                chip.setClipChildren(false);
                final int t = i;
                chip.setOnClickListener(v -> {
                    tab = t;
                    updateTabBar();
                    adapter.notifyDataSetChanged();
                });
                chips.addView(chip, LayoutHelper.createLinear(0, 54, 1f, 4, 0, 4, 0));
            }
            chips.setClipChildren(false);
            setClipChildren(false);
            addView(chips, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 60, Gravity.TOP, 8, top / AndroidUtilities.density + 252, 8, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.statusBarHeight + AndroidUtilities.dp(326), MeasureSpec.EXACTLY));
        }

        void bind() {
            card.setBackdrop(backdrop);
            card.setPattern(pattern);
            setDocument(modelImage, model.document, collection);
            title.setText(collection.title != null ? collection.title : "NFT");
            final TL_stars.StarGiftAttribute[] parts = {model, backdrop, pattern};
            for (int i = 0; i < 3; i++) {
                chipName[i].setText(parts[i].name);
                final String r = rarityText(parts[i]);
                chipRarity[i].setText(r == null ? "" : r);
                chipRarity[i].setVisibility(r == null ? GONE : VISIBLE);
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
        bg.setColor(0x40000000);
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
                    tile.bindBackdrop(backdrops.get(i));
                } else {
                    tile.bindPattern(patterns.get(i));
                }
            }
        }
    }
}
