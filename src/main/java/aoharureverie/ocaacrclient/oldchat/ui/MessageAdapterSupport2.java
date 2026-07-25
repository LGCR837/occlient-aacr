package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.bili.BiliApi;
import aoharureverie.ocaacrclient.oldchat.bili.BiliShareUtil;
import aoharureverie.ocaacrclient.oldchat.models.Message;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;
import aoharureverie.ocaacrclient.oldchat.util.MediaUrlResolver;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;

import java.util.List;

import org.json.JSONObject;

abstract class MessageAdapterSupport2 extends android.support.v7.widget.RecyclerView.Adapter<android.support.v7.widget.RecyclerView.ViewHolder> {
    protected List<Message> messages;
    protected String myUID;
    protected Context context;
    protected MessageVoicePlayer voicePlayer;
    protected float fontScale = 1.0f;

    protected static class ResourceInfo {
        String title;
        String sub;
        boolean biliShare;
        String coverUrl;
        String coverFallbackUrl;
    }

    protected static class MusicShareInfo {
        String title;
        String artist;
        String sub;
        String coverUrl;
    }

    protected ResourceInfo parseResourceInfo(String rawText, String url) {
        ResourceInfo info = new ResourceInfo();
        String text = rawText == null ? "" : rawText;
        String title = "";
        String size = "";
        String hint = "";
        boolean isBiliShare = BiliShareUtil.isShareUrl(url);
        BiliShareUtil.ShareInfo shareInfo = isBiliShare ? BiliShareUtil.parseShareUrl(url) : null;

        String[] lines = text.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.length() == 0) {
                continue;
            }
            if (title.length() == 0 && (line.startsWith("资源:") || line.startsWith("资源：") || line.startsWith("文件:")
                    || line.startsWith("文件：") || line.startsWith("视频:") || line.startsWith("视频：")
                    || line.startsWith("标题:") || line.startsWith("标题："))) {
                int idx = line.indexOf(':');
                if (idx < 0) {
                    idx = line.indexOf('：');
                }
                if (idx >= 0 && idx + 1 < line.length()) {
                    title = line.substring(idx + 1).trim();
                }
                continue;
            }
            if (size.length() == 0 && (line.startsWith("大小:") || line.startsWith("大小：")
                    || line.startsWith("时长:") || line.startsWith("时长："))) {
                int idx = line.indexOf(':');
                if (idx < 0) {
                    idx = line.indexOf('：');
                }
                if (idx >= 0 && idx + 1 < line.length()) {
                    size = line.substring(idx + 1).trim();
                }
                continue;
            }
            if (hint.length() == 0 && line.contains("点击")) {
                if (line.contains("观看")) {
                    hint = "点击观看";
                } else if (line.contains("下载")) {
                    hint = "点击下载";
                }
            }
        }

        if (title.length() == 0 && shareInfo != null && shareInfo.title != null && shareInfo.title.length() > 0) {
            title = shareInfo.title.trim();
        }
        if (title.length() == 0) {
            title = guessNameFromUrl(url);
        }
        if (title.length() == 0) {
            title = isBiliShare ? "B站视频" : "资源分享";
        }

        if (size.length() == 0 && shareInfo != null && shareInfo.duration > 0) {
            size = "时长 " + formatShareDuration(shareInfo.duration);
        }

        if (size.length() > 0) {
            info.sub = size;
        } else if (hint.length() > 0) {
            info.sub = hint;
        } else {
            info.sub = isBiliShare ? "点击观看" : "点击下载";
        }

        info.title = title;
        info.biliShare = isBiliShare;
        String shareCover = shareInfo == null ? null : shareInfo.cover;
        info.coverUrl = normalizeShareCover(shareCover, true);
        info.coverFallbackUrl = normalizeShareCover(shareCover, false);
        return info;
    }

    protected MusicShareInfo parseMusicShareInfo(String rawText, String songUrl, String thumbUrl) {
        MusicShareInfo info = new MusicShareInfo();
        String text = rawText == null ? "" : rawText.trim();
        String title = "";
        String artist = "";
        String duration = "";
        String cover = normalizeMusicCoverUrl(thumbUrl);

        if (text.startsWith("{") && text.endsWith("}")) {
            try {
                JSONObject obj = new JSONObject(text);
                title = obj.optString("title", "");
                if (title.length() == 0) {
                    title = obj.optString("song_name", "");
                }
                if (title.length() == 0) {
                    title = obj.optString("name", "");
                }
                artist = obj.optString("artist", "");
                if (artist.length() == 0) {
                    artist = obj.optString("owner_name", "");
                }
                duration = obj.optString("duration", "");
                if (cover.length() == 0) {
                    cover = obj.optString("cover_url", "");
                }
                if (cover.length() == 0) {
                    cover = obj.optString("cover", "");
                }
                if (cover.length() == 0) {
                    cover = obj.optString("thumb_url", "");
                }
                if (cover.length() == 0) {
                    cover = obj.optString("thumb", "");
                }
            } catch (Exception e) {
            }
        }

        String firstLine = "";
        String[] lines = text.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.length() == 0) {
                continue;
            }
            if (firstLine.length() == 0) {
                firstLine = line;
            }
            if (title.length() == 0) {
                String parsedTitle = parseLabelValue(line, new String[]{"歌曲:", "歌曲：", "歌名:", "歌名：", "标题:", "标题："});
                if (parsedTitle.length() > 0) {
                    title = parsedTitle;
                    continue;
                }
            }
            if (artist.length() == 0) {
                String parsedArtist = parseLabelValue(line, new String[]{"歌手:", "歌手：", "作者:", "作者：", "演唱:", "演唱：", "上传者:", "上传者："});
                if (parsedArtist.length() > 0) {
                    artist = parsedArtist;
                    continue;
                }
            }
            if (duration.length() == 0) {
                String parsedDuration = parseLabelValue(line, new String[]{"时长:", "时长："});
                if (parsedDuration.length() > 0) {
                    duration = parsedDuration;
                    continue;
                }
            }
            if (cover.length() == 0) {
                String parsedCover = parseLabelValue(line, new String[]{"封面:", "封面：", "封面链接:", "封面链接：", "cover:", "cover：", "cover_url:", "cover_url：", "thumb_url:", "thumb_url："});
                if (parsedCover.length() > 0) {
                    cover = parsedCover;
                }
            }
        }

        if (title.length() == 0) {
            title = firstLine;
        }
        if (title.length() == 0) {
            title = guessNameFromUrl(songUrl);
        }
        if (title.length() == 0) {
            title = "音乐分享";
        }

        String sub;
        if (artist.length() > 0 && duration.length() > 0) {
            sub = artist + " · " + duration;
        } else if (artist.length() > 0) {
            sub = artist;
        } else if (duration.length() > 0) {
            sub = duration;
        } else {
            sub = "点击播放";
        }

        info.title = title;
        info.artist = artist;
        info.sub = sub;
        info.coverUrl = normalizeMusicCoverUrl(cover);
        return info;
    }

    private String normalizeMusicCoverUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String out = raw.trim();
        if (out.length() == 0) {
            return "";
        }
        if (out.length() > 1) {
            if ((out.startsWith("\"") && out.endsWith("\"")) || (out.startsWith("'") && out.endsWith("'"))) {
                out = out.substring(1, out.length() - 1).trim();
            }
        }
        out = out.replace("\\u0026", "&");
        out = out.replace("\\/", "/");
        if (out.startsWith("cover_url=") || out.startsWith("thumb_url=")) {
            int idx = out.indexOf('=');
            if (idx >= 0 && idx + 1 < out.length()) {
                out = out.substring(idx + 1).trim();
            }
        }
        if (out.startsWith("v1/") || out.startsWith("music/") || out.startsWith("uploads/")) {
            out = "/" + out;
        }
        if (out.startsWith("/v1/uploads/media/")) {
            String name = out.substring("/v1/uploads/media/".length()).trim();
            if (name.length() > 0 && name.indexOf('/') < 0 && name.indexOf('\\') < 0) {
                out = "/v1/music/cover/" + name;
            }
        } else if (out.startsWith("/uploads/media/")) {
            String name = out.substring("/uploads/media/".length()).trim();
            if (name.length() > 0 && name.indexOf('/') < 0 && name.indexOf('\\') < 0) {
                out = "/v1/music/cover/" + name;
            }
        } else if (out.startsWith("/music/cover/")) {
            out = "/v1" + out;
        }
        return out;
    }

    private String parseLabelValue(String line, String[] labels) {
        if (line == null || labels == null) {
            return "";
        }
        String value = line.trim();
        if (value.length() == 0) {
            return "";
        }
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            if (label != null && value.startsWith(label)) {
                String parsed = value.substring(label.length()).trim();
                return parsed == null ? "" : parsed;
            }
        }
        return "";
    }

    protected void bindResourcePreview(MessageAdapterSupport1.ViewHolder holder, ResourceInfo info) {
        if (holder == null || holder.resourceCoverContainer == null) {
            return;
        }
        holder.resourceCoverContainer.setVisibility(View.GONE);
        if (holder.resourceCover != null) {
            holder.resourceCover.setTag(null);
        }
        if (holder.resourcePlay != null) {
            holder.resourcePlay.setVisibility(View.VISIBLE);
            holder.resourcePlay.setImageResource(R.drawable.ic_action_video);
        }
        if (info == null || !info.biliShare) {
            return;
        }
        String cover = info.coverUrl == null ? "" : info.coverUrl.trim();
        String fallback = info.coverFallbackUrl == null ? "" : info.coverFallbackUrl.trim();
        if (cover.length() == 0) {
            cover = fallback;
            fallback = "";
        }
        if (cover.length() == 0 || holder.resourceCover == null) {
            return;
        }
        holder.resourceCoverContainer.setVisibility(View.VISIBLE);
        loadShareCover(holder.resourceCover, cover, fallback);
    }


    protected void bindMusicPreview(MessageAdapterSupport1.ViewHolder holder, String coverUrl) {
        if (holder == null || holder.resourceCoverContainer == null) {
            return;
        }
        holder.resourceCoverContainer.setVisibility(View.GONE);
        if (holder.resourceCover != null) {
            holder.resourceCover.setTag(null);
        }
        if (holder.resourcePlay != null) {
            holder.resourcePlay.setVisibility(View.VISIBLE);
            holder.resourcePlay.setImageResource(R.drawable.ic_voice);
        }
    }

    private void loadShareCover(final ImageView view, final String cover, final String fallbackCover) {
        if (view == null || cover == null || cover.length() == 0) {
            return;
        }
        Object tag = view.getTag();
        if (tag != null && cover.equals(tag)) {
            return;
        }
        view.setTag(cover);
        view.setImageResource(R.drawable.bg_image_placeholder);
        ImageLoader.load(view, cover, new ImageLoader.ImageLoadListener() {
            @Override
            public void onComplete(String url) {
                Object currentTag = view.getTag();
                if (currentTag == null || !cover.equals(currentTag)) {
                    return;
                }
                if (fallbackCover == null || fallbackCover.length() == 0 || cover.equals(fallbackCover)) {
                    return;
                }
                android.content.Context ctx = view.getContext();
                if (ctx != null && ImageLoader.isCached(ctx, cover)) {
                    return;
                }
                view.setTag(fallbackCover);
                view.setImageResource(R.drawable.bg_image_placeholder);
                ImageLoader.load(view, fallbackCover);
            }
        });
    }

    private String normalizeShareCover(String cover, boolean stripProcess) {
        if (cover == null) {
            return "";
        }
        String out = cover.trim();
        if (out.length() == 0) {
            return "";
        }
        out = out.replace("\\u0026", "&");
        out = out.replace("\\/", "/");
        out = BiliApi.normalizeUrl(out);
        if (out.startsWith("//")) {
            out = "https:" + out;
        }
        int hash = out.indexOf('#');
        if (hash >= 0) {
            out = out.substring(0, hash);
        }
        int q = out.indexOf('?');
        if (q >= 0) {
            out = out.substring(0, q);
        }
        if (stripProcess) {
            int slash = out.lastIndexOf('/');
            int at = out.indexOf('@', slash >= 0 ? slash : 0);
            if (at > 0) {
                out = out.substring(0, at);
            }
            out = stripAfterImageExt(out);
        }
        return out;
    }

    private String stripAfterImageExt(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        String lower = value.toLowerCase(java.util.Locale.US);
        String[] exts = new String[]{".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp"};
        int cut = -1;
        for (int i = 0; i < exts.length; i++) {
            int idx = lower.indexOf(exts[i]);
            if (idx < 0) {
                continue;
            }
            int end = idx + exts[i].length();
            if (cut < 0 || end < cut) {
                cut = end;
            }
        }
        if (cut > 0 && cut < value.length()) {
            return value.substring(0, cut);
        }
        return value;
    }

    private String formatShareDuration(long durationSec) {
        if (durationSec <= 0) {
            return "";
        }
        long hours = durationSec / 3600;
        long minutes = (durationSec % 3600) / 60;
        long seconds = durationSec % 60;
        if (hours > 0) {
            return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds);
    }

    protected void openMusicShare(String title, String artist, String songUrl, String coverUrl) {
        String url = MediaUrlResolver.resolve(songUrl);
        if (url == null || url.length() == 0) {
            Toast.makeText(context, "无法获取播放地址", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(context, MusicPlayerActivity.class);
        intent.putExtra("song_name", title == null ? "" : title);
        intent.putExtra("song_url", url);
        intent.putExtra("cover_url", coverUrl == null ? "" : coverUrl);
        intent.putExtra("owner_name", artist == null ? "" : artist);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    protected void openVideo(Message message) {
        if (message == null || message.media_url == null || message.media_url.isEmpty()) {
            Toast.makeText(context, "无法获取播放地址", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = MediaUrlResolver.resolve(message.media_url);
        if (url == null || url.length() == 0) {
            Toast.makeText(context, "无法获取播放地址", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(url), "video/*");
            if (!(context instanceof android.app.Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "无法播放视频", Toast.LENGTH_SHORT).show();
        }
    }

    protected void openBiliShare(String url) {
        BiliShareUtil.ShareInfo info = BiliShareUtil.parseShareUrl(url);
        Intent intent = new Intent(context, OldViewVideoDetailActivity.class);
        if (info.bvid != null && info.bvid.length() > 0) {
            intent.putExtra(OldViewVideoDetailActivity.EXTRA_BVID, info.bvid);
        }
        if (info.aid > 0) {
            intent.putExtra(OldViewVideoDetailActivity.EXTRA_AID, info.aid);
        }
        if (info.cid > 0) {
            intent.putExtra(OldViewVideoDetailActivity.EXTRA_CID, info.cid);
        }
        String normalizedCover = normalizeShareCover(info.cover, false);
        if (normalizedCover.length() > 0) {
            intent.putExtra(OldViewVideoDetailActivity.EXTRA_COVER, normalizedCover);
        }
        if (info.title != null && info.title.length() > 0) {
            intent.putExtra(OldViewVideoDetailActivity.EXTRA_TITLE, info.title);
        }
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    protected void openRedPacket(String packetId) {
        if (packetId == null || packetId.isEmpty()) {
            Toast.makeText(context, R.string.red_packet_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(context, RedPacketOpenActivity.class);
        intent.putExtra(RedPacketOpenActivity.EXTRA_PACKET_ID, packetId);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    protected String guessNameFromUrl(String url) {
        if (url == null) {
            return "";
        }
        String s = url;
        int q = s.indexOf('?');
        if (q >= 0) {
            s = s.substring(0, q);
        }
        int h = s.indexOf('#');
        if (h >= 0) {
            s = s.substring(0, h);
        }
        int slash = s.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < s.length()) {
            return s.substring(slash + 1);
        }
        return "";
    }

    protected void bindQuote(MessageAdapterSupport1.ViewHolder holder, MessagePayload.Quote quote) {
        if (holder.quoteContainer == null) {
            return;
        }
        if (quote == null) {
            holder.quoteContainer.setVisibility(View.GONE);
            holder.quoteContainer.setOnClickListener(null);
            if (holder.quoteImage != null) {
                holder.quoteImage.setVisibility(View.GONE);
                holder.quoteImage.setTag(null);
            }
            return;
        }
        String sender = quote.fromName != null && !quote.fromName.isEmpty() ? quote.fromName : quote.fromUid;
        if (sender == null || sender.isEmpty()) {
            sender = "对方";
        }
        holder.quoteSender.setText(sender);
        holder.quoteContent.setText(ChatMessageUtil.quotePreview(quote.type, quote.mediaKind, quote.text));
        holder.quoteContainer.setVisibility(View.VISIBLE);
        bindQuoteThumb(holder.quoteImage, quote.thumbUrl);
        final MessagePayload.Quote target = quote;
        holder.quoteContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (quoteClickListener != null && target.id != null && !target.id.isEmpty()) {
                    quoteClickListener.onQuoteClick(target);
                }
            }
        });
    }

    protected void bindQuoteThumb(ImageView view, String url) {
        if (view == null) {
            return;
        }
        if (url == null || url.isEmpty()) {
            view.setVisibility(View.GONE);
            view.setTag(null);
            return;
        }
        view.setVisibility(View.VISIBLE);
        ImageLoader.load(view, url);
    }

    protected boolean canRecallMessage(Message msg) {
        if (msg.created_at <= 0) {
            return false;
        }
        long currentTime = System.currentTimeMillis();
        long messageTime = ChatMessageUtil.normalizeTimestamp(msg.created_at);
        return (currentTime - messageTime) < 60 * 1000;
    }

    protected int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    protected android.view.animation.Animation buildVoiceAnimation() {
        android.view.animation.AlphaAnimation anim = new android.view.animation.AlphaAnimation(0.4f, 1.0f);
        anim.setDuration(400);
        anim.setRepeatMode(android.view.animation.Animation.REVERSE);
        anim.setRepeatCount(android.view.animation.Animation.INFINITE);
        return anim;
    }

    protected void bindTimeLabel(MessageAdapterSupport1.ViewHolder holder, Message msg, int position) {
        if (holder.messageTime == null) {
            return;
        }
        boolean shouldShow = shouldShowTime(msg, position);
        if (shouldShow) {
            String label = ChatTimeFormatter.formatTime(msg.created_at);
            if (label.isEmpty()) {
                holder.messageTime.setVisibility(android.view.View.GONE);
            } else {
                holder.messageTime.setText(label);
                holder.messageTime.setVisibility(android.view.View.VISIBLE);
            }
        } else {
            holder.messageTime.setVisibility(android.view.View.GONE);
        }
    }

    protected boolean shouldShowTime(Message msg, int position) {
        if (position == 0) {
            return true;
        }
        Message prev = messages.get(position - 1);
        return ChatTimeFormatter.shouldShowTime(msg.created_at, prev.created_at);
    }

    protected MessageAdapter.QuoteClickListener quoteClickListener;
}
