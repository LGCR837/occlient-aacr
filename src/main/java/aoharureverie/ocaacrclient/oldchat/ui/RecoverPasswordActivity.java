package aoharureverie.ocaacrclient.oldchat.ui;

import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import org.json.JSONObject;
import java.util.Locale;

public class RecoverPasswordActivity extends BaseActivity {

    private EditText etUsername;
    private EditText etEmail;
    private EditText etCaptcha;
    private EditText etEmailCode;
    private EditText etNewPassword;
    private ImageView ivCaptcha;
    private Button btnSendRecoverCode;
    private Button btnResetPassword;
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
        setContentView(R.layout.activity_recover_password);

        etUsername = findViewByIdCompat(R.id.etUsername);
        etEmail = findViewByIdCompat(R.id.etEmail);
        etCaptcha = findViewByIdCompat(R.id.etCaptcha);
        etEmailCode = findViewByIdCompat(R.id.etEmailCode);
        etNewPassword = findViewByIdCompat(R.id.etNewPassword);
        ivCaptcha = findViewByIdCompat(R.id.ivCaptcha);
        btnSendRecoverCode = findViewByIdCompat(R.id.btnSendRecoverCode);
        btnResetPassword = findViewByIdCompat(R.id.btnResetPassword);
        View btnBack = (View) findViewByIdCompat(R.id.btnRecoverBack);
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
        if (btnSendRecoverCode != null) {
            btnSendRecoverCode.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendRecoverCode();
                }
            });
        }
        if (btnResetPassword != null) {
            btnResetPassword.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    resetPassword();
                }
            });
        }
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

    private void loadCaptcha(boolean fromUser) {
        long now = System.currentTimeMillis();
        if (fromUser && lastCaptchaAt > 0 && now - lastCaptchaAt < 5000) {
            Toast.makeText(this, R.string.captcha_too_fast, Toast.LENGTH_SHORT).show();
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

    private void sendRecoverCode() {
        if (isInCooldown()) {
            Toast.makeText(this, "请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        String username = etUsername != null ? etUsername.getText().toString().trim() : "";
        String email = etEmail != null ? etEmail.getText().toString().trim() : "";
        String captcha = etCaptcha != null ? etCaptcha.getText().toString().trim() : "";
        if (username.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, R.string.error_required, Toast.LENGTH_SHORT).show();
            return;
        }
        String normalizedUsername = username.toLowerCase(Locale.US);
        String normalizedEmail = email.toLowerCase(Locale.US);
        if (!isValidUsername(normalizedUsername)) {
            Toast.makeText(this, R.string.error_invalid_username, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isValidEmail(normalizedEmail)) {
            Toast.makeText(this, R.string.error_invalid_email, Toast.LENGTH_SHORT).show();
            return;
        }
        if (captcha.isEmpty() || captchaId == null || captchaId.isEmpty()) {
            Toast.makeText(this, R.string.error_invalid_captcha, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("email", normalizedEmail);
            json.put("captcha_id", captchaId);
            json.put("captcha_code", captcha);
            json.put("username", normalizedUsername);
            HttpUtil.post("/auth/email/send", json, null, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    Toast.makeText(RecoverPasswordActivity.this, R.string.recover_code_sent, Toast.LENGTH_SHORT).show();
                    startCooldown(120);
                }

                @Override
                public void onError(int code, String error) {
                    if (error != null && error.contains("invalid_captcha")) {
                        Toast.makeText(RecoverPasswordActivity.this, R.string.error_invalid_captcha, Toast.LENGTH_SHORT).show();
                    } else if (error != null && error.contains("invalid_account")) {
                        Toast.makeText(RecoverPasswordActivity.this, R.string.recover_account_mismatch, Toast.LENGTH_SHORT).show();
                    } else if (error != null && error.contains("email_cooldown")) {
                        Toast.makeText(RecoverPasswordActivity.this, "发送太频繁，请稍后再试", Toast.LENGTH_SHORT).show();
                        startCooldown(120);
                    } else {
                        Toast.makeText(RecoverPasswordActivity.this, "发送失败 (" + code + ")", Toast.LENGTH_SHORT).show();
                    }
                    loadCaptcha(false);
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "构造请求失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetPassword() {
        String username = etUsername != null ? etUsername.getText().toString().trim() : "";
        String email = etEmail != null ? etEmail.getText().toString().trim() : "";
        String emailCode = etEmailCode != null ? etEmailCode.getText().toString().trim() : "";
        String newPassword = etNewPassword != null ? etNewPassword.getText().toString().trim() : "";
        if (username.isEmpty() || email.isEmpty() || emailCode.isEmpty() || newPassword.isEmpty()) {
            Toast.makeText(this, R.string.error_required, Toast.LENGTH_SHORT).show();
            return;
        }
        String normalizedUsername = username.toLowerCase(Locale.US);
        String normalizedEmail = email.toLowerCase(Locale.US);
        if (!isValidUsername(normalizedUsername)) {
            Toast.makeText(this, R.string.error_invalid_username, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isValidEmail(normalizedEmail)) {
            Toast.makeText(this, R.string.error_invalid_email, Toast.LENGTH_SHORT).show();
            return;
        }
        if (newPassword.length() < 8) {
            Toast.makeText(this, R.string.error_password_too_short, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("username", normalizedUsername);
            json.put("email", normalizedEmail);
            json.put("email_code", emailCode);
            json.put("new_password", newPassword);
            HttpUtil.post("/auth/password/reset", json, null, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    Toast.makeText(RecoverPasswordActivity.this, R.string.recover_reset_done, Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onError(int code, String error) {
                    if (error != null && error.contains("invalid_email_code")) {
                        Toast.makeText(RecoverPasswordActivity.this, R.string.error_invalid_email_code, Toast.LENGTH_SHORT).show();
                    } else if (error != null && error.contains("invalid_account")) {
                        Toast.makeText(RecoverPasswordActivity.this, R.string.recover_account_mismatch, Toast.LENGTH_SHORT).show();
                    } else if (error != null && error.contains("invalid_password")) {
                        Toast.makeText(RecoverPasswordActivity.this, R.string.error_password_too_short, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(RecoverPasswordActivity.this, "重置失败 (" + code + ")", Toast.LENGTH_SHORT).show();
                    }
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
        if (btnSendRecoverCode == null) {
            return;
        }
        long remaining = cooldownUntil - System.currentTimeMillis();
        if (remaining <= 0) {
            cooldownUntil = 0;
            btnSendRecoverCode.setEnabled(true);
            btnSendRecoverCode.setText(getString(R.string.recover_send_code));
            handler.removeCallbacks(cooldownTick);
            return;
        }
        int seconds = (int) (remaining / 1000L);
        btnSendRecoverCode.setEnabled(false);
        btnSendRecoverCode.setText("重新发送(" + seconds + "s)");
        handler.removeCallbacks(cooldownTick);
        handler.postDelayed(cooldownTick, 1000);
    }

    private boolean isValidEmail(String email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
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
}
