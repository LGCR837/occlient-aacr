package aoharureverie.ocaacrclient.oldchat.ui;

import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.models.FriendCache;
import aoharureverie.ocaacrclient.oldchat.models.User;
import aoharureverie.ocaacrclient.oldchat.models.UserNameCache;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;

import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

abstract class UserSpaceActivitySupport1 extends UserSpaceActivitySupport0 {
    @Override
    protected void retryProfileRequest() {
        if (lvMoments != null) {
            lvMoments.postDelayed(new Runnable() {
                @Override
                public void run() {
                    requestProfile(true);
                }
            }, 500);
        } else {
            requestProfile(true);
        }
    }

    @Override
    protected void retryMomentsRequest() {
        if (lvMoments != null) {
            lvMoments.postDelayed(new Runnable() {
                @Override
                public void run() {
                    requestMoments(true);
                }
            }, 500);
        } else {
            requestMoments(true);
        }
    }

    @Override
    protected boolean shouldRetryProfileRequest(int code, String error, boolean retried) {
        if (retried) {
            return false;
        }
        if (code <= 0 || code == 408 || code == 429 || code == 500 || code == 502 || code == 503 || code == 504) {
            return true;
        }
        return code == 404 && !isUserProfileNotFound(code, error);
    }

    @Override
    protected boolean shouldRetryNotFoundProfile(int code, String error, boolean retried) {
        if (retried || profileNotFoundRetried) {
            return false;
        }
        if (isSelf) {
            return false;
        }
        if (!isUserProfileNotFound(code, error)) {
            return false;
        }
        String uid = normalizeUid(profileUid);
        return uid != null && uid.length() > 0;
    }

    @Override
    protected boolean isUserProfileNotFound(int code, String error) {
        if (code != 404) {
            return false;
        }
        return containsErrorCode(error, "user_not_found") || containsErrorCode(error, "not_found");
    }

    protected boolean containsErrorCode(String error, String code) {
        if (error == null || error.length() == 0 || code == null || code.length() == 0) {
            return false;
        }
        String raw = error.toLowerCase(Locale.US);
        String target = code.toLowerCase(Locale.US);
        if (raw.contains(target)) {
            return true;
        }
        try {
            JSONObject obj = new JSONObject(error);
            String errCode = obj.optString("error", "");
            if (target.equalsIgnoreCase(errCode)) {
                return true;
            }
            String errMsg = obj.optString("message", "");
            return errMsg != null && errMsg.toLowerCase(Locale.US).contains(target);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void loadCachedProfile() {
        hasCachedProfile = false;
        String payload = readProfileCache();
        if (payload != null && payload.length() > 0 && applyProfilePayload(payload)) {
            hasCachedProfile = true;
            return;
        }
        if (applyProfileFromFriendCache()) {
            hasCachedProfile = true;
        }
    }

    @Override
    protected void persistProfileCache(String payload) {
        if (payload == null || payload.length() == 0) {
            return;
        }
        getSharedPreferences(PROFILE_CACHE_PREFS, MODE_PRIVATE)
                .edit()
                .putString(buildProfileCacheKey(), payload)
                .apply();
    }

    protected String readProfileCache() {
        return getSharedPreferences(PROFILE_CACHE_PREFS, MODE_PRIVATE)
                .getString(buildProfileCacheKey(), "");
    }

    protected String buildProfileCacheKey() {
        String uid = normalizeUid(profileUid);
        if (uid.length() == 0) {
            uid = normalizeUid(myUid);
        }
        if (uid.length() == 0) {
            uid = "self";
        }
        return PROFILE_CACHE_KEY_PREFIX + uid.toUpperCase(Locale.US);
    }

    protected boolean applyProfileFromFriendCache() {
        if (isSelf || profileUid == null || profileUid.length() == 0) {
            return false;
        }
        List<User> friends = FriendCache.getFriends(this);
        if (friends == null || friends.isEmpty()) {
            return false;
        }
        for (int i = 0; i < friends.size(); i++) {
            User one = friends.get(i);
            if (one == null || one.uid == null || one.uid.length() == 0) {
                continue;
            }
            if (!profileUid.equalsIgnoreCase(one.uid)) {
                continue;
            }
            String name = one.display_name;
            if (name == null || name.length() == 0) {
                name = one.username;
            }
            if (name == null || name.length() == 0) {
                name = one.uid;
            }
            profileName = name;
            profileAvatar = one.avatar_url;
            if (tvName != null) {
                tvName.setText(name);
            }
            if (tvUid != null) {
                tvUid.setText("UID: " + one.uid);
            }
            UserTitleBinder.bindCompact(tvTitleBadge, one.user_title);
            applySignature(one.signature);
            if (ivAvatar != null) {
                ImageLoader.loadAvatar(ivAvatar, one.avatar_url);
            }
            applyCover("");
            refreshActionButtons();
            return true;
        }
        return false;
    }

    @Override
    protected boolean applyProfilePayload(String payload) {
        if (payload == null || payload.length() == 0) {
            return false;
        }
        try {
            JSONObject obj = new JSONObject(payload);
            String uid = obj.optString("uid", "");
            String displayName = obj.optString("display_name", "");
            String userTitle = obj.optString("user_title", "");
            String avatarUrl = obj.optString("avatar_url", "");
            String signature = obj.optString("signature", "");
            String coverUrl = obj.optString("cover_url", "");
            if (displayName == null || displayName.length() == 0) {
                displayName = uid;
            }
            if ((displayName == null || displayName.length() == 0) && profileUid != null) {
                displayName = profileUid;
            }
            profileName = displayName == null ? "" : displayName;
            profileAvatar = avatarUrl == null ? "" : avatarUrl;
            if (uid != null && uid.length() > 0) {
                UserNameCache.put(UserSpaceActivitySupport1.this, uid, profileName);
                aoharureverie.ocaacrclient.oldchat.models.UserTitleCache.put(UserSpaceActivitySupport1.this, uid, userTitle);
            }
            if (tvName != null) {
                tvName.setText(profileName);
            }
            if (tvUid != null) {
                tvUid.setText(uid == null || uid.length() == 0 ? "" : ("UID: " + uid));
            }
            UserTitleBinder.bindCompact(tvTitleBadge, userTitle);
            applySignature(signature);
            if (ivAvatar != null) {
                ImageLoader.loadAvatar(ivAvatar, avatarUrl);
            }
            applyCover(coverUrl);
            refreshActionButtons();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    protected void refreshActionButtons() {
        if (!isSelf) {
            UserSpaceActionHelper.bindActions((UserSpaceActivity) this, btnPrimary, btnReport, isFriend,
                    profileUid, profileName, profileAvatar, token);
        }
    }

    protected void applySignature(String signature) {
        if (tvSignature == null) {
            return;
        }
        if (signature == null || signature.length() == 0) {
            tvSignature.setText("暂无签名");
            tvSignature.setTextColor(ContextCompat.getColor(UserSpaceActivitySupport1.this, R.color.color_text_secondary));
        } else {
            tvSignature.setText(signature);
            tvSignature.setTextColor(ContextCompat.getColor(UserSpaceActivitySupport1.this, R.color.color_text_primary));
        }
    }

    protected void applyCover(String coverUrl) {
        if (ivCover == null) {
            return;
        }
        if (coverUrl == null || coverUrl.length() == 0) {
            ivCover.setImageDrawable(null);
            ivCover.setBackgroundColor(ContextCompat.getColor(UserSpaceActivitySupport1.this, R.color.color_surface));
        } else {
            ivCover.setBackgroundColor(0x00000000);
            ImageLoader.load(ivCover, coverUrl);
        }
    }
}
