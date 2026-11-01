package org.telegram.messenger.merida;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.utils.settings.MeridaSettings;

/**
 * MeridaGram «Visual»: a stars and GRAM balance and a Premium badge that are shown only on this phone and
 * always carry a «visual» mark, so a screenshot cannot pass them off as real. Nothing is sent to Telegram:
 * only the text on screen changes, never the real balance used for payments.
 * Conditions agreed with the owner are in CLAUDE.md («Визуал»); the mark cannot be turned off.
 */
public final class MeridaVisual {

    private static final int MARK_COLOR = 0xFFFF9500;

    private MeridaVisual() {
    }

    public static boolean on() {
        return MeridaSettings.visualEnabled.get();
    }

    public static boolean starsOn() {
        return on() && MeridaSettings.visualStars.get() > 0;
    }

    public static boolean gramsOn() {
        return on() && MeridaSettings.visualGrams.get() > 0;
    }

    public static boolean premiumOn() {
        return on() && MeridaSettings.visualPremium.get();
    }

    public static String formatNumber(long value) {
        final String s = String.format(java.util.Locale.US, "%,d", value);
        return s.replace(',', ' ');
    }

    /** The orange «VISUAL» pill that goes next to every visual number. */
    public static CharSequence mark() {
        final SpannableString s = new SpannableString(LocaleController.getString(R.string.MeridaVisualMark));
        s.setSpan(new MarkSpan(), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    /** «759 000 [VISUAL]» */
    public static CharSequence starsText() {
        final SpannableStringBuilder sb = new SpannableStringBuilder(formatNumber(MeridaSettings.visualStars.get()));
        sb.append(' ').append(mark());
        return sb;
    }

    public static CharSequence gramsText() {
        final SpannableStringBuilder sb = new SpannableStringBuilder(formatNumber(MeridaSettings.visualGrams.get()));
        sb.append(' ').append(mark());
        return sb;
    }

    /** Draws the pill text in a small rounded orange box; its size follows the surrounding text. */
    private static final class MarkSpan extends ReplacementSpan {
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        private void setup(Paint paint) {
            textPaint.setTextSize(paint.getTextSize() * 0.62f);
            textPaint.setTypeface(AndroidUtilities.bold());
            textPaint.setColor(0xFFFFFFFF);
            bg.setColor(MARK_COLOR);
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            setup(paint);
            final String t = text.subSequence(start, end).toString().toUpperCase();
            return (int) Math.ceil(textPaint.measureText(t) + paint.getTextSize() * 0.6f);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            setup(paint);
            final String t = text.subSequence(start, end).toString().toUpperCase();
            final float padH = paint.getTextSize() * 0.3f;
            final float w = textPaint.measureText(t) + padH * 2;
            final float h = textPaint.getTextSize() * 1.45f;
            final float centerY = (top + bottom) / 2f;
            rect.set(x, centerY - h / 2, x + w, centerY + h / 2);
            final int alpha = paint.getAlpha();
            bg.setAlpha(alpha);
            textPaint.setAlpha(alpha);
            canvas.drawRoundRect(rect, h / 2, h / 2, bg);
            final Paint.FontMetrics fm = textPaint.getFontMetrics();
            canvas.drawText(t, x + padH, centerY - (fm.ascent + fm.descent) / 2, textPaint);
        }
    }

    /** An icon (the Premium star) with the «VISUAL» pill drawn right after it. */
    public static Drawable withMark(Drawable icon) {
        return new MarkedDrawable(icon);
    }

    private static final class MarkedDrawable extends Drawable implements Drawable.Callback {
        private final Drawable icon;
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final String text;
        private int alpha = 255;

        MarkedDrawable(Drawable icon) {
            this.icon = icon;
            icon.setCallback(this);
            text = LocaleController.getString(R.string.MeridaVisualMark).toUpperCase();
            textPaint.setTextSize(AndroidUtilities.dp(9));
            textPaint.setTypeface(AndroidUtilities.bold());
            textPaint.setColor(0xFFFFFFFF);
            bg.setColor(MARK_COLOR);
        }

        private int iconSize() {
            return AndroidUtilities.dp(22);
        }

        private float pillWidth() {
            return textPaint.measureText(text) + AndroidUtilities.dp(10);
        }

        @Override
        public int getIntrinsicWidth() {
            return iconSize() + AndroidUtilities.dp(3) + (int) Math.ceil(pillWidth());
        }

        @Override
        public int getIntrinsicHeight() {
            return iconSize();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final Rect b = getBounds();
            final int s = iconSize();
            final int top = b.centerY() - s / 2;
            icon.setBounds(b.left, top, b.left + s, top + s);
            icon.setAlpha(alpha);
            icon.draw(canvas);
            final float h = AndroidUtilities.dp(15);
            final float x = b.left + s + AndroidUtilities.dp(3);
            rect.set(x, b.centerY() - h / 2, x + pillWidth(), b.centerY() + h / 2);
            bg.setAlpha(alpha);
            textPaint.setAlpha(alpha);
            canvas.drawRoundRect(rect, h / 2, h / 2, bg);
            final Paint.FontMetrics fm = textPaint.getFontMetrics();
            canvas.drawText(text, x + AndroidUtilities.dp(5), b.centerY() - (fm.ascent + fm.descent) / 2, textPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            invalidateSelf();
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            scheduleSelf(what, when);
        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            unscheduleSelf(what);
        }
    }
}
