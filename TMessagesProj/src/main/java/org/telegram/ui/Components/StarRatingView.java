package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.Theme;

public class StarRatingView extends View {
    private final BadgeLevelDrawable drawable;
    private final Colors colors = new Colors();

    private final AnimatedFloat isVisibleAnimator = new AnimatedFloat(this::onUpdateVisibilityFactor, 380, CubicBezierInterpolator.EASE_OUT_QUINT);
    private boolean isVisible;

    private Delegate delegate;

    public StarRatingView(Context context) {
        super(context);

        drawable = new BadgeLevelDrawable(context);
        drawable.setCallback(this);
        isVisibleAnimator.set(isVisible = false);

        checkVisibility();
    }

    public void setResourcesProvider(Theme.ResourcesProvider resourcesProvider) {
        this.colors.resourcesProvider = resourcesProvider;
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || who == drawable;
    }

    public interface Delegate {
        void onUpdateState(float visibility);
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void set(TL_stars.Tl_starsRating starsRating) {
        isVisibleInternal = starsRating != null;
        checkVisibility();
        if (starsRating == null) {
            return;
        }

        drawable.setBadgeLevel(starsRating.level, true);
        setContentDescription(getString(R.string.AccDescrProfileRatingLevel) + " " + starsRating.level);
        invalidate();
    }

    private boolean isVisibleInternal;
    private boolean isVisibleExternal;

    public void setVisibility(boolean isVisible) {
        isVisibleExternal = isVisible;
        checkVisibility();
    }

    private void checkVisibility() {
        isVisible = isVisibleExternal && isVisibleInternal;
        isVisibleAnimator.set(isVisible);

        setEnabled(isVisible);
        setClickable(isVisible);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        final float visibility = isVisibleAnimator.set(isVisible);
        final int x = (getMeasuredWidth() - dp(24)) / 2;
        final int y = (getMeasuredHeight() - dp(24)) / 2;

        canvas.save();
        canvas.translate(x, y);
        canvas.scale(visibility, visibility, 0, dp(12));

        drawable.setBounds(0, 0, dp(24), dp(24));
        drawable.setOuterColor(colors.backgroundColor);
        drawable.setInnerColor(colors.fillingColor);
        drawable.setTextColor(colors.backgroundColor | 0xFF000000);
        drawable.draw(canvas);

        canvas.restore();

        if (false && meridaVisual && visibility > 0) {
            // MeridaGram «Visual»: a rating that is not real carries the mark under the badge
            meridaPaint.setTextSize(dp(6.5f));
            meridaPaint.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
            final String mark = org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.MeridaVisualMark).toUpperCase();
            final float w = meridaPaint.measureText(mark) + dp(4);
            final float h = dp(9);
            final float cx = getMeasuredWidth() / 2f;
            final float bottomY = getMeasuredHeight();
            meridaRect.set(cx - w / 2, bottomY - h, cx + w / 2, bottomY);
            meridaBg.setColor(0xFFFF9500);
            meridaBg.setAlpha((int) (255 * visibility));
            canvas.drawRoundRect(meridaRect, h / 2, h / 2, meridaBg);
            meridaPaint.setColor(0xFFFFFFFF);
            meridaPaint.setAlpha((int) (255 * visibility));
            final android.graphics.Paint.FontMetrics fm = meridaPaint.getFontMetrics();
            canvas.drawText(mark, cx - w / 2 + dp(2), meridaRect.centerY() - (fm.ascent + fm.descent) / 2, meridaPaint);
        }
    }

    /** MeridaGram «Visual»: this rating was set on this phone and is not real. */
    public boolean meridaVisual;
    private final android.text.TextPaint meridaPaint = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Paint meridaBg = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF meridaRect = new android.graphics.RectF();

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        drawable.debugUpdateStart();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        drawable.debugUpdateStop();
    }

    private void onUpdateVisibilityFactor() {
        invalidate();
        AndroidUtilities.runOnUIThread(() -> {
            if (delegate != null) {
                delegate.onUpdateState(getVisibilityFactor());
            }
        });
    }

    public float getVisibilityFactor() {
        return isVisibleAnimator.get();
    }


    /**/

    public void updateColors(MessagesController.PeerColor peerColor) {
        colors.update(peerColor);
        invalidate();
    }

    public void setParentExpanded(float parentExpanded) {
        colors.setParentExpanded(parentExpanded);
        invalidate();
    }

    private static class Colors {
        public MessagesController.PeerColor peerColor;
        private Theme.ResourcesProvider resourcesProvider;

        public int backgroundColor = 0xFF000000;
        public int fillingColor = 0xFFFFFFFF;
        private float parentExpanded;

        public void update(MessagesController.PeerColor peerColor) {
            this.peerColor = peerColor;
            if (peerColor == null) {
                reset();
                return;
            }

            final int color1 = peerColor.getBgColor1(Theme.isCurrentThemeDark());
            final int color2 = peerColor.getBgColor2(Theme.isCurrentThemeDark());
            backgroundColor = getTabsViewBackgroundColor(resourcesProvider, color2, color1);
            fillingColor = AndroidUtilities.computePerceivedBrightness(backgroundColor) > .721f ? Color.BLACK : Color.WHITE;

            backgroundColor = ColorUtils.blendARGB(backgroundColor, 0x24000000, parentExpanded);
            fillingColor = ColorUtils.blendARGB(fillingColor, 0xFFFFFFFF, parentExpanded);
        }

        public void reset() {
            backgroundColor = Theme.getColor(Theme.key_actionBarDefaultTitle, resourcesProvider);
            fillingColor = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);

            backgroundColor = ColorUtils.blendARGB(backgroundColor, 0x24000000, parentExpanded);
            fillingColor = ColorUtils.blendARGB(fillingColor, 0xFFFFFFFF, parentExpanded);
        }

        public void setParentExpanded(float parentExpanded) {
            this.parentExpanded = parentExpanded;
            update(peerColor);
        }
    }

    public static int getTabsViewBackgroundColor(Theme.ResourcesProvider resourcesProvider, int color1, int color2) {
        final int bgColor = ColorUtils.blendARGB(color1, color2, .75f);
        final boolean bgColorIsLight = AndroidUtilities.computePerceivedBrightness(bgColor) > .721f;

        return bgColorIsLight ?
            Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider) :
            Theme.adaptHSV(bgColor, +.08f, -.08f);
    }
}
