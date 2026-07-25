package aoharureverie.ocaacrclient.oldchat.util;

import android.content.Context;

import java.io.File;
import java.security.MessageDigest;
import java.util.Locale;

public class ImageCacheUtil {
    private ImageCacheUtil() {
    }

    public static File getCacheFile(Context context, String cacheDirName, String url) {
        if (context == null || url == null) {
            return null;
        }
        File base = new File(context.getCacheDir(), cacheDirName);
        if (!base.exists() && !base.mkdirs()) {
            return null;
        }
        String name = buildCacheKey(url);
        return new File(base, name + ".img");
    }

    public static String buildCacheKey(String url) {
        String key = normalizeCacheKey(url);
        return hashKey(key);
    }

    public static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    private static String normalizeCacheKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        int hashIndex = normalized.indexOf('#');
        if (hashIndex >= 0) {
            normalized = normalized.substring(0, hashIndex);
        }
        int queryIndex = normalized.indexOf('?');
        if (queryIndex > 0) {
            String path = normalized.substring(0, queryIndex).toLowerCase(Locale.US);
            if (path.contains("/uploads/") || path.contains("/v1/uploads/")) {
                normalized = normalized.substring(0, queryIndex);
            }
        }
        return normalized;
    }

    private static String hashKey(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(value.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(value.hashCode());
        }
    }
}
