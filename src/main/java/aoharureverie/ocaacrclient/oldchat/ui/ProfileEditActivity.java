package aoharureverie.ocaacrclient.oldchat.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.InputFilter;
import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.FriendNameResolver;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;
import aoharureverie.ocaacrclient.oldchat.util.ImageCompressUtil;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;
import org.json.JSONObject;

public class ProfileEditActivity extends BaseActivity {
    private static final int REQ_PICK_AVATAR = 1001;
    private static final int REQ_STORAGE = 1002;
    private static final int MAX_DISPLAY_NAME_LENGTH = 15;

    private ImageView ivAvatar;
    private TextView tvUser;
    private TextView tvEmail;
    private EditText etDisplayName;
    private EditText etUID;
    private View btnSaveProfile;
    private View btnChangeUID;
    private View btnChangeAvatar;
    private String token;
    private String currentAvatarUrl;
    private String currentDisplayName;
    private String currentUid;
    private String currentUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_edit);

        ivAvatar = findViewByIdCompat(R.id.ivAvatar);
        tvUser = findViewByIdCompat(R.id.tvMyUsername);
        tvEmail = findViewByIdCompat(R.id.tvMyEmail);
        etDisplayName = findViewByIdCompat(R.id.etDisplayName);
        etUID = findViewByIdCompat(R.id.etUID);
        etDisplayName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_DISPLAY_NAME_LENGTH)});
        etDisplayName.setSingleLine(true);
        btnSaveProfile = findViewByIdCompat(R.id.btnSaveProfile);
        btnChangeUID = findViewByIdCompat(R.id.btnChangeUID);
        btnChangeAvatar = findViewByIdCompat(R.id.btnChangeAvatar);
        View btnBack = (View) findViewByIdCompat(R.id.btnProfileEditBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        token = prefs.getString("access_token", "");

        btnChangeAvatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickAvatar();
            }
        });
        btnSaveProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateProfile();
            }
        });
        btnChangeUID.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateUID();
            }
        });

        loadProfile();
    }

    private void loadProfile() {
        HttpUtil.get("/me", token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject obj = new JSONObject(response);
                    String uid = obj.optString("uid", "");
                    String username = obj.optString("username", "");
                    String displayName = normalizeDisplayName(obj.optString("display_name", ""));
                    currentAvatarUrl = obj.optString("avatar_url", "");
                    currentDisplayName = displayName;
                    currentUid = uid;
                    currentUsername = username;
                    String primary = displayName;
                    if (primary == null || primary.isEmpty()) {
                        primary = uid;
                    }
                    tvUser.setText(primary == null ? "" : primary);
                    updateSecondaryText(uid, username);
                    etDisplayName.setText(displayName);
                    if (uid != null && !uid.isEmpty()) {
                        etUID.setText(uid);
                    }
                    ImageLoader.loadAvatar(ivAvatar, currentAvatarUrl);
                } catch (Exception e) {
                    Toast.makeText(ProfileEditActivity.this, "加载资料失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(int code, String error) {
                if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                    return;
                }
                Toast.makeText(ProfileEditActivity.this, "加载资料失败: " + code, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateProfile() {
        String displayName = normalizeDisplayName(etDisplayName.getText().toString());
        if (displayName.isEmpty()) {
            displayName = normalizeDisplayName(currentDisplayName);
        }
        if (displayName == null || displayName.isEmpty()) {
            Toast.makeText(ProfileEditActivity.this, "昵称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        final String newDisplayName = displayName;
        try {
            JSONObject json = new JSONObject();
            json.put("display_name", newDisplayName);
            json.put("avatar_url", currentAvatarUrl == null ? "" : currentAvatarUrl);
            HttpUtil.post("/me/profile", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    currentDisplayName = newDisplayName;
                    updatePrimaryText();
                    Toast.makeText(ProfileEditActivity.this, "保存成功", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(int code, String error) {
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    Toast.makeText(ProfileEditActivity.this, "保存失败: " + code, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(ProfileEditActivity.this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePrimaryText() {
        String primary = currentDisplayName;
        if (primary == null || primary.isEmpty()) {
            primary = currentUid;
        }
        tvUser.setText(primary == null ? "" : primary);
    }

    private void updateSecondaryText(String uid, String username) {
        StringBuilder secondary = new StringBuilder();
        if (uid != null && !uid.isEmpty()) {
            secondary.append("UID: ").append(uid);
        }
        if (username != null && !username.isEmpty()) {
            if (secondary.length() > 0) {
                secondary.append("  ");
            }
            secondary.append("用户名: ").append(username);
        }
        tvEmail.setText(secondary.toString());
    }

    private void updateUID() {
        String newUID = etUID.getText().toString().trim().toUpperCase();
        if (newUID.isEmpty()) {
            Toast.makeText(ProfileEditActivity.this, "请输入新的UID", Toast.LENGTH_SHORT).show();
            return;
        }
        final String newUidFinal = newUID;
        try {
            JSONObject json = new JSONObject();
            json.put("uid", newUID);
            HttpUtil.post("/me/uid", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
                    String oldUid = prefs.getString("my_uid", "");
                    MyUidStore.recordUidAlias(ProfileEditActivity.this, oldUid, newUidFinal);
                    prefs.edit().putString("my_uid", newUidFinal).apply();
                    currentUid = newUidFinal;
                    updatePrimaryText();
                    updateSecondaryText(currentUid, currentUsername);
                    Toast.makeText(ProfileEditActivity.this, "UID已更新", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(int code, String error) {
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    Toast.makeText(ProfileEditActivity.this, "更新失败: " + code, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(ProfileEditActivity.this, "更新失败", Toast.LENGTH_SHORT).show();
        }
    }

    private String normalizeDisplayName(String value) {
        return FriendNameResolver.normalizeDisplayName(value, MAX_DISPLAY_NAME_LENGTH);
    }

    private void pickAvatar() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "选择头像"), REQ_PICK_AVATAR);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickAvatar();
            } else {
                Toast.makeText(ProfileEditActivity.this, "未授权读取存储", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_AVATAR && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                uploadAvatar(uri);
            }
        }
    }

    private void uploadAvatar(Uri uri) {
        try {
            byte[] data = ImageCompressUtil.compressToBytes(getContentResolver(), uri, 512, 400 * 1024);
            HttpUtil.postMultipart("/me/avatar", data, "avatar.jpg", "image/jpeg", token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    try {
                        JSONObject obj = new JSONObject(response);
                        currentAvatarUrl = obj.optString("avatar_url", "");
                        ImageLoader.loadAvatar(ivAvatar, currentAvatarUrl);
                        Toast.makeText(ProfileEditActivity.this, "头像已更新", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(ProfileEditActivity.this, "头像更新失败", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(int code, String error) {
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    Toast.makeText(ProfileEditActivity.this, "头像上传失败: " + code, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(ProfileEditActivity.this, "头像上传失败", Toast.LENGTH_SHORT).show();
        }
    }
}
