package aoharureverie.ocaacrclient.oldchat.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.util.LruCache;
import android.widget.ImageView;
import aoharureverie.ocaacrclient.oldchat.R;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageLoader {
    public interface ImageLoadListener {
        void onComplete(String url);
    }

    private static final String CACHE_DIR = "img_cache";
    private static final int AVATAR_MAX_BYTES = 16 * 1024;

    // 使用有限的线程池，避免同时加载太多图片
    // 最多 8 个线程用于图片加载，群聊中需要更多并发
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(8);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public static void load(ImageView view, String url) {
        load(view, url, null);
    }

    public static void load(ImageView view, String url, ImageLoadListener listener) {
        if (url == null || url.isEmpty()) {
            view.setTag(null);
            view.setImageResource(R.drawable.bg_image_placeholder);
            notifyComplete(listener, null);
            return;
        }
        String resolved = resolveUrl(url);
        String cacheKey = ImageCacheUtil.buildCacheKey(resolved);

        // 如果当前tag已经是目标URL，且图片已加载，则不需要重新加载
        Object currentTag = view.getTag();
        boolean sameTag = resolved.equals(currentTag);

        view.setTag(resolved);
        Context ctx = view.getContext();
        Context appContext = ctx != null ? ctx.getApplicationContext() : null;


        LruCache<String, Bitmap> cache = ImageLoaderBitmapSupport.getCache(appContext);
        if (sameTag) {
            Bitmap cached = cache.get(cacheKey);
            if (cached != null) {
                notifyComplete(listener, resolved);
                return;
            }
        }

        // 先检查内存缓存
        Bitmap cached = cache.get(cacheKey);
        if (cached != null) {
            view.setImageBitmap(cached);
            notifyComplete(listener, resolved);
            return;
        }

        int maxSize = ImageLoaderBitmapSupport.resolveMaxBitmapSize(appContext);

        // 主线程只检查文件是否存在，不做磁盘解码，避免旧机卡顿
        boolean hasDiskCache = appContext != null && isCached(appContext, resolved);
        if (!sameTag && !hasDiskCache) {
            view.setImageResource(R.drawable.bg_image_placeholder);
        }

        // 没有内存缓存，异步加载磁盘/网络
        loadAsync(view, appContext, resolved, cacheKey, listener, maxSize, 0);
    }

    public static void loadLarge(ImageView view, String url, ImageLoadListener listener) {
        if (url == null || url.isEmpty()) {
            view.setTag(null);
            view.setImageResource(R.drawable.bg_image_placeholder);
            notifyComplete(listener, null);
            return;
        }
        String resolved = resolveUrl(url);
        String cacheKey = ImageCacheUtil.buildCacheKey(resolved);

        Object currentTag = view.getTag();
        boolean sameTag = resolved.equals(currentTag);
        view.setTag(resolved);
        Context ctx = view.getContext();
        Context appContext = ctx != null ? ctx.getApplicationContext() : null;

        LruCache<String, Bitmap> cache = ImageLoaderBitmapSupport.getCache(appContext);
        Bitmap cached = cache.get(cacheKey);
        if (cached != null) {
            view.setImageBitmap(cached);
            notifyComplete(listener, resolved);
            return;
        }

        boolean hasDiskCache = appContext != null && isCached(appContext, resolved);
        if (!sameTag && !hasDiskCache) {
            view.setImageResource(R.drawable.bg_image_placeholder);
        }

        int maxSize = ImageLoaderBitmapSupport.resolveMaxBitmapSize(appContext);
        loadAsync(view, appContext, resolved, cacheKey, listener, maxSize, 0);
    }

    public static void loadOriginal(ImageView view, String url, ImageLoadListener listener) {
        if (url == null || url.isEmpty()) {
            view.setTag(null);
            view.setImageResource(R.drawable.bg_image_placeholder);
            notifyComplete(listener, null);
            return;
        }
        String resolved = resolveUrl(url);
        view.setTag(resolved);
        Context ctx = view.getContext();
        Context appContext = ctx != null ? ctx.getApplicationContext() : null;
        String cacheKey = ImageCacheUtil.buildCacheKey(resolved);

        // 加载原图，不限制尺寸，只限制最大字节数防止 OOM
        int maxSize = 2048;  // 最大 2048px，防止超大图片 OOM
        loadAsync(view, appContext, resolved, cacheKey, listener, maxSize, 0);
    }

    public static void loadAvatar(ImageView view, String url) {
        loadAvatar(view, url, null);
    }

    public static void loadAvatar(ImageView view, String url, ImageLoadListener listener) {
        if (view != null) {
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
        if (url == null || url.isEmpty()) {
            view.setTag(null);
            view.setImageResource(R.drawable.ic_avatar_placeholder);
            notifyComplete(listener, null);
            return;
        }
        String resolved = resolveUrl(url);
        String cacheKey = ImageCacheUtil.buildCacheKey(resolved);

        Object currentTag = view.getTag();
        boolean sameTag = resolved.equals(currentTag);
        view.setTag(resolved);

        Context ctx = view.getContext();
        Context appContext = ctx != null ? ctx.getApplicationContext() : null;


        LruCache<String, Bitmap> cache = ImageLoaderBitmapSupport.getCache(appContext);

        // 先检查内存缓存
        Bitmap cached = cache.get(cacheKey);
        if (cached != null) {
            // 有缓存直接设置，不闪烁
            view.setImageBitmap(cached);
            notifyComplete(listener, resolved);
            return;
        }

        int maxSize = ImageLoaderBitmapSupport.resolveAvatarMaxSize(appContext);

        // 主线程只检查文件是否存在，不做磁盘解码，避免旧机卡顿
        boolean hasDiskCache = appContext != null && isCached(appContext, resolved);

        // 只在以下情况显示占位图：
        // 1. tag变化了（加载不同的图片）
        // 2. 且没有磁盘缓存
        if (!sameTag && !hasDiskCache) {
            view.setImageResource(R.drawable.ic_avatar_placeholder);
        }

        // 异步加载
        loadAsync(view, appContext, resolved, cacheKey, listener, maxSize, AVATAR_MAX_BYTES);
    }

    public static void prefetch(Context context, String url) {
        if (context == null || url == null || url.isEmpty()) {
            return;
        }
        String resolved = resolveUrl(url);
        String cacheKey = ImageCacheUtil.buildCacheKey(resolved);
        if (isCached(context, resolved)) {
            return;
        }
        prefetchAsync(context.getApplicationContext(), resolved, cacheKey,
                ImageLoaderBitmapSupport.resolveAvatarMaxSize(context), AVATAR_MAX_BYTES);
    }

    public static boolean isCached(Context context, String url) {
        if (context == null || url == null || url.isEmpty()) {
            return false;
        }
        String resolved = resolveUrl(url);
        File cached = ImageCacheUtil.getCacheFile(context.getApplicationContext(), CACHE_DIR, resolved);
        return cached != null && cached.exists();
    }

    private static String resolveUrl(String url) {
        return MediaUrlResolver.resolve(url);
    }

    // 使用 ExecutorService 替代 AsyncTask
    private static void loadAsync(final ImageView view, final Context context,
                                   final String url, final String cacheKey,
                                   final ImageLoadListener listener,
                                   final int maxSize, final int maxBytes) {
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap loaded = ImageLoaderBitmapSupport.downloadBitmap(context, CACHE_DIR, url, maxSize, maxBytes);
                String loadedFrom = url;
                if (loaded == null) {
                    String fallbackUrl = MediaUrlResolver.resolveFallback(url);
                    if (fallbackUrl != null && fallbackUrl.length() > 0 && !fallbackUrl.equals(url)) {
                        loaded = ImageLoaderBitmapSupport.downloadBitmap(context, CACHE_DIR, fallbackUrl, maxSize, maxBytes);
                        if (loaded != null) {
                            loadedFrom = fallbackUrl;
                        }
                    }
                }
                // 失败时延迟500ms重试一次
                if (loaded == null) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {
                    }
                    if (!url.equals(view.getTag())) {
                        return;
                    }
                    loaded = ImageLoaderBitmapSupport.downloadBitmap(context, CACHE_DIR, url, maxSize, maxBytes);
                    loadedFrom = url;
                    if (loaded == null) {
                        String fallbackUrl = MediaUrlResolver.resolveFallback(url);
                        if (fallbackUrl != null && fallbackUrl.length() > 0 && !fallbackUrl.equals(url)) {
                            loaded = ImageLoaderBitmapSupport.downloadBitmap(context, CACHE_DIR, fallbackUrl, maxSize, maxBytes);
                            if (loaded != null) {
                                loadedFrom = fallbackUrl;
                            }
                        }
                    }
                }
                final Bitmap bitmap = loaded;
                final String finalLoadedFrom = loadedFrom;
                MAIN_HANDLER.post(new Runnable() {
                    @Override
                    public void run() {
                        if (bitmap != null && url.equals(view.getTag())) {
                            LruCache<String, Bitmap> cache = ImageLoaderBitmapSupport.getCache(context);
                            cache.put(cacheKey, bitmap);
                            view.setImageBitmap(bitmap);
                        }
                        notifyComplete(listener, finalLoadedFrom);
                    }
                });
            }
        });
    }

    private static void prefetchAsync(final Context context, final String url,
                                      final String cacheKey,
                                      final int maxSize, final int maxBytes) {
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap loaded = ImageLoaderBitmapSupport.downloadBitmap(context, CACHE_DIR, url, maxSize, maxBytes);
                if (loaded == null) {
                    String fallbackUrl = MediaUrlResolver.resolveFallback(url);
                    if (fallbackUrl != null && fallbackUrl.length() > 0 && !fallbackUrl.equals(url)) {
                        loaded = ImageLoaderBitmapSupport.downloadBitmap(context, CACHE_DIR, fallbackUrl, maxSize, maxBytes);
                    }
                }
                final Bitmap bitmap = loaded;
                if (bitmap != null) {
                    MAIN_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            LruCache<String, Bitmap> cache = ImageLoaderBitmapSupport.getCache(context);
                            cache.put(cacheKey, bitmap);
                        }
                    });
                }
            }
        });
    }


    private static void notifyComplete(ImageLoadListener listener, String url) {
        if (listener != null) {
            listener.onComplete(url);
        }
    }


    public static void clearDiskCache(Context context) {
        if (context == null) {
            return;
        }
        File base = new File(context.getCacheDir(), CACHE_DIR);
        ImageCacheUtil.deleteRecursive(base);
        LruCache<String, Bitmap> cache = ImageLoaderBitmapSupport.getCache(context.getApplicationContext());
        if (cache != null) {
            cache.evictAll();
        }
    }
}
