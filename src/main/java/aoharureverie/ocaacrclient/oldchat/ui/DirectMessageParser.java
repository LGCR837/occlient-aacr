package aoharureverie.ocaacrclient.oldchat.ui;

import aoharureverie.ocaacrclient.oldchat.models.Message;
import aoharureverie.ocaacrclient.oldchat.util.MessageFieldRepair;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class DirectMessageParser {
    public static List<Message> parse(String response) throws Exception {
        JSONObject obj = new JSONObject(response);
        JSONArray arr = obj.getJSONArray("messages");
        List<Message> incoming = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject mObj = arr.getJSONObject(i);
            Message m = new Message();
            m.id = mObj.getString("id");
            m.from_uid = mObj.getString("from_uid");
            m.body = mObj.getString("body");
            m.msg_type = mObj.optString("msg_type", "text");
            m.media_url = mObj.optString("media_url");
            m.thumb_url = mObj.optString("thumb_url");
            m.duration_ms = mObj.optInt("duration_ms", 0);
            m.created_at = ChatMessageUtil.sanitizeTimestamp(mObj.optLong("created_at", 0));
            m.status = ChatMessageUtil.parseStatus(mObj);
            MessageFieldRepair.repairDirect(m);
            incoming.add(m);
        }
        return incoming;
    }

    public static int parseEffectiveOffset(String response, int fallback) {
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

}
