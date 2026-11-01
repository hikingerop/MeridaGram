package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.merida.MeridaCrypto;
import org.telegram.messenger.merida.MeridaPlayer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.merida.MeridaVpn;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SnowflakesEffect;
import org.telegram.utils.settings.BooleanSetting;
import org.telegram.utils.settings.MeridaSettings;

import java.util.Date;

/**
 * MeridaGram's own settings. The first screen is a list of sections (Merida VPN, ghost mode, spy,
 * appearance, general); tapping a section opens the same screen again with only that section's switches.
 */
public class MeridaSettingsActivity extends BaseFragment implements MeridaVpn.Listener {
    public static final int PAGE_MAIN = 0;
    public static final int PAGE_VPN = 1;
    public static final int PAGE_GHOST = 2;
    public static final int PAGE_SPY = 3;
    public static final int PAGE_LOOK = 4;
    public static final int PAGE_GENERAL = 5;
    public static final int PAGE_PLAYER = 6;
    public static final int PAGE_SECURITY = 8;
    public static final int PAGE_PLUGINS = 9;

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_INFO = 2;
    private static final int TYPE_VALUE = 3;
    private static final int TYPE_SECTION = 4;
    private static final int TYPE_SNOW_PREVIEW = 5;
    private static final int TYPE_APP_HEADER = 6;
    private static final int TYPE_GAP = 7;
    /** Links: a plain grey icon, kept apart from the coloured section tiles so recycled rows don't mix them. */
    private static final int TYPE_LINK = 8;

    private static final int ROW_VPN_HEADER = 0;
    private static final int ROW_VPN_ENABLED = 1;
    private static final int ROW_VPN_CONNECTION = 2;
    private static final int ROW_VPN_SUBSCRIPTION = 3;
    private static final int ROW_VPN_RENEW = 4;
    private static final int ROW_VPN_INFO = 5;
    private static final int ROW_GENERAL_HEADER = 6;
    private static final int ROW_HIDE_STORIES = 7;
    private static final int ROW_SHOW_ID = 8;
    private static final int ROW_TIME_WITH_SECONDS = 9;
    private static final int ROW_GENERAL_INFO = 10;
    private static final int ROW_GHOST_HEADER = 11;
    private static final int ROW_GHOST = 12;
    private static final int ROW_GHOST_NO_READ = 13;
    private static final int ROW_GHOST_NO_STORIES = 14;
    private static final int ROW_GHOST_NO_ONLINE = 15;
    private static final int ROW_GHOST_NO_TYPING = 16;
    private static final int ROW_GHOST_AUTO_OFFLINE = 17;
    private static final int ROW_SEND_SILENT = 18;
    private static final int ROW_GHOST_INFO = 19;
    private static final int ROW_SPY_HEADER = 20;
    private static final int ROW_SPY_DELETED = 21;
    private static final int ROW_SPY_INFO = 22;
    private static final int ROW_LOOK_HEADER = 23;
    private static final int ROW_SNOW = 24;
    private static final int ROW_LOOK_INFO = 25;
    private static final int ROW_SECTION_VPN = 26;
    private static final int ROW_SECTION_GHOST = 27;
    private static final int ROW_SECTION_SPY = 28;
    private static final int ROW_SECTION_LOOK = 29;
    private static final int ROW_SECTION_GENERAL = 30;
    private static final int ROW_SECTIONS_INFO = 31;
    private static final int ROW_SNOW_PREVIEW = 32;
    private static final int ROW_SECTION_PLAYER = 33;
    private static final int ROW_ALLOW_SCREENSHOTS = 45;
    private static final int ROW_SESSION_GUARD = 46;
    private static final int ROW_STRIP_METADATA = 47;
    private static final int ROW_WEBAPP_PRIVACY = 48;
    private static final int ROW_VPN_PING = 49;
    private static final int ROW_APP_HEADER = 50;
    private static final int ROW_CATEGORIES_HEADER = 51;
    private static final int ROW_LINKS_HEADER = 52;
    private static final int ROW_LINK_CHANNEL = 53;
    private static final int ROW_LINK_BOT = 54;
    private static final int ROW_HIDE_FOLDER_TABS = 55;
    private static final int ROW_SECTION_CRYPTO = 56;
    private static final int ROW_SECTION_SECURITY = 57;
    private static final int ROW_CRYPTO_HEADER = 58;
    private static final int ROW_CRYPTO_ENABLED = 59;
    private static final int ROW_CRYPTO_INFO = 60;
    private static final int ROW_CRYPTO_CHATS_HEADER = 61;
    private static final int ROW_CRYPTO_CHATS_HINT = 62;
    private static final int ROW_SECURITY_HEADER = 63;
    private static final int ROW_SECURITY_INFO = 64;
    private static final int ROW_COPY_ID_MENU = 65;
    private static final int ROW_PLAYER_SLEEP = 66;
    private static final int ROW_SECURITY_GAP = 67;
    private static final int ROW_SPY_TRANSLUCENT = 68;
    private static final int ROW_SPY_MARK = 69;
    private static final int ROW_SPY_MARK_COLOR = 70;
    private static final int ROW_GHOST_STATUS = 71;
    private static final int ROW_BACKGROUND = 72;
    private static final int ROW_SECTION_PLUGINS = 73;
    private static final int ROW_PLUGINS_ENABLED = 74;
    private static final int ROW_VISUAL_ENABLED = 75;
    private static final int ROW_VISUAL_STARS = 76;
    private static final int ROW_VISUAL_GRAMS = 77;
    private static final int ROW_VISUAL_PREMIUM = 78;
    private static final int ROW_VISUAL_GIFTS = 79;
    private static final int ROW_VISUAL_USERNAME = 80;
    private static final int ROW_VISUAL_NUMBER = 81;
    private static final int ROW_VISUAL_RATING = 82;
    private static final int ROW_VISUAL_PURCHASES = 83;
    /** Installed plugins on the plugins page: ROW_PLUGIN_BASE + index in plugins. */
    private static final int ROW_PLUGIN_BASE = 9000;
    private static final int[] MARK_STYLE_NAMES = {R.string.MeridaMarkIconWord, R.string.MeridaMarkIcon, R.string.MeridaMarkWord};
    private static final int[] MARK_COLOR_NAMES = {R.string.MeridaColorDefault, R.string.MeridaColorRed, R.string.MeridaColorPink, R.string.MeridaColorPurple, R.string.MeridaColorViolet, R.string.MeridaColorBlue, R.string.MeridaColorTeal, R.string.MeridaColorGreen, R.string.MeridaColorOrange};
    /** Rows of chats with encryption on the encryption page: ROW_CRYPTO_CHAT_BASE + index in cryptoChats. */
    private static final int ROW_CRYPTO_CHAT_BASE = 2000;
    private static final int[] SLEEP_MINUTES = {15, 30, 45, 60, 90};

    /** Last speed test result in ms; -1 not run yet, -2 running, -3 failed. */
    private static int pingResult = -1;
    private static final int ROW_PLAYER_HEADER = 34;
    private static final int ROW_PLAYER_ADVANCED = 35;
    private static final int ROW_PLAYER_WHERE = 36;
    private static final int ROW_PLAYER_EQ = 37;
    private static final int ROW_PLAYER_BASS = 38;
    private static final int ROW_PLAYER_LOUDER = 39;
    private static final int ROW_PLAYER_PRELOAD = 40;
    private static final int ROW_PLAYER_INFO = 41;
    private static final int ROW_PLAYER_CHATS_HEADER = 42;
    private static final int ROW_PLAYER_CHATS_EMPTY = 43;
    private static final int ROW_PLAYER_CHATS_HINT = 44;
    /** Rows of the chosen chats on the player page: ROW_PLAYER_CHAT_BASE + index in playerChats. */
    private static final int ROW_PLAYER_CHAT_BASE = 1000;

    private static final int[] EQ_NAMES = {R.string.MeridaPlayerEqNormal, R.string.MeridaPlayerEqBass, R.string.MeridaPlayerEqRock, R.string.MeridaPlayerEqPop, R.string.MeridaPlayerEqVoice, R.string.MeridaPlayerEqElectro};
    private static final int[] MODE_NAMES = {R.string.MeridaPlayerModeAll, R.string.MeridaPlayerModeExcept, R.string.MeridaPlayerModeOnly};

    private final int page;
    private int[] rows;
    private final java.util.ArrayList<Long> playerChats = new java.util.ArrayList<>();
    private final java.util.ArrayList<Long> cryptoChats = new java.util.ArrayList<>();
    private final java.util.ArrayList<org.telegram.messenger.merida.MeridaPlugins.Info> plugins = new java.util.ArrayList<>();
    private RecyclerListView listView;
    private ListAdapter adapter;

    public MeridaSettingsActivity() {
        this(PAGE_MAIN);
    }

    public MeridaSettingsActivity(int page) {
        super(pageArgs(page));
        this.page = page;
        this.rows = rowsFor(page);
        if (page == PAGE_PLAYER) {
            buildPlayerRows();
        } else if (page == PAGE_SECURITY) {
            buildSecurityRows();
        } else if (page == PAGE_PLUGINS) {
            buildPluginRows();
        }
    }

    private static boolean isPluginRow(int row) {
        return row >= ROW_PLUGIN_BASE;
    }

    /** Plugins page: the main switch, then the installed plugins (each can be switched off or removed). */
    private void buildPluginRows() {
        plugins.clear();
        final java.util.ArrayList<Integer> list = new java.util.ArrayList<>();
        list.add(hdr(R.string.MeridaPluginsHeader));
        list.add(ROW_PLUGINS_ENABLED);
        list.add(note(R.string.MeridaInfoPlugins));
        if (MeridaSettings.pluginsEnabled.get()) {
            plugins.addAll(org.telegram.messenger.merida.MeridaPlugins.list());
            list.add(hdr(R.string.MeridaPluginsInstalled));
            if (plugins.isEmpty()) {
                list.add(note(R.string.MeridaPluginsEmpty));
            } else {
                for (int i = 0; i < plugins.size(); i++) {
                    list.add(ROW_PLUGIN_BASE + i);
                }
                list.add(note(R.string.MeridaPluginsHint));
            }
        }
        rows = new int[list.size()];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = list.get(i);
        }
    }

    private void refreshPluginsPage() {
        buildPluginRows();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private String pluginSubtitle(org.telegram.messenger.merida.MeridaPlugins.Info info) {
        final String error = org.telegram.messenger.merida.MeridaPlugins.error(info.id);
        if (error != null && org.telegram.messenger.merida.MeridaPlugins.isEnabled(info.id)) {
            final String[] lines = error.trim().split("\n");
            return LocaleController.formatString(R.string.MeridaPluginError, lines[lines.length - 1]);
        }
        final StringBuilder sb = new StringBuilder();
        if (!android.text.TextUtils.isEmpty(info.version)) {
            sb.append(info.version);
        }
        if (!android.text.TextUtils.isEmpty(info.author)) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(info.author);
        }
        if (!android.text.TextUtils.isEmpty(info.description)) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(info.description);
        }
        return sb.toString();
    }

    private void askRemovePlugin(org.telegram.messenger.merida.MeridaPlugins.Info info) {
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.MeridaPluginRemove));
        builder.setMessage(LocaleController.formatString(R.string.MeridaPluginRemoveInfo, info.name));
        builder.setPositiveButton(LocaleController.getString(R.string.MeridaPluginRemove), (d, w) -> {
            org.telegram.messenger.merida.MeridaPlugins.remove(info.id);
            refreshPluginsPage();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private static boolean isPlayerChatRow(int row) {
        return row >= ROW_PLAYER_CHAT_BASE && row < ROW_CRYPTO_CHAT_BASE;
    }

    private static boolean isCryptoChatRow(int row) {
        return row >= ROW_CRYPTO_CHAT_BASE && row < ROW_HDR_BASE;
    }

    private static Bundle pageArgs(int page) {
        final Bundle args = new Bundle();
        args.putInt("page", page);
        return args;
    }

    /** Group titles and grey notes under groups, like exteraGram: rows ROW_HDR_BASE + i and ROW_NOTE_BASE + i. */
    private static final int ROW_HDR_BASE = 3000;
    private static final int ROW_NOTE_BASE = 5000;
    private static final java.util.ArrayList<Integer> HEADERS = new java.util.ArrayList<>();
    private static final java.util.ArrayList<Integer> NOTES = new java.util.ArrayList<>();

    private static int hdr(int res) {
        int i = HEADERS.indexOf(res);
        if (i < 0) {
            HEADERS.add(res);
            i = HEADERS.size() - 1;
        }
        return ROW_HDR_BASE + i;
    }

    private static int note(int res) {
        int i = NOTES.indexOf(res);
        if (i < 0) {
            NOTES.add(res);
            i = NOTES.size() - 1;
        }
        return ROW_NOTE_BASE + i;
    }

    /** Which rows each screen shows, top to bottom: small groups, each with a title and a note under it. */
    private static int[] rowsFor(int page) {
        switch (page) {
            case PAGE_VPN:
                return new int[]{hdr(R.string.MeridaVpnHeader), ROW_VPN_ENABLED, ROW_VPN_CONNECTION, ROW_VPN_PING, ROW_VPN_SUBSCRIPTION, ROW_VPN_INFO};
            case PAGE_GHOST:
                return new int[]{
                        hdr(R.string.MeridaGhostHeader), ROW_GHOST, ROW_GHOST_STATUS, note(R.string.MeridaInfoGhostMain),
                        hdr(R.string.MeridaHdrGhostHide), ROW_GHOST_NO_READ, ROW_GHOST_NO_STORIES, ROW_GHOST_NO_ONLINE, ROW_GHOST_NO_TYPING, ROW_GHOST_AUTO_OFFLINE, note(R.string.MeridaInfoGhostHide),
                        hdr(R.string.MeridaHdrSending), ROW_SEND_SILENT, note(R.string.MeridaInfoSendSilent)};
            case PAGE_SPY:
                return new int[]{
                        hdr(R.string.MeridaHdrDeleted), ROW_SPY_DELETED, note(R.string.MeridaSpyInfo),
                        hdr(R.string.MeridaHdrDeletedLook), ROW_SPY_TRANSLUCENT, ROW_SPY_MARK, ROW_SPY_MARK_COLOR, note(R.string.MeridaInfoDeletedLook)};
            case PAGE_LOOK:
                return new int[]{
                        hdr(R.string.MeridaHdrChatList), ROW_SNOW_PREVIEW, ROW_SNOW, ROW_HIDE_FOLDER_TABS, ROW_HIDE_STORIES, note(R.string.MeridaInfoChatList),
                        hdr(R.string.MeridaHdrMessages), ROW_TIME_WITH_SECONDS, note(R.string.MeridaInfoSeconds),
                        hdr(R.string.MeridaHdrVisual), ROW_VISUAL_ENABLED, ROW_VISUAL_STARS, ROW_VISUAL_GRAMS, ROW_VISUAL_GIFTS, ROW_VISUAL_USERNAME, ROW_VISUAL_NUMBER, ROW_VISUAL_RATING, ROW_VISUAL_PURCHASES, ROW_VISUAL_PREMIUM, note(R.string.MeridaInfoVisual)};
            case PAGE_GENERAL:
                return new int[]{
                        hdr(R.string.MeridaHdrId), ROW_SHOW_ID, ROW_COPY_ID_MENU, note(R.string.MeridaInfoId),
                        hdr(R.string.MeridaHdrScreenshots), ROW_ALLOW_SCREENSHOTS, note(R.string.MeridaInfoScreenshots),
                        hdr(R.string.MeridaHdrNotifications), ROW_BACKGROUND, note(R.string.MeridaInfoBackground)};
            default:
                return new int[]{ROW_APP_HEADER, ROW_CATEGORIES_HEADER, ROW_SECTION_GENERAL, ROW_SECTION_LOOK, ROW_SECTION_VPN, ROW_SECTION_GHOST, ROW_SECTION_SPY, ROW_SECTION_PLAYER, ROW_SECTION_SECURITY,
                        ROW_SECTION_PLUGINS, ROW_SECURITY_GAP, ROW_LINKS_HEADER, ROW_LINK_CHANNEL};
        }
    }

    /** The player page lists the chosen chats, so its rows change when the list or the mode changes. */
    private void buildPlayerRows() {
        playerChats.clear();
        final java.util.ArrayList<Integer> list = new java.util.ArrayList<>();
        list.add(hdr(R.string.MeridaPlayerHeader));
        list.add(ROW_PLAYER_ADVANCED);
        list.add(note(R.string.MeridaInfoPlayerMain));
        list.add(hdr(R.string.MeridaHdrSound));
        list.add(ROW_PLAYER_WHERE);
        list.add(ROW_PLAYER_EQ);
        list.add(ROW_PLAYER_BASS);
        list.add(ROW_PLAYER_LOUDER);
        list.add(note(R.string.MeridaInfoSound));
        list.add(hdr(R.string.MeridaHdrPlayback));
        list.add(ROW_PLAYER_PRELOAD);
        list.add(ROW_PLAYER_SLEEP);
        list.add(note(R.string.MeridaInfoPlayback));
        if (MeridaSettings.playerMode.get() != MeridaPlayer.MODE_ALL) {
            list.add(ROW_PLAYER_CHATS_HEADER);
            playerChats.addAll(MeridaPlayer.getChosenChats());
            if (playerChats.isEmpty()) {
                list.add(ROW_PLAYER_CHATS_EMPTY);
            } else {
                for (int i = 0; i < playerChats.size(); i++) {
                    list.add(ROW_PLAYER_CHAT_BASE + i);
                }
                list.add(ROW_PLAYER_CHATS_HINT);
            }
        }
        rows = new int[list.size()];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = list.get(i);
        }
    }

    /** Security page: its switches, then encryption with the list of chats that have a password. */
    private void buildSecurityRows() {
        MeridaCrypto.isFeatureOn();
        cryptoChats.clear();
        cryptoChats.addAll(MeridaCrypto.chatsWithPassword(currentAccount));
        final java.util.ArrayList<Integer> list = new java.util.ArrayList<>();
        list.add(hdr(R.string.MeridaHdrAccount));
        list.add(ROW_SESSION_GUARD);
        list.add(note(R.string.MeridaDescSessionGuard));
        list.add(hdr(R.string.MeridaHdrPrivacy));
        list.add(ROW_STRIP_METADATA);
        list.add(ROW_WEBAPP_PRIVACY);
        list.add(note(R.string.MeridaInfoPrivacy));
        list.add(hdr(R.string.MeridaCryptoHeader));
        list.add(ROW_CRYPTO_ENABLED);
        list.add(note(R.string.MeridaDescCrypto));
        if (!cryptoChats.isEmpty()) {
            list.add(ROW_CRYPTO_CHATS_HEADER);
            for (int i = 0; i < cryptoChats.size(); i++) {
                list.add(ROW_CRYPTO_CHAT_BASE + i);
            }
            list.add(ROW_CRYPTO_CHATS_HINT);
        }
        rows = new int[list.size()];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = list.get(i);
        }
    }

    private void refreshCryptoPage() {
        buildSecurityRows();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    /** Pause or resume encryption in one chat, or forget its password. */
    private void showCryptoChatOptions(long dialogId) {
        if (getParentActivity() == null) {
            return;
        }
        final boolean on = MeridaCrypto.isChatOn(currentAccount, dialogId);
        final CharSequence[] items = {
                LocaleController.getString(on ? R.string.MeridaCryptoDisable : R.string.MeridaCryptoResume),
                LocaleController.getString(R.string.MeridaCryptoForget)
        };
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(chatName(dialogId));
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                MeridaCrypto.setOn(currentAccount, dialogId, !on);
                refreshCryptoPage();
            } else {
                final AlertDialog.Builder confirm = new AlertDialog.Builder(getParentActivity());
                confirm.setTitle(LocaleController.getString(R.string.MeridaCryptoForget));
                confirm.setMessage(LocaleController.getString(R.string.MeridaCryptoForgetInfo));
                confirm.setPositiveButton(LocaleController.getString(R.string.MeridaCryptoForget), (d, w) -> {
                    MeridaCrypto.forget(currentAccount, dialogId);
                    refreshCryptoPage();
                });
                confirm.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                showDialog(confirm.create());
            }
        });
        showDialog(builder.create());
    }

    private void showSleepChoice() {
        if (getParentActivity() == null) {
            return;
        }
        final CharSequence[] items = new CharSequence[SLEEP_MINUTES.length + 1];
        items[0] = LocaleController.getString(R.string.MeridaPlayerSleepOff);
        for (int i = 0; i < SLEEP_MINUTES.length; i++) {
            items[i + 1] = LocaleController.formatString(R.string.MeridaPlayerSleepIn, SLEEP_MINUTES[i]);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.MeridaPlayerSleep));
        builder.setItems(items, (dialog, which) -> {
            MeridaPlayer.setSleepTimer(which == 0 ? 0 : SLEEP_MINUTES[which - 1]);
            refreshPlayerPage();
        });
        showDialog(builder.create());
    }

    /** «Work in the background»: Telegram's keep-alive service and background connection together. */
    private static boolean backgroundOn() {
        final android.content.SharedPreferences global = MessagesController.getGlobalNotificationsSettings();
        final boolean service = global.contains("pushService") ? global.getBoolean("pushService", true)
                : MessagesController.getMainSettings(UserConfig.selectedAccount).getBoolean("keepAliveService", false);
        final boolean connection = MessagesController.getNotificationsSettings(UserConfig.selectedAccount)
                .getBoolean("pushConnection", MessagesController.getInstance(UserConfig.selectedAccount).backgroundConnection);
        return service && connection;
    }

    private static void setBackground(boolean on) {
        MessagesController.getGlobalNotificationsSettings().edit().putBoolean("pushService", on).commit();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                continue;
            }
            MessagesController.getNotificationsSettings(a).edit().putBoolean("pushService", on).putBoolean("pushConnection", on).commit();
            org.telegram.tgnet.ConnectionsManager.getInstance(a).setPushConnectionEnabled(on);
        }
        org.telegram.messenger.ApplicationLoader.startPushService();
    }

    private static CharSequence colorName(int i) {
        final int color = i > 0 && i < org.telegram.messenger.merida.MeridaSpy.MARK_COLORS.length ? org.telegram.messenger.merida.MeridaSpy.MARK_COLORS[i] : 0;
        final android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        if (color != 0) {
            sb.append("● ");
            sb.setSpan(new android.text.style.ForegroundColorSpan(color), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        sb.append(LocaleController.getString(MARK_COLOR_NAMES[i]));
        return sb;
    }

    private void showMarkColorChoice() {
        if (getParentActivity() == null) {
            return;
        }
        final int current = MeridaSettings.spyMarkColor.get();
        final CharSequence[] items = new CharSequence[MARK_COLOR_NAMES.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = i == current ? android.text.TextUtils.concat("✓ ", colorName(i)) : colorName(i);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.MeridaMarkColor));
        builder.setItems(items, (dialog, which) -> {
            MeridaSettings.spyMarkColor.set(which);
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });
        showDialog(builder.create());
    }

    /** Asks for a whole number (for the visual stars and GRAM); 0 turns that number off. */
    private void showNumberInput(int title, int current, java.util.function.IntConsumer onDone) {
        if (getParentActivity() == null) {
            return;
        }
        final Context context = getParentActivity();
        final org.telegram.ui.Components.EditTextBoldCursor input = new org.telegram.ui.Components.EditTextBoldCursor(context);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setHint("0");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(9)});
        input.setSingleLine(true);
        input.setBackground(null);
        input.setLineColors(Theme.getColor(Theme.key_dialogInputField), Theme.getColor(Theme.key_dialogInputFieldActivated), Theme.getColor(Theme.key_text_RedBold));
        input.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        if (current > 0) {
            input.setText(String.valueOf(current));
            input.setSelection(input.length());
        }
        final android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 4));
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(title));
        builder.setMessage(LocaleController.getString(R.string.MeridaVisualNumberHint));
        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
            int value = 0;
            try {
                value = Integer.parseInt(input.getText() == null ? "0" : input.getText().toString().trim());
            } catch (NumberFormatException ignore) {
            }
            onDone.accept(Math.max(0, value));
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    /** Visual NFT gifts: add one by its link, or tap one to remove it. */
    private void showVisualGifts() {
        if (getParentActivity() == null) {
            return;
        }
        final java.util.List<String> slugs = org.telegram.messenger.merida.MeridaVisualGifts.slugs();
        final CharSequence[] items = new CharSequence[slugs.size() + 1];
        items[0] = LocaleController.getString(R.string.MeridaVisualGiftAdd);
        for (int i = 0; i < slugs.size(); i++) {
            final org.telegram.tgnet.tl.TL_stars.StarGift any = org.telegram.messenger.merida.MeridaVisualGifts.loadAny(slugs.get(i));
            final String label = any instanceof org.telegram.tgnet.tl.TL_stars.TL_starGiftUnique
                    ? any.title + " #" + ((org.telegram.tgnet.tl.TL_stars.TL_starGiftUnique) any).num
                    : any != null ? LocaleController.formatString(R.string.MeridaVisualSendGift, org.telegram.messenger.merida.MeridaVisual.formatNumber(any.stars)) : slugs.get(i);
            items[i + 1] = label + "  ✕";
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.MeridaVisualGifts));
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                askVisualGiftLink();
            } else {
                org.telegram.messenger.merida.MeridaVisualGifts.remove(slugs.get(which - 1));
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        });
        showDialog(builder.create());
    }

    private void askVisualGiftLink() {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final org.telegram.ui.Components.EditTextBoldCursor input = new org.telegram.ui.Components.EditTextBoldCursor(context);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setHint("t.me/nft/PlushPepe-1");
        input.setSingleLine(true);
        input.setBackground(null);
        input.setLineColors(Theme.getColor(Theme.key_dialogInputField), Theme.getColor(Theme.key_dialogInputFieldActivated), Theme.getColor(Theme.key_text_RedBold));
        input.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        final android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 4));
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.MeridaVisualGiftAdd));
        builder.setMessage(LocaleController.getString(R.string.MeridaVisualGiftHint));
        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
            final String slug = org.telegram.messenger.merida.MeridaVisualGifts.parseSlug(input.getText() == null ? "" : input.getText().toString());
            if (slug == null) {
                android.widget.Toast.makeText(context, LocaleController.getString(R.string.MeridaVisualGiftBadLink), android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            org.telegram.messenger.merida.MeridaVisualGifts.add(currentAccount, slug, title -> {
                android.widget.Toast.makeText(context, title != null
                        ? LocaleController.formatString(R.string.MeridaVisualGiftAdded, title)
                        : LocaleController.getString(R.string.MeridaVisualGiftNotFound), android.widget.Toast.LENGTH_LONG).show();
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            });
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    /** Asks for a line of text; an empty answer turns the value off. */
    private void showTextInput(int title, int message, String hint, String current, int inputType, java.util.function.Consumer<String> onDone) {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final org.telegram.ui.Components.EditTextBoldCursor input = new org.telegram.ui.Components.EditTextBoldCursor(context);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setHint(hint);
        input.setInputType(inputType);
        input.setSingleLine(true);
        input.setBackground(null);
        input.setLineColors(Theme.getColor(Theme.key_dialogInputField), Theme.getColor(Theme.key_dialogInputFieldActivated), Theme.getColor(Theme.key_text_RedBold));
        input.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        if (current != null) {
            input.setText(current);
            input.setSelection(input.length());
        }
        final android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 4));
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(title));
        builder.setMessage(LocaleController.getString(message));
        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> onDone.accept(input.getText() == null ? "" : input.getText().toString().trim()));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void toast(int res) {
        if (getParentActivity() != null) {
            android.widget.Toast.makeText(getParentActivity(), LocaleController.getString(res), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void askVisualUsername() {
        showTextInput(R.string.MeridaVisualUsername, R.string.MeridaVisualUsernameHint, "username", MeridaSettings.visualUsername.get(),
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, text -> {
                    final String username = text.startsWith("@") ? text.substring(1) : text;
                    if (username.isEmpty()) {
                        MeridaSettings.visualUsername.set(null);
                        adapter.notifyDataSetChanged();
                        return;
                    }
                    org.telegram.messenger.merida.MeridaVisual.checkUsername(currentAccount, username, result -> {
                        if (result == org.telegram.messenger.merida.MeridaVisual.CHECK_OK) {
                            MeridaSettings.visualUsername.set(username);
                            adapter.notifyDataSetChanged();
                        } else {
                            toast(result == org.telegram.messenger.merida.MeridaVisual.CHECK_TAKEN ? R.string.MeridaVisualUsernameTaken
                                    : result == org.telegram.messenger.merida.MeridaVisual.CHECK_INVALID ? R.string.MeridaVisualUsernameInvalid : R.string.MeridaVisualUsernameError);
                        }
                    });
                });
    }

    private void askVisualNumber() {
        showTextInput(R.string.MeridaVisualNumber, R.string.MeridaVisualNumberRule, "+888 0000 0000", null,
                android.text.InputType.TYPE_CLASS_PHONE, text -> {
                    final String digits = text.replaceAll("[^0-9]", "");
                    if (digits.isEmpty()) {
                        MeridaSettings.visualNumber.set(null);
                        adapter.notifyDataSetChanged();
                    } else if (org.telegram.messenger.merida.MeridaVisual.isAnonymousNumber(digits)) {
                        MeridaSettings.visualNumber.set(digits);
                        adapter.notifyDataSetChanged();
                    } else {
                        toast(R.string.MeridaVisualNumberOnly888);
                    }
                });
    }

    private static String visualNumberText(int value) {
        return value > 0 ? org.telegram.messenger.merida.MeridaVisual.formatNumber(value) : LocaleController.getString(R.string.NotificationsOff);
    }

    private static String sleepText() {
        final int left = MeridaPlayer.sleepMinutesLeft();
        return left > 0 ? LocaleController.formatString(R.string.MeridaPlayerSleepIn, left) : LocaleController.getString(R.string.NotificationsOff);
    }

    private void refreshPlayerPage() {
        buildPlayerRows();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private String chatName(long dialogId) {
        final MessagesController controller = MessagesController.getInstance(currentAccount);
        if (DialogObject.isUserDialog(dialogId)) {
            final TLRPC.User user = controller.getUser(dialogId);
            if (user != null) {
                return UserObject.getUserName(user);
            }
        } else {
            final TLRPC.Chat chat = controller.getChat(-dialogId);
            if (chat != null && chat.title != null) {
                return chat.title;
            }
        }
        return String.valueOf(dialogId);
    }

    private void showChoice(int title, int[] names, int selected, java.util.function.IntConsumer onChosen) {
        if (getParentActivity() == null) {
            return;
        }
        final CharSequence[] items = new CharSequence[names.length];
        for (int i = 0; i < names.length; i++) {
            items[i] = (i == selected ? "✓ " : "") + LocaleController.getString(names[i]);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(title));
        builder.setItems(items, (dialog, which) -> onChosen.accept(which));
        showDialog(builder.create());
    }

    /** Measures how fast Telegram answers right now (through Merida VPN when it is on): best of 3 tries. */
    private void runPingTest() {
        if (pingResult == -2) {
            return;
        }
        pingResult = -2;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        final int[] best = {Integer.MAX_VALUE};
        final int[] left = {3};
        for (int i = 0; i < 3; i++) {
            final long start = android.os.SystemClock.elapsedRealtime();
            org.telegram.tgnet.ConnectionsManager.getInstance(currentAccount).sendRequest(new TLRPC.TL_help_getNearestDc(), (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response != null && error == null) {
                    best[0] = Math.min(best[0], (int) (android.os.SystemClock.elapsedRealtime() - start));
                }
                if (--left[0] == 0) {
                    pingResult = best[0] == Integer.MAX_VALUE ? -3 : best[0];
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                }
            }));
        }
    }

    private int positionOf(int row) {
        for (int i = 0; i < rows.length; i++) {
            if (rows[i] == row) {
                return i;
            }
        }
        return -1;
    }

    private boolean showsVpn() {
        return page == PAGE_MAIN || page == PAGE_VPN;
    }

    @Override
    public boolean onFragmentCreate() {
        if (page == PAGE_PLUGINS) {
            org.telegram.messenger.merida.MeridaPlugins.listener = this::refreshPluginsPage;
        }
        if (showsVpn()) {
            MeridaVpn.addListener(this);
            MeridaVpn.refresh();
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        if (page == PAGE_PLUGINS) {
            org.telegram.messenger.merida.MeridaPlugins.listener = null;
        }
        if (showsVpn()) {
            MeridaVpn.removeListener(this);
        }
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        // the section list shows «on / off» next to each section; refresh it after coming back from one
        if (page == PAGE_SECURITY) {
            buildSecurityRows();
        } else if (page == PAGE_PLUGINS) {
            buildPluginRows();
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onMeridaVpnChanged() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        // our own title always, not Telegram's «Connecting…»
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(titleForPage(page)));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        // in a dark theme the background is pure black, like exteraGram; the cards keep the theme's colour
        frameLayout.setBackgroundColor(blackBackground() ? 0xFF000000 : Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setSections();
        if (blackBackground()) {
            actionBar.setBackgroundColor(0xFF000000);
        } else {
            actionBar.setAdaptiveBackground(listView);
        }
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < rows.length) {
                onRowClicked(view, rows[position]);
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (position >= 0 && position < rows.length && isPluginRow(rows[position])) {
                final int i = rows[position] - ROW_PLUGIN_BASE;
                if (i < plugins.size()) {
                    askRemovePlugin(plugins.get(i));
                    return true;
                }
            }
            return false;
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT
        ));
        return fragmentView;
    }

    private static boolean blackBackground() {
        return Theme.isCurrentThemeDark();
    }

    @Override
    public int getNavigationBarColor() {
        return blackBackground() ? 0xFF000000 : super.getNavigationBarColor();
    }

    private static int titleForPage(int page) {
        switch (page) {
            case PAGE_VPN:
                return R.string.MeridaVpnHeader;
            case PAGE_GHOST:
                return R.string.MeridaGhostMode;
            case PAGE_SPY:
                return R.string.MeridaSpyHeader;
            case PAGE_LOOK:
                return R.string.MeridaLookHeader;
            case PAGE_GENERAL:
                return R.string.MeridaSettingsGeneral;
            case PAGE_PLAYER:
                return R.string.MeridaPlayerHeader;
            case PAGE_SECURITY:
                return R.string.MeridaSecurityHeader;
            case PAGE_PLUGINS:
                return R.string.MeridaPluginsHeader;
            default:
                return R.string.MeridaSettings;
        }
    }

    private static int pageForSection(int row) {
        switch (row) {
            case ROW_SECTION_VPN:
                return PAGE_VPN;
            case ROW_SECTION_GHOST:
                return PAGE_GHOST;
            case ROW_SECTION_SPY:
                return PAGE_SPY;
            case ROW_SECTION_LOOK:
                return PAGE_LOOK;
            case ROW_SECTION_GENERAL:
                return PAGE_GENERAL;
            case ROW_SECTION_PLAYER:
                return PAGE_PLAYER;
            case ROW_SECTION_SECURITY:
                return PAGE_SECURITY;
            case ROW_SECTION_PLUGINS:
                return PAGE_PLUGINS;
            default:
                return -1;
        }
    }

    private void onRowClicked(View view, int position) {
        final int section = pageForSection(position);
        if (section >= 0) {
            presentFragment(new MeridaSettingsActivity(section));
            return;
        }
        if (position == ROW_VPN_ENABLED) {
            final boolean enabled = !MeridaSettings.vpnEnabled.get();
            MeridaVpn.setEnabled(enabled);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            adapter.notifyDataSetChanged();
            return;
        }
        if (position == ROW_PLAYER_WHERE) {
            showChoice(R.string.MeridaPlayerWhere, MODE_NAMES, MeridaSettings.playerMode.get(), which -> {
                MeridaSettings.playerMode.set(which);
                MeridaPlayer.reapply();
                refreshPlayerPage();
            });
            return;
        }
        if (position == ROW_PLAYER_EQ) {
            showChoice(R.string.MeridaPlayerEqualizer, EQ_NAMES, MeridaSettings.playerEqPreset.get(), which -> {
                MeridaSettings.playerEqPreset.set(which);
                MeridaPlayer.reapply();
                refreshPlayerPage();
            });
            return;
        }
        if (position == ROW_PLAYER_SLEEP) {
            showSleepChoice();
            return;
        }
        if (position == ROW_VISUAL_GIFTS) {
            showVisualGifts();
            return;
        }
        if (position == ROW_VISUAL_USERNAME) {
            askVisualUsername();
            return;
        }
        if (position == ROW_VISUAL_NUMBER) {
            askVisualNumber();
            return;
        }
        if (position == ROW_VISUAL_RATING) {
            showNumberInput(R.string.MeridaVisualRating, MeridaSettings.visualRating.get(), value -> {
                MeridaSettings.visualRating.set(Math.min(100, value));
                adapter.notifyDataSetChanged();
            });
            return;
        }
        if (position == ROW_VISUAL_STARS || position == ROW_VISUAL_GRAMS) {
            final boolean stars = position == ROW_VISUAL_STARS;
            showNumberInput(stars ? R.string.MeridaVisualStars : R.string.MeridaVisualGrams,
                    (stars ? MeridaSettings.visualStars : MeridaSettings.visualGrams).get(), value -> {
                        (stars ? MeridaSettings.visualStars : MeridaSettings.visualGrams).set(value);
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    });
            return;
        }
        if (position == ROW_SPY_MARK) {
            showChoice(R.string.MeridaMarkStyle, MARK_STYLE_NAMES, MeridaSettings.spyMarkStyle.get(), which -> {
                MeridaSettings.spyMarkStyle.set(which);
                adapter.notifyDataSetChanged();
            });
            return;
        }
        if (position == ROW_SPY_MARK_COLOR) {
            showMarkColorChoice();
            return;
        }
        if (isPluginRow(position)) {
            final int i = position - ROW_PLUGIN_BASE;
            if (i < plugins.size()) {
                final org.telegram.messenger.merida.MeridaPlugins.Info info = plugins.get(i);
                final boolean on = !org.telegram.messenger.merida.MeridaPlugins.isEnabled(info.id);
                org.telegram.messenger.merida.MeridaPlugins.setEnabled(info.id, on);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(on);
                }
            }
            return;
        }
        if (position == ROW_PLUGINS_ENABLED) {
            final boolean on = !MeridaSettings.pluginsEnabled.get();
            MeridaSettings.pluginsEnabled.set(on);
            org.telegram.messenger.merida.MeridaPlugins.reloadAsync();
            refreshPluginsPage();
            return;
        }
        if (position == ROW_BACKGROUND) {
            final boolean on = !backgroundOn();
            setBackground(on);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(on);
            }
            return;
        }
        if (isCryptoChatRow(position)) {
            final int i = position - ROW_CRYPTO_CHAT_BASE;
            if (i >= 0 && i < cryptoChats.size()) {
                showCryptoChatOptions(cryptoChats.get(i));
            }
            return;
        }
        if (isPlayerChatRow(position)) {
            final int i = position - ROW_PLAYER_CHAT_BASE;
            if (i >= 0 && i < playerChats.size()) {
                MeridaPlayer.removeChosen(playerChats.get(i));
                refreshPlayerPage();
            }
            return;
        }
        if (position == ROW_LINK_CHANNEL) {
            Browser.openUrl(getParentActivity(), "https://t.me/MeridaGram");
            return;
        }
        if (position == ROW_VPN_PING) {
            runPingTest();
            return;
        }
        final BooleanSetting setting = settingForRow(position);
        if (setting == null || (isGhostPart(position) && !MeridaSettings.ghostMode.get())) {
            return;
        }
        final boolean enabled = setting.toggle();
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(enabled);
        }
        if (position == ROW_GHOST) {
            // the parts below follow the master switch
            final int first = positionOf(ROW_GHOST_NO_READ);
            if (first >= 0) {
                adapter.notifyItemRangeChanged(first, ROW_GHOST_AUTO_OFFLINE - ROW_GHOST_NO_READ + 1);
            }
        }
        if (position == ROW_PLAYER_ADVANCED || position == ROW_PLAYER_BASS || position == ROW_PLAYER_LOUDER) {
            MeridaPlayer.reapply();
        }
        if (position == ROW_CRYPTO_ENABLED) {
            refreshCryptoPage();
        }
        if (position == ROW_HIDE_FOLDER_TABS) {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.dialogFiltersUpdated);
                }
            }
        }
        if (position == ROW_HIDE_STORIES) {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.storiesUpdated);
                }
            }
        }
    }

    private static BooleanSetting settingForRow(int position) {
        switch (position) {
            case ROW_VPN_ENABLED:
                return MeridaSettings.vpnEnabled;
            case ROW_HIDE_STORIES:
                return MeridaSettings.hideStories;
            case ROW_SHOW_ID:
                return MeridaSettings.showId;
            case ROW_COPY_ID_MENU:
                return MeridaSettings.copyIdMenu;
            case ROW_CRYPTO_ENABLED:
                return MeridaSettings.cryptoEnabled;
            case ROW_HIDE_FOLDER_TABS:
                return MeridaSettings.hideFolderTabs;
            case ROW_SPY_TRANSLUCENT:
                return MeridaSettings.spyTranslucent;
            case ROW_PLUGINS_ENABLED:
                return MeridaSettings.pluginsEnabled;
            case ROW_VISUAL_ENABLED:
                return MeridaSettings.visualEnabled;
            case ROW_VISUAL_PREMIUM:
                return MeridaSettings.visualPremium;
            case ROW_VISUAL_PURCHASES:
                return MeridaSettings.visualPurchases;
            case ROW_GHOST_STATUS:
                return MeridaSettings.ghostStatus;
            case ROW_TIME_WITH_SECONDS:
                return MeridaSettings.timeWithSeconds;
            case ROW_GHOST:
                return MeridaSettings.ghostMode;
            case ROW_GHOST_NO_READ:
                return MeridaSettings.ghostNoRead;
            case ROW_GHOST_NO_STORIES:
                return MeridaSettings.ghostNoStories;
            case ROW_GHOST_NO_ONLINE:
                return MeridaSettings.ghostNoOnline;
            case ROW_GHOST_NO_TYPING:
                return MeridaSettings.ghostNoTyping;
            case ROW_GHOST_AUTO_OFFLINE:
                return MeridaSettings.ghostAutoOffline;
            case ROW_SEND_SILENT:
                return MeridaSettings.sendSilent;
            case ROW_SPY_DELETED:
                return MeridaSettings.spySaveDeleted;
            case ROW_SNOW:
                return MeridaSettings.snow;
            case ROW_PLAYER_ADVANCED:
                return MeridaSettings.playerAdvanced;
            case ROW_ALLOW_SCREENSHOTS:
                return MeridaSettings.allowScreenshots;
            case ROW_SESSION_GUARD:
                return MeridaSettings.sessionGuard;
            case ROW_STRIP_METADATA:
                return MeridaSettings.stripPhotoMetadata;
            case ROW_WEBAPP_PRIVACY:
                return MeridaSettings.webAppPrivacy;
            case ROW_PLAYER_BASS:
                return MeridaSettings.playerBassBoost;
            case ROW_PLAYER_LOUDER:
                return MeridaSettings.playerLouder;
            case ROW_PLAYER_PRELOAD:
                return MeridaSettings.playerPreload;
            default:
                return null;
        }
    }

    /** A short grey line under the switch's name, like exteraGram; the longer note is under the group. */
    private static int descForRow(int position) {
        switch (position) {
            case ROW_SNOW:
                return R.string.MeridaSubSnow;
            case ROW_TIME_WITH_SECONDS:
                return R.string.MeridaSubSeconds;
            case ROW_COPY_ID_MENU:
                return R.string.MeridaSubCopyId;
            case ROW_GHOST_AUTO_OFFLINE:
                return R.string.MeridaSubAutoOffline;
            case ROW_GHOST_STATUS:
                return R.string.MeridaSubGhostStatus;
            case ROW_BACKGROUND:
                return R.string.MeridaSubBackground;
            case ROW_VISUAL_ENABLED:
                return R.string.MeridaSubVisual;
            case ROW_VISUAL_PREMIUM:
                return R.string.MeridaSubVisualPremium;
            case ROW_VISUAL_PURCHASES:
                return R.string.MeridaSubVisualPurchases;
            default:
                return 0;
        }
    }

    private static boolean isGhostPart(int position) {
        return position >= ROW_GHOST_NO_READ && position <= ROW_GHOST_AUTO_OFFLINE;
    }

    private static int titleForRow(int position) {
        switch (position) {
            case ROW_VPN_ENABLED:
                return R.string.MeridaVpnEnabled;
            case ROW_HIDE_STORIES:
                return R.string.MeridaHideStories;
            case ROW_SHOW_ID:
                return R.string.MeridaShowId;
            case ROW_COPY_ID_MENU:
                return R.string.MeridaCopyIdMenu;
            case ROW_CRYPTO_ENABLED:
                return R.string.MeridaCryptoFeature;
            case ROW_HIDE_FOLDER_TABS:
                return R.string.MeridaHideFolderTabs;
            case ROW_SPY_TRANSLUCENT:
                return R.string.MeridaSpyTranslucent;
            case ROW_PLUGINS_ENABLED:
                return R.string.MeridaPluginsEnable;
            case ROW_VISUAL_ENABLED:
                return R.string.MeridaVisualEnable;
            case ROW_VISUAL_PREMIUM:
                return R.string.MeridaVisualPremium;
            case ROW_VISUAL_PURCHASES:
                return R.string.MeridaVisualPurchases;
            case ROW_GHOST_STATUS:
                return R.string.MeridaGhostStatus;
            case ROW_BACKGROUND:
                return R.string.MeridaBackground;
            case ROW_GHOST:
                return R.string.MeridaGhostMode;
            case ROW_GHOST_NO_READ:
                return R.string.MeridaGhostNoRead;
            case ROW_GHOST_NO_STORIES:
                return R.string.MeridaGhostNoStories;
            case ROW_GHOST_NO_ONLINE:
                return R.string.MeridaGhostNoOnline;
            case ROW_GHOST_NO_TYPING:
                return R.string.MeridaGhostNoTyping;
            case ROW_GHOST_AUTO_OFFLINE:
                return R.string.MeridaGhostAutoOffline;
            case ROW_SEND_SILENT:
                return R.string.MeridaSendSilent;
            case ROW_SPY_DELETED:
                return R.string.MeridaSpySaveDeleted;
            case ROW_SNOW:
                return R.string.MeridaSnow;
            case ROW_PLAYER_ADVANCED:
                return R.string.MeridaPlayerAdvanced;
            case ROW_ALLOW_SCREENSHOTS:
                return R.string.MeridaAllowScreenshots;
            case ROW_SESSION_GUARD:
                return R.string.MeridaSessionGuard;
            case ROW_STRIP_METADATA:
                return R.string.MeridaStripMetadata;
            case ROW_WEBAPP_PRIVACY:
                return R.string.MeridaWebAppPrivacy;
            case ROW_PLAYER_BASS:
                return R.string.MeridaPlayerBass;
            case ROW_PLAYER_LOUDER:
                return R.string.MeridaPlayerLouder;
            case ROW_PLAYER_PRELOAD:
                return R.string.MeridaPlayerPreload;
            default:
                return R.string.MeridaTimeWithSeconds;
        }
    }

    private static String connectionText() {
        if (!MeridaSettings.vpnEnabled.get()) {
            return LocaleController.getString(R.string.MeridaVpnOff);
        }
        switch (MeridaVpn.getState()) {
            case MeridaVpn.STATE_CONNECTED:
                return LocaleController.getString(MeridaVpn.isUsingFallback() ? R.string.MeridaVpnConnectedFallback : R.string.MeridaVpnConnected);
            case MeridaVpn.STATE_FAILED:
                return LocaleController.getString(R.string.MeridaVpnFailed);
            default:
                return LocaleController.getString(R.string.MeridaVpnConnecting);
        }
    }

    private static String onOff(boolean on) {
        return LocaleController.getString(on ? R.string.NotificationsOn : R.string.NotificationsOff);
    }

    private static String subscriptionText() {
        if (!MeridaVpn.hasAccount()) {
            return "—";
        }
        if (!MeridaVpn.isActive()) {
            return LocaleController.getString(R.string.MeridaVpnExpired);
        }
        final String date = LocaleController.getInstance().getFormatterDayMonth().format(new Date(MeridaVpn.getExpiresAt()));
        return LocaleController.formatString(MeridaVpn.isPaid() ? R.string.MeridaVpnPaidUntil : R.string.MeridaVpnTrialUntil, date);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        private ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return rows.length;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            final int type = holder.getItemViewType();
            final int position = holder.getAdapterPosition();
            return type == TYPE_CHECK || type == TYPE_SECTION || type == TYPE_LINK
                    || (position >= 0 && position < rows.length && (rows[position] == ROW_VPN_RENEW || rows[position] == ROW_VPN_PING
                    || rows[position] == ROW_PLAYER_WHERE || rows[position] == ROW_PLAYER_EQ || rows[position] == ROW_PLAYER_SLEEP || rows[position] == ROW_SPY_MARK || rows[position] == ROW_SPY_MARK_COLOR || rows[position] == ROW_VISUAL_STARS || rows[position] == ROW_VISUAL_GRAMS || rows[position] == ROW_VISUAL_GIFTS || rows[position] == ROW_VISUAL_USERNAME || rows[position] == ROW_VISUAL_NUMBER || rows[position] == ROW_VISUAL_RATING || isPlayerChatRow(rows[position]) || isCryptoChatRow(rows[position])));
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_GAP) {
                view = new org.telegram.ui.Cells.ShadowSectionCell(context);
            } else if (viewType == TYPE_HEADER) {
                view = new HeaderCell(context);
                // like exteraGram: the blue title sits above the card, not inside it
                view.setTag(RecyclerListView.TAG_NOT_SECTION);
            } else if (viewType == TYPE_CHECK) {
                view = new TextCheckCell(context);
            } else if (viewType == TYPE_VALUE) {
                view = new TextSettingsCell(context);
            } else if (viewType == TYPE_SECTION || viewType == TYPE_LINK) {
                view = new TextCell(context);
            } else if (viewType == TYPE_SNOW_PREVIEW) {
                view = new SnowPreviewView(context);
            } else if (viewType == TYPE_APP_HEADER) {
                view = new AppHeaderView(context);
                view.setTag(RecyclerListView.TAG_NOT_SECTION);
            } else {
                view = new TextInfoPrivacyCell(context);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
            ));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int index) {
            final int position = rows[index];
            switch (holder.getItemViewType()) {
                case TYPE_SNOW_PREVIEW:
                case TYPE_APP_HEADER:
                case TYPE_GAP:
                    break;
                case TYPE_LINK: {
                    TextCell cell = (TextCell) holder.itemView;
                    cell.setTextAndValueAndIcon(LocaleController.getString(R.string.MeridaLinkChannel), "@MeridaGram", R.drawable.msg_channel, false);
                    cell.valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    break;
                }
                case TYPE_SECTION: {
                    // like exteraGram: icon and name only, no status on the right
                    TextCell cell = (TextCell) holder.itemView;
                    final boolean divider = index + 1 < rows.length && getItemViewType(index + 1) == TYPE_SECTION;
                    if (position == ROW_LINK_CHANNEL) {
                        cell.setTextAndValueAndIcon(LocaleController.getString(R.string.MeridaLinkChannel), "@MeridaGram", R.drawable.msg_channel, divider);
                        cell.valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                        break;
                    }
                    // MeridaGram's own icons on coloured tiles, each section its own colour
                    final int title;
                    final int icon;
                    final int color;
                    if (position == ROW_SECTION_VPN) {
                        title = R.string.MeridaVpnHeader;
                        icon = R.drawable.merida_section_proxy;
                        color = 0xFF7B5CFF;
                    } else if (position == ROW_SECTION_GHOST) {
                        title = R.string.MeridaGhostMode;
                        icon = R.drawable.merida_section_ghost;
                        color = 0xFF5AC8FA;
                    } else if (position == ROW_SECTION_SPY) {
                        title = R.string.MeridaSpyHeader;
                        icon = R.drawable.merida_section_spy;
                        color = 0xFF34C759;
                    } else if (position == ROW_SECTION_SECURITY) {
                        title = R.string.MeridaSecurityHeader;
                        icon = R.drawable.merida_section_security;
                        color = 0xFFFF3B30;
                    } else if (position == ROW_SECTION_PLUGINS) {
                        title = R.string.MeridaPluginsHeader;
                        icon = R.drawable.merida_section_plugins;
                        color = 0xFF30B0C7;
                    } else if (position == ROW_SECTION_PLAYER) {
                        title = R.string.MeridaPlayerHeader;
                        icon = R.drawable.merida_section_player;
                        color = 0xFFFF9500;
                    } else if (position == ROW_SECTION_LOOK) {
                        title = R.string.MeridaLookHeader;
                        icon = R.drawable.merida_section_look;
                        color = 0xFFFF5E99;
                    } else {
                        title = R.string.MeridaSettingsGeneral;
                        icon = R.drawable.merida_section_general;
                        color = 0xFF8E8E93;
                    }
                    cell.setTextAndColorfulIcon(LocaleController.getString(title), icon, color, divider);
                    break;
                }
                case TYPE_HEADER:
                    if (position >= ROW_HDR_BASE && position < ROW_NOTE_BASE) {
                        ((HeaderCell) holder.itemView).setText(LocaleController.getString(HEADERS.get(position - ROW_HDR_BASE)));
                        break;
                    }
                    ((HeaderCell) holder.itemView).setText(LocaleController.getString(
                            position == ROW_VPN_HEADER ? R.string.MeridaVpnHeader : position == ROW_GHOST_HEADER ? R.string.MeridaGhostHeader : position == ROW_SPY_HEADER ? R.string.MeridaSpyHeader : position == ROW_LOOK_HEADER ? R.string.MeridaLookHeader : position == ROW_PLAYER_HEADER ? R.string.MeridaPlayerHeader : position == ROW_CRYPTO_HEADER ? R.string.MeridaCryptoHeader : position == ROW_SECURITY_HEADER ? R.string.MeridaSecurityHeader : position == ROW_CRYPTO_CHATS_HEADER ? R.string.MeridaCryptoChatsHeader : position == ROW_CATEGORIES_HEADER ? R.string.MeridaCategories : position == ROW_LINKS_HEADER ? R.string.MeridaLinks : position == ROW_PLAYER_CHATS_HEADER ? (MeridaSettings.playerMode.get() == MeridaPlayer.MODE_ONLY ? R.string.MeridaPlayerChosenOnly : R.string.MeridaPlayerChosenExcept) : R.string.MeridaSettingsGeneral
                    ));
                    break;
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (isPluginRow(position)) {
                        final int i = position - ROW_PLUGIN_BASE;
                        if (i < plugins.size()) {
                            final org.telegram.messenger.merida.MeridaPlugins.Info info = plugins.get(i);
                            final String sub = pluginSubtitle(info);
                            final boolean on = org.telegram.messenger.merida.MeridaPlugins.isEnabled(info.id);
                            final boolean line = i < plugins.size() - 1;
                            if (sub.isEmpty()) {
                                cell.setTextAndCheck(info.name, on, line);
                            } else {
                                cell.setTextAndValueAndCheck(info.name, sub, on, true, line);
                            }
                            cell.setEnabled(true, null);
                        }
                        break;
                    }
                    final boolean checked = position == ROW_CRYPTO_ENABLED ? MeridaCrypto.isFeatureOn() : position == ROW_BACKGROUND ? backgroundOn() : settingForRow(position).get();
                    // a line under the switch only when another switch or value follows
                    final boolean divider = index + 1 < rows.length && (getItemViewType(index + 1) == TYPE_CHECK || getItemViewType(index + 1) == TYPE_VALUE);
                    final int desc = descForRow(position);
                    if (desc != 0) {
                        cell.setTextAndValueAndCheck(LocaleController.getString(titleForRow(position)), LocaleController.getString(desc), checked, true, divider);
                    } else {
                        cell.setTextAndCheck(LocaleController.getString(titleForRow(position)), checked, divider);
                    }
                    // parts of ghost mode are greyed out while the master switch is off
                    cell.setEnabled(!isGhostPart(position) || MeridaSettings.ghostMode.get(), null);
                    break;
                }
                case TYPE_VALUE: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == ROW_PLAYER_WHERE) {
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        final int mode = Math.max(0, Math.min(MODE_NAMES.length - 1, MeridaSettings.playerMode.get()));
                        cell.setTextAndValue(LocaleController.getString(R.string.MeridaPlayerWhere), LocaleController.getString(MODE_NAMES[mode]), true);
                    } else if (position == ROW_PLAYER_EQ) {
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        final int eq = Math.max(0, Math.min(EQ_NAMES.length - 1, MeridaSettings.playerEqPreset.get()));
                        cell.setTextAndValue(LocaleController.getString(R.string.MeridaPlayerEqualizer), LocaleController.getString(EQ_NAMES[eq]), true);
                    } else if (position == ROW_VISUAL_USERNAME || position == ROW_VISUAL_NUMBER || position == ROW_VISUAL_RATING) {
                        final String off = LocaleController.getString(R.string.NotificationsOff);
                        final String u = MeridaSettings.visualUsername.get();
                        final String n = MeridaSettings.visualNumber.get();
                        final int r = MeridaSettings.visualRating.get();
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        if (position == ROW_VISUAL_USERNAME) {
                            cell.setTextAndValue(LocaleController.getString(R.string.MeridaVisualUsername), u != null && !u.isEmpty() ? "@" + u : off, true);
                        } else if (position == ROW_VISUAL_NUMBER) {
                            cell.setTextAndValue(LocaleController.getString(R.string.MeridaVisualNumber), org.telegram.messenger.merida.MeridaVisual.isAnonymousNumber(n) ? org.telegram.messenger.merida.MeridaVisual.formatNumber888(n) : off, true);
                        } else {
                            cell.setTextAndValue(LocaleController.getString(R.string.MeridaVisualRating), r > 0 ? LocaleController.formatString(R.string.MeridaVisualRatingLevel, r) : off, true);
                        }
                    } else if (position == ROW_VISUAL_GIFTS) {
                        final int n = org.telegram.messenger.merida.MeridaVisualGifts.slugs().size();
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        cell.setTextAndValue(LocaleController.getString(R.string.MeridaVisualGifts), n > 0 ? String.valueOf(n) : LocaleController.getString(R.string.NotificationsOff), true);
                    } else if (position == ROW_VISUAL_STARS || position == ROW_VISUAL_GRAMS) {
                        final boolean stars = position == ROW_VISUAL_STARS;
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        cell.setTextAndValue(LocaleController.getString(stars ? R.string.MeridaVisualStars : R.string.MeridaVisualGrams),
                                visualNumberText((stars ? MeridaSettings.visualStars : MeridaSettings.visualGrams).get()), true);
                    } else if (position == ROW_SPY_MARK) {
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        final int st = Math.max(0, Math.min(MARK_STYLE_NAMES.length - 1, MeridaSettings.spyMarkStyle.get()));
                        cell.setTextAndValue(LocaleController.getString(R.string.MeridaMarkStyle), LocaleController.getString(MARK_STYLE_NAMES[st]), true);
                    } else if (position == ROW_SPY_MARK_COLOR) {
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        final int ci = Math.max(0, Math.min(MARK_COLOR_NAMES.length - 1, MeridaSettings.spyMarkColor.get()));
                        cell.setTextAndValue(LocaleController.getString(R.string.MeridaMarkColor), colorName(ci), false);
                    } else if (position == ROW_PLAYER_SLEEP) {
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        cell.setTextAndValue(LocaleController.getString(R.string.MeridaPlayerSleep), sleepText(), false);
                    } else if (isCryptoChatRow(position)) {
                        final int i = position - ROW_CRYPTO_CHAT_BASE;
                        final boolean chatOn = i < cryptoChats.size() && MeridaCrypto.isChatOn(currentAccount, cryptoChats.get(i));
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        cell.setTextAndValue(i < cryptoChats.size() ? chatName(cryptoChats.get(i)) : "",
                                LocaleController.getString(chatOn ? R.string.MeridaCryptoChatOn : R.string.MeridaCryptoChatPaused), i < cryptoChats.size() - 1);
                    } else if (isPlayerChatRow(position)) {
                        final int i = position - ROW_PLAYER_CHAT_BASE;
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        cell.setTextAndValue(i < playerChats.size() ? chatName(playerChats.get(i)) : "", "✕", i < playerChats.size() - 1);
                    } else if (position == ROW_VPN_PING) {
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        final String value = pingResult == -2 ? "…" : pingResult == -3 ? LocaleController.getString(R.string.MeridaVpnPingFailed)
                                : pingResult >= 0 ? LocaleController.formatString(R.string.MeridaVpnPingMs, pingResult) : LocaleController.getString(R.string.MeridaVpnPingTap);
                        cell.setTextAndValue(LocaleController.getString(R.string.MeridaVpnPing), value, true);
                    } else if (position == ROW_VPN_CONNECTION) {
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        cell.setTextAndValue(LocaleController.getString(R.string.MeridaVpnConnection), connectionText(), true);
                    } else if (position == ROW_VPN_SUBSCRIPTION) {
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        cell.setTextAndValue(LocaleController.getString(R.string.MeridaVpnSubscription), subscriptionText(), true);
                    } else {
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                        cell.setTextAndValue(LocaleController.getString(R.string.MeridaVpnRenew), "", false);
                    }
                    break;
                }
                default: {
                    String text;
                    if (position >= ROW_NOTE_BASE) {
                        text = LocaleController.getString(NOTES.get(position - ROW_NOTE_BASE));
                    } else if (position == ROW_VPN_INFO) {
                        text = LocaleController.getString(R.string.MeridaVpnInfo);
                        final String error = MeridaVpn.getError();
                        if (error != null && !error.isEmpty() && MeridaSettings.vpnEnabled.get()) {
                            text = error + "\n\n" + text;
                        }
                    } else if (position == ROW_GHOST_INFO) {
                        text = LocaleController.getString(R.string.MeridaGhostInfo);
                    } else if (position == ROW_SPY_INFO) {
                        text = LocaleController.getString(R.string.MeridaSpyInfo);
                    } else if (position == ROW_LOOK_INFO) {
                        text = LocaleController.getString(R.string.MeridaLookInfo);
                    } else if (position == ROW_SECURITY_INFO) {
                        text = LocaleController.getString(R.string.MeridaSecurityInfo);
                    } else if (position == ROW_CRYPTO_INFO) {
                        text = LocaleController.getString(R.string.MeridaCryptoSettingsInfo);
                    } else if (position == ROW_CRYPTO_CHATS_HINT) {
                        text = LocaleController.getString(R.string.MeridaCryptoChatsHint);
                    } else if (position == ROW_SECTIONS_INFO) {
                        text = LocaleController.getString(R.string.MeridaSectionsInfo);
                    } else if (position == ROW_PLAYER_INFO) {
                        text = LocaleController.getString(R.string.MeridaPlayerInfo);
                    } else if (position == ROW_PLAYER_CHATS_EMPTY) {
                        text = LocaleController.getString(R.string.MeridaPlayerNoChats);
                    } else if (position == ROW_PLAYER_CHATS_HINT) {
                        text = LocaleController.getString(R.string.MeridaPlayerRemoveHint);
                    } else {
                        text = LocaleController.getString(R.string.MeridaSettingsGeneralInfo);
                    }
                    ((TextInfoPrivacyCell) holder.itemView).setText(text);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int index) {
            if (rows[index] >= ROW_PLUGIN_BASE) {
                return TYPE_CHECK;
            }
            if (rows[index] >= ROW_NOTE_BASE) {
                return TYPE_INFO;
            }
            if (rows[index] >= ROW_HDR_BASE) {
                return TYPE_HEADER;
            }
            if (rows[index] >= ROW_PLAYER_CHAT_BASE) {
                return TYPE_VALUE;
            }
            switch (rows[index]) {
                case ROW_SNOW_PREVIEW:
                    return TYPE_SNOW_PREVIEW;
                case ROW_SECURITY_GAP:
                    return TYPE_GAP;
                case ROW_APP_HEADER:
                    return TYPE_APP_HEADER;
                case ROW_LINK_CHANNEL:
                    return TYPE_LINK;
                case ROW_SECTION_VPN:
                case ROW_SECTION_GHOST:
                case ROW_SECTION_SPY:
                case ROW_SECTION_LOOK:
                case ROW_SECTION_GENERAL:
                case ROW_SECTION_PLAYER:
                case ROW_SECTION_SECURITY:
                case ROW_SECTION_PLUGINS:
                    return TYPE_SECTION;
                case ROW_VPN_HEADER:
                case ROW_GENERAL_HEADER:
                case ROW_GHOST_HEADER:
                case ROW_SPY_HEADER:
                case ROW_LOOK_HEADER:
                case ROW_PLAYER_HEADER:
                case ROW_PLAYER_CHATS_HEADER:
                case ROW_CATEGORIES_HEADER:
                case ROW_LINKS_HEADER:
                case ROW_CRYPTO_HEADER:
                case ROW_SECURITY_HEADER:
                case ROW_CRYPTO_CHATS_HEADER:
                    return TYPE_HEADER;
                case ROW_VPN_CONNECTION:
                case ROW_VPN_SUBSCRIPTION:
                case ROW_VPN_RENEW:
                case ROW_VPN_PING:
                case ROW_PLAYER_WHERE:
                case ROW_PLAYER_EQ:
                case ROW_PLAYER_SLEEP:
                case ROW_SPY_MARK:
                case ROW_SPY_MARK_COLOR:
                case ROW_VISUAL_STARS:
                case ROW_VISUAL_GRAMS:
                case ROW_VISUAL_GIFTS:
                case ROW_VISUAL_USERNAME:
                case ROW_VISUAL_NUMBER:
                case ROW_VISUAL_RATING:
                    return TYPE_VALUE;
                case ROW_VPN_INFO:
                case ROW_GENERAL_INFO:
                case ROW_GHOST_INFO:
                case ROW_SPY_INFO:
                case ROW_LOOK_INFO:
                case ROW_SECTIONS_INFO:
                case ROW_PLAYER_INFO:
                case ROW_PLAYER_CHATS_EMPTY:
                case ROW_PLAYER_CHATS_HINT:
                case ROW_SECURITY_INFO:
                case ROW_CRYPTO_INFO:
                case ROW_CRYPTO_CHATS_HINT:
                    return TYPE_INFO;
                default:
                    return TYPE_CHECK;
            }
        }
    }

    /**
     * A small copy of the chat list's top bar with the snow always falling, so people can see what
     * «Snow» looks like before turning it on.
     */
    /** Top of the main page: the MeridaGram icon, name and version, like other Telegram clients. */
    private static class AppHeaderView extends android.widget.LinearLayout {
        AppHeaderView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(16));

            final android.widget.ImageView icon = new android.widget.ImageView(context);
            icon.setImageResource(R.mipmap.ic_launcher_round);
            icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            addView(icon, LayoutHelper.createLinear(96, 96, android.view.Gravity.CENTER_HORIZONTAL));

            final android.widget.TextView name = new android.widget.TextView(context);
            name.setText(LocaleController.getString(R.string.MeridaAppName));
            name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 24);
            name.setTypeface(AndroidUtilities.bold());
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            name.setGravity(android.view.Gravity.CENTER);
            addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, android.view.Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

            final android.widget.TextView version = new android.widget.TextView(context);
            // the build number shows at once whether the newest APK is installed
            version.setText(LocaleController.formatString(R.string.MeridaVersionBuild, org.telegram.messenger.BuildVars.BUILD_VERSION_STRING, org.telegram.messenger.merida.MeridaBuild.NUMBER));
            version.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
            version.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            version.setGravity(android.view.Gravity.CENTER);
            addView(version, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, android.view.Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
        }
    }

    private static class SnowPreviewView extends View {
        private final SnowflakesEffect snow = new SnowflakesEffect(0);
        /** Not shown: only gives the snow the size of the preview bar to spread flakes over. */
        private final View area;
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bar = new RectF();
        private final Path clip = new Path();

        SnowPreviewView(Context context) {
            super(context);
            area = new View(context);
            titlePaint.setTextSize(AndroidUtilities.dp(20));
            titlePaint.setTypeface(AndroidUtilities.bold());
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(88));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            final int pad = AndroidUtilities.dp(16);
            bar.set(pad, AndroidUtilities.dp(12), w - pad, h - AndroidUtilities.dp(12));
            clip.reset();
            clip.addRoundRect(bar, AndroidUtilities.dp(14), AndroidUtilities.dp(14), Path.Direction.CW);
            // the snow leaves room for the status bar and 20dp at the bottom; make its area that much taller
            area.measure(
                    MeasureSpec.makeMeasureSpec((int) bar.width(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec((int) bar.height() + AndroidUtilities.statusBarHeight + AndroidUtilities.dp(20), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int textColor = Theme.getColor(Theme.key_actionBarDefaultTitle);
            barPaint.setColor(Theme.getColor(Theme.key_actionBarDefault));
            titlePaint.setColor(textColor);
            dotPaint.setColor(textColor);
            canvas.drawPath(clip, barPaint);
            canvas.drawText(LocaleController.getString(R.string.MeridaAppName), bar.left + AndroidUtilities.dp(18), bar.centerY() + AndroidUtilities.dp(7), titlePaint);
            final float dotX = bar.right - AndroidUtilities.dp(20);
            for (int i = -1; i <= 1; i++) {
                canvas.drawCircle(dotX, bar.centerY() + i * AndroidUtilities.dp(5), AndroidUtilities.dp(1.8f), dotPaint);
            }
            canvas.save();
            canvas.clipPath(clip);
            canvas.translate(bar.left, bar.top - AndroidUtilities.statusBarHeight);
            snow.onDraw(area, canvas);
            canvas.restore();
            invalidate();
        }
    }
}
