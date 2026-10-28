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
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.utils.settings.BooleanSetting;
import org.telegram.utils.settings.MeridaSettings;

/** MeridaGram's own settings screen. */
public class MeridaSettingsActivity extends BaseFragment {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_INFO = 2;

    private static final int ROW_GENERAL_HEADER = 0;
    private static final int ROW_HIDE_STORIES = 1;
    private static final int ROW_SHOW_ID = 2;
    private static final int ROW_TIME_WITH_SECONDS = 3;
    private static final int ROW_GENERAL_INFO = 4;
    private static final int ROW_COUNT = 5;

    private RecyclerListView listView;
    private ListAdapter adapter;

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
            case ROW_HIDE_STORIES:
                return R.string.MeridaHideStories;
            case ROW_SHOW_ID:
                return R.string.MeridaShowId;
            default:
                return R.string.MeridaTimeWithSeconds;
        }
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
            return holder.getItemViewType() == TYPE_CHECK;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_HEADER) {
                view = new HeaderCell(context);
            } else if (viewType == TYPE_CHECK) {
                view = new TextCheckCell(context);
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
            if (holder.getItemViewType() == TYPE_HEADER) {
                ((HeaderCell) holder.itemView).setText(
                        LocaleController.getString(R.string.MeridaSettingsGeneral)
                );
            } else if (holder.getItemViewType() == TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                cell.setTextAndCheck(
                        LocaleController.getString(titleForRow(position)),
                        settingForRow(position).get(),
                        position != ROW_TIME_WITH_SECONDS
                );
            } else {
                ((TextInfoPrivacyCell) holder.itemView).setText(
                        LocaleController.getString(R.string.MeridaSettingsGeneralInfo)
                );
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == ROW_GENERAL_HEADER) {
                return TYPE_HEADER;
            }
            if (position == ROW_GENERAL_INFO) {
                return TYPE_INFO;
            }
            return TYPE_CHECK;
        }
    }
}
