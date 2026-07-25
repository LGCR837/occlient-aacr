package aoharureverie.ocaacrclient.oldchat.ui;

import android.support.v7.widget.RecyclerView;
import android.widget.ListView;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.WSManager;
import aoharureverie.ocaacrclient.oldchat.api.WSModels;
import aoharureverie.ocaacrclient.oldchat.models.Message;
import aoharureverie.ocaacrclient.oldchat.models.MessageHistoryCache;
import aoharureverie.ocaacrclient.oldchat.models.RecentChatCache;
import aoharureverie.ocaacrclient.oldchat.util.MessageFieldRepair;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;
import aoharureverie.ocaacrclient.oldchat.util.SettingsPrefs;
import aoharureverie.ocaacrclient.oldchat.util.TypingStatusManager;
import java.util.HashSet;
import java.util.List;

public class ChatWsListener implements WSManager.Listener {
    private final ChatActivity activity;
    private final String friendUID;
    private String myUID;
    private final HashSet<String> messageIds;
    private final List<Message> messageList;
    private final MessageAdapter adapter;
    private final RecyclerView listView;
    private final Runnable markRead;
    private final Runnable onNewMessage;

    public ChatWsListener(ChatActivity activity, String friendUID, String myUID,
                          HashSet<String> messageIds, List<Message> messageList,
                          MessageAdapter adapter, RecyclerView listView, Runnable markRead,
                          Runnable onNewMessage) {
        this.activity = activity;
        this.friendUID = friendUID;
        this.myUID = myUID;
        this.messageIds = messageIds;
        this.messageList = messageList;
        this.adapter = adapter;
        this.listView = listView;
        this.markRead = markRead;
        this.onNewMessage = onNewMessage;
    }

    public void setMyUID(String myUID) {
        this.myUID = myUID;
    }

    @Override
    public void onDirectMessage(WSModels.DirectMessage message) {
        if (friendUID == null || !friendUID.equals(message.fromUid)) {
            return;
        }
        if (message.id != null && messageIds.contains(message.id)) {
            return;
        }
        Message m = new Message();
        m.id = message.id;
        m.from_uid = message.fromUid;
        m.body = message.body;
        m.msg_type = message.msgType;
        m.media_url = message.mediaUrl;
        m.thumb_url = message.thumbUrl;
        m.duration_ms = message.durationMs;
        m.created_at = ChatMessageUtil.sanitizeTimestamp(message.createdAt);
        MessageFieldRepair.repairDirect(m);
        if (m.id != null && m.id.length() > 0) {
            messageIds.add(m.id);
        }
        messageList.add(m);
        DirectMessageMerger.trimOldestInMemory(messageList, messageIds, DirectMessageMerger.MAX_ACTIVE_WINDOW_MESSAGES);
        ChatMessageUtil.applyMessageStatus(activity, messageList, myUID);
        MessageHistoryCache.saveDirectMessages(activity, friendUID, messageList);
        boolean transitioned = adapter != null && adapter.startTypingTransition(m);
        TypingStatusManager.getInstance().clearTypingOnMessage(friendUID, message.fromUid);
        if (adapter != null && !transitioned) {
            adapter.notifyDataSetChanged();
        }
        if (onNewMessage != null) {
            onNewMessage.run();
        }
        // RecyclerView 检查是否在底部
        android.support.v7.widget.LinearLayoutManager layoutManager =
            (android.support.v7.widget.LinearLayoutManager) listView.getLayoutManager();
        if (layoutManager != null) {
            int lastVisible = layoutManager.findLastVisibleItemPosition();
            int total = adapter.getItemCount();
            if (lastVisible >= total - 2) {
                listView.scrollToPosition(messageList.size() - 1);
                RecentChatCache.clearUnread(activity, friendUID);
                if (markRead != null) {
                    markRead.run();
                }
            }
        }
    }

    @Override
    public void onDirectRead(String threadId, String readerUid, long readAt) {
        if (friendUID == null || !friendUID.equals(readerUid)) {
            return;
        }
        for (Message msg : messageList) {
            if (MyUidStore.isMyUid(activity, msg.from_uid, myUID)) {
                msg.status = Message.STATUS_READ;
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onDirectRecall(WSModels.DirectRecall recall) {
        if (recall == null || recall.messageId == null || recall.messageId.isEmpty()) {
            return;
        }
        if (!messageIds.contains(recall.messageId)) {
            return;
        }
        for (Message msg : messageList) {
            if (msg != null && recall.messageId.equals(msg.id)) {
                msg.msg_type = "recall";
                if (MyUidStore.isMyUid(activity, recall.fromUid, myUID)) {
                    msg.body = activity.getString(R.string.message_recalled_self);
                } else {
                    msg.body = activity.getString(R.string.message_recalled_other);
                }
                msg.media_url = "";
                msg.thumb_url = "";
                msg.duration_ms = 0;
                MessageHistoryCache.saveDirectMessages(activity, friendUID, messageList);
                adapter.notifyDataSetChanged();
                break;
            }
        }
    }

    @Override
    public void onGroupMessage(WSModels.GroupMessage message) {
    }

    @Override
    public void onGroupRecall(WSModels.GroupRecall recall) {
    }

    @Override
    public void onTyping(WSModels.TypingEvent event) {
        if (event == null || event.isGroup) {
            return;
        }
        if (friendUID == null || !friendUID.equals(event.chatId)) {
            return;
        }
        TypingStatusManager.getInstance().handleRemoteTyping(friendUID, event.uid, event.isTyping);
    }

    @Override
    public void onConnectionChanged(boolean connected) {
        if (activity == null || friendUID == null || friendUID.isEmpty()) {
            return;
        }
        if (!SettingsPrefs.isTypingIndicatorEnabled(activity)) {
            return;
        }
        TypingStatusManager manager = TypingStatusManager.getInstance();
        if (connected) {
            manager.stopCheckingTyping(friendUID);
            return;
        }
        android.content.SharedPreferences prefs = activity.getSharedPreferences("auth",
                android.content.Context.MODE_PRIVATE);
        String token = prefs.getString("access_token", "");
        manager.startCheckingTyping(activity, token, friendUID, false);
    }
}
