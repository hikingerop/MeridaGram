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

    public static boolean purchasesOn() {
        return on() && MeridaSettings.visualPurchases.get();
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

    // ---- stage 3: username, number, rating -------------------------------------------------------

    public static boolean usernameOn() {
        final String u = MeridaSettings.visualUsername.get();
        return on() && MeridaSettings.visualUsernameShow.get() && u != null && !u.isEmpty();
    }

    /** Saved visual usernames other than the one in the profile (shown as «also …»). */
    public static java.util.List<String> otherUsernames() {
        final java.util.ArrayList<String> list = new java.util.ArrayList<>();
        final String all = MeridaSettings.visualUsernames.get();
        final String active = MeridaSettings.visualUsername.get();
        if (all != null) {
            for (String u : all.split("\n")) {
                if (!u.isEmpty() && !u.equals(active) && !list.contains(u)) list.add(u);
            }
        }
        return list;
    }

    public static boolean numberOn() {
        final String n = MeridaSettings.visualNumber.get();
        return on() && MeridaSettings.visualNumberShow.get() && n != null && isAnonymousNumber(n);
    }

    public static boolean ratingOn() {
        return on() && MeridaSettings.visualRatingShow.get() && MeridaSettings.visualRating.get() > 0;
    }

    /** Only anonymous Fragment numbers: 888 and 8 more digits. Real phone numbers belong to real people. */
    public static boolean isAnonymousNumber(String digits) {
        return digits != null && digits.matches("888\\d{8}");
    }

    public static String formatNumber888(String digits) {
        return "+888 " + digits.substring(3, 7) + " " + digits.substring(7);
    }

    public static boolean isValidUsername(String username) {
        return username != null && username.matches("[A-Za-z][A-Za-z0-9_]{3,31}");
    }

    public static final int CHECK_OK = 0;
    public static final int CHECK_TAKEN = 1;
    public static final int CHECK_INVALID = 2;
    public static final int CHECK_ERROR = 3;

    public interface CheckResult {
        void done(int result);
    }

    /**
     * A visual username may not belong to another account, channel or bot: Telegram is asked who has it.
     * Free and unassigned collectible usernames are allowed; so is your own.
     */
    public static void checkUsername(int account, String username, CheckResult result) {
        if (!isValidUsername(username)) {
            result.done(CHECK_INVALID);
            return;
        }
        final org.telegram.tgnet.TLRPC.TL_contacts_resolveUsername req = new org.telegram.tgnet.TLRPC.TL_contacts_resolveUsername();
        req.username = username;
        org.telegram.tgnet.ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof org.telegram.tgnet.TLRPC.TL_contacts_resolvedPeer) {
                final org.telegram.tgnet.TLRPC.Peer peer = ((org.telegram.tgnet.TLRPC.TL_contacts_resolvedPeer) response).peer;
                final long self = org.telegram.messenger.UserConfig.getInstance(account).getClientUserId();
                result.done(peer instanceof org.telegram.tgnet.TLRPC.TL_peerUser && peer.user_id == self ? CHECK_OK : CHECK_TAKEN);
            } else if (error != null && "USERNAME_NOT_OCCUPIED".equals(error.text)) {
                result.done(CHECK_OK);
            } else if (error != null && "USERNAME_INVALID".equals(error.text)) {
                result.done(CHECK_INVALID);
            } else {
                result.done(CHECK_ERROR);
            }
        }));
    }

    public static final int MAX_LEVEL = 100;

    /** Points needed for a level (MeridaGram's own smooth scale: each level is about 15% more). */
    public static long levelStart(int level) {
        if (level <= 1) {
            return 0;
        }
        return Math.round(1000 * (Math.pow(1.15, level - 1) - 1));
    }

    /** The visual rating points, kept inside the chosen level's range. */
    public static long ratingPoints() {
        final int level = Math.max(1, Math.min(MAX_LEVEL, MeridaSettings.visualRating.get()));
        final long from = levelStart(level);
        final long to = level >= MAX_LEVEL ? Math.round(from * 1.4) : levelStart(level + 1) - 1;
        final long p = MeridaSettings.visualRatingPoints.get() & 0xFFFFFFFFL;
        return p < from ? from : Math.min(p, to);
    }

    /** «1171.64M», «585.2K», «950». */
    public static String shortNumber(long v) {
        if (v >= 1_000_000) {
            return trim(String.format(java.util.Locale.US, "%.2f", v / 1_000_000.0)) + "M";
        }
        if (v >= 1_000) {
            return trim(String.format(java.util.Locale.US, "%.1f", v / 1_000.0)) + "K";
        }
        return String.valueOf(v);
    }

    private static String trim(String s) {
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
    }

    public static org.telegram.tgnet.tl.TL_stars.Tl_starsRating visualRating() {
        final org.telegram.tgnet.tl.TL_stars.Tl_starsRating r = new org.telegram.tgnet.tl.TL_stars.Tl_starsRating();
        r.level = Math.max(1, Math.min(MAX_LEVEL, MeridaSettings.visualRating.get()));
        r.current_level_stars = levelStart(r.level);
        r.next_level_stars = levelStart(r.level + 1);
        r.stars = ratingPoints();
        if (r.level < MAX_LEVEL) {
            r.flags |= 1;
        }
        return r;
    }

    /**
     * The text with «VISUAL» written over it, repeated across its whole width, so a screenshot cannot be
     * cropped to show the text without the mark.
     */
    public static CharSequence watermark(CharSequence text) {
        final SpannableString s = new SpannableString(text);
        s.setSpan(new WatermarkSpan(), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private static final class WatermarkSpan extends ReplacementSpan {
        private final TextPaint markPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final String mark = LocaleController.getString(R.string.MeridaVisualMark).toUpperCase();

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            if (fm != null) {
                paint.getFontMetricsInt(fm);
            }
            return (int) Math.ceil(paint.measureText(text, start, end));
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            canvas.drawText(text, start, end, x, y, paint);
            final float width = paint.measureText(text, start, end);
            markPaint.setTextSize(paint.getTextSize() * 0.7f);
            markPaint.setTypeface(AndroidUtilities.bold());
            markPaint.setColor(MARK_COLOR);
            markPaint.setAlpha(225 * paint.getAlpha() / 255);
            final float step = markPaint.measureText(mark) + paint.getTextSize() * 0.5f;
            final Paint.FontMetrics fm = markPaint.getFontMetrics();
            final float cy = (top + bottom) / 2f;
            canvas.save();
            canvas.clipRect(x, top, x + width, bottom);
            canvas.rotate(-8, x + width / 2, cy);
            for (float mx = x - step / 3; mx < x + width; mx += step) {
                canvas.drawText(mark, mx, cy - (fm.ascent + fm.descent) / 2, markPaint);
            }
            canvas.restore();
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
