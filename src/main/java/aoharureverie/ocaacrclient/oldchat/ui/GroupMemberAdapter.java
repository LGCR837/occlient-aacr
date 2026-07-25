package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.models.GroupMember;
import aoharureverie.ocaacrclient.oldchat.util.GroupAvatarCache;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;
import java.util.List;

public class GroupMemberAdapter extends BaseAdapter {
    public interface ActionListener {
        void onKick(GroupMember member);
        void onToggleAdmin(GroupMember member, boolean makeAdmin);
    }

    private final Context context;
    private List<GroupMember> members;
    private int myRole;
    private final ActionListener listener;

    public GroupMemberAdapter(Context context, List<GroupMember> members, int myRole, ActionListener listener) {
        this.context = context;
        this.members = members;
        this.myRole = myRole;
        this.listener = listener;
    }

    public void setMembers(List<GroupMember> members) {
        this.members = members;
        notifyDataSetChanged();
    }

    public void setMyRole(int myRole) {
        this.myRole = myRole;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() { return members == null ? 0 : members.size(); }

    @Override
    public Object getItem(int position) { return members.get(position); }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        GroupMember member = members.get(position);
        final GroupMember target = member;
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_group_member, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        String name = member.display_name != null && !member.display_name.isEmpty()
                ? member.display_name : member.username;
        holder.title.setText(name == null ? "" : name);
        bindRoleBadge(holder.roleBadge, member.role);
        UserTitleBinder.bind(holder.titleBadge, member.user_title);
        holder.subtitle.setText(member.uid);
        String avatarUrl = member.avatar_url;
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            avatarUrl = GroupAvatarCache.getCachedAvatar(context, member.uid);
        }
        ImageLoader.loadAvatar(holder.avatar, avatarUrl);

        boolean isOwner = member.role == 2;
        boolean isAdmin = member.role == 1;
        final boolean isAdminFinal = isAdmin;
        boolean canKick = myRole >= 1 && !isOwner;
        boolean canSetAdmin = myRole == 2 && !isOwner;

        holder.btnKick.setVisibility(canKick ? View.VISIBLE : View.GONE);
        holder.btnAdmin.setVisibility(canSetAdmin ? View.VISIBLE : View.GONE);
        holder.btnAdmin.setText(isAdmin ? "取消管理员" : "设为管理员");

        holder.avatar.setOnClickListener(null);
        holder.btnKick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onKick(target);
                }
            }
        });
        holder.btnAdmin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onToggleAdmin(target, !isAdminFinal);
                }
            }
        });

        holder.avatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (target.uid == null || target.uid.isEmpty()) {
                    return;
                }
                Intent intent = new Intent(context, UserSpaceActivity.class);
                intent.putExtra("uid", target.uid);
                context.startActivity(intent);
            }
        });

        return convertView;
    }

    private void bindRoleBadge(TextView badge, int role) {
        if (badge == null) {
            return;
        }
        if (role == 2) {
            badge.setText("群主");
            badge.setBackgroundResource(R.drawable.bg_badge_owner);
            badge.setVisibility(View.VISIBLE);
        } else if (role == 1) {
            badge.setText("管理员");
            badge.setBackgroundResource(R.drawable.bg_badge_admin);
            badge.setVisibility(View.VISIBLE);
        } else {
            badge.setVisibility(View.GONE);
        }
    }

    private static class ViewHolder {
        final ImageView avatar;
        final TextView title;
        final TextView subtitle;
        final TextView roleBadge;
        final TextView titleBadge;
        final TextView btnKick;
        final TextView btnAdmin;

        ViewHolder(View view) {
            avatar = view.findViewById(R.id.ivAvatar);
            title = view.findViewById(R.id.tvTitle);
            subtitle = view.findViewById(R.id.tvSubtitle);
            roleBadge = view.findViewById(R.id.tvRoleBadge);
            titleBadge = view.findViewById(R.id.tvTitleBadge);
            btnKick = view.findViewById(R.id.btnKick);
            btnAdmin = view.findViewById(R.id.btnAdmin);
        }
    }
}
