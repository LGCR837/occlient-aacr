package aoharureverie.ocaacrclient.oldchat.util;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Build;
import aoharureverie.ocaacrclient.oldchat.service.MessageService;
import java.util.List;

public class AppState {
    private static int startedCount = 0;
    private static boolean initialized = false;
    private static Context appContext;

    public static void init(Application app) {
        if (initialized) {
            return;
        }
        initialized = true;
        appContext = app.getApplicationContext();
        if (Build.VERSION.SDK_INT >= 14) {
            app.registerActivityLifecycleCallbacks(new LifecycleCallbacks());
        }
    }

    public static boolean isForeground() {
        if (startedCount > 0) {
            return true;
        }
        Context ctx = appContext;
        if (ctx == null) {
            return false;
        }
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return false;
        }
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes == null) {
            return false;
        }
        String pkg = ctx.getPackageName();
        for (ActivityManager.RunningAppProcessInfo proc : processes) {
            if (pkg.equals(proc.processName)) {
                int importance = proc.importance;
                return importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                        || importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
            }
        }
        return false;
    }

    private static class LifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
            startedCount++;
            if (startedCount == 1) {
                MessageService.startIfAllowed(activity.getApplicationContext());
            }
        }

        @Override
        public void onActivityResumed(Activity activity) {
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @Override
        public void onActivityStopped(Activity activity) {
            startedCount--;
            if (startedCount < 0) {
                startedCount = 0;
            }
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }
    }
}
