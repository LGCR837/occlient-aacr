package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;

import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.util.MessageFieldRepair;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

class GroupMessageSyncHelper {


    static int parseEffectiveOffset(String response, int fallback) {
        try {
            JSONObject obj = new JSONObject(response);
            int value = obj.optInt("effective_offset", fallback);
            if (value < 0) {
                return 0;
            }
            return value;
        } catch (Exception e) {
            if (fallback < 0) {
                return 0;
            }
            return fallback;
        }
    }

    static final int MAX_ACTIVE_WINDOW_MESSAGES = 320;

    private GroupMessageSyncHelper() {
    }

    static List<GroupMessage> parseIncoming(String response) throws Exception {
        JSONObject obj = new JSONObject(response);
        JSONArray arr = obj.getJSONArray("messages");
        List<GroupMessage> incoming = new ArrayList<GroupMessage>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject mObj = arr.getJSONObject(i);
            GroupMessage m = new GroupMessage();
            m.id = mObj.optString("id");
            m.group_id = mObj.optString("group_id");
            m.from_uid = mObj.optString("from_uid");
            m.body = mObj.optString("body");
            m.msg_type = mObj.optString("msg_type", "text");
            m.media_url = mObj.optString("media_url");
            m.thumb_url = mObj.optString("thumb_url");
            m.duration_ms = mObj.optInt("duration_ms", 0);
            m.created_at = ChatMessageUtil.sanitizeTimestamp(mObj.optLong("created_at", 0));
            m.read_count = mObj.optInt("read_count", 0);
            MessageFieldRepair.repairGroup(m);
            incoming.add(m);
        }
        Collections.reverse(incoming);
        return incoming;
    }

    static int countIncomingForBubble(Context context, String myUid, List<GroupMessage> incoming,
                                      HashSet<String> messageIds) {
        if (incoming == null || incoming.isEmpty()) {
            return 0;
        }
        int count = 0;
        HashSet<String> existingIds = new HashSet<String>(messageIds);
        for (GroupMessage msg : incoming) {
            if (msg.id == null || existingIds.contains(msg.id)) {
                continue;
            }
            if (msg.from_uid == null || !MyUidStore.isMyUid(context, msg.from_uid, myUid)) {
                count++;
            }
        }
        return count;
    }

    static int trimOldestInMemory(List<GroupMessage> messageList, HashSet<String> messageIds, int maxCount) {
        if (messageList == null || messageIds == null || maxCount <= 0) {
            return 0;
        }
        int size = messageList.size();
        if (size <= maxCount) {
            return 0;
        }
        int removeCount = size - maxCount;
        List<GroupMessage> removed = new ArrayList<GroupMessage>(messageList.subList(0, removeCount));
        messageList.subList(0, removeCount).clear();
        for (GroupMessage msg : removed) {
            if (msg != null && msg.id != null && msg.id.length() > 0) {
                messageIds.remove(msg.id);
            }
        }
        return removeCount;
    }

    static GroupMessageMergeResult mergeAppend(List<GroupMessage> messageList, HashSet<String> messageIds,
                                               List<GroupMessage> incoming, int currentOffset,
                                               int pageLimit) {
        List<GroupMessage> toAdd = new ArrayList<GroupMessage>();
        for (GroupMessage msg : incoming) {
            if (msg.id == null || !messageIds.contains(msg.id)) {
                toAdd.add(msg);
            }
        }
        if (!toAdd.isEmpty()) {
            messageList.addAll(0, toAdd);
            for (GroupMessage msg : toAdd) {
                if (msg.id != null) {
                    messageIds.add(msg.id);
                }
            }
        }
        int nextOffset = currentOffset + incoming.size();
        boolean hasMore = incoming.size() >= pageLimit;
        return new GroupMessageMergeResult(nextOffset, hasMore, false);
    }

    static GroupMessageMergeResult mergeRefresh(List<GroupMessage> messageList, HashSet<String> messageIds,
                                                List<GroupMessage> incoming, int pageLimit,
                                                boolean hasMore) {
        boolean hasOverlap = false;
        if (!messageList.isEmpty()) {
            for (GroupMessage newMsg : incoming) {
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
            for (GroupMessage msg : incoming) {
                if (msg.id != null) {
                    messageIds.add(msg.id);
                }
            }
            return new GroupMessageMergeResult(messageList.size(), true, true);
        }
        for (GroupMessage msg : incoming) {
            if (msg.id != null && messageIds.contains(msg.id)) {
                for (GroupMessage existing : messageList) {
                    if (existing.id != null && existing.id.equals(msg.id)) {
                        existing.from_uid = msg.from_uid;
                        existing.body = msg.body;
                        existing.msg_type = msg.msg_type;
                        existing.thumb_url = msg.thumb_url;
                        existing.duration_ms = msg.duration_ms;
                        existing.created_at = msg.created_at;
                        existing.read_count = msg.read_count;
                        existing.media_url = msg.media_url;
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
        Collections.sort(messageList, new Comparator<GroupMessage>() {
            @Override
            public int compare(GroupMessage m1, GroupMessage m2) {
                return compareGroupMessage(m1, m2);
            }
        });
        return new GroupMessageMergeResult(messageList.size(), hasMore, false);
    }

    private static int compareGroupMessage(GroupMessage m1, GroupMessage m2) {
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
        return safeString(m1.from_uid).compareTo(safeString(m2.from_uid));
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }
}
