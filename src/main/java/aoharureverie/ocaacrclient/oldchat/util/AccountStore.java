package aoharureverie.ocaacrclient.oldchat.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import aoharureverie.ocaacrclient.oldchat.models.AccountInfo;

public class AccountStore {
    private static final String PREFS_NAME = "account_list";
    private static final String KEY_ACCOUNTS = "accounts";

    public static List<AccountInfo> loadAll(Context context) {
        List<AccountInfo> list = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_ACCOUNTS, null);
        if (json == null || json.isEmpty()) {
            return list;
        }
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                AccountInfo a = new AccountInfo();
                a.id = obj.optString("id", "");
                a.displayName = obj.optString("displayName", "");
                a.baseurl = obj.optString("baseurl", "");
                a.username = obj.optString("username", "");
                a.password = obj.optString("password", "");
                if (a.id != null && !a.id.isEmpty()) {
                    list.add(a);
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static void saveAll(Context context, List<AccountInfo> list) {
        JSONArray arr = new JSONArray();
        try {
            for (AccountInfo a : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", a.id);
                obj.put("displayName", a.displayName == null ? "" : a.displayName);
                obj.put("baseurl", a.baseurl == null ? "" : a.baseurl);
                obj.put("username", a.username == null ? "" : a.username);
                obj.put("password", a.password == null ? "" : a.password);
                arr.put(obj);
            }
        } catch (Exception ignored) {
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_ACCOUNTS, arr.toString()).apply();
    }

    public static void addAccount(Context context, AccountInfo account) {
        List<AccountInfo> list = loadAll(context);
        if (account.id == null || account.id.isEmpty()) {
            account.id = UUID.randomUUID().toString();
        }
        list.add(account);
        saveAll(context, list);
    }

    public static void updateAccount(Context context, AccountInfo account) {
        List<AccountInfo> list = loadAll(context);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id != null && list.get(i).id.equals(account.id)) {
                list.set(i, account);
                break;
            }
        }
        saveAll(context, list);
    }

    public static void deleteAccount(Context context, String id) {
        List<AccountInfo> list = loadAll(context);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id != null && list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
        saveAll(context, list);
    }
}
