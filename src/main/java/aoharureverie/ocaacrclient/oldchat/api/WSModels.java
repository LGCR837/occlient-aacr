package aoharureverie.ocaacrclient.oldchat.api;

public final class WSModels {
    private WSModels() {
    }

    public static class DirectMessage {
        public String id;
        public String threadId;
        public String fromUid;
        public String peerUid;
        public String body;
        public String msgType;
        public String mediaUrl;
        public String thumbUrl;
        public int durationMs;
        public long createdAt;
    }

    public static class DirectRecall {
        public String messageId;
        public String threadId;
        public String fromUid;
    }

    public static class GroupMessage {
        public String id;
        public String groupId;
        public String fromUid;
        public String body;
        public String msgType;
        public String mediaUrl;
        public String thumbUrl;
        public int durationMs;
        public long createdAt;
    }

    public static class GroupRecall {
        public String messageId;
        public String groupId;
        public String fromUid;
    }

    public static class TypingEvent {
        public String chatId;
        public String uid;
        public boolean isGroup;
        public boolean isTyping;
    }
}
