package aoharureverie.ocaacrclient.oldchat.util;

import android.content.Context;
import aoharureverie.ocaacrclient.oldchat.data.FriendRequestStore;
import aoharureverie.ocaacrclient.oldchat.data.MomentNoticeStore;
import aoharureverie.ocaacrclient.oldchat.data.NotificationReadStore;
import aoharureverie.ocaacrclient.oldchat.models.ChatCache;
import aoharureverie.ocaacrclient.oldchat.models.FriendCache;
import aoharureverie.ocaacrclient.oldchat.models.GroupCache;
import aoharureverie.ocaacrclient.oldchat.models.GroupRecentChatCache;
import aoharureverie.ocaacrclient.oldchat.models.MessageHistoryCache;
import aoharureverie.ocaacrclient.oldchat.models.MomentCache;
import aoharureverie.ocaacrclient.oldchat.models.RecentChatCache;
import aoharureverie.ocaacrclient.oldchat.models.UserNameCache;

public class AccountDataCleaner {
    private AccountDataCleaner() {
    }

    public static void clearAll(Context context) {
        if (context == null) {
            return;
        }
        RecentChatCache.clearAll(context);
        GroupRecentChatCache.clearAll(context);
        MessageHistoryCache.clearAll(context);
        ChatCache.clearAll(context);
        FriendCache.clearAll(context);
        GroupCache.clearAll(context);
        UserNameCache.clearAll(context);
        MomentCache.clearAll(context);
        MomentNoticeStore.clearAll(context);
        NotificationReadStore.clearAll(context);
        FriendRequestStore.clearAll(context);
        AvatarSyncManager.clearAll(context);
    }
}
