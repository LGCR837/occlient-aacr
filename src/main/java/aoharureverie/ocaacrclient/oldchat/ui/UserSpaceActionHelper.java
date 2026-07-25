package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.FriendCache;
import aoharureverie.ocaacrclient.oldchat.models.User;
import org.json.JSONObject;
import java.util.List;

class UserSpaceActionHelper {
    private UserSpaceActionHelper() {
    }

    static boolean isFriend(Context context, String uid) {
        if (context == null || uid == null || uid.isEmpty()) {
            return false;
        }
        List<User> friends = FriendCache.getFriends(context);
        for (User u : friends) {
            if (uid.equals(u.uid)) {
                return true;
            }
        }
        return false;
    }

    static void bindActions(UserSpaceActivity activity, View btnPrimary, View btnReport,
                            boolean isFriend, String profileUid, String profileName,
                            String profileAvatar, String token) {
        if (activity == null) {
            return;
        }
        final UserSpaceActivity activityFinal = activity;
        final String profileUidFinal = profileUid;
        final String profileNameFinal = profileName;
        final String profileAvatarFinal = profileAvatar;
        final String tokenFinal = token;
        if (btnPrimary != null) {
            if (isFriend) {
                if (btnPrimary instanceof TextView) {
                    ((TextView) btnPrimary).setText("发消息");
                }
                btnPrimary.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openChat(activityFinal, profileUidFinal, profileNameFinal, profileAvatarFinal);
                    }
                });
            } else {
                if (btnPrimary instanceof TextView) {
                    ((TextView) btnPrimary).setText("加好友");
                }
                btnPrimary.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sendFriendRequest(activityFinal, tokenFinal, profileUidFinal);
                    }
                });
            }
        }
        if (btnReport != null) {
            btnReport.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showReportDialog(activityFinal, tokenFinal, profileUidFinal);
                }
            });
        }
    }

    private static void openChat(Context context, String uid, String name, String avatar) {
        if (context == null || uid == null || uid.isEmpty()) {
            return;
        }
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra("friend_uid", uid);
        intent.putExtra("friend_name", name);
        intent.putExtra("friend_avatar", avatar);
        context.startActivity(intent);
    }

    private static void sendFriendRequest(Context context, String token, String uid) {
        if (context == null || uid == null || uid.isEmpty()) {
            return;
        }
        final Context contextFinal = context;
        try {
            JSONObject json = new JSONObject();
            json.put("to_uid", uid);
            HttpUtil.post("/friends/request", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    Toast.makeText(contextFinal, R.string.friend_request_sent, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(int code, String error) {
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    if (code == 409) {
                        if (FriendRequestErrorHelper.isPending(error)) {
                            Toast.makeText(contextFinal, R.string.friend_request_pending_processing, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(contextFinal, R.string.friend_request_conflict, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(contextFinal, R.string.friend_request_failed, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (Exception e) {
            Toast.makeText(contextFinal, R.string.friend_request_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private static void showReportDialog(UserSpaceActivity activity, String token, String targetUid) {
        if (activity == null || targetUid == null || targetUid.isEmpty()) {
            return;
        }
        EditText etReason = new EditText(activity);
        final UserSpaceActivity activityFinal = activity;
        final String tokenFinal = token;
        final String targetUidFinal = targetUid;
        final EditText etReasonFinal = etReason;
        etReason.setHint("如：骚扰、诈骗、辱骂");
        int pad = (int) (activity.getResources().getDisplayMetrics().density * 16);
        etReason.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(activity)
                .setTitle("举报用户")
                .setView(etReason)
                .setPositiveButton("提交", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        submitReport(activityFinal, tokenFinal, targetUidFinal, etReasonFinal.getText().toString().trim());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static void submitReport(Context context, String token, String targetUid, String reason) {
        final Context contextFinal = context;
        try {
            JSONObject json = new JSONObject();
            json.put("target_uid", targetUid);
            json.put("reason", reason == null ? "" : reason);
            HttpUtil.post("/reports/user", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    Toast.makeText(contextFinal, "举报已提交，已进入公开法庭", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(int code, String error) {
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    Toast.makeText(contextFinal, "举报失败: " + code, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(contextFinal, "举报失败", Toast.LENGTH_SHORT).show();
        }
    }
}
