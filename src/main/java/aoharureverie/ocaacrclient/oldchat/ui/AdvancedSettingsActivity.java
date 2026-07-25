package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import aoharureverie.ocaacrclient.oldchat.MainActivity;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;

public class AdvancedSettingsActivity extends BaseActivity {

    private EditText etBaseUrl;
    private TextView tvDefaultUrl;
    private Button btnSaveUrl;
    private Button btnResetUrl;
    private Button btnServerList;
    private Button btnSkipLogin;
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_advanced_settings);

        etBaseUrl = findViewByIdCompat(R.id.etBaseUrl);
        tvDefaultUrl = findViewByIdCompat(R.id.tvDefaultUrl);
        btnSaveUrl = findViewByIdCompat(R.id.btnSaveUrl);
        btnResetUrl = findViewByIdCompat(R.id.btnResetUrl);
        btnServerList = findViewByIdCompat(R.id.btnServerList);
        btnSkipLogin = findViewByIdCompat(R.id.btnSkipLogin);
        btnBack = findViewByIdCompat(R.id.btnBack);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        etBaseUrl.setText(HttpUtil.BASE_URL);
        tvDefaultUrl.setText("默认地址: " + HttpUtil.DEFAULT_BASE_URL);

        btnSaveUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = etBaseUrl.getText().toString().trim();
                if (url.isEmpty()) {
                    Toast.makeText(AdvancedSettingsActivity.this,
                            "地址不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    Toast.makeText(AdvancedSettingsActivity.this,
                            "地址需以 http:// 或 https:// 开头", Toast.LENGTH_SHORT).show();
                    return;
                }
                HttpUtil.saveBaseUrl(AdvancedSettingsActivity.this, url);
                Toast.makeText(AdvancedSettingsActivity.this,
                        "已保存并应用", Toast.LENGTH_SHORT).show();
            }
        });

        btnResetUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HttpUtil.resetBaseUrl(AdvancedSettingsActivity.this);
                etBaseUrl.setText(HttpUtil.DEFAULT_BASE_URL);
                Toast.makeText(AdvancedSettingsActivity.this,
                        "已恢复默认地址", Toast.LENGTH_SHORT).show();
            }
        });

        btnServerList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(AdvancedSettingsActivity.this, ServerListActivity.class));
            }
        });

        btnSkipLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AdvancedSettingsActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.putExtra("skip_login", true);
                startActivity(intent);
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (etBaseUrl != null) {
            etBaseUrl.setText(HttpUtil.BASE_URL);
        }
    }
}
