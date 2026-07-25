package aoharureverie.ocaacrclient.oldchat.ui;

import aoharureverie.ocaacrclient.oldchat.models.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public class DirectMessageMerger {
    public static final int MAX_ACTIVE_WINDOW_MESSAGES = 320;

    public static DirectMessageMergeResult mergeAppend(List<Message> messageList,
                                                       HashSet<String> messageIds,
                                                       List<Message> incoming,
                                                       int currentOffset,
                                                       int pageLimit) {
        List<Message> toAdd = new ArrayList<Message>();
        for (Message msg : incoming) {
            if (msg.id == null || !messageIds.contains(msg.id)) {
                toAdd.add(msg);
            }
        }
        if (!toAdd.isEmpty()) {
            messageList.addAll(0, toAdd);
            for (Message msg : toAdd) {
                if (msg.id != null) {
                    messageIds.add(msg.id);
                }
            }
        }
        currentOffset += incoming.size();

        boolean hasMore = incoming.size() >= pageLimit;
        return new DirectMessageMergeResult(currentOffset, hasMore, false);
    }

    public static DirectMessageMergeResult mergeRefresh(List<Message> messageList,
                                                        HashSet<String> messageIds,
                                                        List<Message> incoming,
                                                        int pageLimit,
                                                        boolean hasMore) {
        boolean hasOverlap = false;
        if (!messageList.isEmpty()) {
            for (Message newMsg : incoming) {
                if (newMsg.id != null && messageIds.contains(newMsg.id)) {
                    hasOverlap = true;
                    break;
                }
            }
        } else {
            hasOverlap = true;
        }

        boolean gapDetected = !hasOverlap && incoming.size() >= pageLimit;
        if (gapDetected) {
            messageList.clear();
            messageIds.clear();
            messageList.addAll(incoming);
            for (Message msg : incoming) {
                if (msg.id != null) {
                    messageIds.add(msg.id);
                }
            }
            trimOldestInMemory(messageList, messageIds, MAX_ACTIVE_WINDOW_MESSAGES);
            int currentOffset = messageList.size();
            return new DirectMessageMergeResult(currentOffset, true, true);
        }

        for (Message msg : incoming) {
            if (msg.id != null && messageIds.contains(msg.id)) {
                for (Message existing : messageList) {
                    if (existing.id != null && existing.id.equals(msg.id)) {
                        existing.from_uid = msg.from_uid;
                        existing.body = msg.body;
                        existing.msg_type = msg.msg_type;
                        existing.status = msg.status;
                        existing.thumb_url = msg.thumb_url;
                        existing.duration_ms = msg.duration_ms;
                        existing.media_url = msg.media_url;
                        existing.created_at = msg.created_at;
                        break;
                    }
                }
            } else {
                messageList.add(msg);
                if (msg.id != null) {
                    messageIds.add(msg.id);
                }
            }
        }

        Collections.sort(messageList, new Comparator<Message>() {
            @Override
            public int compare(Message m1, Message m2) {
                return compareMessage(m1, m2);
            }
        });
        trimOldestInMemory(messageList, messageIds, MAX_ACTIVE_WINDOW_MESSAGES);

        int currentOffset = messageList.size();
        return new DirectMessageMergeResult(currentOffset, hasMore, false);
    }

    public static int trimOldestInMemory(List<Message> messageList, HashSet<String> messageIds, int maxCount) {
        if (messageList == null || messageIds == null || maxCount <= 0) {
            return 0;
        }
        int size = messageList.size();
        if (size <= maxCount) {
            return 0;
        }
        int removeCount = size - maxCount;
        List<Message> removed = new ArrayList<Message>(messageList.subList(0, removeCount));
        messageList.subList(0, removeCount).clear();
        for (Message msg : removed) {
            if (msg != null && msg.id != null && msg.id.length() > 0) {
                messageIds.remove(msg.id);
            }
        }
        return removeCount;
    }

    private static int compareMessage(Message m1, Message m2) {
        if (m1 == m2) {
            return 0;
        }
        if (m1 == null) {
            return -1;
        }
        if (m2 == null) {
            return 1;
        }
        if (m1.created_at < m2.created_at) {
            return -1;
        }
        if (m1.created_at > m2.created_at) {
            return 1;
        }
        int idCmp = safeString(m1.id).compareTo(safeString(m2.id));
        if (idCmp != 0) {
            return idCmp;
        }
        int uidCmp = safeString(m1.from_uid).compareTo(safeString(m2.from_uid));
        if (uidCmp != 0) {
            return uidCmp;
        }
        return safeString(m1.thread_id).compareTo(safeString(m2.thread_id));
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }
}
