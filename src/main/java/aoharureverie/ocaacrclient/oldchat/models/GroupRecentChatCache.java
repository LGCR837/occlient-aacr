package aoharureverie.ocaacrclient.oldchat.models;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GroupRecentChatCache {
    private static final String PREF_NAME = "recent_groups";

    public static class RecentGroup {
        public String groupId;
        public String groupName;
        public String avatarUrl;
        public String lastMessage;
        public long timestamp;
        public int unreadCount;
        public int role;
        public boolean mentionUnread;
        public int memberCount;

        public RecentGroup(String groupId, String groupName, String avatarUrl, String lastMessage,
                           long timestamp, int unreadCount, int role) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.avatarUrl = avatarUrl;
            this.lastMessage = lastMessage;
            this.timestamp = timestamp;
            this.unreadCount = unreadCount;
            this.role = role;
            this.mentionUnread = false;
            this.memberCount = -1;
        }

        public RecentGroup(String groupId, String groupName, String avatarUrl, String lastMessage,
                           long timestamp, int unreadCount, int role, boolean mentionUnread) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.avatarUrl = avatarUrl;
            this.lastMessage = lastMessage;
            this.timestamp = timestamp;
            this.unreadCount = unreadCount;
            this.role = role;
            this.mentionUnread = mentionUnread;
            this.memberCount = -1;
        }

        public RecentGroup(String groupId, String groupName, String avatarUrl, String lastMessage,
                           long timestamp, int unreadCount, int role, boolean mentionUnread, int memberCount) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.avatarUrl = avatarUrl;
            this.lastMessage = lastMessage;
            this.timestamp = timestamp;
            this.unreadCount = unreadCount;
            this.role = role;
            this.mentionUnread = mentionUnread;
            this.memberCount = memberCount;
        }
    }

    public static List<RecentGroup> getRecentGroups(Context context) {
        if (context == null) {
            return new ArrayList<>();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString("list", null);
        if (json == null) return new ArrayList<>();
        try {
            List<RecentGroup> chats = new Gson().fromJson(json,
                    TypeToken.getParameterized(List.class, RecentGroup.class).getType());
            if (chats == null) {
                return new ArrayList<>();
            }
            Collections.sort(chats, new java.util.Comparator<RecentGroup>() {
                @Override
                public int compare(RecentGroup a, RecentGroup b) {
                    if (a == b) {
                        return 0;
                    }
                    if (a == null) {
                        return 1;
                    }
                    if (b == null) {
                        return -1;
                    }
                    if (a.timestamp < b.timestamp) {
                        return 1;
                    }
                    if (a.timestamp > b.timestamp) {
                        return -1;
                    }
                    String aKey = a.groupId == null ? "" : a.groupId;
                    String bKey = b.groupId == null ? "" : b.groupId;
                    int keyCmp = aKey.compareTo(bKey);
                    if (keyCmp != 0) {
                        return keyCmp;
                    }
                    String aName = a.groupName == null ? "" : a.groupName;
                    String bName = b.groupName == null ? "" : b.groupName;
                    return aName.compareTo(bName);
                }
            });
            return chats;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static RecentGroup getGroup(Context context, String groupId) {
        if (context == null || groupId == null || groupId.isEmpty()) {
            return null;
        }
        List<RecentGroup> groups = getRecentGroups(context);
        for (RecentGroup g : groups) {
            if (groupId.equals(g.groupId)) {
                return g;
            }
        }
        return null;
    }

    public static void clearAll(Context context) {
        if (context == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    public static void updateGroupIncoming(Context context, String groupId, String groupName, String avatarUrl,
                                           String lastMessage, long timestampSeconds, int unreadDelta) {
        updateInternal(context, groupId, groupName, avatarUrl, lastMessage,
                normalizeTimestamp(timestampSeconds), unreadDelta, false, -1);
    }

    public static void setUnreadCount(Context context, String groupId, String groupName, String avatarUrl,
                                      String lastMessage, long timestampSeconds, int unreadCount) {
        updateInternalWithUnread(context, groupId, groupName, avatarUrl, lastMessage,
                normalizeTimestamp(timestampSeconds), unreadCount, -1);
    }

    public static void updateGroupOutgoing(Context context, String groupId, String groupName, String avatarUrl,
                                           String lastMessage, long timestampSeconds) {
        updateInternal(context, groupId, groupName, avatarUrl, lastMessage,
                normalizeTimestamp(timestampSeconds), 0, false, -1);
    }

    public static void touchGroup(Context context, String groupId, String groupName, String avatarUrl, int role) {
        updateInternal(context, groupId, groupName, avatarUrl, null,
                System.currentTimeMillis(), 0, false, role);
    }

    public static void clearUnread(Context context, String groupId) {
        updateInternal(context, groupId, null, null, null, 0, 0, true, -1);
        setMentionUnread(context, groupId, false);
    }

    public static void setMentionUnread(Context context, String groupId, boolean mentionUnread) {
        if (context == null || groupId == null || groupId.isEmpty()) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        List<RecentGroup> list = getRecentGroups(context);
        boolean changed = false;
        for (RecentGroup group : list) {
            if (groupId.equals(group.groupId)) {
                if (group.mentionUnread != mentionUnread) {
                    group.mentionUnread = mentionUnread;
                    changed = true;
                }
                break;
            }
        }
        if (changed) {
            String json = new Gson().toJson(list);
            prefs.edit().putString("list", json).apply();
        }
    }

    public static void updateMemberCount(Context context, String groupId, int memberCount) {
        if (context == null || groupId == null || groupId.isEmpty()) {
            return;
        }
        if (memberCount <= 0) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        List<RecentGroup> list = getRecentGroups(context);
        boolean changed = false;
        for (RecentGroup group : list) {
            if (groupId.equals(group.groupId)) {
                if (group.memberCount != memberCount) {
                    group.memberCount = memberCount;
                    changed = true;
                }
                break;
            }
        }
        if (changed) {
            String json = new Gson().toJson(list);
            prefs.edit().putString("list", json).apply();
        }
    }

    public static void updateName(Context context, String groupId, String name) {
        if (context == null || groupId == null || groupId.isEmpty()) {
            return;
        }
        String newName = name == null ? "" : name.trim();
        if (newName.length() == 0) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        List<RecentGroup> list = getRecentGroups(context);
        boolean changed = false;
        for (RecentGroup group : list) {
            if (groupId.equals(group.groupId)) {
                if (group.groupName == null || !newName.equals(group.groupName)) {
                    group.groupName = newName;
                    changed = true;
                }
                break;
            }
        }
        if (changed) {
            String json = new Gson().toJson(list);
            prefs.edit().putString("list", json).apply();
        }
    }

    public static void mergeGroupInfo(Context context, List<Group> groups) {
        if (context == null || groups == null || groups.isEmpty()) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        List<RecentGroup> list = getRecentGroups(context);
        boolean changed = false;
        for (Group g : groups) {
            if (g.id == null || g.id.isEmpty()) {
                continue;
            }
            RecentGroup existing = null;
            for (RecentGroup c : list) {
                if (g.id.equals(c.groupId)) {
                    existing = c;
                    break;
                }
            }
            if (existing == null) {
                int memberCount = g.member_count > 0 ? g.member_count : -1;
                list.add(new RecentGroup(g.id, g.name, g.avatar_url, "",
                        0, 0, g.role, false, memberCount));
                changed = true;
                continue;
            }
            if (g.name != null && !g.name.isEmpty() && !g.name.equals(existing.groupName)) {
                existing.groupName = g.name;
                changed = true;
            }
            if (g.avatar_url != null && !g.avatar_url.isEmpty() && !g.avatar_url.equals(existing.avatarUrl)) {
                existing.avatarUrl = g.avatar_url;
                changed = true;
            }
            if (existing.role != g.role) {
                existing.role = g.role;
                changed = true;
            }
            if (g.member_count > 0 && existing.memberCount != g.member_count) {
                existing.memberCount = g.member_count;
                changed = true;
            }
        }
        if (changed) {
            String json = new Gson().toJson(list);
            prefs.edit().putString("list", json).apply();
        }
    }

    public static void remove(Context context, String groupId) {
        if (context == null || groupId == null || groupId.isEmpty()) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        List<RecentGroup> list = getRecentGroups(context);
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            RecentGroup g = list.get(i);
            if (groupId.equals(g.groupId)) {
                list.remove(i);
                changed = true;
            }
        }
        if (changed) {
            String json = new Gson().toJson(list);
            prefs.edit().putString("list", json).apply();
        }
    }

    private static void updateInternal(Context context, String groupId, String groupName, String avatarUrl,
                                       String lastMessage, long timestamp, int unreadDelta, boolean clearUnread, int role) {
        if (context == null || groupId == null || groupId.isEmpty()) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        List<RecentGroup> list = getRecentGroups(context);

        RecentGroup existing = null;
        for (RecentGroup c : list) {
            if (groupId.equals(c.groupId)) {
                existing = c;
                break;
            }
        }
        if (existing != null) {
            list.remove(existing);
        }

        String finalName = groupName;
        String finalAvatar = avatarUrl;
        int unread = existing != null ? existing.unreadCount : 0;
        int finalRole = existing != null ? existing.role : 0;
        boolean mentionUnread = existing != null && existing.mentionUnread;
        int finalMemberCount = existing != null ? existing.memberCount : -1;
        if (existing != null) {
            if (finalName == null || finalName.isEmpty()) {
                finalName = existing.groupName;
            }
            if (finalAvatar == null || finalAvatar.isEmpty()) {
                finalAvatar = existing.avatarUrl;
            }
        }
        if (role >= 0) {
            finalRole = role;
        }
        if (finalName == null || finalName.isEmpty()) {
            finalName = groupId;
        }
        if (clearUnread) {
            unread = 0;
        } else if (unreadDelta > 0) {
            unread += unreadDelta;
        }

        long ts = timestamp > 0 ? timestamp : (existing != null ? existing.timestamp : System.currentTimeMillis());
        String finalMessage = lastMessage != null ? lastMessage : (existing != null ? existing.lastMessage : "");
        list.add(0, new RecentGroup(groupId, finalName, finalAvatar, finalMessage, ts, unread, finalRole,
                mentionUnread, finalMemberCount));

        String json = new Gson().toJson(list);
        prefs.edit().putString("list", json).apply();
    }

    private static void updateInternalWithUnread(Context context, String groupId, String groupName, String avatarUrl,
                                                 String lastMessage, long timestamp, int unreadCount, int role) {
        if (context == null || groupId == null || groupId.isEmpty()) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        List<RecentGroup> list = getRecentGroups(context);

        RecentGroup existing = null;
        for (RecentGroup c : list) {
            if (groupId.equals(c.groupId)) {
                existing = c;
                break;
            }
        }
        if (existing != null) {
            list.remove(existing);
        }

        String finalName = groupName;
        String finalAvatar = avatarUrl;
        int finalRole = existing != null ? existing.role : 0;
        boolean mentionUnread = existing != null && existing.mentionUnread;
        int finalMemberCount = existing != null ? existing.memberCount : -1;
        if (existing != null) {
            if (finalName == null || finalName.isEmpty()) {
                finalName = existing.groupName;
            }
            if (finalAvatar == null || finalAvatar.isEmpty()) {
                finalAvatar = existing.avatarUrl;
            }
        }
        if (role >= 0) {
            finalRole = role;
        }
        if (finalName == null || finalName.isEmpty()) {
            finalName = groupId;
        }

        long ts = timestamp > 0 ? timestamp : (existing != null ? existing.timestamp : System.currentTimeMillis());
        String finalMessage = lastMessage != null ? lastMessage : (existing != null ? existing.lastMessage : "");
        int finalUnread = unreadCount < 0 ? 0 : unreadCount;
        list.add(0, new RecentGroup(groupId, finalName, finalAvatar, finalMessage, ts, finalUnread, finalRole,
                mentionUnread, finalMemberCount));

        String json = new Gson().toJson(list);
        prefs.edit().putString("list", json).apply();
    }

    private static long normalizeTimestamp(long value) {
        if (value <= 0) {
            return System.currentTimeMillis();
        }
        if (value < 100000000000L) {
            return value * 1000L;
        }
        return value;
    }
}
