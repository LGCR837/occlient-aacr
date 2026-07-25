package aoharureverie.ocaacrclient.oldchat.ui.fragments;

import android.content.Context;
import android.content.SharedPreferences;

final class RecentFoldStore {
    private static final String PREFS = "recent_fold";

    private RecentFoldStore() {
    }

    static boolean isFolded(Context context, boolean isGroup, String id) {
        if (context == null || id == null || id.isEmpty()) {
            return false;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(buildKey(isGroup, id), false);
    }

    static void setFolded(Context context, boolean isGroup, String id, boolean folded) {
        if (context == null || id == null || id.isEmpty()) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(buildKey(isGroup, id), folded).apply();
    }

    private static String buildKey(boolean isGroup, String id) {
        return (isGroup ? "g:" : "u:") + id;
    }
}
