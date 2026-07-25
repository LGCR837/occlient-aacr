package aoharureverie.ocaacrclient.oldchat.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class SettingsPrefs {
    private static final String PREFS_SETTINGS = "settings";
    private static final String KEY_NOTIFY = "notify_enabled";
    private static final String KEY_DARK_MODE = "dark_mode_enabled";
    private static final String KEY_FONT_SIZE = "font_size_index";
    private static final String KEY_PRIVACY_AGREED = "privacy_agreed";
    private static final String KEY_TYPING_INDICATOR = "typing_indicator_enabled";
    private static final String KEY_ENTER_SEND = "enter_send_enabled";

    private SettingsPrefs() {
    }

    public static boolean isNotifyEnabled(Context context) {
        return prefs(context).getBoolean(KEY_NOTIFY, true);
    }

    public static void setNotifyEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_NOTIFY, enabled).apply();
    }

    public static boolean isDarkModeEnabled(Context context) {
        return prefs(context).getBoolean(KEY_DARK_MODE, false);
    }

    public static void setDarkModeEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_DARK_MODE, enabled).commit();
    }

    public static int getFontSizeIndex(Context context) {
        return prefs(context).getInt(KEY_FONT_SIZE, 1);
    }

    public static void setFontSizeIndex(Context context, int index) {
        prefs(context).edit().putInt(KEY_FONT_SIZE, index).apply();
    }

    public static float getFontScale(Context context) {
        int index = getFontSizeIndex(context);
        switch (index) {
            case 0:
                return 0.9f;
            case 2:
                return 1.15f;
            case 3:
                return 1.3f;
            default:
                return 1.0f;
        }
    }

    public static boolean isPrivacyAgreed(Context context) {
        return prefs(context).getBoolean(KEY_PRIVACY_AGREED, false);
    }

    public static void setPrivacyAgreed(Context context, boolean agreed) {
        prefs(context).edit().putBoolean(KEY_PRIVACY_AGREED, agreed).apply();
    }

    public static boolean isTypingIndicatorEnabled(Context context) {
        return prefs(context).getBoolean(KEY_TYPING_INDICATOR, true);
    }

    public static void setTypingIndicatorEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_TYPING_INDICATOR, enabled).apply();
    }

    public static boolean isEnterSendEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENTER_SEND, false);
    }

    public static void setEnterSendEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENTER_SEND, enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
    }
}
