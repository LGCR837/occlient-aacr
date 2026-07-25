package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.models.Moment;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;
import aoharureverie.ocaacrclient.oldchat.util.MomentImageUtil;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MomentAdapter extends BaseAdapter {
    public interface OnMomentActionListener {
        void onLike(Moment moment);
        void onComment(Moment moment);
        void onAvatar(Moment moment);
    }

    private final Context context;
    private final List<Moment> moments;
    private final OnMomentActionListener listener;
    private final LayoutInflater inflater;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private final ArrayList<String> reusableOpenUrls = new ArrayList<String>();

    public MomentAdapter(Context context, List<Moment> moments, OnMomentActionListener listener) {
        this.context = context;
        this.moments = moments;
        this.listener = listener;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return moments == null ? 0 : moments.size();
    }

    @Override
    public Object getItem(int position) {
        return moments.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_moment, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Moment moment = moments.get(position);
        String name = !TextUtils.isEmpty(moment.from_name) ? moment.from_name : moment.from_uid;
        holder.tvName.setText(name == null ? "" : name);
        UserTitleBinder.bindCompact(holder.tvTitleBadge, moment.from_title);
        holder.tvTime.setText(formatTime(moment.created_at));

        if (TextUtils.isEmpty(moment.body)) {
            holder.tvBody.setVisibility(View.GONE);
        } else {
            holder.tvBody.setVisibility(View.VISIBLE);
            holder.tvBody.setText(moment.body);
        }

        final Moment target = moment;
        java.util.List<String> imageUrls = target.parsedImageUrls;
        if (imageUrls == null) {
            imageUrls = MomentImageUtil.parseUrls(target.image_url);
            target.parsedImageUrls = imageUrls;
        }
        if (imageUrls == null || imageUrls.isEmpty()) {
            holder.flImage.setVisibility(View.GONE);
            holder.ivImage.setOnClickListener(null);
            holder.ivImage.setTag(null);
            holder.tvImageCount.setVisibility(View.GONE);
        } else {
            holder.flImage.setVisibility(View.VISIBLE);
            final String imageUrl = imageUrls.get(0);
            holder.ivImage.setTag(imageUrl);
            ImageLoader.load(holder.ivImage, imageUrl);
            if (imageUrls.size() > 1) {
                holder.tvImageCount.setVisibility(View.VISIBLE);
                holder.tvImageCount.setText(imageUrls.size() + "张");
            } else {
                holder.tvImageCount.setVisibility(View.GONE);
            }
            reusableOpenUrls.clear();
            reusableOpenUrls.addAll(imageUrls);
            final java.util.ArrayList<String> openUrls = new java.util.ArrayList<String>(reusableOpenUrls);
            holder.ivImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (openUrls.size() > 1) {
                        MomentGalleryActivity.start(context, openUrls, 0);
                    } else {
                        ImagePreviewActivity.start(context, imageUrl);
                    }
                }
            });
        }

        ImageLoader.loadAvatar(holder.ivAvatar, target.from_avatar);
        holder.ivAvatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onAvatar(target);
                }
            }
        });

        // 更新点赞图标和文字
        int likedColor = ContextCompat.getColor(context, R.color.colorDanger);
        int normalColor = ContextCompat.getColor(context, R.color.color_text_secondary);
        if (target.liked) {
            holder.ivLikeIcon.setImageResource(R.drawable.ic_like_filled);
            holder.ivLikeIcon.setColorFilter(likedColor);
            holder.tvLikeCount.setTextColor(likedColor);
            holder.tvLikeCount.setText(target.likes > 0 ? String.valueOf(target.likes) : "已赞");
        } else {
            holder.ivLikeIcon.setImageResource(R.drawable.ic_like);
            holder.ivLikeIcon.setColorFilter(normalColor);
            holder.tvLikeCount.setTextColor(normalColor);
            holder.tvLikeCount.setText(target.likes > 0 ? String.valueOf(target.likes) : "赞");
        }

        // 更新评论数
        if (target.comments > 0) {
            holder.tvCommentCount.setText(String.valueOf(target.comments));
        } else {
            holder.tvCommentCount.setText("评论");
        }

        holder.btnLike.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onLike(target);
                }
            }
        });
        holder.btnComment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onComment(target);
                }
            }
        });

        return convertView;
    }

    private String formatTime(long seconds) {
        if (seconds <= 0) {
            return "";
        }
        long now = System.currentTimeMillis();
        long ts = seconds * 1000L;
        long diff = now - ts;
        if (diff < 0) {
            diff = 0;
        }
        long minute = 60L * 1000L;
        long hour = 60L * minute;
        long day = 24L * hour;
        if (diff < minute) {
            return "刚刚";
        }
        if (diff < hour) {
            return (diff / minute) + "分钟前";
        }
        if (diff < day) {
            return (diff / hour) + "小时前";
        }
        Date date = new Date(ts);
        return TIME_FORMAT.format(date);
    }

    private static class ViewHolder {
        final ImageView ivAvatar;
        final TextView tvName;
        final TextView tvTitleBadge;
        final TextView tvTime;
        final TextView tvBody;
        final View flImage;
        final ImageView ivImage;
        final TextView tvImageCount;
        final View btnLike;
        final View btnComment;
        final ImageView ivLikeIcon;
        final TextView tvLikeCount;
        final TextView tvCommentCount;
        final TextView tvCounts;

        ViewHolder(View view) {
            ivAvatar = view.findViewById(R.id.ivMomentAvatar);
            tvName = view.findViewById(R.id.tvMomentName);
            tvTitleBadge = view.findViewById(R.id.tvMomentTitleBadge);
            tvTime = view.findViewById(R.id.tvMomentTime);
            tvBody = view.findViewById(R.id.tvMomentBody);
            flImage = view.findViewById(R.id.flMomentImage);
            ivImage = view.findViewById(R.id.ivMomentImage);
            tvImageCount = view.findViewById(R.id.tvMomentImageCount);
            btnLike = view.findViewById(R.id.btnMomentLike);
            btnComment = view.findViewById(R.id.btnMomentComment);
            ivLikeIcon = view.findViewById(R.id.ivLikeIcon);
            tvLikeCount = view.findViewById(R.id.tvLikeCount);
            tvCommentCount = view.findViewById(R.id.tvCommentCount);
            tvCounts = view.findViewById(R.id.tvMomentCounts);
        }
    }
}
