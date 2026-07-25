package aoharureverie.ocaacrclient.oldchat.models;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecentChatCache {
    private static final String PREF_NAME = "recent_chats";
    
    public static class RecentChat {
        public String friendId;
        public String friendUID;
        public String friendName;
        public String avatarUrl;
        public String lastMessage;
        public long timestamp;
        public int unreadCount;

        public RecentChat(String friendId, String friendUID, String friendName, String avatarUrl, String lastMessage, long timestamp, int unreadCount) {
            this.friendId = friendId;
            this.friendUID = friendUID;
            this.friendName = friendName;
            this.avatarUrl = avatarUrl;
            this.lastMessage = lastMessage;
            this.timestamp = timestamp;
            this.unreadCount = unreadCount;
        }
    }

    public static void updateRecentChat(Context context, String friendUID, String friendName, String avatarUrl, String lastMessage) {
        updateRecentChatInternal(context, friendUID, friendName, avatarUrl, lastMessage,
                System.currentTimeMillis(), 0, false);
    }

    public static void touchRecentChat(Context context, String friendUID, String friendName, String avatarUrl) {
        updateRecentChatInternal(context, friendUID, friendName, avatarUrl, null,
                System.currentTimeMillis(), 0, false);
    }

    public static void updateRecentChatIncoming(Context context, String friendUID, String friendName, String avatarUrl,
                                                String lastMessage, long timestampSeconds, int unreadDelta) {
        updateRecentChatInternal(context, friendUID, friendName, avatarUrl, lastMessage,
                normalizeTimestamp(timestampSeconds), unreadDelta, false);
    }

    public static void updateRecentChatOutgoing(Context context, String friendUID, String friendName, String avatarUrl,
                                                String lastMessage, long timestampSeconds) {
        updateRecentChatInternal(context, friendUID, friendName, avatarUrl, lastMessage,
                normalizeTimestamp(timestampSeconds), 0, false);
    }

    public static void clearUnread(Context context, String friendUID) {
        updateRecentChatInternal(context, friendUID, null, null, null, 0, 0, true);
    }

    public static void setUnreadCount(Context context, String friendUID, String friendName, String avatarUrl,
                                      String lastMessage, long timestampSeconds, int unreadCount) {
        updateRecentChatWithUnread(context, friendUID, friendName, avatarUrl, lastMessage,
                normalizeTimestamp(timestampSeconds), unreadCount);
    }

    public static void mergeFriendInfo(Context context, List<User> friends) {
        if (context == null || friends == null || friends.isEmpty()) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        List<RecentChat> list = getRecentChats(context);
        boolean changed = false;
        for (RecentChat c : list) {
            if (c.friendUID == null || c.friendUID.isEmpty()) {
                continue;
            }
            for (User u : friends) {
                if (u.uid != null && u.uid.equals(c.friendUID)) {
                    String name = FriendNameResolver.resolve(u);
                    if (name != null && !name.isEmpty() && !name.equals(c.friendName)) {
                        c.friendName = name;
                        changed = true;
                    }
                    if (u.avatar_url != null && !u.avatar_url.isEmpty() && !u.avatar_url.equals(c.avatarUrl)) {
                        c.avatarUrl = u.avatar_url;
                        changed = true;
                    }
                    break;
                }
            }
        }
        if (changed) {
            String json = new Gson().toJson(list);
            prefs.edit().putString("list", json).apply();
        }
    }

    public static void cleanupInvalidChats(Context context, List<User> friends) {
        if (context == null || friends == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        List<RecentChat> list = getRecentChats(context);
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            RecentChat c = list.get(i);
            if (c.friendUID == null || c.friendUID.isEmpty()) {
                continue;
            }
            boolean found = false;
            for (User u : friends) {
                if (u.uid != null && u.uid.equals(c.friendUID)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                list.remove(i);
                changed = true;
            }
        }
        if (changed) {
            String json = new Gson().toJson(list);
            prefs.edit().putString("list", json).apply();
        }
    }

    public static List<RecentChat> getRecentChats(Context context) {
        if (context == null) {
            return new ArrayList<>();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString("list", null);
        if (json == null) return new ArrayList<>();
        try {
            List<RecentChat> chats = new Gson().fromJson(json,
                    TypeToken.getParameterized(List.class, RecentChat.class).getType());
            if (chats == null) {
                return new ArrayList<>();
            }
            Collections.sort(chats, new java.util.Comparator<RecentChat>() {
                @Override
                public int compare(RecentChat a, RecentChat b) {
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
                    String aKey = a.friendUID != null && a.friendUID.length() > 0 ? a.friendUID : (a.friendId == null ? "" : a.friendId);
                    String bKey = b.friendUID != null && b.friendUID.length() > 0 ? b.friendUID : (b.friendId == null ? "" : b.friendId);
                    int keyCmp = aKey.compareTo(bKey);
                    if (keyCmp != 0) {
                        return keyCmp;
                    }
                    String aName = a.friendName == null ? "" : a.friendName;
                    String bName = b.friendName == null ? "" : b.friendName;
                    return aName.compareTo(bName);
                }
            });
            return chats;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static RecentChat getChat(Context context, String friendUID) {
        if (context == null || friendUID == null || friendUID.isEmpty()) {
            return null;
        }
        List<RecentChat> chats = getRecentChats(context);
        for (RecentChat c : chats) {
            String key = c.friendUID != null ? c.friendUID : c.friendId;
            if (key != null && key.equals(friendUID)) {
                return c;
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

    public static void remove(Context context, String friendUID) {
        removeChat(context, friendUID);
    }

    public static void removeChat(Context context, String friendUID) {
        if (context == null || friendUID == null || friendUID.isEmpty()) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        List<RecentChat> list = getRecentChats(context);
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            RecentChat c = list.get(i);
            String key = c.friendUID != null ? c.friendUID : c.friendId;
            if (key != null && key.equals(friendUID)) {
                list.remove(i);
                changed = true;
            }
        }
        if (changed) {
            String json = new Gson().toJson(list);
            prefs.edit().putString("list", json).apply();
        }
    }

    private static void updateRecentChatInternal(Context context, String friendUID, String friendName, String avatarUrl,
                                                 String lastMessage, long timestamp, int unreadDelta, boolean clearUnread) {
        if (context == null || friendUID == null || friendUID.isEmpty()) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        List<RecentChat> list = getRecentChats(context);

        RecentChat existing = null;
        for (RecentChat c : list) {
            String key = c.friendUID != null ? c.friendUID : c.friendId;
            if (key != null && key.equals(friendUID)) {
                existing = c;
                break;
            }
        }
        if (existing != null) {
            list.remove(existing);
        }

        String finalName = friendName;
        String finalAvatar = avatarUrl;
        int unread = existing != null ? existing.unreadCount : 0;
        if (existing != null) {
            if (finalName == null || finalName.isEmpty()) {
                finalName = existing.friendName;
            }
            if (finalAvatar == null || finalAvatar.isEmpty()) {
                finalAvatar = existing.avatarUrl;
            }
        }
        if (finalName == null || finalName.isEmpty()) {
            finalName = friendUID;
        }
        if (clearUnread) {
            unread = 0;
        } else if (unreadDelta > 0) {
            unread += unreadDelta;
        }

        long ts = timestamp > 0 ? timestamp : (existing != null ? existing.timestamp : System.currentTimeMillis());
        String finalMessage = lastMessage != null ? lastMessage : (existing != null ? existing.lastMessage : "");
        list.add(0, new RecentChat(friendUID, friendUID, finalName, finalAvatar, finalMessage, ts, unread));

        String json = new Gson().toJson(list);
        prefs.edit().putString("list", json).apply();
    }

    private static void updateRecentChatWithUnread(Context context, String friendUID, String friendName, String avatarUrl,
                                                   String lastMessage, long timestamp, int unreadCount) {
        if (context == null || friendUID == null || friendUID.isEmpty()) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        List<RecentChat> list = getRecentChats(context);

        RecentChat existing = null;
        for (RecentChat c : list) {
            String key = c.friendUID != null ? c.friendUID : c.friendId;
            if (key != null && key.equals(friendUID)) {
                existing = c;
                break;
            }
        }
        if (existing != null) {
            list.remove(existing);
        }

        String finalName = friendName;
        String finalAvatar = avatarUrl;
        if (existing != null) {
            if (finalName == null || finalName.isEmpty()) {
                finalName = existing.friendName;
            }
            if (finalAvatar == null || finalAvatar.isEmpty()) {
                finalAvatar = existing.avatarUrl;
            }
        }
        if (finalName == null || finalName.isEmpty()) {
            finalName = friendUID;
        }

        long ts = timestamp > 0 ? timestamp : (existing != null ? existing.timestamp : System.currentTimeMillis());
        String finalMessage = lastMessage != null ? lastMessage : (existing != null ? existing.lastMessage : "");
        int finalUnread = unreadCount < 0 ? 0 : unreadCount;
        list.add(0, new RecentChat(friendUID, friendUID, finalName, finalAvatar, finalMessage, ts, finalUnread));

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
