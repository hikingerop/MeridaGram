package org.telegram.messenger.merida;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.utils.settings.MeridaSettings;

/**
 * MeridaGram ghost mode. Every request passes through {@link #filter} before it leaves the phone:
 * read marks, «typing» and story views can be held back, the online status forced to «offline»,
 * and messages sent without sound. Each part has its own switch in MeridaGram Settings.
 */
public final class MeridaGhost {

    private MeridaGhost() {
    }

    /** True while the master switch is on and the given part is chosen. */
    private static boolean on(boolean part) {
        return MeridaSettings.ghostMode.get() && part;
    }

    /**
     * Changes the request in place where needed and says whether it must be dropped (not sent at all).
     * After a message is sent, «auto offline» sends a fresh «offline» status, because Telegram marks
     * the sender online on every message.
     */
    public static boolean filter(TLObject object, ConnectionsManager connections) {
        if (object == null) {
            return false;
        }
        if (object instanceof TL_account.updateStatus) {
            if (on(MeridaSettings.ghostNoOnline.get())) {
                ((TL_account.updateStatus) object).offline = true;
            }
            return false;
        }
        if (on(MeridaSettings.ghostNoRead.get()) && isRead(object)) {
            return true;
        }
        if (on(MeridaSettings.ghostNoStories.get()) && isStoryView(object)) {
            return true;
        }
        if (on(MeridaSettings.ghostNoTyping.get()) && isTyping(object)) {
            return true;
        }
        if (isSend(object)) {
            if (MeridaSettings.sendSilent.get()) {
                makeSilent(object);
            }
            if (on(MeridaSettings.ghostAutoOffline.get())) {
                AndroidUtilities.runOnUIThread(() -> {
                    final TL_account.updateStatus offline = new TL_account.updateStatus();
                    offline.offline = true;
                    connections.sendRequest(offline, null);
                }, 1500);
            }
        }
        return false;
    }

    private static boolean isRead(TLObject object) {
        return object instanceof TLRPC.TL_messages_readHistory
                || object instanceof TLRPC.TL_channels_readHistory
                || object instanceof TLRPC.TL_messages_readMessageContents
                || object instanceof TLRPC.TL_channels_readMessageContents
                || object instanceof TLRPC.TL_messages_readDiscussion
                || object instanceof TLRPC.TL_messages_readEncryptedHistory;
    }

    private static boolean isStoryView(TLObject object) {
        return object instanceof TL_stories.TL_stories_readStories
                || object instanceof TL_stories.TL_stories_incrementStoryViews;
    }

    private static boolean isTyping(TLObject object) {
        return object instanceof TLRPC.TL_messages_setTyping
                || object instanceof TLRPC.TL_messages_setEncryptedTyping;
    }

    private static boolean isSend(TLObject object) {
        return object instanceof TLRPC.TL_messages_sendMessage
                || object instanceof TLRPC.TL_messages_sendMedia
                || object instanceof TLRPC.TL_messages_sendMultiMedia
                || object instanceof TLRPC.TL_messages_forwardMessages
                || object instanceof TLRPC.TL_messages_sendInlineBotResult;
    }

    private static void makeSilent(TLObject object) {
        if (object instanceof TLRPC.TL_messages_sendMessage) {
            ((TLRPC.TL_messages_sendMessage) object).silent = true;
        } else if (object instanceof TLRPC.TL_messages_sendMedia) {
            ((TLRPC.TL_messages_sendMedia) object).silent = true;
        } else if (object instanceof TLRPC.TL_messages_sendMultiMedia) {
            ((TLRPC.TL_messages_sendMultiMedia) object).silent = true;
        } else if (object instanceof TLRPC.TL_messages_forwardMessages) {
            ((TLRPC.TL_messages_forwardMessages) object).silent = true;
        } else if (object instanceof TLRPC.TL_messages_sendInlineBotResult) {
            ((TLRPC.TL_messages_sendInlineBotResult) object).silent = true;
        }
    }
}
