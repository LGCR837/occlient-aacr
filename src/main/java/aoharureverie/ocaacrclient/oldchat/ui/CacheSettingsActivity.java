package aoharureverie.ocaacrclient.oldchat.ui;

import android.os.Bundle;
import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.models.GroupRecentChatCache;
import aoharureverie.ocaacrclient.oldchat.models.RecentChatCache;
import aoharureverie.ocaacrclient.oldchat.util.AvatarSyncManager;
import aoharureverie.ocaacrclient.oldchat.util.CacheSizeUtil;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;
import java.io.File;

public class CacheSettingsActivity extends BaseActivity {
    private static final String VOICE_CACHE_DIR = "voice_cache";

    private TextView tvTotalSize;
    private TextView tvImageCacheSize;
    private TextView tvVoiceCacheSize;
    private TextView tvChatCacheSize;
    private TextView tvFriendCacheSize;
    private TextView tvGroupCacheSize;
    private TextView tvMomentCacheSize;
    private TextView tvRecentCacheSize;
    private TextView tvUserCacheSize;
    private TextView tvAvatarMetaCacheSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cache_settings);

        tvTotalSize = findViewByIdCompat(R.id.tvCacheTotalSize);
        tvImageCacheSize = findViewByIdCompat(R.id.tvImageCacheSize);
        tvVoiceCacheSize = findViewByIdCompat(R.id.tvVoiceCacheSize);
        tvChatCacheSize = findViewByIdCompat(R.id.tvChatCacheSize);
        tvFriendCacheSize = findViewByIdCompat(R.id.tvFriendCacheSize);
        tvGroupCacheSize = findViewByIdCompat(R.id.tvGroupCacheSize);
        tvMomentCacheSize = findViewByIdCompat(R.id.tvMomentCacheSize);
        tvRecentCacheSize = findViewByIdCompat(R.id.tvRecentCacheSize);
        tvUserCacheSize = findViewByIdCompat(R.id.tvUserCacheSize);
        tvAvatarMetaCacheSize = findViewByIdCompat(R.id.tvAvatarMetaCacheSize);
        View btnBack = (View) findViewByIdCompat(R.id.btnCacheBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        View btnClearImageCache = (View) findViewByIdCompat(R.id.btnClearImageCache);
        View btnClearVoiceCache = (View) findViewByIdCompat(R.id.btnClearVoiceCache);
        View btnClearChatCache = (View) findViewByIdCompat(R.id.btnClearChatCache);
        View btnClearFriendCache = (View) findViewByIdCompat(R.id.btnClearFriendCache);
        View btnClearGroupCache = (View) findViewByIdCompat(R.id.btnClearGroupCache);
        View btnClearMomentCache = (View) findViewByIdCompat(R.id.btnClearMomentCache);
        View btnClearRecentCache = (View) findViewByIdCompat(R.id.btnClearRecentCache);
        View btnClearUserCache = (View) findViewByIdCompat(R.id.btnClearUserCache);
        View btnClearAvatarMetaCache = (View) findViewByIdCompat(R.id.btnClearAvatarMetaCache);

        if (btnClearImageCache != null) {
            btnClearImageCache.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ImageLoader.clearDiskCache(CacheSettingsActivity.this);
                    Toast.makeText(CacheSettingsActivity.this, "已清理图片缓存", Toast.LENGTH_SHORT).show();
                    updateSizes();
                }
            });
        }
        if (btnClearVoiceCache != null) {
            btnClearVoiceCache.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearVoiceCache();
                    Toast.makeText(CacheSettingsActivity.this, "已清理语音缓存", Toast.LENGTH_SHORT).show();
                    updateSizes();
                }
            });
        }
        if (btnClearChatCache != null) {
            btnClearChatCache.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CacheSizeUtil.clearSharedPrefs(CacheSettingsActivity.this, "message_history_cache");
                    Toast.makeText(CacheSettingsActivity.this, "已清理聊天记录缓存", Toast.LENGTH_SHORT).show();
                    updateSizes();
                }
            });
        }
        if (btnClearFriendCache != null) {
            btnClearFriendCache.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CacheSizeUtil.clearSharedPrefs(CacheSettingsActivity.this, "friend_cache");
                    Toast.makeText(CacheSettingsActivity.this, "已清理好友缓存", Toast.LENGTH_SHORT).show();
                    updateSizes();
                }
            });
        }
        if (btnClearGroupCache != null) {
            btnClearGroupCache.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CacheSizeUtil.clearSharedPrefs(CacheSettingsActivity.this, "group_cache");
                    Toast.makeText(CacheSettingsActivity.this, "已清理群列表缓存", Toast.LENGTH_SHORT).show();
                    updateSizes();
                }
            });
        }
        if (btnClearMomentCache != null) {
            btnClearMomentCache.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CacheSizeUtil.clearSharedPrefs(CacheSettingsActivity.this, "moment_cache");
                    Toast.makeText(CacheSettingsActivity.this, "已清理动态缓存", Toast.LENGTH_SHORT).show();
                    updateSizes();
                }
            });
        }
        if (btnClearRecentCache != null) {
            btnClearRecentCache.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    RecentChatCache.clearAll(CacheSettingsActivity.this);
                    GroupRecentChatCache.clearAll(CacheSettingsActivity.this);
                    Toast.makeText(CacheSettingsActivity.this, "已清理最近会话缓存", Toast.LENGTH_SHORT).show();
                    updateSizes();
                }
            });
        }
        if (btnClearUserCache != null) {
            btnClearUserCache.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CacheSizeUtil.clearSharedPrefs(CacheSettingsActivity.this, "user_name_cache");
                    Toast.makeText(CacheSettingsActivity.this, "已清理昵称缓存", Toast.LENGTH_SHORT).show();
                    updateSizes();
                }
            });
        }
        if (btnClearAvatarMetaCache != null) {
            btnClearAvatarMetaCache.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AvatarSyncManager.clearAll(CacheSettingsActivity.this);
                    Toast.makeText(CacheSettingsActivity.this, "已清理头像映射缓存", Toast.LENGTH_SHORT).show();
                    updateSizes();
                }
            });
        }
        updateSizes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSizes();
    }

    private void updateSizes() {
        long imageSize = CacheSizeUtil.getDirSize(new File(getCacheDir(), "img_cache"));
        long voiceSize = getVoiceCacheSize();
        long chatSize = CacheSizeUtil.getSharedPrefsSize(this, "message_history_cache");
        long friendSize = CacheSizeUtil.getSharedPrefsSize(this, "friend_cache");
        long groupSize = CacheSizeUtil.getSharedPrefsSize(this, "group_cache");
        long momentSize = CacheSizeUtil.getSharedPrefsSize(this, "moment_cache");
        long recentSize = CacheSizeUtil.getSharedPrefsSize(this, "recent_chats")
                + CacheSizeUtil.getSharedPrefsSize(this, "recent_groups");
        long userSize = CacheSizeUtil.getSharedPrefsSize(this, "user_name_cache");
        long avatarMetaSize = CacheSizeUtil.getSharedPrefsSize(this, "avatar_cache");

        long total = imageSize + voiceSize + chatSize + friendSize + groupSize + momentSize +
                recentSize + userSize + avatarMetaSize;

        setSizeText(tvTotalSize, total);
        setSizeText(tvImageCacheSize, imageSize);
        setSizeText(tvVoiceCacheSize, voiceSize);
        setSizeText(tvChatCacheSize, chatSize);
        setSizeText(tvFriendCacheSize, friendSize);
        setSizeText(tvGroupCacheSize, groupSize);
        setSizeText(tvMomentCacheSize, momentSize);
        setSizeText(tvRecentCacheSize, recentSize);
        setSizeText(tvUserCacheSize, userSize);
        setSizeText(tvAvatarMetaCacheSize, avatarMetaSize);
    }

    private long getVoiceCacheSize() {
        File filesDirCache = getVoiceCacheDirInFiles();
        File cacheDirCache = getVoiceCacheDirInCache();
        long total = 0;
        total += CacheSizeUtil.getDirSize(filesDirCache);
        if (cacheDirCache != null) {
            if (filesDirCache == null || !cacheDirCache.getAbsolutePath().equals(filesDirCache.getAbsolutePath())) {
                total += CacheSizeUtil.getDirSize(cacheDirCache);
            }
        }
        return total;
    }

    private void clearVoiceCache() {
        File filesDirCache = getVoiceCacheDirInFiles();
        File cacheDirCache = getVoiceCacheDirInCache();
        deleteRecursive(filesDirCache);
        if (cacheDirCache != null) {
            if (filesDirCache == null || !cacheDirCache.getAbsolutePath().equals(filesDirCache.getAbsolutePath())) {
                deleteRecursive(cacheDirCache);
            }
        }
    }

    private File getVoiceCacheDirInFiles() {
        File root = getFilesDir();
        if (root == null) {
            return null;
        }
        return new File(root, VOICE_CACHE_DIR);
    }

    private File getVoiceCacheDirInCache() {
        File root = getCacheDir();
        if (root == null) {
            return null;
        }
        return new File(root, VOICE_CACHE_DIR);
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteRecursive(children[i]);
                }
            }
        }
        try {
            file.delete();
        } catch (Exception e) {
        }
    }

    private void setSizeText(TextView view, long bytes) {
        if (view != null) {
            view.setText(CacheSizeUtil.formatSize(bytes));
        }
    }
}
