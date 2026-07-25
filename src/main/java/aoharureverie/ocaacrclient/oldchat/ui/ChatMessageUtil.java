package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import aoharureverie.ocaacrclient.oldchat.models.Message;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;
import org.json.JSONObject;
import java.util.List;

public class ChatMessageUtil {
    public static void applyMessageStatus(List<Message> messages, String myUID) {
        applyMessageStatus(null, messages, myUID);
    }

    public static void applyMessageStatus(Context context, List<Message> messages, String myUID) {
        long lastIncoming = -1;
        long lastOutgoing = -1;
        for (Message msg : messages) {
            if (msg.from_uid == null) {
                continue;
            }
            if (MyUidStore.isMyUid(context, msg.from_uid, myUID)) {
                if (msg.created_at > lastOutgoing) {
                    lastOutgoing = msg.created_at;
                }
            } else {
                if (msg.created_at > lastIncoming) {
                    lastIncoming = msg.created_at;
                }
            }
        }

        for (Message msg : messages) {
            if (msg.from_uid == null || !MyUidStore.isMyUid(context, msg.from_uid, myUID)) {
                msg.status = Message.STATUS_NONE;
                continue;
            }
            if (msg.status == Message.STATUS_READ || msg.status == Message.STATUS_DELIVERED) {
                continue;
            }
            if (lastIncoming > msg.created_at) {
                msg.status = Message.STATUS_READ;
            } else if (lastOutgoing > msg.created_at) {
                msg.status = Message.STATUS_DELIVERED;
            } else {
                msg.status = Message.STATUS_SENT;
            }
        }
    }

    public static int parseStatus(JSONObject mObj) {
        long readAt = mObj.optLong("read_at", 0);
        if (readAt > 0) {
            return Message.STATUS_READ;
        }
        long deliveredAt = mObj.optLong("delivered_at", 0);
        if (deliveredAt > 0) {
            return Message.STATUS_DELIVERED;
        }
        return Message.STATUS_SENT;
    }

    public static String previewForMessage(Message msg) {
        if (msg == null) {
            return "";
        }
        String type = msg.msg_type == null ? "" : msg.msg_type.toLowerCase();
        MessagePayload payload = MessagePayload.fromBody(msg.body);
        if ("image".equals(type)) {
            if ("emoji".equals(payload.mediaKind)) {
                return "[表情]";
            }
            return "[图片]";
        }
        if ("voice".equals(type)) {
            return "[语音]";
        }
        if ("video".equals(type)) {
            return "[视频]";
        }
        if ("resource".equals(type)) {
            if ("music".equals(payload.mediaKind)) {
                return "[音乐]";
            }
            return "[资源]";
        }
        if ("music".equals(type)) {
            return "[音乐]";
        }
        if ("red_packet".equals(type)) {
            return "[红包]";
        }
        return payload.text != null ? payload.text : "";
    }

    public static String mediaPreview(String type) {
        if ("image".equals(type)) {
            return "[图片]";
        }
        if ("voice".equals(type)) {
            return "[语音]";
        }
        if ("video".equals(type)) {
            return "[视频]";
        }
        if ("resource".equals(type)) {
            return "[资源]";
        }
        if ("music".equals(type)) {
            return "[音乐]";
        }
        if ("red_packet".equals(type)) {
            return "[红包]";
        }
        return "";
    }

    public static String previewForType(String type, String body) {
        String normalized = type == null ? "" : type.toLowerCase();
        MessagePayload payload = MessagePayload.fromBody(body);
        if ("image".equals(normalized)) {
            if ("emoji".equals(payload.mediaKind)) {
                return "[表情]";
            }
            return "[图片]";
        }
        if ("voice".equals(normalized)) {
            return "[语音]";
        }
        if ("video".equals(normalized)) {
            return "[视频]";
        }
        if ("resource".equals(normalized)) {
            if ("music".equals(payload.mediaKind)) {
                return "[音乐]";
            }
            return "[资源]";
        }
        if ("music".equals(normalized)) {
            return "[音乐]";
        }
        if ("red_packet".equals(normalized)) {
            return "[红包]";
        }
        return payload.text != null ? payload.text : "";
    }

    public static String quotePreview(String type, String mediaKind, String text) {
        if (text != null && !text.isEmpty()) {
            return text;
        }
        String normalized = type == null ? "" : type.toLowerCase();
        if ("image".equals(normalized)) {
            return "emoji".equals(mediaKind) ? "[表情]" : "[图片]";
        }
        if ("voice".equals(normalized)) {
            return "[语音]";
        }
        if ("video".equals(normalized)) {
            return "[视频]";
        }
        if ("resource".equals(normalized)) {
            return "music".equals(mediaKind) ? "[音乐]" : "[资源]";
        }
        if ("music".equals(normalized)) {
            return "[音乐]";
        }
        if ("red_packet".equals(normalized)) {
            return "[红包]";
        }
        return "";
    }

    public static long normalizeTimestamp(long value) {
        if (value <= 0) {
            return 0;
        }
        if (value < 100000000000L) {
            return value * 1000L;
        }
        return value;
    }

    public static long sanitizeTimestamp(long value) {
        if (value <= 0 || value < 1000000000L) {
            return System.currentTimeMillis() / 1000L;
        }
        return value;
    }
}
