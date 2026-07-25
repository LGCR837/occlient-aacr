package aoharureverie.ocaacrclient.oldchat.util;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

public class MyUidStore {
    private static final String PREFS = "auth";
    private static final String KEY_UID = "my_uid";
    private static final String KEY_UID_ALIASES = "my_uid_aliases";
    private static final int MAX_ALIAS_COUNT = 5;

    private MyUidStore() {
    }

    public static String getCurrentUid(Context context) {
        if (context == null) {
            return "";
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString(KEY_UID, "");
    }

    public static void recordUidAlias(Context context, String oldUid, String newUid) {
        if (context == null) {
            return;
        }
        if (oldUid == null || oldUid.length() == 0) {
            return;
        }
        if (newUid != null && oldUid.equals(newUid)) {
            return;
        }
        List<String> aliases = loadAliases(context);
        for (int i = aliases.size() - 1; i >= 0; i--) {
            if (oldUid.equals(aliases.get(i))) {
                aliases.remove(i);
            }
        }
        aliases.add(0, oldUid);
        while (aliases.size() > MAX_ALIAS_COUNT) {
            aliases.remove(aliases.size() - 1);
        }
        saveAliases(context, aliases);
    }

    public static void clearUidAliases(Context context) {
        if (context == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_UID_ALIASES).apply();
    }

    public static boolean isMyUid(Context context, String uid) {
        return isMyUid(context, uid, null);
    }

    public static boolean isMyUid(Context context, String uid, String currentUid) {
        if (uid == null || uid.length() == 0) {
            return false;
        }

        String persistedUid = getCurrentUid(context);
        if (currentUid == null || currentUid.length() == 0) {
            currentUid = persistedUid;
        }

        if (uid.equals(currentUid)) {
            return true;
        }
        if (persistedUid != null && persistedUid.length() > 0 && uid.equals(persistedUid)) {
            return true;
        }

        if (context == null) {
            return false;
        }
        List<String> aliases = loadAliases(context);
        for (int i = 0; i < aliases.size(); i++) {
            if (uid.equals(aliases.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> loadAliases(Context context) {
        List<String> result = new ArrayList<>();
        if (context == null) {
            return result;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_UID_ALIASES, "");
        if (raw == null || raw.length() == 0) {
            return result;
        }
        int start = 0;
        int length = raw.length();
        for (int i = 0; i <= length; i++) {
            if (i == length || raw.charAt(i) == ',') {
                if (i > start) {
                    String item = raw.substring(start, i).trim();
                    if (item.length() > 0) {
                        result.add(item);
                    }
                }
                start = i + 1;
            }
        }
        return result;
    }

    private static void saveAliases(Context context, List<String> aliases) {
        if (context == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < aliases.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(aliases.get(i));
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_UID_ALIASES, builder.toString()).apply();
    }
}
