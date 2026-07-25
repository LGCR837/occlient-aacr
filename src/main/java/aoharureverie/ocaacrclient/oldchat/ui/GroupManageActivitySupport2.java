package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Intent;
import android.support.v7.app.AlertDialog;
import android.widget.EditText;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;

import org.json.JSONObject;

class GroupManageActivitySupport2 extends GroupManageActivitySupport {
    protected void openSearchMessages() {
        if (groupId == null || groupId.length() == 0) {
            Toast.makeText(this, "群ID无效", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, ChatSearchActivity.class);
        intent.putExtra(ChatSearchActivity.EXTRA_MODE, ChatSearchActivity.MODE_GROUP);
        intent.putExtra(ChatSearchActivity.EXTRA_GROUP_ID, groupId);
        intent.putExtra(ChatSearchActivity.EXTRA_GROUP_NAME, groupName);
        intent.putExtra(ChatSearchActivity.EXTRA_GROUP_ROLE, myRole);
        startActivity(intent);
    }

    protected void showGroupReportDialog() {
        if (groupId == null || groupId.length() == 0) {
            Toast.makeText(this, "群ID无效", Toast.LENGTH_SHORT).show();
            return;
        }
        final EditText etReason = new EditText(this);
        etReason.setHint("如：违规内容、诈骗、辱骂");
        int pad = (int) (getResources().getDisplayMetrics().density * 16);
        etReason.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("举报群聊")
                .setView(etReason)
                .setPositiveButton("提交", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        submitGroupReport(etReason.getText().toString().trim());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    protected void submitGroupReport(String reason) {
        try {
            JSONObject json = new JSONObject();
            json.put("group_id", groupId);
            json.put("reason", reason == null ? "" : reason);
            HttpUtil.post("/reports/group", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    Toast.makeText(GroupManageActivitySupport2.this, "举报已提交", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(int code, String error) {
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    Toast.makeText(GroupManageActivitySupport2.this, "举报失败: " + code, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(GroupManageActivitySupport2.this, "举报失败", Toast.LENGTH_SHORT).show();
        }
    }
}
