package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * MeridaGram «Visual»: swiping left / right between gift windows, like Telegram's gift window (StarGiftSheet):
 * the card follows the finger, shrinks a little towards its far edge and turns slightly, and the neighbour
 * comes in from the side right next to it. Released past a third of the width (or flung) it moves on,
 * otherwise it springs back. At the ends the card only gives a little.
 */
final class MeridaGiftPager extends FrameLayout {

    interface Factory {
        View create(int index);
    }

    private final Factory factory;
    private final int count;
    private int index;
    private View current;
    private View neighbour;
    private int neighbourIndex = -1;
    private float offset;
    private boolean dragging;
    private float startX, startY, lastX;
    private VelocityTracker velocity;
    private final int touchSlop;
    private ValueAnimator animator;

    MeridaGiftPager(Context context, int count, int index, Factory factory) {
        super(context);
        this.factory = factory;
        this.count = count;
        this.index = index;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        current = factory.create(index);
        addView(current, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    /**
     * As tall as the taller of the current card and the neighbour sliding in; the cards stand on the bottom and
     * draw their own backgrounds (the sheet is transparent), so a taller or shorter neighbour never makes the
     * window jump — like Telegram's swipeable gift window.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int w = MeasureSpec.getSize(widthMeasureSpec);
        final int hMode = MeasureSpec.getMode(heightMeasureSpec);
        final int childH = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), hMode == MeasureSpec.UNSPECIFIED ? MeasureSpec.UNSPECIFIED : MeasureSpec.AT_MOST);
        final int childW = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
        int h = 0;
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            child.measure(childW, childH);
            h = Math.max(h, child.getMeasuredHeight());
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int h = bottom - top;
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            child.layout(0, h - child.getMeasuredHeight(), child.getMeasuredWidth(), h);
        }
    }

    private boolean startsDrag(float x, float y) {
        final float dx = x - startX, dy = y - startY;
        return Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.5f;
    }

    private void beginDrag(float x) {
        dragging = true;
        lastX = x;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        final ViewParent p = getParent();
        if (p != null) p.requestDisallowInterceptTouchEvent(true);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (count <= 1) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = ev.getX();
                startY = ev.getY();
                dragging = false;
                trackVelocity(ev, true);
                return false;
            case MotionEvent.ACTION_MOVE:
                trackVelocity(ev, false);
                if (!dragging && startsDrag(ev.getX(), ev.getY())) {
                    beginDrag(ev.getX());
                }
                return dragging;
            default:
                return dragging;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (count <= 1) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = ev.getX();
                startY = ev.getY();
                dragging = false;
                trackVelocity(ev, true);
                return true;
            case MotionEvent.ACTION_MOVE:
                trackVelocity(ev, false);
                if (!dragging && startsDrag(ev.getX(), ev.getY())) {
                    beginDrag(ev.getX());
                }
                if (dragging) {
                    offset += ev.getX() - lastX;
                    lastX = ev.getX();
                    apply();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float vx = 0;
                if (velocity != null) {
                    velocity.addMovement(ev);
                    velocity.computeCurrentVelocity(1000);
                    vx = velocity.getXVelocity();
                    velocity.recycle();
                    velocity = null;
                }
                if (dragging) {
                    dragging = false;
                    settle(ev.getActionMasked() == MotionEvent.ACTION_UP ? vx : 0);
                }
                return true;
        }
        return true;
    }

    private void trackVelocity(MotionEvent ev, boolean reset) {
        if (reset && velocity != null) {
            velocity.recycle();
            velocity = null;
        }
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(ev);
    }

    private int target() {
        return offset < 0 ? index + 1 : offset > 0 ? index - 1 : -1;
    }

    private boolean exists(int i) {
        return i >= 0 && i < count;
    }

    private void apply() {
        final int w = getWidth();
        if (w <= 0) return;
        final int target = target();
        if (!exists(target)) {
            dropNeighbour();
            // at the ends the card only gives a little
            transform(current, offset * .25f, w);
            return;
        }
        if (neighbourIndex != target) {
            dropNeighbour();
            neighbour = factory.create(target);
            neighbourIndex = target;
            addView(neighbour, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            neighbour.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, MeasureSpec.AT_MOST));
            neighbour.layout(0, getHeight() - neighbour.getMeasuredHeight(), w, getHeight());
            requestLayout();
        }
        transform(current, offset, w);
        transform(neighbour, offset + (target > index ? w : -w), w);
    }

    /** Telegram's gift-window swipe: slide, shrink towards the far edge, turn a little. */
    private static void transform(View v, float tx, int w) {
        final float t = Math.max(-1f, Math.min(1f, tx / w));
        v.setTranslationX(tx);
        v.setPivotX(t > 0 ? 0 : w);
        v.setPivotY(v.getHeight() / 2f);
        v.setCameraDistance(Math.max(1, v.getHeight()) * 3.4f);
        v.setScaleX(1f - Math.abs(t) * .25f);
        v.setScaleY(1f - Math.abs(t) * .06f);
        v.setRotationY(t * 10);
    }

    private static void reset(View v) {
        v.setTranslationX(0);
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setRotationY(0);
    }

    private void dropNeighbour() {
        if (neighbour != null) {
            removeView(neighbour);
            neighbour = null;
        }
        neighbourIndex = -1;
    }

    private void settle(float vx) {
        final int w = getWidth();
        final int target = target();
        final boolean flung = Math.abs(vx) > AndroidUtilities.dp(500) && Math.signum(vx) == Math.signum(offset);
        final boolean go = exists(target) && (Math.abs(offset) > w / 3f || flung);
        final float from = offset;
        final float to = go ? (offset < 0 ? -w : w) : 0;
        animator = ValueAnimator.ofFloat(0, 1);
        animator.addUpdateListener(a -> {
            final float f = (float) a.getAnimatedValue();
            // keep the sign while settling so the same neighbour stays on screen
            final float value = from + (to - from) * f;
            offset = go ? value : (from < 0 ? Math.min(value, -.5f) : Math.max(value, .5f));
            apply();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animator != animation) return;
                animator = null;
                if (go && neighbour != null) {
                    removeView(current);
                    current = neighbour;
                    index = neighbourIndex;
                    neighbour = null;
                    neighbourIndex = -1;
                } else {
                    dropNeighbour();
                }
                offset = 0;
                reset(current);
                requestLayout();
            }
        });
        animator.setDuration(go ? 280 : 320);
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animator.start();
    }
}
