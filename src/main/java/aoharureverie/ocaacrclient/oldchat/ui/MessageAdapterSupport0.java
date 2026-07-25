package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.models.Message;

import java.util.HashSet;
import java.util.List;

abstract class MessageAdapterSupport0 extends MessageAdapterSupport1 {
    protected static final int VIEW_TYPE_MESSAGE = 0;
    protected static final int VIEW_TYPE_TYPING = 1;
    protected static final int MAX_VOICE_SECONDS = 60;
    protected static final int VOICE_MIN_DP = 80;
    protected static final int VOICE_MAX_DP = 200;
    protected static final long JUMP_HIGHLIGHT_MS = 1200L;
    protected static final int JUMP_HIGHLIGHT_COLOR = 0x2AFFD54F;
    protected final HashSet<String> pendingSendAnimations = new HashSet<String>();
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
    protected MessageAdapter.MessageActionListener actionListener;

    MessageAdapterSupport0(Context context, List<Message> messages, String myUID) {
        this.context = context;
        this.messages = messages;
        this.myUID = myUID;
        this.voicePlayer = new MessageVoicePlayer(context, new MessageVoicePlayer.PlaybackListener() {
            @Override
            public void onPlaybackStateChanged() {
                notifyDataSetChanged();
            }
        });
    }

    public void setActionListener(MessageAdapter.MessageActionListener listener) {
        this.actionListener = listener;
    }

    public void setQuoteClickListener(MessageAdapter.QuoteClickListener listener) {
        this.quoteClickListener = listener;
    }

    public void setMyUID(String myUID) {
        if (this.myUID != null && this.myUID.equals(myUID)) {
            return;
        }
        this.myUID = myUID;
        notifyDataSetChanged();
    }

    public void setFontScale(float scale) {
        if (scale <= 0f) {
            scale = 1.0f;
        }
        if (Math.abs(this.fontScale - scale) < 0.01f) {
            return;
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

    public void updateMessages(List<Message> newMessages) {
        if (newMessages == null) {
            return;
        }
        int oldSize = messages.size();
        messages.clear();
        messages.addAll(newMessages);
        if (newMessages.size() > oldSize && oldSize > 0) {
            notifyItemRangeInserted(oldSize, newMessages.size() - oldSize);
        } else {
            notifyDataSetChanged();
        }
    }

    public void addMessage(Message message) {
        if (message == null) {
            return;
        }
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void markMessageAnimating(Message message) {
        String key = animationKeyForMessage(message);
        if (key != null && !key.isEmpty()) {
            pendingSendAnimations.add(key);
        }
    }

    public void updateMessageStatus(String messageId, int status) {
        if (messageId == null) {
            return;
        }
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (messageId.equals(msg.id)) {
                msg.status = status;
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void setTypingIndicatorVisible(boolean visible) {
        if (this.typingIndicatorVisible == visible) {
            return;
        }
        this.typingIndicatorVisible = visible;
        notifyDataSetChanged();
    }

    public boolean isTypingIndicatorVisible() {
        return typingIndicatorVisible;
    }

    public boolean startTypingTransition(Message message) {
        if (message == null) {
            return false;
        }
        if (!typingIndicatorVisible && !transitionRunning) {
            return false;
        }
        transitionMessage = message;
        transitionRunning = true;
        typingIndicatorVisible = false;
        notifyDataSetChanged();
        return true;
    }

    @Override
    public int getItemViewType(int position) {
        if (transitionRunning && position == messages.size() - 1) {
            return VIEW_TYPE_TYPING;
        }
        if (typingIndicatorVisible && position == getItemCount() - 1) {
            return VIEW_TYPE_TYPING;
        }
        return VIEW_TYPE_MESSAGE;
    }

    protected void maybeAnimateSend(ViewHolder holder, Message message, boolean isMine) {
        if (holder == null || holder.bubble == null) {
            return;
        }
        holder.bubble.clearAnimation();
        if (!isMine) {
            return;
        }
        String key = animationKeyForMessage(message);
        if (key == null || !pendingSendAnimations.remove(key)) {
            return;
        }
        AnimationSet set = new AnimationSet(true);
        set.setInterpolator(new DecelerateInterpolator());
        ScaleAnimation scale = new ScaleAnimation(
                0.92f, 1f, 0.92f, 1f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 1f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 1f);
        scale.setDuration(180);
        AlphaAnimation alpha = new AlphaAnimation(0.6f, 1f);
        alpha.setDuration(180);
        set.addAnimation(scale);
        set.addAnimation(alpha);
        holder.bubble.startAnimation(set);
    }

    private String animationKeyForMessage(Message message) {
        if (message == null) {
            return null;
        }
        if (message.id != null && !message.id.isEmpty()) {
            return "id:" + message.id;
        }
        if (message.created_at > 0) {
            return "ts:" + message.created_at + "_" + (message.from_uid == null ? "" : message.from_uid);
        }
        return "obj:" + System.identityHashCode(message);
    }

    protected void applyBubbleBackground(ViewHolder holder, int drawableRes) {
        if (holder == null || holder.bubble == null) {
            return;
        }
        holder.bubble.setBackgroundResource(drawableRes);
        holder.bubble.setPadding(holder.bubblePaddingLeft, holder.bubblePaddingTop,
                holder.bubblePaddingRight, holder.bubblePaddingBottom);
    }

    protected void clearBubbleBackground(ViewHolder holder) {
        if (holder == null || holder.bubble == null) {
            return;
        }
        holder.bubble.setBackgroundResource(0);
        holder.bubble.setPadding(0, 0, 0, 0);
    }

    protected void bindStatus(ViewHolder holder, int status) {
        if (holder == null) {
            return;
        }
        bindStatusViews(holder.statusText, holder.statusIcon, status);
    }

    protected void bindStatusViews(TextView statusText, ImageView statusIcon, int status) {
        if (statusText == null || statusIcon == null) {
            return;
        }
        if (status == Message.STATUS_SENT) {
            statusText.setText(context.getString(R.string.message_status_sent));
            statusText.setVisibility(View.VISIBLE);
            statusIcon.setVisibility(View.GONE);
            return;
        }
        statusText.setVisibility(View.GONE);
        statusIcon.setVisibility(View.VISIBLE);
        if (status == Message.STATUS_READ) {
            statusIcon.setImageResource(R.drawable.ic_msg_read);
        } else {
            statusIcon.setImageResource(R.drawable.ic_msg_sent);
        }
    }

    protected int textColorPrimary() {
        return ContextCompat.getColor(context, R.color.color_text_primary);
    }

    protected int textColorSecondary() {
        return ContextCompat.getColor(context, R.color.color_text_secondary);
    }

    protected int textColorAction() {
        return ContextCompat.getColor(context, R.color.colorPrimary);
    }

    protected void applyMineStyle(ViewHolder holder, String type, Message target, boolean useMediaStatus) {
        holder.messageContainer.setGravity(Gravity.END);
        if ("red_packet".equals(type)) {
            applyBubbleBackground(holder, R.drawable.bg_red_packet_out);
        } else {
            applyBubbleBackground(holder, R.drawable.bg_msg_me);
        }
        holder.text.setTextColor(0xFFFFFFFF);
        holder.voiceDuration.setTextColor(0xFFFFFFFF);
        holder.voiceIcon.setColorFilter(0xFFFFFFFF);
        holder.resourceTitle.setTextColor(0xFFFFFFFF);
        holder.resourceSub.setTextColor(0xCCFFFFFF);
        holder.resourceAction.setTextColor(0xFFFFFFFF);
        holder.resourceIcon.setColorFilter(0xFFFFFFFF);
        holder.redPacketTitle.setTextColor(0xFFFFFFFF);
        holder.redPacketDesc.setTextColor(0xCCFFFFFF);
        if (holder.redPacketOpenTip != null) {
            holder.redPacketOpenTip.setTextColor(0x99FFFFFF);
        }
        if (holder.redPacketStatusIcon != null && holder.redPacketStatusIcon.getVisibility() == View.VISIBLE) {
            holder.redPacketStatusIcon.setColorFilter(0x99FFFFFF);
        }
        if (useMediaStatus) {
            if (holder.statusRow != null) {
                holder.statusRow.setVisibility(View.GONE);
            }
            if (holder.statusRowMedia != null) {
                holder.statusRowMedia.setVisibility(View.VISIBLE);
                bindStatusViews(holder.statusTextMedia, holder.statusIconMedia, target.status);
            }
        } else {
            if (holder.statusRowMedia != null) {
                holder.statusRowMedia.setVisibility(View.GONE);
            }
            if (holder.statusRow != null) {
                holder.statusRow.setVisibility(View.VISIBLE);
                bindStatus(holder, target.status);
            }
        }
    }

    protected void applyOtherStyle(ViewHolder holder, String type) {
        holder.messageContainer.setGravity(Gravity.START);
        if ("red_packet".equals(type)) {
            applyBubbleBackground(holder, R.drawable.bg_red_packet_in);
        } else {
            applyBubbleBackground(holder, R.drawable.bg_msg_other);
        }
        int textColor = textColorPrimary();
        int subColor = textColorSecondary();
        int actionColor = textColorAction();
        holder.text.setTextColor(textColor);
        holder.voiceDuration.setTextColor(textColor);
        holder.voiceIcon.setColorFilter(textColor);
        holder.resourceTitle.setTextColor(textColor);
        holder.resourceSub.setTextColor(subColor);
        holder.resourceAction.setTextColor(actionColor);
        holder.resourceIcon.setColorFilter(actionColor);
        holder.redPacketTitle.setTextColor(0xFFFFFFFF);
        holder.redPacketDesc.setTextColor(0xCCFFFFFF);
        if (holder.redPacketOpenTip != null) {
            holder.redPacketOpenTip.setTextColor(0x99FFFFFF);
        }
        if (holder.redPacketStatusIcon != null) {
            holder.redPacketStatusIcon.setVisibility(View.GONE);
        }
        if (holder.statusRow != null) {
            holder.statusRow.setVisibility(View.GONE);
        }
        if (holder.statusRowMedia != null) {
            holder.statusRowMedia.setVisibility(View.GONE);
        }
    }
}
