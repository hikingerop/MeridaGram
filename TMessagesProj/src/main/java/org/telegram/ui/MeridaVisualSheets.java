package org.telegram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.merida.MeridaVisual;
import org.telegram.messenger.merida.MeridaVisualGifts;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BadgeLevelDrawable;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.utils.settings.MeridaSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Bottom sheets of the «Visual» screens, laid out like Chimera NFT's: «Balance», «My usernames»,
 * «My +888 number», «Profile rating». Values are kept on this phone only.
 */
final class MeridaVisualSheets {

    private MeridaVisualSheets() {
    }

    private static String s(int res) {
        return LocaleController.getString(res);
    }

    // ---- common --------------------------------------------------------------------------------------

    /** A dark bottom sheet with a title bar (title, blue subtitle, buttons, ✕) and a scrolling body. */
    static final class Sheet {
        final BottomSheet sheet;
        final LinearLayout body;
        final LinearLayout titleBar;
        final TextView title;
        final TextView subtitle;

        Sheet(BaseFragment fragment, CharSequence titleText, CharSequence subtitleText) {
            final Context context = fragment.getParentActivity();
            sheet = new BottomSheet(context, true);
            sheet.setBackgroundColor(MeridaVisualUi.SHEET_BG);
            sheet.fixNavigationBar(MeridaVisualUi.SHEET_BG);
            final LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(6), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
            final TextView[] t = new TextView[1];
            final TextView[] st = new TextView[1];
            titleBar = MeridaVisualUi.sheetTitle(context, titleText, subtitleText, t, st);
            title = t[0];
            subtitle = st[0];
            root.addView(titleBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));
            final ScrollView scroll = new ScrollView(context);
            scroll.setVerticalScrollBarEnabled(false);
            body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(body);
            root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            sheet.setCustomView(root);
        }

        ImageView addButton(int icon, int color, View.OnClickListener click) {
            final ImageView b = MeridaVisualUi.iconButton(titleBar.getContext(), icon, color);
            b.setOnClickListener(click);
            titleBar.addView(b, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL, 4, 0, 0, 0));
            return b;
        }

        void addClose() {
            addButton(R.drawable.ic_close_white, 0xFFFFFFFF, v -> sheet.dismiss());
        }

        void setSubtitle(CharSequence text) {
            subtitle.setText(text);
            subtitle.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        }

        /** Shown directly (not as the fragment's dialog), so dialogs opened from it do not close it. */
        void show(BaseFragment fragment) {
            sheet.show();
        }
    }

    static TextView label(Context context, CharSequence text, int color, float sizeDp, boolean bold) {
        final TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        if (bold) {
            tv.setTypeface(AndroidUtilities.bold());
        }
        return tv;
    }

    static LinearLayout blackCard(Context context) {
        final LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(MeridaVisualUi.round(MeridaVisualUi.SHEET_CARD, 22));
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        return card;
    }

    /** «Show in profile: on/off» chip: blue-tinted with blue text when on, black with grey text when off. */
    static TextView showChip(Context context, boolean on, Runnable toggle) {
        final TextView chip = label(context, "", 0xFFFFFFFF, 13.5f, false);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        final boolean[] state = {on};
        final Runnable paint = () -> {
            chip.setText(LocaleController.formatString(R.string.MeridaVShowInProfile, s(state[0] ? R.string.MeridaVOn : R.string.MeridaVOff)));
            chip.setTextColor(state[0] ? 0xFFFFFFFF : 0xFFAAAAAA);
            chip.setBackground(MeridaVisualUi.round(state[0] ? MeridaVisualUi.BLUE : 0xFF000000, 24));
        };
        paint.run();
        chip.setOnClickListener(v -> {
            state[0] = !state[0];
            paint.run();
            toggle.run();
        });
        return chip;
    }

    static TextView toggleChip(Context context, int format, boolean on, Runnable toggle) {
        final TextView chip = label(context, "", MeridaVisualUi.BLUE, 13.5f, false);
        chip.setGravity(Gravity.CENTER);
        chip.setMaxLines(2);
        chip.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(4), AndroidUtilities.dp(14), AndroidUtilities.dp(4));
        final boolean[] state = {on};
        final Runnable paint = () -> {
            chip.setText(LocaleController.formatString(format, s(state[0] ? R.string.MeridaVOn : R.string.MeridaVOff)));
            chip.setTextColor(state[0] ? MeridaVisualUi.BLUE : 0xFFAAAAAA);
            chip.setBackground(MeridaVisualUi.round(state[0] ? 0xFF1C2E44 : 0xFF000000, 24));
        };
        paint.run();
        chip.setOnClickListener(v -> {
            state[0] = !state[0];
            paint.run();
            toggle.run();
        });
        return chip;
    }

    /** ≈ price in the currency of your Telegram stars shop (₽ for Russia), else in dollars. */
    static String localPrice(BaseFragment fragment, long stars) {
        if (stars <= 0) {
            return null;
        }
        final java.util.ArrayList<org.telegram.tgnet.tl.TL_stars.TL_starsTopupOption> options = org.telegram.ui.Stars.StarsController.getInstance(fragment.getCurrentAccount()).getOptions();
        if (options != null) {
            org.telegram.tgnet.tl.TL_stars.TL_starsTopupOption best = null;
            for (org.telegram.tgnet.tl.TL_stars.TL_starsTopupOption o : options) {
                if (o != null && o.stars > 0 && o.currency != null && (best == null || o.stars > best.stars)) {
                    best = o;
                }
            }
            if (best != null) {
                final long amount = Math.round(best.amount * (double) stars / best.stars);
                final int exp = org.telegram.messenger.BillingController.getInstance().getCurrencyExp(best.currency);
                final long unit = (long) Math.pow(10, exp);
                final long rounded = Math.round(amount / (double) unit) * unit;
                return "≈ " + org.telegram.messenger.BillingController.getInstance().formatCurrency(rounded, best.currency, exp, true);
            }
        }
        return "≈ " + usd(starsUsd(fragment, stars));
    }

    static String dateText(long ms) {
        final java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d MMMM yyyy, HH:mm",
                "ru".equals(LocaleController.getInstance().getCurrentLocale().getLanguage()) ? new java.util.Locale("ru") : java.util.Locale.getDefault());
        return f.format(new java.util.Date(ms));
    }

    static void askNumber(BaseFragment fragment, CharSequence title, int current, IntConsumer onDone) {
        askText(fragment, title, String.valueOf(current), InputType.TYPE_CLASS_NUMBER, text -> {
            try {
                onDone.accept(Integer.parseInt(text.replaceAll("[^0-9]", "")));
            } catch (Exception ignore) {
                onDone.accept(0);
            }
        });
    }

    static void askText(BaseFragment fragment, CharSequence title, String current, int inputType, java.util.function.Consumer<String> onDone) {
        final Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setInputType(inputType);
        input.setSingleLine(true);
        input.setBackground(null);
        input.setLineColors(Theme.getColor(Theme.key_dialogInputField), Theme.getColor(Theme.key_dialogInputFieldActivated), Theme.getColor(Theme.key_text_RedBold));
        input.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        if (current != null) {
            input.setText(current);
            input.setSelection(input.length());
        }
        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 4));
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setView(layout);
        builder.setNegativeButton(s(R.string.Cancel), null);
        builder.setPositiveButton(s(R.string.Save), (d, w) -> onDone.accept(input.getText() == null ? "" : input.getText().toString().trim()));
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            AndroidUtilities.showKeyboard(input);
        });
        dialog.show();
    }

    static void toast(BaseFragment fragment, CharSequence text) {
        if (fragment.getParentActivity() != null) {
            Toast.makeText(fragment.getParentActivity(), text, Toast.LENGTH_LONG).show();
        }
    }

    static void refresh(BaseFragment fragment) {
        if (fragment instanceof MeridaVisualActivity) {
            ((MeridaVisualActivity) fragment).rebuild();
            ((MeridaVisualActivity) fragment).notifyProfile();
        }
    }

    // ---- gift by link --------------------------------------------------------------------------------

    static void askGiftLink(BaseFragment fragment, Runnable onAdded) {
        askText(fragment, s(R.string.MeridaVisualGiftAdd), "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, text -> {
            final String slug = MeridaVisualGifts.parseSlug(text);
            if (slug == null) {
                toast(fragment, s(R.string.MeridaVisualGiftBadLink));
                return;
            }
            MeridaVisualGifts.add(fragment.getCurrentAccount(), slug, title -> {
                toast(fragment, title != null ? LocaleController.formatString(R.string.MeridaVisualGiftAdded, title) : s(R.string.MeridaVisualGiftNotFound));
                if (title != null && onAdded != null) {
                    onAdded.run();
                }
            });
        });
    }

    // ---- balance -------------------------------------------------------------------------------------

    static double starsUsd(BaseFragment fragment, long stars) {
        return stars * MessagesController.getInstance(fragment.getCurrentAccount()).starsUsdWithdrawRate1000 * 0.00001;
    }

    static double tonUsdRate(BaseFragment fragment) {
        try {
            return MessagesController.getInstance(fragment.getCurrentAccount()).config.tonUsdRate.get();
        } catch (Throwable e) {
            return 0;
        }
    }

    static String usd(double v) {
        return "$" + (v >= 100 ? String.format(java.util.Locale.US, "%.2f", v) : String.format(java.util.Locale.US, "%.1f", v));
    }

    static void showBalance(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final Context context = fragment.getParentActivity();
        final long[] stars = {MeridaSettings.visualStars.get()};
        final long[] grams = {MeridaSettings.visualGrams.get()};
        final boolean[] spend = {MeridaSettings.visualSpendStars.get()};
        final Sheet sh = new Sheet(fragment, s(R.string.MeridaVBalance), "");
        sh.addClose();

        // like Chimera: the numbers are typed right in place with the keyboard
        final EditTextBoldCursor starsValue = amountField(context);
        final TextView starsUsd = label(context, "", 0xFF7C7C7C, 13.5f, false);
        final EditTextBoldCursor gramsValue = amountField(context);
        final TextView gramsUsd = label(context, "", 0xFF7C7C7C, 13.5f, false);
        final Runnable update = () -> {
            setIfDifferent(starsValue, stars[0]);
            setIfDifferent(gramsValue, grams[0]);
            starsUsd.setText("≈ " + usd(starsUsd(fragment, stars[0])));
            final double rate = tonUsdRate(fragment);
            gramsUsd.setText(rate > 0 ? "≈ " + usd(grams[0] * rate) + " · " + LocaleController.formatString(R.string.MeridaVTonRate, String.format(java.util.Locale.US, "%.2f", rate)) : "");
            sh.setSubtitle(org.telegram.messenger.merida.MeridaVisual.balance(stars[0], grams[0]));
        };

        watchAmount(starsValue, stars, update);
        watchAmount(gramsValue, grams, update);
        sh.body.addView(balanceCard(fragment, context, org.telegram.ui.Stars.StarsIntroActivity.replaceStarsWithPlain("⭐", 1.2f), starsValue, "Stars", starsUsd, new int[]{100, 1000, 10000}, stars, update),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
        sh.body.addView(balanceCard(fragment, context, org.telegram.ui.Stars.StarsIntroActivity.replaceStarsWithPlain(true, "⭐", 1.2f), gramsValue, "GRAM", gramsUsd, new int[]{1, 10, 100}, grams, update),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));
        final TextView note = label(context, s(R.string.MeridaVBalanceNote), 0xFF7C7C7C, 12, false);
        // sh.body.addView(note, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 2, 14, 2, 8));

        final MeridaVisualUi.Row[] spendRow = new MeridaVisualUi.Row[1];
        spendRow[0] = MeridaVisualUi.switchRow(context, 0, s(R.string.MeridaVSpendStars), s(R.string.MeridaVSpendStarsDesc), spend[0], false, v -> {
            spend[0] = !spend[0];
            spendRow[0].setChecked(spend[0]);
        });
        spendRow[0].setBackground(null);
        spendRow[0].title.setTextColor(0xFFFFFFFF);
        sh.body.addView(spendRow[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -10, 0, -10, 0));

        final TextView save = MeridaVisualUi.bigButton(context, s(R.string.Save));
        save.setOnClickListener(v -> {
            MeridaSettings.visualStars.set((int) Math.min(Integer.MAX_VALUE, stars[0]));
            MeridaSettings.visualGrams.set((int) Math.min(Integer.MAX_VALUE, grams[0]));
            MeridaSettings.visualSpendStars.set(spend[0]);
            AndroidUtilities.hideKeyboard(starsValue);
            sh.sheet.dismiss();
            refresh(fragment);
            try {
                org.telegram.ui.Components.BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, s(R.string.MeridaVBalanceUpdated)).show();
            } catch (Throwable ignore) {
            }
        });
        sh.body.addView(save, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 0, 16, 0, 0));
        update.run();
        sh.show(fragment);
    }

    private static EditTextBoldCursor amountField(Context context) {
        final EditTextBoldCursor field = new EditTextBoldCursor(context);
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28.5f);
        field.setTextColor(0xFFFFFFFF);
        field.setTypeface(AndroidUtilities.bold());
        field.setBackground(null);
        field.setPadding(0, 0, 0, 0);
        field.setCursorColor(0xFFFFFFFF);
        field.setCursorSize(AndroidUtilities.dp(30));
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setSingleLine(true);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        return field;
    }

    private static void setIfDifferent(EditTextBoldCursor field, long value) {
        final String now = field.getText() == null ? "" : field.getText().toString();
        long current;
        try {
            current = now.isEmpty() ? -1 : Long.parseLong(now);
        } catch (Exception e) {
            current = -1;
        }
        if (current != value && !(now.isEmpty() && field.hasFocus() && value == 0)) {
            field.setText(String.valueOf(value));
            field.setSelection(field.length());
        }
    }

    private static void watchAmount(EditTextBoldCursor field, long[] holder, Runnable update) {
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence a, int b, int c, int d) {}
            @Override public void onTextChanged(CharSequence a, int b, int c, int d) {}
            @Override
            public void afterTextChanged(android.text.Editable e) {
                long v;
                try {
                    v = e.length() == 0 ? 0 : Math.min(Integer.MAX_VALUE, Long.parseLong(e.toString()));
                } catch (Exception ex) {
                    v = holder[0];
                }
                if (v != holder[0]) {
                    holder[0] = v;
                    update.run();
                }
            }
        });
    }

    private static View balanceCard(BaseFragment fragment, Context context, CharSequence icon, TextView value, String unit, TextView usd, int[] steps, long[] holder, Runnable update) {
        final LinearLayout card = blackCard(context);
        final LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(label(context, icon, MeridaVisualUi.BLUE, 20, false), LayoutHelper.createLinear(40, LayoutHelper.WRAP_CONTENT));
        top.addView(value, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 4, 0, 0, 0));
        top.addView(label(context, unit, 0xFF7C7C7C, 15, false));

        card.addView(top);
        card.addView(usd, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        final LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        for (int step : steps) {
            final TextView b = MeridaVisualUi.pillButton(context, "+" + step, false);
            b.setBackground(MeridaVisualUi.round(0xFF0E1A26, 24));
            b.setOnClickListener(v -> {
                holder[0] = Math.min(Integer.MAX_VALUE, holder[0] + step);
                update.run();
            });
            buttons.addView(b, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 38, 0, 0, 10, 0));
        }
        final TextView zero = label(context, "0", 0xFF7C7C7C, 14.5f, false);
        zero.setGravity(Gravity.CENTER);
        zero.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        zero.setOnClickListener(v -> {
            holder[0] = 0;
            update.run();
        });
        buttons.addView(zero, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 38));
        final HorizontalScrollView hs = new HorizontalScrollView(context);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(buttons);
        card.addView(hs, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));
        return card;
    }

    // ---- usernames -----------------------------------------------------------------------------------

    static List<String> usernames() {
        final ArrayList<String> list = new ArrayList<>();
        final String all = MeridaSettings.visualUsernames.get();
        if (all != null) {
            for (String u : all.split("\n")) {
                if (!u.isEmpty() && !list.contains(u)) list.add(u);
            }
        }
        final String active = MeridaSettings.visualUsername.get();
        if (active != null && !active.isEmpty() && !list.contains(active)) {
            list.add(0, active);
        }
        return list;
    }

    static void saveUsernames(List<String> list) {
        MeridaSettings.visualUsernames.set(TextUtils.join("\n", list));
    }

    private static final int[] PILL_COLORS = {0xFF8E6CE0, 0xFFC98A3E, 0xFF3FA36B, 0xFF3E8BD9, 0xFFD25C7C, 0xFF4BA6A6};

    static void showUsernames(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final Context context = fragment.getParentActivity();
        final Sheet sh = new Sheet(fragment, s(R.string.MeridaVMyUsernames), "");
        final TextView count = label(context, "", MeridaVisualUi.BLUE, 12.5f, false);
        count.setGravity(Gravity.CENTER);
        final GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xFF1C2A3A);
        count.setBackground(circle);
        sh.titleBar.addView(count, LayoutHelper.createLinear(34, 34, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
        final LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        final Runnable[] rebuild = new Runnable[1];
        sh.addButton(R.drawable.msg_add, MeridaVisualUi.BLUE, v -> askText(fragment, s(R.string.MeridaVUsernameNoAt), "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, text -> {
            final String username = text.startsWith("@") ? text.substring(1) : text;
            if (username.isEmpty()) {
                return;
            }
            MeridaVisual.checkUsername(fragment.getCurrentAccount(), username, result -> {
                if (result == MeridaVisual.CHECK_OK) {
                    final List<String> list = usernames();
                    list.remove(username);
                    list.add(username);
                    saveUsernames(list);
                    MeridaSettings.visualUsername.set(username);
                    setUsernameMeta(username, 108, System.currentTimeMillis());
                    rebuild[0].run();
                    refresh(fragment);
                    showCostDate(fragment, "@" + username, false, username, 108, System.currentTimeMillis(), (tenths, time) -> setUsernameMeta(username, tenths, time));
                } else {
                    toast(fragment, s(result == MeridaVisual.CHECK_TAKEN ? R.string.MeridaVisualUsernameTaken
                            : result == MeridaVisual.CHECK_INVALID ? R.string.MeridaVisualUsernameInvalid : R.string.MeridaVisualUsernameError));
                }
            });
        }));
        sh.addClose();
        sh.body.addView(label(context, s(R.string.MeridaVUsernamesHint), 0xFF7C7C7C, 13.5f, false), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        final LinearLayout chipsRow = new LinearLayout(context);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.addView(toggleChip(context, R.string.MeridaVShowInProfile, MeridaSettings.visualUsernameShow.get(), () -> {
            MeridaSettings.visualUsernameShow.set(!MeridaSettings.visualUsernameShow.get());
            refresh(fragment);
        }), LayoutHelper.createLinear(0, 46, 1f, 0, 0, 6, 0));
        chipsRow.addView(toggleChip(context, R.string.MeridaVReplaceOriginal, MeridaSettings.visualUsernameReplace.get(), () -> {
            MeridaSettings.visualUsernameReplace.set(!MeridaSettings.visualUsernameReplace.get());
            refresh(fragment);
        }), LayoutHelper.createLinear(0, 46, 1f, 6, 0, 0, 0));
        sh.body.addView(chipsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 0, 0, 0, 14));
        sh.body.addView(grid);

        rebuild[0] = () -> {
            grid.removeAllViews();
            final List<String> list = usernames();
            count.setText(String.valueOf(list.size()));
            final String active = MeridaSettings.visualUsername.get();
            LinearLayout line = null;
            for (int i = 0; i < list.size(); i++) {
                if (i % 2 == 0) {
                    line = new LinearLayout(context);
                    line.setOrientation(LinearLayout.HORIZONTAL);
                    grid.addView(line, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
                }
                final String u = list.get(i);
                final int color = PILL_COLORS[i % PILL_COLORS.length];
                line.addView(usernameCard(context, u, color, u.equals(active), () -> showUsernamePreview(fragment, u, color, () -> {
                    MeridaSettings.visualUsername.set(u);
                    rebuild[0].run();
                    refresh(fragment);
                }, () -> {
                    final List<String> l = usernames();
                    l.remove(u);
                    saveUsernames(l);
                    if (u.equals(MeridaSettings.visualUsername.get())) {
                        MeridaSettings.visualUsername.set(l.isEmpty() ? null : l.get(0));
                    }
                    rebuild[0].run();
                    refresh(fragment);
                }), () -> MeridaVisualActivity.confirm(fragment, "@" + u, s(R.string.MeridaVDeleteQ), s(R.string.Delete), () -> {
                    final List<String> l = usernames();
                    l.remove(u);
                    saveUsernames(l);
                    if (u.equals(MeridaSettings.visualUsername.get())) {
                        MeridaSettings.visualUsername.set(l.isEmpty() ? null : l.get(0));
                    }
                    rebuild[0].run();
                    refresh(fragment);
                })), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, i % 2 == 0 ? 0 : 6, 0, i % 2 == 0 ? 6 : 0, 0));
            }
            if (line != null && list.size() % 2 == 1) {
                line.addView(new View(context), LayoutHelper.createLinear(0, 1, 1f, 6, 0, 0, 0));
            }
        };
        rebuild[0].run();
        sh.show(fragment);
    }

    private static View usernameCard(Context context, String username, int color, boolean active, Runnable click, Runnable longClick) {
        return usernameCard(context, username, color, active, click, longClick, 1f);
    }

    /** k scales the whole card (the big preview uses ~1.7). */
    private static View usernameCard(Context context, String username, int color, boolean active, Runnable click, Runnable longClick, float k) {
        final LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        final GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF2A3644, 0xFF1A1F26});
        bg.setCornerRadius(AndroidUtilities.dp(22 * k));
        if (active) {
            bg.setStroke(AndroidUtilities.dp(2), MeridaVisualUi.BLUE);
        }
        card.setBackground(bg);
        card.setPadding(AndroidUtilities.dp(8 * k), AndroidUtilities.dp(20 * k), AndroidUtilities.dp(8 * k), AndroidUtilities.dp(14 * k));
        final FrameLayout logoFrame = new FrameLayout(context);
        logoFrame.setBackground(MeridaVisualUi.round(0xFFFFFFFF, 22 * k));
        final FrameLayout circle = new FrameLayout(context);
        final GradientDrawable blue = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF37AEE2, 0xFF1E96C8});
        blue.setShape(GradientDrawable.OVAL);
        circle.setBackground(blue);
        final ImageView plane = new ImageView(context);
        plane.setImageResource(R.drawable.msg_send);
        plane.setScaleType(ImageView.ScaleType.FIT_CENTER);
        plane.setColorFilter(new android.graphics.PorterDuffColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN));
        circle.addView(plane, LayoutHelper.createFrame((int) (28 * k), (int) (28 * k), Gravity.CENTER, 0, 0, 2 * k, 0));
        logoFrame.addView(circle, LayoutHelper.createFrame((int) (52 * k), (int) (52 * k), Gravity.CENTER));
        card.addView(logoFrame, LayoutHelper.createLinear((int) (72 * k), (int) (72 * k)));
        final TextView pill = label(context, "@ " + username, 0xFFFFFFFF, 20 * k, true);
        pill.setSingleLine(true);
        pill.setEllipsize(TextUtils.TruncateAt.END);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(AndroidUtilities.dp(12 * k), AndroidUtilities.dp(4 * k), AndroidUtilities.dp(12 * k), AndroidUtilities.dp(4 * k));
        pill.setBackground(MeridaVisualUi.round(color, 12 * k));
        card.addView(pill, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, (int) (16 * k), 0, 0));
        final TextView link = label(context, username.toLowerCase() + ".t.me", 0xFF9AA4AE, 13 * k, false);
        card.addView(link, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, (int) (6 * k), 0, 0));
        card.setOnClickListener(v -> click.run());
        card.setOnLongClickListener(v -> {
            longClick.run();
            return true;
        });
        return card;
    }

    // ---- username preview (Chimera: a big card with «Put in profile / Change price / Delete») -----------

    static void showUsernamePreview(BaseFragment fragment, String username, int color, Runnable wear, Runnable delete) {
        final Context context = fragment.getParentActivity();
        if (context == null) return;
        final android.app.Dialog dialog = new android.app.Dialog(context, R.style.TransparentDialog);
        final FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0xB3000000);
        root.setOnClickListener(v -> dialog.dismiss());
        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setClickable(true);
        final View card = usernameCard(context, username, color, false, () -> { }, () -> { }, 1.75f);
        column.addView(card, LayoutHelper.createLinear(310, 310));
        final TextView name = label(context, "@" + username, 0xFFFFFFFF, 15, true);
        name.setGravity(Gravity.CENTER);
        column.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        final long[] meta = usernameMeta(username);
        final TextView info = label(context, priceLine(fragment, meta[0]) + "\n" + LocaleController.formatString(R.string.MeridaVBought, dateText(meta[1])), 0xFFFFFFFF, 12, false);
        info.setGravity(Gravity.CENTER);
        column.addView(info, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 14));
        final TextView put = MeridaVisualUi.bigButton(context, s(R.string.MeridaVPutInProfile));
        put.setOnClickListener(v -> {
            dialog.dismiss();
            wear.run();
        });
        column.addView(put, LayoutHelper.createLinear(310, 48));
        final TextView price = label(context, s(R.string.MeridaVChangePrice), 0xFFFFFFFF, 12.5f, true);
        price.setGravity(Gravity.CENTER);
        price.setOnClickListener(v -> {
            dialog.dismiss();
            showCostDate(fragment, "@" + username, false, username, (int) meta[0], meta[1], (tenths, time) -> setUsernameMeta(username, tenths, time));
        });
        column.addView(price, LayoutHelper.createLinear(310, 48, 0, 8, 0, 0));
        final TextView del = label(context, s(R.string.Delete), 0xFFFFFFFF, 12.5f, true);
        del.setGravity(Gravity.CENTER);
        del.setOnClickListener(v -> {
            dialog.dismiss();
            delete.run();
        });
        column.addView(del, LayoutHelper.createLinear(310, 48));
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        }
        column.setScaleX(0.9f);
        column.setScaleY(0.9f);
        column.setAlpha(0f);
        column.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220).setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT_BACK).start();
        dialog.show();
    }

    /** «TON 10.8 · ≈ $32.4» */
    static String priceLine(BaseFragment fragment, long tenths) {
        final double ton = tenths / 10.0;
        final StringBuilder sb = new StringBuilder("TON ").append(tenths % 10 == 0 ? String.valueOf(tenths / 10) : String.valueOf(ton));
        final String money = money(fragment, ton);
        if (money != null) sb.append(" · ≈ ").append(money);
        return sb.toString();
    }

    // ---- currencies (for «≈ $ / € / ₽»): the TON rate from Telegram, USD→EUR/RUB from a public rates service --

    private static final String[] CURRENCIES = {"USD", "EUR", "RUB"};
    private static final String[] SIGNS = {"$", "€", "₽"};

    static int currency() {
        return Math.max(0, Math.min(2, meta().getInt("currency", 0)));
    }

    static double usdTo(int currency) {
        if (currency == 0) return 1;
        final float r = meta().getFloat("rate_" + CURRENCIES[currency], 0);
        if (System.currentTimeMillis() - meta().getLong("rates_time", 0) > 12L * 3600 * 1000) {
            loadRates();
        }
        return r;
    }

    private static boolean ratesLoading;

    private static void loadRates() {
        if (ratesLoading) return;
        ratesLoading = true;
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            try {
                final java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://open.er-api.com/v6/latest/USD").openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                final java.io.InputStream in = c.getInputStream();
                final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                final byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close();
                final org.json.JSONObject rates = new org.json.JSONObject(out.toString("UTF-8")).getJSONObject("rates");
                meta().edit().putFloat("rate_EUR", (float) rates.getDouble("EUR")).putFloat("rate_RUB", (float) rates.getDouble("RUB"))
                        .putLong("rates_time", System.currentTimeMillis()).apply();
            } catch (Throwable ignore) {
            }
            ratesLoading = false;
        });
    }

    /** ≈ money for an amount of TON in the chosen currency, or null when the rate is unknown. */
    static String money(BaseFragment fragment, double ton) {
        final double rate = tonUsdRate(fragment);
        final int cur = currency();
        final double k = usdTo(cur);
        if (rate <= 0 || k <= 0) {
            return rate > 0 ? usd(ton * rate) : null;
        }
        final double v = ton * rate * k;
        return SIGNS[cur] + (v >= 100 ? String.format(java.util.Locale.US, "%.2f", v) : String.format(java.util.Locale.US, "%.2f", v));
    }

    interface CostDateResult {
        void done(int tonTenths, long time);
    }

    /**
     * «Price and date» like Chimera: the price in TON with ≈ in $ / € / ₽, the purchase date on three wheels,
     * a summary line, «Save» and «Like on Fragment» (opens the real Fragment page).
     */
    static void showCostDate(BaseFragment fragment, CharSequence subtitle, boolean phone, String name, int tonTenths, long time, CostDateResult result) {
        final Context context = fragment.getParentActivity();
        if (context == null) return;
        final Sheet sh = new Sheet(fragment, s(R.string.MeridaVCostDate), subtitle);
        sh.addClose();
        final java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(time > 0 ? time : System.currentTimeMillis());
        final int[] tenths = {Math.max(0, tonTenths)};

        final LinearLayout card = blackCard(context);
        final LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        final EditTextBoldCursor amount = new EditTextBoldCursor(context);
        amount.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        amount.setTextColor(0xFFFFFFFF);
        amount.setBackground(null);
        amount.setPadding(AndroidUtilities.dp(18), 0, 0, 0);
        amount.setCursorColor(0xFFFFFFFF);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setSingleLine(true);
        amount.setText(tenths[0] % 10 == 0 ? String.valueOf(tenths[0] / 10) : String.valueOf(tenths[0] / 10.0));
        top.addView(amount, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        top.addView(label(context, "TON", 0xFF7C7C7C, 14.5f, false));
        card.addView(top);
        final TextView approx = label(context, "", 0xFF7C7C7C, 12.5f, false);
        card.addView(approx, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
        sh.body.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        final LinearLayout chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] chip = new TextView[3];
        sh.body.addView(chips, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, 0, 12, 0, 0));
        sh.body.addView(label(context, s(R.string.MeridaVPurchaseDate), 0xFF7C7C7C, 12.5f, false), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));

        final LinearLayout wheels = new LinearLayout(context);
        wheels.setOrientation(LinearLayout.HORIZONTAL);
        final org.telegram.ui.Components.NumberPicker day = new org.telegram.ui.Components.NumberPicker(context, 18);
        final org.telegram.ui.Components.NumberPicker month = new org.telegram.ui.Components.NumberPicker(context, 18);
        final org.telegram.ui.Components.NumberPicker year = new org.telegram.ui.Components.NumberPicker(context, 18);
        // standalone month names («Ноябрь», not «ноября»)
        final String[] months = new String[12];
        final java.text.SimpleDateFormat monthFormat = new java.text.SimpleDateFormat("LLLL", LocaleController.getInstance().getCurrentLocale());
        final java.util.Calendar mc = java.util.Calendar.getInstance();
        for (int i = 0; i < 12; i++) {
            mc.set(2020, i, 1);
            months[i] = monthFormat.format(mc.getTime());
        }
        day.setMinValue(1);
        day.setMaxValue(31);
        day.setFormatter(v -> String.format(java.util.Locale.US, "%02d", v));
        month.setMinValue(0);
        month.setMaxValue(11);
        month.setFormatter(v -> {
            final String m = v < months.length ? months[v] : String.valueOf(v + 1);
            return m.isEmpty() ? m : Character.toUpperCase(m.charAt(0)) + m.substring(1);
        });
        year.setMinValue(2015);
        year.setMaxValue(cal.get(java.util.Calendar.YEAR) + 5);
        for (org.telegram.ui.Components.NumberPicker p : new org.telegram.ui.Components.NumberPicker[]{day, month, year}) {
            p.setTextColor(0xFFFFFFFF);
            p.setSelectorColor(MeridaVisualUi.BLUE);
        }
        day.setValue(cal.get(java.util.Calendar.DAY_OF_MONTH));
        month.setValue(cal.get(java.util.Calendar.MONTH));
        year.setValue(cal.get(java.util.Calendar.YEAR));
        wheels.addView(day, LayoutHelper.createLinear(0, 150, 0.6f));
        wheels.addView(month, LayoutHelper.createLinear(0, 150, 1.2f));
        wheels.addView(year, LayoutHelper.createLinear(0, 150, 0.8f));
        sh.body.addView(wheels, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 150, 0, 4, 0, 0));
        final TextView summary = label(context, "", 0xFF7C7C7C, 12, false);
        summary.setGravity(Gravity.CENTER);
        sh.body.addView(summary, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

        final Runnable update = () -> {
            try {
                tenths[0] = (int) Math.round(Double.parseDouble(amount.getText().toString().replace(',', '.')) * 10);
            } catch (Exception e) {
                tenths[0] = 0;
            }
            final double ton = tenths[0] / 10.0;
            final double rate = tonUsdRate(fragment);
            final int cur = currency();
            final double k = usdTo(cur);
            final String m = money(fragment, ton);
            approx.setText(m == null ? "" : "≈ " + m + (rate > 0 ? " · " + LocaleController.formatString(R.string.MeridaVTonRate,
                    String.format(java.util.Locale.US, "%.3f", rate * (k > 0 ? k : 1))).replace("$", k > 0 ? SIGNS[cur] : "$") : ""));
            for (int i = 0; i < 3; i++) {
                final boolean sel = i == cur;
                chip[i].setTextColor(sel ? 0xFFFFFFFF : 0xFFAAAAAA);
                chip[i].setBackground(MeridaVisualUi.round(sel ? MeridaVisualUi.BLUE : 0xFF000000, 20));
            }
            day.setMaxValue(new java.util.GregorianCalendar(year.getValue(), month.getValue(), 1).getActualMaximum(java.util.Calendar.DAY_OF_MONTH));
            cal.set(year.getValue(), month.getValue(), Math.min(day.getValue(), day.getMaxValue()));
            summary.setText(LocaleController.formatString(R.string.MeridaVBoughtFor, dateText(cal.getTimeInMillis()), priceLine(fragment, tenths[0]).replace(" · ≈ ", " (").concat(m == null ? "" : ")")));
        };
        for (int i = 0; i < 3; i++) {
            final int c = i;
            chip[i] = label(context, SIGNS[i] + " " + CURRENCIES[i], 0xFFFFFFFF, 12.5f, false);
            chip[i].setGravity(Gravity.CENTER);
            chip[i].setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
            chip[i].setOnClickListener(v -> {
                meta().edit().putInt("currency", c).apply();
                update.run();
            });
            chips.addView(chip[i], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, 0, 0, 8, 0));
        }
        amount.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence a, int b, int c, int d) {}
            @Override public void onTextChanged(CharSequence a, int b, int c, int d) {}
            @Override public void afterTextChanged(android.text.Editable e) { update.run(); }
        });
        final org.telegram.ui.Components.NumberPicker.OnValueChangeListener changed = (picker, o, n) -> update.run();
        day.setOnValueChangedListener(changed);
        month.setOnValueChangedListener(changed);
        year.setOnValueChangedListener(changed);
        update.run();
        loadRates();

        final TextView save = MeridaVisualUi.bigButton(context, s(R.string.Save));
        save.setOnClickListener(v -> {
            update.run();
            result.done(tenths[0], cal.getTimeInMillis());
            sh.sheet.dismiss();
            refresh(fragment);
        });
        sh.body.addView(save, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 12, 0, 0));
        final TextView fragmentButton = MeridaVisualUi.pillButton(context, s(R.string.MeridaVLikeFragment), false);
        fragmentButton.setTextColor(0xFFFFFFFF);
        fragmentButton.setTypeface(AndroidUtilities.bold());
        fragmentButton.setBackground(MeridaVisualUi.round(0xFF181818, 30));
        fragmentButton.setOnClickListener(v -> Browser.openUrl(context, phone ? "https://fragment.com/numbers" : "https://fragment.com/username/" + name));
        sh.body.addView(fragmentButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 8, 0, 0));
        sh.show(fragment);
    }

    // ---- collectible username / number window (like Telegram's Fragment window) -----------------------

    private static android.content.SharedPreferences meta() {
        return org.telegram.messenger.ApplicationLoader.applicationContext.getSharedPreferences("merida_visual_meta", Context.MODE_PRIVATE);
    }

    /** Price (tenths of TON) and time «bought» of a visual username; shown in its Fragment-like window. */
    static void setUsernameMeta(String username, int tonTenths, long time) {
        meta().edit().putString("u_" + username.toLowerCase(), tonTenths + "|" + time).apply();
    }

    static long[] usernameMeta(String username) {
        final String v = meta().getString("u_" + username.toLowerCase(), null);
        if (v != null) {
            try {
                final String[] p = v.split("\\|");
                return new long[]{Long.parseLong(p[0]), Long.parseLong(p[1])};
            } catch (Exception ignore) {
            }
        }
        return new long[]{108, System.currentTimeMillis()};
    }

    /**
     * The window shown when you tap your visual username or +888 number in your profile, laid out like Telegram's
     * «collectible username / number» window, with the orange VISUAL mark and your name watermarked.
     */
    /**
     * «Verification by an organization» for your profile (dialogId = your id) or a group / channel: none, Major
     * or Hold. Only on this phone, with the VISUAL mark; the icon and name come from the organization's bot.
     */
    static void askVerification(BaseFragment fragment, long dialogId, Runnable done) {
        final Context context = fragment.getParentActivity();
        if (context == null) return;
        final int account = fragment.getCurrentAccount();
        final String current = org.telegram.messenger.merida.MeridaVisualVerify.get(dialogId);
        final String[] orgs = org.telegram.messenger.merida.MeridaVisualVerify.ORGS;
        final CharSequence[] items = new CharSequence[orgs.length + 1];
        items[0] = (current == null ? "✓ " : "") + s(R.string.MeridaVVerifyNone);
        for (int i = 0; i < orgs.length; i++) {
            final String company = org.telegram.messenger.merida.MeridaVisualVerify.company(orgs[i]);
            final String label = (company != null ? company : Character.toUpperCase(orgs[i].charAt(0)) + orgs[i].substring(1)) + "  (@" + orgs[i] + ")";
            items[i + 1] = (orgs[i].equals(current) ? "✓ " : "") + label;
        }
        final org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
        b.setTitle(s(R.string.MeridaVVerifyTitle));
        b.setItems(items, (d, which) -> {
            org.telegram.messenger.merida.MeridaVisualVerify.set(account, dialogId, which == 0 ? null : orgs[which - 1]);
            if (done != null) {
                done.run();
                // the organization's icon and name may still be loading from its bot
                AndroidUtilities.runOnUIThread(done, 1500);
            }
        });
        b.setNegativeButton(s(R.string.Cancel), null);
        b.show();
    }

    static void showCollectible(BaseFragment fragment, boolean phone, String name) {
        final Context context = fragment.getParentActivity();
        if (context == null || name == null) {
            return;
        }
        final long tenths;
        final long time;
        if (phone) {
            final Num n = find(numbers(), name);
            tenths = n != null ? n.tonTenths : 0;
            time = n != null && n.time > 0 ? n.time : System.currentTimeMillis();
        } else {
            final long[] m = usernameMeta(name);
            tenths = m[0];
            time = m[1];
        }
        final BottomSheet sheet = new BottomSheet(context, false);
        sheet.setBackgroundColor(MeridaVisualUi.SHEET_BG);
        sheet.fixNavigationBar(MeridaVisualUi.SHEET_BG);
        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), AndroidUtilities.dp(8));

        final FrameLayout circle = new FrameLayout(context);
        circle.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(80), Theme.getColor(Theme.key_featuredStickers_addButton)));
        layout.addView(circle, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 16));
        final org.telegram.ui.Components.RLottieImageView icon = new org.telegram.ui.Components.RLottieImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        final int sz = phone ? 78 : 70;
        icon.setAnimation(phone ? R.raw.fragment : R.raw.fragment_username, sz, sz);
        icon.playAnimation();
        icon.setColorFilter(new android.graphics.PorterDuffColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN));
        if (phone) {
            icon.setTranslationY(AndroidUtilities.dp(2));
        } else {
            icon.setScaleX(0.86f);
            icon.setScaleY(0.86f);
        }
        circle.addView(icon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        final String shown = phone ? MeridaVisual.formatNumber888(name) : "@" + name;
        // like Telegram's FragmentUsernameBottomSheet: the username / number in the title is a blue link that copies it
        final Runnable copyLink = () -> {
            AndroidUtilities.addToClipboard(phone ? shown : MessagesController.getInstance(fragment.getCurrentAccount()).linkPrefix + "/" + name);
            if (phone) {
                org.telegram.ui.Components.BulletinFactory.of(sheet.getContainer(), null).createCopyBulletin(s(R.string.PhoneCopied)).show();
            } else {
                org.telegram.ui.Components.BulletinFactory.of(sheet.getContainer(), null).createCopyLinkBulletin().show();
            }
        };
        final android.text.SpannableStringBuilder title = new android.text.SpannableStringBuilder(AndroidUtilities.replaceSingleTag(
                LocaleController.formatString(phone ? R.string.FragmentPhoneTitle : R.string.FragmentUsernameTitle, shown), copyLink));
        title.append(' ').append(MeridaVisual.mark());
        // sizes exactly as Telegram's FragmentUsernameBottomSheet: title 16 sp bold, owner chip 13 sp,
        // description 14 sp, buttons 48 dp
        final org.telegram.ui.Components.LinkSpanDrawable.LinksTextView header = new org.telegram.ui.Components.LinkSpanDrawable.LinksTextView(context);
        header.setText(title);
        header.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        header.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2));
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        header.setTypeface(AndroidUtilities.bold());
        header.setGravity(Gravity.CENTER);
        layout.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 42, 0, 42, 0));

        final FrameLayout chip = new FrameLayout(context);
        chip.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(28), AndroidUtilities.dp(28), Theme.getColor(Theme.key_groupcreate_spanBackground)));
        final org.telegram.tgnet.TLRPC.User me = org.telegram.messenger.UserConfig.getInstance(fragment.getCurrentAccount()).getCurrentUser();
        final org.telegram.ui.Components.BackupImageView avatar = new org.telegram.ui.Components.BackupImageView(context);
        avatar.setRoundRadius(AndroidUtilities.dp(28));
        if (me != null) {
            final org.telegram.ui.Components.AvatarDrawable ad = new org.telegram.ui.Components.AvatarDrawable();
            ad.setInfo(fragment.getCurrentAccount(), me);
            avatar.setForUserOrChat(me, ad);
        }
        chip.addView(avatar, LayoutHelper.createFrame(28, 28, Gravity.LEFT | Gravity.TOP));
        final String myName = me != null ? org.telegram.messenger.UserObject.getUserName(me) : "";
        final TextView chipText = label(context, MeridaVisual.watermark(myName.isEmpty() ? "—" : myName), Theme.getColor(Theme.key_dialogTextBlack), 13, false);
        chipText.setSingleLine(true);
        chip.addView(chipText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 37, 0, 10, 0));
        layout.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_HORIZONTAL, 42, 10, 42, 18));

        final double ton = tenths / 10.0;
        final String crypto = "TON " + (tenths % 10 == 0 ? String.valueOf(tenths / 10) : String.valueOf(ton));
        final double rate = tonUsdRate(fragment);
        final String money = rate > 0 && tenths > 0 ? "(" + usd(ton * rate) + ")" : "";
        final String message = LocaleController.formatString(phone ? R.string.FragmentPhoneMessage : R.string.FragmentUsernameMessage,
                LocaleController.formatShortDateTime(time / 1000), crypto, money);
        final android.text.SpannableString tonIcon = new android.text.SpannableString("TON");
        final org.telegram.ui.Components.ColoredImageSpan span = new org.telegram.ui.Components.ColoredImageSpan(R.drawable.mini_gram_16);
        span.recolorDrawable = false;
        span.setWidth(AndroidUtilities.dp(13));
        tonIcon.setSpan(span, 0, tonIcon.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        final CharSequence messageText = AndroidUtilities.replaceCharSequence("TON", AndroidUtilities.replaceTags(message), tonIcon);
        final TextView description = label(context, messageText, Theme.getColor(Theme.key_dialogTextBlack), 14, false);
        description.setGravity(Gravity.CENTER);
        layout.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 8));
        final TextView note = label(context, s(R.string.MeridaVCollectibleNote), 0xFF7C7C7C, 12, false);
        note.setGravity(Gravity.CENTER);
    //     layout.addView(note, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 19));

        final org.telegram.ui.Stories.recorder.ButtonWithCounterView more = new org.telegram.ui.Stories.recorder.ButtonWithCounterView(context, null).setRound();
        more.setText(s(R.string.FragmentUsernameOpen), false);
        more.setOnClickListener(v -> Browser.openUrl(context, phone ? "https://fragment.com/numbers" : "https://fragment.com/username/" + name));
        layout.addView(more, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 6, 0, 6, 0));
        final org.telegram.ui.Stories.recorder.ButtonWithCounterView copy = new org.telegram.ui.Stories.recorder.ButtonWithCounterView(context, null).setRound().setNeutral();
        copy.setText(s(phone ? R.string.FragmentPhoneCopy : R.string.FragmentUsernameCopy), false);
        copy.setOnClickListener(v -> {
            AndroidUtilities.addToClipboard(phone ? shown : "https://t.me/" + name);
            sheet.dismiss();
        });
        layout.addView(copy, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 6, 6, 6, 0));
        sheet.setCustomView(layout);
        sheet.show();
    }

    // ---- +888 numbers --------------------------------------------------------------------------------

    /** One saved number: digits, price in tenths of TON, time bought (ms). */
    static final class Num {
        String digits;
        int tonTenths;
        long time;

        String encode() {
            return digits + "|" + tonTenths + "|" + time;
        }

        static Num decode(String line) {
            final String[] p = line.split("\\|");
            if (p.length == 0 || !MeridaVisual.isAnonymousNumber(p[0])) {
                return null;
            }
            final Num n = new Num();
            n.digits = p[0];
            try {
                n.tonTenths = p.length > 1 ? Integer.parseInt(p[1]) : 0;
                n.time = p.length > 2 ? Long.parseLong(p[2]) : 0;
            } catch (Exception ignore) {
            }
            return n;
        }
    }

    static List<Num> numbers() {
        final ArrayList<Num> list = new ArrayList<>();
        final String all = MeridaSettings.visualNumbers.get();
        if (all != null) {
            for (String line : all.split("\n")) {
                final Num n = Num.decode(line);
                if (n != null) list.add(n);
            }
        }
        final String active = MeridaSettings.visualNumber.get();
        if (active != null && MeridaVisual.isAnonymousNumber(active) && find(list, active) == null) {
            final Num n = new Num();
            n.digits = active;
            n.time = System.currentTimeMillis();
            list.add(0, n);
        }
        return list;
    }

    static Num find(List<Num> list, String digits) {
        for (Num n : list) {
            if (n.digits.equals(digits)) return n;
        }
        return null;
    }

    static void saveNumbers(List<Num> list) {
        final StringBuilder sb = new StringBuilder();
        for (Num n : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(n.encode());
        }
        MeridaSettings.visualNumbers.set(sb.toString());
    }

    static String numberDetails(BaseFragment fragment, Num n) {
        final StringBuilder sb = new StringBuilder();
        if (n.tonTenths > 0) {
            final double ton = n.tonTenths / 10.0;
            sb.append("TON ").append(n.tonTenths % 10 == 0 ? String.valueOf(n.tonTenths / 10) : String.valueOf(ton));
            final double rate = tonUsdRate(fragment);
            if (rate > 0) sb.append(" · ≈ ").append(usd(ton * rate));
        }
        if (n.time > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(LocaleController.formatString(R.string.MeridaVBought, dateText(n.time)));
        }
        return sb.toString();
    }

    static void showNumbers(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final Context context = fragment.getParentActivity();
        final Sheet sh = new Sheet(fragment, s(R.string.MeridaVMyNumber), "");
        final Runnable[] rebuild = new Runnable[1];
        sh.addButton(R.drawable.msg_add, MeridaVisualUi.BLUE, v -> editNumber(fragment, null, () -> rebuild[0].run()));
        sh.addClose();
        final LinearLayout current = new LinearLayout(context);
        current.setOrientation(LinearLayout.VERTICAL);
        sh.body.addView(current);
        sh.body.addView(showChip(context, MeridaSettings.visualNumberShow.get(), () -> {
            MeridaSettings.visualNumberShow.set(!MeridaSettings.visualNumberShow.get());
            rebuild[0].run();
            refresh(fragment);
        }), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 38, 0, 16, 0, 0));
        sh.body.addView(label(context, s(R.string.MeridaVSavedNumbers), 0xFF7C7C7C, 13.5f, false), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 10));
        final LinearLayout saved = new LinearLayout(context);
        saved.setOrientation(LinearLayout.VERTICAL);
        sh.body.addView(saved);
        final TextView fragmentButton = MeridaVisualUi.pillButton(context, s(R.string.MeridaVLikeFragment), false);
        fragmentButton.setTextColor(0xFFFFFFFF);
        fragmentButton.setTypeface(AndroidUtilities.bold());
        fragmentButton.setBackground(MeridaVisualUi.round(0xFF181818, 30));
        fragmentButton.setOnClickListener(v -> Browser.openUrl(context, "https://fragment.com/numbers"));
        sh.body.addView(fragmentButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 0, 16, 0, 0));

        rebuild[0] = () -> {
            final List<Num> list = numbers();
            final String active = MeridaSettings.visualNumber.get();
            sh.setSubtitle((MeridaSettings.visualNumberShow.get() && active != null ? s(R.string.MeridaVInProfile) : s(R.string.MeridaVHiddenWord)) + " · "
                    + LocaleController.formatString(R.string.MeridaVSavedCount, list.size()));
            current.removeAllViews();
            final Num cur = active == null ? null : find(list, active);
            final LinearLayout card = blackCard(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            final ImageView phone = new ImageView(context);
            phone.setImageResource(R.drawable.msg_calls);
            phone.setColorFilter(new android.graphics.PorterDuffColorFilter(MeridaVisualUi.BLUE, android.graphics.PorterDuff.Mode.SRC_IN));
            card.addView(phone, LayoutHelper.createLinear(32, 32, 0, 0, 16, 0));
            final LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(label(context, cur == null ? s(R.string.MeridaVNoNumber) : MeridaVisual.formatNumber888(cur.digits), 0xFFFFFFFF, 20, true));
            if (cur == null) {
                texts.addView(label(context, s(R.string.MeridaVAddNumberHint), 0xFF7C7C7C, 12.5f, false));
            }
            if (cur != null) {
                final String d = numberDetails(fragment, cur);
                if (!d.isEmpty()) {
                    final TextView details = label(context, d.replace(" · " + LocaleController.formatString(R.string.MeridaVBought, ""), "\n" + LocaleController.formatString(R.string.MeridaVBought, "")), 0xFF7C7C7C, 12.5f, false);
                    texts.addView(details, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
                }
            }
            card.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            current.addView(card);

            saved.removeAllViews();
            if (list.isEmpty()) {
                saved.addView(label(context, s(R.string.MeridaVNoNumbersYet), 0xFF7C7C7C, 12.5f, false), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
            }
            for (Num n : list) {
                final LinearLayout item = blackCard(context);
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setGravity(Gravity.CENTER_VERTICAL);
                final LinearLayout t = new LinearLayout(context);
                t.setOrientation(LinearLayout.VERTICAL);
                t.addView(label(context, MeridaVisual.formatNumber888(n.digits), 0xFFFFFFFF, 16, false));
                final boolean isActive = n.digits.equals(active);
                final String d = (isActive && MeridaSettings.visualNumberShow.get() ? s(R.string.MeridaVInProfile) : "")
                        + (isActive && MeridaSettings.visualNumberShow.get() && !numberDetails(fragment, n).isEmpty() ? " · " : "") + numberDetails(fragment, n);
                if (!d.isEmpty()) {
                    t.addView(label(context, d, isActive ? MeridaVisualUi.BLUE : 0xFF7C7C7C, 14, false), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
                }
                item.addView(t, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
                final ImageView edit = MeridaVisualUi.iconButton(context, R.drawable.merida_v_pencil, MeridaVisualUi.BLUE);
                edit.setOnClickListener(v -> editNumber(fragment, n, () -> rebuild[0].run()));
                item.addView(edit, LayoutHelper.createLinear(44, 44));
                final ImageView del = MeridaVisualUi.iconButton(context, R.drawable.merida_v_trash, 0xFFE5534B);
                del.setOnClickListener(v -> MeridaVisualActivity.confirm(fragment, MeridaVisual.formatNumber888(n.digits), s(R.string.MeridaVDeleteQ), s(R.string.Delete), () -> {
                    final List<Num> l = numbers();
                    final Num x = find(l, n.digits);
                    if (x != null) l.remove(x);
                    saveNumbers(l);
                    if (n.digits.equals(MeridaSettings.visualNumber.get())) {
                        MeridaSettings.visualNumber.set(l.isEmpty() ? null : l.get(0).digits);
                    }
                    rebuild[0].run();
                    refresh(fragment);
                }));
                item.addView(del, LayoutHelper.createLinear(44, 44));
                item.setOnClickListener(v -> {
                    MeridaSettings.visualNumber.set(n.digits);
                    rebuild[0].run();
                    refresh(fragment);
                });
                saved.addView(item, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
            }
        };
        rebuild[0].run();
        sh.show(fragment);
    }

    /** Add (n == null): the number, then «Price and date». Edit: «Price and date» of that number. */
    private static void editNumber(BaseFragment fragment, Num n, Runnable done) {
        if (n != null) {
            showCostDate(fragment, MeridaVisual.formatNumber888(n.digits), true, n.digits, n.tonTenths, n.time, (tenths, time) -> {
                final List<Num> list = numbers();
                final Num target = find(list, n.digits);
                if (target != null) {
                    target.tonTenths = tenths;
                    target.time = time;
                    saveNumbers(list);
                }
                done.run();
            });
            return;
        }
        askText(fragment, s(R.string.MeridaVNumberExample), "+888 ", InputType.TYPE_CLASS_PHONE, text -> {
            final String digits = text.replaceAll("[^0-9]", "");
            if (!MeridaVisual.isAnonymousNumber(digits)) {
                toast(fragment, s(R.string.MeridaVisualNumberOnly888));
                return;
            }
            final List<Num> list = numbers();
            Num target = find(list, digits);
            if (target == null) {
                target = new Num();
                target.digits = digits;
                target.tonTenths = 108;
                target.time = System.currentTimeMillis();
                list.add(0, target);
            }
            saveNumbers(list);
            MeridaSettings.visualNumber.set(digits);
            done.run();
            refresh(fragment);
            final Num added = target;
            showCostDate(fragment, MeridaVisual.formatNumber888(digits), true, digits, added.tonTenths, added.time, (tenths, time) -> {
                final List<Num> l = numbers();
                final Num t = find(l, digits);
                if (t != null) {
                    t.tonTenths = tenths;
                    t.time = time;
                    saveNumbers(l);
                }
                done.run();
            });
        });
    }

    // ---- rating --------------------------------------------------------------------------------------

    static void showRating(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final Context context = fragment.getParentActivity();
        final int[] level = {Math.max(1, Math.min(MeridaVisual.MAX_LEVEL, MeridaSettings.visualRating.get() <= 0 ? 1 : MeridaSettings.visualRating.get()))};
        final long[] points = {MeridaVisual.ratingPoints()};
        final boolean[] show = {MeridaSettings.visualRatingShow.get()};
        final Sheet sh = new Sheet(fragment, s(R.string.MeridaVRatingTitle), "");
        sh.addClose();

        final LinearLayout card = blackCard(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        final BadgeLevelDrawable badge = new BadgeLevelDrawable(context);
        badge.setOuterColor(0xFFFFFFFF);
        badge.setInnerColor(0xFF000000);
        badge.setTextColor(0xFFFFFFFF);
        final ImageView badgeView = new ImageView(context);
        badgeView.setImageDrawable(badge);
        card.addView(badgeView, LayoutHelper.createLinear(60, 60, 0, 0, 18, 0));
        final LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        final TextView levelTitle = label(context, "", 0xFFFFFFFF, 18.5f, true);
        final TextView levelSub = label(context, "", 0xFF7C7C7C, 13.5f, false);
        texts.addView(levelTitle);
        texts.addView(levelSub);
        card.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        sh.body.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        sh.body.addView(label(context, s(R.string.MeridaVRatingLooks), 0xFF7C7C7C, 13.5f, false), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 18, 0, 10));
        final LinearLayout shapes = new LinearLayout(context);
        shapes.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 1; i <= 7; i++) {
            final BadgeLevelDrawable d = new BadgeLevelDrawable(context);
            d.setOuterColor(0xFF7C7C7C);
            d.setInnerColor(MeridaVisualUi.SHEET_BG);
            d.setTextColor(0xFF7C7C7C);
            d.setBadgeLevel(i, false);
            final ImageView iv = new ImageView(context);
            iv.setImageDrawable(d);
            shapes.addView(iv, LayoutHelper.createLinear(0, 36, 1f));
        }
        sh.body.addView(shapes);

        final TextView levelValue = label(context, "", MeridaVisualUi.BLUE, 15, false);
        sh.body.addView(sliderHeader(context, s(R.string.MeridaVLevel), null, levelValue), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 22, 0, 0));
        final SeekBarView levelBar = new SeekBarView(context);
        levelBar.setReportChanges(true);
        sh.body.addView(levelBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38, 0, 6, 0, 0));

        final TextView pointsValue = label(context, "", MeridaVisualUi.BLUE, 15, false);
        final TextView range = label(context, "", 0xFF7C7C7C, 12.5f, false);
        sh.body.addView(sliderHeader(context, s(R.string.MeridaVPoints), range, pointsValue), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 18, 0, 0));
        final SeekBarView pointsBar = new SeekBarView(context);
        pointsBar.setReportChanges(true);
        sh.body.addView(pointsBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38, 0, 6, 0, 0));

        final Runnable update = () -> {
            final long from = MeridaVisual.levelStart(level[0]);
            final long to = level[0] >= MeridaVisual.MAX_LEVEL ? Math.round(from * 1.4) : MeridaVisual.levelStart(level[0] + 1) - 1;
            points[0] = Math.max(from, Math.min(to, points[0]));
            badge.setBadgeLevel(level[0], true);
            levelTitle.setText(LocaleController.formatString(R.string.MeridaVLevelN, level[0]));
            levelSub.setText(level[0] >= MeridaVisual.MAX_LEVEL ? s(R.string.MeridaVMaxLevel)
                    : LocaleController.formatString(R.string.MeridaVToNext, level[0] + 1, MeridaVisual.shortNumber(to + 1 - points[0])));
            levelValue.setText(String.valueOf(level[0]));
            pointsValue.setText(MeridaVisual.shortNumber(points[0]));
            range.setText(LocaleController.formatString(R.string.MeridaVRange, MeridaVisual.shortNumber(from), MeridaVisual.shortNumber(to)));
            sh.setSubtitle(LocaleController.formatString(R.string.MeridaVRatingSub, level[0], MeridaVisual.shortNumber(points[0])));
        };
        levelBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                level[0] = Math.max(1, Math.min(MeridaVisual.MAX_LEVEL, 1 + Math.round(progress * (MeridaVisual.MAX_LEVEL - 1))));
                points[0] = MeridaVisual.levelStart(level[0]);
                update.run();
                pointsBar.setProgress(0);
            }
        });
        pointsBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                final long from = MeridaVisual.levelStart(level[0]);
                final long to = level[0] >= MeridaVisual.MAX_LEVEL ? Math.round(from * 1.4) : MeridaVisual.levelStart(level[0] + 1) - 1;
                points[0] = from + Math.round((to - from) * (double) progress);
                update.run();
            }
        });
        update.run();
        levelBar.setProgress((level[0] - 1) / (float) (MeridaVisual.MAX_LEVEL - 1));
        {
            final long from = MeridaVisual.levelStart(level[0]);
            final long to = level[0] >= MeridaVisual.MAX_LEVEL ? Math.round(from * 1.4) : MeridaVisual.levelStart(level[0] + 1) - 1;
            pointsBar.setProgress(to > from ? (points[0] - from) / (float) (to - from) : 0);
        }

        sh.body.addView(showChip(context, show[0], () -> show[0] = !show[0]), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 38, 0, 22, 0, 0));
        final TextView save = MeridaVisualUi.bigButton(context, s(R.string.Save));
        save.setOnClickListener(v -> {
            MeridaSettings.visualRating.set(level[0]);
            MeridaSettings.visualRatingPoints.set((int) Math.min(Integer.MAX_VALUE, points[0]));
            MeridaSettings.visualRatingShow.set(show[0]);
            sh.sheet.dismiss();
            refresh(fragment);
        });
        sh.body.addView(save, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 0, 16, 0, 0));
        sh.show(fragment);
    }

    private static View sliderHeader(Context context, String title, TextView sub, TextView value) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final LinearLayout left = new LinearLayout(context);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(label(context, title, 0xFFFFFFFF, 16, false));
        if (sub != null) {
            left.addView(sub);
        }
        row.addView(left, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        row.addView(value);
        return row;
    }
}
