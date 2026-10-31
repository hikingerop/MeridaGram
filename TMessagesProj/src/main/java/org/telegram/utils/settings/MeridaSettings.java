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

    public static final BooleanSetting vpnEnabled =
        BooleanSetting.of("merida_vpn_enabled", true);

    private MeridaSettings() {
    }
}
