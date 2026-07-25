package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.Message;
import aoharureverie.ocaacrclient.oldchat.models.MessageHistoryCache;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;
import java.util.HashSet;
import java.util.List;

class DirectChatUiActions {
    private DirectChatUiActions() {
    }

    static MessagePayload.Quote buildQuote(Message msg, String fallbackName) {
        if (msg == null) {
            return null;
        }
        MessagePayload payload = MessagePayload.fromBody(msg.body);
        MessagePayload.Quote quote = new MessagePayload.Quote();
        quote.id = msg.id;
        quote.fromUid = msg.from_uid;
        String name = fallbackName;
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

    static void confirmRecall(final ChatActivity activity, final String token, final String friendUID,
                              final List<Message> messageList, final HashSet<String> messageIds,
                              final MessageAdapter adapter, final String messageId) {
        if (activity == null || messageId == null) {
            return;
        }
        new android.support.v7.app.AlertDialog.Builder(activity)
                .setTitle("撤回消息")
                .setMessage("确定要撤回这条消息吗？")
                .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        performRecall(activity, token, friendUID, messageList, messageIds, adapter, messageId);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static void performRecall(final Context context, final String token, final String friendUID,
                                      final List<Message> messageList, final HashSet<String> messageIds,
                                      final MessageAdapter adapter, final String messageId) {
        String path = "/direct/messages/" + messageId;
        HttpUtil.delete(path, token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                for (int i = 0; i < messageList.size(); i++) {
                    Message msg = messageList.get(i);
                    if (msg != null && messageId.equals(msg.id)) {
                        String originType = msg.msg_type == null ? "text" : msg.msg_type.toLowerCase();
                        msg.recall_edit_type = originType;
                        if ("text".equals(originType) && msg.body != null) {
                            msg.recall_edit_text = msg.body;
                        } else {
                            msg.recall_edit_text = "";
                        }
                        msg.msg_type = "recall";
                        msg.body = context.getString(R.string.message_recalled_self);
                        msg.media_url = "";
                        msg.thumb_url = "";
                        msg.duration_ms = 0;
                        if (friendUID != null && !friendUID.isEmpty()) {
                            MessageHistoryCache.saveDirectMessages(context, friendUID, messageList);
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
