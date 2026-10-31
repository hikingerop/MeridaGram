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

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_INFO = 2;
    private static final int TYPE_VALUE = 3;
    private static final int TYPE_SECTION = 4;
    private static final int TYPE_SNOW_PREVIEW = 5;

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

    private final int page;
    private final int[] rows;
    private RecyclerListView listView;
    private ListAdapter adapter;

    public MeridaSettingsActivity() {
        this(PAGE_MAIN);
    }

    public MeridaSettingsActivity(int page) {
        super(pageArgs(page));
        this.page = page;
        this.rows = rowsFor(page);
    }

    private static Bundle pageArgs(int page) {
        final Bundle args = new Bundle();
        args.putInt("page", page);
        return args;
    }

    /** Which rows each screen shows, top to bottom. */
    private static int[] rowsFor(int page) {
        switch (page) {
            case PAGE_VPN:
                return new int[]{ROW_VPN_HEADER, ROW_VPN_ENABLED, ROW_VPN_CONNECTION, ROW_VPN_SUBSCRIPTION, ROW_VPN_RENEW, ROW_VPN_INFO};
            case PAGE_GHOST:
                return new int[]{ROW_GHOST_HEADER, ROW_GHOST, ROW_GHOST_NO_READ, ROW_GHOST_NO_STORIES, ROW_GHOST_NO_ONLINE, ROW_GHOST_NO_TYPING, ROW_GHOST_AUTO_OFFLINE, ROW_SEND_SILENT, ROW_GHOST_INFO};
            case PAGE_SPY:
                return new int[]{ROW_SPY_HEADER, ROW_SPY_DELETED, ROW_SPY_INFO};
            case PAGE_LOOK:
                return new int[]{ROW_LOOK_HEADER, ROW_SNOW_PREVIEW, ROW_SNOW, ROW_LOOK_INFO};
            case PAGE_GENERAL:
                return new int[]{ROW_GENERAL_HEADER, ROW_HIDE_STORIES, ROW_SHOW_ID, ROW_TIME_WITH_SECONDS, ROW_GENERAL_INFO};
            default:
                return new int[]{ROW_SECTION_VPN, ROW_SECTION_GHOST, ROW_SECTION_SPY, ROW_SECTION_LOOK, ROW_SECTION_GENERAL, ROW_SECTIONS_INFO};
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
        if (showsVpn()) {
            MeridaVpn.addListener(this);
            MeridaVpn.refresh();
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        if (showsVpn()) {
            MeridaVpn.removeListener(this);
        }
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        // the section list shows «on / off» next to each section; refresh it after coming back from one
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
        actionBar.setAllowOverlayTitle(true);
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
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setSections();
        actionBar.setAdaptiveBackground(listView);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < rows.length) {
                onRowClicked(view, rows[position]);
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT
        ));
        return fragmentView;
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
        if (position == ROW_VPN_RENEW) {
            final String code = MeridaVpn.getAccountCode();
            Browser.openUrl(getParentActivity(), "https://t.me/" + MeridaVpn.BOT_USERNAME + (code.isEmpty() ? "" : "?start=" + code.replace("-", "")));
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
            default:
                return null;
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
            return type == TYPE_CHECK || type == TYPE_SECTION
                    || (position >= 0 && position < rows.length && rows[position] == ROW_VPN_RENEW);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_HEADER) {
                view = new HeaderCell(context);
            } else if (viewType == TYPE_CHECK) {
                view = new TextCheckCell(context);
            } else if (viewType == TYPE_VALUE) {
                view = new TextSettingsCell(context);
            } else if (viewType == TYPE_SECTION) {
                view = new TextCell(context);
            } else if (viewType == TYPE_SNOW_PREVIEW) {
                view = new SnowPreviewView(context);
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
                    break;
                case TYPE_SECTION: {
                    TextCell cell = (TextCell) holder.itemView;
                    final boolean divider = position != ROW_SECTION_GENERAL;
                    if (position == ROW_SECTION_VPN) {
                        cell.setTextAndValueAndIcon(LocaleController.getString(R.string.MeridaVpnHeader), connectionText(), R.drawable.msg_policy, divider);
                    } else if (position == ROW_SECTION_GHOST) {
                        cell.setTextAndValueAndIcon(LocaleController.getString(R.string.MeridaGhostMode), onOff(MeridaSettings.ghostMode.get()), R.drawable.msg_mask, divider);
                    } else if (position == ROW_SECTION_SPY) {
                        cell.setTextAndValueAndIcon(LocaleController.getString(R.string.MeridaSpyHeader), onOff(MeridaSettings.spySaveDeleted.get()), R.drawable.msg_views, divider);
                    } else if (position == ROW_SECTION_LOOK) {
                        cell.setTextAndValueAndIcon(LocaleController.getString(R.string.MeridaLookHeader), "", R.drawable.msg_palette, divider);
                    } else {
                        cell.setTextAndValueAndIcon(LocaleController.getString(R.string.MeridaSettingsGeneral), "", R.drawable.msg_settings, divider);
                    }
                    break;
                }
                case TYPE_HEADER:
                    ((HeaderCell) holder.itemView).setText(LocaleController.getString(
                            position == ROW_VPN_HEADER ? R.string.MeridaVpnHeader : position == ROW_GHOST_HEADER ? R.string.MeridaGhostHeader : position == ROW_SPY_HEADER ? R.string.MeridaSpyHeader : position == ROW_LOOK_HEADER ? R.string.MeridaLookHeader : R.string.MeridaSettingsGeneral
                    ));
                    break;
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    cell.setTextAndCheck(
                            LocaleController.getString(titleForRow(position)),
                            settingForRow(position).get(),
                            position != ROW_TIME_WITH_SECONDS && position != ROW_SEND_SILENT && position != ROW_SPY_DELETED && position != ROW_SNOW
                    );
                    // parts of ghost mode are greyed out while the master switch is off
                    cell.setEnabled(!isGhostPart(position) || MeridaSettings.ghostMode.get(), null);
                    break;
                }
                case TYPE_VALUE: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == ROW_VPN_CONNECTION) {
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
                    if (position == ROW_VPN_INFO) {
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
                        text = LocaleController.getString(R.string.MeridaSnowInfo);
                    } else if (position == ROW_SECTIONS_INFO) {
                        text = LocaleController.getString(R.string.MeridaSectionsInfo);
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
            switch (rows[index]) {
                case ROW_SNOW_PREVIEW:
                    return TYPE_SNOW_PREVIEW;
                case ROW_SECTION_VPN:
                case ROW_SECTION_GHOST:
                case ROW_SECTION_SPY:
                case ROW_SECTION_LOOK:
                case ROW_SECTION_GENERAL:
                    return TYPE_SECTION;
                case ROW_VPN_HEADER:
                case ROW_GENERAL_HEADER:
                case ROW_GHOST_HEADER:
                case ROW_SPY_HEADER:
                case ROW_LOOK_HEADER:
                    return TYPE_HEADER;
                case ROW_VPN_CONNECTION:
                case ROW_VPN_SUBSCRIPTION:
                case ROW_VPN_RENEW:
                    return TYPE_VALUE;
                case ROW_VPN_INFO:
                case ROW_GENERAL_INFO:
                case ROW_GHOST_INFO:
                case ROW_SPY_INFO:
                case ROW_LOOK_INFO:
                case ROW_SECTIONS_INFO:
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
            canvas.drawText(LocaleController.getString(R.string.AppName), bar.left + AndroidUtilities.dp(18), bar.centerY() + AndroidUtilities.dp(7), titlePaint);
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
