package org.telegram.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.merida.MeridaVisual;
import org.telegram.messenger.merida.MeridaVisualGifts;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stars.StarsController;
import org.telegram.utils.settings.MeridaSettings;

import java.util.List;

/**
 * MeridaGram «Visual» home, laid out like Chimera NFT's menu: a header with your NFT, then «My gifts»,
 * «NFT profile», «Premium badge», «Balance», «Craft», «Auctions», «Integrations». Everything here is shown
 * only on this phone and always carries the orange «VISUAL» mark; nothing is sent to Telegram.
 */
public class MeridaVisualActivity extends BaseFragment {

    public static final int PAGE_MAIN = 0;
    public static final int PAGE_PROFILE = 1;
    public static final int PAGE_CRAFT = 2;
    public static final int PAGE_AUCTION = 3;
    public static final int PAGE_INTEGRATIONS = 4;

    private final int page;
    private LinearLayout content;

    public MeridaVisualActivity() {
        this(PAGE_MAIN);
    }

    public MeridaVisualActivity(int page) {
        this.page = page;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setBackgroundColor(MeridaVisualUi.pageBg());
        actionBar.setItemsColor(MeridaVisualUi.text(), false);
        actionBar.setTitleColor(MeridaVisualUi.text());
        actionBar.setCastShadows(false);
        actionBar.setTitle(LocaleController.getString(titleRes()));
        MeridaVisualUi.roundBackButton(actionBar);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        final FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(MeridaVisualUi.pageBg());
        final ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, AndroidUtilities.dp(40));
        scroll.addView(content);
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = root;
        rebuild();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (content != null) {
            rebuild();
        }
    }

    @Override
    public int getNavigationBarColor() {
        return MeridaVisualUi.dark() ? 0xFF000000 : super.getNavigationBarColor();
    }

    private int titleRes() {
        switch (page) {
            case PAGE_PROFILE: return R.string.MeridaVNftProfile;
            case PAGE_CRAFT: return R.string.MeridaVCraft;
            case PAGE_AUCTION: return R.string.MeridaVAuctions;
            case PAGE_INTEGRATIONS: return R.string.MeridaVIntegrations;
            default: return R.string.MeridaVTitle;
        }
    }

    void rebuild() {
        content.removeAllViews();
        switch (page) {
            case PAGE_PROFILE: buildProfile(); break;
            case PAGE_CRAFT: buildCraft(); break;
            case PAGE_AUCTION: buildAuction(); break;
            case PAGE_INTEGRATIONS: buildIntegrations(); break;
            default: buildMain(); break;
        }
    }

    private String s(int res) {
        return LocaleController.getString(res);
    }

    private void addCard(LinearLayout card) {
        content.addView(card, MeridaVisualUi.cardParams());
    }

    private void toast(CharSequence text) {
        if (getParentActivity() != null) {
            Toast.makeText(getParentActivity(), text, Toast.LENGTH_LONG).show();
        }
    }

    private boolean needOn() {
        if (MeridaVisual.on()) {
            return true;
        }
        toast(s(R.string.MeridaNftConstructorTurnOn));
        return false;
    }

    // ---- main ----------------------------------------------------------------------------------------

    private void buildMain() {
        final Context context = getContext();
        content.addView(headerView(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (MeridaVisual.on()) {
            org.telegram.messenger.merida.MeridaVisualBackup.snapshot(30 * 60 * 1000L);
        }
        // like Chimera: no master switch on this screen; it only shows while «Visual» is off
        if (!MeridaVisual.on()) {
        content.addView(MeridaVisualUi.header(context, s(R.string.MeridaHdrVisual)));
        final LinearLayout onCard = MeridaVisualUi.card(context);
        final MeridaVisualUi.Row[] onRow = new MeridaVisualUi.Row[1];
        onRow[0] = MeridaVisualUi.switchRow(context, 0, s(R.string.MeridaVisualEnable), s(R.string.MeridaSubVisual), MeridaSettings.visualEnabled.get(), false, v -> {
            MeridaSettings.visualEnabled.set(!MeridaSettings.visualEnabled.get());
            onRow[0].setChecked(MeridaSettings.visualEnabled.get());
            notifyProfile();
            AndroidUtilities.runOnUIThread(this::rebuild, 250);
        });
        onCard.addView(onRow[0]);
        addCard(onCard);
        }

        content.addView(MeridaVisualUi.header(context, s(R.string.MeridaVTitle)));
        final LinearLayout card = MeridaVisualUi.card(context);
        card.addView(MeridaVisualUi.row(context, R.drawable.merida_v_diamond, s(R.string.MeridaVMyGifts), myGiftsSubtitle(), true,
                v -> { if (needOn()) new MeridaMyGiftsSheet(this).show(); }));
        card.addView(MeridaVisualUi.row(context, R.drawable.merida_v_gear, s(R.string.MeridaVNftProfile), nftProfileSubtitle(), true,
                v -> presentFragment(new MeridaVisualActivity(PAGE_PROFILE))));
        // like Chimera's «Local Premium» row: a tap turns it on/off and says so below
        final MeridaVisualUi.Row[] premium = new MeridaVisualUi.Row[1];
        premium[0] = MeridaVisualUi.row(context, R.drawable.merida_v_premium, s(R.string.MeridaVPremium), premiumSubtitle(), true, v -> {
            MeridaSettings.visualPremium.set(!MeridaSettings.visualPremium.get());
            premium[0].setSubtitle(premiumSubtitle());
            notifyProfile();
            bulletin(s(MeridaSettings.visualPremium.get() ? R.string.MeridaVPremiumTurnedOn : R.string.MeridaVPremiumTurnedOff));
        });
        card.addView(premium[0]);
        card.addView(MeridaVisualUi.row(context, R.drawable.merida_v_wallet, s(R.string.MeridaVBalance), balanceSubtitle(), true,
                v -> MeridaVisualSheets.showBalance(this)));
        card.addView(MeridaVisualUi.row(context, R.drawable.merida_v_dice, s(R.string.MeridaVCraft), s(R.string.MeridaVCraftSub), true,
                v -> presentFragment(new MeridaVisualActivity(PAGE_CRAFT))));
        card.addView(MeridaVisualUi.row(context, R.drawable.merida_v_auction, s(R.string.MeridaVAuctions), s(R.string.MeridaVAuctionsSub), true,
                v -> presentFragment(new MeridaVisualActivity(PAGE_AUCTION))));
        card.addView(MeridaVisualUi.row(context, R.drawable.merida_v_gear, s(R.string.MeridaVIntegrations), s(R.string.MeridaVIntegrationsSub), false,
                v -> presentFragment(new MeridaVisualActivity(PAGE_INTEGRATIONS))));
        addCard(card);

        content.addView(MeridaVisualUi.header(context, s(R.string.MeridaVCatalogs)));
        final LinearLayout extra = MeridaVisualUi.card(context);
        extra.addView(MeridaVisualUi.row(context, R.drawable.merida_v_diamond, s(R.string.MeridaNftCatalog), s(R.string.MeridaVNftCatalogSub), true,
                v -> { if (needOn()) presentFragment(new MeridaNftConstructorActivity()); }));
        extra.addView(MeridaVisualUi.row(context, R.drawable.merida_v_gift, s(R.string.MeridaVGiftCatalog), s(R.string.MeridaVGiftCatalogSub), true,
                v -> { if (needOn()) presentFragment(new MeridaNftConstructorActivity(MeridaNftConstructorActivity.MODE_GIFTS)); }));
        extra.addView(MeridaVisualUi.row(context, R.drawable.merida_v_link, s(R.string.MeridaVisualGiftAdd), s(R.string.MeridaVByLinkSub), true,
                v -> { if (needOn()) MeridaVisualSheets.askGiftLink(this, this::rebuild); }));
        final MeridaVisualUi.Row[] purchases = new MeridaVisualUi.Row[1];
        purchases[0] = MeridaVisualUi.switchRow(context, 0, s(R.string.MeridaVisualPurchases), s(R.string.MeridaSubVisualPurchases), MeridaSettings.visualPurchases.get(), false, v -> {
            MeridaSettings.visualPurchases.set(!MeridaSettings.visualPurchases.get());
            purchases[0].setChecked(MeridaSettings.visualPurchases.get());
        });
        extra.addView(purchases[0]);
        addCard(extra);

        content.addView(MeridaVisualUi.header(context, s(R.string.MeridaVExtra)));
        final LinearLayout more = MeridaVisualUi.card(context);
        more.addView(MeridaVisualUi.row(context, R.drawable.msg_shareout, s(R.string.MeridaVBackup), backupSubtitle(), true, v -> showBackups()));
        more.addView(MeridaVisualUi.row(context, R.drawable.msg_help, s(R.string.MeridaVHelp), s(R.string.MeridaVHelpSub), false,
                v -> org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), "https://t.me/MeridaGram")));
        addCard(more);
        content.addView(MeridaVisualUi.note(context, s(R.string.MeridaInfoVisual)));
    }

    private View headerView(Context context) {
        final LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(4));
        final TLRPC.Document model = headerModel();
        if (model != null) {
            final BackupImageView image = new BackupImageView(context);
            final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(model.thumbs, AndroidUtilities.dp(160));
            image.setImage(ImageLocation.getForDocument(model), "160_160", ImageLocation.getForDocument(thumb, model), "160_160", null, model);
            image.getImageReceiver().setAutoRepeat(1);
            header.addView(image, LayoutHelper.createLinear(120, 120));
        } else {
            final ImageView icon = new ImageView(context);
            icon.setImageResource(R.mipmap.ic_launcher);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            header.addView(icon, LayoutHelper.createLinear(100, 100, 0, 10, 0, 10));
        }
        final TextView title = new TextView(context);
        title.setText(s(R.string.MeridaVHeaderTitle));
        title.setTextColor(MeridaVisualUi.text());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        header.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        final TextView desc = new TextView(context);
        desc.setText(s(R.string.MeridaVHeaderDesc));
        desc.setTextColor(MeridaVisualUi.gray());
        desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        desc.setGravity(Gravity.CENTER);
        header.addView(desc, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 10));
        return header;
    }

    /** The NFT you wear, else your newest visual NFT, else none (the app icon is shown). */
    private static TLRPC.Document headerModel() {
        final TLRPC.Document worn = MeridaVisualGifts.wornModel();
        if (worn != null) {
            return worn;
        }
        for (String key : MeridaVisualGifts.slugs()) {
            final TL_stars.TL_starGiftUnique u = MeridaVisualGifts.load(key);
            if (u != null) {
                final TL_stars.starGiftAttributeModel m = StarsController.findAttribute(u.attributes, TL_stars.starGiftAttributeModel.class);
                if (m != null && m.document != null) {
                    return m.document;
                }
            }
        }
        return null;
    }

    private String myGiftsSubtitle() {
        int nft = 0;
        int ordinary = 0;
        for (String key : MeridaVisualGifts.slugs()) {
            final TL_stars.StarGift g = MeridaVisualGifts.loadAny(key);
            if (g instanceof TL_stars.TL_starGiftUnique) nft++;
            else if (g != null) ordinary++;
        }
        return ordinary > 0 ? nft + " NFT · " + LocaleController.formatString(R.string.MeridaVOrdinaryCount, ordinary) : nft + " NFT";
    }

    private void bulletin(CharSequence text) {
        try {
            org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, text).show();
        } catch (Throwable ignore) {
        }
    }

    /** «Premium · Username · Number · Rating» — only what is set, like Chimera. */
    private String nftProfileSubtitle() {
        final java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        if (MeridaSettings.visualPremium.get()) parts.add("Premium");
        final String u = MeridaSettings.visualUsername.get();
        if (u != null && !u.isEmpty()) parts.add("Username");
        final String n = MeridaSettings.visualNumber.get();
        if (n != null && !n.isEmpty()) parts.add("Number");
        if (MeridaSettings.visualRating.get() > 0) parts.add(s(R.string.MeridaVisualRating));
        return parts.isEmpty() ? s(R.string.MeridaVNftProfileSub) : android.text.TextUtils.join(" · ", parts);
    }

    private String backupSubtitle() {
        final java.util.List<java.io.File> list = org.telegram.messenger.merida.MeridaVisualBackup.list();
        if (list.isEmpty()) {
            return s(R.string.MeridaVNoBackups);
        }
        return LocaleController.formatString(R.string.MeridaVBackupsN, list.size(),
                LocaleController.getInstance().getFormatterDay().format(org.telegram.messenger.merida.MeridaVisualBackup.time(list.get(0))));
    }

    /** «Backup»: make a copy now or bring back one of the copies. */
    private void showBackups() {
        if (getParentActivity() == null) return;
        final java.util.List<java.io.File> list = org.telegram.messenger.merida.MeridaVisualBackup.list();
        final CharSequence[] items = new CharSequence[list.size() + 1];
        items[0] = s(R.string.MeridaVBackupNow);
        for (int i = 0; i < list.size(); i++) {
            final java.io.File f = list.get(i);
            items[i + 1] = LocaleController.formatDateTime(org.telegram.messenger.merida.MeridaVisualBackup.time(f) / 1000, true)
                    + " · " + LocaleController.formatString(R.string.MeridaVGiftsN, org.telegram.messenger.merida.MeridaVisualBackup.giftCount(f));
        }
        final AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(s(R.string.MeridaVBackup));
        b.setItems(items, (d, which) -> {
            if (which == 0) {
                org.telegram.messenger.merida.MeridaVisualBackup.snapshot(0);
                rebuild();
                bulletin(s(R.string.MeridaVBackupDone));
                return;
            }
            final java.io.File f = list.get(which - 1);
            confirm(this, s(R.string.MeridaVBackupRestoreQ), items[which], s(R.string.MeridaVRestore), () -> {
                org.telegram.messenger.merida.MeridaVisualBackup.snapshot(0);
                if (org.telegram.messenger.merida.MeridaVisualBackup.restore(f)) {
                    notifyProfile();
                    rebuild();
                    bulletin(s(R.string.MeridaVBackupRestored));
                }
            });
        });
        b.setNegativeButton(s(R.string.Cancel), null);
        b.show();
    }

    private String premiumSubtitle() {
        return s(MeridaSettings.visualPremium.get() ? R.string.MeridaVPremiumOn : R.string.MeridaVPremiumOff);
    }

    static String balanceSubtitle() {
        return MeridaSettings.visualStars.get() + " Stars · " + MeridaSettings.visualGrams.get() + " GRAM";
    }

    void notifyProfile() {
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_EMOJI_STATUS);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoad, getUserConfig().getClientUserId(), getMessagesController().getUserFull(getUserConfig().getClientUserId()));
    }

    // ---- NFT profile ---------------------------------------------------------------------------------

    private void buildProfile() {
        final Context context = getContext();
        content.addView(MeridaVisualUi.header(context, s(R.string.MeridaVNftProfile)));
        final LinearLayout card = MeridaVisualUi.card(context);
        final String u = MeridaSettings.visualUsername.get();
        card.addView(MeridaVisualUi.row(context, R.drawable.merida_v_pencil, "Username", u == null || u.isEmpty() ? s(R.string.MeridaVNotChosen) : "@" + u, true,
                v -> MeridaVisualSheets.showUsernames(this)));
        final String n = MeridaSettings.visualNumber.get();
        card.addView(MeridaVisualUi.row(context, R.drawable.merida_v_link, "Number", n == null || n.isEmpty() ? s(R.string.MeridaVNotChosen) : "+" + n, true,
                v -> MeridaVisualSheets.showNumbers(this)));
        final int level = MeridaSettings.visualRating.get();
        card.addView(MeridaVisualUi.row(context, R.drawable.merida_v_chart, s(R.string.MeridaVisualRating),
                level > 0 ? LocaleController.formatString(R.string.MeridaVRatingShort, level, MeridaVisual.ratingPoints()) : s(R.string.MeridaVNotChosen), true,
                v -> MeridaVisualSheets.showRating(this)));
        final MeridaVisualUi.Row[] hide = new MeridaVisualUi.Row[1];
        hide[0] = MeridaVisualUi.switchRow(context, R.drawable.merida_v_trash, s(R.string.MeridaVHideOrdinary), s(R.string.MeridaVHideOrdinaryDesc), MeridaSettings.visualHideOrdinary.get(), false, v -> {
            MeridaSettings.visualHideOrdinary.set(!MeridaSettings.visualHideOrdinary.get());
            hide[0].setChecked(MeridaSettings.visualHideOrdinary.get());
            StarsController.getInstance(currentAccount).invalidateProfileGifts(getUserConfig().getClientUserId());
        });
        card.addView(hide[0]);
        addCard(card);
        content.addView(MeridaVisualUi.note(context, s(R.string.MeridaVNftProfileNote)));
    }

    // ---- craft ---------------------------------------------------------------------------------------

    private void buildCraft() {
        final Context context = getContext();
        content.addView(MeridaVisualUi.header(context, s(R.string.MeridaVCraft)));
        final LinearLayout card = MeridaVisualUi.card(context);
        final int chance = MeridaSettings.visualCraftChance.get();
        card.addView(MeridaVisualUi.row(context, R.drawable.merida_v_dice, s(R.string.MeridaVCraftChance),
                chance < 0 ? s(R.string.MeridaVCraftChanceSub) : chance + "%", true,
                v -> MeridaVisualSheets.askText(this, s(R.string.MeridaVCraft), String.valueOf(MeridaSettings.visualCraftChance.get()),
                        android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED, text -> {
                            int value;
                            try {
                                value = Integer.parseInt(text.trim());
                            } catch (Exception e) {
                                value = -1;
                            }
                            MeridaSettings.visualCraftChance.set(value < 0 ? -1 : Math.min(100, value));
                            rebuild();
                            bulletin(s(R.string.MeridaVSaved));
                        })));
        card.addView(MeridaVisualUi.row(context, R.drawable.merida_v_gift, s(R.string.MeridaVCraftOpen), s(R.string.MeridaVCraftPick), true,
                v -> { if (needOn()) new MeridaMyGiftsSheet(this).showCraft(); }));
        final MeridaVisualUi.Row[] always = new MeridaVisualUi.Row[1];
        always[0] = MeridaVisualUi.switchRow(context, 0, s(R.string.MeridaVCraftAlways), null, MeridaSettings.visualCraftAlways.get(), false, v -> {
            MeridaSettings.visualCraftAlways.set(!MeridaSettings.visualCraftAlways.get());
            always[0].setChecked(MeridaSettings.visualCraftAlways.get());
        });
        card.addView(always[0]);
        addCard(card);
        content.addView(MeridaVisualUi.note(context, s(R.string.MeridaVCraftNote)));
    }

    // ---- auctions ------------------------------------------------------------------------------------

    private void buildAuction() {
        final Context context = getContext();
        content.addView(MeridaVisualUi.header(context, s(R.string.MeridaVAuctions)));
        final LinearLayout card = MeridaVisualUi.card(context);
        card.addView(MeridaVisualUi.row(context, 0, s(R.string.MeridaVAuctionTime),
                LocaleController.formatString(R.string.MeridaVSeconds, MeridaSettings.visualAuctionSeconds.get()), true,
                v -> MeridaVisualSheets.askNumber(this, s(R.string.MeridaVAuctionTime), MeridaSettings.visualAuctionSeconds.get(), value -> {
                    MeridaSettings.visualAuctionSeconds.set(Math.max(1, Math.min(3600, value)));
                    rebuild();
                })));
        card.addView(MeridaVisualUi.row(context, R.drawable.merida_v_gift, s(R.string.MeridaVAuctionGifts),
                String.valueOf(MeridaSettings.visualAuctionGifts.get()), false,
                v -> MeridaVisualSheets.askNumber(this, s(R.string.MeridaVAuctionGifts), MeridaSettings.visualAuctionGifts.get(), value -> {
                    MeridaSettings.visualAuctionGifts.set(Math.max(1, Math.min(50, value)));
                    rebuild();
                })));
        addCard(card);
        content.addView(MeridaVisualUi.note(context, s(R.string.MeridaVAuctionNote)));
    }

    // ---- integrations --------------------------------------------------------------------------------

    private void buildIntegrations() {
        final Context context = getContext();
        content.addView(MeridaVisualUi.header(context, s(R.string.MeridaVGiftsHeader)));
        final LinearLayout card = MeridaVisualUi.card(context);
        final MeridaVisualUi.Row[] nft = new MeridaVisualUi.Row[1];
        nft[0] = MeridaVisualUi.switchRow(context, 0, s(R.string.MeridaVAcceptNft), s(R.string.MeridaVAcceptNftDesc), MeridaSettings.visualAcceptNft.get(), true, v -> {
            MeridaSettings.visualAcceptNft.set(!MeridaSettings.visualAcceptNft.get());
            nft[0].setChecked(MeridaSettings.visualAcceptNft.get());
        });
        card.addView(nft[0]);
        final MeridaVisualUi.Row[] gifts = new MeridaVisualUi.Row[1];
        gifts[0] = MeridaVisualUi.switchRow(context, 0, s(R.string.MeridaVAcceptGifts), s(R.string.MeridaVAcceptGiftsDesc), MeridaSettings.visualAcceptGifts.get(), false, v -> {
            MeridaSettings.visualAcceptGifts.set(!MeridaSettings.visualAcceptGifts.get());
            gifts[0].setChecked(MeridaSettings.visualAcceptGifts.get());
        });
        card.addView(gifts[0]);
        addCard(card);
        content.addView(MeridaVisualUi.note(context, s(R.string.MeridaVIntegrationsNote)));

        content.addView(MeridaVisualUi.header(context, s(R.string.MeridaVFunctions)));
        final LinearLayout f = MeridaVisualUi.card(context);
        final MeridaVisualUi.Row[] send = new MeridaVisualUi.Row[1];
        send[0] = MeridaVisualUi.switchRow(context, 0, s(R.string.MeridaVPurchasesShort), s(R.string.MeridaSubVisualPurchases), MeridaSettings.visualPurchases.get(), false, v -> {
            MeridaSettings.visualPurchases.set(!MeridaSettings.visualPurchases.get());
            send[0].setChecked(MeridaSettings.visualPurchases.get());
        });
        f.addView(send[0]);
        addCard(f);
    }

    static void confirm(BaseFragment fragment, CharSequence title, CharSequence message, String ok, Runnable onOk) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle(title);
        b.setMessage(message);
        b.setPositiveButton(ok, (d, w) -> onOk.run());
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        b.show();
    }

    static List<String> keys() {
        return MeridaVisualGifts.slugs();
    }
}
