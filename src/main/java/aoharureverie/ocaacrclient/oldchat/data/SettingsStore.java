package aoharureverie.ocaacrclient.oldchat.data;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsStore {
    private static final String PREFS = "oldchat_settings";
    private static final String KEY_NOTIFY_ENABLED = "notify_enabled";
    private static final String KEY_PUBLIC_COURT_ENABLED = "public_court_enabled";
    private static final String KEY_MUTE_PREFIX = "mute_";

    public static boolean isNotifyEnabled(Context context) {
        return prefs(context).getBoolean(KEY_NOTIFY_ENABLED, true);
    }

    public static void setNotifyEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_NOTIFY_ENABLED, enabled).apply();
    }

    public static boolean isPublicCourtEnabled(Context context) {
        return prefs(context).getBoolean(KEY_PUBLIC_COURT_ENABLED, true);
    }

    public static void setPublicCourtEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PUBLIC_COURT_ENABLED, enabled).apply();
    }

    public static boolean isConversationMuted(Context context, String conversationId, boolean isGroup) {
        if (context == null || conversationId == null || conversationId.trim().length() == 0) {
            return false;
        }
        return prefs(context).getBoolean(buildMuteKey(context, conversationId, isGroup), false);
    }

    public static void setConversationMuted(Context context, String conversationId, boolean isGroup, boolean muted) {
        if (context == null || conversationId == null || conversationId.trim().length() == 0) {
            return;
        }
        prefs(context).edit().putBoolean(buildMuteKey(context, conversationId, isGroup), muted).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String buildMuteKey(Context context, String conversationId, boolean isGroup) {
        String account = resolveAccountKey(context);
        String safeConversation = sanitizeKey(conversationId);
        String type = isGroup ? "g" : "u";
        return KEY_MUTE_PREFIX + account + "_" + type + "_" + safeConversation;
    }

    private static String resolveAccountKey(Context context) {
        if (context == null) {
            return "";
        }
        SharedPreferences authPrefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE);
        String uid = authPrefs.getString("my_uid", "");
        if (uid == null || uid.length() == 0) {
            return "";
        }
        return sanitizeKey(uid);
    }

    private static String sanitizeKey(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z') || c == '_') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        return builder.toString();
    }
}
