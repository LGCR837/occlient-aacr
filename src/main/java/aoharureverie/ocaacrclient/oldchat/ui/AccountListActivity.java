package aoharureverie.ocaacrclient.oldchat.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import aoharureverie.ocaacrclient.oldchat.MainActivity;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.AccountInfo;
import aoharureverie.ocaacrclient.oldchat.util.AccountDataCleaner;
import aoharureverie.ocaacrclient.oldchat.util.AccountStore;
import aoharureverie.ocaacrclient.oldchat.util.DeviceInfoUtil;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;

import org.json.JSONObject;

import android.content.SharedPreferences;

public class AccountListActivity extends BaseActivity {

    private ListView lvAccounts;
    private TextView tvEmpty;
    private Button btnAddAccount;
    private Button btnBack;
    private AccountListAdapter adapter;
    private List<AccountInfo> accounts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_list);

        lvAccounts = findViewByIdCompat(R.id.lvAccounts);
        tvEmpty = findViewByIdCompat(R.id.tvEmpty);
        btnAddAccount = findViewByIdCompat(R.id.btnAddAccount);
        btnBack = findViewByIdCompat(R.id.btnBack);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnAddAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AccountListActivity.this, AccountEditActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        accounts = AccountStore.loadAll(this);
        if (accounts.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            lvAccounts.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            lvAccounts.setVisibility(View.VISIBLE);
        }

        adapter = new AccountListAdapter(this, accounts,
                new AccountListAdapter.OnAccountClickListener() {
                    @Override
                    public void onAccountClick(AccountInfo account) {
                        loginWithAccount(account);
                    }
                },
                new AccountListAdapter.OnAccountEditListener() {
                    @Override
                    public void onAccountEdit(AccountInfo account) {
                        showDeleteConfirm(account);
                    }
                }
        );
        lvAccounts.setAdapter(adapter);
    }

    private void showDeleteConfirm(final AccountInfo account) {
        new AlertDialog.Builder(this)
                .setTitle("删除账户")
                .setMessage("确定删除账户 \"" + account.displayName + "\"？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        AccountStore.deleteAccount(AccountListActivity.this, account.id);
                        Toast.makeText(AccountListActivity.this, "账户已删除", Toast.LENGTH_SHORT).show();
                        refreshList();
                    }
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("编辑", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(AccountListActivity.this, AccountEditActivity.class);
                        intent.putExtra("account_id", account.id);
                        startActivity(intent);
                    }
                })
                .show();
    }

    private void loginWithAccount(final AccountInfo account) {
        if (account.baseurl == null || account.baseurl.isEmpty()) {
            Toast.makeText(this, "服务器地址无效", Toast.LENGTH_SHORT).show();
            return;
        }
        if (account.username == null || account.username.isEmpty() || account.password == null || account.password.isEmpty()) {
            Toast.makeText(this, "账户名或密码为空", Toast.LENGTH_SHORT).show();
            return;
        }

        HttpUtil.saveBaseUrl(this, account.baseurl);

        try {
            JSONObject json = new JSONObject();
            json.put("identifier", account.username);
            json.put("password", account.password);
            json.put("device_id", DeviceInfoUtil.getDeviceId(this));
            json.put("imei", DeviceInfoUtil.getImei(this));
            json.put("device_name", DeviceInfoUtil.getDeviceName());
            json.put("platform", "android");
            json.put("app_version", DeviceInfoUtil.getAppVersion(this));

            Toast.makeText(this, "正在登录...", Toast.LENGTH_SHORT).show();

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
                            Toast.makeText(AccountListActivity.this, "登录失败: 缺少access_token", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        clearCachesOnAccountSwitch(myUID);
                        saveToken(accessToken, refreshToken, userId, myUID);
                        Toast.makeText(AccountListActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(AccountListActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } catch (Exception e) {
                        Toast.makeText(AccountListActivity.this, "解析失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(int code, String error) {
                    if (code == 403 || (error != null && (error.contains("user_banned") || error.contains("device_banned")))) {
                        Toast.makeText(AccountListActivity.this, "已被封禁", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(AccountListActivity.this, "登录失败 (" + code + ")", Toast.LENGTH_SHORT).show();
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
}
