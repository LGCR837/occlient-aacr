package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.WSManager;
import aoharureverie.ocaacrclient.oldchat.models.GroupMember;
import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.models.GroupRecentChatCache;
import aoharureverie.ocaacrclient.oldchat.models.UserNameCache;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

abstract class GroupChatActivitySupport0 extends BaseActivity {
    protected static final int REQ_PICK_EMOJI = 3201;
    protected static final int REQ_SEND_RED_PACKET = 3202;
    protected static final int REQ_PICK_CHAT_BG = 3004;
    protected static final int REQ_GROUP_MANAGE = 3005;
    protected static final int ANNOUNCEMENT_MODE_OPTIONAL = 0;
    protected static final int ANNOUNCEMENT_MODE_REQUIRED = 1;
    protected static final long TYPING_IDLE_MS = 3000;

    protected ListView lvMessages;
    protected EditText etInput;
    protected String lastDraft = "";
    protected Button btnSend;
    protected TextView btnLoadMore;
    protected ProgressBar pbSend;
    protected ProgressBar pbMessagesLoading;
    protected View quotePreview;
    protected TextView tvQuotePreview;
    protected View btnQuoteClose;
    protected View chatBackground;
    protected TextView tvTitle;
    protected MessagePayload.Quote quoteDraft;
    protected int sendingCount = 0;
    protected ChatMediaHelper mediaHelper;
    protected GroupMessageAdapter adapter;
    protected final List<GroupMessage> messageList = new ArrayList<>();
    protected final HashSet<String> messageIds = new HashSet<>();
    protected final Map<String, String> nameMap = new HashMap<>();
    protected final Map<String, String> avatarMap = new HashMap<>();
    protected final Map<String, String> titleMap = new HashMap<>();
    protected final Map<String, Integer> roleMap = new HashMap<>();
    protected final List<GroupMember> mentionMembers = new ArrayList<>();
    protected final List<MessagePayload.Mention> mentionDrafts = new ArrayList<>();
    protected String groupId;
    protected String groupName;
    protected String groupAvatar;
    protected String token;
    protected String myUID;
    protected int myRole = 0;
    protected boolean needsRefreshOnResume = false;
    protected WSManager.Listener wsListener;
    protected View backDot;
    protected TextView btnNewMessage;
    protected TextView btnJumpToUnread;
    protected GroupChatListHelper listHelper;
    protected GroupMessageSender messageSender;
    protected ChatBackgroundHelper backgroundHelper;
    protected GroupManageApi manageApi;
    protected View announcementBanner;
    protected TextView tvAnnouncementBanner;
    protected String announcementText = "";
    protected int announcementMode = ANNOUNCEMENT_MODE_OPTIONAL;
    protected long announcementUpdatedAt = 0;
    protected long announcementReadAt = 0;
    protected boolean announcementDialogShowing = false;
    protected float inputBaseSizePx;
    protected float quotePreviewBaseSizePx;
    protected android.support.v7.app.AlertDialog mentionDialog;
    protected GroupMentionAdapter mentionAdapter;
    protected boolean suppressMentionTrigger = false;
    protected boolean isTyping = false;
    protected final LinkedHashSet<String> typingUsers = new LinkedHashSet<>();
    protected aoharureverie.ocaacrclient.oldchat.util.TypingStatusManager typingStatusManager;
    protected final Handler typingHandler = new Handler(Looper.getMainLooper());
    protected final Runnable typingIdleRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isTyping) {
                return;
            }
            if (typingStatusManager != null && groupId != null && !groupId.isEmpty()) {
                typingStatusManager.stopTyping(GroupChatActivitySupport0.this, token, groupId, true);
            }
            isTyping = false;
        }
    };
    protected final WSManager.Listener backDotListener = UnreadDotHelper.createBackDotListener(new Runnable() {
        @Override
        public void run() {
            updateBackDot();
        }
    });

    protected void setupLoadMoreHeader() {
        View header = getLayoutInflater().inflate(R.layout.list_load_more, lvMessages, false);
        btnLoadMore = header.findViewById(R.id.btnLoadMore);
        btnLoadMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listHelper != null && listHelper.canLoadMore()) {
                    long before = listHelper.getOldestTimestamp();
                    listHelper.loadMessages(token, true, before, false);
                }
            }
        });
        lvMessages.addHeaderView(header);
    }

    protected void openManage() {
        Intent intent = new Intent(this, GroupManageActivity.class);
        intent.putExtra("group_id", groupId);
        intent.putExtra("group_name", groupName);
        intent.putExtra("group_role", myRole);
        startActivityForResult(intent, REQ_GROUP_MANAGE);
    }

    protected void setSending(boolean sending) {
        if (sending) {
            sendingCount++;
        } else {
            sendingCount = Math.max(0, sendingCount - 1);
        }
        if (pbSend != null) {
            pbSend.setVisibility(sendingCount > 0 ? View.VISIBLE : View.GONE);
        }
        if (btnSend != null) {
            btnSend.setEnabled(sendingCount == 0);
        }
    }

    protected void openEmojiPicker() {
        Intent intent = new Intent(this, EmojiPickerActivity.class);
        startActivityForResult(intent, REQ_PICK_EMOJI);
    }

    protected void openRedPacketSend() {
        if (groupId == null || groupId.isEmpty()) {
            return;
        }
        Intent intent = new Intent(this, RedPacketSendActivity.class);
        intent.putExtra(RedPacketSendActivity.EXTRA_GROUP_ID, groupId);
        intent.putExtra(RedPacketSendActivity.EXTRA_TARGET_NAME, groupName == null ? "" : groupName);
        startActivityForResult(intent, REQ_SEND_RED_PACKET);
    }

    protected String messagePreview(String type, String body) {
        return ChatMessageUtil.previewForType(type, body);
    }

    protected void updateRecentFromLastMessage() {
        if (groupId == null || groupId.isEmpty() || messageList.isEmpty()) {
            return;
        }
        GroupMessage last = messageList.get(messageList.size() - 1);
        if (last == null) {
            return;
        }
        String name = groupName != null && !groupName.isEmpty() ? groupName : groupId;
        String preview = messagePreview(last.msg_type, last.body);
        if (MyUidStore.isMyUid(this, last.from_uid, myUID)) {
            GroupRecentChatCache.updateGroupOutgoing(this, groupId, name, groupAvatar, preview, last.created_at);
        } else {
            GroupRecentChatCache.updateGroupIncoming(this, groupId, name, groupAvatar, preview, last.created_at, 0);
        }
        GroupRecentChatCache.clearUnread(this, groupId);
    }

    protected void quoteMessage(GroupMessage msg, String displayName) {
        quoteDraft = GroupChatUiActions.buildQuote(msg, displayName);
        GroupChatUiActions.applyQuote(quotePreview, tvQuotePreview, quoteDraft);
    }

    protected void recallMessage(GroupMessage msg) {
        if (msg == null || msg.id == null) {
            return;
        }
        GroupChatUiActions.confirmRecall(asGroupChatActivity(), token, messageList, messageIds, adapter, msg.id,
                resolveDisplayName(msg.from_uid));
    }

    protected void reEditRecalledMessage(GroupMessage msg) {
        if (msg == null || etInput == null) {
            return;
        }
        String type = msg.recall_edit_type == null ? "text" : msg.recall_edit_type.toLowerCase();
        String content = msg.recall_edit_text == null ? "" : msg.recall_edit_text.trim();
        if (!"text".equals(type) || content.length() == 0) {
            Toast.makeText(this, "该消息暂不支持重新编辑", Toast.LENGTH_SHORT).show();
            return;
        }
        etInput.requestFocus();
        etInput.setText(content);
        etInput.setSelection(content.length());
        if (quoteDraft != null) {
            clearQuoteDraft();
        }
    }

    protected void clearQuoteDraft() {
        quoteDraft = null;
        GroupChatUiActions.clearQuote(quotePreview, tvQuotePreview);
    }

    protected void showChatBackgroundDialog() {
        if (backgroundHelper != null) {
            backgroundHelper.showBackgroundDialog(REQ_PICK_CHAT_BG);
        }
    }

    protected void pickChatBackground() {
        if (backgroundHelper != null) {
            backgroundHelper.pickBackground(REQ_PICK_CHAT_BG);
        }
    }

    protected void clearChatBackground() {
        if (backgroundHelper != null) {
            backgroundHelper.clearBackground();
        }
    }

    protected void applyChatBackground() {
        if (backgroundHelper != null) {
            backgroundHelper.applyBackground();
        }
    }

    protected void applyFontScale() {
        float scale = aoharureverie.ocaacrclient.oldchat.util.SettingsPrefs.getFontScale(this);
        if (etInput != null) {
            if (inputBaseSizePx == 0f) {
                inputBaseSizePx = etInput.getTextSize();
            }
            etInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, inputBaseSizePx * scale);
        }
        if (tvQuotePreview != null) {
            if (quotePreviewBaseSizePx == 0f) {
                quotePreviewBaseSizePx = tvQuotePreview.getTextSize();
            }
            tvQuotePreview.setTextSize(TypedValue.COMPLEX_UNIT_PX, quotePreviewBaseSizePx * scale);
        }
        if (adapter != null) {
            adapter.setFontScale(scale);
        }
    }

    protected void sendFromInput() {
        if (mediaHelper != null) {
            mediaHelper.hideActionPanel();
        }
        if (messageSender != null) {
            String content = etInput == null ? "" : etInput.getText().toString();
            messageSender.sendText(content, quoteDraft, collectMentions(content));
        }
    }

    protected String getDraftKey() {
        if (groupId == null || groupId.length() == 0) {
            return null;
        }
        String owner = myUID == null ? "" : myUID;
        return "group:" + owner + ":" + groupId;
    }

    protected String resolveDisplayName(String uid) {
        if (uid == null || uid.isEmpty()) {
            return "成员";
        }
        String name = nameMap.get(uid);
        if (name == null || name.isEmpty()) {
            name = UserNameCache.getName(this, uid);
        }
        if (name == null || name.isEmpty()) {
            name = uid;
        }
        return name;
    }

    protected void updateBackDot() {
        if (backDot == null) {
            return;
        }
        boolean hasUnread = UnreadDotHelper.hasOtherUnread(this, null, groupId);
        backDot.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
    }

    protected GroupChatActivity asGroupChatActivity() {
        return (GroupChatActivity) this;
    }

    protected abstract List<MessagePayload.Mention> collectMentions(String content);
}
