package aoharureverie.ocaacrclient.oldchat.api;

import android.content.Context;
import android.content.SharedPreferences;
import aoharureverie.ocaacrclient.oldchat.OldChatApplication;
import aoharureverie.ocaacrclient.oldchat.util.CryptoUtil;
import aoharureverie.ocaacrclient.oldchat.util.DeviceInfoUtil;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

class HttpAuthHelper {
    private static final String AUTH_PREFS = "auth";

    static String getSavedAccessToken() {
        Context context = OldChatApplication.getAppContext();
        if (context == null) {
            return "";
        }
        SharedPreferences prefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE);
        return prefs.getString("access_token", "");
    }

    static boolean ensureSession() {
        if (!CryptoUtil.isEcdhSupported()) {
            return false;
        }
        if (CryptoUtil.hasSession()) {
            return true;
        }
        synchronized (HttpAuthHelper.class) {
            if (CryptoUtil.hasSession()) {
                return true;
            }
            try {
                CryptoUtil.Handshake handshake = CryptoUtil.beginHandshake();
                JSONObject json = new JSONObject();
                json.put("client_pub", handshake.getPublicKey());
                HttpUtil.Result result = executePlainRequest("POST", "/auth/handshake", json, null);
                if (result.code < 200 || result.code >= 300) {
                    CryptoUtil.clearSession();
                    return false;
                }
                JSONObject obj = new JSONObject(result.data);
                String sessionId = obj.optString("session_id", "");
                String serverPub = obj.optString("server_pub", "");
                if (sessionId.length() == 0 || serverPub.length() == 0) {
                    CryptoUtil.clearSession();
                    return false;
                }
                CryptoUtil.SessionKeys keys = handshake.finish(serverPub);
                CryptoUtil.setSession(sessionId, keys.encKey, keys.macKey);
                return true;
            } catch (Exception e) {
                CryptoUtil.clearSession();
                return false;
            }
        }
    }

    static String refreshAccessToken() {
        try {
            Context context = OldChatApplication.getAppContext();
            if (context == null) {
                return null;
            }
            SharedPreferences prefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE);
            String oldUid = prefs.getString("my_uid", "");
            String refreshToken = prefs.getString("refresh_token", "");
            if (refreshToken != null && refreshToken.length() > 0) {
                try {
                    JSONObject json = new JSONObject();
                    json.put("refresh_token", refreshToken);
                    HttpUtil.Result result = HttpUtil.executeRequest("POST", "/auth/refresh", json, null);
                    if (result.code >= 200 && result.code < 300) {
                        return applyAuthResponse(prefs, context, oldUid, result.data);
                    }
                    if (result.code == HttpURLConnection.HTTP_UNAUTHORIZED || result.code == HttpURLConnection.HTTP_FORBIDDEN) {
                        prefs.edit().remove("refresh_token").apply();
                    }
                } catch (Exception e) {
                    // Ignore and fall back to password login.
                }
            }
            String savedUsername = prefs.getString("saved_username", null);
            String savedPassword = prefs.getString("saved_password", null);
            if (savedUsername == null || savedUsername.length() == 0 ||
                    savedPassword == null || savedPassword.length() == 0) {
                return null;
            }
            JSONObject json = new JSONObject();
            json.put("identifier", savedUsername);
            json.put("password", savedPassword);
            json.put("device_id", DeviceInfoUtil.getDeviceId(context));
            json.put("imei", DeviceInfoUtil.getImei(context));
            json.put("device_name", DeviceInfoUtil.getDeviceName());
            json.put("platform", "android");
            json.put("app_version", DeviceInfoUtil.getAppVersion(context));
            HttpUtil.Result result = HttpUtil.executeRequest("POST", "/auth/login", json, null);
            if (result.code < 200 || result.code >= 300) {
                return null;
            }
            return applyAuthResponse(prefs, context, oldUid, result.data);
        } catch (Exception e) {
            return null;
        }
    }

    private static String applyAuthResponse(SharedPreferences prefs, Context context, String oldUid, String response) throws Exception {
        JSONObject obj = new JSONObject(response);
        String accessToken = obj.optString("access_token", "");
        String newRefreshToken = obj.optString("refresh_token", "");
        JSONObject user = obj.optJSONObject("user");
        if (accessToken.length() == 0) {
            return null;
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("access_token", accessToken);
        if (newRefreshToken != null && newRefreshToken.length() > 0) {
            editor.putString("refresh_token", newRefreshToken);
        }
        if (user != null) {
            String userId = user.optString("id", "");
            String myUID = user.optString("uid", "");
            if (userId.length() > 0) {
                editor.putString("user_id", userId);
            }
            if (myUID.length() > 0) {
                if (oldUid.length() > 0 && !oldUid.equals(myUID)) {
                    MyUidStore.recordUidAlias(context, oldUid, myUID);
                }
                editor.putString("my_uid", myUID);
            }
        }
        editor.apply();
        return accessToken;
    }

    private static HttpUtil.Result executePlainRequest(String method, String path, JSONObject json, String token) throws Exception {
        URL url = new URL(HttpUtil.BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod(method);
        if (token != null && token.length() > 0) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (json != null) {
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.write(json.toString().getBytes("UTF-8"));
            os.flush();
            os.close();
        }
        int code = conn.getResponseCode();
        return new HttpUtil.Result(code, HttpUtil.readResponseBody(conn, code));
    }
}
