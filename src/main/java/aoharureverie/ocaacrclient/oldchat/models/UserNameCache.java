package aoharureverie.ocaacrclient.oldchat.models;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;
import java.util.Map;

public class UserNameCache {
    private static final String PREF_NAME = "user_name_cache";
    private static final String KEY_PREFIX = "name_";
    private static final int MAX_NAME_LENGTH = 15;

    public static void put(Context context, String uid, String name) {
        if (context == null || uid == null || uid.isEmpty()) {
            return;
        }
        String finalName = sanitize(name);
        if (finalName.isEmpty()) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String key = KEY_PREFIX + uid;
        String existing = prefs.getString(key, "");
        if (finalName.equals(existing)) {
            return;
        }
        prefs.edit().putString(key, finalName).apply();
    }

    public static void putAll(Context context, Map<String, String> names) {
        if (context == null || names == null || names.isEmpty()) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        for (Map.Entry<String, String> entry : names.entrySet()) {
            String uid = entry.getKey();
            if (uid == null || uid.isEmpty()) {
                continue;
            }
            String name = sanitize(entry.getValue());
            if (name.isEmpty()) {
                continue;
            }
            String key = KEY_PREFIX + uid;
            String existing = prefs.getString(key, "");
            if (!name.equals(existing)) {
                editor.putString(key, name);
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
            String name = FriendNameResolver.resolve(u.remark_name, u.display_name, u.username, u.uid);
            if (name.isEmpty()) {
                continue;
            }
            String key = KEY_PREFIX + u.uid;
            String existing = prefs.getString(key, "");
            if (!name.equals(existing)) {
                editor.putString(key, name);
                changed = true;
            }
        }
        if (changed) {
            editor.apply();
        }
    }

    public static String getName(Context context, String uid) {
        if (context == null || uid == null || uid.isEmpty()) {
            return "";
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sanitize(prefs.getString(KEY_PREFIX + uid, ""));
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
        String out = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (out.length() > MAX_NAME_LENGTH) {
            out = out.substring(0, MAX_NAME_LENGTH);
        }
        return out;
    }
}
