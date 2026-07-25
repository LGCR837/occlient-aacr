package aoharureverie.ocaacrclient.oldchat.models;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MessageHistoryCache {
    private static final String PREF_NAME = "message_history_cache";
    private static final String KEY_DIRECT_PREFIX = "direct_";
    private static final String KEY_GROUP_PREFIX = "group_";
    private static final int MAX_MESSAGES = 200;
    private static final Gson GSON = new Gson();
    private static final Executor SAVE_EXECUTOR = Executors.newSingleThreadExecutor();

    public static void saveDirectMessages(Context context, String uid, List<Message> messages) {
        if (context == null || uid == null || uid.isEmpty() || messages == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        saveAsync(appContext, KEY_DIRECT_PREFIX + uid, trimCopy(messages));
    }

    public static List<Message> getDirectMessages(Context context, String uid) {
        if (context == null || uid == null || uid.isEmpty()) {
            return new ArrayList<>();
        }
        return load(context, KEY_DIRECT_PREFIX + uid,
                TypeToken.getParameterized(List.class, Message.class).getType());
    }

    public static void saveGroupMessages(Context context, String groupId, List<GroupMessage> messages) {
        if (context == null || groupId == null || groupId.isEmpty() || messages == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        saveAsync(appContext, KEY_GROUP_PREFIX + groupId, trimCopy(messages));
    }

    public static List<GroupMessage> getGroupMessages(Context context, String groupId) {
        if (context == null || groupId == null || groupId.isEmpty()) {
            return new ArrayList<>();
        }
        return load(context, KEY_GROUP_PREFIX + groupId,
                TypeToken.getParameterized(List.class, GroupMessage.class).getType());
    }

    public static void removeGroupMessages(Context context, String groupId) {
        if (context == null || groupId == null || groupId.isEmpty()) {
            return;
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_GROUP_PREFIX + groupId)
                .apply();
    }

    public static List<String> getDirectChatIds(Context context) {
        return getChatIds(context, KEY_DIRECT_PREFIX);
    }

    public static List<String> getGroupChatIds(Context context) {
        return getChatIds(context, KEY_GROUP_PREFIX);
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

    private static List<String> getChatIds(Context context, String prefix) {
        if (context == null) {
            return new ArrayList<>();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Map<String, ?> all = prefs.getAll();
        List<String> ids = new ArrayList<>();
        for (String key : all.keySet()) {
            if (key != null && key.startsWith(prefix)) {
                String id = key.substring(prefix.length());
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private static <T> void save(Context context, String key, List<T> messages) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = GSON.toJson(messages);
        prefs.edit().putString(key, json).apply();
    }

    private static <T> List<T> load(Context context, String key, Type type) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(key, null);
        if (json == null) {
            return new ArrayList<>();
        }
        try {
            List<T> list = GSON.fromJson(json, type);
            return list == null ? new ArrayList<T>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static <T> void saveAsync(final Context context, final String key, final List<T> messages) {
        if (context == null || key == null || key.isEmpty() || messages == null) {
            return;
        }
        SAVE_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                save(context, key, messages);
            }
        });
    }

    private static <T> List<T> trimCopy(List<T> list) {
        if (list == null) {
            return new ArrayList<>();
        }
        int size = list.size();
        if (size <= MAX_MESSAGES) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>(list.subList(size - MAX_MESSAGES, size));
    }
}
