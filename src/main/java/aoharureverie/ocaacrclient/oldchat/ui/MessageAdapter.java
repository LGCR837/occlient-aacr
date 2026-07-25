package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.models.Message;
import aoharureverie.ocaacrclient.oldchat.util.ClipboardUtil;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;
import aoharureverie.ocaacrclient.oldchat.util.MediaUrlResolver;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;
import aoharureverie.ocaacrclient.oldchat.util.RedPacketPayload;

import java.util.List;

public class MessageAdapter extends MessageAdapterSupport0 {
    public interface MessageActionListener {
        void onQuote(Message message);
        void onCopy(String text);
        void onRecall(Message message);
        void onReEdit(Message message);
    }

    public interface QuoteClickListener {
        void onQuoteClick(MessagePayload.Quote quote);
    }

    public MessageAdapter(Context context, List<Message> messages, String myUID) {
        super(context, messages, myUID);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_TYPING) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_typing_indicator, parent, false);
            return new TypingViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_TYPING) {
            bindTyping((TypingViewHolder) holder, transitionRunning ? transitionMessage : null);
            ((TypingViewHolder) holder).itemView.setBackgroundColor(0x00000000);
            return;
        }
        Message msg = messages.get(position);
        bindMessage((ViewHolder) holder, msg, position);
    }

    @Override
    public void onViewRecycled(RecyclerView.ViewHolder holder) {
        if (holder instanceof TypingViewHolder) {
            ((TypingViewHolder) holder).reset();
        }
        super.onViewRecycled(holder);
    }

    private void bindMessage(final ViewHolder holder, final Message msg, int position) {
        applyFontScale(holder);
        final MessagePayload payload = getPayload(msg);
        String type = msg.msg_type == null ? "text" : msg.msg_type.toLowerCase();
        final boolean isMine = MyUidStore.isMyUid(context, msg.from_uid, myUID);
        final boolean isImageMessage = "image".equals(type);
        final boolean isEmojiMessage = isImageMessage && payload != null && "emoji".equals(payload.mediaKind);
        final boolean isMusicMessage = "music".equals(type)
                || ("resource".equals(type) && payload != null && "music".equals(payload.mediaKind));
        final boolean useMediaStatus = isMine && ("image".equals(type) || "video".equals(type) || "voice".equals(type));
        holder.messageContainer.setVisibility(View.VISIBLE);
        bindJumpHighlight(holder.itemView, msg.id);
        if (holder.messageTime != null) {
            holder.messageTime.setOnClickListener(null);
            holder.messageTime.setClickable(false);
            holder.messageTime.setTextColor(ContextCompat.getColor(context, R.color.color_text_secondary));
        }

        if ("recall".equals(type)) {
            holder.messageContainer.setVisibility(View.GONE);
            String recallText = payload.text == null ? "" : payload.text;
            if (recallText.length() == 0) {
                recallText = isMine ? context.getString(R.string.message_recalled_self)
                        : context.getString(R.string.message_recalled_other);
            }
            if (canReEditRecalled(msg, isMine) && holder.messageTime != null) {
                SpannableStringBuilder builder = new SpannableStringBuilder();
                builder.append(recallText).append("  ");
                int actionStart = builder.length();
                builder.append("重新编辑");
                builder.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.colorPrimary)),
                        actionStart, builder.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                holder.messageTime.setText(builder);
                holder.messageTime.setClickable(true);
                holder.messageTime.setOnClickListener(new View.OnClickListener() {
                    
                    public void onClick(View v) {
                        if (actionListener != null) {
                            actionListener.onReEdit(msg);
                        }
                    }
                });
            } else {
                holder.messageTime.setText(recallText);
            }
            holder.messageTime.setVisibility(View.VISIBLE);
            return;
        }

        bindTimeLabel(holder, msg, position);
        holder.text.setVisibility(View.GONE);
        if (holder.imageContainer != null) {
            holder.imageContainer.setVisibility(View.GONE);
        }
        if (holder.videoPlay != null) {
            holder.videoPlay.setVisibility(View.GONE);
        }
        holder.image.setVisibility(View.GONE);
        holder.voiceRow.setVisibility(View.GONE);
        holder.voiceRow.setOnClickListener(null);
        if (holder.voiceLoading != null) {
            holder.voiceLoading.setVisibility(View.GONE);
        }
        holder.image.setOnClickListener(null);
        holder.bubble.setOnClickListener(null);
        holder.text.setOnClickListener(null);
        holder.resourceCard.setVisibility(View.GONE);
        holder.resourceCard.setOnClickListener(null);
        holder.resourceAction.setOnClickListener(null);
        if (holder.resourceCoverContainer != null) {
            holder.resourceCoverContainer.setVisibility(View.GONE);
        }
        if (holder.resourceCover != null) {
            holder.resourceCover.setTag(null);
        }
        holder.redPacketContainer.setVisibility(View.GONE);
        holder.redPacketContainer.setOnClickListener(null);
        if (holder.redPacketStatusIcon != null) {
            holder.redPacketStatusIcon.setVisibility(View.GONE);
            holder.redPacketStatusIcon.setImageResource(R.drawable.ic_msg_sent);
            holder.redPacketStatusIcon.setColorFilter(0x99FFFFFF);
        }
        holder.quoteContainer.setVisibility(View.GONE);
        holder.voiceIcon.clearAnimation();

        if ("image".equals(type) || "video".equals(type)) {
            final boolean isVideo = "video".equals(type);
            holder.image.setVisibility(View.VISIBLE);
            final String thumb = !TextUtils.isEmpty(msg.thumb_url) ? msg.thumb_url : msg.media_url;

            if (holder.imageContainer != null) {
                holder.imageContainer.setVisibility(View.VISIBLE);
            }
            if (holder.imageLoading != null) {
                holder.imageLoading.setVisibility(View.VISIBLE);
            }
            if (holder.videoPlay != null) {
                holder.videoPlay.setVisibility(isVideo ? View.VISIBLE : View.GONE);
            }

            ImageLoader.load(holder.image, thumb, new ImageLoader.ImageLoadListener() {
                @Override
                public void onComplete(String url) {
                    Object tag = holder.image.getTag();
                    boolean same;
                    if (tag == null) {
                        same = (url == null || url.length() == 0);
                    } else {
                        same = tag.equals(url);
                    }
                    if (same && holder.imageLoading != null) {
                        holder.imageLoading.setVisibility(View.GONE);
                    }
                }
            });

            holder.image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isVideo) {
                        openVideo(msg);
                    } else {
                        ImagePreviewActivity.start(context, msg.media_url);
                    }
                }
            });
        } else if ("voice".equals(type)) {
            holder.voiceRow.setVisibility(View.VISIBLE);
            int seconds = Math.max(1, msg.duration_ms / 1000);
            seconds = Math.min(seconds, MAX_VOICE_SECONDS);
            boolean loadingVoice = voicePlayer.isLoading(msg);
            int width = dpToPx(VOICE_MIN_DP + (VOICE_MAX_DP - VOICE_MIN_DP) * seconds / MAX_VOICE_SECONDS);
            if (loadingVoice) {
                width = dpToPx(VOICE_MAX_DP);
            }
            ViewGroup.LayoutParams params = holder.voiceRow.getLayoutParams();
            if (params instanceof LinearLayout.LayoutParams) {
                params.width = width;
                holder.voiceRow.setLayoutParams(params);
            }
            holder.voiceDuration.setText(voicePlayer.getDurationLabel(msg, seconds));
            if (holder.voiceLoading != null) {
                holder.voiceLoading.setVisibility(loadingVoice ? View.VISIBLE : View.GONE);
            }
            holder.voiceRow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    voicePlayer.play(msg);
                }
            });
            if (voicePlayer.isPlaying(msg)) {
                holder.voiceIcon.startAnimation(buildVoiceAnimation());
            } else {
                holder.voiceIcon.clearAnimation();
            }
        } else if (isMusicMessage) {
            holder.text.setVisibility(View.GONE);
            holder.resourceCard.setVisibility(View.VISIBLE);
            holder.resourceAction.setVisibility(View.VISIBLE);
            final String songUrl = MediaUrlResolver.resolve(msg.media_url);
            MusicShareInfo info = parseMusicShareInfo(payload.text, songUrl, msg.thumb_url);
            holder.resourceTitle.setText(info.title);
            holder.resourceSub.setText(info.sub);
            holder.resourceAction.setText("播放");
            holder.resourceIcon.setImageResource(R.drawable.ic_voice);
            bindMusicPreview(holder, info.coverUrl);
            if (songUrl != null && songUrl.length() > 0) {
                final String songTitle = info.title;
                final String songArtist = info.artist;
                final String coverUrl = info.coverUrl;
                View.OnClickListener open = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openMusicShare(songTitle, songArtist, songUrl, coverUrl);
                    }
                };
                holder.bubble.setOnClickListener(open);
                holder.resourceCard.setOnClickListener(open);
                holder.resourceAction.setOnClickListener(open);
            } else {
                holder.resourceSub.setText("歌曲链接不可用");
                holder.resourceAction.setVisibility(View.GONE);
                holder.bubble.setOnClickListener(null);
                holder.resourceCard.setOnClickListener(null);
                holder.resourceAction.setOnClickListener(null);
            }
        } else if ("resource".equals(type)) {
            holder.text.setVisibility(View.GONE);
            holder.resourceCard.setVisibility(View.VISIBLE);
            holder.resourceAction.setVisibility(View.VISIBLE);
            final String url = MediaUrlResolver.resolve(msg.media_url);
            if (url != null && url.length() > 0) {
                final boolean isBiliShare = aoharureverie.ocaacrclient.oldchat.bili.BiliShareUtil.isShareUrl(url);
                ResourceInfo info = parseResourceInfo(payload.text, url);
                holder.resourceTitle.setText(info.title);
                holder.resourceSub.setText(info.sub);
                holder.resourceAction.setText(isBiliShare ? "观看" : "下载");
                holder.resourceIcon.setImageResource(isBiliShare ? R.drawable.ic_action_video : R.drawable.ic_document);
                bindResourcePreview(holder, info);
                View.OnClickListener open = isBiliShare ? new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openBiliShare(url);
                    }
                } : new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new AlertDialog.Builder(context)
                                .setTitle("下载资源")
                                .setMessage(url)
                                .setPositiveButton("复制链接", new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(android.content.DialogInterface dialog, int which) {
                                        ClipboardUtil.copyText(context, url);
                                    }
                                })
                                .setNegativeButton("浏览器下载", new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(android.content.DialogInterface dialog, int which) {
                                        try {
                                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            context.startActivity(intent);
                                        } catch (Exception e) {
                                        }
                                    }
                                })
                                .setNeutralButton("取消", null)
                                .show();
                    }
                };
                holder.bubble.setOnClickListener(open);
                holder.resourceCard.setOnClickListener(open);
                holder.resourceAction.setOnClickListener(open);
            } else {
                ResourceInfo info = parseResourceInfo(payload.text, "");
                holder.resourceTitle.setText(info.title);
                holder.resourceSub.setText("链接不可用");
                holder.resourceIcon.setImageResource(R.drawable.ic_document);
                holder.resourceAction.setText("下载");
                holder.resourceAction.setVisibility(View.GONE);
                bindResourcePreview(holder, null);
            }
        } else if ("red_packet".equals(type)) {
            holder.redPacketContainer.setVisibility(View.VISIBLE);
            RedPacketPayload redPacket = RedPacketPayload.fromBody(msg.body);
            String title = redPacket.title == null ? "" : redPacket.title;
            if (title.isEmpty()) {
                title = context.getString(R.string.red_packet_title_default);
            }
            holder.redPacketTitle.setText(title);
            String desc;
            if (redPacket.totalAmount > 0 && redPacket.totalCount > 0) {
                String amount = context.getString(R.string.message_red_packet_amount, redPacket.totalAmount);
                String count = context.getString(R.string.message_red_packet_count, redPacket.totalCount);
                desc = amount + " · " + count;
            } else {
                desc = context.getString(R.string.message_red_packet_tag);
            }
            holder.redPacketDesc.setText(desc);
            if (holder.redPacketOpenTip != null) {
                holder.redPacketOpenTip.setText(R.string.red_packet_open_tip);
            }
            if (holder.redPacketStatusIcon != null) {
                if (isMine) {
                    if (msg.status == Message.STATUS_READ) {
                        holder.redPacketStatusIcon.setImageResource(R.drawable.ic_msg_read);
                    } else {
                        holder.redPacketStatusIcon.setImageResource(R.drawable.ic_msg_sent);
                    }
                    holder.redPacketStatusIcon.setColorFilter(0x99FFFFFF);
                    holder.redPacketStatusIcon.setVisibility(View.VISIBLE);
                } else {
                    holder.redPacketStatusIcon.setVisibility(View.GONE);
                }
            }
            final String packetId = redPacket.packetId;
            View.OnClickListener open = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openRedPacket(packetId);
                }
            };
            holder.bubble.setOnClickListener(open);
            holder.redPacketContainer.setOnClickListener(open);
        } else {
            holder.text.setVisibility(View.VISIBLE);
            holder.text.setText(payload.text);
        }

        if (isMine) {
            applyMineStyle(holder, type, msg, useMediaStatus);
        } else {
            applyOtherStyle(holder, type);
            holder.messageContainer.setGravity(Gravity.START);
        }
        if (isMusicMessage) {
            applyBubbleBackground(holder, isMine ? R.drawable.bg_msg_music_me : R.drawable.bg_msg_music_other);
        }
        if (isImageMessage) {
            clearBubbleBackground(holder);
        }

        bindQuote(holder, payload.quote);
        MessageActionBinder.bind(context,
                holder.bubble,
                holder.text,
                holder.image,
                holder.voiceRow,
                msg,
                payload,
                isMine,
                actionListener,
                new MessageActionBinder.RecallChecker() {
                    @Override
                    public boolean canRecall(Message target) {
                        return canRecallMessage(target);
                    }
                });
        maybeAnimateSend(holder, msg, isMine);
    }

    private boolean canReEditRecalled(Message msg, boolean isMine) {
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
}
