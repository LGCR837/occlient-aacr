package aoharureverie.ocaacrclient.oldchat.ui.fragments;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorListenerAdapter;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.MainActivity;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.data.FriendRequestStore;
import aoharureverie.ocaacrclient.oldchat.models.FriendCache;
import aoharureverie.ocaacrclient.oldchat.models.Group;
import aoharureverie.ocaacrclient.oldchat.models.GroupCache;
import aoharureverie.ocaacrclient.oldchat.models.GroupRecentChatCache;
import aoharureverie.ocaacrclient.oldchat.models.RecentChatCache;
import aoharureverie.ocaacrclient.oldchat.models.User;
import aoharureverie.ocaacrclient.oldchat.ui.FriendAdapter;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

abstract class FriendsFragmentSupport extends Fragment {
    protected ListView lvFriends;
    protected EditText etFriendSearch;
    protected View btnClearFriendSearch;
    protected View fabAdd;
    protected View fabMenu;
    protected View fabMenuAddFriend;
    protected View fabMenuCreateGroup;
    protected ProgressBar pbFriendsLoading;
    protected boolean isMenuOpen = false;
    protected String token;
    protected FriendAdapter adapter;
    protected List<User> recentFriends = new ArrayList<>();
    protected List<User> otherFriends = new ArrayList<>();
    protected List<Group> groups = new ArrayList<>();
    protected List<FriendAdapter.FriendRequestItem> pendingRequests = new ArrayList<>();
    protected boolean groupsLoaded = false;
    protected boolean friendsLoaded = false;
    protected boolean requestsLoaded = false;
    private boolean requestActionLoading = false;

    protected void hideKeyboard(View view) {
        if (view == null || getActivity() == null) {
            return;
        }
        try {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
    }

    protected void toggleMenu() {
        isMenuOpen = !isMenuOpen;
        if (isMenuOpen) {
            fabMenu.setVisibility(View.VISIBLE);
            ViewCompat.setAlpha(fabMenu, 0f);
            ViewCompat.animate(fabMenu).alpha(1f).setDuration(200).setListener(null).start();
            ViewCompat.animate(fabAdd).rotation(45f).setDuration(200).setListener(null).start();
        } else {
            ViewCompat.animate(fabMenu).alpha(0f).setDuration(200)
                    .setListener(new ViewPropertyAnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(View view) {
                            if (fabMenu != null) {
                                fabMenu.setVisibility(View.GONE);
                            }
                        }
                    }).start();
            ViewCompat.animate(fabAdd).rotation(0f).setDuration(200).setListener(null).start();
        }
    }

    protected void splitFriends(List<User> friends) {
        recentFriends.clear();
        otherFriends.clear();
        long cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
        for (User u : friends) {
            long addedMs = u.friend_added_at > 0 ? u.friend_added_at * 1000L : 0;
            if (addedMs > 0 && addedMs >= cutoff) {
                recentFriends.add(u);
            } else {
                otherFriends.add(u);
            }
        }
    }

    protected void updateAdapter() {
        if (adapter == null) {
            return;
        }
        adapter.setData(groups, recentFriends, otherFriends, pendingRequests);
        showLoading(!(groupsLoaded && friendsLoaded && requestsLoaded));
    }

    protected void loadCachedData() {
        if (getActivity() == null) {
            return;
        }
        List<Group> cachedGroups = GroupCache.getGroups(getActivity());
        List<User> cachedFriends = FriendCache.getFriends(getActivity());
        groups.clear();
        recentFriends.clear();
        otherFriends.clear();
        if (!cachedGroups.isEmpty()) {
            groups.addAll(cachedGroups);
        }
        if (!cachedFriends.isEmpty()) {
            splitFriends(cachedFriends);
        }
        updateAdapter();
    }

    protected void showLoading(boolean loading) {
        if (pbFriendsLoading != null) {
            pbFriendsLoading.setVisibility((loading || requestActionLoading) ? View.VISIBLE : View.GONE);
        }
    }

    protected void respondToRequest(String requestId, boolean accept) {
        if (requestId == null || requestId.length() == 0 || getActivity() == null) {
            return;
        }
        if (requestActionLoading) {
            Toast.makeText(getActivity(), "正在处理中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        if (token == null || token.length() == 0) {
            Toast.makeText(getActivity(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        final boolean acceptFinal = accept;
        final String requestIdFinal = requestId;
        setRequestActionLoading(true);
        try {
            JSONObject json = new JSONObject();
            json.put("request_id", requestId);
            json.put("accept", acceptFinal);
            HttpUtil.post("/friends/respond", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    if (getActivity() == null) {
                        setRequestActionLoading(false);
                        return;
                    }
                    removePendingRequestLocal(requestIdFinal);
                    String msg = acceptFinal ? "已接受好友申请" : "已拒绝好友申请";
                    Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
                    setRequestActionLoading(false);
                    loadFriends();
                    loadRequests();
                }

                @Override
                public void onError(int code, String error) {
                    if (getActivity() == null) {
                        setRequestActionLoading(false);
                        return;
                    }
                    setRequestActionLoading(false);
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    Toast.makeText(getActivity(), "处理失败", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            setRequestActionLoading(false);
        }
    }

    private void setRequestActionLoading(boolean loading) {
        requestActionLoading = loading;
        setInteractionEnabled(!loading);
        if (loading && getActivity() != null) {
            Toast.makeText(getActivity(), "正在处理好友申请...", Toast.LENGTH_SHORT).show();
        }
        showLoading(!(groupsLoaded && friendsLoaded && requestsLoaded));
    }

    private void setInteractionEnabled(boolean enabled) {
        if (lvFriends != null) {
            lvFriends.setEnabled(enabled);
        }
        if (etFriendSearch != null) {
            etFriendSearch.setEnabled(enabled);
        }
        if (btnClearFriendSearch != null) {
            btnClearFriendSearch.setEnabled(enabled);
        }
        if (fabAdd != null) {
            fabAdd.setEnabled(enabled);
        }
        if (fabMenuAddFriend != null) {
            fabMenuAddFriend.setEnabled(enabled);
        }
        if (fabMenuCreateGroup != null) {
            fabMenuCreateGroup.setEnabled(enabled);
        }
    }

    private void removePendingRequestLocal(String requestId) {
        if (requestId == null || requestId.length() == 0 || pendingRequests == null) {
            return;
        }
        boolean changed = false;
        for (int i = pendingRequests.size() - 1; i >= 0; i--) {
            FriendAdapter.FriendRequestItem item = pendingRequests.get(i);
            if (item != null && requestId.equals(item.id)) {
                pendingRequests.remove(i);
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        if (getActivity() != null) {
            int count = pendingRequests.size();
            FriendRequestStore.setPendingCount(getActivity(), count);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).updateFriendsBadge(count);
            }
        }
        updateAdapter();
    }

    protected abstract void loadFriends();

    protected abstract void loadRequests();
}
