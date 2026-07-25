package aoharureverie.ocaacrclient.oldchat.ui;

import android.app.Activity;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.models.GroupRecentChatCache;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayloadBuilder;
import org.json.JSONObject;
import java.io.File;
import java.net.HttpURLConnection;
import java.util.List;

public class GroupMessageSender {
    private static final int MAX_VOICE_DURATION_MS = 60000;

    public interface SendStateListener {
        void onSendState(boolean sending);
    }

    public interface QuoteClearListener {
        void onClearQuote();
    }

    private final Activity context;
    private final GroupChatListHelper listHelper;
    private final TextView btnLoadMore;
    private final EditText input;
    private final String token;
    private final String groupId;
    private final String groupName;
    private final SendStateListener sendStateListener;
    private final QuoteClearListener quoteClearListener;

    public GroupMessageSender(Activity context,
                              GroupChatListHelper listHelper,
                              TextView btnLoadMore,
                              EditText input,
                              String token,
                              String groupId,
                              String groupName,
                              SendStateListener sendStateListener,
                              QuoteClearListener quoteClearListener) {
        this.context = context;
        this.listHelper = listHelper;
        this.btnLoadMore = btnLoadMore;
        this.input = input;
        this.token = token;
        this.groupId = groupId;
        this.groupName = groupName;
        this.sendStateListener = sendStateListener;
        this.quoteClearListener = quoteClearListener;
    }

    public void sendText(String content, MessagePayload.Quote quoteDraft, List<MessagePayload.Mention> mentions) {
        String trimmed = content == null ? "" : content.trim();
        if ((trimmed.length() == 0 && quoteDraft == null) || groupId == null || groupId.length() == 0) {
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("group_id", groupId);
            final String body = MessagePayloadBuilder.buildBody(trimmed, quoteDraft, mentions, null);
            json.put("body", body);
            json.put("msg_type", "text");
            if (sendStateListener != null) {
                sendStateListener.onSendState(true);
            }
            HttpUtil.post("/groups/message/send", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    if (input != null) {
                        input.setText("");
                    }
                    if (quoteClearListener != null) {
                        quoteClearListener.onClearQuote();
                    }
                    GroupRecentChatCache.updateGroupOutgoing(context, groupId, groupName,
                            null, messagePreview("text", body), System.currentTimeMillis());
                    updateLoadMore();
                    GroupMessage sent = listHelper == null ? null : listHelper.parseMessageFromResponse(response);
                    if (sent != null && listHelper != null) {
                        boolean atBottom = listHelper.isAtBottom();
                        listHelper.appendMessage(sent, atBottom);
                    } else if (listHelper != null) {
                        listHelper.loadMessages(token, false, 0, false);
                    }
                    if (sendStateListener != null) {
                        sendStateListener.onSendState(false);
                    }
                }

                @Override
                public void onError(int code, String error) {
                    if (sendStateListener != null) {
                        sendStateListener.onSendState(false);
                    }
                    if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                        HttpUtil.showAuthWarning();
                        return;
                    }
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    String tip = resolveSendErrorTip(code, error);
                    if (tip != null) {
                        Toast.makeText(context, tip, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(context, "发送失败: " + code, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(context, "发送失败", Toast.LENGTH_SHORT).show();
            if (sendStateListener != null) {
                sendStateListener.onSendState(false);
            }
        }
    }

    public void sendMedia(String type, String url, String thumbUrl, int durationMs, MessagePayload.Quote quoteDraft) {
        String body = MessagePayloadBuilder.buildBody("", quoteDraft, null, null);
        sendMediaInternal(type, url, thumbUrl, durationMs, body);
    }

    public void sendEmojiFromPath(String path, boolean isGif, final MessagePayload.Quote quoteDraft) {
        if (path == null || path.length() == 0) {
            return;
        }
        File file = new File(path);
        if (!file.exists()) {
            Toast.makeText(context, "表情文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sendStateListener != null) {
            sendStateListener.onSendState(true);
        }
        EmojiSendHelper.send(context, file, isGif, token, new EmojiSendHelper.Callback() {
            @Override
            public void onUploaded(String url) {
                try {
                    String body = MessagePayloadBuilder.buildBody("", quoteDraft, null, "emoji");
                    sendMediaInternal("image", url, "", 0, body);
                } catch (Exception e) {
                    Toast.makeText(context, "发送表情失败", Toast.LENGTH_SHORT).show();
                }
                if (sendStateListener != null) {
                    sendStateListener.onSendState(false);
                }
            }

            @Override
            public void onError(String message) {
                if (sendStateListener != null) {
                    sendStateListener.onSendState(false);
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendMediaInternal(String type, String url, String thumbUrl, int durationMs, String body) {
        if (groupId == null || groupId.length() == 0 || type == null || type.length() == 0 || url == null || url.length() == 0) {
            return;
        }
        final String typeFinal = type;
        final String bodyFinal = body;
        try {
            if ("voice".equals(type)) {
                if (durationMs <= 0) {
                    Toast.makeText(context, "语音时长无效", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (durationMs > MAX_VOICE_DURATION_MS) {
                    durationMs = MAX_VOICE_DURATION_MS;
                }
            }
            JSONObject json = new JSONObject();
            json.put("group_id", groupId);
            json.put("msg_type", type);
            json.put("media_url", url);
            if (thumbUrl != null && thumbUrl.length() > 0) {
                json.put("thumb_url", thumbUrl);
            }
            if (durationMs > 0) {
                json.put("duration_ms", durationMs);
            }
            if (body != null && body.length() > 0) {
                json.put("body", body);
            }
            if (sendStateListener != null) {
                sendStateListener.onSendState(true);
            }
            HttpUtil.post("/groups/message/send", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    GroupRecentChatCache.updateGroupOutgoing(context, groupId, groupName,
                            null, messagePreview(typeFinal, bodyFinal), System.currentTimeMillis());
                    updateLoadMore();
                    GroupMessage sent = listHelper == null ? null : listHelper.parseMessageFromResponse(response);
                    if (sent != null && listHelper != null) {
                        listHelper.appendMessage(sent, true);
                    } else if (listHelper != null) {
                        listHelper.loadMessages(token, false, 0, false);
                    }
                    if (quoteClearListener != null) {
                        quoteClearListener.onClearQuote();
                    }
                    if (sendStateListener != null) {
                        sendStateListener.onSendState(false);
                    }
                }

                @Override
                public void onError(int code, String error) {
                    if (sendStateListener != null) {
                        sendStateListener.onSendState(false);
                    }
                    if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                        HttpUtil.showAuthWarning();
                        return;
                    }
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    if (error != null && error.contains("duration_too_long")) {
                        Toast.makeText(context, "语音不能超过60秒", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String tip = resolveSendErrorTip(code, error);
                    if (tip != null) {
                        Toast.makeText(context, tip, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(context, "发送失败: " + code, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(context, "发送失败", Toast.LENGTH_SHORT).show();
            if (sendStateListener != null) {
                sendStateListener.onSendState(false);
            }
        }
    }

    private String resolveSendErrorTip(int code, String error) {
        if (code != 403 || error == null) {
            return null;
        }
        String lower = error.toLowerCase();
        if (lower.contains("not_member")) {
            return "你已不在该群，无法发送消息";
        }
        if (lower.contains("group_muted")) {
            return "当前群已全员禁言";
        }
        if (lower.contains("user_banned")) {
            return "账号已被封禁，无法发送消息";
        }
        if (lower.contains("video_disabled")) {
            return "服务器禁用了视频/3GP上传，请联系管理员";
        }
        return "发送失败: 403";
    }


    private void updateLoadMore() {
        if (btnLoadMore != null) {
            btnLoadMore.setEnabled(true);
            btnLoadMore.setText(R.string.load_more_messages);
        }
    }

    private String messagePreview(String type, String body) {
        return ChatMessageUtil.previewForType(type, body);
    }
}
