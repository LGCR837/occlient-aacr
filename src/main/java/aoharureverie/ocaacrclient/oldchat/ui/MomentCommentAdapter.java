package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.models.MomentComment;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MomentCommentAdapter extends BaseAdapter {
    private final Context context;
    private final List<MomentComment> comments;
    private final LayoutInflater inflater;

    public MomentCommentAdapter(Context context, List<MomentComment> comments) {
        this.context = context;
        this.comments = comments;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return comments == null ? 0 : comments.size();
    }

    @Override
    public Object getItem(int position) {
        return comments.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_moment_comment, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        MomentComment comment = comments.get(position);
        final MomentComment target = comment;
        String name = !TextUtils.isEmpty(target.from_name) ? target.from_name : target.from_uid;
        holder.tvName.setText(name == null ? "" : name);
        UserTitleBinder.bind(holder.tvTitleBadge, target.from_title);
        holder.tvBody.setText(target.body == null ? "" : target.body);
        holder.tvTime.setText(formatTime(target.created_at));
        ImageLoader.loadAvatar(holder.ivAvatar, target.from_avatar);
        holder.ivAvatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (target.from_uid == null || target.from_uid.isEmpty()) {
                    return;
                }
                Intent intent = new Intent(context, UserSpaceActivity.class);
                intent.putExtra("uid", target.from_uid);
                context.startActivity(intent);
            }
        });
        return convertView;
    }

    private String formatTime(long seconds) {
        if (seconds <= 0) {
            return "";
        }
        Date date = new Date(seconds * 1000L);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(date);
    }

    private static class ViewHolder {
        final ImageView ivAvatar;
        final TextView tvName;
        final TextView tvTitleBadge;
        final TextView tvBody;
        final TextView tvTime;

        ViewHolder(View view) {
            ivAvatar = view.findViewById(R.id.ivCommentAvatar);
            tvName = view.findViewById(R.id.tvCommentName);
            tvTitleBadge = view.findViewById(R.id.tvCommentTitleBadge);
            tvBody = view.findViewById(R.id.tvCommentBody);
            tvTime = view.findViewById(R.id.tvCommentTime);
        }
    }
}
