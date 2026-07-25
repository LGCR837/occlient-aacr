package aoharureverie.ocaacrclient.oldchat.api;

import android.content.Context;
import android.content.SharedPreferences;
import aoharureverie.ocaacrclient.oldchat.models.GroupRecentChatCache;
import aoharureverie.ocaacrclient.oldchat.models.RecentChatCache;
import aoharureverie.ocaacrclient.oldchat.util.MessageFieldRepair;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class WSUnreadSyncHandler {
    public interface DirectMessageDispatcher {
        void dispatch(WSModels.DirectMessage message);
    }

    public interface GroupMessageDispatcher {
        void dispatch(WSModels.GroupMessage message);
    }

    public void handleDirectUnread(Context context, String response, DirectMessageDispatcher dispatcher) {
        if (response == null || dispatcher == null) {
            return;
        }
        try {
            JSONObject obj = new JSONObject(response);
            JSONArray arr = obj.getJSONArray("messages");
            List<WSModels.DirectMessage> messages = new ArrayList<>();
            HashMap<String, WSModels.DirectMessage> latestByPeer = new HashMap<>();
            HashMap<String, Integer> countByPeer = new HashMap<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject mObj = arr.getJSONObject(i);
                WSModels.DirectMessage msg = new WSModels.DirectMessage();
                msg.id = mObj.optString("id");
                msg.threadId = mObj.optString("thread_id");
                msg.fromUid = mObj.optString("from_uid");
                msg.peerUid = mObj.optString("peer_uid");
                msg.body = mObj.optString("body");
                msg.msgType = mObj.optString("msg_type", "text");
                msg.mediaUrl = mObj.optString("media_url");
                msg.thumbUrl = mObj.optString("thumb_url");
                msg.durationMs = mObj.optInt("duration_ms", 0);
                msg.createdAt = mObj.optLong("created_at");
                MessageFieldRepair.repairDirect(msg);
                messages.add(msg);

                String peer = msg.peerUid;
                if (peer != null && peer.length() > 0) {
                    WSModels.DirectMessage latest = latestByPeer.get(peer);
                    if (latest == null || msg.createdAt > latest.createdAt) {
                        latestByPeer.put(peer, msg);
                    }
                    int count = countByPeer.containsKey(peer) ? countByPeer.get(peer) : 0;
                    countByPeer.put(peer, count + 1);
                }
            }
            for (String peer : latestByPeer.keySet()) {
                WSModels.DirectMessage latest = latestByPeer.get(peer);
                int count = countByPeer.containsKey(peer) ? countByPeer.get(peer) : 1;
                RecentChatCache.setUnreadCount(context, peer, null, null,
                        WSMessagePreview.direct(latest), latest.createdAt, count);
            }
            for (WSModels.DirectMessage msg : messages) {
                dispatcher.dispatch(msg);
            }
        } catch (Exception e) {
        }
    }

    public void handleGroupUnread(Context context, String response, GroupMessageDispatcher dispatcher) {
        if (response == null || dispatcher == null) {
            return;
        }
        try {
            JSONObject obj = new JSONObject(response);
            JSONArray arr = obj.getJSONArray("messages");
            List<WSModels.GroupMessage> messages = new ArrayList<>();
            HashMap<String, WSModels.GroupMessage> latestByGroup = new HashMap<>();
            HashMap<String, Integer> countByGroup = new HashMap<>();
            HashMap<String, Boolean> mentionByGroup = new HashMap<>();
            String myUid = getMyUid(context);
            boolean canCheckMention = myUid != null && !myUid.isEmpty();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject mObj = arr.getJSONObject(i);
                WSModels.GroupMessage msg = new WSModels.GroupMessage();
                msg.id = mObj.optString("id");
                msg.groupId = mObj.optString("group_id");
                msg.fromUid = mObj.optString("from_uid");
                msg.body = mObj.optString("body");
                msg.msgType = mObj.optString("msg_type", "text");
                msg.mediaUrl = mObj.optString("media_url");
                msg.thumbUrl = mObj.optString("thumb_url");
                msg.durationMs = mObj.optInt("duration_ms", 0);
                msg.createdAt = mObj.optLong("created_at");
                MessageFieldRepair.repairGroup(msg);
                messages.add(msg);

                String groupId = msg.groupId;
                if (groupId != null && groupId.length() > 0) {
                    WSModels.GroupMessage latest = latestByGroup.get(groupId);
                    if (latest == null || msg.createdAt > latest.createdAt) {
                        latestByGroup.put(groupId, msg);
                    }
                    int count = countByGroup.containsKey(groupId) ? countByGroup.get(groupId) : 0;
                    countByGroup.put(groupId, count + 1);
                    if (canCheckMention && !Boolean.TRUE.equals(mentionByGroup.get(groupId))) {
                        if (msg.fromUid == null || !msg.fromUid.equals(myUid)) {
                            MessagePayload payload = MessagePayload.fromBody(msg.body);
                            if (payload.hasMention(myUid)) {
                                mentionByGroup.put(groupId, true);
                            } else if (!mentionByGroup.containsKey(groupId)) {
                                mentionByGroup.put(groupId, false);
                            }
                        }
                    }
                }
            }
            for (String groupId : latestByGroup.keySet()) {
                WSModels.GroupMessage latest = latestByGroup.get(groupId);
                int count = countByGroup.containsKey(groupId) ? countByGroup.get(groupId) : 1;
                GroupRecentChatCache.setUnreadCount(context, groupId, null, null,
                        WSMessagePreview.group(latest), latest.createdAt, count);
                if (canCheckMention) {
                    boolean mentionUnread = Boolean.TRUE.equals(mentionByGroup.get(groupId));
                    GroupRecentChatCache.setMentionUnread(context, groupId, mentionUnread);
                }
            }
            for (WSModels.GroupMessage msg : messages) {
                dispatcher.dispatch(msg);
            }
        } catch (Exception e) {
        }
    }

    private String getMyUid(Context context) {
        if (context == null) {
            return "";
        }
        SharedPreferences prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE);
        return prefs.getString("my_uid", "");
    }
}
