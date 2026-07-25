package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.FriendNameResolver;
import aoharureverie.ocaacrclient.oldchat.models.User;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class GroupCreateActivity extends BaseActivity {
    private EditText etGroupName;
    private ListView lvFriends;
    private String token;
    private final List<User> friends = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_create);

        etGroupName = findViewByIdCompat(R.id.etGroupName);
        lvFriends = findViewByIdCompat(R.id.lvFriends);

        SharedPreferences prefs = getSharedPreferences("auth", Context.MODE_PRIVATE);
        token = prefs.getString("access_token", "");

        findViewByIdCompat(R.id.btnCreateGroup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createGroup();
            }
        });
        View btnBack = (View) findViewByIdCompat(R.id.btnGroupCreateBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        loadFriends();
    }

    private void loadFriends() {
        HttpUtil.get("/friends", token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject obj = new JSONObject(response);
                    JSONArray arr = obj.getJSONArray("friends");
                    friends.clear();
                    List<String> names = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject uObj = arr.getJSONObject(i);
                        User u = new User();
                        u.id = uObj.optString("id");
                        u.uid = uObj.optString("uid");
                        u.username = uObj.optString("username");
                        u.display_name = uObj.optString("display_name");
                        u.remark_name = uObj.optString("remark_name", "");
                        u.user_title = uObj.optString("user_title");
                        u.avatar_url = uObj.optString("avatar_url");
                        friends.add(u);
                        String title = FriendNameResolver.resolve(u);
                        names.add(title + " (" + u.uid + ")");
                    }
                    adapter = new ArrayAdapter<>(GroupCreateActivity.this,
                            android.R.layout.simple_list_item_multiple_choice, names);
                    lvFriends.setAdapter(adapter);
                } catch (Exception e) {
                    Toast.makeText(GroupCreateActivity.this, "加载好友失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(int code, String error) {
                if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                    return;
                }
                Toast.makeText(GroupCreateActivity.this, "加载好友失败: " + code, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createGroup() {
        String name = etGroupName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入群名称", Toast.LENGTH_SHORT).show();
            return;
        }
        final String nameFinal = name;
        SparseBooleanArray checked = lvFriends.getCheckedItemPositions();
        JSONArray memberUids = new JSONArray();
        for (int i = 0; i < friends.size(); i++) {
            if (checked != null && checked.get(i)) {
                User u = friends.get(i);
                if (u.uid != null && !u.uid.isEmpty()) {
                    memberUids.put(u.uid);
                }
            }
        }
        try {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("member_uids", memberUids);
            HttpUtil.post("/groups/create", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    try {
                        JSONObject obj = new JSONObject(response);
                        String groupId = obj.optString("group_id");
                        Intent intent = new Intent(GroupCreateActivity.this, GroupChatActivity.class);
                        intent.putExtra("group_id", groupId);
                        intent.putExtra("group_name", nameFinal);
                        startActivity(intent);
                        finish();
                    } catch (Exception e) {
                        Toast.makeText(GroupCreateActivity.this, "创建失败", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(int code, String error) {
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    Toast.makeText(GroupCreateActivity.this, "创建失败: " + code, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "创建失败", Toast.LENGTH_SHORT).show();
        }
    }
}
