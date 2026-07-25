package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.api.WSManager;
import aoharureverie.ocaacrclient.oldchat.service.MessageService;
import aoharureverie.ocaacrclient.oldchat.util.AccountDataCleaner;

import org.json.JSONObject;

public class AccountManagementActivity extends BaseActivity {
    private String token;
    private View btnDeleteAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_management);

        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        token = prefs.getString("access_token", "");

        View btnBack = (View) findViewByIdCompat(R.id.btnAccountBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        View btnChangePassword = (View) findViewByIdCompat(R.id.btnChangePassword);
        if (btnChangePassword != null) {
            btnChangePassword.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(AccountManagementActivity.this, ChangePasswordActivity.class));
                }
            });
        }

        View btnDeviceManagement = (View) findViewByIdCompat(R.id.btnDeviceManagement);
        if (btnDeviceManagement != null) {
            btnDeviceManagement.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(AccountManagementActivity.this, DeviceManagementActivity.class));
                }
            });
        }

        btnDeleteAccount = (View) findViewByIdCompat(R.id.btnDeleteAccount);
        if (btnDeleteAccount != null) {
            btnDeleteAccount.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDeleteConfirmDialog();
                }
            });
        }
    }

    private void showDeleteConfirmDialog() {
        new AlertDialog.Builder(this, R.style.AppDialogTheme)
                .setTitle("注销账号")
                .setMessage("注销后账号与聊天数据将被删除且不可恢复，是否继续？")
                .setPositiveButton("继续", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        showPasswordConfirmDialog();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showPasswordConfirmDialog() {
        final EditText input = new EditText(this);
        input.setHint("请输入当前密码");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int pad = dp(16);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this, R.style.AppDialogTheme)
                .setTitle("确认密码")
                .setView(input)
                .setPositiveButton("确认注销", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        deleteAccount(input.getText().toString().trim());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteAccount(String password) {
        if (password == null || password.length() == 0) {
            Toast.makeText(this, "请输入当前密码", Toast.LENGTH_SHORT).show();
            return;
        }
        if (token == null || token.length() == 0) {
            Toast.makeText(this, "登录已失效，请重新登录", Toast.LENGTH_SHORT).show();
            return;
        }
        if (btnDeleteAccount != null) {
            btnDeleteAccount.setEnabled(false);
        }
        try {
            JSONObject json = new JSONObject();
            json.put("password", password);
            HttpUtil.post("/me/delete", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    Toast.makeText(AccountManagementActivity.this, "账号已注销", Toast.LENGTH_SHORT).show();
                    forceLogout();
                }

                @Override
                public void onError(int code, String error) {
                    if (btnDeleteAccount != null) {
                        btnDeleteAccount.setEnabled(true);
                    }
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    if (code == 401) {
                        Toast.makeText(AccountManagementActivity.this, "密码错误", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(AccountManagementActivity.this, "注销失败(" + code + ")", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (Exception e) {
            if (btnDeleteAccount != null) {
                btnDeleteAccount.setEnabled(true);
            }
            Toast.makeText(this, "注销失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void forceLogout() {
        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        prefs.edit().clear().apply();
        AccountDataCleaner.clearAll(this);
        WSManager.getInstance().stop();
        MessageService.stop(this);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }
}
