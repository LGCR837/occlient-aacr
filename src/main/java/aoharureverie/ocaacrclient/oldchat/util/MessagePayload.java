package aoharureverie.ocaacrclient.oldchat.util;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class MessagePayload {
    public static final int VERSION = 1;
    public static final int VERSION_MAX = 10;

    public static class Quote {
        public String id;
        public String fromUid;
        public String fromName;
        public String type;
        public String text;
        public String mediaKind;
        public String thumbUrl;
    }

    public static class Mention {
        public String uid;
        public String name;
    }

    public String text;
    public String mediaKind;
    public Quote quote;
    public List<Mention> mentions;

    public MessagePayload() {
        mentions = new ArrayList<>();
    }

    public static MessagePayload fromBody(String body) {
        MessagePayload payload = new MessagePayload();
        payload.text = body == null ? "" : body;
        if (body == null) {
            return payload;
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return payload;
        }
        try {
            JSONObject obj = new JSONObject(trimmed);
            int v = obj.optInt("v", 0);
            if (v < 0 || v > VERSION_MAX) {
                return payload;
            }
            payload.text = obj.optString("text", "");
            payload.mediaKind = obj.optString("media_kind", "");
            JSONObject qObj = obj.optJSONObject("quote");
            if (qObj != null) {
                Quote q = new Quote();
                q.id = qObj.optString("id", "");
                q.fromUid = qObj.optString("from_uid", "");
                q.fromName = qObj.optString("from_name", "");
                q.type = qObj.optString("type", "");
                q.text = qObj.optString("text", "");
                q.mediaKind = qObj.optString("media_kind", "");
                q.thumbUrl = qObj.optString("thumb_url", "");
                payload.quote = q;
            }
            JSONArray arr = obj.optJSONArray("mentions");
            if (arr != null) {
                payload.mentions.clear();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject mObj = arr.optJSONObject(i);
                    if (mObj == null) {
                        continue;
                    }
                    Mention m = new Mention();
                    m.uid = mObj.optString("uid", "");
                    m.name = mObj.optString("name", "");
                    if (m.uid != null && !m.uid.isEmpty()) {
                        payload.mentions.add(m);
                    }
                }
            }
        } catch (Exception e) {
            return payload;
        }
        return payload;
    }

    public String toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("v", VERSION);
            obj.put("text", text == null ? "" : text);
            if (mediaKind != null && !mediaKind.isEmpty()) {
                obj.put("media_kind", mediaKind);
            }
            if (quote != null) {
                JSONObject qObj = new JSONObject();
                qObj.put("id", quote.id == null ? "" : quote.id);
                qObj.put("from_uid", quote.fromUid == null ? "" : quote.fromUid);
                qObj.put("from_name", quote.fromName == null ? "" : quote.fromName);
                qObj.put("type", quote.type == null ? "" : quote.type);
                qObj.put("text", quote.text == null ? "" : quote.text);
                if (quote.mediaKind != null && !quote.mediaKind.isEmpty()) {
                    qObj.put("media_kind", quote.mediaKind);
                }
                if (quote.thumbUrl != null && !quote.thumbUrl.isEmpty()) {
                    qObj.put("thumb_url", quote.thumbUrl);
                }
                obj.put("quote", qObj);
            }
            if (mentions != null && !mentions.isEmpty()) {
                JSONArray arr = new JSONArray();
                for (Mention m : mentions) {
                    if (m == null || m.uid == null || m.uid.isEmpty()) {
                        continue;
                    }
                    JSONObject mObj = new JSONObject();
                    mObj.put("uid", m.uid);
                    mObj.put("name", m.name == null ? "" : m.name);
                    arr.put(mObj);
                }
                if (arr.length() > 0) {
                    obj.put("mentions", arr);
                }
            }
            return obj.toString();
        } catch (Exception e) {
            return text == null ? "" : text;
        }
    }

    public boolean hasMention(String uid) {
        if (uid == null || uid.isEmpty() || mentions == null) {
            return false;
        }
        for (Mention m : mentions) {
            if (m != null && uid.equals(m.uid)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasExtras() {
        return (quote != null) || (mentions != null && !mentions.isEmpty()) ||
                (mediaKind != null && !mediaKind.isEmpty());
    }
}
