package aoharureverie.ocaacrclient.oldchat.ui;

import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import android.util.Patterns;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.util.DeviceInfoUtil;
import aoharureverie.ocaacrclient.oldchat.util.PrivacyPolicyHelper;
import aoharureverie.ocaacrclient.oldchat.util.SettingsPrefs;
import org.json.JSONObject;
import java.util.Locale;

public class RegisterActivity extends BaseActivity {

    private EditText etUsername, etEmail, etPassword, etCaptcha, etEmailCode;
    private Button btnRegister, btnSendEmailCode;
    private ImageView ivCaptcha;
    private android.widget.CheckBox cbPrivacyAgree;
    private android.widget.TextView tvPrivacyPolicy;
    private String captchaId;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable cooldownTick = new Runnable() {
        @Override
        public void run() {
            updateCooldownLabel();
        }
    };
    private boolean captchaLoading = false;
    private long cooldownUntil = 0;
    private long lastCaptchaAt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etUsername = findViewByIdCompat(R.id.etUsername);
        etEmail = findViewByIdCompat(R.id.etEmail);
        etPassword = findViewByIdCompat(R.id.etPassword);
        etCaptcha = findViewByIdCompat(R.id.etCaptcha);
        etEmailCode = findViewByIdCompat(R.id.etEmailCode);
        ivCaptcha = findViewByIdCompat(R.id.ivCaptcha);
        btnSendEmailCode = findViewByIdCompat(R.id.btnSendEmailCode);
        btnRegister = findViewByIdCompat(R.id.btnRegister);
        cbPrivacyAgree = findViewByIdCompat(R.id.cbPrivacyAgree);
        tvPrivacyPolicy = findViewByIdCompat(R.id.tvPrivacyPolicy);

        if (cbPrivacyAgree != null) {
            cbPrivacyAgree.setChecked(SettingsPrefs.isPrivacyAgreed(this));
            cbPrivacyAgree.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!SettingsPrefs.isPrivacyAgreed(RegisterActivity.this)) {
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
        View btnBack = (View) findViewByIdCompat(R.id.btnRegisterBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        if (ivCaptcha != null) {
            ivCaptcha.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadCaptcha(true);
                }
            });
        }
        if (btnSendEmailCode != null) {
            btnSendEmailCode.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendEmailCode();
                }
            });
        }

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = etUsername.getText().toString().trim();
                String email = etEmail.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                String captcha = etCaptcha != null ? etCaptcha.getText().toString().trim() : "";
                String emailCode = etEmailCode != null ? etEmailCode.getText().toString().trim() : "";

                if (username.isEmpty() || password.isEmpty() || email.isEmpty()) {
                    Toast.makeText(RegisterActivity.this, R.string.error_required, Toast.LENGTH_SHORT).show();
                    return;
                }

                String normalizedUsername = username.toLowerCase(Locale.US);
                String normalizedEmail = email.toLowerCase(Locale.US);

                if (!isValidEmail(normalizedEmail)) {
                    Toast.makeText(RegisterActivity.this, R.string.error_invalid_email, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!isQQEmail(normalizedEmail)) {
                    Toast.makeText(RegisterActivity.this, R.string.error_qq_email_only, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!isValidUsername(normalizedUsername)) {
                    Toast.makeText(RegisterActivity.this, R.string.error_invalid_username, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (password.length() < 8) {
                    Toast.makeText(RegisterActivity.this, R.string.error_password_too_short, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (captcha.isEmpty() || captchaId == null || captchaId.isEmpty()) {
                    Toast.makeText(RegisterActivity.this, R.string.error_invalid_captcha, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (emailCode.isEmpty()) {
                    Toast.makeText(RegisterActivity.this, R.string.error_invalid_email_code, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!ensurePrivacyAgreed()) {
                    return;
                }

                performRegister(normalizedUsername, password, normalizedEmail, emailCode);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCaptcha(false);
        updateCooldownLabel();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(cooldownTick);
    }

    private void performRegister(String username, String password, String email, String emailCode) {
        try {
            JSONObject json = new JSONObject();
            json.put("username", username);
            json.put("password", password);
            json.put("email", email);
            json.put("email_code", emailCode);
            json.put("device_id", DeviceInfoUtil.getDeviceId(this));
            json.put("imei", DeviceInfoUtil.getImei(this));
            json.put("device_name", DeviceInfoUtil.getDeviceName());
            json.put("platform", "android");
            json.put("app_version", DeviceInfoUtil.getAppVersion(this));

            HttpUtil.post("/auth/register", json, null, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    Toast.makeText(RegisterActivity.this, "注册成功", Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onError(int code, String error) {
                    if (code == 409) {
                        Toast.makeText(RegisterActivity.this, "用户名或邮箱已占用", Toast.LENGTH_SHORT).show();
                    } else if (code == 403 || (error != null && error.contains("device_banned"))) {
                        Toast.makeText(RegisterActivity.this, "设备已被封禁", Toast.LENGTH_SHORT).show();
                    } else if (error != null && error.contains("invalid_email_code")) {
                        Toast.makeText(RegisterActivity.this, R.string.error_invalid_email_code, Toast.LENGTH_SHORT).show();
                    } else if (error != null && error.contains("invalid_email_domain")) {
                        Toast.makeText(RegisterActivity.this, R.string.error_qq_email_only, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(RegisterActivity.this, "注册失败 (" + code + ")", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "构造请求失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadCaptcha(boolean fromUser) {
        long now = System.currentTimeMillis();
        if (fromUser && lastCaptchaAt > 0 && now-lastCaptchaAt < 5000) {
            Toast.makeText(this, "请稍后再刷新", Toast.LENGTH_SHORT).show();
            return;
        }
        if (captchaLoading) {
            return;
        }
        captchaLoading = true;
        lastCaptchaAt = now;
        HttpUtil.get("/auth/captcha", null, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject obj = new JSONObject(response);
                    captchaId = obj.optString("captcha_id", "");
                    String base64 = obj.optString("image_base64", "");
                    if (base64.isEmpty()) {
                        captchaLoading = false;
                        return;
                    }
                    byte[] data = Base64.decode(base64, Base64.NO_WRAP);
                    Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (ivCaptcha != null && bmp != null) {
                        ivCaptcha.setImageBitmap(bmp);
                    }
                    if (etCaptcha != null) {
                        etCaptcha.setText("");
                    }
                } catch (Exception e) {
                }
                captchaLoading = false;
            }

            @Override
            public void onError(int code, String error) {
                captchaLoading = false;
            }
        });
    }

    private void sendEmailCode() {
        if (isInCooldown()) {
            Toast.makeText(this, "请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ensurePrivacyAgreed()) {
            return;
        }
        String email = etEmail != null ? etEmail.getText().toString().trim() : "";
        String captcha = etCaptcha != null ? etCaptcha.getText().toString().trim() : "";
        if (email.isEmpty() || !isValidEmail(email)) {
            Toast.makeText(this, R.string.error_invalid_email, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isQQEmail(email)) {
            Toast.makeText(this, R.string.error_qq_email_only, Toast.LENGTH_SHORT).show();
            return;
        }
        if (captcha.isEmpty() || captchaId == null || captchaId.isEmpty()) {
            Toast.makeText(this, R.string.error_invalid_captcha, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("captcha_id", captchaId);
            json.put("captcha_code", captcha);
            HttpUtil.post("/auth/email/send", json, null, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    Toast.makeText(RegisterActivity.this, "验证码已发送", Toast.LENGTH_SHORT).show();
                    startCooldown(120);
                }

                @Override
                public void onError(int code, String error) {
                    if (error != null && error.contains("invalid_captcha")) {
                        Toast.makeText(RegisterActivity.this, R.string.error_invalid_captcha, Toast.LENGTH_SHORT).show();
                    } else if (error != null && error.contains("email_cooldown")) {
                        Toast.makeText(RegisterActivity.this, "发送太频繁，请稍后再试", Toast.LENGTH_SHORT).show();
                        startCooldown(120);
                    } else if (error != null && error.contains("invalid_email_domain")) {
                        Toast.makeText(RegisterActivity.this, R.string.error_qq_email_only, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(RegisterActivity.this, "发送失败 (" + code + ")", Toast.LENGTH_SHORT).show();
                    }
                    loadCaptcha(false);
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "构造请求失败", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isInCooldown() {
        return cooldownUntil > System.currentTimeMillis();
    }

    private void startCooldown(int seconds) {
        cooldownUntil = System.currentTimeMillis() + seconds * 1000L;
        updateCooldownLabel();
    }

    private void updateCooldownLabel() {
        if (btnSendEmailCode == null) {
            return;
        }
        long remaining = cooldownUntil - System.currentTimeMillis();
        if (remaining <= 0) {
            cooldownUntil = 0;
            btnSendEmailCode.setEnabled(true);
            btnSendEmailCode.setText(getString(R.string.send_email_code));
            handler.removeCallbacks(cooldownTick);
            return;
        }
        int seconds = (int) (remaining / 1000L);
        btnSendEmailCode.setEnabled(false);
        btnSendEmailCode.setText("重新发送(" + seconds + "s)");
        handler.removeCallbacks(cooldownTick);
        handler.postDelayed(cooldownTick, 1000);
    }

    private boolean isValidEmail(String email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isQQEmail(String email) {
        if (email == null) {
            return false;
        }
        String e = email.toLowerCase(Locale.US).trim();
        return e.endsWith("@qq.com") || e.endsWith("@vip.qq.com");
    }

    private boolean isValidUsername(String username) {
        if (username.length() < 3 || username.length() > 24) {
            return false;
        }
        for (int i = 0; i < username.length(); i++) {
            char c = username.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                continue;
            }
            return false;
        }
        return true;
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
                SettingsPrefs.setPrivacyAgreed(RegisterActivity.this, true);
            }
        });
    }
}
