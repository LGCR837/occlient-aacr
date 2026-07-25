package aoharureverie.ocaacrclient.oldchat.models;

public class Message {
    public static final int STATUS_NONE = 0;
    public static final int STATUS_SENT = 1;
    public static final int STATUS_DELIVERED = 2;
    public static final int STATUS_READ = 3;

    public String id;
    public String thread_id;
    public String from_uid;
    public String body;
    public String msg_type;
    public String media_url;
    public String thumb_url;
    public int duration_ms;
    public long created_at;
    public int status = STATUS_NONE;

    public String recall_edit_type;
    public String recall_edit_text;

    public Message() {}
    public Message(String body) { this.body = body; }
}
