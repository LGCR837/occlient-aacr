package aoharureverie.ocaacrclient.oldchat.ui.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import aoharureverie.ocaacrclient.oldchat.models.Group;
import aoharureverie.ocaacrclient.oldchat.models.GroupCache;
import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.models.GroupRecentChatCache;
import aoharureverie.ocaacrclient.oldchat.models.FriendNameResolver;
import aoharureverie.ocaacrclient.oldchat.models.FriendCache;
import aoharureverie.ocaacrclient.oldchat.models.Message;
import aoharureverie.ocaacrclient.oldchat.models.MessageHistoryCache;
import aoharureverie.ocaacrclient.oldchat.models.RecentChatCache;
import aoharureverie.ocaacrclient.oldchat.models.User;
import aoharureverie.ocaacrclient.oldchat.models.UserNameCache;
import aoharureverie.ocaacrclient.oldchat.models.UserTitleCache;
import aoharureverie.ocaacrclient.oldchat.ui.ChatMessageUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecentChatBuilder {
    public static List<RecentItem> buildCombinedList(Context ctx, int unreadNotificationCount) {
        List<RecentItem> items = new ArrayList<>();
        if (ctx == null) {
            return items;
        }

        RecentItem sysNotif = new RecentItem();
        sysNotif.isSystemNotification = true;
        sysNotif.id = "SYSTEM_NOTIFICATION";
        sysNotif.title = "系统通知";
        sysNotif.subtitle = "官方公告和通知";
        sysNotif.timestamp = System.currentTimeMillis();
        sysNotif.unreadCount = unreadNotificationCount;

        Map<String, RecentItem> map = new HashMap<>();

        List<User> friends = FriendCache.getFriends(ctx);
        Map<String, User> friendByUid = new HashMap<>();
        Set<String> friendUids = new HashSet<>();
        Set<String> friendIds = new HashSet<>();
        for (User u : friends) {
            if (u == null) {
                continue;
            }
            if (u.uid != null && !u.uid.isEmpty()) {
                friendByUid.put(u.uid, u);
                friendUids.add(u.uid);
            }
            if (u.id != null && !u.id.isEmpty()) {
                friendIds.add(u.id);
            }
            if (u.uid == null || u.uid.isEmpty()) {
                continue;
            }
            List<Message> history = MessageHistoryCache.getDirectMessages(ctx, u.uid);
            if (history.isEmpty()) {
                continue;
            }
            Message last = history.get(history.size() - 1);
            RecentItem item = new RecentItem();
            item.isGroup = false;
            item.id = u.uid;
            item.title = FriendNameResolver.resolve(u);
            item.userTitle = u.user_title;
            item.subtitle = ChatMessageUtil.previewForMessage(last);
            item.avatarUrl = u.avatar_url;
            item.timestamp = ChatMessageUtil.normalizeTimestamp(last.created_at);
            item.unreadCount = 0;
            map.put("u:" + u.uid, item);
        }

        List<RecentChatCache.RecentChat> direct = RecentChatCache.getRecentChats(ctx);
        for (RecentChatCache.RecentChat chat : direct) {
            String id = chat.friendUID != null && !chat.friendUID.isEmpty() ? chat.friendUID : chat.friendId;
            if (id == null || id.isEmpty()) {
                continue;
            }
            if (!isKnownFriend(id, friendUids, friendIds)) {
                continue;
            }
            String key = "u:" + id;
            RecentItem item = map.get(key);
            if (item == null) {
                item = new RecentItem();
                item.isGroup = false;
                item.id = id;
                item.timestamp = 0;
                map.put(key, item);
            }
            if (item.title == null || item.title.isEmpty()) {
                item.title = chat.friendName != null && !chat.friendName.isEmpty() ? chat.friendName : id;
            }
            if (item.userTitle == null || item.userTitle.isEmpty()) {
                User u = friendByUid.get(id);
                if (u != null && u.user_title != null && !u.user_title.isEmpty()) {
                    item.userTitle = u.user_title;
                } else {
                    item.userTitle = UserTitleCache.getTitle(ctx, id);
                }
            }
            if (item.avatarUrl == null || item.avatarUrl.isEmpty()) {
                item.avatarUrl = chat.avatarUrl;
            }
            if (chat.timestamp > item.timestamp) {
                item.timestamp = chat.timestamp;
                item.subtitle = chat.lastMessage;
            } else if (item.subtitle == null || item.subtitle.isEmpty()) {
                item.subtitle = chat.lastMessage;
            }
            if (chat.unreadCount > item.unreadCount) {
                item.unreadCount = chat.unreadCount;
            }
        }

        List<Group> groupList = GroupCache.getGroups(ctx);
        Map<String, Group> groupById = new HashMap<>();
        for (Group g : groupList) {
            if (g == null || g.id == null || g.id.isEmpty()) {
                continue;
            }
            groupById.put(g.id, g);
            List<GroupMessage> history = MessageHistoryCache.getGroupMessages(ctx, g.id);
            if (history.isEmpty()) {
                continue;
            }
            GroupMessage last = history.get(history.size() - 1);
            RecentItem item = new RecentItem();
            item.isGroup = true;
            item.id = g.id;
            item.title = g.name != null && !g.name.isEmpty() ? g.name : g.id;
            item.subtitle = ChatMessageUtil.previewForType(last.msg_type, last.body);
            item.avatarUrl = g.avatar_url;
            item.timestamp = ChatMessageUtil.normalizeTimestamp(last.created_at);
            item.unreadCount = 0;
            item.groupRole = g.role;
            item.mentionUnread = false;
            item.groupMemberCount = g.member_count;
            map.put("g:" + g.id, item);
        }

        List<GroupRecentChatCache.RecentGroup> groups = GroupRecentChatCache.getRecentGroups(ctx);
        for (GroupRecentChatCache.RecentGroup g : groups) {
            if (g.groupId == null || g.groupId.isEmpty()) {
                continue;
            }
            if (!groupById.containsKey(g.groupId)) {
                continue;
            }
            String key = "g:" + g.groupId;
            RecentItem item = map.get(key);
            if (item == null) {
                item = new RecentItem();
                item.isGroup = true;
                item.id = g.groupId;
                item.timestamp = 0;
                map.put(key, item);
            }
            if (item.title == null || item.title.isEmpty()) {
                item.title = g.groupName != null && !g.groupName.isEmpty() ? g.groupName : g.groupId;
            }
            if (item.avatarUrl == null || item.avatarUrl.isEmpty()) {
                item.avatarUrl = g.avatarUrl;
            }
            if (item.groupMemberCount <= 0 && g.memberCount > 0) {
                item.groupMemberCount = g.memberCount;
            }
            if (g.timestamp > item.timestamp) {
                item.timestamp = g.timestamp;
                item.subtitle = g.lastMessage;
            } else if (item.subtitle == null || item.subtitle.isEmpty()) {
                item.subtitle = g.lastMessage;
            }
            if (g.unreadCount > item.unreadCount) {
                item.unreadCount = g.unreadCount;
            }
            item.groupRole = g.role;
            item.mentionUnread = g.mentionUnread;
        }

        List<String> directIds = MessageHistoryCache.getDirectChatIds(ctx);
        for (String uid : directIds) {
            if (uid == null || uid.isEmpty()) {
                continue;
            }
            if (!isKnownFriend(uid, friendUids, friendIds)) {
                continue;
            }
            String key = "u:" + uid;
            if (map.containsKey(key)) {
                continue;
            }
            List<Message> history = MessageHistoryCache.getDirectMessages(ctx, uid);
            if (history.isEmpty()) {
                continue;
            }
            Message last = history.get(history.size() - 1);
            RecentItem item = new RecentItem();
            item.isGroup = false;
            item.id = uid;
            User u = friendByUid.get(uid);
            if (u != null) {
                item.title = FriendNameResolver.resolve(u);
                item.avatarUrl = u.avatar_url;
            }
            if (item.title == null || item.title.isEmpty()) {
                String cachedName = UserNameCache.getName(ctx, uid);
                item.title = cachedName != null && !cachedName.isEmpty() ? cachedName : uid;
            }
            item.subtitle = ChatMessageUtil.previewForMessage(last);
            item.timestamp = ChatMessageUtil.normalizeTimestamp(last.created_at);
            item.unreadCount = 0;
            map.put(key, item);
        }

        List<String> groupIds = MessageHistoryCache.getGroupChatIds(ctx);
        for (String groupId : groupIds) {
            if (groupId == null || groupId.isEmpty()) {
                continue;
            }
            if (!groupById.containsKey(groupId)) {
                continue;
            }
            String key = "g:" + groupId;
            if (map.containsKey(key)) {
                continue;
            }
            List<GroupMessage> history = MessageHistoryCache.getGroupMessages(ctx, groupId);
            if (history.isEmpty()) {
                continue;
            }
            GroupMessage last = history.get(history.size() - 1);
            RecentItem item = new RecentItem();
            item.isGroup = true;
            item.id = groupId;
            Group g = groupById.get(groupId);
            if (g != null) {
                item.title = g.name;
                item.avatarUrl = g.avatar_url;
                item.groupRole = g.role;
                item.groupMemberCount = g.member_count;
            }
            if (item.title == null || item.title.isEmpty()) {
                item.title = groupId;
            }
            item.subtitle = ChatMessageUtil.previewForType(last.msg_type, last.body);
            item.timestamp = ChatMessageUtil.normalizeTimestamp(last.created_at);
            item.unreadCount = 0;
            map.put(key, item);
        }

        String myUid = getMyUid(ctx);
        if (myUid != null && !myUid.isEmpty()) {
            for (RecentItem item : map.values()) {
                if (item == null || item.id == null || item.id.isEmpty()) {
                    continue;
                }
                String draft = loadDraft(ctx, myUid, item.isGroup, item.id);
                if (draft != null && draft.trim().length() > 0) {
                    item.draftText = draft;
                }
            }
        }

        for (RecentItem item : map.values()) {
            if (item == null) {
                continue;
            }
            item.pinned = RecentPinStore.isPinned(ctx, item.isGroup, item.id);
            item.folded = RecentFoldStore.isFolded(ctx, item.isGroup, item.id);
        }

        items.add(sysNotif);
        items.addAll(map.values());
        Collections.sort(items, new java.util.Comparator<RecentItem>() {
            @Override
            public int compare(RecentItem a, RecentItem b) {
                if (a == b) {
                    return 0;
                }
                if (a == null) {
                    return 1;
                }
                if (b == null) {
                    return -1;
                }
                if (a.isSystemNotification != b.isSystemNotification) {
                    return a.isSystemNotification ? -1 : 1;
                }
                if (a.pinned != b.pinned) {
                    return a.pinned ? -1 : 1;
                }
                if (a.timestamp < b.timestamp) {
                    return 1;
                }
                if (a.timestamp > b.timestamp) {
                    return -1;
                }
                int groupCmp = (a.isGroup ? 1 : 0) - (b.isGroup ? 1 : 0);
                if (groupCmp != 0) {
                    return groupCmp;
                }
                String aId = a.id == null ? "" : a.id;
                String bId = b.id == null ? "" : b.id;
                int idCmp = aId.compareTo(bId);
                if (idCmp != 0) {
                    return idCmp;
                }
                String aTitle = a.title == null ? "" : a.title;
                String bTitle = b.title == null ? "" : b.title;
                return aTitle.compareTo(bTitle);
            }
        });
        return items;
    }

    private static boolean isKnownFriend(String uid, Set<String> friendUids, Set<String> friendIds) {
        if (uid == null || uid.isEmpty()) {
            return false;
        }
        return friendUids.contains(uid) || friendIds.contains(uid);
    }

    private static String getMyUid(Context ctx) {
        if (ctx == null) {
            return "";
        }
        SharedPreferences prefs = ctx.getSharedPreferences("auth", Context.MODE_PRIVATE);
        return prefs.getString("my_uid", "");
    }

    private static String loadDraft(Context ctx, String myUid, boolean isGroup, String id) {
        if (ctx == null || myUid == null || myUid.isEmpty() || id == null || id.isEmpty()) {
            return "";
        }
        SharedPreferences prefs = ctx.getSharedPreferences("drafts", Context.MODE_PRIVATE);
        String key = (isGroup ? "group:" : "direct:") + myUid + ":" + id;
        return prefs.getString(key, "");
    }
}
