package aoharureverie.ocaacrclient.oldchat.ui;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.GroupCache;
import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.models.GroupRecentChatCache;
import aoharureverie.ocaacrclient.oldchat.models.MessageHistoryCache;
import aoharureverie.ocaacrclient.oldchat.util.NetworkStateManager;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.List;

public class GroupChatListHelper extends GroupChatListHelperSupport {

    public GroupChatListHelper(Context context, ListView lvMessages, GroupMessageAdapter adapter,
                               List<GroupMessage> messageList, HashSet<String> messageIds,
                               TextView btnLoadMore, TextView btnNewMessage, ProgressBar loadingBar,
                               String groupId, String groupName, String myUID) {
        this.context = context;
        this.lvMessages = lvMessages;
        this.adapter = adapter;
        this.messageList = messageList;
        this.messageIds = messageIds;
        this.btnLoadMore = btnLoadMore;
        this.btnNewMessage = btnNewMessage;
        this.loadingBar = loadingBar;
        this.groupId = groupId;
        this.groupName = groupName;
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
        lvMessages.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_DISABLED);
        lvMessages.setSelectionFromTop(position, 0);
        lvMessages.post(new Runnable() {
            @Override
            public void run() {
                if (position < 0 || position >= messageList.size()) {
                    return;
                }
                lvMessages.setSelectionFromTop(position, 0);
            }
        });
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
            GroupMessage msg = messageList.get(i);
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
        if (groupId == null || groupId.isEmpty()) {
            return;
        }
        final boolean appendFinal = append;
        final boolean silentFinal = silent;
        final boolean keepPositionFinal = keepPosition;
        final String tokenFinal = token;
        
        // 1. 如果是首次加载（非加载更多），先从缓存加载显示，实现秒开
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
            lvMessages.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_DISABLED);
        } else {
            currentOffset = 0;
        }
        setLoading(true);
        StringBuilder path = new StringBuilder("/groups/messages/v2?group_id=");
        path.append(groupId);
        path.append("&limit=").append(PAGE_LIMIT);
        path.append("&offset=").append(currentOffset);
        if (appendFinal && pendingJumpMessageId != null && !pendingJumpMessageId.isEmpty() && !pendingJumpAnchorUsed) {
            path.append("&anchor_message_id=").append(urlEncode(pendingJumpMessageId));
            pendingJumpAnchorUsed = true;
        }
        final int firstVisible = lvMessages.getFirstVisiblePosition();
        final View firstView = lvMessages.getChildAt(0);
        final int firstTop = firstView == null ? 0 : firstView.getTop();

        HttpUtil.get(path.toString(), token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    List<GroupMessage> incoming = GroupMessageSyncHelper.parseIncoming(response);
                    int effectiveOffset = GroupMessageSyncHelper.parseEffectiveOffset(response, currentOffset);
                    if (!appendFinal && !keepPositionFinal && incoming.isEmpty() && !messageList.isEmpty()) {
                        // 首刷偶发空列表时，若本地已有消息则立即重拉一次，避免进入页面空白
                        loadMessages(tokenFinal, false, 0, true, true);
                    }
                    int newIncomingCount = 0;
                    if (!appendFinal && keepPositionFinal && !incoming.isEmpty()) {
                        newIncomingCount = GroupMessageSyncHelper.countIncomingForBubble(
                                context, myUID, incoming, messageIds);
                    }
                    int insertedCount = 0;
                    if (appendFinal) {
                        int previousSize = messageList.size();
                        GroupMessageMergeResult mergeResult = GroupMessageSyncHelper.mergeAppend(
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
                        GroupMessageMergeResult mergeResult = GroupMessageSyncHelper.mergeRefresh(
                                messageList, messageIds, incoming, PAGE_LIMIT, hasMore);
                        currentOffset = mergeResult.currentOffset;
                        hasMore = mergeResult.hasMore;
                        if (mergeResult.gapReset && btnLoadMore != null) {
                            btnLoadMore.setEnabled(true);
                            btnLoadMore.setText(R.string.load_more_messages);
                        }
                    }
                    
                    updateRecentFromMessages(incoming, appendFinal);
                    adapter.notifyDataSetChanged();
                    MessageHistoryCache.saveGroupMessages(context, groupId, messageList);
                    
                    if (appendFinal) {
                        lvMessages.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_DISABLED);
                        int targetPos = firstVisible + insertedCount;
                        if (targetPos < 0) {
                            targetPos = 0;
                        }
                        if (targetPos >= messageList.size()) {
                            targetPos = messageList.size() - 1;
                        }
                        lvMessages.setSelectionFromTop(targetPos, firstTop);
                    } else {
                        if (!messageList.isEmpty()) {
                            if (keepPositionFinal) {
                                int targetPos = Math.min(firstVisible, messageList.size() - 1);
                                lvMessages.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_DISABLED);
                                lvMessages.setSelectionFromTop(targetPos, firstTop);
                            } else {
                                lvMessages.setSelection(messageList.size() - 1);
                                lvMessages.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
                                markRead(tokenFinal);
                            }
                        }
                    }
                    if (!appendFinal && keepPositionFinal && newIncomingCount > 0) {
                        newMessageCount += newIncomingCount;
                        showNewMessageBubble();
                    }
                } catch (Exception e) {
                    if (!silentFinal && isContextValid()) {
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
                    GroupRecentChatCache.remove(context, groupId);
                    GroupCache.remove(context, groupId);
                    MessageHistoryCache.removeGroupMessages(context, groupId);
                    if (context instanceof Activity) {
                        ((Activity) context).finish();
                    }
                    return;
                }
                if (!silentFinal && isContextValid()) {
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
