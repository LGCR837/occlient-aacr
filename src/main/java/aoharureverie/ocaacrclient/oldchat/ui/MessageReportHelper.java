package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.models.Message;
import org.json.JSONObject;

public class MessageReportHelper {
    private static final String AUTH_PREFS = "auth";

    private MessageReportHelper() {
    }

    public static void reportDirectMessage(Context context, Message msg) {
        if (context == null || msg == null) {
            return;
        }
        String targetUid = safe(msg.from_uid);
        if (targetUid.isEmpty()) {
            Toast.makeText(context, "无法举报该消息", Toast.LENGTH_SHORT).show();
            return;
        }
        String detail = buildDirectDetail(msg);
        showReportDialog(context, targetUid, detail);
    }

    public static void reportGroupMessage(Context context, GroupMessage msg) {
        if (context == null || msg == null) {
            return;
        }
        String targetUid = safe(msg.from_uid);
        if (targetUid.isEmpty()) {
            Toast.makeText(context, "无法举报该消息", Toast.LENGTH_SHORT).show();
            return;
        }
        String detail = buildGroupDetail(msg);
        showReportDialog(context, targetUid, detail);
    }

    private static void showReportDialog(Context context, final String targetUid, final String detail) {
        final Context ctx = context;
        final EditText etReason = new EditText(ctx);
        etReason.setHint("如：骚扰、诈骗、辱骂");
        int pad = (int) (ctx.getResources().getDisplayMetrics().density * 16);
        etReason.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(ctx)
                .setTitle("举报消息")
                .setView(etReason)
                .setPositiveButton("提交", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String reason = etReason.getText() == null ? "" : etReason.getText().toString().trim();
                        submitReport(ctx, targetUid, reason, detail);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static void submitReport(Context context, String targetUid, String reason, String detail) {
        if (context == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE);
        String token = prefs.getString("access_token", "");
        if (token == null || token.length() == 0) {
            Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        String fullReason = reason == null ? "" : reason;
        if (detail != null && detail.length() > 0) {
            if (!TextUtils.isEmpty(fullReason)) {
                fullReason = fullReason + "\n";
            }
            fullReason = fullReason + detail;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("target_uid", targetUid);
            json.put("reason", fullReason);
            HttpUtil.post("/reports/user", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    Toast.makeText(context, "举报已提交，已进入公开法庭", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(int code, String error) {
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    Toast.makeText(context, "举报失败: " + code, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(context, "举报失败", Toast.LENGTH_SHORT).show();
        }
    }

    private static String buildDirectDetail(Message msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("消息类型: ").append(safe(msg.msg_type));
        if (!TextUtils.isEmpty(msg.id)) {
            sb.append("\n消息ID: ").append(msg.id);
        }
        if (!TextUtils.isEmpty(msg.body)) {
            sb.append("\n内容: ").append(truncate(msg.body, 120));
        }
        if (!TextUtils.isEmpty(msg.media_url)) {
            sb.append("\n媒体: ").append(truncate(msg.media_url, 200));
        }
        return sb.toString();
    }

    private static String buildGroupDetail(GroupMessage msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("群ID: ").append(safe(msg.group_id));
        sb.append("\n消息类型: ").append(safe(msg.msg_type));
        if (!TextUtils.isEmpty(msg.id)) {
            sb.append("\n消息ID: ").append(msg.id);
        }
        if (!TextUtils.isEmpty(msg.body)) {
            sb.append("\n内容: ").append(truncate(msg.body, 120));
        }
        if (!TextUtils.isEmpty(msg.media_url)) {
            sb.append("\n媒体: ").append(truncate(msg.media_url, 200));
        }
        return sb.toString();
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen) + "...";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
