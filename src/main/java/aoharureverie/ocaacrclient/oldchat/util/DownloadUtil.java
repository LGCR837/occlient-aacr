package aoharureverie.ocaacrclient.oldchat.util;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

public class DownloadUtil {
    public interface Callback {
        void onResult(boolean success, String message, File file);
    }

    private static final String DEFAULT_ERROR_MESSAGE = "保存失败";

    public static void saveUrlToDownloadsAsync(final Context context, final String url,
                                               final String prefix, final String fallbackExt,
                                               final Callback callback) {
        if (context == null || url == null || url.isEmpty()) {
            if (callback != null) {
                callback.onResult(false, "无效的下载链接", null);
            }
            return;
        }
        final Context appContext = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                File outFile = null;
                DownloadAttempt attempt;
                try {
                    String resolved = resolveUrl(url);
                    if (resolved == null || resolved.length() == 0) {
                        postResult(callback, false, "下载链接无效", null);
                        return;
                    }
                    String ext = guessExtension(resolved, fallbackExt);
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null || (!dir.exists() && !dir.mkdirs())) {
                        dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    }
                    if (dir == null) {
                        dir = appContext.getExternalFilesDir(null);
                    }
                    if (dir == null) {
                        postResult(callback, false, "无法访问存储目录", null);
                        return;
                    }
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    String safePrefix = (prefix == null || prefix.isEmpty()) ? "oldchat_" : prefix;
                    outFile = new File(dir, safePrefix + System.currentTimeMillis() + ext);
                    attempt = downloadToFileWithFallback(resolved, outFile);
                } catch (Exception e) {
                    attempt = fail(formatDownloadError(e));
                }

                if (attempt.success && outFile != null) {
                    scanFile(appContext, outFile);
                    postResult(callback, true, "已保存到下载: " + outFile.getAbsolutePath(), outFile);
                } else {
                    if (outFile != null && outFile.exists()) {
                        outFile.delete();
                    }
                    String message = attempt == null ? DEFAULT_ERROR_MESSAGE : attempt.message;
                    if (message == null || message.length() == 0) {
                        message = DEFAULT_ERROR_MESSAGE;
                    }
                    postResult(callback, false, message, null);
                }
            }
        }).start();
    }

    private static void postResult(final Callback callback, final boolean success,
                                   final String message, final File file) {
        if (callback == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                callback.onResult(success, message, file);
            }
        });
    }

    private static void scanFile(Context context, File file) {
        try {
            MediaScannerConnection.scanFile(context,
                    new String[]{file.getAbsolutePath()},
                    null,
                    null);
        } catch (Throwable e) {
            // ignore
        }
    }

    private static String resolveUrl(String url) {
        return MediaUrlResolver.resolve(url);
    }

    private static String guessExtension(String url, String fallbackExt) {
        if (url != null) {
            int q = url.indexOf('?');
            if (q >= 0) {
                url = url.substring(0, q);
            }
            int h = url.indexOf('#');
            if (h >= 0) {
                url = url.substring(0, h);
            }
            int idx = url.lastIndexOf('.');
            if (idx >= 0 && idx < url.length() - 1) {
                String ext = url.substring(idx).toLowerCase();
                if (ext.length() <= 8) {
                    return ext;
                }
            }
        }
        if (fallbackExt == null || fallbackExt.isEmpty()) {
            return ".jpg";
        }
        return fallbackExt.startsWith(".") ? fallbackExt : ("." + fallbackExt);
    }

    private static DownloadAttempt downloadToFileWithFallback(String url, File outFile) {
        String[] candidates = MediaUrlResolver.resolveCandidates(url);
        if (candidates == null || candidates.length == 0) {
            return downloadToFile(url, outFile);
        }
        String lastError = DEFAULT_ERROR_MESSAGE;
        for (int i = 0; i < candidates.length; i++) {
            String one = candidates[i];
            if (one == null || one.length() == 0) {
                continue;
            }
            DownloadAttempt oneResult = downloadToFile(one, outFile);
            if (oneResult.success) {
                return oneResult;
            }
            if (oneResult.message != null && oneResult.message.length() > 0) {
                lastError = oneResult.message;
            }
        }
        if (lastError == null || lastError.length() == 0) {
            lastError = DEFAULT_ERROR_MESSAGE;
        }
        return fail(lastError);
    }

    private static DownloadAttempt downloadToFile(String url, File outFile) {
        if (url == null || url.length() == 0) {
            return fail("下载链接为空");
        }
        if (outFile == null) {
            return fail("保存路径无效");
        }

        InputStream is = null;
        FileOutputStream os = null;
        HttpURLConnection conn = null;
        File tempFile = null;
        boolean fileSaved = false;
        try {
            File parent = outFile.getParentFile();
            if (parent == null) {
                return fail("保存路径无效");
            }
            if (!parent.exists() && !parent.mkdirs()) {
                return fail("无法创建下载目录");
            }

            tempFile = new File(outFile.getAbsolutePath() + ".tmp");
            if (tempFile.exists()) {
                tempFile.delete();
            }

            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return fail("服务器返回 " + code);
            }

            is = conn.getInputStream();
            os = new FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            int len;
            long total = 0L;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
                total += len;
            }
            os.flush();

            if (total <= 0L) {
                return fail("下载内容为空");
            }
            if (outFile.exists()) {
                outFile.delete();
            }
            if (!tempFile.renameTo(outFile)) {
                return fail("写入文件失败");
            }
            fileSaved = true;
            return success();
        } catch (Exception e) {
            return fail(formatDownloadError(e));
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (Exception e) {
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
            if (!fileSaved && tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private static String formatDownloadError(Exception e) {
        if (e == null) {
            return DEFAULT_ERROR_MESSAGE;
        }
        if (e instanceof SocketTimeoutException) {
            return "下载超时，请重试";
        }
        if (e instanceof UnknownHostException) {
            return "网络不可用，请检查连接";
        }
        if (e instanceof FileNotFoundException) {
            return "资源不存在或无权限访问";
        }
        if (e instanceof SSLException) {
            return "安全连接失败，请稍后重试";
        }
        String msg = e.getMessage();
        if (msg != null) {
            msg = msg.trim();
            if (msg.length() > 0) {
                return msg;
            }
        }
        return DEFAULT_ERROR_MESSAGE;
    }

    private static DownloadAttempt success() {
        return new DownloadAttempt(true, "");
    }

    private static DownloadAttempt fail(String message) {
        String reason = message;
        if (reason == null || reason.length() == 0) {
            reason = DEFAULT_ERROR_MESSAGE;
        }
        return new DownloadAttempt(false, reason);
    }

    private static class DownloadAttempt {
        final boolean success;
        final String message;

        DownloadAttempt(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
