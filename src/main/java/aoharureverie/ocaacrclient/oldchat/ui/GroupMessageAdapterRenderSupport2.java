package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.util.GroupAvatarCache;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;

import java.util.List;

abstract class GroupMessageAdapterRenderSupport2 extends GroupMessageAdapterRenderSupport1 {
    GroupMessageAdapterRenderSupport2(android.content.Context context, List<GroupMessage> messages, String myUID) {
        super(context, messages, myUID);
    }

    @Override
    protected void bindSenderSection(final ViewHolder holderFinal, final GroupMessage target, String type,
                                     boolean compactWithPrevious, boolean compactWithNext, boolean isMine) {
        if (isMine) {
            holderFinal.messageContainer.setGravity(Gravity.END);
            holderFinal.avatar.setVisibility(View.GONE);
            if (holderFinal.avatarRight != null) {
                holderFinal.avatarRight.setVisibility(compactWithPrevious ? View.INVISIBLE : View.VISIBLE);
            }
            if (holderFinal.senderRow != null) {
                if (compactWithPrevious) {
                    holderFinal.senderRow.setVisibility(View.GONE);
                } else {
                    holderFinal.senderRow.setVisibility(View.VISIBLE);
                    holderFinal.senderRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                    ViewGroup.LayoutParams rowParams = holderFinal.senderRow.getLayoutParams();
                    if (rowParams instanceof LinearLayout.LayoutParams) {
                        ((LinearLayout.LayoutParams) rowParams).width = LinearLayout.LayoutParams.WRAP_CONTENT;
                        ((LinearLayout.LayoutParams) rowParams).gravity = Gravity.END;
                        holderFinal.senderRow.setLayoutParams(rowParams);
                    }
                }
            }
            if (holderFinal.contentContainer != null) {
                holderFinal.contentContainer.setGravity(Gravity.END);
                ViewGroup.LayoutParams contentParams = holderFinal.contentContainer.getLayoutParams();
                if (contentParams instanceof LinearLayout.LayoutParams) {
                    ((LinearLayout.LayoutParams) contentParams).gravity = Gravity.END;
                    holderFinal.contentContainer.setLayoutParams(contentParams);
                }
            }
            if (holderFinal.senderName != null) {
                holderFinal.senderName.setGravity(Gravity.END);
            }
            final String selfUid = MyUidStore.getCurrentUid(context);
            final String profileUid = (selfUid != null && selfUid.length() > 0) ? selfUid : target.from_uid;
            String myName = resolveName(profileUid);
            if (holderFinal.senderName != null) {
                holderFinal.senderName.setText(myName == null ? "" : myName);
            }
            if (holderFinal.senderBadge != null) {
                if (compactWithPrevious) {
                    holderFinal.senderBadge.setVisibility(View.GONE);
                } else {
                    bindRoleBadge(holderFinal.senderBadge, resolveRole(profileUid));
                }
            }
            if (compactWithPrevious) {
                UserTitleBinder.bind(holderFinal.senderTitle, "");
            } else {
                UserTitleBinder.bind(holderFinal.senderTitle, resolveTitle(profileUid));
            }
            String myAvatar = avatarMap.get(profileUid);
            if (myAvatar == null || myAvatar.isEmpty()) {
                myAvatar = GroupAvatarCache.getCachedAvatar(context, profileUid);
                if (myAvatar != null && !myAvatar.isEmpty()) {
                    avatarMap.put(profileUid, myAvatar);
                }
            }
            if (holderFinal.avatarRight != null) {
                if (compactWithPrevious) {
                    holderFinal.avatarRight.setOnClickListener(null);
                } else {
                    if (myAvatar == null || myAvatar.isEmpty()) {
                        holderFinal.avatarRight.setImageResource(R.drawable.ic_avatar_placeholder);
                        holderFinal.avatarRight.setTag(null);
                    } else {
                        Object currentTag = holderFinal.avatarRight.getTag();
                        if (currentTag == null || !myAvatar.equals(currentTag)) {
                            holderFinal.avatarRight.setTag(myAvatar);
                            ImageLoader.loadAvatar(holderFinal.avatarRight, myAvatar);
                        }
                    }
                    holderFinal.avatarRight.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String openUid = normalizeProfileUid(profileUid);
                            if (openUid.length() == 0) {
                                return;
                            }
                            Intent intent = new Intent(context, UserSpaceActivity.class);
                            intent.putExtra("uid", openUid);
                            context.startActivity(intent);
                        }
                    });
                }
            }
            if ("red_packet".equals(type)) {
                applyBubbleBackground(holderFinal, R.drawable.bg_red_packet_out);
            } else {
                applyBubbleBackground(holderFinal, R.drawable.bg_msg_me);
            }
            holderFinal.text.setTextColor(0xFFFFFFFF);
            holderFinal.voiceDuration.setTextColor(0xFFFFFFFF);
            holderFinal.voiceIcon.setColorFilter(0xFFFFFFFF);
            holderFinal.resourceTitle.setTextColor(0xFFFFFFFF);
            holderFinal.resourceSub.setTextColor(0xCCFFFFFF);
            holderFinal.resourceAction.setTextColor(0xFFFFFFFF);
            holderFinal.resourceIcon.setColorFilter(0xFFFFFFFF);
            holderFinal.redPacketTitle.setTextColor(0xFFFFFFFF);
            holderFinal.redPacketDesc.setTextColor(0xCCFFFFFF);
            if (holderFinal.redPacketOpenTip != null) {
                holderFinal.redPacketOpenTip.setTextColor(0x99FFFFFF);
            }
            if (holderFinal.redPacketStatusIcon != null && holderFinal.redPacketStatusIcon.getVisibility() == View.VISIBLE) {
                holderFinal.redPacketStatusIcon.setColorFilter(0x99FFFFFF);
            }
            boolean useMediaStatus = "image".equals(type) || "video".equals(type) || "voice".equals(type);
            if (compactWithNext) {
                if (holderFinal.statusRow != null) {
                    holderFinal.statusRow.setVisibility(View.GONE);
                }
                if (holderFinal.statusRowMedia != null) {
                    holderFinal.statusRowMedia.setVisibility(View.GONE);
                }
            } else {
                bindReadStatus(holderFinal, target.read_count, useMediaStatus);
            }
            return;
        }

        holderFinal.messageContainer.setGravity(Gravity.START);
        holderFinal.avatar.setVisibility(compactWithPrevious ? View.INVISIBLE : View.VISIBLE);
        if (holderFinal.avatarRight != null) {
            holderFinal.avatarRight.setVisibility(View.GONE);
        }
        String name = resolveName(target.from_uid);
        int role = resolveRole(target.from_uid);
        holderFinal.senderName.setText(name == null ? "" : name);
        if (holderFinal.senderRow != null) {
            if (compactWithPrevious) {
                holderFinal.senderRow.setVisibility(View.GONE);
            } else {
                holderFinal.senderRow.setVisibility(View.VISIBLE);
                holderFinal.senderRow.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                ViewGroup.LayoutParams rowParams = holderFinal.senderRow.getLayoutParams();
                if (rowParams instanceof LinearLayout.LayoutParams) {
                    ((LinearLayout.LayoutParams) rowParams).width = LinearLayout.LayoutParams.WRAP_CONTENT;
                    ((LinearLayout.LayoutParams) rowParams).gravity = Gravity.START;
                    holderFinal.senderRow.setLayoutParams(rowParams);
                }
            }
        }
        if (holderFinal.contentContainer != null) {
            holderFinal.contentContainer.setGravity(Gravity.START);
            ViewGroup.LayoutParams contentParams = holderFinal.contentContainer.getLayoutParams();
            if (contentParams instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) contentParams).gravity = Gravity.START;
                holderFinal.contentContainer.setLayoutParams(contentParams);
            }
        }
        if (holderFinal.senderName != null) {
            holderFinal.senderName.setGravity(Gravity.START);
        }
        if (compactWithPrevious) {
            if (holderFinal.senderBadge != null) {
                holderFinal.senderBadge.setVisibility(View.GONE);
            }
            UserTitleBinder.bind(holderFinal.senderTitle, "");
        } else {
            bindRoleBadge(holderFinal.senderBadge, role);
            UserTitleBinder.bind(holderFinal.senderTitle, resolveTitle(target.from_uid));
        }
        String avatarUrl = avatarMap.get(target.from_uid);
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            avatarUrl = GroupAvatarCache.getCachedAvatar(context, target.from_uid);
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                avatarMap.put(target.from_uid, avatarUrl);
            }
        }
        if (compactWithPrevious) {
            holderFinal.avatar.setOnClickListener(null);
        } else {
            if (avatarUrl == null || avatarUrl.isEmpty()) {
                holderFinal.avatar.setImageResource(R.drawable.ic_avatar_placeholder);
                holderFinal.avatar.setTag(null);
            } else {
                Object currentTag = holderFinal.avatar.getTag();
                if (currentTag == null || !avatarUrl.equals(currentTag)) {
                    holderFinal.avatar.setTag(avatarUrl);
                    ImageLoader.loadAvatar(holderFinal.avatar, avatarUrl);
                }
            }
            holderFinal.avatar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String openUid = normalizeProfileUid(target.from_uid);
                    if (openUid.length() == 0) {
                        return;
                    }
                    Intent intent = new Intent(context, UserSpaceActivity.class);
                    intent.putExtra("uid", openUid);
                    context.startActivity(intent);
                }
            });
        }
        if ("red_packet".equals(type)) {
            applyBubbleBackground(holderFinal, R.drawable.bg_red_packet_in);
        } else {
            applyBubbleBackground(holderFinal, R.drawable.bg_msg_other);
        }
        int textColor = ContextCompat.getColor(context, R.color.color_text_primary);
        int subColor = ContextCompat.getColor(context, R.color.color_text_secondary);
        int actionColor = ContextCompat.getColor(context, R.color.colorPrimary);
        holderFinal.text.setTextColor(textColor);
        holderFinal.voiceDuration.setTextColor(textColor);
        holderFinal.voiceIcon.setColorFilter(textColor);
        holderFinal.resourceTitle.setTextColor(textColor);
        holderFinal.resourceSub.setTextColor(subColor);
        holderFinal.resourceAction.setTextColor(actionColor);
        holderFinal.resourceIcon.setColorFilter(actionColor);
        holderFinal.redPacketTitle.setTextColor(0xFFFFFFFF);
        holderFinal.redPacketDesc.setTextColor(0xCCFFFFFF);
        if (holderFinal.redPacketOpenTip != null) {
            holderFinal.redPacketOpenTip.setTextColor(0x99FFFFFF);
        }
        if (holderFinal.redPacketStatusIcon != null) {
            holderFinal.redPacketStatusIcon.setVisibility(View.GONE);
        }
        if (holderFinal.statusRow != null) {
            holderFinal.statusRow.setVisibility(View.GONE);
        }
        if (holderFinal.statusRowMedia != null) {
            holderFinal.statusRowMedia.setVisibility(View.GONE);
        }
    }
}
