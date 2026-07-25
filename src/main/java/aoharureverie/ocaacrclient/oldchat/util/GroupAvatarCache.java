package aoharureverie.ocaacrclient.oldchat.util;

import android.content.Context;
import android.content.SharedPreferences;
import aoharureverie.ocaacrclient.oldchat.models.GroupMember;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupAvatarCache {
    private static final String PREFS = "group_avatar_cache";
    private static final String KEY_MAP = "avatar_map";

    private GroupAvatarCache() {
    }

    public static void fillMissing(Context context, Map<String, String> avatarMap) {
        if (context == null || avatarMap == null) {
            return;
        }
        JSONObject map = loadMap(context.getApplicationContext());
        JSONArray keys = map.names();
        if (keys == null) {
            return;
        }
        for (int i = 0; i < keys.length(); i++) {
            String uid = keys.optString(i);
            if (uid == null || uid.isEmpty()) {
                continue;
            }
            String cachedUrl = map.optString(uid, "");
            if (cachedUrl == null || cachedUrl.isEmpty()) {
                continue;
            }
            String current = avatarMap.get(uid);
            if (current == null || current.isEmpty()) {
                avatarMap.put(uid, cachedUrl);
            }
        }
    }

    public static String getCachedAvatar(Context context, String uid) {
        if (context == null || uid == null || uid.isEmpty()) {
            return "";
        }
        JSONObject map = loadMap(context.getApplicationContext());
        return map.optString(uid, "");
    }

    public static void updateFromMembers(Context context, List<GroupMember> members) {
        if (context == null || members == null || members.isEmpty()) {
            return;
        }
        Map<String, String> map = new HashMap<>();
        for (GroupMember m : members) {
            if (m == null || m.uid == null || m.uid.isEmpty()) {
                continue;
            }
            if (m.avatar_url == null || m.avatar_url.isEmpty()) {
                continue;
            }
            map.put(m.uid, m.avatar_url);
        }
        updateFromAvatarMap(context, map);
    }

    public static void updateFromAvatarMap(Context context, Map<String, String> avatarMap) {
        if (context == null || avatarMap == null || avatarMap.isEmpty()) {
            return;
        }
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONObject map = loadMap(prefs);
        boolean changed = false;
        for (Map.Entry<String, String> entry : avatarMap.entrySet()) {
            String uid = entry.getKey();
            String url = entry.getValue();
            if (uid == null || uid.isEmpty() || url == null || url.isEmpty()) {
                continue;
            }
            String cached = map.optString(uid, "");
            if (!url.equals(cached)) {
                try {
                    map.put(uid, url);
                    changed = true;
                } catch (Exception e) {
                }
            }
            if (!ImageLoader.isCached(appContext, url)) {
                ImageLoader.prefetch(appContext, url);
            }
        }
        if (changed) {
            prefs.edit().putString(KEY_MAP, map.toString()).apply();
        }
    }

    private static JSONObject loadMap(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return loadMap(prefs);
    }

    private static JSONObject loadMap(SharedPreferences prefs) {
        try {
            String raw = prefs.getString(KEY_MAP, "{}");
            return new JSONObject(raw);
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
