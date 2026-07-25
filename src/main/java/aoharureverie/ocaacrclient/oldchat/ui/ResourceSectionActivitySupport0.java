package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.FriendCache;
import aoharureverie.ocaacrclient.oldchat.models.FriendNameResolver;
import aoharureverie.ocaacrclient.oldchat.models.Group;
import aoharureverie.ocaacrclient.oldchat.models.GroupCache;
import aoharureverie.ocaacrclient.oldchat.models.ResourceItem;
import aoharureverie.ocaacrclient.oldchat.models.User;
import aoharureverie.ocaacrclient.oldchat.util.ClipboardUtil;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayloadBuilder;
import aoharureverie.ocaacrclient.oldchat.util.NetworkStateManager;

import org.json.JSONObject;

abstract class ResourceSectionActivitySupport0 extends ResourceSectionActivitySupport1 {
    @Override
    public void onDownload(ResourceItem item) {
        if (item == null || item.url == null || item.url.isEmpty()) {
            return;
        }
        showDownloadDialog(item.url);
    }

    @Override
    public void onShare(final ResourceItem item) {
        Toast.makeText(this, "资源分享功能已下线", Toast.LENGTH_SHORT).show();
    }

    protected void pickFriendAndSend(final ResourceItem item) {
        final java.util.List<User> friends = FriendCache.getFriends(this);
        ShareTargetSearchPicker.pickFriend(this, friends, "选择好友", new ShareTargetSearchPicker.FriendSelectCallback() {
            @Override
            public void onSelected(User user) {
                if (user == null || user.uid == null || user.uid.length() == 0) {
                    return;
                }
                sendResourceToFriend(item, user);
            }
        });
    }

    protected void pickGroupAndSend(final ResourceItem item) {
        final java.util.List<Group> groups = GroupCache.getGroups(this);
        ShareTargetSearchPicker.pickGroup(this, groups, "选择群聊", new ShareTargetSearchPicker.GroupSelectCallback() {
            @Override
            public void onSelected(Group group) {
                if (group == null || group.id == null || group.id.length() == 0) {
                    return;
                }
                sendResourceToGroup(item, group);
            }
        });
    }

    protected void sendResourceToFriend(final ResourceItem item, final User friend) {
        if (!NetworkStateManager.getInstance().isServerAvailable()) {
            Toast.makeText(this, "网络不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String text = buildResourceShareText(item);
            String body = MessagePayloadBuilder.buildBody(text, null, null, null);
            JSONObject json = new JSONObject();
            json.put("to_uid", friend.uid);
            json.put("msg_type", "resource");
            json.put("media_url", item.url);
            json.put("body", body);
            HttpUtil.post("/direct/send", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    Toast.makeText(ResourceSectionActivitySupport0.this, "已发送", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(ResourceSectionActivitySupport0.this, ChatActivity.class);
                    intent.putExtra("friend_uid", friend.uid);
                    intent.putExtra("friend_name", FriendNameResolver.resolve(friend));
                    intent.putExtra("friend_avatar", friend.avatar_url);
                    startActivity(intent);
                }

                @Override
                public void onError(int code, String error) {
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    Toast.makeText(ResourceSectionActivitySupport0.this, "发送失败: " + code, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "发送失败", Toast.LENGTH_SHORT).show();
        }
    }

    protected void sendResourceToGroup(final ResourceItem item, final Group group) {
        if (!NetworkStateManager.getInstance().isServerAvailable()) {
            Toast.makeText(this, "网络不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String text = buildResourceShareText(item);
            String body = MessagePayloadBuilder.buildBody(text, null, null, null);
            JSONObject json = new JSONObject();
            json.put("group_id", group.id);
            json.put("msg_type", "resource");
            json.put("media_url", item.url);
            json.put("body", body);
            HttpUtil.post("/groups/message/send", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    Toast.makeText(ResourceSectionActivitySupport0.this, "已发送", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(ResourceSectionActivitySupport0.this, GroupChatActivity.class);
                    intent.putExtra("group_id", group.id);
                    intent.putExtra("group_name", group.name);
                    intent.putExtra("group_avatar", group.avatar_url);
                    intent.putExtra("group_role", group.role);
                    startActivity(intent);
                }

                @Override
                public void onError(int code, String error) {
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    Toast.makeText(ResourceSectionActivitySupport0.this, "发送失败: " + code, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "发送失败", Toast.LENGTH_SHORT).show();
        }
    }

    protected String buildResourceShareText(ResourceItem item) {
        String name = item == null || item.name == null ? "" : item.name;
        String size = formatShareSize(item == null ? 0 : item.size_bytes);
        return "资源: " + name + "\n" + "大小: " + size + "\n点击气泡下载";
    }

    protected String formatShareSize(long sizeBytes) {
        if (sizeBytes <= 0) {
            return "0B";
        }
        float size = sizeBytes;
        if (size < 1024) {
            return (int) size + "B";
        }
        size = size / 1024f;
        if (size < 1024) {
            return String.format(java.util.Locale.getDefault(), "%.1fKB", size);
        }
        size = size / 1024f;
        if (size < 1024) {
            return String.format(java.util.Locale.getDefault(), "%.1fMB", size);
        }
        size = size / 1024f;
        return String.format(java.util.Locale.getDefault(), "%.1fGB", size);
    }

    protected void showDownloadDialog(final String url) {
        final String resolved = resolveUrl(url);
        new AlertDialog.Builder(this)
                .setTitle("下载资源")
                .setMessage(resolved)
                .setPositiveButton("复制链接", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        ClipboardUtil.copyText(ResourceSectionActivitySupport0.this, resolved);
                    }
                })
                .setNegativeButton("浏览器下载", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(resolved));
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(ResourceSectionActivitySupport0.this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNeutralButton("取消", null)
                .show();
    }
}
