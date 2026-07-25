package aoharureverie.ocaacrclient.oldchat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.view.View;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.util.CrashHandler;
import aoharureverie.ocaacrclient.oldchat.util.SettingsPrefs;
import java.util.ArrayList;
import java.util.List;

public class BaseActivity extends AppCompatActivity {
    private static final int REQ_STARTUP_PERMISSIONS = 4101;
    private static boolean startupPermissionRequested;
    private static boolean startupPermissionRequesting;

    @SuppressWarnings("unchecked")
    protected <T extends View> T findViewByIdCompat(int id) {
        return (T) super.findViewById(id);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ensurePreferredNightMode();
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ensurePreferredNightMode();
        ensureStartupPermissions();
    }

    private void ensurePreferredNightMode() {
        if (CrashHandler.isCrashProcess()) {
            return;
        }
        boolean darkModeEnabled = SettingsPrefs.isDarkModeEnabled(this);
        int preferredMode = darkModeEnabled
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO;
        if (AppCompatDelegate.getDefaultNightMode() != preferredMode) {
            AppCompatDelegate.setDefaultNightMode(preferredMode);
        }
        try {
            getDelegate().setLocalNightMode(preferredMode);
            int currentMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            boolean darkNow = currentMode == Configuration.UI_MODE_NIGHT_YES;
            if (darkNow != darkModeEnabled) {
                getDelegate().applyDayNight();
            }
        } catch (Throwable ignored) {
        }
    }

    private void ensureStartupPermissions() {
        if (startupPermissionRequested) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            startupPermissionRequested = true;
            return;
        }
        if (CrashHandler.isCrashProcess()) {
            startupPermissionRequested = true;
            return;
        }
        List<String> missing = new ArrayList<String>();
        if (Build.VERSION.SDK_INT >= 33) {
            addIfMissing(missing, "android.permission.READ_MEDIA_IMAGES");
            addIfMissing(missing, "android.permission.READ_MEDIA_VIDEO");
            addIfMissing(missing, "android.permission.READ_MEDIA_AUDIO");
            addIfMissing(missing, "android.permission.POST_NOTIFICATIONS");
        } else {
            addIfMissing(missing, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (missing.isEmpty()) {
            startupPermissionRequested = true;
            return;
        }
        if (startupPermissionRequesting) {
            return;
        }
        startupPermissionRequesting = true;
        ActivityCompat.requestPermissions(this, missing.toArray(new String[missing.size()]), REQ_STARTUP_PERMISSIONS);
    }

    private void addIfMissing(List<String> missing, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            missing.add(permission);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_STARTUP_PERMISSIONS) {
            return;
        }
        startupPermissionRequesting = false;
        startupPermissionRequested = true;
        if (grantResults == null || grantResults.length == 0) {
            return;
        }
        boolean deniedStorage = false;
        boolean deniedNotification = false;
        boolean dontAskAgain = false;
        for (int i = 0; i < grantResults.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                continue;
            }
            String permission = permissions != null && i < permissions.length ? permissions[i] : "";
            if ("android.permission.POST_NOTIFICATIONS".equals(permission)) {
                deniedNotification = true;
            } else {
                deniedStorage = true;
            }
            if (permission != null && permission.length() > 0
                    && !ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                dontAskAgain = true;
            }
        }
        if (deniedStorage || deniedNotification) {
            String msg;
            if (deniedStorage && deniedNotification) {
                msg = "通知和媒体权限未授予，部分功能可能受限";
            } else if (deniedNotification) {
                msg = "通知权限未授予，可能收不到消息提醒";
            } else {
                msg = "媒体读取权限未授予，图片/视频/音频功能受限";
            }
            if (dontAskAgain) {
                msg = msg + "，可在系统设置中手动开启";
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }
}
