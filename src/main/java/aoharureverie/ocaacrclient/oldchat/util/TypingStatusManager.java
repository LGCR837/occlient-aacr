package aoharureverie.ocaacrclient.oldchat.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.TypingStatus;
import org.json.JSONObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TypingStatusManager {
    private static TypingStatusManager instance;
    private final Map<String, TypingStatus> typingUsers = new ConcurrentHashMap<>();
    private final Map<String, TypingListener> listeners = new ConcurrentHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Runnable> sendTasks = new ConcurrentHashMap<>();
    private final Map<String, Runnable> checkTasks = new ConcurrentHashMap<>();
    private final Map<String, Runnable> stopTasks = new ConcurrentHashMap<>();
    private static final long TYPING_TIMEOUT = 3000;
    private static final long SEND_INTERVAL = 3000;
    private static final long CHECK_INTERVAL = 1000;
    private static final long STOP_GRACE_MS = 3000;

    public interface TypingListener {
        void onTypingStatusChanged(String uid, boolean isTyping);
    }

    private TypingStatusManager() {
    }

    public static synchronized TypingStatusManager getInstance() {
        if (instance == null) {
            instance = new TypingStatusManager();
        }
        return instance;
    }

    public void registerListener(String chatId, TypingListener listener) {
        listeners.put(chatId, listener);
    }

    public void unregisterListener(String chatId) {
        listeners.remove(chatId);
        stopCheckingTyping(chatId);
    }

    public void startTyping(final Context context, final String token, final String chatId, final boolean isGroup) {
        String key = chatId + "_send";
        if (sendTasks.containsKey(key)) {
            return;
        }

        Runnable task = new Runnable() {
            @Override
            public void run() {
                sendTypingStatus(context, token, chatId, true, isGroup);
                handler.postDelayed(this, SEND_INTERVAL);
            }
        };
        sendTasks.put(key, task);
        handler.post(task);
    }

    public void stopTyping(final Context context, final String token, final String chatId, final boolean isGroup) {
        String key = chatId + "_send";
        Runnable task = sendTasks.remove(key);
        if (task != null) {
            handler.removeCallbacks(task);
        }
        sendTypingStatus(context, token, chatId, false, isGroup);
    }

    public void startCheckingTyping(final Context context, final String token, final String chatId, final boolean isGroup) {
        String key = chatId + "_check";
        if (checkTasks.containsKey(key)) {
            return;
        }

        Runnable task = new Runnable() {
            @Override
            public void run() {
                checkTypingStatus(context, token, chatId, isGroup);
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        checkTasks.put(key, task);
        handler.post(task);
    }

    public void stopCheckingTyping(String chatId) {
        String key = chatId + "_check";
        Runnable task = checkTasks.remove(key);
        if (task != null) {
            handler.removeCallbacks(task);
        }
        clearTypingUsers(chatId);
    }

    public boolean isUserTyping(String chatId, String uid) {
        String key = chatId + "_" + uid;
        TypingStatus status = typingUsers.get(key);
        if (status == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - status.timestamp > TYPING_TIMEOUT) {
            typingUsers.remove(key);
            notifyListener(chatId, uid, false);
            return false;
        }
        return status.isTyping;
    }

    private void sendTypingStatus(Context context, String token, String chatId, boolean isTyping, boolean isGroup) {
        if (context == null || token == null || token.isEmpty() || chatId == null || chatId.isEmpty()) {
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("chat_id", chatId);
            body.put("is_typing", isTyping);
            body.put("is_group", isGroup);

            String endpoint = isGroup ? "/groups/typing" : "/chats/typing";
            HttpUtil.post(endpoint, body, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String data) {
                    // Success
                }

                @Override
                public void onError(int code, String message) {
                    // Silently fail for typing status
                }
            });
        } catch (Exception e) {
            // Silently fail for typing status
        }
    }

    private void checkTypingStatus(Context context, String token, String chatId, boolean isGroup) {
        if (context == null || token == null || token.isEmpty() || chatId == null || chatId.isEmpty()) {
            return;
        }

        String endpoint = isGroup
            ? "/groups/" + chatId + "/typing"
            : "/chats/" + chatId + "/typing";

        HttpUtil.get(endpoint, token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    if (response == null || response.isEmpty()) {
                        return;
                    }

                    JSONObject json = new JSONObject(response);
                    if (json.has("users")) {
                        org.json.JSONArray users = json.getJSONArray("users");
                        for (int i = 0; i < users.length(); i++) {
                            JSONObject user = users.getJSONObject(i);
                            String uid = user.optString("uid");
                            boolean isTyping = user.optBoolean("is_typing", false);

                            if (uid != null && !uid.isEmpty()) {
                                updateTypingStatus(chatId, uid, isTyping);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Silently fail for typing status
                }
            }

            @Override
            public void onError(int code, String message) {
                // Silently fail for typing status
            }
        });
    }

    private void updateTypingStatus(String chatId, String uid, boolean isTyping) {
        String key = chatId + "_" + uid;
        if (isTyping) {
            cancelStopTask(key);
            TypingStatus status = typingUsers.get(key);
            if (status == null) {
                status = new TypingStatus();
                status.uid = uid;
                status.chatId = chatId;
            }
            status.isTyping = true;
            status.timestamp = System.currentTimeMillis();
            typingUsers.put(key, status);
            notifyListener(chatId, uid, true);
            return;
        }
        TypingStatus existing = typingUsers.get(key);
        if (existing == null) {
            notifyListener(chatId, uid, false);
            return;
        }
        scheduleStop(chatId, uid, key);
    }

    public void handleRemoteTyping(String chatId, String uid, boolean isTyping) {
        if (chatId == null || chatId.isEmpty() || uid == null || uid.isEmpty()) {
            return;
        }
        updateTypingStatus(chatId, uid, isTyping);
    }

    public void clearTypingOnMessage(String chatId, String uid) {
        if (chatId == null || chatId.isEmpty() || uid == null || uid.isEmpty()) {
            return;
        }
        String key = chatId + "_" + uid;
        cancelStopTask(key);
        typingUsers.remove(key);
        notifyListener(chatId, uid, false);
    }

    private void notifyListener(final String chatId, final String uid, final boolean isTyping) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                TypingListener listener = listeners.get(chatId);
                if (listener != null) {
                    listener.onTypingStatusChanged(uid, isTyping);
                }
            }
        });
    }

    private void clearTypingUsers(String chatId) {
        java.util.ArrayList<String> keys = new java.util.ArrayList<>(typingUsers.keySet());
        for (String key : keys) {
            if (key.startsWith(chatId + "_")) {
                typingUsers.remove(key);
            }
        }
        java.util.ArrayList<String> stopKeys = new java.util.ArrayList<>(stopTasks.keySet());
        for (String key : stopKeys) {
            if (key.startsWith(chatId + "_")) {
                Runnable task = stopTasks.remove(key);
                if (task != null) {
                    handler.removeCallbacks(task);
                }
            }
        }
    }

    private void scheduleStop(final String chatId, final String uid, final String key) {
        cancelStopTask(key);
        TypingStatus status = typingUsers.get(key);
        if (status == null) {
            status = new TypingStatus();
            status.uid = uid;
            status.chatId = chatId;
        }
        status.isTyping = true;
        status.timestamp = System.currentTimeMillis();
        typingUsers.put(key, status);
        Runnable task = new Runnable() {
            @Override
            public void run() {
                Runnable current = stopTasks.get(key);
                if (current != this) {
                    return;
                }
                stopTasks.remove(key);
                typingUsers.remove(key);
                notifyListener(chatId, uid, false);
            }
        };
        stopTasks.put(key, task);
        handler.postDelayed(task, STOP_GRACE_MS);
    }

    private void cancelStopTask(String key) {
        Runnable task = stopTasks.remove(key);
        if (task != null) {
            handler.removeCallbacks(task);
        }
    }
}
