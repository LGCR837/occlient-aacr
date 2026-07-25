package aoharureverie.ocaacrclient.oldchat.ui;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
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
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.ServerInfo;

public class ServerListActivity extends BaseActivity {

    private static final String SERVER_LIST_URL = "http://crmoment.ccwu.cc/oldchataacrlist.php?action=list";

    private ListView lvServers;
    private ProgressBar pbLoading;
    private TextView tvEmpty;
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_list);

        lvServers = findViewByIdCompat(R.id.lvServers);
        pbLoading = findViewByIdCompat(R.id.pbLoading);
        tvEmpty = findViewByIdCompat(R.id.tvEmpty);
        btnBack = findViewByIdCompat(R.id.btnBack);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        loadServerList();
    }

    private void loadServerList() {
        pbLoading.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        lvServers.setVisibility(View.GONE);

        new AsyncTask<Void, Void, List<ServerInfo>>() {
            private String errorMessage;

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
                        errorMessage = "服务器返回错误";
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
                } catch (Exception e) {
                    errorMessage = "加载失败: " + e.getMessage();
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
                            errorMessage = "请求失败 (" + code + ")";
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
                        // 老设备不支持新版SSL，尝试将https降级为http
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
                errorMessage = "重定向次数过多";
                return null;
            }

            @Override
            protected void onPostExecute(List<ServerInfo> servers) {
                pbLoading.setVisibility(View.GONE);

                if (servers.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    lvServers.setVisibility(View.GONE);
                    if (errorMessage != null) {
                        Toast.makeText(ServerListActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                    return;
                }

                tvEmpty.setVisibility(View.GONE);
                lvServers.setVisibility(View.VISIBLE);

                ServerListAdapter adapter = new ServerListAdapter(
                        ServerListActivity.this, servers,
                        new ServerListAdapter.OnServerClickListener() {
                            @Override
                            public void onServerClick(ServerInfo server) {
                                selectServer(server);
                            }
                        }
                );
                lvServers.setAdapter(adapter);
            }
        }.execute();
    }

    private void selectServer(ServerInfo server) {
        if (server.baseurl == null || server.baseurl.isEmpty()) {
            Toast.makeText(this, "服务器地址无效", Toast.LENGTH_SHORT).show();
            return;
        }

        HttpUtil.saveBaseUrl(this, server.baseurl);
        Toast.makeText(this, "已切换到: " + server.name, Toast.LENGTH_SHORT).show();
        finish();
    }
}
