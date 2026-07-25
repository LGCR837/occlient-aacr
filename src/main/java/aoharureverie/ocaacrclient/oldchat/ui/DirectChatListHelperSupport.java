package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.Message;
import aoharureverie.ocaacrclient.oldchat.models.MessageHistoryCache;
import aoharureverie.ocaacrclient.oldchat.models.RecentChatCache;
import aoharureverie.ocaacrclient.oldchat.util.MessageFieldRepair;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;

abstract class DirectChatListHelperSupport {
    protected static final int PAGE_LIMIT = 20;
    protected static final int MAX_ACTIVE_WINDOW_MESSAGES = DirectMessageMerger.MAX_ACTIVE_WINDOW_MESSAGES;

    protected Context context;
    protected RecyclerView lvMessages;
    protected MessageAdapter adapter;
    protected List<Message> messageList;
    protected HashSet<String> messageIds;
    protected TextView btnLoadMore;
    protected TextView btnNewMessage;
    protected ProgressBar loadingBar;
    protected String friendUID;
    protected String friendName;
    protected String friendAvatar;
    protected String myUID;
    protected TextView btnJumpToUnread;
    protected boolean isLoadingMore = false;
    protected boolean hasMore = true;
    protected int newMessageCount = 0;
    protected int currentOffset = 0;
    protected int initialUnreadCount = 0;
    protected boolean pendingJumpToUnread = false;
    protected String pendingJumpToken = null;
    protected String pendingJumpMessageId = null;
    protected String pendingJumpMessageToken = null;
    protected boolean pendingJumpAnchorUsed = false;
    protected int cacheLoadSeq = 0;
    protected int lastFirstVisible = 0;
    protected int lastFirstTop = 0;
    protected boolean lastKnownAtBottom = true;

    protected abstract void hideJumpToUnreadButton();

    public Message parseMessageFromResponse(String response) {
        try {
            JSONObject obj = new JSONObject(response);
            Message msg = new Message();
            String id = obj.optString("id", null);
            msg.id = (id != null && !id.isEmpty()) ? id : null;
            msg.thread_id = obj.optString("thread_id", null);
            msg.from_uid = obj.optString("from_uid", myUID);
            msg.body = obj.optString("body", "");
            msg.msg_type = obj.optString("msg_type", "text");
            msg.media_url = obj.optString("media_url", "");
            msg.thumb_url = obj.optString("thumb_url", "");
            msg.duration_ms = obj.optInt("duration_ms", 0);
            long createdAt = obj.optLong("created_at", 0);
            msg.created_at = ChatMessageUtil.sanitizeTimestamp(createdAt);
            msg.status = ChatMessageUtil.parseStatus(obj);
            MessageFieldRepair.repairDirect(msg);
            return msg;
        } catch (Exception e) {
            return null;
        }
    }

    public void appendMessage(Message msg, boolean forceScroll, String token) {
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
        DirectMessageMerger.trimOldestInMemory(messageList, messageIds, MAX_ACTIVE_WINDOW_MESSAGES);
        ChatMessageUtil.applyMessageStatus(context, messageList, myUID);
        if (adapter != null && MyUidStore.isMyUid(context, msg.from_uid, myUID)) {
            adapter.markMessageAnimating(msg);
        }
        adapter.notifyDataSetChanged();
        MessageHistoryCache.saveDirectMessages(context, friendUID, messageList);
        updateRecentFromMessage(msg, atBottom);
        if (atBottom) {
            scrollToBottom();
            if (msg.from_uid != null && !MyUidStore.isMyUid(context, msg.from_uid, myUID)) {
                markRead(token);
            }
        } else if (msg.from_uid != null && !MyUidStore.isMyUid(context, msg.from_uid, myUID)) {
            onNewMessageReceived();
        }
    }

    public void markRead(String token) {
        if (friendUID == null || friendUID.isEmpty()) {
            return;
        }
        RecentChatCache.clearUnread(context, friendUID);
        try {
            JSONObject json = new JSONObject();
            json.put("with_uid", friendUID);
            HttpUtil.post("/direct/read", json, token, new HttpUtil.Callback() {
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

    public void onNewMessageReceived() {
        if (!isAtBottom()) {
            newMessageCount++;
            showNewMessageBubble();
        }
    }

    public void onUserScroll(boolean atBottom) {
        captureScrollPosition();
        if (atBottom) {
            hideNewMessageBubbleInternal();
            if (initialUnreadCount == 0) {
                hideJumpToUnreadButton();
            }
        }
    }

    public void scrollToBottom() {
        hideNewMessageBubble();
        if (!messageList.isEmpty()) {
            lvMessages.scrollToPosition(messageList.size() - 1);
        }
    }

    public void recordScrollPosition() {
        lastKnownAtBottom = isAtBottom();
        captureScrollPosition();
    }

    protected LinearLayoutManager getLayoutManager() {
        RecyclerView.LayoutManager lm = lvMessages.getLayoutManager();
        if (lm instanceof LinearLayoutManager) {
            return (LinearLayoutManager) lm;
        }
        return null;
    }

    protected void captureScrollPosition() {
        LinearLayoutManager layoutManager = getLayoutManager();
        if (layoutManager == null) {
            return;
        }
        int pos = layoutManager.findFirstVisibleItemPosition();
        if (pos < 0) {
            return;
        }
        View firstView = layoutManager.findViewByPosition(pos);
        lastFirstVisible = pos;
        lastFirstTop = firstView == null ? 0 : firstView.getTop();
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

    protected void hideNewMessageBubbleInternal() {
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
        LinearLayoutManager layoutManager = (LinearLayoutManager) lvMessages.getLayoutManager();
        if (layoutManager == null) {
            return lastKnownAtBottom;
        }
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        int total = adapter.getItemCount();
        if (lastVisible < 0 || total <= 0) {
            return lastKnownAtBottom;
        }
        lastKnownAtBottom = lastVisible >= total - 2;
        return lastKnownAtBottom;
    }

    protected void updateRecentChat(String lastMessage) {
        if (friendUID == null || friendUID.isEmpty()) {
            return;
        }
        String name = friendName != null ? friendName : friendUID;
        RecentChatCache.updateRecentChat(context, friendUID, name, friendAvatar, lastMessage);
    }

    protected void updateRecentFromMessages(List<Message> incoming, boolean append) {
        if (append || incoming.isEmpty()) {
            return;
        }
        Message newest = incoming.get(incoming.size() - 1);
        updateRecentChat(ChatMessageUtil.previewForMessage(newest));
    }

    protected void updateRecentFromMessage(Message msg, boolean atBottom) {
        if (msg == null || friendUID == null || friendUID.isEmpty()) {
            return;
        }
        String preview = ChatMessageUtil.previewForMessage(msg);
        if (msg.from_uid != null && MyUidStore.isMyUid(context, msg.from_uid, myUID)) {
            RecentChatCache.updateRecentChatOutgoing(context, friendUID, friendName, friendAvatar,
                    preview, msg.created_at);
            return;
        }
        int unreadDelta = atBottom ? 0 : 1;
        RecentChatCache.updateRecentChatIncoming(context, friendUID, friendName, friendAvatar,
                preview, msg.created_at, unreadDelta);
    }

    protected void loadFromCacheAsync() {
        final int seq = ++cacheLoadSeq;
        final Context appContext = context != null ? context.getApplicationContext() : null;
        new android.os.AsyncTask<Void, Void, List<Message>>() {
            @Override
            protected List<Message> doInBackground(Void... voids) {
                if (appContext == null) {
                    return null;
                }
                return MessageHistoryCache.getDirectMessages(appContext, friendUID);
            }

            @Override
            protected void onPostExecute(List<Message> cached) {
                if (seq != cacheLoadSeq || cached == null || cached.isEmpty() || !messageList.isEmpty()) {
                    return;
                }
                hasMore = true;
                messageList.clear();
                messageIds.clear();
                messageList.addAll(cached);
                for (Message msg : cached) {
                    if (msg.id != null) {
                        messageIds.add(msg.id);
                    }
                }
                DirectMessageMerger.trimOldestInMemory(messageList, messageIds, MAX_ACTIVE_WINDOW_MESSAGES);
                currentOffset = messageList.size();
                ChatMessageUtil.applyMessageStatus(context, messageList, myUID);
                adapter.notifyDataSetChanged();
                lvMessages.scrollToPosition(messageList.size() - 1);
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
