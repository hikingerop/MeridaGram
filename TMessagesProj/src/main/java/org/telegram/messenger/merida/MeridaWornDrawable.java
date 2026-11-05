package org.telegram.messenger.merida;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

/**
 * MeridaGram «Visual»: the model of the worn visual NFT drawn by its own ImageReceiver, looping.
 * Telegram's emoji drawables show only the first frame when «animated emoji» is off in the power-saving
 * presets (medium/low devices without Premium), so the worn NFT at your name never moved.
 */
public class MeridaWornDrawable extends Drawable {

    private final ImageReceiver receiver;
    private final int size;
    private final long documentId;
    private boolean attached;

    public MeridaWornDrawable(View parent, TLRPC.Document document, int size) {
        this.size = size;
        this.documentId = document.id;
        receiver = new ImageReceiver(parent);
        receiver.setLayerNum(4 | 6656); // not paused by sheets opening over the profile
        receiver.setAllowStartLottieAnimation(true);
        receiver.setAllowStartAnimation(true);
        receiver.setAutoRepeat(1);
        receiver.setAspectFit(true);
        final SvgHelper.SvgDrawable thumb = DocumentObject.getSvgThumb(document.thumbs, Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
        final TLRPC.PhotoSize photoThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
        receiver.setImage(ImageLocation.getForDocument(document), "66_66", ImageLocation.getForDocument(photoThumb, document), "66_66", thumb, document.size, null, document, 1);
    }

    public long getDocumentId() {
        return documentId;
    }

    public void attach() {
        if (attached) return;
        attached = true;
        receiver.onAttachedToWindow();
        receiver.startAnimation();
    }

    public void detach() {
        if (!attached) return;
        attached = false;
        receiver.onDetachedFromWindow();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect b = getBounds();
        receiver.setImageCoords(b.left, b.top, b.width(), b.height());
        receiver.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {
        receiver.setAlpha(alpha / 255f);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // the model keeps its own colours
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
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
