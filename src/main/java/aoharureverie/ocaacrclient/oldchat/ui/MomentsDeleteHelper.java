package aoharureverie.ocaacrclient.oldchat.ui;

import android.app.Activity;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.Moment;

import org.json.JSONObject;

import java.util.List;

final class MomentsDeleteHelper {
    private MomentsDeleteHelper() {
    }

    static boolean isOwnMoment(String myUid, Moment moment) {
        if (moment == null || myUid == null || myUid.isEmpty()) {
            return false;
        }
        return myUid.equals(moment.from_uid);
    }

    static void confirmDeleteMoment(final Activity activity, final String token,
                                    final Moment moment, final int index,
                                    final List<Moment> moments, final MomentAdapter adapter) {
        final Moment target = moment;
        final int targetIndex = index;
        new AlertDialog.Builder(activity)
                .setTitle("删除动态")
                .setMessage("确定删除这条动态吗？")
                .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        deleteMoment(activity, token, target, targetIndex, moments, adapter);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static void deleteMoment(final Activity activity, String token,
                                     Moment moment, int index,
                                     final List<Moment> moments, final MomentAdapter adapter) {
        if (moment == null || moment.id == null || moment.id.isEmpty()) {
            return;
        }
        final int targetIndex = index;
        try {
            JSONObject json = new JSONObject();
            json.put("moment_id", moment.id);
            HttpUtil.post("/moments/delete", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    if (targetIndex >= 0 && targetIndex < moments.size()) {
                        moments.remove(targetIndex);
                        adapter.notifyDataSetChanged();
                    }
                    Toast.makeText(activity, "已删除", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(int code, String error) {
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    Toast.makeText(activity, "删除失败: " + code, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(activity, "删除失败", Toast.LENGTH_SHORT).show();
        }
    }
}
