package org.telegram.utils.settings;

/** MeridaGram's own options, shown in Settings → MeridaGram Settings. */
public final class MeridaSettings {

    public static final BooleanSetting hideStories =
        BooleanSetting.of("merida_hide_stories", false);

    public static final BooleanSetting showId =
        BooleanSetting.of("merida_show_id", false);

    /** «Copy ID» of the sender in the message menu. */
    public static final BooleanSetting copyIdMenu =
        BooleanSetting.of("merida_copy_id_menu", false);

    /**
     * Encryption over Telegram (MeridaCrypto). While off, the chat menu has no «MeridaGram encryption»
     * and new messages go out as usual; see MeridaCrypto.isFeatureOn for phones that used it before.
     */
    public static final BooleanSetting cryptoEnabled =
        BooleanSetting.of("merida_crypto_enabled", false);

    /** Python plugins (MeridaPlugins). Off until turned on in MeridaGram Settings → Plugins. */
    public static final BooleanSetting pluginsEnabled =
        BooleanSetting.of("merida_plugins_enabled", false);

    /** Folder tabs are not shown above the chat list; only «All chats» stays. */
    public static final BooleanSetting hideFolderTabs =
        BooleanSetting.of("merida_hide_folder_tabs", false);

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

    /** Messages kept by spy mode are drawn half-transparent. */
    public static final BooleanSetting spyTranslucent =
        BooleanSetting.of("merida_spy_translucent", true);

    /** The mark next to their time: 0 icon and word, 1 icon only, 2 word only (MeridaSpy.mark). */
    public static final IntSetting spyMarkStyle =
        IntSetting.of("merida_spy_mark_style", 0);

    /** Colour of that mark, index in MeridaSpy.MARK_COLORS; 0 is the colour of the time. */
    public static final IntSetting spyMarkColor =
        IntSetting.of("merida_spy_mark_color", 0);

    /** A ghost icon next to MeridaGram on the chat list's top bar while ghost mode is on. */
    public static final BooleanSetting ghostStatus =
        BooleanSetting.of("merida_ghost_status", true);

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

    /**
     * Allow screenshots in channels, groups and stories where copying is restricted.
     * Secret chats, disappearing media and paid media stay protected.
     */
    public static final BooleanSetting allowScreenshots =
        BooleanSetting.of("merida_allow_screenshots", true);

    /** Warn with a big window when a new device logs into the account (MeridaSessions). */
    public static final BooleanSetting sessionGuard =
        BooleanSetting.of("merida_session_guard", true);

    /** Remove GPS position, phone model and time from photos sent as files (MeridaExif). */
    public static final BooleanSetting stripPhotoMetadata =
        BooleanSetting.of("merida_strip_photo_metadata", true);

    /** Mini apps get a generic device name and no third-party cookies. */
    public static final BooleanSetting webAppPrivacy =
        BooleanSetting.of("merida_webapp_privacy", true);

    public static final BooleanSetting vpnEnabled =
        BooleanSetting.of("merida_vpn_enabled", true);

    private MeridaSettings() {
    }
}
