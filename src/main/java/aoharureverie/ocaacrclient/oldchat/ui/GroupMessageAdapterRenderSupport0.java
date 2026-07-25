package aoharureverie.ocaacrclient.oldchat.ui;

import android.support.v4.content.ContextCompat;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;

import java.util.List;

abstract class GroupMessageAdapterRenderSupport0 extends GroupMessageAdapterSupport0 {
    GroupMessageAdapterRenderSupport0(android.content.Context context, List<GroupMessage> messages, String myUID) {
        super(context, messages, myUID);
    }

    protected boolean canCompactBetween(GroupMessage previous, GroupMessage current) {
        if (previous == null || current == null) {
            return false;
        }
        String currentUid = current.from_uid == null ? "" : current.from_uid.trim();
        String previousUid = previous.from_uid == null ? "" : previous.from_uid.trim();
        if (currentUid.length() == 0 || previousUid.length() == 0 || !currentUid.equals(previousUid)) {
            return false;
        }
        String currentType = current.msg_type == null ? "text" : current.msg_type.trim().toLowerCase();
        String previousType = previous.msg_type == null ? "text" : previous.msg_type.trim().toLowerCase();
        if ("recall".equals(currentType) || "recall".equals(previousType)) {
            return false;
        }
        if (ChatTimeFormatter.shouldShowTime(current.created_at, previous.created_at)) {
            return false;
        }
        return true;
    }

    protected boolean shouldCompactWithPrevious(int position, GroupMessage current) {
        if (current == null || position <= 0 || position >= messages.size()) {
            return false;
        }
        GroupMessage previous = messages.get(position - 1);
        return canCompactBetween(previous, current);
    }

    protected boolean shouldCompactWithNext(int position, GroupMessage current) {
        if (current == null || position < 0 || position >= messages.size() - 1) {
            return false;
        }
        GroupMessage next = messages.get(position + 1);
        return canCompactBetween(current, next);
    }

    protected void applyCompactRowStyle(ViewHolder holder, boolean compactWithPrevious, boolean compactWithNext) {
        if (holder == null || holder.messageContainer == null) {
            return;
        }
        int compactPadding = dpToPx(1);
        int topPadding = compactWithPrevious ? compactPadding : holder.messageContainerPaddingTop;
        int bottomPadding = compactWithNext ? compactPadding : holder.messageContainerPaddingBottom;
        holder.messageContainer.setPadding(
                holder.messageContainerPaddingLeft,
                topPadding,
                holder.messageContainerPaddingRight,
                bottomPadding);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int viewType = getItemViewType(position);
        if (viewType == VIEW_TYPE_TYPING) {
            TypingViewHolder typingHolder;
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_typing_indicator, parent, false);
                typingHolder = new TypingViewHolder(convertView);
                convertView.setTag(typingHolder);
            } else {
                typingHolder = (TypingViewHolder) convertView.getTag();
            }
            boolean isTransitionRow = transitionRunning && position == messages.size() - 1;
            bindTyping(typingHolder, isTransitionRow ? transitionMessage : null);
            convertView.setBackgroundColor(0x00000000);
            return convertView;
        }

        GroupMessage msg = messages.get(position);
        final GroupMessage target = msg;
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_group_message, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        applyFontScale(holder);
        applyMessageWidthLimits(holder);
        final ViewHolder holderFinal = holder;
        bindJumpHighlight(convertView, target.id);
        String type = target.msg_type == null ? "text" : target.msg_type.toLowerCase();
        MessagePayload payload = getPayload(target);
        final boolean isImageMessage = "image".equals(type);
        final boolean isMusicMessage = "music".equals(type)
                || ("resource".equals(type) && payload != null && "music".equals(payload.mediaKind));
        final boolean isMine = MyUidStore.isMyUid(context, target.from_uid, myUID);
        holderFinal.messageContainer.setVisibility(View.VISIBLE);
        if (holderFinal.messageTime != null) {
            holderFinal.messageTime.setOnClickListener(null);
            holderFinal.messageTime.setClickable(false);
            holderFinal.messageTime.setTextColor(ContextCompat.getColor(context, R.color.color_text_secondary));
        }

        if ("recall".equals(type)) {
            holderFinal.messageContainer.setVisibility(View.GONE);
            String recallText = payload.text == null ? "" : payload.text;
            if (recallText.length() == 0) {
                String name = resolveName(target.from_uid);
                if (name == null || name.isEmpty()) {
                    name = "成员";
                }
                recallText = context.getString(R.string.message_recalled_member, name);
            }
            if (canReEditRecalled(target, isMine) && holderFinal.messageTime != null) {
                SpannableStringBuilder builder = new SpannableStringBuilder();
                builder.append(recallText).append("  ");
                int actionStart = builder.length();
                builder.append("重新编辑");
                builder.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.colorPrimary)),
                        actionStart, builder.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                holderFinal.messageTime.setText(builder);
                holderFinal.messageTime.setClickable(true);
                holderFinal.messageTime.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (actionListener != null) {
                            actionListener.onReEdit(target);
                        }
                    }
                });
            } else {
                holderFinal.messageTime.setText(recallText);
            }
            holderFinal.messageTime.setVisibility(View.VISIBLE);
            return convertView;
        }

        bindTimeLabel(holder, msg, position);
        resetMessageHolderForBind(holder);

        final boolean compactWithPrevious = shouldCompactWithPrevious(position, target);
        final boolean compactWithNext = shouldCompactWithNext(position, target);
        applyCompactRowStyle(holderFinal, compactWithPrevious, compactWithNext);

        bindMessageTypeSection(holderFinal, target, payload, type, isMine);
        bindSenderSection(holderFinal, target, type, compactWithPrevious, compactWithNext, isMine);

        if (isMusicMessage) {
            applyBubbleBackground(holderFinal, isMine ? R.drawable.bg_msg_music_me : R.drawable.bg_msg_music_other);
        }
        if (isImageMessage) {
            clearBubbleBackground(holderFinal);
        }
        bindQuote(holderFinal, payload.quote);
        bindItemActions(holderFinal, target, payload);
        return convertView;
    }

    protected boolean canReEditRecalled(GroupMessage msg, boolean isMine) {
        if (!isMine || msg == null) {
            return false;
        }
        String text = msg.recall_edit_text == null ? "" : msg.recall_edit_text.trim();
        if (text.length() == 0) {
            return false;
        }
        String type = msg.recall_edit_type == null ? "text" : msg.recall_edit_type.toLowerCase();
        return "text".equals(type);
    }

    protected String normalizeProfileUid(String rawUid) {
        if (rawUid == null) {
            return "";
        }
        String uid = rawUid.trim();
        while (uid.startsWith("@")) {
            uid = uid.substring(1).trim();
        }
        return uid;
    }

    protected abstract void bindMessageTypeSection(ViewHolder holder, GroupMessage target,
                                                   MessagePayload payload, String type, boolean isMine);

    protected abstract void bindSenderSection(ViewHolder holder, GroupMessage target, String type,
                                              boolean compactWithPrevious, boolean compactWithNext,
                                              boolean isMine);
}
