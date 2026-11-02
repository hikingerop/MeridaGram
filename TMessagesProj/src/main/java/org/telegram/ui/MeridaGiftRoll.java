package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stars.StarGiftPatterns;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stars.StarsIntroActivity;

import java.util.ArrayList;
import java.util.Random;

/**
 * MeridaGram «Visual»: the slot-machine roll of a gift upgrade, done the way Telegram's own upgrade does it
 * (StarGiftSheet.Roller): frame by frame, models glide by continuously (about 7 a second, turning a little in 3D),
 * backdrops slide over each other with soft edges, the pattern swaps, the table values roll like reels, then
 * everything slows down and stops on the result. Purely local drawing — nothing is sent anywhere.
 */
final class MeridaGiftRoll {

    private MeridaGiftRoll() {
    }

    private static final Random RANDOM = new Random();

    // ------------------------------------------------------------------ attributes

    static class Attr {
        String name;
        TL_stars.StarGiftAttribute attr;

        boolean isLoaded() {
            return true;
        }

        void attach() {
        }

        void detach() {
        }
    }

    static final class Sticker extends Attr {
        final boolean mine;
        final ImageReceiver receiver;
        private final long created = System.currentTimeMillis();

        Sticker(View parent, TL_stars.starGiftAttributeModel model) {
            name = model.name;
            attr = model;
            mine = true;
            receiver = new ImageReceiver(parent);
            receiver.setLayerNum(4 | 6656); // like Telegram's roll: plays at full speed inside the sheet
            StarsIntroActivity.setGiftImage(receiver, model.document, 160);
        }

        /** Uses a picture already on screen (the model shown before the roll), so it does not blink. */
        Sticker(ImageReceiver from, TL_stars.starGiftAttributeModel model) {
            name = model.name;
            attr = model;
            mine = false;
            receiver = from;
        }

        @Override
        void attach() {
            if (mine) receiver.onAttachedToWindow();
        }

        @Override
        void detach() {
            if (mine) receiver.onDetachedFromWindow();
        }

        @Override
        boolean isLoaded() {
            // never hang on a slow network: after 5 s it counts as ready anyway
            return receiver.getLottieAnimation() != null || receiver.hasBitmapImage() || System.currentTimeMillis() - created > 5000;
        }
    }

    static final class Symbol extends Attr {
        final TL_stars.starGiftAttributePattern pattern;

        Symbol(TL_stars.starGiftAttributePattern pattern) {
            name = pattern.name;
            attr = pattern;
            this.pattern = pattern;
        }
    }

    static final class Background extends Attr {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Matrix matrix = new Matrix();
        final RadialGradient gradient;
        final int textColor, patternColor;

        Background(TL_stars.starGiftAttributeBackdrop backdrop) {
            name = backdrop.name;
            attr = backdrop;
            gradient = new RadialGradient(0, 0, dp(200), new int[]{backdrop.center_color | 0xFF000000, backdrop.edge_color | 0xFF000000}, new float[]{0, 1}, Shader.TileMode.CLAMP);
            paint.setShader(gradient);
            textColor = backdrop.text_color | 0xFF000000;
            patternColor = backdrop.pattern_color | 0xFF000000;
        }

        void drawFull(Canvas canvas, float cx, float cy, float w, float h, float alpha) {
            if (alpha <= 0) return;
            matrix.reset();
            matrix.postTranslate(cx, cy);
            gradient.setLocalMatrix(matrix);
            paint.setAlpha((int) (0xFF * Utilities.clamp01(alpha)));
            canvas.drawRect(0, 0, w, h, paint);
        }
    }

    // ------------------------------------------------------------------ one reel (same maths as Telegram)

    static final class AttrRoller<T extends Attr> {
        T prev, current, next;
        int currentT;
        float time;
        final ArrayList<T> attributes;
        final T start, finish;
        private final float speedMult;
        private final int totalSlowing;
        private int slowing;
        private final AnimatedFloat fast;
        private int lastNextIndex = -1;

        AttrRoller(Runnable invalidate, ArrayList<T> attributes, T start, T finish, float speedMult, int totalSlowing) {
            this.attributes = attributes;
            this.start = start;
            this.finish = finish;
            this.speedMult = speedMult;
            this.totalSlowing = totalSlowing;
            this.fast = new AnimatedFloat(invalidate, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
            this.fast.force(true);
            time = -0.5f;
            currentT = 1;
            slowing = totalSlowing;
            prev = start;
            current = next(false);
            next = next(false);
        }

        void skip() {
            prev = current;
            current = finish;
            next = null;
            currentT++;
            time = currentT + 0.5f;
        }

        boolean isFinished() {
            return current == finish && time >= currentT + 0.5f;
        }

        boolean isAlmostFinished(float offset) {
            return current == finish && time + offset >= currentT + 0.5f;
        }

        float step(float deltaTime, boolean allowFinish) {
            fast.setDuration(slowing >= totalSlowing ? 450 : (totalSlowing == 3 ? 4500 : 2500));
            final float isFast = fast.set(slowing >= totalSlowing);
            final float speed = lerp((totalSlowing == 3 ? 0.75f : 2.0f), 7.5f, isFast) * speedMult;
            time += deltaTime * speed;
            float t = time;
            if (t >= 0 && Math.floor(t) + 1 > currentT && current != finish) {
                prev = current;
                current = next;
                next = current == finish ? null : next(allowFinish);
                currentT = (int) Math.floor(t) + 1;
            }
            if (current == finish) {
                t = Math.min(t, currentT + 0.5f);
            }
            return t;
        }

        T next(boolean allowFinish) {
            if (allowFinish && finish.isLoaded()) {
                if (slowing <= 0) {
                    return finish;
                }
                slowing--;
            }
            final ArrayList<Integer> loaded = new ArrayList<>();
            for (int i = 0; i < attributes.size(); ++i) {
                if (i != lastNextIndex && attributes.get(i).isLoaded()) loaded.add(i);
            }
            if (loaded.isEmpty()) {
                for (int i = 0; i < attributes.size(); ++i) {
                    if (attributes.get(i).isLoaded()) loaded.add(i);
                }
                if (loaded.isEmpty()) {
                    return start;
                }
            }
            final int index = loaded.get(RANDOM.nextInt(loaded.size()));
            lastNextIndex = index;
            return attributes.get(index);
        }
    }

    // ------------------------------------------------------------------ the models

    /** Draws up to three models gliding sideways; placed over the big model (160 dp tall). */
    static final class StickersView extends View {
        private Sticker a, b, c;
        private float aT, bT, cT;
        private boolean aF, bF, cF;
        private final Camera camera = new Camera();
        final ArrayList<Sticker> attached = new ArrayList<>();

        StickersView(Context context) {
            super(context);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            for (Sticker s : attached) s.attach();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            for (Sticker s : attached) s.detach();
        }

        void own(Sticker s) {
            attached.add(s);
            if (isAttachedToWindow()) s.attach();
        }

        void set(Sticker a, float aT, boolean aF, Sticker b, float bT, boolean bF, Sticker c, float cT, boolean cF) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.aT = aT;
            this.bT = bT;
            this.cT = cT;
            this.aF = aF;
            this.bF = bF;
            this.cF = cF;
            invalidate();
        }

        boolean isEmpty() {
            return a == null && b == null && c == null;
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            draw(canvas, a, aT, aF);
            draw(canvas, b, bT, bF);
            draw(canvas, c, cT, cF);
        }

        private void draw(Canvas canvas, Sticker sticker, float t, boolean isFinish) {
            if (sticker == null) return;
            if (isFinish) t = Math.max(0.5f, t);
            final ImageReceiver r = sticker.receiver;
            final float wasX = r.getImageX(), wasY = r.getImageY(), wasW = r.getImageWidth(), wasH = r.getImageHeight();
            final float wasAlpha = r.getAlpha();

            final float tt = (t - 0.5f) / 1.5f;
            final float alpha = Utilities.clamp01(1.0f - Math.abs(tt));
            final float cx = getWidth() / 2.0f - tt * dp(220);
            final float cy = getHeight() / 2.0f;
            final float scale = lerp(0.85f, 1.0f, alpha);
            final float sz = getHeight();

            canvas.save();
            canvas.translate(cx + sz / 2.0f * tt, cy);
            camera.save();
            camera.rotateY(-30.0f * tt);
            camera.applyToCanvas(canvas);
            camera.restore();
            canvas.translate(-(cx + sz / 2.0f * tt), -cy);
            r.setImageCoords(cx - sz * scale / 2.0f, cy - sz * scale / 2.0f, sz * scale, sz * scale);
            r.setAlpha(alpha);
            r.draw(canvas);
            r.setImageCoords(wasX, wasY, wasW, wasH);
            r.setAlpha(wasAlpha);
            canvas.restore();
        }
    }

    // ------------------------------------------------------------------ the header background

    /**
     * Backdrop + pattern of the header, centred on the model. Changing the backdrop cross-fades; while rolling,
     * the rolling backdrops slide over it with soft edges (like Telegram).
     */
    static final class HeaderBackground extends Drawable {
        private final View view;
        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable pattern;
        private final AnimatedFloat swap;
        private final float centerY;
        private Background from, to;
        private final Background[] roll = new Background[3];
        private final float[] rollT = new float[3];
        private final boolean[] rollF = new boolean[3];
        private final GradientClip clip = new GradientClip();
        private final RectF rect = new RectF();
        private int centerTextColor;

        HeaderBackground(View view, float centerYdp) {
            this.view = view;
            this.centerY = dp(centerYdp);
            pattern = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(view, dp(28));
            swap = new AnimatedFloat(view, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    pattern.attach();
                }

                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {
                    pattern.detach();
                }
            });
            if (view.isAttachedToWindow()) pattern.attach();
        }

        void setBackdrop(TL_stars.starGiftAttributeBackdrop backdrop, boolean animated) {
            setBackdrop(backdrop == null ? null : new Background(backdrop), animated);
        }

        void setBackdrop(Background b, boolean animated) {
            if (animated && to != null) {
                from = to;
                to = b;
                swap.force(0);
                swap.set(1);
            } else {
                from = null;
                to = b;
                swap.force(1);
            }
            view.invalidate();
        }

        void setPattern(TL_stars.starGiftAttributePattern p, boolean animated) {
            pattern.set(p == null ? null : p.document, animated);
            view.invalidate();
        }

        void setRolling(Background a, float aT, boolean aF, Background b, float bT, boolean bF, Background c, float cT, boolean cF) {
            roll[0] = a;
            roll[1] = b;
            roll[2] = c;
            rollT[0] = aT;
            rollT[1] = bT;
            rollT[2] = cT;
            rollF[0] = aF;
            rollF[1] = bF;
            rollF[2] = cF;
            view.invalidate();
        }

        void clearRolling() {
            roll[0] = roll[1] = roll[2] = null;
            view.invalidate();
        }

        int getCenterTextColor() {
            return centerTextColor;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final Rect bounds = getBounds();
            final float w = bounds.width(), h = bounds.height();
            final float cx = w / 2.0f, cy = centerY;
            canvas.save();
            canvas.clipRect(bounds);
            canvas.translate(bounds.left, bounds.top);

            final float p = swap.set(1);
            int patternColor = 0, textColor = 0xFFFFFFFF;
            if (from != null && p < 1) {
                from.drawFull(canvas, cx, cy, w, h, 1f);
                patternColor = from.patternColor;
                textColor = from.textColor;
            }
            if (to != null) {
                to.drawFull(canvas, cx, cy, w, h, from != null ? p : 1f);
                patternColor = from != null && p < 1 ? androidx.core.graphics.ColorUtils.blendARGB(patternColor, to.patternColor, p) : to.patternColor;
                textColor = from != null && p < 1 ? androidx.core.graphics.ColorUtils.blendARGB(textColor, to.textColor, p) : to.textColor;
            } else {
                canvas.drawColor(0xFF2A2A2C);
            }
            for (int i = 0; i < 3; i++) {
                final Background bg = roll[i];
                if (bg == null) continue;
                float t = rollT[i];
                if (rollF[i]) t = Math.max(0.5f, t);
                final float tt = (t - 0.5f) / 1.5f;
                final float alpha = Utilities.clamp01(1.0f - Math.abs(tt));
                final float r = Math.max(.8f * w, dp(180));
                final float bx = cx - tt * r * 1.8f;
                canvas.saveLayerAlpha(bx - r, 0, bx + r, h, 0xFF, Canvas.ALL_SAVE_FLAG);
                bg.matrix.reset();
                bg.matrix.postTranslate(bx, cy);
                bg.gradient.setLocalMatrix(bg.matrix);
                bg.paint.setAlpha((int) (0xFF * alpha));
                canvas.drawRect(bx - r, 0, bx + r, h, bg.paint);
                final float R = dp(90);
                rect.set(bx - r, 0, bx - r + R, h);
                clip.draw(canvas, rect, GradientClip.LEFT, 1.0f);
                rect.set(bx + r - R, 0, bx + r, h);
                clip.draw(canvas, rect, GradientClip.RIGHT, 1.0f);
                canvas.restore();
                // colour in the middle of the header, where the pattern and the title are
                final float inside = cx < bx - r || cx > bx + r ? 0 : Math.min(Utilities.clamp01((cx - (bx - r)) / r), Utilities.clamp01(1.0f - (cx - bx) / r));
                patternColor = Theme.blendOver(patternColor, Theme.multAlpha(bg.patternColor, alpha * inside));
                textColor = Theme.blendOver(textColor, Theme.multAlpha(bg.textColor, alpha * inside));
            }
            centerTextColor = textColor;
            if (to != null) {
                canvas.save();
                canvas.translate(cx, cy);
                pattern.setColor(patternColor);
                StarGiftPatterns.drawPattern(canvas, pattern, w, h, 1.0f, 1.0f);
                canvas.restore();
            }
            canvas.restore();
            if (p < 1 || roll[0] != null || roll[1] != null || roll[2] != null) {
                view.invalidate();
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // ------------------------------------------------------------------ the table values

    /** A table value that rolls like a reel: the old name leaves upwards, the new one comes from below. */
    static final class TextRoll extends FrameLayout {
        private final Theme.ResourcesProvider resourcesProvider;
        private final ButtonSpan.TextViewButtons[] views = new ButtonSpan.TextViewButtons[3];
        private final Attr[] shown = new Attr[3];
        private final GradientClip clip = new GradientClip();
        private final RectF rect = new RectF();
        private boolean bounced;

        TextRoll(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            for (int i = 0; i < 3; i++) {
                views[i] = new ButtonSpan.TextViewButtons(context);
                views[i].setTextColor(0xFFFFFFFF);
                views[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                views[i].setSingleLine(true);
                views[i].setEllipsize(TextUtils.TruncateAt.END);
                views[i].setPadding(0, dp(4), 0, dp(4));
                views[i].setVisibility(View.INVISIBLE);
                addView(views[i], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 12.66f, 5.33f, 12.66f, 5.33f));
            }
        }

        private CharSequence text(Attr a) {
            final SpannableStringBuilder sb = new SpannableStringBuilder(a.name != null ? a.name : "");
            if (a.attr != null) {
                final Integer[] color = new Integer[1];
                final CharSequence rarity = StarGiftSheet.getRarityName(a.attr.rarity, color);
                if (!TextUtils.isEmpty(rarity)) {
                    sb.append(' ').append(ButtonSpan.make(rarity, null, resourcesProvider, color[0]));
                }
            }
            return sb;
        }

        /** The final value, still. */
        void setStatic(Attr a) {
            update(null, 0, false, a, 0.5f, true, null, 0, false);
        }

        void update(Attr prev, float prevT, boolean prevF, Attr current, float currentT, boolean currentF, Attr next, float nextT, boolean nextF) {
            put(0, prev, prevT, prevF);
            put(1, current, currentT, currentF);
            put(2, next, nextT, nextF);
            if (current != null && currentF && (Math.max(0.5f, currentT) - 0.5f) <= 0) {
                bounce(views[1]);
            }
        }

        private void put(int i, Attr a, float t, boolean isFinish) {
            if (a == null) {
                views[i].setVisibility(View.INVISIBLE);
                return;
            }
            if (isFinish) t = Math.max(0.5f, t);
            final float tt = (t - 0.5f) / 1.5f;
            if (shown[i] != a) {
                shown[i] = a;
                views[i].setText(text(a));
            }
            views[i].setVisibility(View.VISIBLE);
            views[i].setTranslationY(dp(36) * tt);
        }

        private void bounce(View view) {
            if (bounced) return;
            bounced = true;
            final android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0, 1.0f);
            va.addUpdateListener(an -> {
                final float scale = 1 + .03f * (float) Math.sin(Math.PI * (float) an.getAnimatedValue());
                view.setScaleX(scale);
                view.setScaleY(scale);
            });
            va.setDuration(180);
            va.start();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(37.66f), MeasureSpec.EXACTLY));
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
            super.dispatchDraw(canvas);
            canvas.save();
            rect.set(0, 0, getWidth(), dp(8));
            clip.draw(canvas, rect, GradientClip.TOP, 1.0f);
            rect.set(0, getHeight() - dp(8), getWidth(), getHeight());
            clip.draw(canvas, rect, GradientClip.BOTTOM, 1.0f);
            canvas.restore();
            canvas.restore();
        }
    }

    // ------------------------------------------------------------------ the driver

    /** Runs the four reels (model, pattern, two backdrops) every frame and feeds the views. */
    static final class Roller {
        final StickersView stickers;
        final HeaderBackground background;
        TextRoll modelText, patternText, backdropText;

        final ArrayList<Sticker> models = new ArrayList<>();
        final ArrayList<Background> backgrounds = new ArrayList<>();
        final ArrayList<Symbol> symbols = new ArrayList<>();

        AttrRoller<Sticker> modelRoller;
        AttrRoller<Symbol> symbolRoller;
        AttrRoller<Background> backdropRoller, backdropRoller2;

        private boolean rolling, sentDone, sentDone2;
        private long lastFrameTime;
        private float realTime, durationT;
        private Runnable whenDone, whenDone2;
        private Attr lastPattern;
        private final Runnable frame = this::update;

        Roller(StickersView stickers, HeaderBackground background) {
            this.stickers = stickers;
            this.background = background;
        }

        /** Start loading every possible model at once, so the roll does not wait for pictures. */
        void preload(ArrayList<TL_stars.starGiftAttributeModel> m, ArrayList<TL_stars.starGiftAttributeBackdrop> b, ArrayList<TL_stars.starGiftAttributePattern> p) {
            for (TL_stars.starGiftAttributeModel model : m) {
                final Sticker s = new Sticker(stickers, model);
                stickers.own(s);
                models.add(s);
            }
            for (TL_stars.starGiftAttributeBackdrop backdrop : b) backgrounds.add(new Background(backdrop));
            for (TL_stars.starGiftAttributePattern pattern : p) symbols.add(new Symbol(pattern));
        }

        boolean isRolling() {
            return rolling;
        }

        void start(ImageReceiver fromImage, TL_stars.starGiftAttributeModel fromModel, TL_stars.starGiftAttributePattern fromPattern, TL_stars.starGiftAttributeBackdrop fromBackdrop,
                   TL_stars.starGiftAttributeModel toModel, TL_stars.starGiftAttributePattern toPattern, TL_stars.starGiftAttributeBackdrop toBackdrop,
                   Runnable whenDone, Runnable whenDone2) {
            this.whenDone = whenDone;
            this.whenDone2 = whenDone2;
            durationT = RANDOM.nextFloat();
            realTime = 0;
            sentDone = sentDone2 = false;
            rolling = true;
            lastFrameTime = System.currentTimeMillis();

            final Sticker finishSticker = new Sticker(stickers, toModel);
            stickers.own(finishSticker);
            final Runnable inv = stickers::invalidate;
            modelRoller = new AttrRoller<>(inv, models, new Sticker(fromImage, fromModel), finishSticker, 0.9f, durationT > 0.5f ? 3 : 2);
            symbolRoller = new AttrRoller<>(inv, symbols, new Symbol(fromPattern), new Symbol(toPattern), 1.0f, durationT > 0.5f ? 2 : 1);
            backdropRoller = new AttrRoller<>(inv, backgrounds, new Background(fromBackdrop), new Background(toBackdrop), 0.5f, durationT > 0.5f ? 2 : 1);
            backdropRoller2 = new AttrRoller<>(inv, backgrounds, new Background(fromBackdrop), new Background(toBackdrop), 1.25f, durationT > 0.5f ? 2 : 1);
            // under the sliding backdrops lies the result, so the end is seamless
            background.setBackdrop(backdropRoller.finish, false);
            stickers.postOnAnimation(frame);
        }

        void skip() {
            if (!rolling) return;
            modelRoller.skip();
            symbolRoller.skip();
            backdropRoller.skip();
            backdropRoller2.skip();
        }

        void stop() {
            rolling = false;
            stickers.removeCallbacks(frame);
        }

        private void update() {
            if (!rolling) return;
            if (!stickers.isAttachedToWindow()) {
                rolling = false; // the window was closed
                return;
            }
            final long now = System.currentTimeMillis();
            final float dt = Math.min((now - lastFrameTime) / 1000.0f, .25f);
            lastFrameTime = now;
            realTime += dt;

            final float bgT = backdropRoller.step(dt, realTime > lerp(0.10f, 1.0f, durationT));
            final float bg2T = backdropRoller2.step(dt, realTime > lerp(0.10f, 1.0f, durationT));
            final float symbolT = symbolRoller.step(dt, backdropRoller.isAlmostFinished(.5f));
            final float modelT = modelRoller.step(dt, backdropRoller.isAlmostFinished(.5f) && symbolRoller.isAlmostFinished(.5f));

            if (modelText != null) {
                modelText.update(modelRoller.prev, (modelRoller.currentT - modelT) - 1, modelRoller.prev == modelRoller.finish,
                        modelRoller.current, modelRoller.currentT - modelT, modelRoller.current == modelRoller.finish,
                        modelRoller.next, (modelRoller.currentT - modelT) + 1, modelRoller.next == modelRoller.finish);
            }
            if (patternText != null) {
                patternText.update(symbolRoller.prev, (symbolRoller.currentT - symbolT) - 1, symbolRoller.prev == symbolRoller.finish,
                        symbolRoller.current, symbolRoller.currentT - symbolT, symbolRoller.current == symbolRoller.finish,
                        symbolRoller.next, (symbolRoller.currentT - symbolT) + 1, symbolRoller.next == symbolRoller.finish);
            }
            if (backdropText != null) {
                backdropText.update(backdropRoller2.prev, (backdropRoller2.currentT - bg2T) - 1, backdropRoller2.prev == backdropRoller2.finish,
                        backdropRoller2.current, backdropRoller2.currentT - bg2T, backdropRoller2.current == backdropRoller2.finish,
                        backdropRoller2.next, (backdropRoller2.currentT - bg2T) + 1, backdropRoller2.next == backdropRoller2.finish);
            }
            if (lastPattern != symbolRoller.current) {
                lastPattern = symbolRoller.current;
                background.setPattern(symbolRoller.current.pattern, true);
            }
            stickers.set(modelRoller.prev, (modelRoller.currentT - modelT) - 1, modelRoller.prev == modelRoller.finish,
                    modelRoller.current, modelRoller.currentT - modelT, modelRoller.current == modelRoller.finish,
                    modelRoller.next, (modelRoller.currentT - modelT) + 1, modelRoller.next == modelRoller.finish);
            background.setRolling(backdropRoller.prev, (backdropRoller.currentT - bgT) - 1, backdropRoller.prev == backdropRoller.finish,
                    backdropRoller.current, backdropRoller.currentT - bgT, backdropRoller.current == backdropRoller.finish,
                    backdropRoller.next, (backdropRoller.currentT - bgT) + 1, backdropRoller.next == backdropRoller.finish);

            final boolean finished = backdropRoller.isFinished() && symbolRoller.isFinished();
            if (finished && modelRoller.isAlmostFinished(.25f) && !sentDone2) {
                sentDone2 = true;
                if (whenDone2 != null) whenDone2.run();
            }
            if (finished && modelRoller.isFinished() && !sentDone) {
                sentDone = true;
                rolling = false;
                // stay on the result: the model in the middle, the result backdrop, still values
                stickers.set(null, 0, false, modelRoller.finish, 0.5f, true, null, 0, false);
                background.clearRolling();
                background.setPattern(symbolRoller.finish.pattern, true);
                if (modelText != null) modelText.setStatic(modelRoller.finish);
                if (patternText != null) patternText.setStatic(symbolRoller.finish);
                if (backdropText != null) backdropText.setStatic(backdropRoller2.finish);
                if (whenDone != null) whenDone.run();
                return;
            }
            stickers.postOnAnimation(frame);
        }
    }
}
