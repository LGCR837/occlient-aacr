package aoharureverie.ocaacrclient.oldchat.models;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;

public class FriendCache {
    private static final String PREF_NAME = "friend_cache";
    private static final String KEY_FRIENDS = "friends";

    public static void saveFriends(Context context, List<User> friends) {
        if (context == null || friends == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = new Gson().toJson(friends);
        prefs.edit().putString(KEY_FRIENDS, json).apply();
    }

    public static List<User> getFriends(Context context) {
        if (context == null) {
            return new ArrayList<>();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_FRIENDS, null);
        if (json == null) {
            return new ArrayList<>();
        }
        try {
            List<User> friends = new Gson().fromJson(json,
                    TypeToken.getParameterized(List.class, User.class).getType());
            return friends == null ? new ArrayList<User>() : friends;
        } catch (Exception e) {
            return new ArrayList<>();
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
