package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.WSManager;
import aoharureverie.ocaacrclient.oldchat.models.Message;
import aoharureverie.ocaacrclient.oldchat.models.RecentChatCache;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;
import aoharureverie.ocaacrclient.oldchat.util.SettingsPrefs;
import aoharureverie.ocaacrclient.oldchat.util.TypingStatusManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

abstract class ChatActivitySupport extends BaseActivity {
    protected static final int REQ_PICK_EMOJI = 3101;
    protected static final int REQ_SEND_RED_PACKET = 3102;
    protected static final int REQ_PICK_CHAT_BG = 3004;
    protected static final int REQ_CHAT_SETTINGS = 3005;
    protected static final long TYPING_IDLE_MS = 3000;

    protected android.support.v7.widget.RecyclerView lvMessages;
    protected EditText etInput;
    protected String lastDraft = "";
    protected Button btnSend;
    protected TextView btnLoadMore;
    protected ProgressBar pbSend;
    protected ProgressBar pbMessagesLoading;
    protected View quotePreview;
    protected TextView tvQuotePreview;
    protected MessagePayload.Quote quoteDraft;
    protected int sendingCount = 0;
    protected ChatMediaHelper mediaHelper;
    protected MessageAdapter adapter;
    protected final List<Message> messageList = new ArrayList<>();
    protected final HashSet<String> messageIds = new HashSet<>();
    protected String friendUID;
    protected String friendName;
    protected String friendAvatar;
    protected String myUID;
    protected String token;
    protected boolean needsRefreshOnResume = false;
    protected ChatWsListener wsListener;
    protected View backDot;
    protected TextView btnNewMessage;
    protected TextView btnJumpToUnread;
    protected DirectChatListHelper listHelper;
    protected DirectMessageSender messageSender;
    protected ChatBackgroundHelper backgroundHelper;
    protected float inputBaseSizePx;
    protected float quotePreviewBaseSizePx;
    protected boolean isTyping = false;
    protected TypingStatusManager typingStatusManager;
    protected final Handler typingHandler = new Handler(Looper.getMainLooper());
    protected final Runnable typingIdleRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isTyping) {
                return;
            }
            if (typingStatusManager != null && friendUID != null && !friendUID.isEmpty()) {
                typingStatusManager.stopTyping(ChatActivitySupport.this, token, friendUID, false);
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
        btnLoadMore = null;
    }

    protected void openFriendSpace() {
        if (friendUID == null || friendUID.isEmpty()) {
            return;
        }
        Intent intent = new Intent(this, UserSpaceActivity.class);
        intent.putExtra("uid", friendUID);
        startActivity(intent);
    }

    protected void openRedPacketSend() {
        if (friendUID == null || friendUID.isEmpty()) {
            return;
        }
        Intent intent = new Intent(this, RedPacketSendActivity.class);
        intent.putExtra(RedPacketSendActivity.EXTRA_TO_UID, friendUID);
        intent.putExtra(RedPacketSendActivity.EXTRA_TARGET_NAME, friendName == null ? "" : friendName);
        startActivityForResult(intent, REQ_SEND_RED_PACKET);
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

    protected void updateRecentFromLastMessage() {
        if (friendUID == null || friendUID.isEmpty() || messageList.isEmpty()) {
            return;
        }
        Message last = messageList.get(messageList.size() - 1);
        if (last == null) {
            return;
        }
        String name = friendName != null && !friendName.isEmpty() ? friendName : friendUID;
        String preview = ChatMessageUtil.previewForMessage(last);
        if (MyUidStore.isMyUid(this, last.from_uid, myUID)) {
            RecentChatCache.updateRecentChatOutgoing(this, friendUID, name, friendAvatar, preview, last.created_at);
        } else {
            RecentChatCache.updateRecentChatIncoming(this, friendUID, name, friendAvatar, preview, last.created_at, 0);
        }
        RecentChatCache.clearUnread(this, friendUID);
    }

    protected void quoteMessage(Message msg) {
        quoteDraft = DirectChatUiActions.buildQuote(msg, friendName);
        DirectChatUiActions.applyQuote(quotePreview, tvQuotePreview, quoteDraft);
    }

    protected void recallMessage(Message msg) {
        if (msg == null || msg.id == null) {
            return;
        }
        DirectChatUiActions.confirmRecall(asChatActivity(), token, friendUID, messageList, messageIds, adapter, msg.id);
    }

    protected void reEditRecalledMessage(Message msg) {
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
        DirectChatUiActions.clearQuote(quotePreview, tvQuotePreview);
    }

    protected void openEmojiPicker() {
        Intent intent = new Intent(this, EmojiPickerActivity.class);
        startActivityForResult(intent, REQ_PICK_EMOJI);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    protected void openChatSettings() {
        Intent intent = new Intent(this, ChatSettingsActivity.class);
        intent.putExtra("friend_uid", friendUID);
        intent.putExtra("friend_name", friendName);
        intent.putExtra("friend_avatar", friendAvatar);
        startActivityForResult(intent, REQ_CHAT_SETTINGS);
    }

    protected void applyFontScale() {
        float scale = SettingsPrefs.getFontScale(this);
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
        if (messageSender != null && etInput != null) {
            messageSender.sendText(etInput.getText().toString(), quoteDraft);
        }
    }

    protected void setupTypingWatcher() {
        if (etInput == null) {
            return;
        }
        etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (friendUID == null || friendUID.isEmpty()) {
                    return;
                }
                if (!SettingsPrefs.isTypingIndicatorEnabled(ChatActivitySupport.this)) {
                    if (isTyping && typingStatusManager != null) {
                        typingStatusManager.stopTyping(ChatActivitySupport.this, token, friendUID, false);
                        isTyping = false;
                    }
                    typingHandler.removeCallbacks(typingIdleRunnable);
                    return;
                }
                String text = s == null ? "" : s.toString().trim();
                if (text.length() > 0 && !isTyping) {
                    isTyping = true;
                    if (typingStatusManager != null) {
                        typingStatusManager.startTyping(ChatActivitySupport.this, token, friendUID, false);
                    }
                    scheduleTypingIdleStop();
                } else if (text.length() == 0 && isTyping) {
                    isTyping = false;
                    if (typingStatusManager != null) {
                        typingStatusManager.stopTyping(ChatActivitySupport.this, token, friendUID, false);
                    }
                    typingHandler.removeCallbacks(typingIdleRunnable);
                } else if (text.length() > 0 && isTyping) {
                    scheduleTypingIdleStop();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                lastDraft = ChatDraftHelper.saveDraft(ChatActivitySupport.this, getDraftKey(),
                        s == null ? "" : s.toString(), lastDraft);
            }
        });
    }

    protected String getDraftKey() {
        if (friendUID == null || friendUID.length() == 0) {
            return null;
        }
        String owner = myUID == null ? "" : myUID;
        return "direct:" + owner + ":" + friendUID;
    }

    protected void scheduleTypingIdleStop() {
        typingHandler.removeCallbacks(typingIdleRunnable);
        typingHandler.postDelayed(typingIdleRunnable, TYPING_IDLE_MS);
    }

    protected void setTypingIndicatorVisible(boolean visible) {
        if (adapter != null) {
            adapter.setTypingIndicatorVisible(visible);
        }
        if (visible && listHelper != null && listHelper.isAtBottom() && lvMessages != null) {
            lvMessages.post(new Runnable() {
                @Override
                public void run() {
                    int last = adapter != null ? adapter.getItemCount() - 1 : -1;
                    if (last >= 0) {
                        lvMessages.scrollToPosition(last);
                    }
                }
            });
        }
    }

    protected void refreshMyUID() {
        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        String current = prefs.getString("my_uid", "");
        if (current == null || current.isEmpty() || current.equals(myUID)) {
            return;
        }
        myUID = current;
        if (adapter != null) {
            adapter.setMyUID(myUID);
        }
        if (listHelper != null) {
            listHelper.setMyUID(myUID);
        }
        if (wsListener != null) {
            wsListener.setMyUID(myUID);
        }
        ChatMessageUtil.applyMessageStatus(this, messageList, myUID);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    protected void updateBackDot() {
        if (backDot == null) {
            return;
        }
        boolean hasUnread = UnreadDotHelper.hasOtherUnread(this, friendUID, null);
        backDot.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
    }

    protected ChatActivity asChatActivity() {
        return (ChatActivity) this;
    }
}
