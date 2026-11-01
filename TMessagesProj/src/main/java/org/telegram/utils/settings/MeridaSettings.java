package org.telegram.utils.settings;

/** MeridaGram's own options, shown in Settings → MeridaGram Settings. */
public final class MeridaSettings {

    public static final BooleanSetting hideStories =
        BooleanSetting.of("merida_hide_stories", false);

    public static final BooleanSetting showId =
        BooleanSetting.of("merida_show_id", false);

    public static final BooleanSetting timeWithSeconds =
        BooleanSetting.of("merida_time_with_seconds", false);

    /** Ghost mode: read marks, «typing», «online» and story views are not sent to Telegram. */
    public static final BooleanSetting ghostMode =
        BooleanSetting.of("merida_ghost_mode", false);

    /** Parts of ghost mode; each works only while ghostMode is on. */
    public static final BooleanSetting ghostNoRead =
        BooleanSetting.of("merida_ghost_no_read", true);

    public static final BooleanSetting ghostNoStories =
        BooleanSetting.of("merida_ghost_no_stories", true);

    public static final BooleanSetting ghostNoOnline =
        BooleanSetting.of("merida_ghost_no_online", true);

    public static final BooleanSetting ghostNoTyping =
        BooleanSetting.of("merida_ghost_no_typing", true);

    public static final BooleanSetting ghostAutoOffline =
        BooleanSetting.of("merida_ghost_auto_offline", true);

    /** Send every message without sound, independent of ghost mode. */
    public static final BooleanSetting sendSilent =
        BooleanSetting.of("merida_send_silent", false);

    /** Spy mode: keep messages others delete, marked as deleted (stored only on this phone). */
    public static final BooleanSetting spySaveDeleted =
        BooleanSetting.of("merida_spy_save_deleted", false);

    /** Telegram's New Year snow on the top bar and chat backgrounds, any time of the year. */
    public static final BooleanSetting snow =
        BooleanSetting.of("merida_snow", false);

    /** Advanced music player (equalizer, bass, louder tracks, preload); see MeridaPlayer. */
    public static final BooleanSetting playerAdvanced =
        BooleanSetting.of("merida_player_advanced", true);

    /** Where it works: 0 all chats, 1 all except chosen, 2 only chosen (MeridaPlayer.MODE_*). */
    public static final IntSetting playerMode =
        IntSetting.of("merida_player_mode", 0);

    /** Equalizer preset, MeridaPlayer.EQ_*; 0 means the equalizer is off. */
    public static final IntSetting playerEqPreset =
        IntSetting.of("merida_player_eq", 0);

    public static final BooleanSetting playerBassBoost =
        BooleanSetting.of("merida_player_bass", false);

    public static final BooleanSetting playerLouder =
        BooleanSetting.of("merida_player_louder", false);

    public static final BooleanSetting playerPreload =
        BooleanSetting.of("merida_player_preload", true);

    public static final BooleanSetting vpnEnabled =
        BooleanSetting.of("merida_vpn_enabled", true);

    private MeridaSettings() {
    }
}
