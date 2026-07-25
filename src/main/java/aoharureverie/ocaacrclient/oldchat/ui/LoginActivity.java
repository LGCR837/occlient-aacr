package aoharureverie.ocaacrclient.oldchat.ui;

import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.MainActivity;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.AccountInfo;
import aoharureverie.ocaacrclient.oldchat.util.AccountDataCleaner;
import aoharureverie.ocaacrclient.oldchat.util.AccountStore;
import aoharureverie.ocaacrclient.oldchat.util.DeviceInfoUtil;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;
import aoharureverie.ocaacrclient.oldchat.util.PrivacyPolicyHelper;
import aoharureverie.ocaacrclient.oldchat.util.SettingsPrefs;
import aoharureverie.ocaacrclient.oldchat.util.UpdateManager;
import org.json.JSONObject;
import java.util.List;

public class LoginActivity extends BaseActivity {

    private EditText etUsername, etPassword;
    private Button btnLogin;
    private TextView tvRegister;
    private Button btnRecoverPassword;
    private android.widget.CheckBox cbPrivacyAgree;
    private TextView tvPrivacyPolicy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewByIdCompat(R.id.etUsername);
        etPassword = findViewByIdCompat(R.id.etPassword);
        btnLogin = findViewByIdCompat(R.id.btnLogin);
        tvRegister = findViewByIdCompat(R.id.tvRegister);
        btnRecoverPassword = findViewByIdCompat(R.id.btnRecoverPassword);
        cbPrivacyAgree = findViewByIdCompat(R.id.cbPrivacyAgree);
        tvPrivacyPolicy = findViewByIdCompat(R.id.tvPrivacyPolicy);

        ImageView ivLogo = findViewByIdCompat(R.id.ivLogo);
        if (ivLogo != null) {
            ivLogo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(LoginActivity.this, AccountListActivity.class));
                }
            });
            ivLogo.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    startActivity(new Intent(LoginActivity.this, AdvancedSettingsActivity.class));
                    return true;
                }
            });
        }

        if (cbPrivacyAgree != null) {
            cbPrivacyAgree.setChecked(SettingsPrefs.isPrivacyAgreed(this));
            cbPrivacyAgree.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!SettingsPrefs.isPrivacyAgreed(LoginActivity.this)) {
                        cbPrivacyAgree.setChecked(false);
                        showPrivacyPolicy();
                    }
                }
            });
        }
        if (tvPrivacyPolicy != null) {
            tvPrivacyPolicy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPrivacyPolicy();
                }
            });
        }

        UpdateManager.check(this);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = etUsername.getText().toString();
                String password = etPassword.getText().toString();
                if (!ensurePrivacyAgreed()) {
                    return;
                }
                login(username, password);
            }
        });

        tvRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            }
        });

        if (btnRecoverPassword != null) {
            btnRecoverPassword.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(LoginActivity.this, RecoverPasswordActivity.class));
                }
            });
        }

        Button btnAccountList = findViewByIdCompat(R.id.btnAccountList);
        if (btnAccountList != null) {
            btnAccountList.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(LoginActivity.this, AccountListActivity.class));
                }
            });
        }

        Button btnServerList = findViewByIdCompat(R.id.btnServerList);
        if (btnServerList != null) {
            btnServerList.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(LoginActivity.this, ServerListActivity.class));
                }
            });
        }

        loadQuickAccounts();
    }

    private void loadQuickAccounts() {
        LinearLayout container = findViewByIdCompat(R.id.quickAccountsContainer);
        View divider = findViewByIdCompat(R.id.quickAccountsDivider);
        if (container == null) return;

        List<AccountInfo> accounts = AccountStore.loadAll(this);
        if (accounts.isEmpty()) {
            container.setVisibility(View.GONE);
            if (divider != null) divider.setVisibility(View.GONE);
            return;
        }

        container.setVisibility(View.VISIBLE);
        if (divider != null) divider.setVisibility(View.VISIBLE);
        container.removeAllViews();

        for (int i = 0; i < accounts.size(); i++) {
            final AccountInfo account = accounts.get(i);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setCornerRadius(dpToPx(4));
            cardBg.setColor(Color.parseColor("#FFFFFF"));
            cardBg.setStroke(1, Color.parseColor("#EEEEEE"));
            card.setBackground(cardBg);
            card.setElevation(0);
            card.setClickable(true);
            card.setFocusable(true);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = dpToPx(8);
            card.setLayoutParams(cardParams);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textCol.setLayoutParams(textParams);

            TextView tvName = new TextView(this);
            tvName.setText(account.displayName != null && !account.displayName.isEmpty()
                    ? account.displayName : "未命名账户");
            tvName.setTextSize(16);
            tvName.setTextColor(Color.parseColor("#1F1F1F"));
            tvName.setTypeface(Typeface.DEFAULT_BOLD);
            tvName.setMaxLines(1);
            textCol.addView(tvName);

            TextView tvUser = new TextView(this);
            tvUser.setText(account.username != null ? account.username : "");
            tvUser.setTextSize(12);
            tvUser.setTextColor(Color.parseColor("#7A7E83"));
            tvUser.setMaxLines(1);
            LinearLayout.LayoutParams userParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            userParams.topMargin = dpToPx(3);
            tvUser.setLayoutParams(userParams);
            textCol.addView(tvUser);

            TextView tvUrl = new TextView(this);
            tvUrl.setText(account.baseurl != null ? account.baseurl : "");
            tvUrl.setTextSize(11);
            tvUrl.setTextColor(Color.parseColor("#7A7E83"));
            tvUrl.setMaxLines(1);
            LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            urlParams.topMargin = dpToPx(3);
            tvUrl.setLayoutParams(urlParams);
            textCol.addView(tvUrl);

            card.addView(textCol);

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!ensurePrivacyAgreed()) return;
                    loginWithAccount(account);
                }
            });

            container.addView(card);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void loginWithAccount(final AccountInfo account) {
        if (account.baseurl == null || account.baseurl.isEmpty()
                || account.username == null || account.username.isEmpty()
                || account.password == null || account.password.isEmpty()) {
            Toast.makeText(this, "账户信息不完整", Toast.LENGTH_SHORT).show();
            return;
        }
        HttpUtil.saveBaseUrl(this, account.baseurl);
        login(account.username, account.password);
    }

    private void login(String username, String password) {
        final String usernameFinal = username;
        final String passwordFinal = password;
        try {
            JSONObject json = new JSONObject();
            json.put("identifier", username);
            json.put("password", password);
            json.put("device_id", DeviceInfoUtil.getDeviceId(this));
            json.put("imei", DeviceInfoUtil.getImei(this));
            json.put("device_name", DeviceInfoUtil.getDeviceName());
            json.put("platform", "android");
            json.put("app_version", DeviceInfoUtil.getAppVersion(this));

            HttpUtil.post("/auth/login", json, null, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    try {
                        JSONObject resp = new JSONObject(response);
                        String accessToken = resp.optString("access_token", "");
                        String refreshToken = resp.optString("refresh_token", "");
                        JSONObject user = resp.optJSONObject("user");
                        String userId = user != null ? user.optString("id", "") : "";
                        String myUID = user != null ? user.optString("uid", "") : "";
                        if (accessToken.isEmpty()) {
                            onError(-1, "missing access_token");
                            return;
                        }

                        clearCachesOnAccountSwitch(myUID);
                        saveToken(accessToken, refreshToken, userId, myUID);
                        saveCredentials(usernameFinal, passwordFinal);
                        Toast.makeText(LoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } catch (Exception e) {
                        onError(-1, "解析失败: " + e.getMessage());
                    }
                }

                @Override
                public void onError(int code, String error) {
                    if (code == 403 || (error != null && (error.contains("user_banned") || error.contains("device_banned")))) {
                        Toast.makeText(LoginActivity.this, "已被封禁", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(LoginActivity.this, "登录失败 (" + code + ")", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "构造请求失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToken(String accessToken, String refreshToken, String userId, String myUID) {
        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        prefs.edit().putString("access_token", accessToken)
                   .putString("refresh_token", refreshToken)
                   .putString("user_id", userId)
                   .putString("my_uid", myUID)
                   .apply();
    }

    private void clearCachesOnAccountSwitch(String newUid) {
        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        String oldUid = prefs.getString("my_uid", "");
        if (!oldUid.isEmpty() && newUid != null && !newUid.isEmpty() && !oldUid.equals(newUid)) {
            AccountDataCleaner.clearAll(this);
            MyUidStore.clearUidAliases(this);
        }
    }

    private void saveCredentials(String username, String password) {
        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        prefs.edit().putString("saved_username", username)
                   .putString("saved_password", password)
                   .apply();
    }

    private boolean ensurePrivacyAgreed() {
        if (cbPrivacyAgree != null && cbPrivacyAgree.isChecked()) {
            SettingsPrefs.setPrivacyAgreed(this, true);
            return true;
        }
        Toast.makeText(this, R.string.privacy_policy_required, Toast.LENGTH_SHORT).show();
        showPrivacyPolicy();
        return false;
    }

    private void showPrivacyPolicy() {
        PrivacyPolicyHelper.showPolicyDialog(this, new Runnable() {
            @Override
            public void run() {
                if (cbPrivacyAgree != null) {
                    cbPrivacyAgree.setChecked(true);
                }
                SettingsPrefs.setPrivacyAgreed(LoginActivity.this, true);
            }
        });
    }
}
