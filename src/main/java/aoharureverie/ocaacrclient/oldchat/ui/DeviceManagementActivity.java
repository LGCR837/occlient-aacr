package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.util.DeviceInfoUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class DeviceManagementActivity extends BaseActivity {
    private ListView lvDevices;
    private TextView tvEmpty;
    private DeviceListAdapter adapter;
    private final List<DeviceListAdapter.DeviceItem> items = new ArrayList<>();
    private String token;
    private String currentDeviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_management);

        View btnBack = (View) findViewByIdCompat(R.id.btnDeviceBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        lvDevices = findViewByIdCompat(R.id.lvDevices);
        tvEmpty = findViewByIdCompat(R.id.tvEmpty);

        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        token = prefs.getString("access_token", "");
        currentDeviceId = DeviceInfoUtil.getDeviceId(this);

        adapter = new DeviceListAdapter(this, items, currentDeviceId);
        if (lvDevices != null) {
            lvDevices.setAdapter(adapter);
        }

        loadDevices();
    }

    private void loadDevices() {
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "未登录", Toast.LENGTH_SHORT).show();
            return;
        }
        if (tvEmpty != null) {
            tvEmpty.setVisibility(View.GONE);
        }
        HttpUtil.get("/me/devices", token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                parseDevices(response);
            }

            @Override
            public void onError(int code, String error) {
                if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                    return;
                }
                Toast.makeText(DeviceManagementActivity.this, "加载失败: " + code, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void parseDevices(String response) {
        try {
            JSONObject obj = new JSONObject(response);
            JSONArray arr = obj.optJSONArray("devices");
            items.clear();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject d = arr.getJSONObject(i);
                    DeviceListAdapter.DeviceItem item = new DeviceListAdapter.DeviceItem();
                    item.deviceId = d.optString("device_id", "");
                    item.deviceName = d.optString("device_name", "");
                    item.platform = d.optString("platform", "");
                    item.appVersion = d.optString("app_version", "");
                    item.lastSeen = d.optLong("last_seen", 0);
                    items.add(item);
                }
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            if (tvEmpty != null) {
                tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            }
        } catch (Exception e) {
            Toast.makeText(this, "解析失败", Toast.LENGTH_SHORT).show();
        }
    }
}
