package aoharureverie.ocaacrclient.oldchat.api;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.OldChatApplication;
import aoharureverie.ocaacrclient.oldchat.ui.LoginActivity;
import aoharureverie.ocaacrclient.oldchat.util.CryptoUtil;
import aoharureverie.ocaacrclient.oldchat.util.NetworkStateManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.GZIPInputStream;

class HttpUtilSupport0 {
    protected static final String TAG = "HttpUtil";
    public static final String DEFAULT_BASE_URL = "http://127.0.0.1:8080/v1";
    public static String BASE_URL = DEFAULT_BASE_URL;
    private static final String PREFS_SERVER = "server_config";
    private static final String KEY_BASE_URL = "base_url";
    protected static final String AUTH_SILENT = "auth_silent";
    protected static final String AUTH_WARNING = "auth_warning";
    protected static final int CONNECT_TIMEOUT_MS = 8000;
    protected static final int READ_TIMEOUT_MS = 15000;
    protected static final int GET_RETRY_TIMES = 2;
    protected static final long GET_RETRY_BASE_DELAY_MS = 350;
    protected static final long GET_RETRY_MAX_DELAY_MS = 1200;
    protected static final long ENCRYPT_BACKOFF_MS = 60 * 1000;
    protected static final long AUTH_REDIRECT_COOLDOWN_MS = 15000;
    protected static volatile long encryptDisabledUntil = 0;
    protected static int authFailStreak = 0;
    protected static boolean authWarned = false;
    protected static volatile long lastAuthRedirectAt = 0;
    protected static final Object refreshLock = new Object();
    protected static volatile boolean isRefreshing = false;
    private static final long GET_CACHE_SUCCESS_TTL_MS = 1500;
    private static final long GET_CACHE_ERROR_TTL_MS = 300;
    private static final long GET_IN_FLIGHT_WAIT_MS = 4000;
    private static final int GET_CACHE_MAX_SIZE = 180;
    private static final Object getCacheLock = new Object();
    private static final HashMap<String, GetCacheEntry> getCacheEntries = new HashMap<String, GetCacheEntry>();

    public static void loadBaseUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_SERVER, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_BASE_URL, null);
        if (saved != null && saved.length() > 0) {
            BASE_URL = saved;
        }
    }

    public static void saveBaseUrl(Context context, String url) {
        context.getSharedPreferences(PREFS_SERVER, Context.MODE_PRIVATE)
                .edit().putString(KEY_BASE_URL, url).apply();
        BASE_URL = url;
    }

    public static void resetBaseUrl(Context context) {
        context.getSharedPreferences(PREFS_SERVER, Context.MODE_PRIVATE)
                .edit().remove(KEY_BASE_URL).apply();
        BASE_URL = DEFAULT_BASE_URL;
    }

    protected static class Result {
        int code;
        String data;

        Result(int c, String d) {
            code = c;
            data = d;
        }
    }

    private static class GetCacheEntry {
        boolean inFlight;
        long finishedAt;
        Result result;
    }

    protected static Result requestWithRefresh(String method, String path, JSONObject json, String token) {
        try {
            Result result = executeRequestWithRetry(method, path, json, token);
            if (result.code == HttpURLConnection.HTTP_BAD_REQUEST && isInvalidSession(result.data)) {
                CryptoUtil.clearSession();
                result = executeRequestWithRetry(method, path, json, token);
            }
            if (result.code == HttpURLConnection.HTTP_UNAUTHORIZED && shouldAttemptRefresh(path, token)) {
                boolean refreshFailed = false;
                synchronized (refreshLock) {
                    if (!isRefreshing) {
                        isRefreshing = true;
                        try {
                            String newToken = HttpAuthHelper.refreshAccessToken();
                            if (newToken != null) {
                                resetAuthStreak();
                                Result refreshed = executeRequestWithRetry(method, path, json, newToken);
                                return applyAuthPolicy(refreshed, path, newToken);
                            }
                            refreshFailed = true;
                        } finally {
                            isRefreshing = false;
                        }
                    } else {
                        Thread.sleep(500);
                        String newToken = HttpAuthHelper.getSavedAccessToken();
                        if (newToken != null && !newToken.isEmpty()) {
                            Result refreshed = executeRequestWithRetry(method, path, json, newToken);
                            if (refreshed.code >= 200 && refreshed.code < 300) {
                                resetAuthStreak();
                            }
                            return applyAuthPolicy(refreshed, path, newToken);
                        }
                        refreshFailed = true;
                    }
                }
                if (refreshFailed) {
                    showAuthWarning();
                }
            }
            return applyAuthPolicy(result, path, token);
        } catch (Exception e) {
            NetworkStateManager.getInstance().recordRequestFailure(-1);
            return new Result(-1, e.getMessage());
        }
    }

    private static Result executeRequestWithRetry(String method, String path, JSONObject json, String token) {
        if (!"GET".equals(method)) {
            try {
                return executeRequest(method, path, json, token);
            } catch (Exception e) {
                return new Result(-1, e.getMessage());
            }
        }

        if (!isGetCacheEligible(path)) {
            return executeGetRetryLoop(method, path, json, token);
        }

        String key = buildGetCacheKey(path, token);
        Result cached = readCachedGetResult(key);
        if (cached != null) {
            return cached;
        }

        boolean owner = beginGetInFlight(key);
        if (!owner) {
            Result shared = waitForGetInFlightResult(key);
            if (shared != null) {
                return shared;
            }
            owner = beginGetInFlight(key);
            if (!owner) {
                shared = waitForGetInFlightResult(key);
                if (shared != null) {
                    return shared;
                }
                owner = true;
            }
        }

        Result finalResult = executeGetRetryLoop(method, path, json, token);
        if (owner) {
            finishGetInFlight(key, finalResult);
        }
        return finalResult;
    }

    private static Result executeGetRetryLoop(String method, String path, JSONObject json, String token) {
        int attempts = GET_RETRY_TIMES + 1;
        Result last = new Result(-1, "network_error");
        for (int i = 0; i < attempts; i++) {
            try {
                last = executeRequest(method, path, json, token);
            } catch (Exception e) {
                last = new Result(-1, e.getMessage());
            }
            if (!shouldRetryGet(last.code) || i >= attempts - 1) {
                return last;
            }
            sleepBeforeGetRetry(i);
        }
        return last;
    }

    private static boolean isGetCacheEligible(String path) {
        if (path == null || path.length() == 0) {
            return false;
        }
        if (path.startsWith("/auth/") || path.startsWith("/ws")) {
            return false;
        }
        String lower = path.toLowerCase();
        if (lower.indexOf("/messages") >= 0
                || lower.indexOf("/typing") >= 0
                || lower.indexOf("/redpackets") >= 0
                || lower.indexOf("timestamp=") >= 0
                || lower.indexOf("nonce=") >= 0) {
            return false;
        }
        return true;
    }

    private static String buildGetCacheKey(String path, String token) {
        String safePath = path == null ? "" : path;
        int hash = token == null ? 0 : token.hashCode();
        return safePath + "#" + hash;
    }

    private static Result readCachedGetResult(String key) {
        long now = System.currentTimeMillis();
        synchronized (getCacheLock) {
            GetCacheEntry entry = getCacheEntries.get(key);
            if (entry == null || entry.inFlight || entry.result == null) {
                return null;
            }
            if (entry.result.code < 200 || entry.result.code >= 300) {
                if (now - entry.finishedAt > GET_CACHE_ERROR_TTL_MS) {
                    getCacheEntries.remove(key);
                }
                return null;
            }
            if (now - entry.finishedAt > GET_CACHE_SUCCESS_TTL_MS) {
                getCacheEntries.remove(key);
                return null;
            }
            return copyResult(entry.result);
        }
    }

    private static boolean beginGetInFlight(String key) {
        synchronized (getCacheLock) {
            GetCacheEntry entry = getCacheEntries.get(key);
            if (entry == null) {
                entry = new GetCacheEntry();
                getCacheEntries.put(key, entry);
            }
            if (entry.inFlight) {
                return false;
            }
            entry.inFlight = true;
            entry.result = null;
            entry.finishedAt = 0;
            return true;
        }
    }

    private static void finishGetInFlight(String key, Result result) {
        synchronized (getCacheLock) {
            GetCacheEntry entry = getCacheEntries.get(key);
            if (entry == null) {
                entry = new GetCacheEntry();
                getCacheEntries.put(key, entry);
            }
            entry.inFlight = false;
            entry.finishedAt = System.currentTimeMillis();
            entry.result = copyResult(result);
            pruneGetCacheLocked();
            getCacheLock.notifyAll();
        }
    }

    private static Result waitForGetInFlightResult(String key) {
        long deadline = System.currentTimeMillis() + GET_IN_FLIGHT_WAIT_MS;
        synchronized (getCacheLock) {
            while (true) {
                GetCacheEntry entry = getCacheEntries.get(key);
                if (entry == null) {
                    return null;
                }
                if (!entry.inFlight) {
                    if (entry.result == null) {
                        return null;
                    }
                    long age = System.currentTimeMillis() - entry.finishedAt;
                    if (entry.result.code >= 200 && entry.result.code < 300) {
                        if (age <= GET_CACHE_SUCCESS_TTL_MS) {
                            return copyResult(entry.result);
                        }
                    } else if (age <= GET_CACHE_ERROR_TTL_MS) {
                        return copyResult(entry.result);
                    }
                    return null;
                }
                long waitMs = deadline - System.currentTimeMillis();
                if (waitMs <= 0) {
                    return null;
                }
                try {
                    getCacheLock.wait(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
    }

    private static void pruneGetCacheLocked() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, GetCacheEntry>> iterator = getCacheEntries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, GetCacheEntry> item = iterator.next();
            GetCacheEntry entry = item.getValue();
            if (entry == null || entry.inFlight || entry.result == null) {
                continue;
            }
            long ttl = entry.result.code >= 200 && entry.result.code < 300
                    ? GET_CACHE_SUCCESS_TTL_MS
                    : GET_CACHE_ERROR_TTL_MS;
            if (now - entry.finishedAt > ttl) {
                iterator.remove();
            }
        }
        if (getCacheEntries.size() <= GET_CACHE_MAX_SIZE) {
            return;
        }
        iterator = getCacheEntries.entrySet().iterator();
        while (getCacheEntries.size() > GET_CACHE_MAX_SIZE && iterator.hasNext()) {
            Map.Entry<String, GetCacheEntry> item = iterator.next();
            GetCacheEntry entry = item.getValue();
            if (entry == null || entry.inFlight) {
                continue;
            }
            iterator.remove();
        }
    }

    private static Result copyResult(Result source) {
        if (source == null) {
            return null;
        }
        return new Result(source.code, source.data);
    }

    private static boolean shouldRetryGet(int code) {
        return code <= 0
                || code == HttpURLConnection.HTTP_CLIENT_TIMEOUT
                || code == HttpURLConnection.HTTP_UNAVAILABLE
                || code == HttpURLConnection.HTTP_GATEWAY_TIMEOUT
                || code == 429
                || code == HttpURLConnection.HTTP_INTERNAL_ERROR
                || code == HttpURLConnection.HTTP_BAD_GATEWAY;
    }

    private static void sleepBeforeGetRetry(int attempt) {
        long delay = GET_RETRY_BASE_DELAY_MS * (1L << attempt);
        if (delay > GET_RETRY_MAX_DELAY_MS) {
            delay = GET_RETRY_MAX_DELAY_MS;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected static Result executeRequest(String method, String path, JSONObject json, String token) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setUseCaches("GET".equals(method));
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Accept-Encoding", "gzip");
        boolean encrypt = shouldEncrypt(path);
        if (encrypt) {
            if (!HttpAuthHelper.ensureSession()) {
                encryptDisabledUntil = System.currentTimeMillis() + ENCRYPT_BACKOFF_MS;
                CryptoUtil.clearSession();
                encrypt = false;
            } else {
                conn.setRequestProperty("X-Enc", "1");
                String sessionId = CryptoUtil.getSessionId();
                if (sessionId != null && sessionId.length() > 0) {
                    conn.setRequestProperty("X-Session", sessionId);
                }
            }
        }
        if (token != null && token.length() > 0) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (json != null) {
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            OutputStream os = null;
            try {
                os = conn.getOutputStream();
                String payload = json.toString();
                if (encrypt) {
                    payload = CryptoUtil.encrypt(payload);
                }
                os.write(payload.getBytes("UTF-8"));
                os.flush();
            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        try {
            int code = conn.getResponseCode();
            String body = readResponseBody(conn, code);
            if (encrypt) {
                String decrypted = CryptoUtil.decryptIfNeeded(body);
                if (decrypted != null) {
                    body = decrypted;
                }
            }
            return new Result(code, body);
        } finally {
            try {
                conn.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    protected static boolean shouldAttemptRefresh(String path, String token) {
        if (token == null || token.length() == 0) {
            return false;
        }
        return !(path.startsWith("/auth/login") || path.startsWith("/auth/register") || path.startsWith("/auth/refresh"));
    }

    private static boolean shouldEncrypt(String path) {
        if (System.currentTimeMillis() < encryptDisabledUntil) {
            return false;
        }
        return path != null && !path.startsWith("/auth/handshake") && CryptoUtil.isEcdhSupported();
    }

    public static boolean shouldSuppressAuthToast(int code, String error) {
        if (code != HttpURLConnection.HTTP_UNAUTHORIZED || error == null) {
            return false;
        }
        return AUTH_SILENT.equals(error) || AUTH_WARNING.equals(error);
    }

    protected static Result applyAuthPolicy(Result result, String path, String token) {
        if (result == null) {
            return result;
        }
        if (!shouldAttemptRefresh(path, token)) {
            if (result.code >= 200 && result.code < 300) {
                resetAuthStreak();
            }
            return result;
        }
        if (result.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            if (!isRefreshing) {
                authFailStreak++;
                if (authFailStreak >= 5 && !authWarned) {
                    authWarned = true;
                    result.data = AUTH_WARNING;
                } else {
                    result.data = AUTH_SILENT;
                }
            } else {
                result.data = AUTH_SILENT;
            }
            return result;
        }
        if (result.code >= 200 && result.code < 300) {
            NetworkStateManager.getInstance().recordRequestSuccess();
            resetAuthStreak();
        } else {
            NetworkStateManager.getInstance().recordRequestFailure(result.code);
            resetAuthStreak();
        }
        return result;
    }

    private static void resetAuthStreak() {
        authFailStreak = 0;
        authWarned = false;
    }

    public static void showAuthWarning() {
        final Context context = OldChatApplication.getAppContext();
        if (context == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, "登录已过期，请重新登录", Toast.LENGTH_LONG).show();
                maybeRedirectToLogin(context);
            }
        });
    }

    private static void maybeRedirectToLogin(Context context) {
        long now = System.currentTimeMillis();
        if (now - lastAuthRedirectAt < AUTH_REDIRECT_COOLDOWN_MS) {
            return;
        }
        lastAuthRedirectAt = now;
        Intent intent = new Intent(context, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    private static boolean isInvalidSession(String body) {
        if (body == null) {
            return false;
        }
        return body.contains("\"code\":\"invalid_session\"") || body.contains("invalid_session");
    }

    protected static String readResponseBody(HttpURLConnection conn, int code) {
        if (conn == null) {
            return "";
        }
        InputStream is = null;
        try {
            if (code >= 200 && code < 300) {
                is = conn.getInputStream();
            } else {
                is = conn.getErrorStream();
                if (is == null) {
                    is = conn.getInputStream();
                }
            }
        } catch (Exception e) {
            is = null;
        }
        if (is == null) {
            return "";
        }
        String encoding = null;
        try {
            encoding = conn.getContentEncoding();
        } catch (Exception ignored) {
        }
        if (encoding != null && encoding.toLowerCase().indexOf("gzip") >= 0) {
            try {
                is = new GZIPInputStream(is);
            } catch (Exception ignored) {
            }
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Exception ignored) {
                }
            } else {
                try {
                    is.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static boolean ensureSession() {
        return HttpAuthHelper.ensureSession();
    }
}
