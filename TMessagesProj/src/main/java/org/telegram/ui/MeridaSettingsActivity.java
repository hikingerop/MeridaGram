package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.utils.settings.BooleanSetting;
import org.telegram.utils.settings.MeridaSettings;

import java.util.Date;

/** MeridaGram's own settings screen. */
public class MeridaSettingsActivity extends BaseFragment implements MeridaVpn.Listener {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_INFO = 2;
    private static final int TYPE_VALUE = 3;

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
    private static final int ROW_COUNT = 11;

    private RecyclerListView listView;
    private ListAdapter adapter;

    @Override
    public boolean onFragmentCreate() {
        MeridaVpn.addListener(this);
        MeridaVpn.refresh();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        MeridaVpn.removeListener(this);
        super.onFragmentDestroy();
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
        actionBar.setTitle(LocaleController.getString(R.string.MeridaSettings));
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
        listView.setOnItemClickListener((view, position) -> onRowClicked(view, position));
        frameLayout.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT
        ));
        return fragmentView;
    }

    private void onRowClicked(View view, int position) {
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
        if (setting == null) {
            return;
        }
        final boolean enabled = setting.toggle();
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(enabled);
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
            default:
                return null;
        }
    }

    private static int titleForRow(int position) {
        switch (position) {
            case ROW_VPN_ENABLED:
                return R.string.MeridaVpnEnabled;
            case ROW_HIDE_STORIES:
                return R.string.MeridaHideStories;
            case ROW_SHOW_ID:
                return R.string.MeridaShowId;
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
            return ROW_COUNT;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_CHECK || holder.getAdapterPosition() == ROW_VPN_RENEW;
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
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER:
                    ((HeaderCell) holder.itemView).setText(LocaleController.getString(
                            position == ROW_VPN_HEADER ? R.string.MeridaVpnHeader : R.string.MeridaSettingsGeneral
                    ));
                    break;
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    cell.setTextAndCheck(
                            LocaleController.getString(titleForRow(position)),
                            settingForRow(position).get(),
                            position != ROW_TIME_WITH_SECONDS
                    );
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
                    } else {
                        text = LocaleController.getString(R.string.MeridaSettingsGeneralInfo);
                    }
                    ((TextInfoPrivacyCell) holder.itemView).setText(text);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            switch (position) {
                case ROW_VPN_HEADER:
                case ROW_GENERAL_HEADER:
                    return TYPE_HEADER;
                case ROW_VPN_CONNECTION:
                case ROW_VPN_SUBSCRIPTION:
                case ROW_VPN_RENEW:
                    return TYPE_VALUE;
                case ROW_VPN_INFO:
                case ROW_GENERAL_INFO:
                    return TYPE_INFO;
                default:
                    return TYPE_CHECK;
            }
        }
    }
}
