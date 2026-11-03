package org.telegram.messenger.merida;

import android.os.Build;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import org.telegram.messenger.FileLog;
import org.telegram.utils.settings.MeridaSettings;

/**
 * MeridaGram «High refresh rate»: asks the phone for its fastest screen mode (90 / 120 / 144 Hz) at the same
 * resolution, like Chimera does. Many phones (Xiaomi and others) give a high refresh rate only to apps on
 * their own list — official Telegram is on it, MeridaGram is not, so without this it runs at 60 Hz and
 * scrolling and animations look less smooth. Off — the phone decides again.
 */
public final class MeridaRefreshRate {

    private MeridaRefreshRate() {
    }

    public static void apply(Window window) {
        if (window == null || Build.VERSION.SDK_INT < 23) {
            return;
        }
        try {
            final WindowManager.LayoutParams params = window.getAttributes();
            if (!MeridaSettings.highRefreshRate.get()) {
                if (params.preferredDisplayModeId != 0 || params.preferredRefreshRate != 0) {
                    params.preferredDisplayModeId = 0;
                    params.preferredRefreshRate = 0;
                    window.setAttributes(params);
                }
                return;
            }
            final Display display = window.getWindowManager().getDefaultDisplay();
            if (display == null) {
                return;
            }
            final Display.Mode current = display.getMode();
            Display.Mode best = current;
            for (Display.Mode mode : display.getSupportedModes()) {
                if (mode.getPhysicalWidth() == current.getPhysicalWidth()
                        && mode.getPhysicalHeight() == current.getPhysicalHeight()
                        && mode.getRefreshRate() > best.getRefreshRate() + 0.5f) {
                    best = mode;
                }
            }
            if (params.preferredDisplayModeId != best.getModeId() || params.preferredRefreshRate != best.getRefreshRate()) {
                params.preferredDisplayModeId = best.getModeId();
                params.preferredRefreshRate = best.getRefreshRate();
                window.setAttributes(params);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
