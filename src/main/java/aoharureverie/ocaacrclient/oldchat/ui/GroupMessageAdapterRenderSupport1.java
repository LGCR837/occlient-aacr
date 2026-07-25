package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.bili.BiliShareUtil;
import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.util.ClipboardUtil;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;
import aoharureverie.ocaacrclient.oldchat.util.MediaUrlResolver;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;
import aoharureverie.ocaacrclient.oldchat.util.RedPacketPayload;

import java.util.List;

abstract class GroupMessageAdapterRenderSupport1 extends GroupMessageAdapterRenderSupport0 {
    GroupMessageAdapterRenderSupport1(android.content.Context context, List<GroupMessage> messages, String myUID) {
        super(context, messages, myUID);
    }

    @Override
    protected void bindMessageTypeSection(final ViewHolder holderFinal, final GroupMessage target,
                                          MessagePayload payload, String type, boolean isMine) {
        if ("image".equals(type) || "video".equals(type)) {
            final boolean isVideo = "video".equals(type);
            if (holderFinal.imageContainer != null) {
                holderFinal.imageContainer.setVisibility(View.VISIBLE);
            }
            holderFinal.image.setVisibility(View.VISIBLE);
            final String thumb = !TextUtils.isEmpty(target.thumb_url) ? target.thumb_url : target.media_url;
            ImageLoader.load(holderFinal.image, thumb);
            holderFinal.image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isVideo) {
                        openVideo(target);
                    } else {
                        ImagePreviewActivity.start(context, target.media_url);
                    }
                }
            });
            if (holderFinal.videoPlay != null) {
                holderFinal.videoPlay.setVisibility(isVideo ? View.VISIBLE : View.GONE);
            }
            return;
        }

        if ("voice".equals(type)) {
            holderFinal.voiceRow.setVisibility(View.VISIBLE);
            int seconds = Math.max(1, target.duration_ms / 1000);
            seconds = Math.min(seconds, MAX_VOICE_SECONDS);
            boolean loadingVoice = voicePlayer.isLoading(target);
            int width = dpToPx(VOICE_MIN_DP + (VOICE_MAX_DP - VOICE_MIN_DP) * seconds / MAX_VOICE_SECONDS);
            if (loadingVoice) {
                width = dpToPx(VOICE_MAX_DP);
            }
            ViewGroup.LayoutParams params = holderFinal.voiceRow.getLayoutParams();
            if (params instanceof LinearLayout.LayoutParams) {
                params.width = width;
                holderFinal.voiceRow.setLayoutParams(params);
            }
            holderFinal.voiceDuration.setText(voicePlayer.getDurationLabel(target, seconds));
            if (holderFinal.voiceLoading != null) {
                holderFinal.voiceLoading.setVisibility(loadingVoice ? View.VISIBLE : View.GONE);
            }
            holderFinal.voiceRow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    voicePlayer.play(target);
                }
            });
            if (voicePlayer.isPlaying(target)) {
                holderFinal.voiceIcon.startAnimation(buildVoiceAnimation());
            } else {
                holderFinal.voiceIcon.clearAnimation();
            }
            return;
        }

        boolean isMusicMessage = "music".equals(type)
                || ("resource".equals(type) && payload != null && "music".equals(payload.mediaKind));
        if (isMusicMessage) {
            holderFinal.text.setVisibility(View.GONE);
            holderFinal.resourceCard.setVisibility(View.VISIBLE);
            holderFinal.resourceAction.setVisibility(View.VISIBLE);
            final String songUrl = MediaUrlResolver.resolve(target.media_url);
            MusicShareInfo info = parseMusicShareInfo(payload.text, songUrl, target.thumb_url);
            holderFinal.resourceTitle.setText(info.title);
            holderFinal.resourceSub.setText(info.sub);
            holderFinal.resourceAction.setText("播放");
            holderFinal.resourceIcon.setImageResource(R.drawable.ic_voice);
            bindMusicPreview(holderFinal, info.coverUrl);
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
                holderFinal.bubble.setOnClickListener(open);
                holderFinal.resourceCard.setOnClickListener(open);
                holderFinal.resourceAction.setOnClickListener(open);
            } else {
                holderFinal.resourceSub.setText("歌曲链接不可用");
                holderFinal.resourceAction.setVisibility(View.GONE);
                holderFinal.bubble.setOnClickListener(null);
                holderFinal.resourceCard.setOnClickListener(null);
                holderFinal.resourceAction.setOnClickListener(null);
            }
            return;
        }

        if ("resource".equals(type)) {
            holderFinal.text.setVisibility(View.GONE);
            holderFinal.resourceCard.setVisibility(View.VISIBLE);
            final String url = MediaUrlResolver.resolve(target.media_url);
            if (url != null && url.length() > 0) {
                final boolean isBiliShare = BiliShareUtil.isShareUrl(url);
                ResourceInfo info = parseResourceInfo(payload.text, url);
                holderFinal.resourceTitle.setText(info.title);
                holderFinal.resourceSub.setText(info.sub);
                holderFinal.resourceAction.setVisibility(View.VISIBLE);
                holderFinal.resourceAction.setText(isBiliShare ? "观看" : "下载");
                holderFinal.resourceIcon.setImageResource(isBiliShare ? R.drawable.ic_action_video : R.drawable.ic_document);
                bindResourcePreview(holderFinal, info);
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
                                            context.startActivity(intent);
                                        } catch (Exception e) {
                                        }
                                    }
                                })
                                .setNeutralButton("取消", null)
                                .show();
                    }
                };
                holderFinal.bubble.setOnClickListener(open);
                holderFinal.resourceCard.setOnClickListener(open);
                holderFinal.resourceAction.setOnClickListener(open);
            } else {
                ResourceInfo info = parseResourceInfo(payload.text, "");
                holderFinal.resourceTitle.setText(info.title);
                holderFinal.resourceSub.setText("链接不可用");
                holderFinal.resourceIcon.setImageResource(R.drawable.ic_document);
                holderFinal.resourceAction.setText("下载");
                holderFinal.resourceAction.setVisibility(View.GONE);
                bindResourcePreview(holderFinal, null);
                holderFinal.bubble.setOnClickListener(null);
                holderFinal.resourceCard.setOnClickListener(null);
                holderFinal.resourceAction.setOnClickListener(null);
            }
            return;
        }

        if ("red_packet".equals(type)) {
            holderFinal.redPacketContainer.setVisibility(View.VISIBLE);
            RedPacketPayload redPacket = RedPacketPayload.fromBody(target.body);
            String title = redPacket.title == null ? "" : redPacket.title;
            if (title.isEmpty()) {
                title = context.getString(R.string.red_packet_title_default);
            }
            holderFinal.redPacketTitle.setText(title);
            String desc;
            if (redPacket.totalAmount > 0 && redPacket.totalCount > 0) {
                String amount = context.getString(R.string.message_red_packet_amount, redPacket.totalAmount);
                String count = context.getString(R.string.message_red_packet_count, redPacket.totalCount);
                desc = amount + " · " + count;
            } else {
                desc = context.getString(R.string.message_red_packet_tag);
            }
            holderFinal.redPacketDesc.setText(desc);
            if (holderFinal.redPacketOpenTip != null) {
                holderFinal.redPacketOpenTip.setText(R.string.red_packet_open_tip);
            }
            if (holderFinal.redPacketStatusIcon != null) {
                if (isMine) {
                    if (target.read_count > 0) {
                        holderFinal.redPacketStatusIcon.setImageResource(R.drawable.ic_msg_read);
                    } else {
                        holderFinal.redPacketStatusIcon.setImageResource(R.drawable.ic_msg_sent);
                    }
                    holderFinal.redPacketStatusIcon.setColorFilter(0x99FFFFFF);
                    holderFinal.redPacketStatusIcon.setVisibility(View.VISIBLE);
                } else {
                    holderFinal.redPacketStatusIcon.setVisibility(View.GONE);
                }
            }
            final String packetId = redPacket.packetId;
            View.OnClickListener open = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openRedPacket(packetId);
                }
            };
            holderFinal.bubble.setOnClickListener(open);
            holderFinal.redPacketContainer.setOnClickListener(open);
            return;
        }

        holderFinal.text.setVisibility(View.VISIBLE);
        holderFinal.text.setText(payload.text);
    }
}
