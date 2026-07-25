package aoharureverie.ocaacrclient.oldchat;

import android.content.Context;
import android.app.Application;
import android.support.v7.app.AppCompatDelegate;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.util.AppState;
import aoharureverie.ocaacrclient.oldchat.util.CrashHandler;
import aoharureverie.ocaacrclient.oldchat.util.LogManager;
import aoharureverie.ocaacrclient.oldchat.util.PrefsSanitizer;
import aoharureverie.ocaacrclient.oldchat.util.SettingsPrefs;
import aoharureverie.ocaacrclient.oldchat.service.MessageService;
import java.lang.reflect.Method;

public class OldChatApplication extends Application {
    private static Context appContext;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        installMultidexCompat();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
        HttpUtil.loadBaseUrl(this);
        PrefsSanitizer.sanitize(this);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        boolean darkMode = SettingsPrefs.isDarkModeEnabled(this);
        AppCompatDelegate.setDefaultNightMode(darkMode
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
        LogManager.getInstance(this);
        if (!CrashHandler.isCrashProcess()) {
            CrashHandler.install(this);
            AppState.init(this);
            MessageService.startIfAllowed(this);
        }
    }

    public static Context getAppContext() {
        return appContext;
    }

    private void installMultidexCompat() {
        try {
            Class<?> cls = Class.forName("android.support.multidex.MultiDex");
            Method method = cls.getMethod("install", Context.class);
            method.invoke(null, this);
        } catch (Throwable ignore) {
            // Release build uses single dex; multidex runtime is only needed for debug.
        }
    }
}
