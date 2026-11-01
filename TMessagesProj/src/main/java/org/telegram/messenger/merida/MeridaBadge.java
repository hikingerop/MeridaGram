package org.telegram.messenger.merida;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.tgnet.TLRPC;

/**
 * MeridaGram's own badge for its official channels and chats: a violet rosette with a white paper plane
 * and twinkling sparkles around it. Shown only inside MeridaGram; tapping it explains what it means.
 * It is not Telegram's verification and looks different from it on purpose.
 */
public final class MeridaBadge extends Drawable {

    /** Public usernames of MeridaGram's official channels and chats (compared without case). */
    private static final String[] OFFICIAL_USERNAMES = {"MeridaGram"};

    public static boolean isOfficial(TLRPC.Chat chat) {
        if (chat == null) {
            return false;
        }
        final String username = ChatObject.getPublicUsername(chat);
        if (username == null) {
            return false;
        }
        for (String official : OFFICIAL_USERNAMES) {
            if (official.equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    private static final int VIOLET = 0xFF7B5CFF;
    private static final int SPARKLE = 0xFFB9A8FF;
    /** Sparkles around the badge: x, y (in parts of the size, from the centre), size, phase. */
    private static final float[][] SPARKLES = {
            {0.42f, -0.36f, 0.10f, 0.0f},
            {0.48f, 0.22f, 0.07f, 0.35f},
            {-0.44f, 0.34f, 0.08f, 0.6f},
            {-0.40f, -0.40f, 0.06f, 0.85f},
            {0.10f, -0.50f, 0.05f, 0.5f},
    };

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path rosette = new Path();
    private final Path plane = new Path();
    private final Path star = new Path();
    private final int size;
    private final long start = SystemClock.elapsedRealtime();

    public MeridaBadge(int sizeDp) {
        size = AndroidUtilities.dp(sizeDp);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        final float cx = bounds.exactCenterX();
        final float cy = bounds.exactCenterY();
        final float r = Math.min(bounds.width(), bounds.height()) * 0.34f;
        // a rosette with 8 soft bumps
        rosette.reset();
        final int steps = 96;
        for (int i = 0; i <= steps; i++) {
            final double a = Math.PI * 2 * i / steps;
            final double rr = r * (1 + 0.09 * Math.cos(8 * a));
            final float x = (float) (cx + rr * Math.cos(a));
            final float y = (float) (cy + rr * Math.sin(a));
            if (i == 0) {
                rosette.moveTo(x, y);
            } else {
                rosette.lineTo(x, y);
            }
        }
        rosette.close();
        // a paper plane pointing up and right, like the app icon
        final float p = r * 0.62f;
        plane.reset();
        plane.moveTo(cx - p, cy + p * 0.05f);
        plane.lineTo(cx + p * 0.85f, cy - p * 0.75f);
        plane.lineTo(cx + p * 0.15f, cy + p * 0.95f);
        plane.lineTo(cx - p * 0.05f, cy + p * 0.25f);
        plane.close();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect b = getBounds();
        final float cx = b.exactCenterX();
        final float cy = b.exactCenterY();
        final float s = Math.min(b.width(), b.height());
        final float t = ((SystemClock.elapsedRealtime() - start) % 2400) / 2400f;
        final int alpha = getAlpha();

        fill.setColor(VIOLET);
        fill.setAlpha(alpha);
        canvas.drawPath(rosette, fill);
        fill.setColor(0xFFFFFFFF);
        fill.setAlpha(alpha);
        canvas.drawPath(plane, fill);

        // sparkles fade in and out one after another
        fill.setColor(SPARKLE);
        for (float[] sp : SPARKLES) {
            final float phase = (t + sp[3]) % 1f;
            final float k = (float) Math.sin(Math.PI * phase);
            if (k <= 0.05f) {
                continue;
            }
            final float x = cx + sp[0] * s;
            final float y = cy + sp[1] * s;
            final float r = sp[2] * s * (0.6f + 0.4f * k);
            star.reset();
            star.moveTo(x, y - r);
            star.quadTo(x, y, x + r, y);
            star.quadTo(x, y, x, y + r);
            star.quadTo(x, y, x - r, y);
            star.quadTo(x, y, x, y - r);
            star.close();
            fill.setAlpha((int) (alpha * k));
            canvas.drawPath(star, fill);
        }
        invalidateSelf();
    }

    private int alphaValue = 255;

    @Override
    public void setAlpha(int alpha) {
        alphaValue = alpha;
    }

    @Override
    public int getAlpha() {
        return alphaValue;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }
}
