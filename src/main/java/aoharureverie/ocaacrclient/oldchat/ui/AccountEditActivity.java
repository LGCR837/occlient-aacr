package aoharureverie.ocaacrclient.oldchat.ui;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.models.AccountInfo;
import aoharureverie.ocaacrclient.oldchat.models.ServerInfo;
import aoharureverie.ocaacrclient.oldchat.util.AccountStore;

public class AccountEditActivity extends BaseActivity {

    private static final String SERVER_LIST_URL = "http://crmoment.ccwu.cc/oldchataacrlist.php?action=list";
    private static final String CUSTOM_SERVER_LABEL = "自定义服务器";

    private TextView tvTitle;
    private EditText etDisplayName;
    private Spinner spinnerServer;
    private EditText etCustomUrl;
    private EditText etUsername;
    private EditText etPassword;
    private Button btnSave;
    private Button btnDelete;
    private Button btnBack;

    private List<ServerInfo> serverList = new ArrayList<>();
    private List<String> serverNames = new ArrayList<>();
    private String editAccountId;
    private boolean isEditMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_edit);

        tvTitle = findViewByIdCompat(R.id.tvTitle);
        etDisplayName = findViewByIdCompat(R.id.etDisplayName);
        spinnerServer = findViewByIdCompat(R.id.spinnerServer);
        etCustomUrl = findViewByIdCompat(R.id.etCustomUrl);
        etUsername = findViewByIdCompat(R.id.etUsername);
        etPassword = findViewByIdCompat(R.id.etPassword);
        btnSave = findViewByIdCompat(R.id.btnSave);
        btnDelete = findViewByIdCompat(R.id.btnDelete);
        btnBack = findViewByIdCompat(R.id.btnBack);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        editAccountId = getIntent().getStringExtra("account_id");
        isEditMode = editAccountId != null && !editAccountId.isEmpty();

        if (isEditMode) {
            tvTitle.setText("编辑账户");
            btnDelete.setVisibility(View.VISIBLE);
            loadExistingAccount();
        }

        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AccountStore.deleteAccount(AccountEditActivity.this, editAccountId);
                Toast.makeText(AccountEditActivity.this, "账户已删除", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAccount();
            }
        });

        spinnerServer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < serverList.size()) {
                    etCustomUrl.setVisibility(View.GONE);
                } else {
                    etCustomUrl.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        loadServerList();
    }

    private void loadExistingAccount() {
        List<AccountInfo> accounts = AccountStore.loadAll(this);
        for (AccountInfo a : accounts) {
            if (a.id != null && a.id.equals(editAccountId)) {
                etDisplayName.setText(a.displayName);
                etUsername.setText(a.username);
                etPassword.setText(a.password);
                etCustomUrl.setText(a.baseurl);
                break;
            }
        }
    }

    private void loadServerList() {
        serverNames.clear();
        serverNames.add("加载中...");

        ArrayAdapter<String> loadingAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, serverNames);
        loadingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerServer.setAdapter(loadingAdapter);

        new AsyncTask<Void, Void, List<ServerInfo>>() {
            @Override
            protected List<ServerInfo> doInBackground(Void... voids) {
                List<ServerInfo> servers = new ArrayList<>();
                try {
                    String response = fetchWithRedirects(SERVER_LIST_URL, 5);
                    if (response == null) {
                        return servers;
                    }
                    JSONObject json = new JSONObject(response);
                    if (json.optInt("code", -1) != 0) {
                        return servers;
                    }
                    JSONArray data = json.optJSONArray("data");
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.getJSONObject(i);
                            ServerInfo info = new ServerInfo();
                            info.id = item.optInt("id", 0);
                            info.name = item.optString("name", "未知服务器");
                            info.author = item.optString("author", "");
                            info.baseurl = item.optString("baseurl", "");
                            servers.add(info);
                        }
                    }
                } catch (Exception ignored) {
                }
                return servers;
            }

            private String fetchWithRedirects(String urlStr, int maxRedirects) throws Exception {
                for (int i = 0; i <= maxRedirects; i++) {
                    HttpURLConnection conn = null;
                    try {
                        URL url = new URL(urlStr);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(15000);
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("Accept", "application/json");
                        conn.setRequestProperty("Accept-Encoding", "gzip");
                        conn.setInstanceFollowRedirects(false);

                        int code = conn.getResponseCode();
                        if (code == HttpURLConnection.HTTP_MOVED_PERM
                                || code == HttpURLConnection.HTTP_MOVED_TEMP
                                || code == 307 || code == 308) {
                            String location = conn.getHeaderField("Location");
                            if (location != null) {
                                urlStr = location;
                                continue;
                            }
                        }
                        if (code < 200 || code >= 300) {
                            return null;
                        }

                        InputStream is = conn.getInputStream();
                        String encoding = conn.getContentEncoding();
                        if (encoding != null && encoding.toLowerCase().contains("gzip")) {
                            is = new GZIPInputStream(is);
                        }
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();
                        return sb.toString();
                    } catch (javax.net.ssl.SSLException e) {
                        if (urlStr.startsWith("https://")) {
                            urlStr = "http://" + urlStr.substring(8);
                            continue;
                        }
                        throw e;
                    } finally {
                        if (conn != null) {
                            conn.disconnect();
                        }
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(List<ServerInfo> servers) {
                serverList.clear();
                serverList.addAll(servers);
                serverNames.clear();
                for (ServerInfo s : servers) {
                    serverNames.add(s.name);
                }
                serverNames.add(CUSTOM_SERVER_LABEL);

                ArrayAdapter<String> adapter = new ArrayAdapter<>(AccountEditActivity.this,
                        android.R.layout.simple_spinner_item, serverNames);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerServer.setAdapter(adapter);

                if (isEditMode) {
                    selectSpinnerByUrl();
                }
            }
        }.execute();
    }

    private void selectSpinnerByUrl() {
        List<AccountInfo> accounts = AccountStore.loadAll(this);
        for (AccountInfo a : accounts) {
            if (a.id != null && a.id.equals(editAccountId)) {
                for (int i = 0; i < serverList.size(); i++) {
                    if (serverList.get(i).baseurl != null && serverList.get(i).baseurl.equals(a.baseurl)) {
                        spinnerServer.setSelection(i);
                        return;
                    }
                }
                spinnerServer.setSelection(serverList.size());
                etCustomUrl.setVisibility(View.VISIBLE);
                etCustomUrl.setText(a.baseurl);
                return;
            }
        }
    }

    private String getSelectedBaseurl() {
        int pos = spinnerServer.getSelectedItemPosition();
        if (pos < serverList.size()) {
            return serverList.get(pos).baseurl;
        } else {
            return etCustomUrl.getText().toString().trim();
        }
    }

    private void saveAccount() {
        String displayName = etDisplayName.getText().toString().trim();
        String baseurl = getSelectedBaseurl();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (displayName.isEmpty()) {
            Toast.makeText(this, "请输入显示名称", Toast.LENGTH_SHORT).show();
            return;
        }
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入账户名和密码", Toast.LENGTH_SHORT).show();
            return;
        }
        if (baseurl.isEmpty()) {
            Toast.makeText(this, "请选择服务器或输入自定义地址", Toast.LENGTH_SHORT).show();
            return;
        }

        AccountInfo account = new AccountInfo();
        account.id = isEditMode ? editAccountId : null;
        account.displayName = displayName;
        account.baseurl = baseurl;
        account.username = username;
        account.password = password;

        if (isEditMode) {
            AccountStore.updateAccount(this, account);
        } else {
            AccountStore.addAccount(this, account);
        }

        Toast.makeText(this, "账户已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
