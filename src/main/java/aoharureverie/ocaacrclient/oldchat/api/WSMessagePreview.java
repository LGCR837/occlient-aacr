package aoharureverie.ocaacrclient.oldchat.api;

import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;

public final class WSMessagePreview {
    private WSMessagePreview() {
    }

    public static String direct(WSModels.DirectMessage msg) {
        if (msg == null) {
            return "";
        }
        String type = msg.msgType == null ? "" : msg.msgType.toLowerCase();
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

    public static String group(WSModels.GroupMessage msg) {
        if (msg == null) {
            return "";
        }
        String type = msg.msgType == null ? "" : msg.msgType.toLowerCase();
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
}
