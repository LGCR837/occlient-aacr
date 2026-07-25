package aoharureverie.ocaacrclient.oldchat.ui;

import android.widget.ListView;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.WSManager;
import aoharureverie.ocaacrclient.oldchat.api.WSModels;
import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.models.GroupRecentChatCache;
import aoharureverie.ocaacrclient.oldchat.models.MessageHistoryCache;
import aoharureverie.ocaacrclient.oldchat.models.UserNameCache;
import aoharureverie.ocaacrclient.oldchat.util.MessageFieldRepair;
import aoharureverie.ocaacrclient.oldchat.util.SettingsPrefs;
import aoharureverie.ocaacrclient.oldchat.util.TypingStatusManager;
import java.util.HashSet;
import java.util.List;

public class GroupChatWsListener implements WSManager.Listener {
    private final GroupChatActivity activity;
    private final String groupId;
    private final HashSet<String> messageIds;
    private final List<GroupMessage> messageList;
    private final GroupMessageAdapter adapter;
    private final ListView listView;
    private final Runnable onNewMessage;
    private final Runnable markRead;

    public GroupChatWsListener(GroupChatActivity activity, String groupId,
                               HashSet<String> messageIds, List<GroupMessage> messageList,
                               GroupMessageAdapter adapter, ListView listView,
                               Runnable onNewMessage, Runnable markRead) {
        this.activity = activity;
        this.groupId = groupId;
        this.messageIds = messageIds;
        this.messageList = messageList;
        this.adapter = adapter;
        this.listView = listView;
        this.onNewMessage = onNewMessage;
        this.markRead = markRead;
    }

    @Override
    public void onDirectMessage(WSModels.DirectMessage message) {
    }

    @Override
    public void onDirectRead(String threadId, String readerUid, long readAt) {
    }

    @Override
    public void onDirectRecall(WSModels.DirectRecall recall) {
    }

    @Override
    public void onGroupMessage(WSModels.GroupMessage message) {
        if (groupId == null || !groupId.equals(message.groupId)) {
            return;
        }
        if (message.id != null && messageIds.contains(message.id)) {
            return;
        }
        GroupMessage msg = new GroupMessage();
        msg.id = message.id;
        msg.group_id = message.groupId;
        msg.from_uid = message.fromUid;
        msg.body = message.body;
        msg.msg_type = message.msgType;
        msg.media_url = message.mediaUrl;
        msg.thumb_url = message.thumbUrl;
        msg.duration_ms = message.durationMs;
        msg.created_at = ChatMessageUtil.sanitizeTimestamp(message.createdAt);
        MessageFieldRepair.repairGroup(msg);
        if (msg.id != null && msg.id.length() > 0) {
            messageIds.add(msg.id);
        }
        messageList.add(msg);
        GroupMessageSyncHelper.trimOldestInMemory(
                messageList,
                messageIds,
                GroupMessageSyncHelper.MAX_ACTIVE_WINDOW_MESSAGES);
        MessageHistoryCache.saveGroupMessages(activity, groupId, messageList);
        boolean transitioned = adapter != null && adapter.startTypingTransition(msg);
        TypingStatusManager.getInstance().clearTypingOnMessage(groupId, message.fromUid);
        if (adapter != null && !transitioned) {
            adapter.notifyDataSetChanged();
        }
        if (onNewMessage != null) {
            onNewMessage.run();
        }
        int lastVisible = listView.getLastVisiblePosition();
        int total = listView.getCount();
        if (lastVisible >= total - 2) {
            listView.setSelection(messageList.size() - 1);
            GroupRecentChatCache.clearUnread(activity, groupId);
            if (markRead != null) {
                markRead.run();
            }
        }
    }

    @Override
    public void onGroupRecall(WSModels.GroupRecall recall) {
        if (recall == null || recall.messageId == null || recall.messageId.isEmpty()) {
            return;
        }
        if (groupId == null || !groupId.equals(recall.groupId)) {
            return;
        }
        if (!messageIds.contains(recall.messageId)) {
            return;
        }
        for (GroupMessage msg : messageList) {
            if (msg != null && recall.messageId.equals(msg.id)) {
                String name = resolveName(recall.fromUid);
                msg.msg_type = "recall";
                msg.body = activity.getString(R.string.message_recalled_member, name);
                msg.media_url = "";
                msg.thumb_url = "";
                msg.duration_ms = 0;
                MessageHistoryCache.saveGroupMessages(activity, groupId, messageList);
                adapter.notifyDataSetChanged();
                break;
            }
        }
    }

    @Override
    public void onTyping(WSModels.TypingEvent event) {
        if (event == null || !event.isGroup) {
            return;
        }
        if (groupId == null || !groupId.equals(event.chatId)) {
            return;
        }
        TypingStatusManager.getInstance().handleRemoteTyping(groupId, event.uid, event.isTyping);
    }

    @Override
    public void onConnectionChanged(boolean connected) {
        if (activity == null || groupId == null || groupId.isEmpty()) {
            return;
        }
        if (!SettingsPrefs.isTypingIndicatorEnabled(activity)) {
            return;
        }
        TypingStatusManager manager = TypingStatusManager.getInstance();
        if (connected) {
            manager.stopCheckingTyping(groupId);
            return;
        }
        android.content.SharedPreferences prefs = activity.getSharedPreferences("auth",
                android.content.Context.MODE_PRIVATE);
        String token = prefs.getString("access_token", "");
        manager.startCheckingTyping(activity, token, groupId, true);
    }

    private String resolveName(String uid) {
        if (uid == null || uid.isEmpty()) {
            return "成员";
        }
        String name = UserNameCache.getName(activity, uid);
        if (name == null || name.isEmpty()) {
            name = uid;
        }
        return name;
    }
}
