package aoharureverie.ocaacrclient.oldchat.ui;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.models.GroupRecentChatCache;
import aoharureverie.ocaacrclient.oldchat.models.MessageHistoryCache;
import aoharureverie.ocaacrclient.oldchat.util.MessageFieldRepair;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;

abstract class GroupChatListHelperSupport {
    protected static final int PAGE_LIMIT = 20;

    protected Context context;
    protected ListView lvMessages;
    protected GroupMessageAdapter adapter;
    protected List<GroupMessage> messageList;
    protected HashSet<String> messageIds;
    protected TextView btnLoadMore;
    protected TextView btnNewMessage;
    protected ProgressBar loadingBar;
    protected String groupId;
    protected String groupName;
    protected String myUID;
    protected boolean isLoadingMore = false;
    protected boolean hasMore = true;
    protected int newMessageCount = 0;
    protected int currentOffset = 0;
    protected long lastMarkReadAt = 0;
    protected TextView btnJumpToUnread;
    protected int initialUnreadCount = 0;
    protected boolean pendingJumpToUnread = false;
    protected String pendingJumpToken = null;
    protected String pendingJumpMessageId = null;
    protected String pendingJumpMessageToken = null;
    protected boolean pendingJumpAnchorUsed = false;
    protected int cacheLoadSeq = 0;
    protected boolean lastKnownAtBottom = true;

    protected abstract void hideJumpToUnreadButton();

    protected boolean isContextValid() {
        if (context == null) {
            return false;
        }
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (android.os.Build.VERSION.SDK_INT >= 17) {
                return !activity.isFinishing() && !activity.isDestroyed();
            }
            return !activity.isFinishing();
        }
        return true;
    }

    public GroupMessage parseMessageFromResponse(String response) {
        try {
            JSONObject obj = new JSONObject(response);
            GroupMessage msg = new GroupMessage();
            String id = obj.optString("id", null);
            msg.id = (id != null && !id.isEmpty()) ? id : null;
            msg.group_id = obj.optString("group_id", groupId);
            String fallbackUid = MyUidStore.getCurrentUid(context);
            if (fallbackUid == null || fallbackUid.length() == 0) {
                fallbackUid = myUID;
            }
            msg.from_uid = obj.optString("from_uid", fallbackUid);
            msg.body = obj.optString("body", "");
            msg.msg_type = obj.optString("msg_type", "text");
            msg.media_url = obj.optString("media_url", "");
            msg.thumb_url = obj.optString("thumb_url", "");
            msg.duration_ms = obj.optInt("duration_ms", 0);
            long createdAt = obj.optLong("created_at", 0);
            msg.created_at = ChatMessageUtil.sanitizeTimestamp(createdAt);
            MessageFieldRepair.repairGroup(msg);
            return msg;
        } catch (Exception e) {
            return null;
        }
    }

    public void appendMessage(GroupMessage msg, boolean forceScroll) {
        if (msg == null) {
            return;
        }
        boolean atBottom = forceScroll || isAtBottom();
        if (msg.id != null && messageIds.contains(msg.id)) {
            return;
        }
        messageList.add(msg);
        if (msg.id != null) {
            messageIds.add(msg.id);
        }
        GroupMessageSyncHelper.trimOldestInMemory(
                messageList,
                messageIds,
                GroupMessageSyncHelper.MAX_ACTIVE_WINDOW_MESSAGES);
        if (adapter != null && MyUidStore.isMyUid(context, msg.from_uid, myUID)) {
            adapter.markMessageAnimating(msg);
        }
        adapter.notifyDataSetChanged();
        MessageHistoryCache.saveGroupMessages(context, groupId, messageList);
        if (atBottom) {
            lvMessages.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
            scrollToBottom();
        } else if (msg.from_uid != null && !MyUidStore.isMyUid(context, msg.from_uid, myUID)) {
            lvMessages.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_DISABLED);
            onNewMessageReceived();
        }
    }

    public void onNewMessageReceived() {
        if (!isAtBottom()) {
            newMessageCount++;
            showNewMessageBubble();
        }
    }

    public void onUserScroll(boolean atBottom) {
        if (atBottom) {
            lvMessages.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
            hideNewMessageBubbleInternal();
            if (initialUnreadCount == 0) {
                hideJumpToUnreadButton();
            }
        } else {
            lvMessages.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_DISABLED);
        }
    }

    public void recordScrollPosition() {
        lastKnownAtBottom = isAtBottom();
    }

    public void scrollToBottom() {
        hideNewMessageBubble();
        if (!messageList.isEmpty()) {
            lvMessages.setSelection(messageList.size() - 1);
        }
    }

    public void markRead(String token) {
        if (groupId == null || groupId.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastMarkReadAt < 2000) {
            return;
        }
        lastMarkReadAt = now;
        GroupRecentChatCache.clearUnread(context, groupId);
        try {
            JSONObject json = new JSONObject();
            json.put("group_id", groupId);
            HttpUtil.post("/groups/read", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                }

                @Override
                public void onError(int code, String error) {
                }
            });
        } catch (Exception e) {
        }
    }

    public void hideNewMessageBubble() {
        hideNewMessageBubbleInternal();
    }

    protected void showNewMessageBubble() {
        if (btnNewMessage != null) {
            btnNewMessage.setText(newMessageCount + " 条新消息");
            btnNewMessage.setVisibility(View.VISIBLE);
        }
    }

    private void hideNewMessageBubbleInternal() {
        newMessageCount = 0;
        if (btnNewMessage != null) {
            btnNewMessage.setVisibility(View.GONE);
        }
    }

    public boolean isAtBottom() {
        if (messageList.isEmpty()) {
            lastKnownAtBottom = true;
            return true;
        }
        int total = lvMessages.getCount();
        if (total <= 0) {
            return lastKnownAtBottom;
        }
        int lastVisible = lvMessages.getLastVisiblePosition();
        if (lastVisible < 0) {
            return lastKnownAtBottom;
        }
        lastKnownAtBottom = lastVisible >= total - 2;
        return lastKnownAtBottom;
    }

    protected void updateRecentFromMessages(List<GroupMessage> incoming, boolean append) {
        if (append || incoming.isEmpty()) {
            return;
        }
        GroupMessage newest = incoming.get(incoming.size() - 1);
        GroupRecentChatCache.updateGroupOutgoing(context, groupId, groupName, null,
                ChatMessageUtil.previewForType(newest.msg_type, newest.body), newest.created_at);
    }

    protected void loadFromCacheAsync() {
        final int seq = ++cacheLoadSeq;
        final Context appContext = context != null ? context.getApplicationContext() : null;
        new AsyncTask<Void, Void, List<GroupMessage>>() {
            @Override
            protected List<GroupMessage> doInBackground(Void... voids) {
                if (appContext == null) {
                    return null;
                }
                return MessageHistoryCache.getGroupMessages(appContext, groupId);
            }

            @Override
            protected void onPostExecute(List<GroupMessage> cached) {
                if (seq != cacheLoadSeq || cached == null || cached.isEmpty() || !messageList.isEmpty()) {
                    return;
                }
                hasMore = true;
                messageList.clear();
                messageIds.clear();
                messageList.addAll(cached);
                currentOffset = messageList.size();
                for (GroupMessage msg : cached) {
                    if (msg.id != null) {
                        messageIds.add(msg.id);
                    }
                }
                adapter.notifyDataSetChanged();
                lvMessages.setSelection(messageList.size() - 1);
                lvMessages.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
                if (btnLoadMore != null) {
                    btnLoadMore.setEnabled(true);
                    btnLoadMore.setText(R.string.load_more_messages);
                }
                updateRecentFromMessages(cached, false);
            }
        }.execute();
    }

    protected void setLoading(boolean loading) {
        if (loadingBar != null) {
            loadingBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }
}
