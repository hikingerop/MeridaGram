package org.telegram.messenger.merida;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.Components.VideoPlayer;
import org.telegram.utils.settings.MeridaSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * MeridaGram's advanced music player. It does not replace Telegram's player: it adds sound effects
 * (equalizer presets, bass boost, louder quiet tracks) to the track that is playing, a sleep timer, and
 * loading of a few next tracks ahead. The effects can work in every chat, in every chat except chosen
 * ones, or only in chosen chats (for example, the music bots someone listens to).
 */
public final class MeridaPlayer {

    public static final int MODE_ALL = 0;
    public static final int MODE_EXCEPT = 1;
    public static final int MODE_ONLY = 2;

    public static final int EQ_NORMAL = 0;
    public static final int EQ_BASS = 1;
    public static final int EQ_ROCK = 2;
    public static final int EQ_POP = 3;
    public static final int EQ_VOICE = 4;
    public static final int EQ_ELECTRO = 5;
    public static final int EQ_COUNT = 6;

    /** How many tracks after the current one are loaded ahead while the option is on. */
    public static final int PRELOAD_AHEAD = 3;

    /** Reference frequencies (Hz) of the presets below; each phone band takes the nearest one. */
    private static final int[] REF_HZ = {60, 230, 910, 3600, 14000};
    /** Gains in dB for {@link #REF_HZ}, one row per preset; the «normal» row turns the equalizer off. */
    private static final int[][] PRESET_DB = {
            {0, 0, 0, 0, 0},
            {6, 4, 0, 0, 0},
            {4, 2, -1, 2, 4},
            {-1, 2, 4, 2, -1},
            {-2, 0, 3, 4, 1},
            {5, 3, 0, 1, 4},
    };

    private static final int BASS_STRENGTH = 800; // of 1000
    private static final int LOUDER_GAIN_MB = 600; // +6 dB, the enhancer keeps peaks from clipping

    private static Equalizer equalizer;
    private static BassBoost bassBoost;
    private static LoudnessEnhancer loudness;
    private static int sessionId;
    private static long playingDialogId;
    private static boolean playingMusic;

    private static Runnable sleepRunnable;
    private static long sleepAt;

    private MeridaPlayer() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("merida_player", Context.MODE_PRIVATE);
    }

    // ---- chats -------------------------------------------------------------------------------

    /** Dialog ids of the chosen chats (the meaning depends on the mode). */
    public static ArrayList<Long> getChosenChats() {
        final ArrayList<Long> result = new ArrayList<>();
        for (String s : prefs().getStringSet("chats", new HashSet<>())) {
            try {
                result.add(Long.parseLong(s));
            } catch (NumberFormatException ignore) {
            }
        }
        return result;
    }

    public static boolean isChosen(long dialogId) {
        return prefs().getStringSet("chats", new HashSet<>()).contains(String.valueOf(dialogId));
    }

    private static void setChosen(long dialogId, boolean chosen) {
        final Set<String> set = new HashSet<>(prefs().getStringSet("chats", new HashSet<>()));
        if (chosen) {
            set.add(String.valueOf(dialogId));
        } else {
            set.remove(String.valueOf(dialogId));
        }
        prefs().edit().putStringSet("chats", set).apply();
    }

    public static void removeChosen(long dialogId) {
        setChosen(dialogId, false);
        reapply();
    }

    /** True when the advanced player works for music from this chat. */
    public static boolean isActiveFor(long dialogId) {
        if (!MeridaSettings.playerAdvanced.get()) {
            return false;
        }
        switch (MeridaSettings.playerMode.get()) {
            case MODE_EXCEPT:
                return !isChosen(dialogId);
            case MODE_ONLY:
                return isChosen(dialogId);
            default:
                return true;
        }
    }

    /**
     * Turns the advanced player on or off for one chat, from the player's menu.
     * In «all chats» mode turning a chat off switches to «all except chosen» with that chat chosen.
     */
    public static void toggleChat(long dialogId) {
        final int mode = MeridaSettings.playerMode.get();
        if (mode == MODE_ALL) {
            MeridaSettings.playerMode.set(MODE_EXCEPT);
            setChosen(dialogId, true);
        } else {
            setChosen(dialogId, !isChosen(dialogId));
        }
        reapply();
    }

    // ---- effects -----------------------------------------------------------------------------

    /** Called by MediaController each time a new audio file starts. */
    public static void onPlay(VideoPlayer player, MessageObject message) {
        releaseEffects();
        sessionId = 0;
        playingMusic = message != null && message.isMusic();
        playingDialogId = message != null ? message.getDialogId() : 0;
        if (!playingMusic || player == null || player.player == null) {
            return;
        }
        try {
            sessionId = player.player.getAudioSessionId();
        } catch (Throwable e) {
            FileLog.e(e);
            sessionId = 0;
        }
        applyEffects();
    }

    /** Called when the player stops for good. */
    public static void onStop() {
        releaseEffects();
        sessionId = 0;
        playingMusic = false;
    }

    /** Applies changed settings to the track that is playing now. */
    public static void reapply() {
        releaseEffects();
        applyEffects();
    }

    private static void applyEffects() {
        if (sessionId == 0 || !playingMusic || !isActiveFor(playingDialogId)) {
            return;
        }
        final int preset = MeridaSettings.playerEqPreset.get();
        if (preset > EQ_NORMAL && preset < EQ_COUNT) {
            try {
                equalizer = new Equalizer(0, sessionId);
                final short bands = equalizer.getNumberOfBands();
                final short[] range = equalizer.getBandLevelRange();
                for (short b = 0; b < bands; b++) {
                    final int hz = equalizer.getCenterFreq(b) / 1000;
                    int mb = PRESET_DB[preset][nearestRef(hz)] * 100;
                    mb = Math.max(range[0], Math.min(range[1], mb));
                    equalizer.setBandLevel(b, (short) mb);
                }
                equalizer.setEnabled(true);
            } catch (Throwable e) {
                FileLog.e(e);
                releaseEqualizer();
            }
        }
        if (MeridaSettings.playerBassBoost.get()) {
            try {
                bassBoost = new BassBoost(0, sessionId);
                if (bassBoost.getStrengthSupported()) {
                    bassBoost.setStrength((short) BASS_STRENGTH);
                }
                bassBoost.setEnabled(true);
            } catch (Throwable e) {
                FileLog.e(e);
                releaseBass();
            }
        }
        if (MeridaSettings.playerLouder.get()) {
            try {
                loudness = new LoudnessEnhancer(sessionId);
                loudness.setTargetGain(LOUDER_GAIN_MB);
                loudness.setEnabled(true);
            } catch (Throwable e) {
                FileLog.e(e);
                releaseLoudness();
            }
        }
    }

    private static int nearestRef(int hz) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        final double logHz = Math.log(Math.max(hz, 1));
        for (int i = 0; i < REF_HZ.length; i++) {
            final double d = Math.abs(Math.log(REF_HZ[i]) - logHz);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    private static void releaseEffects() {
        releaseEqualizer();
        releaseBass();
        releaseLoudness();
    }

    private static void releaseEqualizer() {
        if (equalizer != null) {
            try {
                equalizer.release();
            } catch (Throwable ignore) {
            }
            equalizer = null;
        }
    }

    private static void releaseBass() {
        if (bassBoost != null) {
            try {
                bassBoost.release();
            } catch (Throwable ignore) {
            }
            bassBoost = null;
        }
    }

    private static void releaseLoudness() {
        if (loudness != null) {
            try {
                loudness.release();
            } catch (Throwable ignore) {
            }
            loudness = null;
        }
    }

    // ---- preload -----------------------------------------------------------------------------

    /** How many next tracks MediaController loads ahead for music from this chat. */
    public static int preloadCount(long dialogId) {
        return MeridaSettings.playerPreload.get() && isActiveFor(dialogId) ? PRELOAD_AHEAD : 1;
    }

    // ---- sleep timer -------------------------------------------------------------------------

    /** Stops the music after the given number of minutes; 0 turns the timer off. */
    public static void setSleepTimer(int minutes) {
        if (sleepRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(sleepRunnable);
            sleepRunnable = null;
        }
        sleepAt = 0;
        if (minutes <= 0) {
            return;
        }
        sleepAt = SystemClock.elapsedRealtime() + minutes * 60_000L;
        sleepRunnable = () -> {
            sleepRunnable = null;
            sleepAt = 0;
            final MediaController controller = MediaController.getInstance();
            final MessageObject playing = controller.getPlayingMessageObject();
            if (playing != null && !controller.isMessagePaused()) {
                controller.pauseMessage(playing);
            }
        };
        AndroidUtilities.runOnUIThread(sleepRunnable, minutes * 60_000L);
    }

    /** Minutes left on the sleep timer, rounded up; 0 when it is off. */
    public static int sleepMinutesLeft() {
        if (sleepAt == 0) {
            return 0;
        }
        final long left = sleepAt - SystemClock.elapsedRealtime();
        return left <= 0 ? 0 : (int) ((left + 59_999L) / 60_000L);
    }
}
