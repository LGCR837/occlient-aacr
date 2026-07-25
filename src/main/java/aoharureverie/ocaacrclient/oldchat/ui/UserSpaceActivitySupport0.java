package aoharureverie.ocaacrclient.oldchat.ui;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.Moment;
import aoharureverie.ocaacrclient.oldchat.ui.fragments.MomentParser;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
abstract class UserSpaceActivitySupport0 extends BaseActivity implements MomentAdapter.OnMomentActionListener {
    protected static final String AUTH_PREFS = "auth";
    protected static final String PROFILE_CACHE_PREFS = "user_space_profile_cache";
    protected static final String PROFILE_CACHE_KEY_PREFIX = "profile_";

    protected ListView lvMoments;
    protected ImageView ivCover;
    protected ImageView ivAvatar;
    protected TextView tvName;
    protected TextView tvTitleBadge;
    protected TextView tvUid;
    protected TextView tvSignature;
    protected View buttonRow;
    protected View btnEdit;
    protected View btnPost;
    protected View actionRow;
    protected View btnPrimary;
    protected View btnReport;
    protected MomentAdapter adapter;
    protected final List<Moment> moments = new ArrayList<Moment>();
    protected String token;
    protected String profileUid;
    protected String myUid;
    protected boolean isSelf;
    protected boolean isFriend;
    protected String profileName;
    protected String profileAvatar;
    protected boolean hasLoadedOnCreate;
    protected boolean hasCachedProfile;
    protected boolean profileNotFoundRetried;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_space);

        lvMoments = findViewByIdCompat(R.id.lvMoments);
        View header = LayoutInflater.from(this).inflate(R.layout.header_user_space, lvMoments, false);
        ivCover = header.findViewById(R.id.ivSpaceCover);
        ivAvatar = header.findViewById(R.id.ivSpaceAvatar);
        tvName = header.findViewById(R.id.tvSpaceName);
        tvTitleBadge = header.findViewById(R.id.tvSpaceTitleBadge);
        tvUid = header.findViewById(R.id.tvSpaceUid);
        tvSignature = header.findViewById(R.id.tvSpaceSignature);
        buttonRow = header.findViewById(R.id.space_button_row);
        btnEdit = header.findViewById(R.id.btnSpaceEdit);
        btnPost = header.findViewById(R.id.btnSpacePost);
        actionRow = header.findViewById(R.id.space_action_row);
        btnPrimary = header.findViewById(R.id.btnSpacePrimary);
        btnReport = header.findViewById(R.id.btnSpaceReport);
        View btnBack = header.findViewById(R.id.btnSpaceBack);
        lvMoments.addHeaderView(header);

        SharedPreferences prefs = getSharedPreferences(AUTH_PREFS, MODE_PRIVATE);
        token = prefs.getString("access_token", "");
        myUid = prefs.getString("my_uid", "");

        profileUid = normalizeUid(getIntent().getStringExtra("uid"));
        if (profileUid == null || profileUid.isEmpty()) {
            profileUid = myUid;
        }
        if (profileUid == null) {
            profileUid = "";
        }
        isSelf = profileUid.equalsIgnoreCase(myUid);
        isFriend = UserSpaceActionHelper.isFriend(this, profileUid);

        if (!isSelf) {
            if (buttonRow != null) {
                buttonRow.setVisibility(View.GONE);
            }
            if (btnEdit != null) {
                btnEdit.setVisibility(View.GONE);
            }
            if (btnPost != null) {
                btnPost.setVisibility(View.GONE);
            }
            if (actionRow != null) {
                actionRow.setVisibility(View.VISIBLE);
            }
        }

        if (btnEdit != null) {
            btnEdit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(UserSpaceActivitySupport0.this, ProfileSpaceEditActivity.class));
                }
            });
        }
        if (btnPost != null) {
            btnPost.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(UserSpaceActivitySupport0.this, MomentComposeActivity.class));
                }
            });
        }
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
        if (!isSelf) {
            UserSpaceActionHelper.bindActions((UserSpaceActivity) this, btnPrimary, btnReport, isFriend,
                    profileUid, profileName, profileAvatar, token);
        }

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
                if (!canDeleteMoment(moment)) {
                    return false;
                }
                confirmDeleteMoment(moment, index);
                return true;
            }
        });

        loadProfile();
        loadMoments();
        hasLoadedOnCreate = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isSelf) {
            isFriend = UserSpaceActionHelper.isFriend(this, profileUid);
            UserSpaceActionHelper.bindActions((UserSpaceActivity) this, btnPrimary, btnReport, isFriend,
                    profileUid, profileName, profileAvatar, token);
        }
        if (hasLoadedOnCreate) {
            hasLoadedOnCreate = false;
            return;
        }
        loadProfile();
        loadMoments();
    }

    protected void loadProfile() {
        profileNotFoundRetried = false;
        loadCachedProfile();
        requestProfile(false);
    }

    protected void requestProfile(final boolean retried) {
        String path;
        if (isSelf) {
            path = "/me";
        } else {
            String encoded = Uri.encode(profileUid == null ? "" : profileUid);
            path = "/users/profile?uid=" + encoded;
        }
        HttpUtil.get(path, token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                persistProfileCache(response);
                if (!applyProfilePayload(response)) {
                    Toast.makeText(UserSpaceActivitySupport0.this, "加载资料失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(int code, String error) {
                if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                    return;
                }
                if (shouldRetryNotFoundProfile(code, error, retried)) {
                    profileNotFoundRetried = true;
                    retryProfileRequest();
                    return;
                }
                if (shouldRetryProfileRequest(code, error, retried)) {
                    retryProfileRequest();
                    return;
                }
                if (isUserProfileNotFound(code, error)) {
                    if (hasCachedProfile) {
                        Toast.makeText(UserSpaceActivitySupport0.this, "网络波动，已显示缓存资料", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(UserSpaceActivitySupport0.this, "用户不存在或已注销", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                if (hasCachedProfile) {
                    Toast.makeText(UserSpaceActivitySupport0.this, "网络波动，已显示缓存资料", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(UserSpaceActivitySupport0.this, "加载资料失败，请检查网络", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    protected void loadMoments() {
        requestMoments(false);
    }

    protected void requestMoments(final boolean retried) {
        String encoded = Uri.encode(profileUid == null ? "" : profileUid);
        String path = "/moments/user?uid=" + encoded + "&limit=20";
        HttpUtil.get(path, token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject obj = new JSONObject(response);
                    List<Moment> incoming = MomentParser.parse(obj.getJSONArray("moments"));
                    moments.clear();
                    moments.addAll(incoming);
                    adapter.notifyDataSetChanged();
                } catch (Exception e) {
                    Toast.makeText(UserSpaceActivitySupport0.this, "加载动态失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(int code, String error) {
                if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                    return;
                }
                if (shouldRetryProfileRequest(code, error, retried)) {
                    retryMomentsRequest();
                    return;
                }
                if (isUserProfileNotFound(code, error)) {
                    if (moments.isEmpty()) {
                        Toast.makeText(UserSpaceActivitySupport0.this, "该用户暂无动态", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                if (!moments.isEmpty()) {
                    Toast.makeText(UserSpaceActivitySupport0.this, "网络波动，已保留已加载动态", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(UserSpaceActivitySupport0.this, "加载动态失败，请稍后重试", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    protected String normalizeUid(String rawUid) {
        if (rawUid == null) {
            return "";
        }
        String uid = rawUid.trim();
        while (uid.startsWith("@")) {
            uid = uid.substring(1).trim();
        }
        return uid;
    }
    protected abstract boolean canDeleteMoment(Moment moment);
    protected abstract void confirmDeleteMoment(Moment moment, int index);
    protected abstract void retryProfileRequest();
    protected abstract void retryMomentsRequest();
    protected abstract boolean shouldRetryProfileRequest(int code, String error, boolean retried);
    protected abstract boolean shouldRetryNotFoundProfile(int code, String error, boolean retried);
    protected abstract boolean isUserProfileNotFound(int code, String error);
    protected abstract void loadCachedProfile();
    protected abstract void persistProfileCache(String payload);
    protected abstract boolean applyProfilePayload(String payload);
}
