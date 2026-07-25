package aoharureverie.ocaacrclient.oldchat.api;

import android.content.Context;
import android.content.SharedPreferences;
import aoharureverie.ocaacrclient.oldchat.models.GroupRecentChatCache;
import aoharureverie.ocaacrclient.oldchat.data.NotificationReadStore;
import aoharureverie.ocaacrclient.oldchat.models.RecentChatCache;
import aoharureverie.ocaacrclient.oldchat.util.AppState;
import aoharureverie.ocaacrclient.oldchat.util.CryptoUtil;
import aoharureverie.ocaacrclient.oldchat.util.MessageFieldRepair;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;
import aoharureverie.ocaacrclient.oldchat.util.NotificationHelper;
import org.json.JSONObject;

public class WSIncomingHandler {
    public interface Callback {
        void onDirectMessage(WSModels.DirectMessage message);
        void onDirectRead(String threadId, String readerUid, long readAt);
        void onDirectRecall(WSModels.DirectRecall recall);
        void onGroupMessage(WSModels.GroupMessage message);
        void onGroupRecall(WSModels.GroupRecall recall);
        void onTyping(WSModels.TypingEvent event);
    }

    public void handleMessage(Context context, String raw, Callback callback) {
        if (callback == null || raw == null) {
            return;
        }
        try {
            String decrypted = CryptoUtil.decryptIfNeeded(raw);
            if (decrypted != null) {
                raw = decrypted;
            }
            JSONObject obj = new JSONObject(raw);
            String type = obj.optString("type");
            JSONObject data = obj.optJSONObject("data");
            if (data == null) {
                return;
            }
            if ("direct_message".equals(type)) {
                WSModels.DirectMessage msg = parseDirectMessage(data);
                MessageFieldRepair.repairDirect(msg);
                if (context != null) {
                    RecentChatCache.updateRecentChatIncoming(context, msg.peerUid, null, null,
                            WSMessagePreview.direct(msg), msg.createdAt, 1);
                    String imageUrl = null;
                    String msgType = msg.msgType == null ? "" : msg.msgType.toLowerCase();
                    if ("image".equals(msgType)) {
                        imageUrl = (msg.thumbUrl != null && !msg.thumbUrl.isEmpty()) ? msg.thumbUrl : msg.mediaUrl;
                    }
                    if (!AppState.isForeground()) {
                        NotificationHelper.notifyDirect(context, msg.peerUid, WSMessagePreview.direct(msg), imageUrl);
                    }
                }
                callback.onDirectMessage(msg);
            } else if ("direct_read".equals(type)) {
                String threadId = data.optString("thread_id");
                String readerUid = data.optString("reader_uid");
                long readAt = data.optLong("read_at");
                callback.onDirectRead(threadId, readerUid, readAt);
            } else if ("direct_recall".equals(type)) {
                callback.onDirectRecall(parseDirectRecall(data));
            } else if ("group_message".equals(type)) {
                WSModels.GroupMessage msg = parseGroupMessage(data);
                MessageFieldRepair.repairGroup(msg);
                if (context != null) {
                    GroupRecentChatCache.updateGroupIncoming(context, msg.groupId, null, null,
                            WSMessagePreview.group(msg), msg.createdAt, 1);
                    if (isMentionForMe(context, msg)) {
                        GroupRecentChatCache.setMentionUnread(context, msg.groupId, true);
                    }
                    String imageUrl = null;
                    String msgType = msg.msgType == null ? "" : msg.msgType.toLowerCase();
                    if ("image".equals(msgType)) {
                        imageUrl = (msg.thumbUrl != null && !msg.thumbUrl.isEmpty()) ? msg.thumbUrl : msg.mediaUrl;
                    }
                    if (!AppState.isForeground()) {
                        NotificationHelper.notifyGroup(context, msg.groupId, msg.fromUid, WSMessagePreview.group(msg), imageUrl);
                    }
                }
                callback.onGroupMessage(msg);
            } else if ("group_recall".equals(type)) {
                callback.onGroupRecall(parseGroupRecall(data));
            } else if ("system_notification".equals(type)) {
                if (context != null) {
                    String id = data.optString("id", "");
                    String title = data.optString("title", "");
                    String body = data.optString("body", "");
                    if (title == null || title.length() == 0) {
                        title = "系统通知";
                    }
                    if (body == null) {
                        body = "";
                    }
                    incrementSystemNotificationUnread(context, id);
                    if (!AppState.isForeground()) {
                        NotificationHelper.notifySystem(context, id, title, body);
                    }
                }
            } else if ("typing".equals(type)) {
                callback.onTyping(parseTypingEvent(data));
            }
        } catch (Exception e) {
        }
    }

    private void incrementSystemNotificationUnread(Context context, String notificationId) {
        if (context == null) {
            return;
        }
        String id = notificationId == null ? "" : notificationId;
        if (id.length() > 0 && NotificationReadStore.isRead(context, id)) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences("notification", Context.MODE_PRIVATE);
        if (id.length() > 0) {
            String lastId = prefs.getString("last_notification_id", "");
            if (id.equals(lastId)) {
                return;
            }
        }
        int unread = prefs.getInt("unread_count", 0);
        if (unread < 0) {
            unread = 0;
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("unread_count", unread + 1);
        if (id.length() > 0) {
            editor.putString("last_notification_id", id);
        }
        editor.apply();
    }

    private WSModels.DirectMessage parseDirectMessage(JSONObject data) {
        WSModels.DirectMessage msg = new WSModels.DirectMessage();
        msg.id = data.optString("id");
        msg.threadId = data.optString("thread_id");
        msg.fromUid = data.optString("from_uid");
        msg.body = data.optString("body");
        msg.msgType = data.optString("msg_type", "text");
        msg.mediaUrl = data.optString("media_url");
        msg.thumbUrl = data.optString("thumb_url");
        msg.durationMs = data.optInt("duration_ms", 0);
        msg.createdAt = data.optLong("created_at");
        msg.peerUid = msg.fromUid;
        return msg;
    }

    private WSModels.GroupMessage parseGroupMessage(JSONObject data) {
        WSModels.GroupMessage msg = new WSModels.GroupMessage();
        msg.id = data.optString("id");
        msg.groupId = data.optString("group_id");
        msg.fromUid = data.optString("from_uid");
        msg.body = data.optString("body");
        msg.msgType = data.optString("msg_type", "text");
        msg.mediaUrl = data.optString("media_url");
        msg.thumbUrl = data.optString("thumb_url");
        msg.durationMs = data.optInt("duration_ms", 0);
        msg.createdAt = data.optLong("created_at");
        return msg;
    }

    private WSModels.DirectRecall parseDirectRecall(JSONObject data) {
        WSModels.DirectRecall recall = new WSModels.DirectRecall();
        recall.messageId = data.optString("message_id");
        recall.threadId = data.optString("thread_id");
        recall.fromUid = data.optString("from_uid");
        return recall;
    }

    private WSModels.GroupRecall parseGroupRecall(JSONObject data) {
        WSModels.GroupRecall recall = new WSModels.GroupRecall();
        recall.messageId = data.optString("message_id");
        recall.groupId = data.optString("group_id");
        recall.fromUid = data.optString("from_uid");
        return recall;
    }

    private WSModels.TypingEvent parseTypingEvent(JSONObject data) {
        WSModels.TypingEvent event = new WSModels.TypingEvent();
        event.chatId = data.optString("chat_id");
        event.uid = data.optString("uid");
        event.isGroup = data.optBoolean("is_group", false);
        event.isTyping = data.optBoolean("is_typing", false);
        return event;
    }

    private boolean isMentionForMe(Context context, WSModels.GroupMessage msg) {
        if (context == null || msg == null) {
            return false;
        }
        String myUid = getMyUid(context);
        if (myUid == null || myUid.isEmpty()) {
            return false;
        }
        if (msg.fromUid != null && msg.fromUid.equals(myUid)) {
            return false;
        }
        MessagePayload payload = MessagePayload.fromBody(msg.body);
        return payload.hasMention(myUid);
    }

    private String getMyUid(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE);
        return prefs.getString("my_uid", "");
    }
}
