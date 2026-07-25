package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.GroupCache;
import aoharureverie.ocaacrclient.oldchat.models.GroupMember;
import aoharureverie.ocaacrclient.oldchat.models.GroupRecentChatCache;
import aoharureverie.ocaacrclient.oldchat.models.FriendNameResolver;
import aoharureverie.ocaacrclient.oldchat.models.UserNameCache;
import aoharureverie.ocaacrclient.oldchat.util.GroupAvatarCache;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.Map;

class GroupMemberLoader {
    private GroupMemberLoader() {
    }

    static void loadMembers(Context context, String token, String groupId,
                            GroupMessageAdapter adapter,
                            Map<String, String> nameMap,
                            Map<String, String> avatarMap) {
        loadMembers(context, token, groupId, adapter, nameMap, avatarMap, null, null, null, null);
    }

    static void loadMembers(Context context, String token, String groupId,
                            GroupMessageAdapter adapter,
                            Map<String, String> nameMap,
                            Map<String, String> avatarMap,
                            List<GroupMember> members) {
        loadMembers(context, token, groupId, adapter, nameMap, avatarMap, null, null, members, null);
    }

    static void loadMemberNames(Context context, String token, String groupId,
                                GroupMessageAdapter adapter,
                                Map<String, String> nameMap,
                                Map<String, String> titleMap,
                                Map<String, Integer> roleMap,
                                List<GroupMember> members,
                                Runnable onLoaded) {
        if (groupId == null || groupId.isEmpty() || token == null || token.isEmpty() || adapter == null) {
            return;
        }
        final Context contextFinal = context;
        final String groupIdFinal = groupId;
        final GroupMessageAdapter adapterFinal = adapter;
        final Map<String, String> nameMapFinal = nameMap;
        final Map<String, String> titleMapFinal = titleMap;
        final Map<String, Integer> roleMapFinal = roleMap;
        final List<GroupMember> membersFinal = members;
        final Runnable onLoadedFinal = onLoaded;
        HttpUtil.get("/groups/members?group_id=" + groupIdFinal, token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject obj = new JSONObject(response);
                    JSONArray arr = obj.getJSONArray("members");
                    if (nameMapFinal != null) {
                        nameMapFinal.clear();
                    }
                    if (titleMapFinal != null) {
                        titleMapFinal.clear();
                    }
                    if (roleMapFinal != null) {
                        roleMapFinal.clear();
                    }
                    if (membersFinal != null) {
                        membersFinal.clear();
                    }
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject mObj = arr.getJSONObject(i);
                        String uid = mObj.optString("uid");
                        if (uid == null || uid.isEmpty()) {
                            continue;
                        }
                        String display = mObj.optString("display_name");
                        String username = mObj.optString("username");
                        String title = mObj.optString("user_title");
                        String name = FriendNameResolver.resolve("", display, username, uid);
                        if (nameMapFinal != null) {
                            nameMapFinal.put(uid, name);
                        }
                        if (titleMapFinal != null) {
                            titleMapFinal.put(uid, title == null ? "" : title);
                        }
                        if (roleMapFinal != null) {
                            roleMapFinal.put(uid, Integer.valueOf(mObj.optInt("role", 0)));
                        }
                        if (membersFinal != null) {
                            GroupMember member = new GroupMember();
                            member.uid = uid;
                            member.display_name = display;
                            member.username = username;
                            member.user_title = title;
                            member.role = mObj.optInt("role", 0);
                            member.joined_at = mObj.optLong("joined_at", 0);
                            membersFinal.add(member);
                        }
                    }
                    if (nameMapFinal != null) {
                        adapterFinal.updateNameMap(nameMapFinal);
                        UserNameCache.putAll(contextFinal, nameMapFinal);
                    }
                    if (titleMapFinal != null) {
                        adapterFinal.updateTitleMap(titleMapFinal);
                        aoharureverie.ocaacrclient.oldchat.models.UserTitleCache.putAll(contextFinal, titleMapFinal);
                    }
                    if (roleMapFinal != null) {
                        adapterFinal.updateRoleMap(roleMapFinal);
                    }
                    int memberCount = arr.length();
                    GroupRecentChatCache.updateMemberCount(contextFinal, groupIdFinal, memberCount);
                    GroupCache.updateMemberCount(contextFinal, groupIdFinal, memberCount);
                    if (onLoadedFinal != null) {
                        onLoadedFinal.run();
                    }
                } catch (Exception e) {
                }
            }

            @Override
            public void onError(int code, String error) {
            }
        });
    }

    static void loadMembers(Context context, String token, String groupId,
                            GroupMessageAdapter adapter,
                            Map<String, String> nameMap,
                            Map<String, String> avatarMap,
                            Map<String, String> titleMap,
                            Map<String, Integer> roleMap,
                            List<GroupMember> members,
                            Runnable onLoaded) {
        if (groupId == null || groupId.isEmpty()) {
            return;
        }
        final Context contextFinal = context;
        final String groupIdFinal = groupId;
        final GroupMessageAdapter adapterFinal = adapter;
        final Map<String, String> nameMapFinal = nameMap;
        final Map<String, String> avatarMapFinal = avatarMap;
        final Map<String, String> titleMapFinal = titleMap;
        final Map<String, Integer> roleMapFinal = roleMap;
        final List<GroupMember> membersFinal = members;
        final Runnable onLoadedFinal = onLoaded;
        GroupAvatarCache.fillMissing(contextFinal, avatarMapFinal);
        adapterFinal.updateAvatarMap(avatarMapFinal);
        HttpUtil.get("/groups/members?group_id=" + groupIdFinal, token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject obj = new JSONObject(response);
                    JSONArray arr = obj.getJSONArray("members");
                    nameMapFinal.clear();
                    avatarMapFinal.clear();
                    if (titleMapFinal != null) {
                        titleMapFinal.clear();
                    }
                    if (roleMapFinal != null) {
                        roleMapFinal.clear();
                    }
                    if (membersFinal != null) {
                        membersFinal.clear();
                    }
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject mObj = arr.getJSONObject(i);
                        String uid = mObj.optString("uid");
                        String display = mObj.optString("display_name");
                        String title = mObj.optString("user_title");
                        String username = mObj.optString("username");
                        String avatar = mObj.optString("avatar_url");
                        String name = FriendNameResolver.resolve("", display, username, uid);
                        if (uid != null && !uid.isEmpty()) {
                            nameMapFinal.put(uid, name);
                            if (avatar != null && !avatar.isEmpty()) {
                                avatarMapFinal.put(uid, avatar);
                            }
                            if (titleMapFinal != null) {
                                titleMapFinal.put(uid, title == null ? "" : title);
                            }
                            if (roleMapFinal != null) {
                                roleMapFinal.put(uid, Integer.valueOf(mObj.optInt("role", 0)));
                            }
                            if (membersFinal != null) {
                                GroupMember member = new GroupMember();
                                member.uid = uid;
                                member.display_name = display;
                                member.user_title = title;
                                member.username = username;
                                member.avatar_url = avatar;
                                member.role = mObj.optInt("role", 0);
                                member.joined_at = mObj.optLong("joined_at", 0);
                                membersFinal.add(member);
                            }
                        }
                    }
                    adapterFinal.updateNameMap(nameMapFinal);
                    adapterFinal.updateAvatarMap(avatarMapFinal);
                    if (titleMapFinal != null) {
                        adapterFinal.updateTitleMap(titleMapFinal);
                    }
                    if (roleMapFinal != null) {
                        adapterFinal.updateRoleMap(roleMapFinal);
                    }
                    UserNameCache.putAll(contextFinal, nameMapFinal);
                    if (titleMapFinal != null) {
                        aoharureverie.ocaacrclient.oldchat.models.UserTitleCache.putAll(contextFinal, titleMapFinal);
                    }
                    GroupAvatarCache.updateFromAvatarMap(contextFinal, avatarMapFinal);
                    int memberCount = arr.length();
                    GroupRecentChatCache.updateMemberCount(contextFinal, groupIdFinal, memberCount);
                    GroupCache.updateMemberCount(contextFinal, groupIdFinal, memberCount);
                    if (onLoadedFinal != null) {
                        onLoadedFinal.run();
                    }
                } catch (Exception e) {
                }
            }

            @Override
            public void onError(int code, String error) {
            }
        });
    }
}
