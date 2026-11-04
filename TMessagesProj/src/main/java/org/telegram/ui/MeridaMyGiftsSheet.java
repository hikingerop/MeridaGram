package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.merida.MeridaVisualGifts;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.utils.settings.MeridaSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * «My gifts» of the «Visual» section, laid out like Chimera NFT's: a summary card, search, filters
 * (all / in profile / hidden / on sale), a grid of gift cards, a selection mode with delete, hide and craft,
 * and «+» to add a gift. All of it lives only on this phone.
 */
final class MeridaMyGiftsSheet {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_PROFILE = 1;
    private static final int FILTER_HIDDEN = 2;
    private static final int FILTER_SALE = 3;

    private final BaseFragment fragment;
    private MeridaVisualSheets.Sheet sh;
    private LinearLayout summary;
    private LinearLayout chips;
    private LinearLayout grid;
    private LinearLayout selectionBar;
    private ImageView selectButton;
    private ImageView addButton;
    private ImageView trashButton;
    private ImageView backButton;
    private String query = "";
    private int filter = FILTER_ALL;
    private boolean selecting;
    private boolean craftMode;
    private final HashSet<String> selected = new HashSet<>();

    MeridaMyGiftsSheet(BaseFragment fragment) {
        this.fragment = fragment;
    }

    private static String s(int res) {
        return LocaleController.getString(res);
    }

    void showCraft() {
        showCraft(null);
    }

    /** Crafting with this gift already chosen (the anvil in the gift window). */
    void showCraft(String withKey) {
        craftMode = true;
        selecting = true;
        if (withKey != null) selected.add(withKey);
        show();
    }

    void show() {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final Context context = fragment.getParentActivity();
        sh = new MeridaVisualSheets.Sheet(fragment, s(R.string.MeridaVMyGifts), "");
        selectButton = sh.addButton(R.drawable.msg_select, MeridaVisualUi.BLUE, v -> setSelecting(true));
        trashButton = sh.addButton(R.drawable.merida_v_trash, 0xFFE5534B, v -> deleteSelected());
        addButton = sh.addButton(R.drawable.msg_add, MeridaVisualUi.BLUE, v -> showAddMenu());
        backButton = sh.addButton(R.drawable.ic_ab_back, MeridaVisualUi.BLUE, v -> setSelecting(false));
        sh.addClose();

        summary = new LinearLayout(context);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setBackground(MeridaVisualUi.round(MeridaVisualUi.SHEET_CARD, 22));
        summary.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        sh.body.addView(summary, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        final EditText search = new EditText(context);
        search.setHint(s(R.string.MeridaVSearchHint));
        search.setHintTextColor(0xFF8E8E93);
        search.setTextColor(0xFFFFFFFF);
        search.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        search.setSingleLine(true);
        search.setBackground(MeridaVisualUi.round(MeridaVisualUi.SHEET_CARD, 22));
        search.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable e) {
                query = e.toString().trim().toLowerCase(Locale.ROOT);
                rebuild();
            }
        });
        sh.body.addView(search, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 0, 14, 0, 0));

        final HorizontalScrollView hs = new HorizontalScrollView(context);
        hs.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(chips);
        sh.body.addView(hs, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 32, 0, 12, 0, 0));

        selectionBar = new LinearLayout(context);
        selectionBar.setOrientation(LinearLayout.HORIZONTAL);
        sh.body.addView(selectionBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38, 0, 10, 0, 0));

        grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        sh.body.addView(grid, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -4, 10, -4, 0));

        rebuild();
        sh.sheet.setOnDismissListener(d -> MeridaVisualSheets.refresh(fragment));
        sh.show(fragment);
    }

    private void setSelecting(boolean on) {
        selecting = on;
        if (!on) {
            craftMode = false;
        }
        selected.clear();
        rebuild();
    }

    // ---- data ----------------------------------------------------------------------------------------

    private static final class Item {
        String key;
        TL_stars.SavedStarGift saved;
        boolean hidden;
        boolean nft;
    }

    private List<Item> allItems() {
        final ArrayList<Item> list = new ArrayList<>();
        for (String key : MeridaVisualGifts.slugs()) {
            final TL_stars.SavedStarGift saved = MeridaVisualGifts.wrap(key);
            if (saved == null) continue;
            final Item it = new Item();
            it.key = key;
            it.saved = saved;
            it.hidden = MeridaVisualGifts.isHidden(key);
            it.nft = saved.gift instanceof TL_stars.TL_starGiftUnique;
            list.add(it);
        }
        return list;
    }

    private boolean matches(Item it) {
        if (filter == FILTER_PROFILE && it.hidden) return false;
        if (filter == FILTER_HIDDEN && !it.hidden) return false;
        if (filter == FILTER_SALE && MeridaVisualGifts.salePrice(it.key) <= 0) return false;
        if (query.isEmpty()) return true;
        final TL_stars.StarGift g = it.saved.gift;
        final StringBuilder hay = new StringBuilder();
        if (g.title != null) hay.append(g.title).append(' ');
        if (g instanceof TL_stars.TL_starGiftUnique) {
            final TL_stars.TL_starGiftUnique u = (TL_stars.TL_starGiftUnique) g;
            hay.append('#').append(u.num).append(' ').append(u.num).append(' ');
            for (TL_stars.StarGiftAttribute a : u.attributes) {
                if (a.name != null) hay.append(a.name).append(' ');
            }
        }
        return hay.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    // ---- ui ------------------------------------------------------------------------------------------

    private void rebuild() {
        final Context context = sh.body.getContext();
        final List<Item> all = allItems();
        int nft = 0, hidden = 0;
        for (Item it : all) {
            if (it.nft) nft++;
            if (it.hidden) hidden++;
        }
        final int inProfile = all.size() - hidden;

        sh.title.setText(selecting ? LocaleController.formatString(R.string.MeridaVSelected, selected.size()) : s(R.string.MeridaVMyGifts));
        selectButton.setVisibility(selecting ? View.GONE : View.VISIBLE);
        addButton.setVisibility(selecting ? View.GONE : View.VISIBLE);
        trashButton.setVisibility(selecting ? View.VISIBLE : View.GONE);
        backButton.setVisibility(selecting ? View.VISIBLE : View.GONE);

        summary.removeAllViews();
        summary.addView(MeridaVisualSheets.label(context, all.size() + " " + MeridaVisualUi.plural(all.size(), "подарок", "подарка", "подарков"), 0xFFFFFFFF, 20, true));
        summary.addView(MeridaVisualSheets.label(context, nft + " NFT · " + LocaleController.formatString(R.string.MeridaVOrdinaryCount, all.size() - nft), 0xFF8E8E93, 13.5f, false));
        final LinearLayout stats = new LinearLayout(context);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(MeridaVisualSheets.label(context, LocaleController.formatString(R.string.MeridaVInProfileN, inProfile), MeridaVisualUi.BLUE, 13.5f, false), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 20, 0));
        stats.addView(MeridaVisualSheets.label(context, LocaleController.formatString(R.string.MeridaVHiddenN, hidden), 0xFF8E8E93, 13.5f, false), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 20, 0));
        final int onSale = MeridaVisualGifts.onSaleCount();
        stats.addView(MeridaVisualSheets.label(context, LocaleController.formatString(R.string.MeridaVOnSaleN, onSale), 0xFF8E8E93, 13.5f, false));
        summary.addView(stats, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        chips.removeAllViews();
        if (selecting) {
            addChip(context, LocaleController.formatString(R.string.MeridaVSelectAll, all.size()), true, true, () -> {
                final List<Item> visible = new ArrayList<>();
                for (Item it : allItems()) if (matches(it)) visible.add(it);
                if (selected.size() == visible.size()) {
                    selected.clear();
                } else {
                    for (Item it : visible) selected.add(it.key);
                }
                rebuild();
            });
        }
        addChip(context, LocaleController.formatString(R.string.MeridaVFilterAll, all.size()), filter == FILTER_ALL, true, () -> { filter = FILTER_ALL; rebuild(); });
        addChip(context, LocaleController.formatString(R.string.MeridaVFilterProfile, inProfile), filter == FILTER_PROFILE, inProfile > 0, () -> { filter = FILTER_PROFILE; rebuild(); });
        addChip(context, LocaleController.formatString(R.string.MeridaVFilterHidden, hidden), filter == FILTER_HIDDEN, hidden > 0, () -> { filter = FILTER_HIDDEN; rebuild(); });
        addChip(context, LocaleController.formatString(R.string.MeridaVFilterSale, onSale), filter == FILTER_SALE, onSale > 0, () -> { filter = FILTER_SALE; rebuild(); });

        selectionBar.removeAllViews();
        selectionBar.setVisibility(selecting ? View.VISIBLE : View.GONE);
        if (selecting) {
            if (craftMode) {
                final TextView hint = MeridaVisualSheets.label(context, s(R.string.MeridaVCraftPick), 0xFF8E8E93, 12, false);
                hint.setGravity(Gravity.CENTER_VERTICAL);
                selectionBar.addView(hint, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
            } else {
                addAction(context, s(R.string.MeridaVHide), () -> setHidden(true));
                addAction(context, s(R.string.MeridaVShow), () -> setHidden(false));
            }
            addAction(context, "🎲 " + s(R.string.MeridaVCraft), this::craft);
        }

        grid.removeAllViews();
        LinearLayout line = null;
        int col = 0;
        for (Item it : all) {
            if (!matches(it)) continue;
            if (col % 3 == 0) {
                line = new LinearLayout(context);
                line.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(line, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            line.addView(tile(context, it), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            col++;
        }
        if (line != null) {
            while (col % 3 != 0) {
                line.addView(new View(context), LayoutHelper.createLinear(0, 1, 1f));
                col++;
            }
        }
        if (col == 0) {
            final TextView empty = MeridaVisualSheets.label(context, s(R.string.MeridaVNoGifts), 0xFF8E8E93, 12.5f, false);
            empty.setGravity(Gravity.CENTER);
            grid.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 120));
        }
    }

    private void addChip(Context context, String text, boolean active, boolean enabled, Runnable click) {
        // compact chips (32 dp, 13 sp) so «All / In profile / Hidden / On sale» fit in one row
        final TextView chip = MeridaVisualSheets.label(context, text, active ? MeridaVisualUi.BLUE : enabled ? 0xFFFFFFFF : 0xFF5C5C5E, 13, false);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(AndroidUtilities.dp(11), 0, AndroidUtilities.dp(11), 0);
        chip.setBackground(MeridaVisualUi.round(active ? 0xFF1C2E44 : enabled ? 0xFF000000 : 0xFF1A1A1A, 16));
        if (enabled) {
            chip.setOnClickListener(v -> {
                // the grid fades and shrinks a little, then the new filter fades in
                grid.animate().cancel();
                grid.animate().alpha(0f).scaleX(.96f).scaleY(.96f).setDuration(110).withEndAction(() -> {
                    click.run();
                    grid.setScaleX(.96f);
                    grid.setScaleY(.96f);
                    grid.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180)
                            .setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT_QUINT).start();
                }).start();
            });
        }
        chips.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, 0, 0, 6, 0));
    }

    private void addAction(Context context, String text, Runnable click) {
        final TextView b = MeridaVisualUi.pillButton(context, text, false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        b.setOnClickListener(v -> click.run());
        selectionBar.addView(b, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, 0, 0, 8, 0));
    }

    private View tile(Context context, Item it) {
        final FrameLayout frame = new FrameLayout(context);
        final GiftSheet.GiftCell cell = new GiftSheet.GiftCell(context, fragment.getCurrentAccount(), fragment.getResourceProvider());
        cell.setImageLayer(7); // full-speed animation inside the sheet, like Telegram's gift sheets
        cell.setStarsGift(it.saved, true, false);
        if (it.hidden) {
            cell.setAlpha(0.5f);
        }
        frame.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        if (selecting) {
            final CheckCircle check = new CheckCircle(context, selected.contains(it.key));
            frame.addView(check, LayoutHelper.createFrame(30, 30, Gravity.TOP | Gravity.LEFT, 14, 12, 0, 0));
        }
        cell.setOnClickListener(v -> {
            if (selecting) {
                if (!selected.remove(it.key)) {
                    selected.add(it.key);
                }
                rebuild();
            } else {
                showItem(it);
            }
        });
        cell.setOnLongClickListener(v -> {
            if (!selecting) {
                selecting = true;
                selected.add(it.key);
                rebuild();
            }
            return true;
        });
        return frame;
    }

    /**
     * Tap on a gift in «My gifts», like Chimera: a compact card — the gift, its name and tags («Sale»), a small
     * table (model / pattern / backdrop or price) and «Edit / Hide / Delete». «Edit» opens the full gift window.
     */
    private void showItem(Item it) {
        final Context context = fragment.getParentActivity();
        if (context == null) return;
        final android.app.Dialog dialog = new android.app.Dialog(context, R.style.TransparentDialog);
        final FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0x99000000);
        root.setOnClickListener(v -> dialog.dismiss());
        final LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        card.setBackground(MeridaVisualUi.round(0xFF2A2A2C, 24));
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14));

        final LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        final GiftSheet.GiftCell cell = new GiftSheet.GiftCell(context, fragment.getCurrentAccount(), fragment.getResourceProvider());
        cell.setImageLayer(7); // full-speed animation inside the sheet, like Telegram's gift sheets
        cell.setStarsGift(it.saved, true, false);
        top.addView(cell, LayoutHelper.createLinear(104, LayoutHelper.WRAP_CONTENT, 0, 0, 12, 0));
        final LinearLayout right = new LinearLayout(context);
        right.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        final TL_stars.StarGift g = it.saved.gift;
        final String name = (g.title != null ? g.title : s(R.string.MeridaVisualGiftTitle)) + (g instanceof TL_stars.TL_starGiftUnique ? " #" + ((TL_stars.TL_starGiftUnique) g).num : "");
        titleRow.addView(MeridaVisualSheets.label(context, name, 0xFFFFFFFF, 16, true), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        final long sale = MeridaVisualGifts.salePrice(it.key);
        if (sale > 0) titleRow.addView(tag(context, s(R.string.MeridaVSaleTag), 0xFF4CAF50), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 26, 6, 0, 0, 0));
        if (it.hidden) titleRow.addView(tag(context, s(R.string.MeridaVHiddenTag), 0xFF8E8E93), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 26, 6, 0, 0, 0));
        // titleRow.addView(tag(context, s(R.string.MeridaVisualMark), 0xFFF5A623), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 26, 6, 0, 0, 0));
        right.addView(titleRow);

        final LinearLayout table = new LinearLayout(context);
        table.setOrientation(LinearLayout.VERTICAL);
        table.setBackground(MeridaVisualUi.round(0xFF000000, 14));
        table.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8));
        if (g instanceof TL_stars.TL_starGiftUnique) {
            final TL_stars.starGiftAttributeModel m = StarsController.findAttribute(g.attributes, TL_stars.starGiftAttributeModel.class);
            final TL_stars.starGiftAttributePattern p = StarsController.findAttribute(g.attributes, TL_stars.starGiftAttributePattern.class);
            final TL_stars.starGiftAttributeBackdrop b = StarsController.findAttribute(g.attributes, TL_stars.starGiftAttributeBackdrop.class);
            if (m != null) tableRow(table, s(R.string.MeridaNftModel), m.name);
            if (p != null) tableRow(table, s(R.string.MeridaNftPattern), p.name);
            if (b != null) tableRow(table, s(R.string.MeridaNftBackdrop), b.name);
        } else {
            tableRow(table, s(R.string.MeridaVCost), org.telegram.messenger.merida.MeridaVisual.stars(g.stars));
        }
        if (sale > 0) tableRow(table, s(R.string.MeridaVSaleTag), org.telegram.messenger.merida.MeridaVisual.stars(sale));
        right.addView(table, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        top.addView(right, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        card.addView(top);

        final LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(actionButton(context, s(R.string.MeridaVEdit), MeridaVisualUi.BLUE, 0xFF1C2E44, () -> {
            dialog.dismiss();
            final List<TL_stars.SavedStarGift> visible = new ArrayList<>();
            for (Item other : allItems()) if (matches(other)) visible.add(other.saved);
            MeridaVisualGiftSheet.show(fragment, it.saved, visible, this::rebuild, false);
        }), LayoutHelper.createLinear(0, 42, 1f, 0, 0, 6, 0));
        buttons.addView(actionButton(context, s(it.hidden ? R.string.MeridaVShow : R.string.MeridaVHide), MeridaVisualUi.BLUE, 0xFF1C2E44, () -> {
            dialog.dismiss();
            MeridaVisualGifts.setHidden(it.key, !it.hidden);
            rebuild();
        }), LayoutHelper.createLinear(0, 42, 1f, 3, 0, 3, 0));
        buttons.addView(actionButton(context, s(R.string.Delete), 0xFFE5534B, 0xFF3A1F1F, () -> {
            dialog.dismiss();
            MeridaVisualGifts.remove(it.key);
            if (it.key.equals(MeridaSettings.visualWorn.get())) {
                MeridaSettings.visualWorn.set(null);
            }
            rebuild();
        }), LayoutHelper.createLinear(0, 42, 1f, 6, 0, 0, 0));
        card.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42, 0, 14, 0, 0));

        root.addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 12, 0, 12, 0));
        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        }
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        card.setAlpha(0f);
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT_BACK).start();
        dialog.show();
    }

    private static TextView tag(Context context, String text, int color) {
        final TextView t = MeridaVisualSheets.label(context, text, color, 13, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        final android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(13));
        bg.setColor((color & 0x00FFFFFF) | 0x22000000);
        bg.setStroke(AndroidUtilities.dp(1), color);
        t.setBackground(bg);
        return t;
    }

    private static void tableRow(LinearLayout table, String label, CharSequence value) {
        final Context context = table.getContext();
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(MeridaVisualSheets.label(context, label, 0xFF8E8E93, 13, false), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        final TextView v = MeridaVisualSheets.label(context, value, 0xFFFFFFFF, 13, false);
        v.setGravity(Gravity.RIGHT);
        v.setSingleLine(true);
        row.addView(v, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        table.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 2));
    }

    private static TextView actionButton(Context context, String text, int color, int bg, Runnable click) {
        final TextView b = MeridaVisualSheets.label(context, text, color, 15, false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(MeridaVisualUi.round(bg, 21));
        b.setOnClickListener(v -> click.run());
        return b;
    }

    /** The round selection mark in the corner of a tile. */
    private static final class CheckCircle extends View {
        private final boolean checked;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        CheckCircle(Context context, boolean checked) {
            super(context);
            this.checked = checked;
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final float r = getWidth() / 2f - AndroidUtilities.dp(1.5f);
            if (checked) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(MeridaVisualUi.BLUE);
                canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, r, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(2.2f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setColor(0xFFFFFFFF);
                path.reset();
                path.moveTo(getWidth() * 0.28f, getHeight() * 0.52f);
                path.lineTo(getWidth() * 0.44f, getHeight() * 0.68f);
                path.lineTo(getWidth() * 0.73f, getHeight() * 0.36f);
                canvas.drawPath(path, paint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x33000000);
                canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, r, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(2));
                paint.setColor(0xFFFFFFFF);
                canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, r, paint);
            }
        }
    }

    // ---- actions -------------------------------------------------------------------------------------

    private void showAddMenu() {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle(s(R.string.MeridaVAddGift));
        b.setItems(new CharSequence[]{s(R.string.MeridaVAddNft), s(R.string.MeridaVAddOrdinary)}, (d, which) -> {
            sh.sheet.dismiss();
            if (which == 0) {
                fragment.presentFragment(new MeridaNftConstructorActivity());
            } else {
                fragment.presentFragment(new MeridaNftConstructorActivity(MeridaNftConstructorActivity.MODE_GIFTS));
            }
        });
        b.setNegativeButton(s(R.string.Cancel), null);
        b.show();
    }

    private void deleteSelected() {
        if (selected.isEmpty()) {
            return;
        }
        MeridaVisualActivity.confirm(fragment, LocaleController.formatString(R.string.MeridaVSelected, selected.size()), s(R.string.MeridaVDeleteGiftsQ), s(R.string.Delete), () -> {
            for (String key : selected) {
                MeridaVisualGifts.remove(key);
                if (key.equals(MeridaSettings.visualWorn.get())) {
                    MeridaSettings.visualWorn.set(null);
                }
            }
            setSelecting(false);
        });
    }

    private void setHidden(boolean hidden) {
        for (String key : selected) {
            MeridaVisualGifts.setHidden(key, hidden);
        }
        setSelecting(false);
    }

    /** Craft: the chosen gifts are used up; with luck (or «always succeed») a new NFT made of their traits appears. */
    private void craft() {
        final ArrayList<TL_stars.TL_starGiftUnique> nfts = new ArrayList<>();
        for (String key : selected) {
            final TL_stars.TL_starGiftUnique u = MeridaVisualGifts.load(key);
            if (u != null) nfts.add(u);
        }
        if (nfts.size() < 2) {
            MeridaVisualSheets.toast(fragment, s(R.string.MeridaVCraftNeed2));
            return;
        }
        final int fixed = MeridaSettings.visualCraftChance.get();
        final int chance = fixed >= 0 ? Math.min(100, fixed) : Math.min(100, 25 * nfts.size());
        final boolean always = MeridaSettings.visualCraftAlways.get();
        final String message = LocaleController.formatString(R.string.MeridaVCraftConfirm, nfts.size(), always ? 100 : chance)
                + (always ? "\n" + s(R.string.MeridaVCraftAlwaysOn) : "");
        MeridaVisualActivity.confirm(fragment, s(R.string.MeridaVCraft), message, s(R.string.MeridaVCraftGo), () -> {
            final Random r = new Random();
            final boolean success = always || r.nextInt(100) < chance;
            for (String key : selected) {
                MeridaVisualGifts.remove(key);
                if (key.equals(MeridaSettings.visualWorn.get())) {
                    MeridaSettings.visualWorn.set(null);
                }
            }
            if (success) {
                final TL_stars.TL_starGiftUnique base = nfts.get(0);
                final TL_stars.TL_starGiftUnique result = new TL_stars.TL_starGiftUnique();
                result.id = base.id;
                result.gift_id = base.gift_id;
                result.title = base.title;
                result.slug = "";
                result.num = 1;
                final TL_stars.TL_starGiftUnique m = nfts.get(r.nextInt(nfts.size()));
                final TL_stars.TL_starGiftUnique b = nfts.get(r.nextInt(nfts.size()));
                final TL_stars.TL_starGiftUnique p = nfts.get(r.nextInt(nfts.size()));
                final TL_stars.starGiftAttributeModel model = StarsController.findAttribute(m.attributes, TL_stars.starGiftAttributeModel.class);
                final TL_stars.starGiftAttributePattern pattern = StarsController.findAttribute(p.attributes, TL_stars.starGiftAttributePattern.class);
                final TL_stars.starGiftAttributeBackdrop backdrop = StarsController.findAttribute(b.attributes, TL_stars.starGiftAttributeBackdrop.class);
                if (model != null) result.attributes.add(model);
                if (pattern != null) result.attributes.add(pattern);
                if (backdrop != null) result.attributes.add(backdrop);
                MeridaVisualGifts.store("craft-" + System.currentTimeMillis(), result);
                MeridaVisualSheets.toast(fragment, LocaleController.formatString(R.string.MeridaVCraftDone, result.title + (model != null ? " · " + model.name : "")));
            } else {
                MeridaVisualSheets.toast(fragment, s(R.string.MeridaVCraftFail));
            }
            setSelecting(false);
        });
    }
}
