package aoharureverie.ocaacrclient.oldchat.ui;

import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import android.view.View;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.util.UpdateManager;

public class SupportSettingsActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support_settings);

        View btnBack = (View) findViewByIdCompat(R.id.btnSupportSettingsBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        View btnFeedback = (View) findViewByIdCompat(R.id.btnFeedback);
        if (btnFeedback != null) {
            btnFeedback.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new android.content.Intent(SupportSettingsActivity.this, FeedbackActivity.class));
                }
            });
        }

        View btnCheckUpdate = (View) findViewByIdCompat(R.id.btnCheckUpdate);
        if (btnCheckUpdate != null) {
            btnCheckUpdate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(SupportSettingsActivity.this, "正在检查更新...", Toast.LENGTH_SHORT).show();
                    UpdateManager.check(SupportSettingsActivity.this, true);
                }
            });
        }

        View btnPrivacyPolicy = (View) findViewByIdCompat(R.id.btnPrivacyPolicy);
        if (btnPrivacyPolicy != null) {
            btnPrivacyPolicy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new android.content.Intent(SupportSettingsActivity.this, PrivacyPolicyActivity.class));
                }
            });
        }

        View btnAboutApp = (View) findViewByIdCompat(R.id.btnAboutApp);
        if (btnAboutApp != null) {
            btnAboutApp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAboutDialog();
                }
            });
        }
    }

    private void showAboutDialog() {
        String versionName = "未知";
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (info != null && info.versionName != null) {
                versionName = info.versionName;
            }
        } catch (Exception e) {
        }
        new AlertDialog.Builder(this)
                .setTitle("关于旧聊")
                .setMessage("当前版本: " + versionName + "\n\n如果你遇到问题，可在本页进入问题反馈。")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
