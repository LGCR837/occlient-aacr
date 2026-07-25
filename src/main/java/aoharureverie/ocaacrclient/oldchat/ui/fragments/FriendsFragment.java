package aoharureverie.ocaacrclient.oldchat.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.EditText;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.data.FriendRequestStore;
import aoharureverie.ocaacrclient.oldchat.MainActivity;
import aoharureverie.ocaacrclient.oldchat.models.FriendCache;
import aoharureverie.ocaacrclient.oldchat.models.FriendNameResolver;
import aoharureverie.ocaacrclient.oldchat.models.Group;
import aoharureverie.ocaacrclient.oldchat.models.GroupCache;
import aoharureverie.ocaacrclient.oldchat.models.GroupRecentChatCache;
import aoharureverie.ocaacrclient.oldchat.models.RecentChatCache;
import aoharureverie.ocaacrclient.oldchat.models.UserNameCache;
import aoharureverie.ocaacrclient.oldchat.models.User;
import aoharureverie.ocaacrclient.oldchat.util.NetworkStateManager;
import aoharureverie.ocaacrclient.oldchat.ui.AddFriendActivity;
import aoharureverie.ocaacrclient.oldchat.ui.ChatActivity;
import aoharureverie.ocaacrclient.oldchat.ui.FriendAdapter;
import aoharureverie.ocaacrclient.oldchat.ui.GroupChatActivity;
import aoharureverie.ocaacrclient.oldchat.ui.GroupCreateActivity;
import aoharureverie.ocaacrclient.oldchat.ui.NotificationChatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class FriendsFragment extends FriendsFragmentSupport {
    private static final int FRIENDS_RETRY_TIMES = 2;
    private static final long FRIENDS_RETRY_DELAY_MS = 900L;
    private static final long RESUME_TOAST_SUPPRESS_MS = 2400L;
    private static final long FRIENDS_AUTO_RECOVER_DELAY_MS = 1800L;
    private static final long FRIENDS_AUTO_RECOVER_MIN_INTERVAL_MS = 12000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private long toastSuppressUntilMs;
    private long lastAutoRecoverRetryMs;
    private int friendsRequestToken;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends, container, false);
        lvFriends = view.findViewById(R.id.lvFriends);
        etFriendSearch = view.findViewById(R.id.etFriendSearch);
        btnClearFriendSearch = view.findViewById(R.id.btnClearFriendSearch);
        fabAdd = view.findViewById(R.id.fabAdd);
        fabMenu = view.findViewById(R.id.fabMenu);
        fabMenuAddFriend = view.findViewById(R.id.fabMenuAddFriend);
        fabMenuCreateGroup = view.findViewById(R.id.fabMenuCreateGroup);
        pbFriendsLoading = view.findViewById(R.id.pbFriendsLoading);

        SharedPreferences prefs = getActivity().getSharedPreferences("auth", Context.MODE_PRIVATE);
        token = prefs.getString("access_token", "");

        adapter = new FriendAdapter(getActivity(), new FriendAdapter.OnFriendClickListener() {
            @Override
            public void onFriendClick(User friend) {
                Intent intent = new Intent(getActivity(), ChatActivity.class);
                intent.putExtra("friend_id", friend.id);
                intent.putExtra("friend_uid", friend.uid);
                intent.putExtra("friend_name", FriendNameResolver.resolve(friend));
                intent.putExtra("friend_avatar", friend.avatar_url);
                startActivity(intent);
            }

            @Override
            public void onAvatarClick(User friend) {
                Intent intent = new Intent(getActivity(), aoharureverie.ocaacrclient.oldchat.ui.UserSpaceActivity.class);
                intent.putExtra("uid", friend.uid);
                startActivity(intent);
            }

            @Override
            public void onRequestAccept(String requestId) {
                respondToRequest(requestId, true);
            }

            @Override
            public void onRequestReject(String requestId) {
                respondToRequest(requestId, false);
            }

            @Override
            public void onGroupClick(Group group) {
                Intent intent = new Intent(getActivity(), GroupChatActivity.class);
                intent.putExtra("group_id", group.id);
                intent.putExtra("group_name", group.name);
                intent.putExtra("group_avatar", group.avatar_url);
                intent.putExtra("group_role", group.role);
                startActivity(intent);
            }

            @Override
            public void onSystemNotificationClick() {
                startActivity(new Intent(getActivity(), NotificationChatActivity.class));
            }
        });

        lvFriends.setAdapter(adapter);

        if (etFriendSearch != null) {
            etFriendSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            etFriendSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    String q = s == null ? "" : s.toString();
                    if (adapter != null) {
                        adapter.filter(q);
                    }
                    if (btnClearFriendSearch != null) {
                        btnClearFriendSearch.setVisibility(q != null && q.trim().length() > 0 ? View.VISIBLE : View.GONE);
                    }
                    if (isMenuOpen) {
                        toggleMenu();
                    }
                }
            });

            etFriendSearch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus && isMenuOpen) {
                        toggleMenu();
                    }
                }
            });
        }

        if (btnClearFriendSearch != null) {
            btnClearFriendSearch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (etFriendSearch != null) {
                        etFriendSearch.setText("");
                        etFriendSearch.clearFocus();
                        hideKeyboard(etFriendSearch);
                    }
                }
            });
        }

        // 浮动按钮点击切换菜单
        fabAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMenu();
            }
        });

        // 添加好友
        fabMenuAddFriend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMenu();
                startActivity(new Intent(getActivity(), AddFriendActivity.class));
            }
        });

        // 创建群聊
        fabMenuCreateGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMenu();
                startActivity(new Intent(getActivity(), GroupCreateActivity.class));
            }
        });

        // 点击列表关闭菜单
        lvFriends.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) {
                if (scrollState != android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE && isMenuOpen) {
                    toggleMenu();
                }
            }

            @Override
            public void onScroll(android.widget.AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = getActivity().getSharedPreferences("auth", Context.MODE_PRIVATE);
        token = prefs.getString("access_token", "");
        toastSuppressUntilMs = System.currentTimeMillis() + RESUME_TOAST_SUPPRESS_MS;
        mainHandler.removeCallbacksAndMessages(null);
        groupsLoaded = false;
        friendsLoaded = false;
        requestsLoaded = false;
        showLoading(true);
        loadCachedData();
        loadGroups();
        loadFriends();
    }

    private void loadGroups() {
        if (token == null || token.isEmpty() || !NetworkStateManager.getInstance().isServerAvailable()) {
            groups = GroupCache.getGroups(getActivity());
            GroupRecentChatCache.mergeGroupInfo(getActivity(), groups);
            groupsLoaded = true;
            updateAdapter();
            return;
        }
        HttpUtil.get("/groups/list", token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject obj = new JSONObject(response);
                    JSONArray arr = obj.getJSONArray("groups");
                    groups.clear();
                    groups.addAll(FriendsApiParser.parseGroups(arr));
                } catch (Exception e) {
                    groups.clear();
                }
                GroupCache.saveGroups(getActivity(), groups);
                GroupRecentChatCache.mergeGroupInfo(getActivity(), groups);
                groupsLoaded = true;
                updateAdapter();
            }

            @Override
            public void onError(int code, String error) {
                if (!NetworkStateManager.getInstance().isServerAvailable()) {
                    groups = GroupCache.getGroups(getActivity());
                } else {
                    groups.clear();
                }
                groupsLoaded = true;
                updateAdapter();
            }
        });
    }

    @Override
    protected void loadFriends() {
        final int requestToken = ++friendsRequestToken;
        loadFriendsInternal(requestToken, 0);
    }

    private void loadFriendsInternal(final int requestToken, final int retryCount) {
        if (token == null || token.isEmpty() || !NetworkStateManager.getInstance().isServerAvailable()) {
            List<User> cached = getCachedFriendsSafe();
            splitFriends(cached);
            friendsLoaded = true;
            requestsLoaded = true;
            updateAdapter();
            return;
        }
        HttpUtil.get("/friends", token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                if (requestToken != friendsRequestToken || !isAdded() || getActivity() == null) {
                    return;
                }
                try {
                    JSONObject obj = new JSONObject(response);
                    JSONArray arr = obj.getJSONArray("friends");
                    List<User> friends = FriendsApiParser.parseFriends(arr);
                    splitFriends(friends);
                    FriendCache.saveFriends(getActivity(), friends);
                    RecentChatCache.mergeFriendInfo(getActivity(), friends);
                    RecentChatCache.cleanupInvalidChats(getActivity(), friends);
                    UserNameCache.mergeUsers(getActivity(), friends);
                    aoharureverie.ocaacrclient.oldchat.models.UserTitleCache.mergeUsers(getActivity(), friends);
                    friendsLoaded = true;
                    loadRequests();
                } catch (Exception e) {
                    if (getActivity() != null) {
                        Toast.makeText(getActivity(), "解析好友列表失败", Toast.LENGTH_SHORT).show();
                    }
                    friendsLoaded = true;
                    requestsLoaded = true;
                    updateAdapter();
                }
            }

            @Override
            public void onError(int code, String error) {
                if (requestToken != friendsRequestToken || !isAdded() || getActivity() == null) {
                    return;
                }
                boolean suppress = HttpUtil.shouldSuppressAuthToast(code, error);
                List<User> cached = getCachedFriendsSafe();
                boolean hasCache = cached != null && !cached.isEmpty();
                if (hasCache) {
                    splitFriends(cached);
                }

                if (isTransientError(code) && retryCount < FRIENDS_RETRY_TIMES) {
                    final int nextRetry = retryCount + 1;
                    mainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!isAdded()) {
                                return;
                            }
                            loadFriendsInternal(requestToken, nextRetry);
                        }
                    }, FRIENDS_RETRY_DELAY_MS);
                }

                boolean autoRecoverScheduled = false;
                if (shouldAutoRecoverRetry(code, retryCount)) {
                    autoRecoverScheduled = true;
                    mainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!isAdded()) {
                                return;
                            }
                            loadFriends();
                        }
                    }, FRIENDS_AUTO_RECOVER_DELAY_MS);
                }

                if (!suppress && !autoRecoverScheduled && shouldShowLoadFailToast(code, hasCache, retryCount)) {
                    Toast.makeText(getActivity(), "加载好友失败: " + code, Toast.LENGTH_SHORT).show();
                }
                friendsLoaded = true;
                requestsLoaded = true;
                updateAdapter();
            }
        });
    }

    private boolean shouldShowLoadFailToast(int code, boolean hasCache, int retryCount) {
        if (hasCache) {
            return false;
        }
        if (retryCount < FRIENDS_RETRY_TIMES && isTransientError(code)) {
            return false;
        }
        return System.currentTimeMillis() > toastSuppressUntilMs;
    }

    private boolean shouldAutoRecoverRetry(int code, int retryCount) {
        if (!isTransientError(code) || retryCount < FRIENDS_RETRY_TIMES) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastAutoRecoverRetryMs < FRIENDS_AUTO_RECOVER_MIN_INTERVAL_MS) {
            return false;
        }
        lastAutoRecoverRetryMs = now;
        return true;
    }

    private boolean isTransientError(int code) {
        return code <= 0
                || code == 408
                || code == 429
                || code == 500
                || code == 502
                || code == 503
                || code == 504;
    }

    private List<User> getCachedFriendsSafe() {
        if (!isAdded() || getActivity() == null) {
            return new ArrayList<User>();
        }
        List<User> cached = FriendCache.getFriends(getActivity());
        if (cached == null) {
            return new ArrayList<User>();
        }
        return cached;
    }

    @Override
    public void onPause() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    protected void loadRequests() {
        HttpUtil.get("/friends/requests", token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject obj = new JSONObject(response);
                    JSONArray arr = obj.getJSONArray("requests");
                    List<FriendAdapter.FriendRequestItem> requests = FriendsApiParser.parseRequests(arr);
                    pendingRequests = requests;
                    FriendRequestStore.setPendingCount(getActivity(), requests.size());
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).updateFriendsBadge(requests.size());
                    }
                    requestsLoaded = true;
                    updateAdapter();
                } catch (Exception e) {
                    pendingRequests.clear();
                    requestsLoaded = true;
                    updateAdapter();
                }
            }

            @Override
            public void onError(int code, String error) {
                pendingRequests.clear();
                requestsLoaded = true;
                updateAdapter();
            }
        });
    }
}
