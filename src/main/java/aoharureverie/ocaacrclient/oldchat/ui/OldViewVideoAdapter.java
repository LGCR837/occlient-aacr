package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.bili.BiliApi;
import aoharureverie.ocaacrclient.oldchat.bili.BiliModels;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;

public class OldViewVideoAdapter extends BaseAdapter {
    public interface OnVideoActionListener {
        void onOpenUpProfile(BiliModels.RecommendItem item);
    }

    private final LayoutInflater inflater;
    private final List<BiliModels.RecommendItem> items = new ArrayList<BiliModels.RecommendItem>();
    private OnVideoActionListener actionListener;

    public OldViewVideoAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void setOnVideoActionListener(OnVideoActionListener listener) {
        this.actionListener = listener;
    }

    public void update(List<BiliModels.RecommendItem> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        if (position < 0 || position >= items.size()) {
            return null;
        }
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_old_view_video, parent, false);
            holder = new ViewHolder();
            holder.cover = (ImageView) convertView.findViewById(R.id.ivOldViewCover);
            holder.title = (TextView) convertView.findViewById(R.id.tvOldViewTitle);
            holder.upName = (TextView) convertView.findViewById(R.id.tvOldViewUpName);
            holder.btnUpProfile = (TextView) convertView.findViewById(R.id.btnOldViewUpProfile);
            holder.meta = (TextView) convertView.findViewById(R.id.tvOldViewMeta);
            holder.duration = (TextView) convertView.findViewById(R.id.tvOldViewDuration);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final BiliModels.RecommendItem item = items.get(position);
        if (item != null) {
            holder.title.setText(item.title != null ? item.title : "");
            holder.upName.setText(buildUpName(item));
            holder.meta.setText(buildMeta(item));

            String duration = item.duration != null ? item.duration.trim() : "";
            if (duration.length() > 0) {
                holder.duration.setText(duration);
                holder.duration.setVisibility(View.VISIBLE);
            } else {
                holder.duration.setVisibility(View.GONE);
            }

            String coverUrl = BiliApi.normalizeUrl(item.cover);
            if (coverUrl != null && coverUrl.length() > 0) {
                ImageLoader.load(holder.cover, coverUrl);
            } else {
                holder.cover.setImageResource(android.R.drawable.ic_media_play);
            }

            if (actionListener != null && hasUpMid(item)) {
                holder.btnUpProfile.setVisibility(View.VISIBLE);
                View.OnClickListener upClick = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (actionListener != null) {
                            actionListener.onOpenUpProfile(item);
                        }
                    }
                };
                holder.btnUpProfile.setOnClickListener(upClick);
                holder.upName.setOnClickListener(upClick);
            } else {
                holder.btnUpProfile.setVisibility(View.GONE);
                holder.btnUpProfile.setOnClickListener(null);
                holder.upName.setOnClickListener(null);
            }
        }

        return convertView;
    }

    private String buildUpName(BiliModels.RecommendItem item) {
        if (item != null && item.args != null && item.args.upName != null && item.args.upName.length() > 0) {
            return item.args.upName;
        }
        return "UP 主";
    }

    private String buildMeta(BiliModels.RecommendItem item) {
        StringBuilder sb = new StringBuilder();
        if (item.playCount != null && item.playCount.length() > 0) {
            appendMeta(sb, "播放 " + item.playCount);
        }
        if (item.danmakuCount != null && item.danmakuCount.length() > 0) {
            String danmaku = item.danmakuCount;
            if (danmaku.indexOf("弹幕") < 0) {
                danmaku = "弹幕 " + danmaku;
            }
            appendMeta(sb, danmaku);
        }
        if (sb.length() == 0) {
            return "推荐视频";
        }
        return sb.toString();
    }

    private void appendMeta(StringBuilder sb, String text) {
        if (text == null || text.length() == 0) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(" · ");
        }
        sb.append(text);
    }

    private boolean hasUpMid(BiliModels.RecommendItem item) {
        if (item == null || item.args == null) {
            return false;
        }
        return item.args.upId > 0 || item.args.mid > 0;
    }

    private static class ViewHolder {
        ImageView cover;
        TextView title;
        TextView upName;
        TextView btnUpProfile;
        TextView meta;
        TextView duration;
    }
}
