package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.Message;
import aoharureverie.ocaacrclient.oldchat.models.MessageHistoryCache;
import aoharureverie.ocaacrclient.oldchat.models.RecentChatCache;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;
import aoharureverie.ocaacrclient.oldchat.util.NetworkStateManager;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class DirectChatListHelper extends DirectChatListHelperSupport {

    public DirectChatListHelper(Context context, RecyclerView lvMessages, MessageAdapter adapter,
                                List<Message> messageList, HashSet<String> messageIds,
                                TextView btnLoadMore, TextView btnNewMessage, ProgressBar loadingBar,
                                String friendUID, String friendName, String friendAvatar,
                                String myUID) {
        this.context = context;
        this.lvMessages = lvMessages;
        this.adapter = adapter;
        this.messageList = messageList;
        this.messageIds = messageIds;
        this.btnLoadMore = btnLoadMore;
        this.btnNewMessage = btnNewMessage;
        this.loadingBar = loadingBar;
        this.friendUID = friendUID;
        this.friendName = friendName;
        this.friendAvatar = friendAvatar;
        this.myUID = myUID;
    }

    public void setMyUID(String myUID) {
        this.myUID = myUID;
    }

    public void setJumpToUnreadButton(TextView btnJumpToUnread) {
        this.btnJumpToUnread = btnJumpToUnread;
    }

    public void setInitialUnreadCount(int count) {
        if (count <= 8) {
            this.initialUnreadCount = 0;
            if (btnJumpToUnread != null) {
                btnJumpToUnread.setVisibility(View.GONE);
            }
            return;
        }
        this.initialUnreadCount = count;
        if (btnJumpToUnread != null) {
            btnJumpToUnread.setText(count + " 条未读");
            btnJumpToUnread.setVisibility(View.VISIBLE);
        }
    }

    public void jumpToEarliestUnread(String token) {
        if (initialUnreadCount <= 0) {
            return;
        }
        int totalMessages = messageList.size();
        int earliestUnreadPos = totalMessages - initialUnreadCount;
        if (earliestUnreadPos < 0) {
            earliestUnreadPos = 0;
        }
        pendingJumpToken = token;
        if (earliestUnreadPos == 0 && hasMore && !isLoadingMore) {
            pendingJumpToUnread = true;
            long before = getOldestTimestamp();
            loadMessages(token, true, before, false);
        } else {
            scrollToPosition(earliestUnreadPos);
            hideJumpToUnreadButton();
        }
    }

    private void scrollToPosition(final int position) {
        if (position < 0 || position >= messageList.size()) {
            return;
        }
        LinearLayoutManager layoutManager = getLayoutManager();
        if (layoutManager != null) {
            layoutManager.scrollToPositionWithOffset(position, 0);
        } else {
            lvMessages.scrollToPosition(position);
        }
        lvMessages.post(new Runnable() {
            @Override
            public void run() {
                if (position < 0 || position >= messageList.size()) {
                    return;
                }
                LinearLayoutManager lm = getLayoutManager();
                if (lm != null) {
                    lm.scrollToPositionWithOffset(position, 0);
                } else {
                    lvMessages.scrollToPosition(position);
                }
            }
        });
    }

    private void smoothScrollToBottom() {
        if (messageList.isEmpty()) {
            return;
        }
        lvMessages.smoothScrollToPosition(messageList.size() - 1);
    }

    public void hideJumpToUnreadButton() {
        initialUnreadCount = 0;
        pendingJumpToUnread = false;
        pendingJumpToken = null;
        if (btnJumpToUnread != null) {
            btnJumpToUnread.setVisibility(View.GONE);
        }
    }

    private void onLoadMoreCompleteForJump() {
        if (tryJumpToMessage()) {
            return;
        }
        if (!pendingJumpToUnread) {
            return;
        }
        int totalMessages = messageList.size();
        int earliestUnreadPos = totalMessages - initialUnreadCount;
        if (earliestUnreadPos < 0) {
            earliestUnreadPos = 0;
        }
        if (earliestUnreadPos == 0 && hasMore && !isLoadingMore && pendingJumpToken != null && !pendingJumpToken.isEmpty()) {
            long before = getOldestTimestamp();
            loadMessages(pendingJumpToken, true, before, false);
            return;
        }
        scrollToPosition(earliestUnreadPos);
        hideJumpToUnreadButton();
    }

    public void jumpToMessageId(String token, String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            return;
        }
        int pos = findMessagePosition(messageId);
        if (pos >= 0) {
            scrollToPosition(pos);
            if (adapter != null) {
                adapter.highlightJumpMessage(messageId);
            }
            return;
        }
        if (isLoadingMore) {
            pendingJumpMessageId = messageId;
            pendingJumpMessageToken = token;
            pendingJumpAnchorUsed = false;
            return;
        }
        if (!canLoadMore() || token == null || token.isEmpty()) {
            Toast.makeText(context, "未找到目标消息，可能已撤回或被清理", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingJumpMessageId = messageId;
        pendingJumpMessageToken = token;
        pendingJumpAnchorUsed = false;
        long before = getOldestTimestamp();
        loadMessages(token, true, before, true);
    }

    private int findMessagePosition(String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < messageList.size(); i++) {
            Message msg = messageList.get(i);
            if (msg != null && messageId.equals(msg.id)) {
                return i;
            }
        }
        return -1;
    }

    private boolean tryJumpToMessage() {
        if (pendingJumpMessageId == null || pendingJumpMessageId.isEmpty()) {
            return false;
        }
        int pos = findMessagePosition(pendingJumpMessageId);
        if (pos >= 0) {
            scrollToPosition(pos);
            if (adapter != null) {
                adapter.highlightJumpMessage(pendingJumpMessageId);
            }
            pendingJumpMessageId = null;
            pendingJumpMessageToken = null;
            pendingJumpAnchorUsed = false;
            return true;
        }
        if (hasMore && !isLoadingMore && pendingJumpMessageToken != null && !pendingJumpMessageToken.isEmpty()) {
            long before = getOldestTimestamp();
            loadMessages(pendingJumpMessageToken, true, before, true);
            return true;
        }
        pendingJumpMessageId = null;
        pendingJumpMessageToken = null;
        pendingJumpAnchorUsed = false;
        Toast.makeText(context, "未找到目标消息，可能已撤回或被清理", Toast.LENGTH_SHORT).show();
        return true;
    }

    public boolean canLoadMore() {
        return !isLoadingMore && hasMore;
    }

    public long getOldestTimestamp() {
        if (messageList.isEmpty()) {
            return 0;
        }
        return messageList.get(0).created_at;
    }

    public void loadMessages(String token, boolean append, long before, boolean silent) {
        loadMessages(token, append, before, silent, false);
    }

    public void loadMessages(String token, boolean append, long before, boolean silent, boolean keepPosition) {
        final boolean appendFinal = append;
        final boolean silentFinal = silent;
        final boolean keepPositionFinal = keepPosition;
        final String tokenFinal = token;
        // 如果是首次加载（非加载更多），先从缓存加载显示，提高响应速度
        if (!appendFinal && !keepPositionFinal) {
            loadFromCacheAsync();
        }

        if ((appendFinal && !NetworkStateManager.getInstance().isServerAvailable())
                || token == null || token.isEmpty()) {
            setLoading(false);
            return;
        }
        if (appendFinal) {
            isLoadingMore = true;
            // RecyclerView does not need setTranscriptMode
        } else {
            // 如果是刷新最新消息，重置offset
            currentOffset = 0;
        }
        setLoading(true);
        StringBuilder path = new StringBuilder("/direct/messages/v2?with_uid=");
        path.append(friendUID);
        path.append("&limit=").append(PAGE_LIMIT);
        path.append("&offset=").append(currentOffset);
        if (appendFinal && pendingJumpMessageId != null && !pendingJumpMessageId.isEmpty() && !pendingJumpAnchorUsed) {
            path.append("&anchor_message_id=").append(urlEncode(pendingJumpMessageId));
            pendingJumpAnchorUsed = true;
        }

        // RecyclerView 获取第一个可见项的位置
        int firstVisible = lastFirstVisible;
        int firstTop = lastFirstTop;
        LinearLayoutManager layoutManager = getLayoutManager();
        if (layoutManager != null) {
            int pos = layoutManager.findFirstVisibleItemPosition();
            if (pos >= 0) {
                View firstView = layoutManager.findViewByPosition(pos);
                firstVisible = pos;
                firstTop = firstView == null ? 0 : firstView.getTop();
            }
        }
        final int firstVisibleFinal = firstVisible;
        final int firstTopFinal = firstTop;
        final LinearLayoutManager layoutManagerFinal = layoutManager;

        HttpUtil.get(path.toString(), tokenFinal, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    List<Message> incoming = DirectMessageParser.parse(response);
                    int effectiveOffset = DirectMessageParser.parseEffectiveOffset(response, currentOffset);
                    // 接口返回通常是倒序（最新的在最前），转为正序（旧到新）方便处理
                    Collections.reverse(incoming);
                    int newIncomingCount = 0;
                    if (!appendFinal && keepPositionFinal && !incoming.isEmpty()) {
                        HashSet<String> existingIds = new HashSet<>(messageIds);
                        for (Message msg : incoming) {
                            if (msg.id == null || existingIds.contains(msg.id)) {
                                continue;
                            }
                            if (msg.from_uid == null || !MyUidStore.isMyUid(context, msg.from_uid, myUID)) {
                                newIncomingCount++;
                            }
                        }
                    }
                    int insertedCount = 0;
                    if (appendFinal) {
                        int previousSize = messageList.size();
                        DirectMessageMergeResult mergeResult = DirectMessageMerger.mergeAppend(
                                messageList, messageIds, incoming, currentOffset, PAGE_LIMIT);
                        currentOffset = mergeResult.currentOffset;
                        currentOffset = effectiveOffset + incoming.size();
                        hasMore = mergeResult.hasMore;
                        insertedCount = messageList.size() - previousSize;
                        if (insertedCount < 0) {
                            insertedCount = 0;
                        }
                        if (!hasMore && btnLoadMore != null) {
                            btnLoadMore.setEnabled(false);
                            btnLoadMore.setText(R.string.no_more_messages);
                        }
                    } else {
                        DirectMessageMergeResult mergeResult = DirectMessageMerger.mergeRefresh(
                                messageList, messageIds, incoming, PAGE_LIMIT, hasMore);
                        currentOffset = mergeResult.currentOffset;
                        hasMore = mergeResult.hasMore;
                        if (mergeResult.gapReset && btnLoadMore != null) {
                            btnLoadMore.setEnabled(true);
                            btnLoadMore.setText(R.string.load_more_messages);
                        }
                    }

                    if (!appendFinal) {
                        DirectMessageMerger.trimOldestInMemory(messageList, messageIds, MAX_ACTIVE_WINDOW_MESSAGES);
                    }
                    updateRecentFromMessages(incoming, appendFinal);
                    ChatMessageUtil.applyMessageStatus(context, messageList, myUID);
                    adapter.notifyDataSetChanged();
                    MessageHistoryCache.saveDirectMessages(context, friendUID, messageList);
                    
                    if (appendFinal) {
                        // RecyclerView does not need setTranscriptMode
                        int targetPos = firstVisibleFinal + insertedCount;
                        if (targetPos < 0) {
                            targetPos = 0;
                        }
                        if (targetPos >= messageList.size()) {
                            targetPos = messageList.size() - 1;
                        }
                        if (layoutManagerFinal != null) {
                            layoutManagerFinal.scrollToPositionWithOffset(targetPos, firstTopFinal);
                        } else {
                            lvMessages.scrollToPosition(targetPos);
                        }
                    } else {
                        if (!messageList.isEmpty()) {
                            if (keepPositionFinal) {
                                int targetPos = Math.min(firstVisibleFinal, messageList.size() - 1);
                                // RecyclerView does not need setTranscriptMode
                                if (layoutManagerFinal != null) {
                                    layoutManagerFinal.scrollToPositionWithOffset(targetPos, firstTopFinal);
                                } else {
                                    lvMessages.scrollToPosition(targetPos);
                                }
                            } else {
                                lvMessages.scrollToPosition(messageList.size() - 1);
                                // RecyclerView does not need setTranscriptMode
                                markRead(tokenFinal);
                            }
                        }
                    }
                    if (!appendFinal && keepPositionFinal && newIncomingCount > 0) {
                        newMessageCount += newIncomingCount;
                        showNewMessageBubble();
                    }
                } catch (Exception e) {
                    if (!silentFinal) {
                        Toast.makeText(context, "加载消息失败", Toast.LENGTH_SHORT).show();
                    }
                }
                setLoading(false);
                isLoadingMore = false;
                if (appendFinal) {
                    onLoadMoreCompleteForJump();
                }
            }

            @Override
            public void onError(int code, String error) {
                // 加载失败时，因为已经在开头加载了缓存，所以不需要在这里loadFromCache
                setLoading(false);
                isLoadingMore = false;
                pendingJumpToUnread = false;
                pendingJumpMessageId = null;
                pendingJumpMessageToken = null;
                pendingJumpAnchorUsed = false;
                if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                    return;
                }
                if (code == 404) {
                    Toast.makeText(context, "该用户不存在，可能已更改UID", Toast.LENGTH_LONG).show();
                    RecentChatCache.removeChat(context, friendUID);
                    if (context instanceof ChatActivity) {
                        ((ChatActivity) context).finish();
                    }
                    return;
                }
                if (!silentFinal) {
                    Toast.makeText(context, "同步失败: " + code, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

}
