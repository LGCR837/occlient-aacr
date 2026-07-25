package aoharureverie.ocaacrclient.oldchat.util;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

class ImageLoaderBitmapSupport {
    private static final int DEFAULT_CACHE_KB = 6 * 1024;
    private static final int LOW_MEMORY_CACHE_KB = 3 * 1024;
    private static final int MIN_CACHE_KB = 1536;

    private static final int MAX_NETWORK_BYTES_AVATAR = 600 * 1024;
    private static final int MAX_NETWORK_BYTES_NORMAL = 2 * 1024 * 1024;
    private static final int MAX_NETWORK_BYTES_LARGE = 4 * 1024 * 1024;
    private static final int MAX_NETWORK_BYTES_HUGE = 6 * 1024 * 1024;

    private static final long DISK_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final long DISK_CACHE_LIMIT_LOW_BYTES = 48L * 1024L * 1024L;
    private static final long DISK_CACHE_LIMIT_NORMAL_BYTES = 96L * 1024L * 1024L;
    private static final long DISK_CACHE_LIMIT_HIGH_BYTES = 128L * 1024L * 1024L;
    private static final long CACHE_CLEANUP_INTERVAL_MS = 2L * 60L * 60L * 1000L;
    private static final long TMP_FILE_TTL_MS = 20L * 60L * 1000L;

    private static final String IMAGE_ACCEPT_HEADER = "image/*,*/*;q=0.8";
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 12000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 20000;
    private static final int DATA_SERVER_FALLBACK_TIMEOUT_MS = 4000;
    private static final Object CACHE_LOCK = new Object();
    private static final Object FILE_LOCK_GUARD = new Object();
    private static final HashMap<String, Object> FILE_LOCKS = new HashMap<String, Object>();
    private static final HashMap<String, Integer> FILE_LOCK_COUNTS = new HashMap<String, Integer>();
    private static final Object BITMAP_IN_FLIGHT_GUARD = new Object();
    private static final HashMap<String, BitmapInFlight> BITMAP_IN_FLIGHT = new HashMap<String, BitmapInFlight>();
    private static volatile long lastCleanupAt;
    private static LruCache<String, Bitmap> CACHE;

    private static class BitmapInFlight {
        boolean running;
        int waiters;
        Bitmap result;
    }

    private ImageLoaderBitmapSupport() {
    }

    static Bitmap downloadBitmap(Context context, String cacheDir, String url, int maxSize, int maxBytes) {
        if (url == null || url.length() == 0) {
            return null;
        }
        String inFlightKey = buildInFlightKey(cacheDir, url, maxSize, maxBytes);
        boolean owner = false;

        while (!owner) {
            synchronized (BITMAP_IN_FLIGHT_GUARD) {
                BitmapInFlight inFlight = BITMAP_IN_FLIGHT.get(inFlightKey);
                if (inFlight == null) {
                    inFlight = new BitmapInFlight();
                    inFlight.running = true;
                    BITMAP_IN_FLIGHT.put(inFlightKey, inFlight);
                    owner = true;
                    break;
                }
                if (!inFlight.running) {
                    Bitmap shared = inFlight.result;
                    BITMAP_IN_FLIGHT.remove(inFlightKey);
                    if (shared != null && !shared.isRecycled()) {
                        return shared;
                    }
                    inFlight.running = true;
                    inFlight.result = null;
                    BITMAP_IN_FLIGHT.put(inFlightKey, inFlight);
                    owner = true;
                    break;
                }
                inFlight.waiters++;
                try {
                    BITMAP_IN_FLIGHT_GUARD.wait(5000L);
                } catch (InterruptedException e) {
                    inFlight.waiters--;
                    if (inFlight.waiters <= 0 && !inFlight.running) {
                        BITMAP_IN_FLIGHT.remove(inFlightKey);
                    }
                    Thread.currentThread().interrupt();
                    return null;
                }
                inFlight.waiters--;
                if (!inFlight.running) {
                    Bitmap shared = inFlight.result;
                    if (inFlight.waiters <= 0) {
                        BITMAP_IN_FLIGHT.remove(inFlightKey);
                    }
                    if (shared != null && !shared.isRecycled()) {
                        return shared;
                    }
                }
            }
        }

        Bitmap result = null;
        try {
            result = downloadBitmapInternal(context, cacheDir, url, maxSize, maxBytes);
            return result;
        } finally {
            synchronized (BITMAP_IN_FLIGHT_GUARD) {
                BitmapInFlight inFlight = BITMAP_IN_FLIGHT.get(inFlightKey);
                if (inFlight != null) {
                    inFlight.result = result;
                    inFlight.running = false;
                    if (inFlight.waiters <= 0) {
                        BITMAP_IN_FLIGHT.remove(inFlightKey);
                    }
                }
                BITMAP_IN_FLIGHT_GUARD.notifyAll();
            }
        }
    }

    private static String buildInFlightKey(String cacheDir, String url, int maxSize, int maxBytes) {
        String dir = cacheDir == null ? "" : cacheDir;
        return dir + "#" + ImageCacheUtil.buildCacheKey(url) + "#" + maxSize + "#" + maxBytes;
    }

    private static Bitmap downloadBitmapInternal(Context context, String cacheDir, String url, int maxSize, int maxBytes) {
        maybeTrimDiskCache(context, cacheDir);

        File cached = ImageCacheUtil.getCacheFile(context, cacheDir, url);
        String lockKey = cached != null ? cached.getAbsolutePath() : url;
        Object fileLock = acquireFileLock(lockKey);

        synchronized (fileLock) {
            try {
                if (cached != null && cached.exists()) {
                    if (isExpired(cached)) {
                        cached.delete();
                    } else {
                        Bitmap bitmap = decodeSampledBitmap(cached.getAbsolutePath(), maxSize, maxBytes);
                        if (bitmap != null) {
                            touch(cached);
                            return bitmap;
                        }
                        cached.delete();
                    }
                }

                HttpURLConnection conn = null;
                try {
                    boolean useFastFailoverTimeout = shouldUseFastFailoverTimeout(url);
                    int connectTimeoutMs = useFastFailoverTimeout
                            ? DATA_SERVER_FALLBACK_TIMEOUT_MS : DEFAULT_CONNECT_TIMEOUT_MS;
                    int readTimeoutMs = useFastFailoverTimeout
                            ? DATA_SERVER_FALLBACK_TIMEOUT_MS : DEFAULT_READ_TIMEOUT_MS;
                    long requestDeadlineAt = useFastFailoverTimeout
                            ? (System.currentTimeMillis() + DATA_SERVER_FALLBACK_TIMEOUT_MS) : 0L;

                    conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(connectTimeoutMs);
                    conn.setReadTimeout(readTimeoutMs);
                    conn.setUseCaches(true);
                    conn.setInstanceFollowRedirects(true);
                    applyImageRequestHeaders(conn, url);
                    conn.connect();
                    if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        return null;
                    }

                    int maxDownloadBytes = resolveMaxDownloadBytes(maxSize);
                    final int expectedLength = conn.getContentLength();
                    if (expectedLength > 0 && expectedLength > maxDownloadBytes) {
                        return null;
                    }
                    if (requestDeadlineAt > 0 && System.currentTimeMillis() > requestDeadlineAt) {
                        return null;
                    }

                    File file = cached != null ? cached : ImageCacheUtil.getCacheFile(context, cacheDir, url);
                    if (file == null) {
                        InputStream is = null;
                        try {
                            is = conn.getInputStream();
                            byte[] data = readToBytes(is, maxDownloadBytes, requestDeadlineAt);
                            if (data == null || data.length == 0) {
                                return null;
                            }
                            return decodeSampledBitmap(data, maxSize, maxBytes);
                        } finally {
                            if (is != null) {
                                try {
                                    is.close();
                                } catch (Exception e) {
                                }
                            }
                        }
                    }

                    File tmp = new File(file.getAbsolutePath() + ".tmp");
                    if (tmp.exists()) {
                        tmp.delete();
                    }

                    InputStream is = null;
                    FileOutputStream out = null;
                    long totalRead = 0;
                    boolean overLimit = false;
                    boolean timeoutReached = false;
                    try {
                        is = conn.getInputStream();
                        out = new FileOutputStream(tmp);
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            if (requestDeadlineAt > 0 && System.currentTimeMillis() > requestDeadlineAt) {
                                timeoutReached = true;
                                break;
                            }
                            totalRead += read;
                            if (totalRead > maxDownloadBytes) {
                                overLimit = true;
                                break;
                            }
                            out.write(buffer, 0, read);
                        }
                        if (overLimit || timeoutReached) {
                            tmp.delete();
                            return null;
                        }
                        out.flush();
                        try {
                            out.getFD().sync();
                        } catch (Throwable ignored) {
                        }
                    } finally {
                        if (is != null) {
                            try {
                                is.close();
                            } catch (Exception e) {
                            }
                        }
                        if (out != null) {
                            try {
                                out.close();
                            } catch (Exception e) {
                            }
                        }
                    }

                    if (!tmp.exists() || totalRead <= 0) {
                        tmp.delete();
                        return null;
                    }
                    if (expectedLength > 0 && totalRead < expectedLength) {
                        tmp.delete();
                        return null;
                    }

                    Bitmap bitmap = decodeSampledBitmap(tmp.getAbsolutePath(), maxSize, maxBytes);
                    if (bitmap == null) {
                        tmp.delete();
                        if (file.exists()) {
                            file.delete();
                        }
                        return null;
                    }

                    if (file.exists()) {
                        file.delete();
                    }
                    if (tmp.renameTo(file)) {
                        touch(file);
                    } else {
                        tmp.delete();
                    }
                    maybeTrimDiskCache(context, cacheDir);
                    return bitmap;
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            } catch (Throwable e) {
                return null;
            } finally {
                releaseFileLock(lockKey);
            }
        }
    }

    private static void applyImageRequestHeaders(HttpURLConnection conn, String url) {
        conn.setRequestProperty("Accept", IMAGE_ACCEPT_HEADER);
        conn.setRequestProperty("Accept-Encoding", "gzip");
        if (!isBiliImageUrl(url)) {
            return;
        }
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 4.0.4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Mobile Safari/537.36");
        conn.setRequestProperty("Referer", "https://www.bilibili.com/");
        conn.setRequestProperty("Origin", "https://www.bilibili.com");
    }

    private static boolean isBiliImageUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        return lower.indexOf("hdslb.com") >= 0 || lower.indexOf("biliimg.com") >= 0 || lower.indexOf("bilibili.com") >= 0;
    }

    static Bitmap decodeSampledBitmap(String path, int maxSize, int maxBytes) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                return null;
            }
            int scale = computeSampleSize(opts.outWidth, opts.outHeight, maxSize, maxBytes);
            Bitmap.Config config = pickConfigByMimeType(opts.outMimeType);
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = scale;
            opts.inPreferredConfig = config;
            opts.inDither = true;
            if (android.os.Build.VERSION.SDK_INT < 11) {
                opts.inPurgeable = true;
                opts.inInputShareable = true;
            }
            return BitmapFactory.decodeFile(path, opts);
        } catch (Throwable e) {
            return null;
        }
    }

    static Bitmap decodeSampledBitmap(byte[] data, int maxSize, int maxBytes) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                return null;
            }
            int scale = computeSampleSize(opts.outWidth, opts.outHeight, maxSize, maxBytes);
            Bitmap.Config config = pickConfigByMimeType(opts.outMimeType);
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = scale;
            opts.inPreferredConfig = config;
            opts.inDither = true;
            if (android.os.Build.VERSION.SDK_INT < 11) {
                opts.inPurgeable = true;
                opts.inInputShareable = true;
            }
            return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        } catch (Throwable e) {
            return null;
        }
    }

    private static Bitmap.Config pickConfigByMimeType(String mimeType) {
        if (mimeType == null) {
            return Bitmap.Config.RGB_565;
        }
        String lower = mimeType.toLowerCase(Locale.US);
        if (lower.indexOf("png") >= 0 || lower.indexOf("webp") >= 0 || lower.indexOf("gif") >= 0) {
            return Bitmap.Config.ARGB_8888;
        }
        return Bitmap.Config.RGB_565;
    }

    private static int computeSampleSize(int width, int height, int maxSize, int maxBytes) {
        int scale = 1;
        if (maxSize > 0) {
            while (width / scale > maxSize || height / scale > maxSize) {
                scale *= 2;
            }
        }
        if (maxBytes > 0) {
            long bytesPerPixel = 2;
            while ((long) (width / scale) * (long) (height / scale) * bytesPerPixel > maxBytes) {
                scale *= 2;
            }
        }
        if (scale < 1) {
            return 1;
        }
        return scale;
    }

    static LruCache<String, Bitmap> getCache(Context context) {
        if (CACHE != null) {
            return CACHE;
        }
        synchronized (CACHE_LOCK) {
            if (CACHE == null) {
                int cacheKb = resolveCacheSizeKb(context);
                CACHE = new LruCache<String, Bitmap>(cacheKb) {
                    @Override
                    protected int sizeOf(String key, Bitmap value) {
                        return getBitmapSizeKb(value);
                    }
                };
            }
        }
        return CACHE;
    }

    static int resolveMaxBitmapSize(Context context) {
        int memClass = getMemoryClassMb(context);
        if (android.os.Build.VERSION.SDK_INT <= 10) {
            if (memClass > 0 && memClass <= 32) {
                return 360;
            }
            return 480;
        }
        if (memClass > 0 && memClass <= 64) {
            return 480;
        }
        if (memClass > 0 && memClass <= 128) {
            return 640;
        }
        return 1024;
    }

    static int resolveAvatarMaxSize(Context context) {
        int memClass = getMemoryClassMb(context);
        if (android.os.Build.VERSION.SDK_INT <= 10) {
            if (memClass > 0 && memClass <= 32) {
                return 64;
            }
            return 96;
        }
        if (memClass > 0 && memClass <= 64) {
            return 96;
        }
        if (memClass > 0 && memClass <= 128) {
            return 128;
        }
        return 192;
    }

    private static int getBitmapSizeKb(Bitmap value) {
        if (value == null) {
            return 0;
        }
        int bytes;
        if (android.os.Build.VERSION.SDK_INT >= 12) {
            bytes = value.getByteCount();
        } else {
            bytes = value.getRowBytes() * value.getHeight();
        }
        return Math.max(1, bytes / 1024);
    }

    private static int resolveCacheSizeKb(Context context) {
        int memClass = getMemoryClassMb(context);
        if (memClass <= 0) {
            return DEFAULT_CACHE_KB;
        }
        int sizeKb = memClass * 1024 / 10;
        int cap = memClass <= 128 ? LOW_MEMORY_CACHE_KB : DEFAULT_CACHE_KB;
        if (sizeKb > cap) {
            sizeKb = cap;
        }
        if (sizeKb < MIN_CACHE_KB) {
            sizeKb = MIN_CACHE_KB;
        }
        return sizeKb;
    }

    private static int getMemoryClassMb(Context context) {
        if (context == null) {
            return 0;
        }
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                return am.getMemoryClass();
            }
        } catch (Exception e) {
            return 0;
        }
        return 0;
    }

    private static int resolveMaxDownloadBytes(int maxSize) {
        if (maxSize <= 160) {
            return MAX_NETWORK_BYTES_AVATAR;
        }
        if (maxSize <= 640) {
            return MAX_NETWORK_BYTES_NORMAL;
        }
        if (maxSize <= 1200) {
            return MAX_NETWORK_BYTES_LARGE;
        }
        return MAX_NETWORK_BYTES_HUGE;
    }

    private static byte[] readToBytes(InputStream is, int maxBytes, long deadlineAt) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = is.read(buffer)) != -1) {
            if (deadlineAt > 0 && System.currentTimeMillis() > deadlineAt) {
                return null;
            }
            total += read;
            if (total > maxBytes) {
                return null;
            }
            bos.write(buffer, 0, read);
        }
        return bos.toByteArray();
    }

    private static boolean shouldUseFastFailoverTimeout(String url) {
        if (url == null || url.length() == 0) {
            return false;
        }
        if (!MediaUrlResolver.isMainOriginUrl(url)) {
            return false;
        }
        String fallback = MediaUrlResolver.resolveFallback(url);
        if (fallback == null || fallback.length() == 0 || fallback.equals(url)) {
            return false;
        }
        return MediaUrlResolver.isDataOriginUrl(fallback);
    }

    private static void maybeTrimDiskCache(Context context, String cacheDirName) {
        if (context == null || cacheDirName == null || cacheDirName.length() == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCleanupAt < CACHE_CLEANUP_INTERVAL_MS) {
            return;
        }
        synchronized (CACHE_LOCK) {
            if (now - lastCleanupAt < CACHE_CLEANUP_INTERVAL_MS) {
                return;
            }
            lastCleanupAt = now;
        }

        File base = new File(context.getCacheDir(), cacheDirName);
        File[] files = base.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        long totalBytes = 0L;
        int validCount = 0;
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file == null || !file.isFile()) {
                continue;
            }
            String name = file.getName();
            if (name != null && name.endsWith(".tmp") && now - file.lastModified() > TMP_FILE_TTL_MS) {
                file.delete();
                continue;
            }
            if (isExpired(file)) {
                file.delete();
                continue;
            }
            totalBytes += Math.max(0L, file.length());
            validCount++;
        }
        if (validCount <= 0) {
            return;
        }

        long maxBytes = resolveDiskCacheLimitBytes(context);
        if (totalBytes <= maxBytes) {
            return;
        }

        FileSortCompat.sortByLastModifiedAsc(files);

        for (int i = 0; i < files.length && totalBytes > maxBytes; i++) {
            File file = files[i];
            if (file == null || !file.isFile()) {
                continue;
            }
            long len = Math.max(0L, file.length());
            if (file.delete()) {
                totalBytes -= len;
            }
        }
    }

    private static long resolveDiskCacheLimitBytes(Context context) {
        int memClass = getMemoryClassMb(context);
        if (memClass <= 0) {
            return DISK_CACHE_LIMIT_NORMAL_BYTES;
        }
        if (memClass <= 64) {
            return DISK_CACHE_LIMIT_LOW_BYTES;
        }
        if (memClass <= 192) {
            return DISK_CACHE_LIMIT_NORMAL_BYTES;
        }
        return DISK_CACHE_LIMIT_HIGH_BYTES;
    }

    private static boolean isExpired(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        long lastModified = file.lastModified();
        if (lastModified <= 0) {
            return false;
        }
        return System.currentTimeMillis() - lastModified > DISK_CACHE_TTL_MS;
    }

    private static void touch(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            file.setLastModified(System.currentTimeMillis());
        } catch (Exception e) {
        }
    }

    private static Object acquireFileLock(String key) {
        synchronized (FILE_LOCK_GUARD) {
            Object lock = FILE_LOCKS.get(key);
            Integer count = FILE_LOCK_COUNTS.get(key);
            if (lock == null) {
                lock = new Object();
                FILE_LOCKS.put(key, lock);
                FILE_LOCK_COUNTS.put(key, 1);
                return lock;
            }
            if (count == null) {
                count = 0;
            }
            FILE_LOCK_COUNTS.put(key, count + 1);
            return lock;
        }
    }

    private static void releaseFileLock(String key) {
        synchronized (FILE_LOCK_GUARD) {
            Integer count = FILE_LOCK_COUNTS.get(key);
            if (count == null || count <= 1) {
                FILE_LOCK_COUNTS.remove(key);
                FILE_LOCKS.remove(key);
                return;
            }
            FILE_LOCK_COUNTS.put(key, count - 1);
        }
    }
}
