package aoharureverie.ocaacrclient.oldchat.models;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;

public class GroupCache {
    private static final String PREF_NAME = "group_cache";
    private static final String KEY_GROUPS = "groups";

    public static void saveGroups(Context context, List<Group> groups) {
        if (context == null || groups == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = new Gson().toJson(groups);
        prefs.edit().putString(KEY_GROUPS, json).apply();
    }

    public static List<Group> getGroups(Context context) {
        if (context == null) {
            return new ArrayList<>();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_GROUPS, null);
        if (json == null) {
            return new ArrayList<>();
        }
        try {
            List<Group> groups = new Gson().fromJson(json,
                    TypeToken.getParameterized(List.class, Group.class).getType());
            return groups == null ? new ArrayList<Group>() : groups;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void remove(Context context, String groupId) {
        if (context == null || groupId == null || groupId.isEmpty()) {
            return;
        }
        List<Group> groups = getGroups(context);
        boolean changed = false;
        for (int i = groups.size() - 1; i >= 0; i--) {
            Group g = groups.get(i);
            if (g != null && groupId.equals(g.id)) {
                groups.remove(i);
                changed = true;
            }
        }
        if (changed) {
            saveGroups(context, groups);
        }
    }

    public static void updateMemberCount(Context context, String groupId, int memberCount) {
        if (context == null || groupId == null || groupId.isEmpty()) {
            return;
        }
        if (memberCount <= 0) {
            return;
        }
        List<Group> groups = getGroups(context);
        boolean changed = false;
        for (Group g : groups) {
            if (g != null && groupId.equals(g.id)) {
                if (g.member_count != memberCount) {
                    g.member_count = memberCount;
                    changed = true;
                }
                break;
            }
        }
        if (changed) {
            saveGroups(context, groups);
        }
    }

    public static void updateName(Context context, String groupId, String name) {
        if (context == null || groupId == null || groupId.isEmpty()) {
            return;
        }
        String newName = name == null ? "" : name.trim();
        if (newName.length() == 0) {
            return;
        }
        List<Group> groups = getGroups(context);
        boolean changed = false;
        for (Group g : groups) {
            if (g != null && groupId.equals(g.id)) {
                if (g.name == null || !newName.equals(g.name)) {
                    g.name = newName;
                    changed = true;
                }
                break;
            }
        }
        if (changed) {
            saveGroups(context, groups);
        }
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
}
