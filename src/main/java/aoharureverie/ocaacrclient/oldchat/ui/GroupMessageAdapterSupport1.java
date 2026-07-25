package aoharureverie.ocaacrclient.oldchat.ui;

import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.util.GroupAvatarCache;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;

abstract class GroupMessageAdapterSupport1 extends GroupMessageAdapterSupport2 {
    GroupMessageAdapterSupport1(android.content.Context context, List<GroupMessage> messages, String myUID) {
        super(context, messages, myUID);
    }

    protected void maybeAnimateSend(ViewHolder holder, GroupMessage message, boolean isMine) {
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

    protected String animationKeyForMessage(GroupMessage message) {
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

    protected boolean isTypingPosition(int position) {
        if (getDisplayTypingUids().isEmpty()) {
            return false;
        }
        if (transitionRunning && position == messages.size() - 1) {
            return false;
        }
        return position == getCount() - 1;
    }

    protected void bindTyping(TypingViewHolder holder, GroupMessage message) {
        if (holder == null) {
            return;
        }
        ArrayList<String> uids = new ArrayList<String>();
        if (message != null && message.from_uid != null && !message.from_uid.isEmpty()) {
            uids.add(message.from_uid);
        } else {
            uids.addAll(getDisplayTypingUids());
        }
        if (uids.isEmpty()) {
            holder.reset();
            holder.clearAvatars();
            return;
        }
        bindTypingAvatars(holder, uids);
        if (message == null) {
            holder.showDots();
            holder.start();
            return;
        }
        String preview = ChatMessageUtil.previewForType(message.msg_type, message.body);
        String key = message.id != null && message.id.length() > 0
                ? message.id
                : String.valueOf(message.created_at);
        holder.showTransition(preview, key);
        scheduleFinishTransition(holder);
    }

    protected ArrayList<String> getDisplayTypingUids() {
        ArrayList<String> list = new ArrayList<String>();
        for (String uid : typingUids) {
            if (uid == null || uid.isEmpty()) {
                continue;
            }
            if (transitionRunning && uid.equals(transitionUid)) {
                continue;
            }
            list.add(uid);
            if (list.size() > 3) {
                list.remove(0);
            }
        }
        return list;
    }

    protected void bindTypingAvatars(TypingViewHolder holder, ArrayList<String> uids) {
        if (holder == null) {
            return;
        }
        if (holder.avatarStack != null) {
            holder.avatarStack.setVisibility(uids.isEmpty() ? View.GONE : View.VISIBLE);
        }
        ImageView[] targets = new ImageView[]{holder.avatar, holder.avatar2, holder.avatar3};
        for (int i = 0; i < targets.length; i++) {
            ImageView target = targets[i];
            if (target == null) {
                continue;
            }
            if (i < uids.size()) {
                target.setVisibility(View.VISIBLE);
                loadTypingAvatar(target, uids.get(i));
            } else {
                target.setVisibility(View.GONE);
                target.setTag(null);
            }
        }
    }

    protected void loadTypingAvatar(ImageView target, String uid) {
        if (target == null || uid == null || uid.isEmpty()) {
            return;
        }
        String avatarUrl = avatarMap.get(uid);
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            avatarUrl = GroupAvatarCache.getCachedAvatar(context, uid);
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                avatarMap.put(uid, avatarUrl);
            }
        }
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            target.setImageResource(R.drawable.ic_avatar_placeholder);
            target.setTag(null);
            return;
        }
        Object currentTag = target.getTag();
        if (currentTag == null || !avatarUrl.equals(currentTag)) {
            target.setTag(avatarUrl);
            ImageLoader.loadAvatar(target, avatarUrl);
        }
    }

    protected void scheduleFinishTransition(TypingViewHolder holder) {
        if (holder == null) {
            return;
        }
        if (holder.root != null) {
            holder.root.removeCallbacks(holder.finishRunnable);
        }
        holder.finishRunnable = new Runnable() {
            @Override
            public void run() {
                finishTypingTransition();
            }
        };
        if (holder.root != null) {
            holder.root.postDelayed(holder.finishRunnable, TYPING_TRANSITION_MS);
        }
    }

    protected void finishTypingTransition() {
        if (!transitionRunning) {
            return;
        }
        transitionRunning = false;
        transitionMessage = null;
        transitionUid = null;
        notifyDataSetChanged();
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
}
