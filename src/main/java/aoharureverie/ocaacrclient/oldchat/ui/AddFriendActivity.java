package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import org.json.JSONObject;

public class AddFriendActivity extends BaseActivity {
    private EditText etFriendUsername;
    private View btnSubmit;
    private EditText etGroupId;
    private View btnJoinGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_friend);

        etFriendUsername = findViewByIdCompat(R.id.etFriendUsername);
        btnSubmit = findViewByIdCompat(R.id.btnSubmit);
        etGroupId = findViewByIdCompat(R.id.etGroupId);
        btnJoinGroup = findViewByIdCompat(R.id.btnJoinGroup);
        View btnBack = (View) findViewByIdCompat(R.id.btnAddFriendBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        final String token = prefs.getString("access_token", "");

        btnSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = etFriendUsername.getText().toString().trim();
                if (username.isEmpty()) {
                    Toast.makeText(AddFriendActivity.this, R.string.friend_request_empty, Toast.LENGTH_SHORT).show();
                    return;
                }

                btnSubmit.setEnabled(false);
                try {
                    JSONObject json = new JSONObject();
                    json.put("to_uid", username);

                    HttpUtil.post("/friends/request", json, token, new HttpUtil.Callback() {
                        @Override
                        public void onSuccess(String response) {
                            btnSubmit.setEnabled(true);
                            Toast.makeText(AddFriendActivity.this, R.string.friend_request_sent, Toast.LENGTH_SHORT).show();
                            finish();
                        }

                        @Override
                        public void onError(int code, String error) {
                            btnSubmit.setEnabled(true);
                            if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                                return;
                            }
                            if (code == 404) {
                                Toast.makeText(AddFriendActivity.this, R.string.friend_request_not_found, Toast.LENGTH_SHORT).show();
                            } else if (code == 409) {
                                if (FriendRequestErrorHelper.isPending(error)) {
                                    Toast.makeText(AddFriendActivity.this, R.string.friend_request_pending_processing, Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(AddFriendActivity.this, R.string.friend_request_conflict, Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(AddFriendActivity.this, R.string.friend_request_failed, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (Exception e) {
                    btnSubmit.setEnabled(true);
                    Toast.makeText(AddFriendActivity.this, R.string.friend_request_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnJoinGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String groupId = etGroupId.getText().toString().trim().toUpperCase();
                if (groupId.isEmpty()) {
                    Toast.makeText(AddFriendActivity.this, R.string.group_id_empty, Toast.LENGTH_SHORT).show();
                    return;
                }
                btnJoinGroup.setEnabled(false);
                try {
                    JSONObject json = new JSONObject();
                    json.put("group_id", groupId);

                    HttpUtil.post("/groups/join", json, token, new HttpUtil.Callback() {
                        @Override
                        public void onSuccess(String response) {
                            btnJoinGroup.setEnabled(true);
                            try {
                                JSONObject obj = new JSONObject(response);
                                String status = obj.optString("status", "");
                                if ("joined".equalsIgnoreCase(status)) {
                                    Toast.makeText(AddFriendActivity.this, R.string.group_joined, Toast.LENGTH_SHORT).show();
                                } else if ("pending".equalsIgnoreCase(status)) {
                                    Toast.makeText(AddFriendActivity.this, R.string.group_join_pending, Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(AddFriendActivity.this, R.string.group_join_failed, Toast.LENGTH_SHORT).show();
                                }
                                finish();
                            } catch (Exception e) {
                                Toast.makeText(AddFriendActivity.this, R.string.group_join_failed, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(int code, String error) {
                            btnJoinGroup.setEnabled(true);
                            if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                                return;
                            }
                            if (code == 404) {
                                Toast.makeText(AddFriendActivity.this, R.string.group_not_found, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(AddFriendActivity.this, R.string.group_join_failed, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (Exception e) {
                    btnJoinGroup.setEnabled(true);
                    Toast.makeText(AddFriendActivity.this, R.string.group_join_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
