package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
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
import java.util.ArrayList;
import java.util.List;

public class GroupMentionAdapter extends BaseAdapter {
    private final Context context;
    private final List<GroupMember> allMembers = new ArrayList<>();
    private final List<GroupMember> filtered = new ArrayList<>();
    private String excludeUid;
    private String query = "";
    private int myRole = 0;

    public GroupMentionAdapter(Context context, List<GroupMember> members, String excludeUid) {
        this.context = context;
        this.excludeUid = excludeUid;
        setMembers(members);
    }

    public void setMyRole(int role) {
        this.myRole = role;
        filter(query);
    }

    public void setMembers(List<GroupMember> members) {
        allMembers.clear();
        if (members != null && !members.isEmpty()) {
            allMembers.addAll(members);
        }
        filter(query);
    }

    public void setExcludeUid(String uid) {
        this.excludeUid = uid;
        filter(query);
    }

    public void filter(String query) {
        this.query = query == null ? "" : query.trim();
        filtered.clear();
        String q = this.query.toLowerCase();

        // Add @all option for admins and owners
        if (myRole >= 1) {
            if (q.length() == 0 || "所有人".contains(q) || "all".contains(q)) {
                GroupMember allMember = new GroupMember();
                allMember.uid = "ALL";
                allMember.display_name = "所有人";
                allMember.username = "all";
                allMember.role = -1; // Special role for @all
                filtered.add(allMember);
            }
        }

        for (int i = 0; i < allMembers.size(); i++) {
            GroupMember member = allMembers.get(i);
            if (member == null || member.uid == null || member.uid.isEmpty()) {
                continue;
            }
            if (excludeUid != null && excludeUid.equals(member.uid)) {
                continue;
            }
            String name = displayName(member);
            if (q.length() == 0) {
                filtered.add(member);
            } else {
                String nameLower = name == null ? "" : name.toLowerCase();
                String uidLower = member.uid.toLowerCase();
                String usernameLower = member.username == null ? "" : member.username.toLowerCase();
                if (nameLower.contains(q) || uidLower.contains(q) || usernameLower.contains(q)) {
                    filtered.add(member);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return filtered.size();
    }

    @Override
    public GroupMember getItem(int position) {
        return filtered.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_group_mention, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        GroupMember member = getItem(position);
        String name = displayName(member);
        holder.name.setText(name == null ? "" : name);

        // Special handling for @all
        if ("ALL".equals(member.uid)) {
            holder.uid.setText("提及所有成员");
            holder.avatar.setImageResource(R.drawable.ic_notification);
        } else {
            holder.uid.setText(member.uid == null ? "" : member.uid);
            String avatarUrl = member.avatar_url;
            if (avatarUrl == null || avatarUrl.isEmpty()) {
                avatarUrl = GroupAvatarCache.getCachedAvatar(context, member.uid);
            }
            ImageLoader.loadAvatar(holder.avatar, avatarUrl);
        }
        return convertView;
    }

    private String displayName(GroupMember member) {
        if (member == null) {
            return "";
        }
        if (member.display_name != null && !member.display_name.isEmpty()) {
            return member.display_name;
        }
        if (member.username != null && !member.username.isEmpty()) {
            return member.username;
        }
        return member.uid == null ? "" : member.uid;
    }

    private static class ViewHolder {
        final ImageView avatar;
        final TextView name;
        final TextView uid;

        ViewHolder(View view) {
            avatar = view.findViewById(R.id.ivAvatar);
            name = view.findViewById(R.id.tvName);
            uid = view.findViewById(R.id.tvUid);
        }
    }
}
