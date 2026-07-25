package aoharureverie.ocaacrclient.oldchat.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.support.v7.widget.PopupMenu;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.api.WSManager;
import aoharureverie.ocaacrclient.oldchat.api.WSModels;
import aoharureverie.ocaacrclient.oldchat.models.FriendCache;
import aoharureverie.ocaacrclient.oldchat.models.FriendNameResolver;
import aoharureverie.ocaacrclient.oldchat.models.Group;
import aoharureverie.ocaacrclient.oldchat.models.GroupCache;
import aoharureverie.ocaacrclient.oldchat.models.GroupRecentChatCache;
import aoharureverie.ocaacrclient.oldchat.models.RecentChatCache;
import aoharureverie.ocaacrclient.oldchat.models.User;
import aoharureverie.ocaacrclient.oldchat.models.UserNameCache;
import aoharureverie.ocaacrclient.oldchat.ui.ChatActivity;
import aoharureverie.ocaacrclient.oldchat.ui.GroupCreateActivity;
import aoharureverie.ocaacrclient.oldchat.ui.GroupChatActivity;
import aoharureverie.ocaacrclient.oldchat.ui.AddFriendActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatsFragment extends Fragment {
    private static final String AUTH_PREFS = "auth";
    private static final long RECENT_LOAD_DEBOUNCE_MS = 220L;
    private static final String FOLDED_FOLDER_ID = "__FOLDED_FOLDER__";
    private RecyclerView rvRecentChats;
    private TextView tvHeaderTitle;
    private View btnHeaderAdd;
    private ProgressBar pbHomeLoading;
    private CombinedChatAdapter adapter;
    private String token;
    private boolean wsConnected = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService recentLoadExecutor = Executors.newSingleThreadExecutor();
    private Runnable pendingRecentLoad;
    private int recentLoadSeq = 0;
    private final RecentAvatarTracker avatarTracker = new RecentAvatarTracker();
    private final List<RecentItem> latestRawItems = new ArrayList<RecentItem>();
    private boolean showFoldedChats;
    private final WSManager.Listener wsListener = new WSManager.Listener() {
        @Override
        public void onDirectMessage(WSModels.DirectMessage message) {
            postLoadRecentChats();
        }

        @Override
        public void onDirectRead(String threadId, String readerUid, long readAt) {
        }

        @Override
        public void onDirectRecall(WSModels.DirectRecall recall) {
            postLoadRecentChats();
        }

        @Override
        public void onGroupMessage(WSModels.GroupMessage message) {
            postLoadRecentChats();
        }

        @Override
        public void onGroupRecall(WSModels.GroupRecall recall) {
            postLoadRecentChats();
        }

        @Override
        public void onTyping(WSModels.TypingEvent event) {
        }

        @Override
        public void onConnectionChanged(boolean connected) {
            wsConnected = connected;
            updateLoadingState();
            if (connected) {
                syncUnread();
            }
        }
    };

    private void postLoadRecentChats() {
        scheduleLoadRecentChats(RECENT_LOAD_DEBOUNCE_MS);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chats, container, false);
        rvRecentChats = view.findViewById(R.id.rvRecentChats);
        tvHeaderTitle = view.findViewById(R.id.tvChatHeaderTitle);
        btnHeaderAdd = view.findViewById(R.id.btnChatHeaderAdd);
        pbHomeLoading = view.findViewById(R.id.pbHomeLoading);
        rvRecentChats.setLayoutManager(new LinearLayoutManager(getContext()));
        rvRecentChats.setItemAnimator(null);
        rvRecentChats.setOverScrollMode(View.OVER_SCROLL_NEVER);
        if (adapter != null) {
            rvRecentChats.setAdapter(adapter);
        }
        if (btnHeaderAdd != null) {
            btnHeaderAdd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showQuickMenu(v);
                }
            });
        }
        View btnSwitchAccount = view.findViewById(R.id.btnSwitchAccount);
        if (btnSwitchAccount != null) {
            btnSwitchAccount.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(getActivity(), aoharureverie.ocaacrclient.oldchat.ui.AccountListActivity.class));
                }
            });
        }
        updateLoadingState();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshRecents();
        wsConnected = WSManager.getInstance().isConnected();
        updateLoadingState();
        WSManager.getInstance().addListener(wsListener);
        syncUnread();
    }

    @Override
    public void onPause() {
        super.onPause();
        WSManager.getInstance().removeListener(wsListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (pendingRecentLoad != null) {
            mainHandler.removeCallbacks(pendingRecentLoad);
            pendingRecentLoad = null;
        }
        recentLoadExecutor.shutdownNow();
    }

    private void scheduleLoadRecentChats(long delayMs) {
        if (pendingRecentLoad != null) {
            mainHandler.removeCallbacks(pendingRecentLoad);
        }
        pendingRecentLoad = new Runnable() {
            @Override
            public void run() {
                pendingRecentLoad = null;
                loadRecentChatsAsync();
            }
        };
        if (delayMs <= 0) {
            mainHandler.post(pendingRecentLoad);
        } else {
            mainHandler.postDelayed(pendingRecentLoad, delayMs);
        }
    }

    private void loadRecentChatsAsync() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        final Context appContext = getActivity().getApplicationContext();
        final int unreadNotificationCount = getUnreadNotificationCount();
        final int requestSeq = ++recentLoadSeq;
        try {
            recentLoadExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final List<RecentItem> items = RecentChatBuilder.buildCombinedList(appContext, unreadNotificationCount);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!isAdded() || getActivity() == null) {
                                return;
                            }
                            if (requestSeq != recentLoadSeq) {
                                return;
                            }
                            applyRecentChats(items);
                        }
                    });
                }
            });
        } catch (RuntimeException ignored) {
        }
    }

    private void applyRecentChats(List<RecentItem> items) {
        if (items == null) {
            items = new ArrayList<RecentItem>();
        }
        latestRawItems.clear();
        latestRawItems.addAll(items);

        List<RecentItem> displayItems = buildDisplayRecentItems(items);
        avatarTracker.reset(displayItems);
        updateLoadingState();
        if (adapter == null) {
            adapter = new CombinedChatAdapter(avatarTracker, new CombinedChatAdapter.Listener() {
                @Override
                public void onItemClick(RecentItem item) {
                    if (item != null && item.isFoldedFolder) {
                        toggleFoldedFolder();
                        return;
                    }
                    if (item.isSystemNotification) {
                        // 打开系统通知界面
                        startActivity(new Intent(getActivity(), aoharureverie.ocaacrclient.oldchat.ui.NotificationChatActivity.class));
                    } else if (item.isGroup) {
                        Intent intent = new Intent(getActivity(), GroupChatActivity.class);
                        intent.putExtra("group_id", item.id);
                        intent.putExtra("group_name", item.title);
                        intent.putExtra("group_avatar", item.avatarUrl);
                        intent.putExtra("group_role", item.groupRole);
                        intent.putExtra("unread_count", item.unreadCount);
                        startActivity(intent);
                    } else {
                        Intent intent = new Intent(getActivity(), ChatActivity.class);
                        intent.putExtra("friend_uid", item.id);
                        intent.putExtra("friend_name", item.title);
                        intent.putExtra("friend_avatar", item.avatarUrl);
                        intent.putExtra("unread_count", item.unreadCount);
                        startActivity(intent);
                    }
                }

                @Override
                public void onAvatarClick(RecentItem item) {
                    Intent intent = new Intent(getActivity(), aoharureverie.ocaacrclient.oldchat.ui.UserSpaceActivity.class);
                    intent.putExtra("uid", item.id);
                    startActivity(intent);
                }

                @Override
                public void onItemLongClick(View anchor, final RecentItem item) {
                    if (item != null && item.isFoldedFolder) {
                        return;
                    }
                    showRecentItemMenu(anchor, item);
                }
            });
        }
        adapter.updateItems(displayItems);
        if (rvRecentChats != null && rvRecentChats.getAdapter() != adapter) {
            rvRecentChats.setAdapter(adapter);
        }
        updateHeaderTitle(items);
    }

    private List<RecentItem> buildDisplayRecentItems(List<RecentItem> rawItems) {
        List<RecentItem> out = new ArrayList<RecentItem>();
        if (rawItems == null || rawItems.isEmpty()) {
            return out;
        }
        List<RecentItem> folded = new ArrayList<RecentItem>();
        for (int i = 0; i < rawItems.size(); i++) {
            RecentItem item = rawItems.get(i);
            if (item == null) {
                continue;
            }
            if (!item.isSystemNotification && item.folded) {
                folded.add(item);
            }
        }
        if (folded.isEmpty()) {
            showFoldedChats = false;
        }

        if (folded.isEmpty()) {
            for (int i = 0; i < rawItems.size(); i++) {
                RecentItem item = rawItems.get(i);
                if (item != null && !item.folded) {
                    out.add(item);
                }
            }
            return out;
        }

        boolean folderInserted = false;
        for (int i = 0; i < rawItems.size(); i++) {
            RecentItem item = rawItems.get(i);
            if (item == null) {
                continue;
            }
            if (item.folded) {
                continue;
            }

            boolean isPinnedChat = !item.isSystemNotification && item.pinned;
            if (!folderInserted && !item.isSystemNotification && !isPinnedChat) {
                out.add(buildFoldedFolderItem(folded));
                folderInserted = true;
                if (showFoldedChats) {
                    out.addAll(folded);
                }
            }
            out.add(item);
        }

        if (!folderInserted) {
            out.add(buildFoldedFolderItem(folded));
            folderInserted = true;
            if (showFoldedChats) {
                out.addAll(folded);
            }
        }
        return out;
    }

    private RecentItem buildFoldedFolderItem(List<RecentItem> foldedItems) {
        RecentItem folder = new RecentItem();
        folder.id = FOLDED_FOLDER_ID;
        folder.isFoldedFolder = true;
        folder.title = "折叠的聊天";
        folder.userTitle = showFoldedChats ? "已展开" : "已折叠";
        folder.unreadCount = 0;
        folder.timestamp = 0;
        int count = 0;
        if (foldedItems != null) {
            count = foldedItems.size();
            for (int i = 0; i < foldedItems.size(); i++) {
                RecentItem one = foldedItems.get(i);
                if (one == null) {
                    continue;
                }
                folder.unreadCount += Math.max(0, one.unreadCount);
                if (one.timestamp > folder.timestamp) {
                    folder.timestamp = one.timestamp;
                }
            }
        }
        if (folder.unreadCount > 0) {
            folder.subtitle = count + " 个聊天 · 未读 " + folder.unreadCount;
        } else {
            folder.subtitle = count + " 个聊天";
        }
        return folder;
    }

    private void toggleFoldedFolder() {
        showFoldedChats = !showFoldedChats;
        if (latestRawItems.isEmpty()) {
            return;
        }
        List<RecentItem> displayItems = buildDisplayRecentItems(latestRawItems);
        avatarTracker.reset(displayItems);
        if (adapter != null) {
            adapter.updateItems(displayItems);
        }
    }

    public void refreshRecents() {
        if (getActivity() == null) {
            return;
        }
        SharedPreferences prefs = getActivity().getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE);
        token = prefs.getString("access_token", "");
        loadGroups();
        loadFriends();
        scheduleLoadRecentChats(0);
    }

    private void syncUnread() {
        if (getActivity() == null) {
            return;
        }
        if (token == null || token.isEmpty()) {
            SharedPreferences prefs = getActivity().getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE);
            token = prefs.getString("access_token", "");
        }
        if (token == null || token.isEmpty()) {
            return;
        }
        WSManager.getInstance().syncUnread(getActivity(), token);
        WSManager.getInstance().syncGroupUnread(getActivity(), token);
    }

    private void loadGroups() {
        if (token == null || token.isEmpty()) {
            return;
        }
        HttpUtil.get("/groups/list", token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject obj = new JSONObject(response);
                    JSONArray arr = obj.getJSONArray("groups");
                    List<Group> groups = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject gObj = arr.getJSONObject(i);
                        Group g = new Group();
                        g.id = gObj.optString("group_id");
                        g.name = gObj.optString("name");
                        g.avatar_url = gObj.optString("avatar_url");
                        g.join_approval = gObj.optBoolean("join_approval", false);
                        g.global_mute = gObj.optBoolean("global_mute", false);
                        g.role = gObj.optInt("role", 0);
                        g.announcement = gObj.optString("announcement", "");
                        g.announcement_mode = gObj.optInt("announcement_mode", 0);
                        g.announcement_updated_at = gObj.optLong("announcement_updated_at", 0);
                        g.announcement_read_at = gObj.optLong("announcement_read_at", 0);
                        g.member_count = gObj.optInt("member_count", 0);
                        groups.add(g);
                    }
                    GroupRecentChatCache.mergeGroupInfo(getActivity(), groups);
                    GroupCache.saveGroups(getActivity(), groups);
                } catch (Exception e) {
                }
                postLoadRecentChats();
            }

            @Override
            public void onError(int code, String error) {
                if (code == 404) {
                    // 如果列表请求都404了，说明可能有严重问题，或者没群？暂不处理
                }
            }
        });
    }

    private void loadFriends() {
        if (token == null || token.isEmpty()) {
            return;
        }
        HttpUtil.get("/friends", token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject obj = new JSONObject(response);
                    JSONArray arr = obj.getJSONArray("friends");
                    List<User> friends = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject uObj = arr.getJSONObject(i);
                        User u = new User();
                        u.id = uObj.optString("id", uObj.optString("user_id", ""));
                        u.uid = uObj.optString("uid", "");
                        u.username = uObj.optString("username", "");
                        u.display_name = uObj.optString("display_name");
                        u.remark_name = uObj.optString("remark_name", "");
                        u.user_title = uObj.optString("user_title");
                        u.avatar_url = uObj.optString("avatar_url");
                        if (u.uid != null && !u.uid.isEmpty()) {
                            friends.add(u);
                        }
                    }
                    FriendCache.saveFriends(getActivity(), friends);
                    RecentChatCache.mergeFriendInfo(getActivity(), friends);
                    aoharureverie.ocaacrclient.oldchat.models.UserTitleCache.mergeUsers(getActivity(), friends);
                    UserNameCache.mergeUsers(getActivity(), friends);
                } catch (Exception e) {
                }
                postLoadRecentChats();
            }

            @Override
            public void onError(int code, String error) {
            }
        });
    }

    private void deleteGroup(Group g) {
        if (g == null || g.id == null) {
            return;
        }
        GroupRecentChatCache.remove(getActivity(), g.id);
        scheduleLoadRecentChats(0);
    }

    private void updateHeaderTitle(List<RecentItem> items) {
        int unread = 0;
        for (RecentItem item : items) {
            unread += item.unreadCount;
        }
        updateHeaderTitle(unread);
    }

    private void updateHeaderTitle(int unread) {
        if (tvHeaderTitle == null) {
            return;
        }
        if (unread > 0) {
            tvHeaderTitle.setText("旧聊(" + unread + ")");
        } else {
            tvHeaderTitle.setText("旧聊");
        }
    }

    private void updateLoadingState() {
        if (pbHomeLoading == null) {
            return;
        }
        pbHomeLoading.setVisibility(wsConnected ? View.GONE : View.VISIBLE);
    }

    private void showQuickMenu(View anchor) {
        PopupMenu menu = new PopupMenu(getActivity(), anchor);
        menu.getMenu().add(0, 1, 0, "添加联系人");
        menu.getMenu().add(0, 2, 1, "创建群聊");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                if (item.getItemId() == 1) {
                    startActivity(new Intent(getActivity(), AddFriendActivity.class));
                    return true;
                }
                if (item.getItemId() == 2) {
                    startActivity(new Intent(getActivity(), GroupCreateActivity.class));
                    return true;
                }
                return false;
            }
        });
        menu.show();
    }

    private void showRecentItemMenu(View anchor, final RecentItem item) {
        if (item == null || item.isSystemNotification || item.isFoldedFolder
                || item.id == null || item.id.isEmpty() || getActivity() == null) {
            return;
        }
        PopupMenu menu = new PopupMenu(getActivity(), anchor);
        final boolean willPin = !item.pinned;
        final boolean willFold = !item.folded;
        menu.getMenu().add(0, 1, 0, willPin ? "置顶聊天" : "取消置顶");
        if (!item.isGroup) {
            menu.getMenu().add(0, 2, 1, "设置备注");
        }
        menu.getMenu().add(0, 3, 2, willFold ? "折叠聊天" : "取消折叠");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem menuItem) {
                if (menuItem.getItemId() == 1) {
                    RecentPinStore.setPinned(getActivity(), item.isGroup, item.id, willPin);
                    Toast.makeText(getActivity(), willPin ? "已置顶" : "已取消置顶", Toast.LENGTH_SHORT).show();
                    scheduleLoadRecentChats(0);
                    return true;
                }
                if (menuItem.getItemId() == 2) {
                    showRemarkDialog(item);
                    return true;
                }
                if (menuItem.getItemId() == 3) {
                    RecentFoldStore.setFolded(getActivity(), item.isGroup, item.id, willFold);
                    if (willFold && item.pinned) {
                        RecentPinStore.setPinned(getActivity(), item.isGroup, item.id, false);
                    }
                    Toast.makeText(getActivity(), willFold ? "已折叠" : "已取消折叠", Toast.LENGTH_SHORT).show();
                    scheduleLoadRecentChats(0);
                    return true;
                }
                return false;
            }
        });
        menu.show();
    }

    private void showRemarkDialog(final RecentItem item) {
        if (item == null || item.id == null || item.id.isEmpty() || getActivity() == null) {
            return;
        }
        final EditText input = new EditText(getActivity());
        input.setHint(getString(R.string.remark_hint));
        User friend = findFriendByUid(item.id);
        if (friend != null && friend.remark_name != null && friend.remark_name.trim().length() > 0) {
            String currentRemark = friend.remark_name.trim();
            input.setText(currentRemark);
            input.setSelection(currentRemark.length());
        }
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.remark_set_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String remark = input.getText() == null ? "" : input.getText().toString().trim();
                        submitRemark(item.id, remark);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void submitRemark(final String friendUid, final String remark) {
        if (getActivity() == null || token == null || token.isEmpty()) {
            return;
        }
        if (remark != null && remark.length() > 32) {
            Toast.makeText(getActivity(), "备注最多32个字", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("friend_uid", friendUid);
            json.put("remark_name", remark == null ? "" : remark);
            HttpUtil.post("/friends/remark", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    User friend = findFriendByUid(friendUid);
                    String fallbackDisplay = friend != null ? friend.display_name : "";
                    String fallbackUsername = friend != null ? friend.username : "";
                    String fallbackAvatar = friend != null ? friend.avatar_url : "";
                    String mergedName = FriendNameResolver.resolve(remark, fallbackDisplay, fallbackUsername, friendUid);
                    updateLocalFriendRemark(friendUid, remark);
                    RecentChatCache.touchRecentChat(getActivity(), friendUid, mergedName, fallbackAvatar);
                    UserNameCache.put(getActivity(), friendUid, mergedName);
                    Toast.makeText(getActivity(),
                            (remark == null || remark.length() == 0) ? "已清除备注" : "备注已更新",
                            Toast.LENGTH_SHORT).show();
                    scheduleLoadRecentChats(0);
                }

                @Override
                public void onError(int code, String error) {
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    if (error != null && error.contains("remark_too_long")) {
                        Toast.makeText(getActivity(), "备注最多32个字", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(getActivity(), "设置备注失败", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(getActivity(), "设置备注失败", Toast.LENGTH_SHORT).show();
        }
    }

    private User findFriendByUid(String uid) {
        if (getActivity() == null || uid == null || uid.isEmpty()) {
            return null;
        }
        List<User> users = FriendCache.getFriends(getActivity());
        if (users == null || users.isEmpty()) {
            return null;
        }
        for (User user : users) {
            if (user == null || user.uid == null) {
                continue;
            }
            if (uid.equals(user.uid)) {
                return user;
            }
        }
        return null;
    }

    private void updateLocalFriendRemark(String uid, String remark) {
        if (getActivity() == null || uid == null || uid.isEmpty()) {
            return;
        }
        List<User> users = FriendCache.getFriends(getActivity());
        if (users == null || users.isEmpty()) {
            return;
        }
        boolean changed = false;
        String safeRemark = remark == null ? "" : remark;
        for (User user : users) {
            if (user == null || user.uid == null) {
                continue;
            }
            if (!uid.equals(user.uid)) {
                continue;
            }
            if (safeRemark.equals(user.remark_name == null ? "" : user.remark_name)) {
                return;
            }
            user.remark_name = safeRemark;
            changed = true;
            break;
        }
        if (changed) {
            FriendCache.saveFriends(getActivity(), users);
        }
    }

    private int getUnreadNotificationCount() {
        // 从服务端获取或本地缓存中读取未读通知数量
        // 这里先返回0，实际应该调用API
        if (getActivity() == null) {
            return 0;
        }
        SharedPreferences prefs = getActivity().getSharedPreferences("notification", Context.MODE_PRIVATE);
        return prefs.getInt("unread_count", 0);
    }
}
