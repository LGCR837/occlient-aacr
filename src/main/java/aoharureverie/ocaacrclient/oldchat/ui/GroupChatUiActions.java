package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.models.MessageHistoryCache;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;
import java.util.HashSet;
import java.util.List;

class GroupChatUiActions {
    private GroupChatUiActions() {
    }

    static MessagePayload.Quote buildQuote(GroupMessage msg, String displayName) {
        if (msg == null) {
            return null;
        }
        MessagePayload payload = MessagePayload.fromBody(msg.body);
        MessagePayload.Quote quote = new MessagePayload.Quote();
        quote.id = msg.id;
        quote.fromUid = msg.from_uid;
        String name = displayName;
        if (name == null || name.isEmpty()) {
            name = msg.from_uid;
        }
        quote.fromName = name;
        quote.type = msg.msg_type == null ? "text" : msg.msg_type.toLowerCase();
        quote.mediaKind = payload.mediaKind;
        quote.text = ChatMessageUtil.previewForType(quote.type, msg.body);
        if (("image".equals(quote.type) || "video".equals(quote.type))
                && msg.media_url != null && !msg.media_url.isEmpty()) {
            quote.thumbUrl = msg.thumb_url != null && !msg.thumb_url.isEmpty() ? msg.thumb_url : msg.media_url;
        }
        return quote;
    }

    static void applyQuote(View quotePreview, TextView tvQuotePreview, MessagePayload.Quote quote) {
        if (quotePreview == null || tvQuotePreview == null) {
            return;
        }
        if (quote == null) {
            quotePreview.setVisibility(View.GONE);
            tvQuotePreview.setText("");
            return;
        }
        String sender = quote.fromName != null && !quote.fromName.isEmpty() ? quote.fromName : quote.fromUid;
        String content = ChatMessageUtil.quotePreview(quote.type, quote.mediaKind, quote.text);
        tvQuotePreview.setText((sender == null ? "" : sender) + ": " + content);
        quotePreview.setVisibility(View.VISIBLE);
    }

    static void clearQuote(View quotePreview, TextView tvQuotePreview) {
        if (quotePreview != null) {
            quotePreview.setVisibility(View.GONE);
        }
        if (tvQuotePreview != null) {
            tvQuotePreview.setText("");
        }
    }

    static void insertMention(EditText etInput, GroupMessage msg, String displayName) {
        if (etInput == null || msg == null || msg.from_uid == null || msg.from_uid.isEmpty()) {
            return;
        }
        String name = displayName;
        if (name == null || name.isEmpty()) {
            name = msg.from_uid;
        }
        String mention = "@" + name + " ";
        int start = etInput.getSelectionStart();
        if (start < 0) {
            start = etInput.getText().length();
        }
        etInput.getText().insert(start, mention);
    }

    static void confirmRecall(final GroupChatActivity activity, final String token, final List<GroupMessage> messageList,
                              final HashSet<String> messageIds, final GroupMessageAdapter adapter, final String messageId,
                              final String displayName) {
        if (activity == null || messageId == null) {
            return;
        }
        new android.support.v7.app.AlertDialog.Builder(activity)
                .setTitle("撤回消息")
                .setMessage("确定要撤回这条消息吗？")
                .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        performRecall(activity, token, messageList, messageIds, adapter, messageId, displayName);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static void performRecall(final Context context, final String token, final List<GroupMessage> messageList,
                                      final HashSet<String> messageIds, final GroupMessageAdapter adapter, final String messageId,
                                      final String displayName) {
        String path = "/groups/messages/" + messageId;
        HttpUtil.delete(path, token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                for (int i = 0; i < messageList.size(); i++) {
                    GroupMessage msg = messageList.get(i);
                    if (msg != null && messageId.equals(msg.id)) {
                        String name = displayName == null || displayName.isEmpty() ? "成员" : displayName;
                        String originType = msg.msg_type == null ? "text" : msg.msg_type.toLowerCase();
                        msg.recall_edit_type = originType;
                        if ("text".equals(originType) && msg.body != null) {
                            msg.recall_edit_text = msg.body;
                        } else {
                            msg.recall_edit_text = "";
                        }
                        msg.msg_type = "recall";
                        msg.body = context.getString(R.string.message_recalled_member, name);
                        msg.media_url = "";
                        msg.thumb_url = "";
                        msg.duration_ms = 0;
                        if (msg.group_id != null && !msg.group_id.isEmpty()) {
                            MessageHistoryCache.saveGroupMessages(context, msg.group_id, messageList);
                        }
                        adapter.notifyDataSetChanged();
                        Toast.makeText(context, "消息已撤回", Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
            }

            @Override
            public void onError(int code, String error) {
                Toast.makeText(context, "撤回失败: " + code, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
