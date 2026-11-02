package org.telegram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

/**
 * Small building blocks for the «Visual» screens, in the exteraGram / Chimera style: a blue section title,
 * a rounded card with rows (icon, title, grey subtitle, divider), switches and grey notes under the card.
 */
final class MeridaVisualUi {

    static final int BLUE = 0xFF3E9BFF;
    static final int SHEET_BG = 0xFF212121;
    static final int SHEET_CARD = 0xFF000000;

    private MeridaVisualUi() {
    }

    static boolean dark() {
        return Theme.isCurrentThemeDark();
    }

    static int pageBg() {
        return dark() ? 0xFF000000 : Theme.getColor(Theme.key_windowBackgroundGray);
    }

    static int cardBg() {
        return dark() ? 0xFF1C1C1E : Theme.getColor(Theme.key_windowBackgroundWhite);
    }

    static int text() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
    }

    static int gray() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
    }

    static int blueHeader() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);
    }

    static GradientDrawable round(int color, float radiusDp) {
        final GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(AndroidUtilities.dp(radiusDp));
        return d;
    }

    static TextView header(Context context, CharSequence title) {
        final TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTextColor(blueHeader());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setPadding(AndroidUtilities.dp(28), AndroidUtilities.dp(22), AndroidUtilities.dp(28), AndroidUtilities.dp(10));
        return tv;
    }

    static LinearLayout card(Context context) {
        final LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(cardBg(), 18));
        card.setClipToOutline(true);
        return card;
    }

    static LinearLayout.LayoutParams cardParams() {
        return LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 0, 14, 0);
    }

    static TextView note(Context context, CharSequence text) {
        final TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(gray());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tv.setPadding(AndroidUtilities.dp(28), AndroidUtilities.dp(10), AndroidUtilities.dp(28), AndroidUtilities.dp(4));
        return tv;
    }

    /** One row of a card. Returns the row; its subtitle TextView is tagged so it can be updated. */
    static Row row(Context context, int icon, CharSequence title, CharSequence subtitle, boolean divider, View.OnClickListener click) {
        return new Row(context, icon, title, subtitle, divider, false, false, click);
    }

    static Row switchRow(Context context, int icon, CharSequence title, CharSequence subtitle, boolean checked, boolean divider, View.OnClickListener click) {
        return new Row(context, icon, title, subtitle, divider, true, checked, click);
    }

    static final class Row extends FrameLayout {
        final TextView title;
        final TextView subtitle;
        final Switch toggle;
        private final boolean divider;
        private final int textLeft;
        private final android.graphics.Paint dividerPaint = new android.graphics.Paint();

        Row(Context context, int icon, CharSequence titleText, CharSequence subtitleText, boolean divider, boolean hasSwitch, boolean checked, View.OnClickListener click) {
            super(context);
            this.divider = divider;
            setWillNotDraw(false);
            dividerPaint.setColor(dark() ? 0xFF2C2C2E : Theme.getColor(Theme.key_divider));
            textLeft = icon != 0 ? 72 : 22;
            if (icon != 0) {
                final ImageView image = new ImageView(context);
                image.setImageResource(icon);
                image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                image.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
                addView(image, LayoutHelper.createFrame(26, 26, Gravity.LEFT | Gravity.CENTER_VERTICAL, 22, 0, 0, 0));
            }
            final LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            title = new TextView(context);
            title.setText(titleText);
            title.setTextColor(text());
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            texts.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            subtitle = new TextView(context);
            subtitle.setTextColor(gray());
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
            setSubtitle(subtitleText);
            addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, textLeft, 14, hasSwitch ? 76 : 20, 14));
            if (hasSwitch) {
                toggle = new Switch(context);
                toggle.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
                toggle.setChecked(checked, false);
                toggle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                addView(toggle, LayoutHelper.createFrame(37, 40, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 22, 0));
            } else {
                toggle = null;
            }
            setMinimumHeight(AndroidUtilities.dp(58));
            setBackground(Theme.getSelectorDrawable(false));
            if (click != null) {
                setOnClickListener(click);
            }
        }

        void setSubtitle(CharSequence text) {
            subtitle.setText(text);
            subtitle.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
        }

        void setChecked(boolean checked) {
            if (toggle != null) {
                toggle.setChecked(checked, true);
            }
        }

        boolean isChecked() {
            return toggle != null && toggle.isChecked();
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            if (divider) {
                canvas.drawRect(AndroidUtilities.dp(textLeft), getHeight() - 1, getWidth(), getHeight(), dividerPaint);
            }
        }
    }

    /** A rounded pill button, filled blue or tinted. */
    static TextView pillButton(Context context, CharSequence text, boolean filled) {
        final TextView tv = new TextView(context);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        tv.setTextColor(filled ? 0xFFFFFFFF : BLUE);
        tv.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        tv.setBackground(round(filled ? BLUE : 0x1F3E9BFF, 26));
        return tv;
    }

    /** A big full-width «Save» button. */
    static TextView bigButton(Context context, CharSequence text) {
        final TextView tv = pillButton(context, text, true);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        tv.setBackground(round(BLUE, 30));
        return tv;
    }

    /** Title bar of a bottom sheet: big title, optional blue subtitle and icon buttons on the right. */
    static LinearLayout sheetTitle(Context context, CharSequence title, CharSequence subtitle, TextView[] outTitle, TextView[] outSubtitle) {
        final LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        final LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        final TextView t = new TextView(context);
        t.setText(title);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        t.setTypeface(AndroidUtilities.bold());
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(t);
        final TextView s = new TextView(context);
        s.setTextColor(BLUE);
        s.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        s.setText(subtitle);
        s.setVisibility(TextUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE);
        texts.addView(s);
        bar.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        if (outTitle != null) outTitle[0] = t;
        if (outSubtitle != null) outSubtitle[0] = s;
        return bar;
    }

    static ImageView iconButton(Context context, int icon, int color) {
        final ImageView b = new ImageView(context);
        b.setImageResource(icon);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        b.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        return b;
    }

    static String plural(int n, String one, String few, String many) {
        final int a = Math.abs(n) % 100;
        final int b = a % 10;
        if (a > 10 && a < 20) return many;
        if (b == 1) return one;
        if (b >= 2 && b <= 4) return few;
        return many;
    }
}
