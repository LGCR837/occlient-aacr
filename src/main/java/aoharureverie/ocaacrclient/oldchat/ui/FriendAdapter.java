package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.models.FriendNameResolver;
import aoharureverie.ocaacrclient.oldchat.models.Group;
import aoharureverie.ocaacrclient.oldchat.models.User;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;
import java.util.ArrayList;
import java.util.List;

public class FriendAdapter extends BaseAdapter {
    private static final int TYPE_SECTION = 0;
    private static final int TYPE_REQUEST = 1;
    private static final int TYPE_GROUP = 2;
    private static final int TYPE_FRIEND = 3;
    private static final int TYPE_SYSTEM = 4;

    public static final String SYSTEM_UID = "SYSTEM";

    private final List<Object> items = new ArrayList<>();
    private final List<Group> groups = new ArrayList<>();
    private final List<User> recentFriends = new ArrayList<>();
    private final List<User> otherFriends = new ArrayList<>();
    private final List<FriendRequestItem> requests = new ArrayList<>();
    private String query = "";
    private Context context;
    private OnFriendClickListener listener;

    public interface OnFriendClickListener {
        void onFriendClick(User friend);
        void onAvatarClick(User friend);
        void onRequestAccept(String requestId);
        void onRequestReject(String requestId);
        void onGroupClick(Group group);
        void onSystemNotificationClick();
    }

    public static class SystemNotificationEntry {
        public static final SystemNotificationEntry INSTANCE = new SystemNotificationEntry();
    }

    public static class SectionHeader {
        public String title;
        public int count;
        public SectionHeader(String title) {
            this.title = title;
            this.count = 0;
        }
        public SectionHeader(String title, int count) {
            this.title = title;
            this.count = count;
        }
    }

    public static class FriendRequestItem {
        public String id;
        public String fromUID;
        public String fromName;
        public String fromTitle;
        public String avatarUrl;
        public FriendRequestItem(String id, String uid, String name, String title, String avatarUrl) {
            this.id = id;
            this.fromUID = uid;
            this.fromName = name;
            this.fromTitle = title;
            this.avatarUrl = avatarUrl;
        }
    }

    public FriendAdapter(Context context, OnFriendClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setData(List<Group> groups, List<User> recentFriends, List<User> otherFriends, List<FriendRequestItem> requests) {
        this.groups.clear();
        this.recentFriends.clear();
        this.otherFriends.clear();
        this.requests.clear();
        if (groups != null) this.groups.addAll(groups);
        if (recentFriends != null) this.recentFriends.addAll(recentFriends);
        if (otherFriends != null) this.otherFriends.addAll(otherFriends);
        if (requests != null) this.requests.addAll(requests);
        rebuildItems();
    }

    public void filter(String query) {
        this.query = query == null ? "" : query.trim();
        rebuildItems();
    }

    private void rebuildItems() {
        items.clear();
        String q = query == null ? "" : query.trim();
        if (q.length() == 0) {
            // Normal grouped list
            items.add(SystemNotificationEntry.INSTANCE);
            if (!groups.isEmpty()) {
                items.add(new SectionHeader("群聊"));
                items.addAll(groups);
            }
            if (!requests.isEmpty()) {
                items.add(new SectionHeader("好友申请", requests.size()));
                items.addAll(requests);
            }
            if (!recentFriends.isEmpty()) {
                items.add(new SectionHeader("最近新增"));
                items.addAll(recentFriends);
            }
            if (!otherFriends.isEmpty()) {
                items.add(new SectionHeader("全部好友"));
                items.addAll(otherFriends);
            }
            notifyDataSetChanged();
            return;
        }

        String qLower = q.toLowerCase();
        List<Object> results = new ArrayList<>();

        if (containsIgnoreCase("系统通知 官方公告和通知", qLower) || containsIgnoreCase("系统通知", qLower)) {
            results.add(SystemNotificationEntry.INSTANCE);
        }

        for (int i = 0; i < groups.size(); i++) {
            Group g = groups.get(i);
            if (g == null) continue;
            if (containsIgnoreCase(g.name, qLower) || containsIgnoreCase(g.id, qLower)) {
                results.add(g);
            }
        }

        for (int i = 0; i < requests.size(); i++) {
            FriendRequestItem r = requests.get(i);
            if (r == null) continue;
            if (containsIgnoreCase(r.fromName, qLower) || containsIgnoreCase(r.fromUID, qLower)) {
                results.add(r);
            }
        }

        for (int i = 0; i < recentFriends.size(); i++) {
            User u = recentFriends.get(i);
            if (u == null) continue;
            if (matchesUser(u, qLower)) {
                results.add(u);
            }
        }

        for (int i = 0; i < otherFriends.size(); i++) {
            User u = otherFriends.get(i);
            if (u == null) continue;
            if (matchesUser(u, qLower)) {
                results.add(u);
            }
        }

        if (results.isEmpty()) {
            items.add(new SectionHeader("无匹配结果"));
        } else {
            items.add(new SectionHeader("搜索结果"));
            items.addAll(results);
        }
        notifyDataSetChanged();
    }

    private boolean matchesUser(User u, String qLower) {
        if (u == null) return false;
        return containsIgnoreCase(u.remark_name, qLower)
                || containsIgnoreCase(u.display_name, qLower)
                || containsIgnoreCase(u.username, qLower)
                || containsIgnoreCase(u.uid, qLower);
    }

    private boolean containsIgnoreCase(String text, String qLower) {
        if (qLower == null || qLower.length() == 0) return true;
        if (text == null) return false;
        try {
            return text.toLowerCase().contains(qLower);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int getCount() { return items.size(); }

    @Override
    public Object getItem(int position) { return items.get(position); }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public int getViewTypeCount() {
        return 5;
    }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof SectionHeader) {
            return TYPE_SECTION;
        }
        if (item instanceof FriendRequestItem) {
            return TYPE_REQUEST;
        }
        if (item instanceof Group) {
            return TYPE_GROUP;
        }
        if (item instanceof SystemNotificationEntry) {
            return TYPE_SYSTEM;
        }
        return TYPE_FRIEND;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Object item = items.get(position);
        int type = getItemViewType(position);
        if (type == TYPE_SECTION) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_section_header, parent, false);
            }
            TextView title = (TextView) convertView.findViewById(R.id.tvSectionTitle);
            TextView badge = (TextView) convertView.findViewById(R.id.tvBadge);
            SectionHeader header = (SectionHeader) item;
            title.setText(header.title);

            // 显示红点
            if (header.count > 0) {
                badge.setText(header.count > 99 ? "99+" : String.valueOf(header.count));
                badge.setVisibility(View.VISIBLE);
            } else {
                badge.setVisibility(View.GONE);
            }

            convertView.setOnClickListener(null);
            convertView.setClickable(false);
            return convertView;
        }

        if (type == TYPE_SYSTEM) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_friend, parent, false);
            }
            // item_friend.xml uses tvName/tvSignature; older layouts may use tvTitle/tvSubtitle.
            TextView t1 = (TextView) convertView.findViewById(R.id.tvName);
            if (t1 == null) t1 = convertView.findViewById(R.id.tvTitle);
            TextView t2 = (TextView) convertView.findViewById(R.id.tvSignature);
            if (t2 == null) t2 = convertView.findViewById(R.id.tvSubtitle);
            TextView badge = (TextView) convertView.findViewById(R.id.tvTitleBadge);
            ImageView avatar = (ImageView) convertView.findViewById(R.id.ivAvatar);
            if (t1 != null) t1.setText("系统通知");
            if (t2 != null) t2.setText("官方公告和通知");
            UserTitleBinder.bind(badge, "");
            avatar.setImageResource(R.drawable.ic_notification);
            try {
                avatar.setColorFilter(context.getResources().getColor(R.color.color_text_secondary));
            } catch (Exception ignored) {
            }
            avatar.setOnClickListener(null);
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onSystemNotificationClick();
                }
            });
            return convertView;
        }

        if (type == TYPE_REQUEST) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_friend_request, parent, false);
            }
            FriendRequestItem req = (FriendRequestItem) item;
            final FriendRequestItem reqFinal = req;
            String name = req.fromName != null && !req.fromName.isEmpty() ? req.fromName : req.fromUID;

            TextView t1 = (TextView) convertView.findViewById(R.id.tvTitle);
            TextView t2 = (TextView) convertView.findViewById(R.id.tvSubtitle);
            TextView badge = (TextView) convertView.findViewById(R.id.tvTitleBadge);
            ImageView avatar = (ImageView) convertView.findViewById(R.id.ivAvatar);

            avatar.setColorFilter(null);

            t1.setText(name == null ? "" : name);
            UserTitleBinder.bindCompact(badge, req.fromTitle);
            if (req.fromUID != null && !req.fromUID.isEmpty()) {
                t2.setText("UID: " + req.fromUID);
            } else {
                t2.setText("好友申请");
            }
            if (req.avatarUrl == null || req.avatarUrl.isEmpty()) {
                avatar.setImageResource(R.drawable.ic_avatar_placeholder);
                avatar.setTag(null);
            } else {
                ImageLoader.loadAvatar(avatar, req.avatarUrl);
            }
            avatar.setOnClickListener(null);

            // 设置按钮点击事件
            View btnAccept = (View) convertView.findViewById(R.id.btnAccept);
            View btnReject = (View) convertView.findViewById(R.id.btnReject);
            btnAccept.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onRequestAccept(reqFinal.id);
                }
            });
            btnReject.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onRequestReject(reqFinal.id);
                }
            });

            convertView.setOnClickListener(null);
            return convertView;
        }

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_friend, parent, false);
        }

        // item_friend.xml uses tvName/tvSignature; older layouts may use tvTitle/tvSubtitle.
        TextView t1 = (TextView) convertView.findViewById(R.id.tvName);
        if (t1 == null) t1 = convertView.findViewById(R.id.tvTitle);
        TextView t2 = (TextView) convertView.findViewById(R.id.tvSignature);
        if (t2 == null) t2 = convertView.findViewById(R.id.tvSubtitle);
        TextView badge = (TextView) convertView.findViewById(R.id.tvTitleBadge);
        ImageView avatar = (ImageView) convertView.findViewById(R.id.ivAvatar);

        if (type == TYPE_GROUP) {
            Group group = (Group) item;
            final Group groupFinal = group;
            if (t1 != null) t1.setText(group.name);
            if (t2 != null) t2.setText(group.id);
            UserTitleBinder.bind(badge, "");
            avatar.setColorFilter(null);
            if (group.avatar_url == null || group.avatar_url.isEmpty()) {
                avatar.setImageResource(R.drawable.group);
                avatar.setTag(null);
            } else {
                ImageLoader.loadAvatar(avatar, group.avatar_url);
            }
            avatar.setOnClickListener(null);
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onGroupClick(groupFinal);
                }
            });
        } else {
            User friend = (User) item;
            final User friendFinal = friend;
            if (t1 != null) t1.setText(FriendNameResolver.resolve(friend));
            if (t2 != null) t2.setText(buildFriendSubtitle(friend));
            UserTitleBinder.bindCompact(badge, friend.user_title);
            avatar.setColorFilter(null);
            ImageLoader.loadAvatar(avatar, friend.avatar_url);
            avatar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onAvatarClick(friendFinal);
                }
            });
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onFriendClick(friendFinal);
                }
            });
        }

        return convertView;
    }

    private String buildFriendSubtitle(User friend) {
        if (friend == null) {
            return "";
        }
        String uid = safeText(friend.uid);
        String remark = safeText(friend.remark_name);
        String original = safeText(friend.display_name);
        if (original.length() == 0) {
            original = safeText(friend.username);
        }
        if (original.length() == 0) {
            original = uid;
        }

        if (remark.length() > 0 && original.length() > 0 && !remark.equals(original)) {
            if (uid.length() > 0) {
                return "原名：" + original + " · UID: " + uid;
            }
            return "原名：" + original;
        }

        if (uid.length() > 0) {
            return "UID: " + uid;
        }
        return "";
    }

    private String safeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
