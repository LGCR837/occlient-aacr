package aoharureverie.ocaacrclient.oldchat.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.support.v4.content.FileProvider;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.lang.ref.WeakReference;

public class UpdateManager {
    private static final String PREFS = "update_prefs";
    private static final String KEY_SKIP_VERSION = "skip_version";
    private static final String UPDATE_JSON = "update.json";

    private static class UpdateInfo {
        int versionCode;
        String versionName;
        String apkName;
        String notes;
        boolean force;
    }

    public static void check(final Activity activity) {
        check(activity, false);
    }

    public static void check(final Activity activity, final boolean forceShow) {
        if (activity == null) {
            return;
        }
        new AsyncTask<Void, Void, UpdateInfo>() {
            @Override
            protected UpdateInfo doInBackground(Void... voids) {
                return fetchUpdateInfo();
            }

            @Override
            protected void onPostExecute(UpdateInfo info) {
                if (activity.isFinishing()) {
                    return;
                }
                if (info == null) {
                    if (forceShow) {
                        Toast.makeText(activity, "检查更新失败，请稍后重试", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                if (!shouldPrompt(activity, info)) {
                    if (forceShow) {
                        Toast.makeText(activity, "当前已是最新版本", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                showUpdateDialog(activity, info);
            }
        }.execute();
    }

    private static boolean shouldPrompt(Activity activity, UpdateInfo info) {
        int current = getCurrentVersionCode(activity);
        if (info.versionCode == current) {
            return false;
        }
        if (info.force) {
            return true;
        }
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        int skipped = prefs.getInt(KEY_SKIP_VERSION, 0);
        return info.versionCode > skipped;
    }

    private static void showUpdateDialog(final Activity activity, final UpdateInfo info) {
        String title = "发现新版本";
        if (info.versionName != null && info.versionName.length() > 0) {
            title = title + " " + info.versionName;
        }
        String message = info.notes != null && info.notes.length() > 0 ? info.notes : "有新版本可用";

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setCancelable(!info.force);
        builder.setPositiveButton("立即更新", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startDownload(activity, info);
            }
        });
        if (!info.force) {
            builder.setNegativeButton("跳过此版本", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
                    prefs.edit().putInt(KEY_SKIP_VERSION, info.versionCode).apply();
                }
            });
            builder.setNeutralButton("稍后", null);
        }
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(!info.force);
        dialog.show();
    }

    private static void startDownload(final Activity activity, final UpdateInfo info) {
        new DownloadTask(activity, info).execute();
    }

    private static class DownloadTask extends AsyncTask<Void, Integer, File> {
        private final WeakReference<Activity> activityRef;
        private final UpdateInfo info;
        private ProgressDialog progress;

        DownloadTask(Activity activity, UpdateInfo info) {
            this.activityRef = new WeakReference<>(activity);
            this.info = info;
        }

        @Override
        protected void onPreExecute() {
            Activity activity = activityRef.get();
            if (activity == null || activity.isFinishing()) {
                cancel(true);
                return;
            }
            progress = new ProgressDialog(activity);
            progress.setMessage("正在下载更新...");
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setIndeterminate(true);
            progress.setCancelable(!info.force);
            progress.show();
        }

        @Override
        protected File doInBackground(Void... voids) {
            Activity activity = activityRef.get();
            if (activity == null) {
                return null;
            }
            try {
                String urlStr = info.apkName;
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);
                int respCode = conn.getResponseCode();
                // 老设备SSL不支持，尝试http
                if (respCode != 200 && urlStr.startsWith("https://")) {
                    conn.disconnect();
                    urlStr = "http://" + urlStr.substring(8);
                    url = new URL(urlStr);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(15000);
                    respCode = conn.getResponseCode();
                }
                if (respCode != 200) {
                    return null;
                }
                int contentLength = conn.getContentLength();
                if (contentLength > 0) {
                    publishProgress(0);
                }
                File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) {
                    dir = activity.getCacheDir();
                }
                if (dir == null) {
                    return null;
                }
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File outFile = new File(dir, info.apkName);
                BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
                FileOutputStream out = new FileOutputStream(outFile);
                byte[] buffer = new byte[8192];
                int count;
                int total = 0;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                    total += count;
                    if (contentLength > 0) {
                        int percent = (int) (total * 100f / contentLength);
                        publishProgress(percent);
                    }
                }
                out.flush();
                out.close();
                in.close();
                return outFile;
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (progress == null || values == null || values.length == 0) {
                return;
            }
            int percent = values[0];
            if (percent > 0) {
                progress.setIndeterminate(false);
                progress.setProgress(percent);
            }
        }

        @Override
        protected void onPostExecute(File file) {
            if (progress != null && progress.isShowing()) {
                try {
                    progress.dismiss();
                } catch (Exception ignored) {}
            }
            Activity activity = activityRef.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }
            if (file == null || !file.exists()) {
                Toast.makeText(activity, "下载失败", Toast.LENGTH_SHORT).show();
                if (info.force) {
                    showUpdateDialog(activity, info);
                }
                return;
            }
            installApk(activity, file);
        }
    }

    private static void installApk(Activity activity, File file) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.getPackageManager().canRequestPackageInstalls()) {
                    Intent permIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(permIntent);
                    Toast.makeText(activity, "请授权安装权限后重试", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                String authority = activity.getPackageName() + ".fileprovider";
                uri = FileProvider.getUriForFile(activity, authority, file);
            } else {
                uri = Uri.fromFile(file);
            }
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "无法安装更新", Toast.LENGTH_SHORT).show();
        }
    }

    private static UpdateInfo fetchUpdateInfo() {
        try {
            String urlStr = "http://crmoment.ccwu.cc/oldchataacrversion.json";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            if (conn.getResponseCode() != 200) {
                // 老设备SSL不支持，尝试http
                if (urlStr.startsWith("https://")) {
                    urlStr = "http://" + urlStr.substring(8);
                    url = new URL(urlStr);
                    conn.disconnect();
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(15000);
                    if (conn.getResponseCode() != 200) {
                        return null;
                    }
                } else {
                    return null;
                }
            }
            byte[] buf = new byte[4096];
            StringBuilder sb = new StringBuilder();
            BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
            int read;
            while ((read = in.read(buf)) != -1) {
                sb.append(new String(buf, 0, read, "UTF-8"));
            }
            in.close();
            JSONObject obj = new JSONObject(sb.toString());
            UpdateInfo info = new UpdateInfo();
            info.versionCode = obj.optInt("version_code", 0);
            info.versionName = obj.optString("version_name", "");
            info.apkName = obj.optString("apk", "");
            info.notes = obj.optString("notes", "");
            info.force = obj.optBoolean("force", false);
            if (info.versionCode <= 0 || info.apkName.length() == 0) {
                return null;
            }
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getBaseUrl() {
        String apiBase = HttpUtil.BASE_URL;
        if (apiBase.endsWith("/v1")) {
            return apiBase.substring(0, apiBase.length() - 3);
        }
        int idx = apiBase.indexOf("/v1/");
        if (idx > 0) {
            return apiBase.substring(0, idx);
        }
        return apiBase;
    }

    private static int getCurrentVersionCode(Context context) {
        if (context == null) {
            return 0;
        }
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }
}
