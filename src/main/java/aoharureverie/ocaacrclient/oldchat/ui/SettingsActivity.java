package aoharureverie.ocaacrclient.oldchat.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Build;
import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.data.SettingsStore;
import aoharureverie.ocaacrclient.oldchat.service.MessageService;
import aoharureverie.ocaacrclient.oldchat.api.WSManager;
import aoharureverie.ocaacrclient.oldchat.util.AccountDataCleaner;
import aoharureverie.ocaacrclient.oldchat.util.CacheSizeUtil;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends BaseActivity {
    private static final int REQ_MANUAL_PERMISSION = 6201;
    private final ArrayList<String> pendingPermissions = new ArrayList<String>();
    private boolean manualPermissionRequesting;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        View btnBack = (View) findViewByIdCompat(R.id.btnSettingsBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        View btnUiSettings = (View) findViewByIdCompat(R.id.btnUiSettings);
        if (btnUiSettings != null) {
            btnUiSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(SettingsActivity.this, UiSettingsActivity.class));
                }
            });
        }

        View btnNotificationSettings = (View) findViewByIdCompat(R.id.btnNotificationSettings);
        if (btnNotificationSettings != null) {
            btnNotificationSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(SettingsActivity.this, NotificationSettingsActivity.class));
                }
            });
        }

        View btnRequestPermissions = (View) findViewByIdCompat(R.id.btnRequestPermissions);
        if (btnRequestPermissions != null) {
            btnRequestPermissions.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestMissingPermissionsInOrder();
                }
            });
        }

        final android.widget.CheckBox cbPublicCourtEnabled =
                (android.widget.CheckBox) findViewByIdCompat(R.id.cbPublicCourtEnabled);
        View btnPublicCourtSetting = (View) findViewByIdCompat(R.id.btnPublicCourtSetting);
        if (cbPublicCourtEnabled != null) {
            cbPublicCourtEnabled.setChecked(SettingsStore.isPublicCourtEnabled(this));
        }
        if (btnPublicCourtSetting != null) {
            btnPublicCourtSetting.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean next = !SettingsStore.isPublicCourtEnabled(SettingsActivity.this);
                    SettingsStore.setPublicCourtEnabled(SettingsActivity.this, next);
                    if (cbPublicCourtEnabled != null) {
                        cbPublicCourtEnabled.setChecked(next);
                    }
                }
            });
        }

        View btnAccountManagement = (View) findViewByIdCompat(R.id.btnAccountManagement);
        if (btnAccountManagement != null) {
            btnAccountManagement.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(SettingsActivity.this, AccountManagementActivity.class));
                }
            });
        }

        View btnDataSettings = (View) findViewByIdCompat(R.id.btnDataSettings);
        if (btnDataSettings != null) {
            btnDataSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(SettingsActivity.this, DataSettingsActivity.class));
                }
            });
        }

        View btnQuickClearImageCache = (View) findViewByIdCompat(R.id.btnQuickClearImageCache);
        if (btnQuickClearImageCache != null) {
            btnQuickClearImageCache.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ImageLoader.clearDiskCache(SettingsActivity.this);
                    Toast.makeText(SettingsActivity.this, "已清理图片缓存", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View btnDeepClearMediaCache = (View) findViewByIdCompat(R.id.btnDeepClearMediaCache);
        if (btnDeepClearMediaCache != null) {
            btnDeepClearMediaCache.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    long before = calculateDeepMediaCacheBytes();
                    clearDeepMediaCache();
                    long after = calculateDeepMediaCacheBytes();
                    long released = before - after;
                    if (released < 0) {
                        released = 0;
                    }
                    Toast.makeText(SettingsActivity.this,
                            "已深度清理媒体缓存，释放 " + CacheSizeUtil.formatSize(released),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        View btnSupportSettings = (View) findViewByIdCompat(R.id.btnSupportSettings);
        if (btnSupportSettings != null) {
            btnSupportSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(SettingsActivity.this, SupportSettingsActivity.class));
                }
            });
        }

        View btnLogout = (View) findViewByIdCompat(R.id.btnLogout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
                    prefs.edit().clear().apply();
                    AccountDataCleaner.clearAll(SettingsActivity.this);
                    WSManager.getInstance().stop();
                    MessageService.stop(SettingsActivity.this);
                    Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                }
            });
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MANUAL_PERMISSION) {
            return;
        }
        String permission = permissions != null && permissions.length > 0 ? permissions[0] : "";
        boolean granted = grantResults != null && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!pendingPermissions.isEmpty()) {
            pendingPermissions.remove(0);
        }
        if (granted) {
            Toast.makeText(this, permissionLabel(permission) + "已授予", Toast.LENGTH_SHORT).show();
        } else {
            boolean dontAskAgain = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && permission != null && permission.length() > 0
                    && !ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
            if (dontAskAgain) {
                Toast.makeText(this, permissionLabel(permission) + "被永久拒绝，请到系统设置手动开启", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, permissionLabel(permission) + "未授予", Toast.LENGTH_SHORT).show();
            }
        }
        requestNextManualPermission();
    }



    private void requestMissingPermissionsInOrder() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, "当前系统无需手动申请运行时权限", Toast.LENGTH_SHORT).show();
            return;
        }
        if (manualPermissionRequesting) {
            Toast.makeText(this, "正在申请权限，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingPermissions.clear();
        collectMissingRuntimePermissions(pendingPermissions);
        if (pendingPermissions.isEmpty()) {
            Toast.makeText(this, "已授予所需权限", Toast.LENGTH_SHORT).show();
            return;
        }
        manualPermissionRequesting = true;
        Toast.makeText(this, "开始申请权限（共" + pendingPermissions.size() + "项）", Toast.LENGTH_SHORT).show();
        requestNextManualPermission();
    }

    private void collectMissingRuntimePermissions(List<String> out) {
        if (out == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            addMissingPermission(out, "android.permission.READ_MEDIA_IMAGES");
            addMissingPermission(out, "android.permission.READ_MEDIA_VIDEO");
            addMissingPermission(out, "android.permission.READ_MEDIA_AUDIO");
            addMissingPermission(out, "android.permission.POST_NOTIFICATIONS");
        } else {
            addMissingPermission(out, Manifest.permission.READ_EXTERNAL_STORAGE);
            if (Build.VERSION.SDK_INT <= 28) {
                addMissingPermission(out, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
        addMissingPermission(out, Manifest.permission.RECORD_AUDIO);
        addMissingPermission(out, Manifest.permission.READ_PHONE_STATE);
    }

    private void addMissingPermission(List<String> out, String permission) {
        if (out == null || permission == null || permission.length() == 0) {
            return;
        }
        if (out.contains(permission)) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            out.add(permission);
        }
    }

    private void requestNextManualPermission() {
        if (pendingPermissions.isEmpty()) {
            manualPermissionRequesting = false;
            Toast.makeText(this, "权限申请流程已结束", Toast.LENGTH_SHORT).show();
            return;
        }
        String permission = pendingPermissions.get(0);
        ActivityCompat.requestPermissions(this, new String[]{permission}, REQ_MANUAL_PERMISSION);
    }

    private String permissionLabel(String permission) {
        if (permission == null || permission.length() == 0) {
            return "权限";
        }
        if ("android.permission.READ_MEDIA_IMAGES".equals(permission)) {
            return "图片读取权限";
        }
        if ("android.permission.READ_MEDIA_VIDEO".equals(permission)) {
            return "视频读取权限";
        }
        if ("android.permission.READ_MEDIA_AUDIO".equals(permission)) {
            return "音频读取权限";
        }
        if (Manifest.permission.READ_EXTERNAL_STORAGE.equals(permission)) {
            return "媒体读取权限";
        }
        if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
            return "存储写入权限";
        }
        if ("android.permission.POST_NOTIFICATIONS".equals(permission)) {
            return "通知权限";
        }
        if (Manifest.permission.RECORD_AUDIO.equals(permission)) {
            return "录音权限";
        }
        if (Manifest.permission.READ_PHONE_STATE.equals(permission)) {
            return "设备识别权限";
        }
        return permission;
    }



    private long calculateDeepMediaCacheBytes() {
        long total = 0L;
        File cacheRoot = getCacheDir();
        File filesRoot = getFilesDir();

        total += getPathSize(new File(cacheRoot, "img_cache"));
        total += getPathSize(new File(filesRoot, "voice_cache"));
        total += getPathSize(new File(cacheRoot, "voice_cache"));
        total += getPathSize(new File(filesRoot, "music_player_cache"));
        total += getPathSize(new File(cacheRoot, "music_player_cache"));
        total += getPathSize(new File(cacheRoot, "video_cache"));
        total += getPathSize(new File(cacheRoot, "video_play_cache"));
        total += getPathSize(new File(cacheRoot, "upload_cache"));

        total += getPrefixFilesSize(cacheRoot, "video_trim_");
        total += getPrefixFilesSize(cacheRoot, "video_thumb_");
        total += getPrefixFilesSize(cacheRoot, "emoji_pkg_");

        return total;
    }

    private void clearDeepMediaCache() {
        ImageLoader.clearDiskCache(this);

        File cacheRoot = getCacheDir();
        File filesRoot = getFilesDir();

        deleteRecursive(new File(cacheRoot, "img_cache"));
        deleteRecursive(new File(filesRoot, "voice_cache"));
        deleteRecursive(new File(cacheRoot, "voice_cache"));
        deleteRecursive(new File(filesRoot, "music_player_cache"));
        deleteRecursive(new File(cacheRoot, "music_player_cache"));
        deleteRecursive(new File(cacheRoot, "video_cache"));
        deleteRecursive(new File(cacheRoot, "video_play_cache"));
        deleteRecursive(new File(cacheRoot, "upload_cache"));

        deleteFilesByPrefix(cacheRoot, "video_trim_");
        deleteFilesByPrefix(cacheRoot, "video_thumb_");
        deleteFilesByPrefix(cacheRoot, "emoji_pkg_");
    }

    private long getPrefixFilesSize(File root, String prefix) {
        if (root == null || !root.exists() || prefix == null) {
            return 0L;
        }
        File[] files = root.listFiles();
        if (files == null) {
            return 0L;
        }
        long total = 0L;
        for (int i = 0; i < files.length; i++) {
            File one = files[i];
            if (one == null || !one.isFile()) {
                continue;
            }
            String name = one.getName();
            if (name != null && name.startsWith(prefix)) {
                total += Math.max(0L, one.length());
            }
        }
        return total;
    }

    private void deleteFilesByPrefix(File root, String prefix) {
        if (root == null || !root.exists() || prefix == null) {
            return;
        }
        File[] files = root.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File one = files[i];
            if (one == null || !one.isFile()) {
                continue;
            }
            String name = one.getName();
            if (name != null && name.startsWith(prefix)) {
                try {
                    one.delete();
                } catch (Exception e) {
                }
            }
        }
    }

    private long getPathSize(File file) {
        if (file == null || !file.exists()) {
            return 0L;
        }
        if (file.isFile()) {
            return Math.max(0L, file.length());
        }
        File[] children = file.listFiles();
        if (children == null) {
            return 0L;
        }
        long total = 0L;
        for (int i = 0; i < children.length; i++) {
            total += getPathSize(children[i]);
        }
        return total;
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteRecursive(children[i]);
                }
            }
        }
        try {
            file.delete();
        } catch (Exception e) {
        }
    }
}
