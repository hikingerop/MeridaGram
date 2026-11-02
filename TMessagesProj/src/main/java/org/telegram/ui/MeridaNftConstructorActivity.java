package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.Stars.StarsController;

import java.util.ArrayList;

/**
 * MeridaGram «Visual» NFT constructor: pick a collection, then any of its real models, backdrops and patterns
 * (loaded from Telegram) and add the result to your own profile as a visual gift with the «VISUAL» ribbon.
 * Nothing is bought or sent; the result exists only on this phone.
 */
public class MeridaNftConstructorActivity extends BaseFragment {

    private static final int TAB_MODELS = 0;
    private static final int TAB_BACKDROPS = 1;
    private static final int TAB_PATTERNS = 2;

    private FrameLayout root;
    private TL_stars.StarGift collection;
    private final ArrayList<TL_stars.starGiftAttributeModel> models = new ArrayList<>();
    private final ArrayList<TL_stars.starGiftAttributeBackdrop> backdrops = new ArrayList<>();
    private final ArrayList<TL_stars.starGiftAttributePattern> patterns = new ArrayList<>();
    private TL_stars.starGiftAttributeModel model;
    private TL_stars.starGiftAttributeBackdrop backdrop;
    private TL_stars.starGiftAttributePattern pattern;
    private int tab = TAB_MODELS;
    private GiftSheet.GiftCell previewCell;
    private LinearLayout optionsList;
    private final TextView[] tabViews = new TextView[3];

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.MeridaNftConstructor));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (collection != null) {
                        collection = null;
                        showCollections();
                    } else {
                        finishFragment();
                    }
                }
            }
        });
        root = new FrameLayout(context);
        root.setBackgroundColor(Theme.isCurrentThemeDark() ? 0xFF000000 : Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;
        showCollections();
        return fragmentView;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (collection != null) {
            if (invoked) {
                collection = null;
                showCollections();
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    // ---- page 1: collections ---------------------------------------------------------------------

    private void showCollections() {
        root.removeAllViews();
        actionBar.setTitle(LocaleController.getString(R.string.MeridaNftConstructor));
        final StarsController c = StarsController.getInstance(currentAccount);
        c.loadStarGifts();
        final ArrayList<TL_stars.StarGift> list = new ArrayList<>();
        for (TL_stars.StarGift g : c.gifts) {
            if (g != null && !(g instanceof TL_stars.TL_starGiftUnique) && (g.can_upgrade || g.upgrade_stars > 0 || g.availability_resale > 0)) {
                list.add(g);
            }
        }
        if (list.isEmpty()) {
            final TextView empty = new TextView(getContext());
            empty.setText(LocaleController.getString(R.string.MeridaNftConstructorLoading));
            empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            empty.setGravity(Gravity.CENTER);
            root.addView(empty, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 32, 0, 32, 0));
            AndroidUtilities.runOnUIThread(() -> {
                if (collection == null && fragmentView != null && !c.gifts.isEmpty()) {
                    showCollections();
                }
            }, 1500);
            return;
        }
        final ScrollView scroll = new ScrollView(getContext());
        final LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(16));
        final TextView hint = new TextView(getContext());
        hint.setText(LocaleController.getString(R.string.MeridaNftConstructorHint));
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        column.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 4, 8, 12));
        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                column.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            final TL_stars.StarGift g = list.get(i);
            final View tile = tile(g.sticker, g, null, g.title != null ? g.title : LocaleController.getString(R.string.MeridaNftConstructor), null);
            addPrice((LinearLayout) tile, g.stars);
            tile.setOnClickListener(v -> openCollection(g));
            row.addView(tile, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 4, 4, 4, 4));
        }
        if (row != null) {
            while (row.getChildCount() < 3) {
                row.addView(new View(getContext()), LayoutHelper.createLinear(0, 1, 1f));
            }
        }
        scroll.addView(column);
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    /** A rounded tile with a picture (document) or a backdrop circle, a name and an optional rarity. */
    private View tile(TLRPC.Document document, Object parent, TL_stars.starGiftAttributeBackdrop circle, String name, String rarity) {
        final Context context = getContext();
        final LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(8));
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(14));
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        tile.setBackground(bg);
        if (circle != null) {
            tile.addView(new BackdropView(context, circle), LayoutHelper.createLinear(64, 64));
        } else {
            final BackupImageView image = new BackupImageView(context);
            if (document != null) {
                final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, AndroidUtilities.dp(100));
                image.setImage(ImageLocation.getForDocument(document), "80_80_nolimit_pcache", ImageLocation.getForDocument(thumb, document), "80_80_nolimit_pcache", null, parent);
            }
            tile.addView(image, LayoutHelper.createLinear(64, 64));
        }
        final TextView title = new TextView(context);
        title.setText(name);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        title.setSingleLine(true);
        title.setGravity(Gravity.CENTER);
        tile.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
        if (rarity != null) {
            final TextView r = new TextView(context);
            r.setText(rarity);
            r.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            r.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            r.setGravity(Gravity.CENTER);
            tile.addView(r, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        return tile;
    }

    /** A golden «⭐ 500» pill under a collection: the gift's price in the Telegram shop. */
    private void addPrice(LinearLayout tile, long stars) {
        if (stars <= 0) {
            return;
        }
        final TextView price = new TextView(getContext());
        price.setText("⭐ " + MeridaVisual.formatNumber(stars));
        price.setTextColor(0xFFF1A73A);
        price.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        price.setTypeface(AndroidUtilities.bold());
        price.setGravity(Gravity.CENTER);
        price.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(2), AndroidUtilities.dp(8), AndroidUtilities.dp(2));
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(10));
        bg.setColor(0x33F1A73A);
        price.setBackground(bg);
        tile.addView(price, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
    }

    private static String rarityText(TL_stars.StarGiftAttribute a) {
        final int permille = a.getRarityPermille();
        if (permille <= 0) {
            return null;
        }
        final float percent = permille / 10f;
        return (percent == (int) percent ? String.valueOf((int) percent) : String.valueOf(percent)) + "%";
    }

    // ---- page 2: constructor ---------------------------------------------------------------------

    private void openCollection(TL_stars.StarGift g) {
        collection = g;
        models.clear();
        backdrops.clear();
        patterns.clear();
        root.removeAllViews();
        actionBar.setTitle(g.title != null ? g.title : LocaleController.getString(R.string.MeridaNftConstructor));
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
                for (TL_stars.StarGiftAttribute a : ((TL_stars.resaleStarGifts) res).attributes) {
                    if (a instanceof TL_stars.starGiftAttributeModel) {
                        models.add((TL_stars.starGiftAttributeModel) a);
                    } else if (a instanceof TL_stars.starGiftAttributeBackdrop) {
                        backdrops.add((TL_stars.starGiftAttributeBackdrop) a);
                    } else if (a instanceof TL_stars.starGiftAttributePattern) {
                        patterns.add((TL_stars.starGiftAttributePattern) a);
                    }
                }
            }
            if (models.isEmpty() || backdrops.isEmpty() || patterns.isEmpty()) {
                Toast.makeText(getContext(), LocaleController.getString(R.string.MeridaNftConstructorNoData), Toast.LENGTH_LONG).show();
                collection = null;
                showCollections();
                return;
            }
            model = models.get(0);
            backdrop = backdrops.get(0);
            pattern = patterns.get(0);
            buildConstructor();
        }));
    }

    private void buildConstructor() {
        final Context context = getContext();
        root.removeAllViews();
        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);

        final FrameLayout previewFrame = new FrameLayout(context);
        previewCell = new GiftSheet.GiftCell(context, currentAccount, getResourceProvider());
        previewFrame.addView(previewCell, LayoutHelper.createFrame(150, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        column.addView(previewFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 8));

        final LinearLayout tabs = new LinearLayout(context);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        final int[] names = {R.string.MeridaNftModels, R.string.MeridaNftBackdrops, R.string.MeridaNftPatterns};
        for (int i = 0; i < 3; i++) {
            final int t = i;
            final TextView tv = new TextView(context);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            tv.setTypeface(AndroidUtilities.bold());
            tv.setOnClickListener(v -> {
                tab = t;
                fillOptions();
            });
            tabViews[i] = tv;
            tabs.addView(tv, LayoutHelper.createLinear(0, 40, 1f, 4, 0, 4, 0));
        }
        column.addView(tabs, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 0, 8, 6));

        final ScrollView scroll = new ScrollView(context);
        optionsList = new LinearLayout(context);
        optionsList.setOrientation(LinearLayout.VERTICAL);
        optionsList.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        scroll.addView(optionsList);
        column.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        final TextView add = new TextView(context);
        add.setText(LocaleController.getString(R.string.MeridaNftConstructorAdd));
        add.setTextColor(0xFFFFFFFF);
        add.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        add.setTypeface(AndroidUtilities.bold());
        add.setGravity(Gravity.CENTER);
        final GradientDrawable addBg = new GradientDrawable();
        addBg.setCornerRadius(AndroidUtilities.dp(12));
        addBg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        add.setBackground(addBg);
        add.setOnClickListener(v -> addToVisual());
        column.addView(add, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 8, 16, 16));

        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        updatePreview();
        fillOptions();
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

    private void updatePreview() {
        if (previewCell == null) {
            return;
        }
        previewCell.setStarsGift(MeridaVisualGifts.preview(buildGift()), true, false);
    }

    private void fillOptions() {
        for (int i = 0; i < 3; i++) {
            final boolean selected = i == tab;
            tabViews[i].setTextColor(selected ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4) : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            final GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(AndroidUtilities.dp(20));
            bg.setColor(selected ? Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4), 0.15f) : 0);
            tabViews[i].setBackground(bg);
        }
        optionsList.removeAllViews();
        final int count = tab == TAB_MODELS ? models.size() : tab == TAB_BACKDROPS ? backdrops.size() : patterns.size();
        LinearLayout row = null;
        for (int i = 0; i < count; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                optionsList.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            final View tile;
            final boolean chosen;
            if (tab == TAB_MODELS) {
                final TL_stars.starGiftAttributeModel m = models.get(i);
                tile = tile(m.document, collection, null, m.name, rarityText(m));
                chosen = m == model;
                tile.setOnClickListener(v -> {
                    model = m;
                    updatePreview();
                    fillOptions();
                });
            } else if (tab == TAB_BACKDROPS) {
                final TL_stars.starGiftAttributeBackdrop b = backdrops.get(i);
                tile = tile(null, null, b, b.name, rarityText(b));
                chosen = b == backdrop;
                tile.setOnClickListener(v -> {
                    backdrop = b;
                    updatePreview();
                    fillOptions();
                });
            } else {
                final TL_stars.starGiftAttributePattern p = patterns.get(i);
                tile = tile(p.document, collection, null, p.name, rarityText(p));
                chosen = p == pattern;
                tile.setOnClickListener(v -> {
                    pattern = p;
                    updatePreview();
                    fillOptions();
                });
            }
            if (chosen) {
                final GradientDrawable bg = (GradientDrawable) tile.getBackground();
                bg.setStroke(AndroidUtilities.dp(2), Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            }
            row.addView(tile, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 4, 4, 4, 4));
        }
        if (row != null) {
            while (row.getChildCount() < 3) {
                row.addView(new View(getContext()), LayoutHelper.createLinear(0, 1, 1f));
            }
        }
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

    /** A round backdrop preview: the backdrop's centre colour fading to its edge colour. */
    private static class BackdropView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TL_stars.starGiftAttributeBackdrop backdrop;

        BackdropView(Context context, TL_stars.starGiftAttributeBackdrop backdrop) {
            super(context);
            this.backdrop = backdrop;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float r = Math.min(getWidth(), getHeight()) / 2f;
            paint.setShader(new RadialGradient(getWidth() / 2f, getHeight() / 2f, Math.max(1, r),
                    backdrop.center_color | 0xFF000000, backdrop.edge_color | 0xFF000000, Shader.TileMode.CLAMP));
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, r, paint);
        }
    }
}
