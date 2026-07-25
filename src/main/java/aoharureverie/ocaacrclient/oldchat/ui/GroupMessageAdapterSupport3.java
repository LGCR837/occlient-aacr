package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;
import aoharureverie.ocaacrclient.oldchat.util.TypingAnimator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

abstract class GroupMessageAdapterSupport3 extends android.widget.BaseAdapter {
    protected static final int PAYLOAD_CACHE_LIMIT = 400;
    protected static final int VIEW_TYPE_MESSAGE = 0;
    protected static final int VIEW_TYPE_TYPING = 1;
    protected static final int MAX_VOICE_SECONDS = 60;
    protected static final int VOICE_MIN_DP = 80;
    protected static final int VOICE_MAX_DP = 200;
    protected static final float BASE_MESSAGE_TEXT_SP = 16f;
    protected static final float BASE_SENDER_NAME_SP = 12f;
    protected static final float BASE_QUOTE_SENDER_SP = 12f;
    protected static final float BASE_QUOTE_CONTENT_SP = 13f;
    protected static final float BASE_VOICE_DURATION_SP = 14f;
    protected static final float BASE_RESOURCE_TITLE_SP = 15f;
    protected static final float BASE_RESOURCE_SUB_SP = 12f;
    protected static final float BASE_RESOURCE_ACTION_SP = 12f;
    protected static final long TYPING_TRANSITION_MS = 200;
    protected static final int MESSAGE_TEXT_MAX_DP = 260;
    protected static final int MESSAGE_TEXT_MIN_DP = 150;
    protected static final int QUOTE_TEXT_MAX_DP = 240;
    protected static final int QUOTE_TEXT_MIN_DP = 140;
    protected static final int OUTGOING_AVATAR_SLOT_DP = 42;
    protected static final int BUBBLE_HORIZONTAL_PADDING_DP = 20;
    protected static final int ROW_HORIZONTAL_PADDING_DP = 8;
    protected static final int READ_STATUS_RESERVED_DP = 36;
    protected static final int WIDTH_SAFETY_MARGIN_DP = 6;

    protected final Context context;
    protected final List<GroupMessage> messages;
    protected String myUID;
    protected int myRole = 0;
    protected Map<String, String> nameMap = new HashMap<String, String>();
    protected Map<String, String> avatarMap = new HashMap<String, String>();
    protected Map<String, String> titleMap = new HashMap<String, String>();
    protected Map<String, Integer> roleMap = new HashMap<String, Integer>();
    protected final Map<String, CachedPayload> payloadCache = new HashMap<String, CachedPayload>();
    protected final GroupMessageVoicePlayer voicePlayer;
    protected GroupMessageAdapter.GroupMessageActionListener actionListener;
    protected float fontScale = 1.0f;
    protected final LinkedHashSet<String> typingUids = new LinkedHashSet<String>();
    protected GroupMessage transitionMessage;
    protected boolean transitionRunning = false;
    protected String transitionUid;
    protected final HashSet<String> pendingSendAnimations = new HashSet<String>();
    protected GroupMessageAdapter.QuoteClickListener quoteClickListener;

    GroupMessageAdapterSupport3(Context context, List<GroupMessage> messages, String myUID) {
        this.context = context;
        this.messages = messages;
        this.myUID = myUID;
        this.voicePlayer = new GroupMessageVoicePlayer(context, new GroupMessageVoicePlayer.PlaybackListener() {
            @Override
            public void onPlaybackStateChanged() {
                notifyDataSetChanged();
            }
        });
    }

    protected static class ViewHolder {
        final LinearLayout senderRow;
        final TextView senderName;
        final TextView senderBadge;
        final TextView senderTitle;
        final ImageView avatar;
        final ImageView avatarRight;
        final LinearLayout contentContainer;
        final TextView text;
        final LinearLayout bubble;
        final LinearLayout resourceCard;
        final ImageView resourceIcon;
        final TextView resourceTitle;
        final TextView resourceSub;
        final TextView resourceAction;
        final View resourceCoverContainer;
        final ImageView resourceCover;
        final ImageView resourcePlay;
        final LinearLayout redPacketContainer;
        final ImageView redPacketIcon;
        final TextView redPacketTitle;
        final TextView redPacketDesc;
        final TextView redPacketOpenTip;
        final ImageView redPacketStatusIcon;
        final LinearLayout quoteContainer;
        final TextView quoteSender;
        final TextView quoteContent;
        final ImageView quoteImage;
        final View imageContainer;
        final ImageView image;
        final ImageView videoPlay;
        final LinearLayout voiceRow;
        final ImageView voiceIcon;
        final TextView voiceDuration;
        final ProgressBar voiceLoading;
        final LinearLayout statusRow;
        final ImageView statusIcon;
        final TextView readCount;
        final LinearLayout statusRowMedia;
        final ImageView statusIconMedia;
        final TextView readCountMedia;
        final TextView messageTime;
        final LinearLayout messageContainer;
        final int messageContainerPaddingLeft;
        final int messageContainerPaddingTop;
        final int messageContainerPaddingRight;
        final int messageContainerPaddingBottom;
        final int bubblePaddingLeft;
        final int bubblePaddingTop;
        final int bubblePaddingRight;
        final int bubblePaddingBottom;

        ViewHolder(View view) {
            messageContainer = view.findViewById(R.id.message_container);
            senderRow = view.findViewById(R.id.sender_row);
            senderName = view.findViewById(R.id.tvSenderName);
            senderBadge = view.findViewById(R.id.tvSenderBadge);
            senderTitle = view.findViewById(R.id.tvSenderTitle);
            avatar = view.findViewById(R.id.ivAvatar);
            avatarRight = view.findViewById(R.id.ivAvatarRight);
            contentContainer = view.findViewById(R.id.content_container);
            text = view.findViewById(R.id.tvMessage);
            bubble = view.findViewById(R.id.message_bubble);
            resourceCard = view.findViewById(R.id.resource_card);
            resourceIcon = view.findViewById(R.id.ivResourceIcon);
            resourceTitle = view.findViewById(R.id.tvResourceTitle);
            resourceSub = view.findViewById(R.id.tvResourceSub);
            resourceAction = view.findViewById(R.id.tvResourceAction);
            resourceCoverContainer = view.findViewById(R.id.flResourceCover);
            resourceCover = view.findViewById(R.id.ivResourceCover);
            resourcePlay = view.findViewById(R.id.ivResourcePlay);
            redPacketContainer = view.findViewById(R.id.red_packet_container);
            redPacketIcon = view.findViewById(R.id.ivRedPacketIcon);
            redPacketTitle = view.findViewById(R.id.tvRedPacketTitle);
            redPacketDesc = view.findViewById(R.id.tvRedPacketDesc);
            redPacketOpenTip = view.findViewById(R.id.tvRedPacketOpenTip);
            redPacketStatusIcon = view.findViewById(R.id.ivRedPacketStatus);
            quoteContainer = view.findViewById(R.id.quote_container);
            quoteSender = view.findViewById(R.id.tvQuoteSender);
            quoteContent = view.findViewById(R.id.tvQuoteContent);
            quoteImage = view.findViewById(R.id.ivQuoteImage);
            imageContainer = view.findViewById(R.id.image_container);
            image = view.findViewById(R.id.ivMessageImage);
            videoPlay = view.findViewById(R.id.ivVideoPlay);
            voiceRow = view.findViewById(R.id.voice_row);
            voiceIcon = view.findViewById(R.id.ivVoiceIcon);
            voiceDuration = view.findViewById(R.id.tvVoiceDuration);
            voiceLoading = view.findViewById(R.id.pbVoiceLoading);
            statusRow = view.findViewById(R.id.message_status_row);
            statusIcon = view.findViewById(R.id.ivMessageStatus);
            readCount = view.findViewById(R.id.tvReadCount);
            statusRowMedia = view.findViewById(R.id.message_status_media_row);
            statusIconMedia = view.findViewById(R.id.ivMessageStatusMedia);
            readCountMedia = view.findViewById(R.id.tvReadCountMedia);
            messageTime = view.findViewById(R.id.tvMessageTime);
            messageContainerPaddingLeft = messageContainer != null ? messageContainer.getPaddingLeft() : 0;
            messageContainerPaddingTop = messageContainer != null ? messageContainer.getPaddingTop() : 0;
            messageContainerPaddingRight = messageContainer != null ? messageContainer.getPaddingRight() : 0;
            messageContainerPaddingBottom = messageContainer != null ? messageContainer.getPaddingBottom() : 0;
            bubblePaddingLeft = bubble != null ? bubble.getPaddingLeft() : 0;
            bubblePaddingTop = bubble != null ? bubble.getPaddingTop() : 0;
            bubblePaddingRight = bubble != null ? bubble.getPaddingRight() : 0;
            bubblePaddingBottom = bubble != null ? bubble.getPaddingBottom() : 0;
        }
    }

    protected static class TypingViewHolder {
        final View root;
        final View avatarStack;
        final ImageView avatar;
        final ImageView avatar2;
        final ImageView avatar3;
        final View dotsContainer;
        final TextView textView;
        final View dot1;
        final View dot2;
        final View dot3;
        final TypingAnimator animator;
        String transitionKey;
        Runnable finishRunnable;

        TypingViewHolder(View view) {
            root = view;
            avatarStack = view.findViewById(R.id.typingAvatarStack);
            avatar = view.findViewById(R.id.ivTypingAvatar);
            avatar2 = view.findViewById(R.id.ivTypingAvatar2);
            avatar3 = view.findViewById(R.id.ivTypingAvatar3);
            dotsContainer = view.findViewById(R.id.typingDots);
            textView = view.findViewById(R.id.tvTypingText);
            dot1 = view.findViewById(R.id.typingDot1);
            dot2 = view.findViewById(R.id.typingDot2);
            dot3 = view.findViewById(R.id.typingDot3);
            animator = new TypingAnimator();
        }

        void start() {
            if (animator != null) {
                animator.start(dot1, dot2, dot3);
            }
        }

        void stop() {
            if (animator != null) {
                animator.stop();
            }
        }

        void reset() {
            stop();
            if (dotsContainer != null) {
                dotsContainer.clearAnimation();
                dotsContainer.setVisibility(View.VISIBLE);
            }
            if (textView != null) {
                textView.clearAnimation();
                textView.setVisibility(View.GONE);
            }
            transitionKey = null;
            if (finishRunnable != null) {
                if (root != null) {
                    root.removeCallbacks(finishRunnable);
                }
                finishRunnable = null;
            }
        }

        void clearAvatars() {
            if (avatarStack != null) {
                avatarStack.setVisibility(View.GONE);
            }
            if (avatar != null) {
                avatar.setVisibility(View.GONE);
                avatar.setTag(null);
            }
            if (avatar2 != null) {
                avatar2.setVisibility(View.GONE);
                avatar2.setTag(null);
            }
            if (avatar3 != null) {
                avatar3.setVisibility(View.GONE);
                avatar3.setTag(null);
            }
        }

        void showDots() {
            reset();
        }

        void showTransition(String text, String key) {
            stop();
            if (dotsContainer == null || textView == null) {
                return;
            }
            String safeKey = key == null ? "" : key;
            if (!safeKey.equals(transitionKey)) {
                transitionKey = safeKey;
                dotsContainer.setVisibility(View.VISIBLE);
                textView.setVisibility(View.VISIBLE);
                textView.setText(text == null ? "" : text);
                android.view.animation.AlphaAnimation dotsFade = new android.view.animation.AlphaAnimation(1f, 0f);
                dotsFade.setDuration(TYPING_TRANSITION_MS);
                dotsFade.setFillAfter(true);
                android.view.animation.AlphaAnimation textFade = new android.view.animation.AlphaAnimation(0f, 1f);
                textFade.setDuration(TYPING_TRANSITION_MS);
                textFade.setFillAfter(true);
                dotsContainer.startAnimation(dotsFade);
                int dotsWidth = dotsContainer.getWidth();
                if (dotsWidth <= 0) {
                    dotsContainer.measure(
                            android.view.View.MeasureSpec.UNSPECIFIED,
                            android.view.View.MeasureSpec.UNSPECIFIED);
                    dotsWidth = dotsContainer.getMeasuredWidth();
                }
                android.view.animation.AnimationSet textSet = new android.view.animation.AnimationSet(false);
                textSet.setFillAfter(true);
                textSet.addAnimation(textFade);
                if (dotsWidth > 0) {
                    android.view.animation.TranslateAnimation slide =
                            new android.view.animation.TranslateAnimation(0f, -dotsWidth, 0f, 0f);
                    slide.setDuration(TYPING_TRANSITION_MS);
                    slide.setFillAfter(true);
                    textSet.addAnimation(slide);
                }
                textView.startAnimation(textSet);
            } else {
                dotsContainer.setVisibility(View.INVISIBLE);
                textView.setVisibility(View.VISIBLE);
            }
        }
    }

    protected static class CachedPayload {
        String body;
        MessagePayload payload;
    }

    protected int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    protected void applyFontScale(ViewHolder holder) {
        float scale = fontScale;
        if (holder.text != null) {
            holder.text.setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_MESSAGE_TEXT_SP * scale);
        }
        if (holder.senderName != null) {
            holder.senderName.setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_SENDER_NAME_SP * scale);
        }
        if (holder.quoteSender != null) {
            holder.quoteSender.setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_QUOTE_SENDER_SP * scale);
        }
        if (holder.quoteContent != null) {
            holder.quoteContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_QUOTE_CONTENT_SP * scale);
        }
        if (holder.voiceDuration != null) {
            holder.voiceDuration.setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_VOICE_DURATION_SP * scale);
        }
        if (holder.resourceTitle != null) {
            holder.resourceTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_RESOURCE_TITLE_SP * scale);
        }
        if (holder.resourceSub != null) {
            holder.resourceSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_RESOURCE_SUB_SP * scale);
        }
        if (holder.resourceAction != null) {
            holder.resourceAction.setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_RESOURCE_ACTION_SP * scale);
        }
    }

    protected void applyMessageWidthLimits(ViewHolder holder) {
        if (holder == null) {
            return;
        }
        int messageMax = resolveMessageTextMaxWidth();
        if (holder.text != null) {
            holder.text.setMaxWidth(messageMax);
        }
        if (holder.quoteContent != null) {
            int quoteMax = Math.min(dpToPx(QUOTE_TEXT_MAX_DP), messageMax + dpToPx(16));
            quoteMax = Math.max(dpToPx(QUOTE_TEXT_MIN_DP), quoteMax);
            holder.quoteContent.setMaxWidth(quoteMax);
        }
    }

    private int resolveMessageTextMaxWidth() {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int reserved = dpToPx(ROW_HORIZONTAL_PADDING_DP)
                + dpToPx(OUTGOING_AVATAR_SLOT_DP)
                + dpToPx(BUBBLE_HORIZONTAL_PADDING_DP)
                + dpToPx(READ_STATUS_RESERVED_DP)
                + dpToPx(WIDTH_SAFETY_MARGIN_DP);
        int dynamic = screenWidth - reserved;
        int max = dpToPx(MESSAGE_TEXT_MAX_DP);
        int min = dpToPx(MESSAGE_TEXT_MIN_DP);
        if (dynamic < min) {
            return min;
        }
        if (dynamic > max) {
            return max;
        }
        return dynamic;
    }
}
