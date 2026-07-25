package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.data.MomentNoticeStore;
import aoharureverie.ocaacrclient.oldchat.models.Moment;
import aoharureverie.ocaacrclient.oldchat.models.MomentCache;
import aoharureverie.ocaacrclient.oldchat.ui.fragments.MomentParser;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;
import aoharureverie.ocaacrclient.oldchat.util.NetworkStateManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class MomentsActivity extends BaseActivity implements MomentAdapter.OnMomentActionListener {
    private static final String AUTH_PREFS = "auth";
    private static final String PROFILE_CACHE_PREFS = "profile_cache";
    private static final String PROFILE_CACHE_KEY = "me_profile_json";
    private static final int PAGE_SIZE = 10;

    private ListView lvMoments;
    private ProgressBar pbMomentsLoading;
    private MomentAdapter adapter;
    private final List<Moment> moments = new ArrayList<>();
    private String token;
    private String myUid;
    private View noticeDot;
    private ImageView ivHeaderCover;
    private ImageView ivHeaderAvatar;
    private TextView tvHeaderName;
    private View headerView;
    private View btnLoadMore;
    private View loadMoreFooter;
    private int currentOffset = 0;
    private boolean hasMoreMoments = true;
    private boolean isLoadingMore = false;
    private String pendingScrollMomentId;
    private int scrollAttempts = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moments);

        lvMoments = findViewByIdCompat(R.id.lvMoments);
        pbMomentsLoading = findViewByIdCompat(R.id.pbMomentsLoading);
        noticeDot = findViewByIdCompat(R.id.viewMomentsNoticeDot);
        View emptyView = (View) findViewByIdCompat(R.id.tvMomentsEmpty);
        if (lvMoments != null && emptyView != null) {
            lvMoments.setEmptyView(emptyView);
        }

        View btnBack = (View) findViewByIdCompat(R.id.btnMomentsBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        View btnAdd = (View) findViewByIdCompat(R.id.btnMomentsAdd);
        if (btnAdd != null) {
            btnAdd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openMomentCompose();
                }
            });
        }
        View btnNotice = (View) findViewByIdCompat(R.id.btnMomentsNotice);
        if (btnNotice != null) {
            btnNotice.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(MomentsActivity.this, MomentNoticeActivity.class));
                }
            });
        }

        pendingScrollMomentId = getIntent().getStringExtra("scroll_moment_id");

        setupLoadMoreFooter();
        setupCoverHeader();

        adapter = new MomentAdapter(this, moments, this);
        lvMoments.setAdapter(adapter);
        lvMoments.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View v, int position, long id) {
                int index = position - lvMoments.getHeaderViewsCount();
                if (index < 0 || index >= moments.size()) {
                    return false;
                }
                Moment moment = moments.get(index);
                if (!MomentsDeleteHelper.isOwnMoment(myUid, moment)) {
                    return false;
                }
                MomentsDeleteHelper.confirmDeleteMoment(MomentsActivity.this, token, moment, index, moments, adapter);
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNoticeDot();
        SharedPreferences prefs = getSharedPreferences(AUTH_PREFS, MODE_PRIVATE);
        token = prefs.getString("access_token", "");
        myUid = prefs.getString("my_uid", "");
        bindHeaderProfile();

        currentOffset = 0;
        hasMoreMoments = true;
        scrollAttempts = 0;

        if (moments == null || moments.isEmpty()) {
            showLoading(true);
        }
        loadCachedFeed();
        loadFeed();
    }

    private void setupCoverHeader() {
        if (lvMoments == null || headerView != null) {
            return;
        }
        headerView = getLayoutInflater().inflate(R.layout.layout_moments_header_wechat, lvMoments, false);
        ivHeaderCover = (ImageView) headerView.findViewById(R.id.ivMomentsHeaderCover);
        ivHeaderAvatar = (ImageView) headerView.findViewById(R.id.ivMomentsHeaderAvatar);
        tvHeaderName = (TextView) headerView.findViewById(R.id.tvMomentsHeaderName);
        if (lvMoments.getHeaderViewsCount() == 0) {
            lvMoments.addHeaderView(headerView, null, false);
        }
        if (ivHeaderAvatar != null) {
            ivHeaderAvatar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (myUid == null || myUid.isEmpty()) {
                        return;
                    }
                    Intent intent = new Intent(MomentsActivity.this, UserSpaceActivity.class);
                    intent.putExtra("uid", myUid);
                    startActivity(intent);
                }
            });
        }
    }

    private void bindHeaderProfile() {
        if (tvHeaderName == null) {
            return;
        }
        String displayName = myUid;
        String avatarUrl = "";
        String coverUrl = "";
        try {
            SharedPreferences prefs = getSharedPreferences(PROFILE_CACHE_PREFS, MODE_PRIVATE);
            String cached = prefs.getString(PROFILE_CACHE_KEY, "");
            if (cached != null && cached.length() > 0) {
                JSONObject obj = new JSONObject(cached);
                String uid = obj.optString("uid", "");
                if (TextUtils.isEmpty(myUid) && !TextUtils.isEmpty(uid)) {
                    myUid = uid;
                    displayName = uid;
                }
                String display = obj.optString("display_name", "");
                String username = obj.optString("username", "");
                if (!TextUtils.isEmpty(display)) {
                    displayName = display;
                } else if (!TextUtils.isEmpty(username)) {
                    displayName = username;
                } else if (!TextUtils.isEmpty(uid)) {
                    displayName = uid;
                }
                avatarUrl = obj.optString("avatar_url", "");
                coverUrl = obj.optString("cover_url", "");
            }
        } catch (Exception e) {
        }
        if (TextUtils.isEmpty(displayName)) {
            displayName = "我";
        }
        tvHeaderName.setText(displayName);
        if (ivHeaderAvatar != null) {
            ImageLoader.loadAvatar(ivHeaderAvatar, avatarUrl);
        }
        if (ivHeaderCover != null && !TextUtils.isEmpty(coverUrl)) {
            ImageLoader.load(ivHeaderCover, coverUrl);
        }
    }

    private void refreshHeaderFromMoments() {
        if (moments == null || moments.isEmpty() || tvHeaderName == null || TextUtils.isEmpty(myUid)) {
            return;
        }
        for (int i = 0; i < moments.size(); i++) {
            Moment one = moments.get(i);
            if (one == null || one.from_uid == null || !myUid.equals(one.from_uid)) {
                continue;
            }
            if (!TextUtils.isEmpty(one.from_name)) {
                tvHeaderName.setText(one.from_name);
            }
            if (ivHeaderAvatar != null && !TextUtils.isEmpty(one.from_avatar)) {
                ImageLoader.loadAvatar(ivHeaderAvatar, one.from_avatar);
            }
            break;
        }
    }

    private void openMomentCompose() {
        Intent intent = new Intent(this, MomentComposeActivity.class);
        startActivity(intent);
    }

    private void setupLoadMoreFooter() {
        loadMoreFooter = getLayoutInflater().inflate(R.layout.list_load_more, lvMoments, false);
        btnLoadMore = loadMoreFooter.findViewById(R.id.btnLoadMore);
        btnLoadMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isLoadingMore && hasMoreMoments) {
                    loadMoreMoments();
                }
            }
        });
        if (lvMoments.getFooterViewsCount() == 0) {
            lvMoments.addFooterView(loadMoreFooter);
        }
        updateLoadMoreButton();
    }

    private void loadFeed() {
        if (token == null || token.isEmpty() || !NetworkStateManager.getInstance().isServerAvailable()) {
            showLoading(false);
            return;
        }
        HttpUtil.get("/moments/v2?limit=" + PAGE_SIZE + "&offset=" + currentOffset, token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject obj = new JSONObject(response);
                    JSONArray arr = obj.getJSONArray("moments");
                    List<Moment> incoming = MomentParser.parse(arr);

                    if (replaceMomentsIfChanged(incoming)) {
                        adapter.notifyDataSetChanged();
                        refreshHeaderFromMoments();
                    }

                    hasMoreMoments = arr.length() >= PAGE_SIZE;
                    MomentCache.saveFeed(MomentsActivity.this, moments);
                    MomentNoticeStore.collectFromMoments(MomentsActivity.this, incoming, myUid);
                    updateNoticeDot();
                    updateLoadMoreButton();
                    maybeScrollToMoment();
                } catch (Exception e) {
                    Toast.makeText(MomentsActivity.this, "加载动态失败", Toast.LENGTH_SHORT).show();
                }
                showLoading(false);
            }

            @Override
            public void onError(int code, String error) {
                if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                    return;
                }
                Toast.makeText(MomentsActivity.this, "加载动态失败: " + code, Toast.LENGTH_SHORT).show();
                loadCachedFeed();
                showLoading(false);
                updateLoadMoreButton();
            }
        });
    }

    private void loadMoreMoments() {
        if (isLoadingMore || !hasMoreMoments) {
            return;
        }
        isLoadingMore = true;
        currentOffset = moments.size();
        updateLoadMoreButton();

        HttpUtil.get("/moments/v2?limit=" + PAGE_SIZE + "&offset=" + currentOffset, token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject obj = new JSONObject(response);
                    JSONArray arr = obj.getJSONArray("moments");
                    List<Moment> incoming = MomentParser.parse(arr);
                    moments.addAll(incoming);
                    hasMoreMoments = arr.length() >= PAGE_SIZE;
                    MomentNoticeStore.collectFromMoments(MomentsActivity.this, incoming, myUid);
                    adapter.notifyDataSetChanged();
                    refreshHeaderFromMoments();
                    updateNoticeDot();
                    maybeScrollToMoment();
                } catch (Exception e) {
                    Toast.makeText(MomentsActivity.this, "加载更多失败", Toast.LENGTH_SHORT).show();
                }
                isLoadingMore = false;
                updateLoadMoreButton();
            }

            @Override
            public void onError(int code, String error) {
                if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                    return;
                }
                Toast.makeText(MomentsActivity.this, "加载更多失败: " + code, Toast.LENGTH_SHORT).show();
                isLoadingMore = false;
                updateLoadMoreButton();
            }
        });
    }

    private void updateLoadMoreButton() {
        if (lvMoments == null || btnLoadMore == null) {
            return;
        }
        boolean hasData = moments != null && !moments.isEmpty();
        if (loadMoreFooter != null && lvMoments.getFooterViewsCount() == 0) {
            lvMoments.addFooterView(loadMoreFooter);
        }
        if (!hasData) {
            if (loadMoreFooter != null) {
                loadMoreFooter.setVisibility(View.GONE);
            }
            return;
        }
        if (loadMoreFooter != null) {
            loadMoreFooter.setVisibility(View.VISIBLE);
        }
        if (isLoadingMore) {
            btnLoadMore.setEnabled(false);
            ((android.widget.TextView) btnLoadMore).setText("加载中...");
        } else if (!hasMoreMoments) {
            btnLoadMore.setEnabled(false);
            ((android.widget.TextView) btnLoadMore).setText("没有更多了");
        } else {
            btnLoadMore.setEnabled(true);
            ((android.widget.TextView) btnLoadMore).setText("加载更多");
        }
    }

    private void maybeScrollToMoment() {
        if (pendingScrollMomentId == null || pendingScrollMomentId.isEmpty()) {
            return;
        }
        int index = findMomentIndex(pendingScrollMomentId);
        if (index >= 0) {
            final int target = index + lvMoments.getHeaderViewsCount();
            lvMoments.post(new Runnable() {
                @Override
                public void run() {
                    lvMoments.setSelection(target);
                }
            });
            pendingScrollMomentId = null;
            return;
        }
        if (hasMoreMoments && !isLoadingMore && scrollAttempts < 3) {
            scrollAttempts++;
            loadMoreMoments();
        }
    }

    private int findMomentIndex(String momentId) {
        if (momentId == null || momentId.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < moments.size(); i++) {
            Moment m = moments.get(i);
            if (m != null && momentId.equals(m.id)) {
                return i;
            }
        }
        return -1;
    }

    private void loadCachedFeed() {
        List<Moment> cached = MomentCache.getFeed(this);
        if (cached == null || cached.isEmpty()) {
            return;
        }
        if (replaceMomentsIfChanged(cached)) {
            adapter.notifyDataSetChanged();
            refreshHeaderFromMoments();
        }
        updateNoticeDot();
        updateLoadMoreButton();
    }

    private boolean replaceMomentsIfChanged(List<Moment> incoming) {
        if (incoming == null) {
            incoming = new ArrayList<Moment>();
        }
        if (isSameMomentList(moments, incoming)) {
            return false;
        }
        moments.clear();
        moments.addAll(incoming);
        return true;
    }

    private boolean isSameMomentList(List<Moment> oldList, List<Moment> newList) {
        if (oldList == null || newList == null) {
            return oldList == newList;
        }
        if (oldList.size() != newList.size()) {
            return false;
        }
        for (int i = 0; i < oldList.size(); i++) {
            Moment a = oldList.get(i);
            Moment b = newList.get(i);
            if (!isSameMoment(a, b)) {
                return false;
            }
        }
        return true;
    }

    private boolean isSameMoment(Moment a, Moment b) {
        if (a == null || b == null) {
            return a == b;
        }
        return same(a.id, b.id)
                && same(a.from_uid, b.from_uid)
                && same(a.from_name, b.from_name)
                && same(a.from_title, b.from_title)
                && same(a.from_avatar, b.from_avatar)
                && same(a.body, b.body)
                && same(a.image_url, b.image_url)
                && a.created_at == b.created_at
                && a.likes == b.likes
                && a.comments == b.comments
                && a.liked == b.liked;
    }

    private boolean same(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    private void showLoading(boolean loading) {
        if (pbMomentsLoading != null) {
            pbMomentsLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void updateNoticeDot() {
        if (noticeDot == null) {
            return;
        }
        noticeDot.setVisibility(MomentNoticeStore.hasNotices(this) ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onLike(Moment moment) {
        if (moment == null || moment.id == null) {
            return;
        }
        final Moment target = moment;
        try {
            JSONObject json = new JSONObject();
            json.put("moment_id", target.id);
            String path = target.liked ? "/moments/unlike" : "/moments/like";
            HttpUtil.post(path, json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    try {
                        JSONObject obj = new JSONObject(response);
                        target.liked = obj.optBoolean("liked", target.liked);
                        target.likes = obj.optInt("likes", target.likes);
                        adapter.notifyDataSetChanged();
                    } catch (Exception e) {
                        adapter.notifyDataSetChanged();
                    }
                }

                @Override
                public void onError(int code, String error) {
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    Toast.makeText(MomentsActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(MomentsActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onComment(Moment moment) {
        if (moment == null || moment.id == null) {
            return;
        }
        Intent intent = new Intent(this, MomentCommentsActivity.class);
        intent.putExtra("moment_id", moment.id);
        intent.putExtra("moment_owner_uid", moment.from_uid);
        startActivity(intent);
    }

    @Override
    public void onAvatar(Moment moment) {
        if (moment == null || moment.from_uid == null || moment.from_uid.isEmpty()) {
            return;
        }
        Intent intent = new Intent(this, UserSpaceActivity.class);
        intent.putExtra("uid", moment.from_uid);
        startActivity(intent);
    }

}
