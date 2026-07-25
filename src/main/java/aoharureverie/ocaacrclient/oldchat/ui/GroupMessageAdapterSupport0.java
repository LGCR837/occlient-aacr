package aoharureverie.ocaacrclient.oldchat.ui;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

abstract class GroupMessageAdapterSupport0 extends GroupMessageAdapterSupport1 {
    protected static final long JUMP_HIGHLIGHT_MS = 1200L;
    protected static final int JUMP_HIGHLIGHT_COLOR = 0x2AFFD54F;
    protected final Handler mainHandler = new Handler(Looper.getMainLooper());
    protected String jumpHighlightMessageId;
    protected long jumpHighlightUntilMs;
    protected final Runnable clearJumpHighlightRunnable = new Runnable() {
        
        public void run() {
            if (jumpHighlightMessageId == null) {
                return;
            }
            jumpHighlightMessageId = null;
            jumpHighlightUntilMs = 0L;
            notifyDataSetChanged();
        }
    };
    GroupMessageAdapterSupport0(android.content.Context context, List<GroupMessage> messages, String myUID) {
        super(context, messages, myUID);
    }

    public void setActionListener(GroupMessageAdapter.GroupMessageActionListener listener) {
        this.actionListener = listener;
    }

    public void setQuoteClickListener(GroupMessageAdapter.QuoteClickListener listener) {
        this.quoteClickListener = listener;
    }

    public void setMyUID(String myUID) {
        this.myUID = myUID;
        notifyDataSetChanged();
    }

    public void setFontScale(float scale) {
        if (scale <= 0f) {
            scale = 1.0f;
        }
        this.fontScale = scale;
        notifyDataSetChanged();
    }

    public void highlightJumpMessage(String messageId) {
        if (messageId == null || messageId.length() == 0) {
            return;
        }
        jumpHighlightMessageId = messageId;
        jumpHighlightUntilMs = System.currentTimeMillis() + JUMP_HIGHLIGHT_MS;
        mainHandler.removeCallbacks(clearJumpHighlightRunnable);
        mainHandler.postDelayed(clearJumpHighlightRunnable, JUMP_HIGHLIGHT_MS + 80L);
        notifyDataSetChanged();
    }

    protected void bindJumpHighlight(View rowView, String messageId) {
        if (rowView == null) {
            return;
        }
        if (shouldHighlightMessage(messageId)) {
            rowView.setBackgroundColor(JUMP_HIGHLIGHT_COLOR);
        } else {
            rowView.setBackgroundColor(0x00000000);
        }
    }

    protected boolean shouldHighlightMessage(String messageId) {
        if (messageId == null || messageId.length() == 0) {
            return false;
        }
        if (jumpHighlightMessageId == null || !messageId.equals(jumpHighlightMessageId)) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now > jumpHighlightUntilMs) {
            jumpHighlightMessageId = null;
            jumpHighlightUntilMs = 0L;
            return false;
        }
        return true;
    }

    public void stopVoice() {
        if (voicePlayer != null) {
            voicePlayer.stop();
        }
        mainHandler.removeCallbacks(clearJumpHighlightRunnable);
    }

    public void updateNameMap(Map<String, String> map) {
        if (map != null) {
            nameMap = map;
            notifyDataSetChanged();
        }
    }

    public void updateAvatarMap(Map<String, String> map) {
        if (map != null) {
            avatarMap = map;
            notifyDataSetChanged();
        }
    }

    public void updateTitleMap(Map<String, String> map) {
        if (map != null) {
            titleMap = map;
            notifyDataSetChanged();
        }
    }

    public void updateRoleMap(Map<String, Integer> map) {
        if (map != null) {
            roleMap = map;
            notifyDataSetChanged();
        }
    }

    public void setMyRole(int role) {
        if (myRole != role) {
            myRole = role;
            notifyDataSetChanged();
        }
    }

    public void setTypingIndicators(Collection<String> uids) {
        LinkedHashSet<String> next = new LinkedHashSet<String>();
        if (uids != null) {
            for (String uid : uids) {
                if (uid != null && !uid.isEmpty()) {
                    next.add(uid);
                }
            }
        }
        if (next.equals(typingUids)) {
            return;
        }
        typingUids.clear();
        typingUids.addAll(next);
        notifyDataSetChanged();
    }

    public void setTypingIndicator(String uid) {
        if (uid == null || uid.isEmpty()) {
            clearTypingIndicator();
            return;
        }
        LinkedHashSet<String> next = new LinkedHashSet<String>();
        next.add(uid);
        if (next.equals(typingUids)) {
            return;
        }
        typingUids.clear();
        typingUids.add(uid);
        notifyDataSetChanged();
    }

    public boolean isTypingIndicatorVisible() {
        return !getDisplayTypingUids().isEmpty();
    }

    public boolean startTypingTransition(GroupMessage message) {
        if (message == null || message.from_uid == null || message.from_uid.isEmpty()) {
            return false;
        }
        if (!typingUids.contains(message.from_uid)) {
            return false;
        }
        if (transitionRunning) {
            return false;
        }
        transitionMessage = message;
        transitionRunning = true;
        transitionUid = message.from_uid;
        notifyDataSetChanged();
        return true;
    }

    public void clearTypingIndicator() {
        if (typingUids.isEmpty()) {
            return;
        }
        typingUids.clear();
        notifyDataSetChanged();
    }

    public void markMessageAnimating(GroupMessage message) {
        String key = animationKeyForMessage(message);
        if (key != null && !key.isEmpty()) {
            pendingSendAnimations.add(key);
        }
    }

    @Override
    public int getCount() {
        int count = messages.size();
        return count + (isTypingIndicatorVisible() ? 1 : 0);
    }

    @Override
    public Object getItem(int position) {
        if (isTypingPosition(position) || (transitionRunning && position == messages.size() - 1)) {
            return null;
        }
        if (position >= 0 && position < messages.size()) {
            return messages.get(position);
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        if (transitionRunning && position == messages.size() - 1) {
            return VIEW_TYPE_TYPING;
        }
        if (isTypingPosition(position)) {
            return VIEW_TYPE_TYPING;
        }
        return VIEW_TYPE_MESSAGE;
    }

    protected void bindItemActions(ViewHolder holderFinal, final GroupMessage target, MessagePayload payload) {
        final boolean isMine = MyUidStore.isMyUid(context, target.from_uid, myUID);
        GroupMessageActionBinder.bind(context,
                holderFinal.bubble,
                holderFinal.text,
                holderFinal.image,
                holderFinal.voiceRow,
                holderFinal.avatar,
                target,
                payload,
                isMine,
                actionListener,
                new GroupMessageActionBinder.NameResolver() {
                    @Override
                    public String resolve(String uid) {
                        return resolveName(uid);
                    }
                },
                new GroupMessageActionBinder.RecallChecker() {
                    @Override
                    public boolean canRecall(GroupMessage msg) {
                        return canRecallMessage(msg);
                    }
                });
        maybeAnimateSend(holderFinal, target, isMine);
    }
}
