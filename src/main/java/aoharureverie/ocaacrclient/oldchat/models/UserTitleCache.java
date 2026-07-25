package aoharureverie.ocaacrclient.oldchat.models;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;
import java.util.Map;

public class UserTitleCache {
    private static final String PREF_NAME = "user_title_cache";
    private static final String KEY_PREFIX = "title_";

    public static void put(Context context, String uid, String title) {
        if (context == null || uid == null || uid.isEmpty()) {
            return;
        }
        String finalTitle = sanitize(title);
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String key = KEY_PREFIX + uid;
        String existing = prefs.getString(key, "");
        if (finalTitle.equals(existing)) {
            return;
        }
        prefs.edit().putString(key, finalTitle).apply();
    }

    public static void putAll(Context context, Map<String, String> titles) {
        if (context == null || titles == null || titles.isEmpty()) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        for (Map.Entry<String, String> entry : titles.entrySet()) {
            String uid = entry.getKey();
            if (uid == null || uid.isEmpty()) {
                continue;
            }
            String title = sanitize(entry.getValue());
            String key = KEY_PREFIX + uid;
            String existing = prefs.getString(key, "");
            if (!title.equals(existing)) {
                editor.putString(key, title);
                changed = true;
            }
        }
        if (changed) {
            editor.apply();
        }
    }

    public static void mergeUsers(Context context, List<User> users) {
        if (context == null || users == null || users.isEmpty()) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        for (User u : users) {
            if (u == null || u.uid == null || u.uid.isEmpty()) {
                continue;
            }
            String title = sanitize(u.user_title);
            String key = KEY_PREFIX + u.uid;
            String existing = prefs.getString(key, "");
            if (!title.equals(existing)) {
                editor.putString(key, title);
                changed = true;
            }
        }
        if (changed) {
            editor.apply();
        }
    }

    public static String getTitle(Context context, String uid) {
        if (context == null || uid == null || uid.isEmpty()) {
            return "";
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PREFIX + uid, "");
    }

    public static void clearAll(Context context) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed;
    }
}
