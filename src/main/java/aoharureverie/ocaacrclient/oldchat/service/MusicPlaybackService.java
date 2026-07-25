package aoharureverie.ocaacrclient.oldchat.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.ui.MusicPlayerActivity;
import aoharureverie.ocaacrclient.oldchat.util.FileSortCompat;
import aoharureverie.ocaacrclient.oldchat.util.MediaUrlResolver;
import aoharureverie.ocaacrclient.oldchat.util.NotificationChannelCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

public class MusicPlaybackService extends Service implements AudioManager.OnAudioFocusChangeListener {
    public static final String ACTION_PLAY_SONG = "aoharureverie.ocaacrclient.oldchat.action.music.PLAY_SONG";
    public static final String ACTION_CACHE_SONG = "aoharureverie.ocaacrclient.oldchat.action.music.CACHE_SONG";
    public static final String ACTION_TOGGLE_PLAY = "aoharureverie.ocaacrclient.oldchat.action.music.TOGGLE_PLAY";
    public static final String ACTION_PAUSE = "aoharureverie.ocaacrclient.oldchat.action.music.PAUSE";
    public static final String ACTION_STOP = "aoharureverie.ocaacrclient.oldchat.action.music.STOP";
    public static final String ACTION_SEEK = "aoharureverie.ocaacrclient.oldchat.action.music.SEEK";
    public static final String ACTION_SEEK_RELATIVE = "aoharureverie.ocaacrclient.oldchat.action.music.SEEK_RELATIVE";
    public static final String ACTION_TOGGLE_REPEAT_ONE = "aoharureverie.ocaacrclient.oldchat.action.music.TOGGLE_REPEAT_ONE";
    public static final String ACTION_REQUEST_STATE = "aoharureverie.ocaacrclient.oldchat.action.music.REQUEST_STATE";

    public static final String ACTION_STATE_CHANGED = "aoharureverie.ocaacrclient.oldchat.action.music.STATE_CHANGED";
    public static final String ACTION_CACHE_RESULT = "aoharureverie.ocaacrclient.oldchat.action.music.CACHE_RESULT";

    public static final String EXTRA_SONG_NAME = "song_name";
    public static final String EXTRA_SONG_URL = "song_url";
    public static final String EXTRA_COVER_URL = "cover_url";
    public static final String EXTRA_LYRICS_URL = "lyrics_url";
    public static final String EXTRA_OWNER_UID = "owner_uid";
    public static final String EXTRA_OWNER_NAME = "owner_name";
    public static final String EXTRA_OWNER_TITLE = "owner_title";
    public static final String EXTRA_OWNER_AVATAR = "owner_avatar";
    public static final String EXTRA_CACHE_URL = "cache_url";
    public static final String EXTRA_CACHE_OK = "cache_ok";
    public static final String EXTRA_CACHE_SIZE = "cache_size";
    public static final String EXTRA_CACHE_ERROR = "cache_error";

    public static final String EXTRA_IS_PREPARING = "is_preparing";
    public static final String EXTRA_IS_PLAYING = "is_playing";
    public static final String EXTRA_DURATION_MS = "duration_ms";
    public static final String EXTRA_POSITION_MS = "position_ms";
    public static final String EXTRA_SEEK_DELTA_MS = "seek_delta_ms";
    public static final String EXTRA_REPEAT_ONE = "repeat_one";
    public static final String EXTRA_ERROR = "error";

    private static final String CHANNEL_ID = "oldchat_music_playback";
    private static final int NOTIFY_ID = 5201;
    private static final int PROGRESS_TICK_MS = 500;

    private static final String MUSIC_CACHE_DIR = "music_player_cache";
    private static final long MUSIC_CACHE_MAX_BYTES = 220L * 1024L * 1024L;
    private static final int MUSIC_CACHE_MAX_FILES = 120;

    private MediaPlayer mediaPlayer;
    private boolean isPreparing;
    private int prepareToken;
    private int currentDurationMs;
    private int currentPositionMs;
    private boolean repeatOne;

    private String currentSongName = "";
    private String currentSongUrl = "";
    private String currentCoverUrl = "";
    private String currentLyricsUrl = "";
    private String currentOwnerUid = "";
    private String currentOwnerName = "";
    private String currentOwnerTitle = "";
    private String currentOwnerAvatar = "";

    private AudioManager audioManager;
    private boolean resumeOnFocusGain;
    private volatile boolean isTrimmingCache;
    private final Object cacheWarmLock = new Object();
    private String warmingCacheUrl = "";

    private LocalBroadcastManager localBroadcastManager;

    private final Handler progressHandler = new Handler();
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            updateProgressFromPlayer();
            pushState("");
            if (isPlayingInternal()) {
                progressHandler.postDelayed(this, PROGRESS_TICK_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_REQUEST_STATE : intent.getAction();
        if (ACTION_PLAY_SONG.equals(action)) {
            handlePlaySong(intent);
        } else if (ACTION_CACHE_SONG.equals(action)) {
            handleCacheSong(intent);
        } else if (ACTION_TOGGLE_PLAY.equals(action)) {
            handleTogglePlay();
        } else if (ACTION_PAUSE.equals(action)) {
            pauseInternal();
        } else if (ACTION_STOP.equals(action)) {
            stopAndExit();
        } else if (ACTION_SEEK.equals(action)) {
            int to = intent == null ? 0 : intent.getIntExtra(EXTRA_POSITION_MS, 0);
            seekToInternal(to);
        } else if (ACTION_SEEK_RELATIVE.equals(action)) {
            int delta = intent == null ? 0 : intent.getIntExtra(EXTRA_SEEK_DELTA_MS, 0);
            seekByInternal(delta);
        } else if (ACTION_TOGGLE_REPEAT_ONE.equals(action)) {
            repeatOne = !repeatOne;
            pushState("");
            updateForegroundNotification();
        } else if (ACTION_REQUEST_STATE.equals(action)) {
            updateProgressFromPlayer();
            pushState("");
        }
        return START_STICKY;
    }

    private void handleCacheSong(Intent intent) {
        String songUrl = intent == null ? "" : strings(intent.getStringExtra(EXTRA_SONG_URL));
        String resolvedUrl = MediaUrlResolver.resolve(songUrl);
        if (resolvedUrl.length() == 0) {
            dispatchCacheResult(songUrl, false, 0L, "歌曲链接不可用");
            return;
        }
        warmMusicCacheAsync(resolvedUrl, true);
    }

    @Override
    public void onDestroy() {
        stopProgressTicker();
        releasePlayerInternal();
        abandonAudioFocusSafe();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            resumeOnFocusGain = isPlayingInternal();
            pauseInternal();
            return;
        }
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            resumeOnFocusGain = false;
            pauseInternal();
            return;
        }
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            if (resumeOnFocusGain) {
                resumeOnFocusGain = false;
                startInternal();
            }
        }
    }

    private void handlePlaySong(Intent intent) {
        if (intent == null) {
            return;
        }
        String songUrl = strings(intent.getStringExtra(EXTRA_SONG_URL));
        String resolvedUrl = MediaUrlResolver.resolve(songUrl);
        if (resolvedUrl.length() == 0) {
            pushState("歌曲链接不可用");
            return;
        }

        String songName = strings(intent.getStringExtra(EXTRA_SONG_NAME));
        String coverUrl = strings(intent.getStringExtra(EXTRA_COVER_URL));
        String lyricsUrl = strings(intent.getStringExtra(EXTRA_LYRICS_URL));
        String ownerUid = strings(intent.getStringExtra(EXTRA_OWNER_UID));
        String ownerName = strings(intent.getStringExtra(EXTRA_OWNER_NAME));
        String ownerTitle = strings(intent.getStringExtra(EXTRA_OWNER_TITLE));
        String ownerAvatar = strings(intent.getStringExtra(EXTRA_OWNER_AVATAR));

        boolean sameSong = resolvedUrl.equals(currentSongUrl);
        if (sameSong && mediaPlayer != null) {
            currentSongName = songName;
            currentCoverUrl = coverUrl;
            currentLyricsUrl = lyricsUrl;
            currentOwnerUid = ownerUid;
            currentOwnerName = ownerName;
            currentOwnerTitle = ownerTitle;
            currentOwnerAvatar = ownerAvatar;
            if (!isPreparing && !isPlayingInternal()) {
                if (isPlaybackCompleted()) {
                    seekToInternal(0);
                }
                startInternal();
            }
            updateProgressFromPlayer();
            pushState("");
            updateForegroundNotification();
            return;
        }

        currentSongName = songName;
        currentSongUrl = resolvedUrl;
        currentCoverUrl = coverUrl;
        currentLyricsUrl = lyricsUrl;
        currentOwnerUid = ownerUid;
        currentOwnerName = ownerName;
        currentOwnerTitle = ownerTitle;
        currentOwnerAvatar = ownerAvatar;

        isPreparing = true;
        currentDurationMs = 0;
        currentPositionMs = 0;
        pushState("");
        updateForegroundNotification();

        final int token = ++prepareToken;
        File cachedFile = resolveMusicCacheFile(resolvedUrl);
        if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
            try {
                cachedFile.setLastModified(System.currentTimeMillis());
            } catch (Exception e) {
            }
            prepareFromFile(cachedFile, resolvedUrl, token);
            return;
        }

        prepareFromUrl(resolvedUrl, token);
        if (shouldWarmCacheForPlayback(resolvedUrl)) {
            warmMusicCacheAsync(resolvedUrl);
        }
    }

    private void prepareFromUrl(final String url, final int token) {
        releasePlayerInternal();
        try {
            mediaPlayer = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= 21) {
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }
            mediaPlayer.setDataSource(url);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    if (token != prepareToken) {
                        return;
                    }
                    isPreparing = false;
                    try {
                        currentDurationMs = mp.getDuration();
                    } catch (Exception e) {
                        currentDurationMs = 0;
                    }
                    currentPositionMs = 0;
                    startInternal();
                    trimMusicCacheAsync();
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if (repeatOne) {
                        seekToInternal(0);
                        startInternal();
                        return;
                    }
                    stopProgressTicker();
                    updateProgressFromPlayer();
                    pushState("");
                    updateForegroundNotification();
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    if (tryFallbackPrepare(url, token)) {
                        return true;
                    }
                    isPreparing = false;
                    stopProgressTicker();
                    releasePlayerInternal();
                    pushState("播放失败");
                    updateForegroundNotification();
                    return true;
                }
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            if (tryFallbackPrepare(url, token)) {
                return;
            }
            isPreparing = false;
            releasePlayerInternal();
            pushState("播放失败");
            updateForegroundNotification();
        }
    }

    private boolean tryFallbackPrepare(String currentUrl, int token) {
        if (token != prepareToken) {
            return false;
        }
        String fallbackUrl = MediaUrlResolver.resolveFallback(currentUrl);
        if (fallbackUrl == null || fallbackUrl.length() == 0 || fallbackUrl.equals(currentUrl)) {
            return false;
        }
        currentSongUrl = fallbackUrl;
        prepareFromUrl(fallbackUrl, token);
        if (shouldWarmCacheForPlayback(fallbackUrl)) {
            warmMusicCacheAsync(fallbackUrl);
        }
        return true;
    }

    private void handleTogglePlay() {
        if (isPreparing) {
            pushState("");
            return;
        }
        if (mediaPlayer == null) {
            if (currentSongUrl != null && currentSongUrl.length() > 0) {
                Intent replay = new Intent(this, MusicPlaybackService.class);
                replay.setAction(ACTION_PLAY_SONG);
                replay.putExtra(EXTRA_SONG_NAME, currentSongName);
                replay.putExtra(EXTRA_SONG_URL, currentSongUrl);
                replay.putExtra(EXTRA_COVER_URL, currentCoverUrl);
                replay.putExtra(EXTRA_LYRICS_URL, currentLyricsUrl);
                replay.putExtra(EXTRA_OWNER_UID, currentOwnerUid);
                replay.putExtra(EXTRA_OWNER_NAME, currentOwnerName);
                replay.putExtra(EXTRA_OWNER_TITLE, currentOwnerTitle);
                replay.putExtra(EXTRA_OWNER_AVATAR, currentOwnerAvatar);
                onStartCommand(replay, 0, 0);
            }
            return;
        }
        if (isPlayingInternal()) {
            pauseInternal();
        } else {
            startInternal();
        }
    }

    private void prepareFromFile(final File file, final String fallbackUrl, final int token) {
        releasePlayerInternal();
        try {
            mediaPlayer = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= 21) {
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    if (token != prepareToken) {
                        return;
                    }
                    isPreparing = false;
                    try {
                        currentDurationMs = mp.getDuration();
                    } catch (Exception e) {
                        currentDurationMs = 0;
                    }
                    currentPositionMs = 0;
                    startInternal();
                    trimMusicCacheAsync();
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if (repeatOne) {
                        seekToInternal(0);
                        startInternal();
                        return;
                    }
                    stopProgressTicker();
                    updateProgressFromPlayer();
                    pushState("");
                    updateForegroundNotification();
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    stopProgressTicker();
                    releasePlayerInternal();
                    if (file != null) {
                        try {
                            file.delete();
                        } catch (Exception ignored) {
                        }
                    }
                    if (fallbackUrl != null && fallbackUrl.length() > 0 && token == prepareToken) {
                        prepareFromUrl(fallbackUrl, token);
                        if (shouldWarmCacheForPlayback(fallbackUrl)) {
                            warmMusicCacheAsync(fallbackUrl);
                        }
                        return true;
                    }
                    isPreparing = false;
                    pushState("播放失败");
                    updateForegroundNotification();
                    return true;
                }
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            releasePlayerInternal();
            if (file != null) {
                try {
                    file.delete();
                } catch (Exception ignored) {
                }
            }
            if (fallbackUrl != null && fallbackUrl.length() > 0 && token == prepareToken) {
                prepareFromUrl(fallbackUrl, token);
                if (shouldWarmCacheForPlayback(fallbackUrl)) {
                    warmMusicCacheAsync(fallbackUrl);
                }
                return;
            }
            isPreparing = false;
            pushState("播放失败");
            updateForegroundNotification();
        }
    }

    private void startInternal() {
        if (mediaPlayer == null) {
            return;
        }
        if (!requestAudioFocusSafe()) {
            pushState("音频焦点被占用");
            updateForegroundNotification();
            return;
        }
        try {
            mediaPlayer.start();
            startProgressTicker();
            pushState("");
            updateForegroundNotification();
        } catch (Exception e) {
            pushState("播放失败");
            updateForegroundNotification();
        }
    }

    private void pauseInternal() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }
            } catch (Exception e) {
            }
        }
        stopProgressTicker();
        updateProgressFromPlayer();
        pushState("");
        updateForegroundNotification();
        abandonAudioFocusSafe();
    }

    private void seekToInternal(int positionMs) {
        if (mediaPlayer == null) {
            return;
        }
        if (positionMs < 0) {
            positionMs = 0;
        }
        try {
            mediaPlayer.seekTo(positionMs);
        } catch (Exception e) {
        }
        updateProgressFromPlayer();
        pushState("");
    }

    private void seekByInternal(int deltaMs) {
        if (deltaMs == 0) {
            pushState("");
            return;
        }
        int now = currentPositionMs;
        if (mediaPlayer != null) {
            try {
                now = mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
            }
        }
        int target = now + deltaMs;
        if (target < 0) {
            target = 0;
        }
        int duration = currentDurationMs;
        if (mediaPlayer != null) {
            try {
                duration = Math.max(duration, mediaPlayer.getDuration());
            } catch (Exception e) {
            }
        }
        if (duration > 0 && target > duration) {
            target = duration;
        }
        seekToInternal(target);
    }

    private void stopAndExit() {
        ++prepareToken;
        isPreparing = false;
        stopProgressTicker();
        releasePlayerInternal();
        abandonAudioFocusSafe();
        stopForegroundCompat();
        pushState("");
        stopSelf();
    }

    private void releasePlayerInternal() {
        if (mediaPlayer == null) {
            return;
        }
        try {
            mediaPlayer.stop();
        } catch (Exception e) {
        }
        try {
            mediaPlayer.release();
        } catch (Exception e) {
        }
        mediaPlayer = null;
    }

    private void updateProgressFromPlayer() {
        if (mediaPlayer == null) {
            currentPositionMs = 0;
            if (currentDurationMs < 0) {
                currentDurationMs = 0;
            }
            return;
        }
        try {
            currentPositionMs = Math.max(0, mediaPlayer.getCurrentPosition());
        } catch (Exception e) {
            currentPositionMs = 0;
        }
        try {
            currentDurationMs = Math.max(0, mediaPlayer.getDuration());
        } catch (Exception e) {
            if (currentDurationMs < 0) {
                currentDurationMs = 0;
            }
        }
    }

    private void startProgressTicker() {
        stopProgressTicker();
        progressHandler.post(progressTicker);
    }

    private void stopProgressTicker() {
        progressHandler.removeCallbacks(progressTicker);
    }

    private boolean isPlayingInternal() {
        if (mediaPlayer == null) {
            return false;
        }
        try {
            return mediaPlayer.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isPlaybackCompleted() {
        if (mediaPlayer == null) {
            return false;
        }
        int duration = currentDurationMs;
        int position = currentPositionMs;
        if (duration <= 0) {
            try {
                duration = Math.max(0, mediaPlayer.getDuration());
            } catch (Exception e) {
                duration = 0;
            }
        }
        if (position <= 0) {
            try {
                position = Math.max(0, mediaPlayer.getCurrentPosition());
            } catch (Exception e) {
                position = 0;
            }
        }
        return duration > 0 && position >= duration - 400;
    }

    private void pushState(String error) {
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.putExtra(EXTRA_SONG_NAME, currentSongName);
        intent.putExtra(EXTRA_SONG_URL, currentSongUrl);
        intent.putExtra(EXTRA_COVER_URL, currentCoverUrl);
        intent.putExtra(EXTRA_LYRICS_URL, currentLyricsUrl);
        intent.putExtra(EXTRA_OWNER_UID, currentOwnerUid);
        intent.putExtra(EXTRA_OWNER_NAME, currentOwnerName);
        intent.putExtra(EXTRA_OWNER_TITLE, currentOwnerTitle);
        intent.putExtra(EXTRA_OWNER_AVATAR, currentOwnerAvatar);
        intent.putExtra(EXTRA_IS_PREPARING, isPreparing);
        intent.putExtra(EXTRA_IS_PLAYING, isPlayingInternal());
        intent.putExtra(EXTRA_DURATION_MS, Math.max(0, currentDurationMs));
        intent.putExtra(EXTRA_POSITION_MS, Math.max(0, currentPositionMs));
        intent.putExtra(EXTRA_REPEAT_ONE, repeatOne);
        intent.putExtra(EXTRA_ERROR, error == null ? "" : error);
        if (localBroadcastManager != null) {
            localBroadcastManager.sendBroadcast(intent);
        }
    }

    private void updateForegroundNotification() {
        Notification notification = buildPlaybackNotification();
        if (notification == null) {
            return;
        }
        try {
            startForeground(NOTIFY_ID, notification);
        } catch (Throwable e) {
        }
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(true);
            return;
        }
        stopForeground(true);
    }

    private Notification buildPlaybackNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannelCompat.ensureChannel(manager, CHANNEL_ID, "音乐播放", NotificationChannelCompat.IMPORTANCE_LOW);
            return buildPlaybackNotification26();
        }
        return buildPlaybackNotificationCompat();
    }

    private Notification buildPlaybackNotificationCompat() {
        String title = currentSongName;
        if (title == null || title.length() == 0) {
            title = "旧聊音乐";
        }
        String text = resolveNotificationText();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_msg_sent)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(buildContentIntent());

        PendingIntent toggleIntent = buildServiceActionPendingIntent(ACTION_TOGGLE_PLAY, 1);
        PendingIntent repeatIntent = buildServiceActionPendingIntent(ACTION_TOGGLE_REPEAT_ONE, 2);
        PendingIntent stopIntent = buildServiceActionPendingIntent(ACTION_STOP, 3);
        if (toggleIntent != null) {
            int icon = isPlayingInternal() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
            String textAction = isPlayingInternal() ? "暂停" : "播放";
            builder.addAction(icon, textAction, toggleIntent);
        }
        if (repeatIntent != null) {
            String textAction = repeatOne ? "循环开" : "循环关";
            builder.addAction(android.R.drawable.ic_menu_rotate, textAction, repeatIntent);
        }
        if (stopIntent != null) {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭", stopIntent);
        }
        return builder.build();
    }

    private Notification buildPlaybackNotification26() {
        String title = currentSongName;
        if (title == null || title.length() == 0) {
            title = "旧聊音乐";
        }
        String text = resolveNotificationText();
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_msg_sent)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(buildContentIntent());

        PendingIntent toggleIntent = buildServiceActionPendingIntent(ACTION_TOGGLE_PLAY, 1);
        PendingIntent repeatIntent = buildServiceActionPendingIntent(ACTION_TOGGLE_REPEAT_ONE, 2);
        PendingIntent stopIntent = buildServiceActionPendingIntent(ACTION_STOP, 3);
        if (toggleIntent != null) {
            int icon = isPlayingInternal() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
            String textAction = isPlayingInternal() ? "暂停" : "播放";
            builder.addAction(new Notification.Action.Builder(icon, textAction, toggleIntent).build());
        }
        if (repeatIntent != null) {
            String textAction = repeatOne ? "循环开" : "循环关";
            builder.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_rotate, textAction, repeatIntent).build());
        }
        if (stopIntent != null) {
            builder.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "关闭", stopIntent).build());
        }
        return builder.build();
    }

    private String resolveNotificationText() {
        if (isPreparing) {
            return "歌曲加载中";
        }
        if (isPlayingInternal()) {
            return "正在后台播放";
        }
        if (mediaPlayer != null) {
            return "已暂停";
        }
        return "等待播放";
    }

    private PendingIntent buildContentIntent() {
        Intent intent = new Intent(this, MusicPlayerActivity.class);
        intent.putExtra(EXTRA_SONG_NAME, currentSongName);
        intent.putExtra(EXTRA_SONG_URL, currentSongUrl);
        intent.putExtra(EXTRA_COVER_URL, currentCoverUrl);
        intent.putExtra(EXTRA_LYRICS_URL, currentLyricsUrl);
        intent.putExtra(EXTRA_OWNER_UID, currentOwnerUid);
        intent.putExtra(EXTRA_OWNER_NAME, currentOwnerName);
        intent.putExtra(EXTRA_OWNER_TITLE, currentOwnerTitle);
        intent.putExtra(EXTRA_OWNER_AVATAR, currentOwnerAvatar);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 3001, intent, pendingFlags());
    }

    private PendingIntent buildServiceActionPendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MusicPlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, requestCode, intent, pendingFlags());
    }

    private int pendingFlags() {
        final int FLAG_IMMUTABLE = 1 << 26;
        if (Build.VERSION.SDK_INT >= 23) {
            return PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE;
        }
        return PendingIntent.FLAG_UPDATE_CURRENT;
    }

    private void startAsync(AsyncTask<Void, Void, File> task) {
        if (task == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 11) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
        } else {
            task.execute((Void[]) null);
        }
    }

    private boolean requestAudioFocusSafe() {
        if (audioManager == null) {
            return true;
        }
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocusSafe() {
        if (audioManager == null) {
            return;
        }
        try {
            audioManager.abandonAudioFocus(this);
        } catch (Exception e) {
        }
    }

    private String strings(String value) {
        return value == null ? "" : value;
    }

    private File ensureCachedMusicFile(String url) {
        if (url == null || url.length() == 0) {
            return null;
        }
        File cacheFile = resolveMusicCacheFile(url);
        if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 0) {
            try {
                cacheFile.setLastModified(System.currentTimeMillis());
            } catch (Exception e) {
            }
            return cacheFile;
        }

        String[] candidates = MediaUrlResolver.resolveCandidates(url);
        if (candidates == null || candidates.length == 0) {
            candidates = new String[]{url};
        }
        for (int i = 0; i < candidates.length; i++) {
            String one = candidates[i];
            if (one == null || one.length() == 0) {
                continue;
            }
            if (ensureCachedMusicFileOne(one, cacheFile)) {
                return cacheFile;
            }
        }
        return null;
    }

    private boolean ensureCachedMusicFileOne(String url, File cacheFile) {
        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream os = null;
        File tmp = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return false;
            }

            if (cacheFile == null) {
                return false;
            }
            File parent = cacheFile.getParentFile();
            if (parent == null) {
                return false;
            }
            if (!parent.exists()) {
                parent.mkdirs();
            }
            tmp = new File(cacheFile.getAbsolutePath() + ".tmp");

            is = conn.getInputStream();
            os = new FileOutputStream(tmp);
            byte[] buffer = new byte[8192];
            int len;
            long total = 0;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
                total += len;
                if (total > 80L * 1024L * 1024L) {
                    return false;
                }
            }
            os.flush();
            if (total <= 0) {
                return false;
            }
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
            if (!tmp.renameTo(cacheFile)) {
                return false;
            }
            try {
                cacheFile.setLastModified(System.currentTimeMillis());
            } catch (Exception e) {
            }
            return true;
        } catch (Exception e) {
            return false;
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
            if (tmp != null && (cacheFile == null || !cacheFile.exists() || tmp.length() != cacheFile.length())) {
                tmp.delete();
            }
        }
    }

    private File resolveMusicCacheFile(String url) {
        if (url == null || url.length() == 0) {
            return null;
        }
        File root = getFilesDir();
        if (root == null) {
            root = getCacheDir();
        }
        if (root == null) {
            return null;
        }
        File dir = new File(root, MUSIC_CACHE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String key = buildMusicCacheKey(url);
        String ext = guessMusicExt(url);
        return new File(dir, key + ext);
    }

    private String guessMusicExt(String url) {
        if (url == null) {
            return ".cache";
        }
        String lower = url.toLowerCase(Locale.US);
        int q = lower.indexOf('?');
        if (q >= 0) {
            lower = lower.substring(0, q);
        }
        int h = lower.indexOf('#');
        if (h >= 0) {
            lower = lower.substring(0, h);
        }
        String[] exts = new String[]{".mp3", ".m4a", ".aac", ".amr", ".3gp", ".wav", ".ogg", ".flac"};
        for (int i = 0; i < exts.length; i++) {
            String ext = exts[i];
            if (lower.endsWith(ext)) {
                return ext;
            }
        }
        return ".cache";
    }

    private String buildMusicCacheKey(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] out = digest.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (int i = 0; i < out.length; i++) {
                int v = out[i] & 0xFF;
                if (v < 16) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    private void warmMusicCacheAsync(final String url) {
        warmMusicCacheAsync(url, false);
    }

    private boolean shouldWarmCacheForPlayback(String url) {
        if (url == null || url.length() == 0) {
            return false;
        }
        File cacheFile = resolveMusicCacheFile(url);
        if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 0) {
            return false;
        }
        return isUnmeteredNetworkConnected();
    }

    private boolean isUnmeteredNetworkConnected() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            NetworkInfo info = cm.getActiveNetworkInfo();
            if (info == null || !info.isConnected()) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= 16) {
                try {
                    if (cm.isActiveNetworkMetered()) {
                        return false;
                    }
                } catch (Throwable ignored) {
                }
            }
            int type = info.getType();
            if (type == ConnectivityManager.TYPE_WIFI
                    || type == ConnectivityManager.TYPE_ETHERNET
                    || type == ConnectivityManager.TYPE_WIMAX) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void warmMusicCacheAsync(final String url, final boolean notifyResult) {
        if (url == null || url.length() == 0) {
            if (notifyResult) {
                dispatchCacheResult("", false, 0L, "歌曲链接不可用");
            }
            return;
        }
        synchronized (cacheWarmLock) {
            if (url.equals(warmingCacheUrl)) {
                if (notifyResult) {
                    dispatchCacheResult(url, false, 0L, "正在缓存中");
                }
                return;
            }
            warmingCacheUrl = url;
        }
        startAsync(new AsyncTask<Void, Void, File>() {
            @Override
            protected File doInBackground(Void... voids) {
                return ensureCachedMusicFile(url);
            }

            @Override
            protected void onPostExecute(File file) {
                synchronized (cacheWarmLock) {
                    if (url.equals(warmingCacheUrl)) {
                        warmingCacheUrl = "";
                    }
                }
                if (file != null && file.exists() && file.length() > 0) {
                    trimMusicCacheAsync();
                    if (notifyResult) {
                        dispatchCacheResult(url, true, file.length(), "");
                    }
                    return;
                }
                if (notifyResult) {
                    dispatchCacheResult(url, false, 0L, "缓存失败");
                }
            }
        });
    }

    private void dispatchCacheResult(String url, boolean ok, long size, String error) {
        Intent result = new Intent(ACTION_CACHE_RESULT);
        result.putExtra(EXTRA_CACHE_URL, strings(url));
        result.putExtra(EXTRA_CACHE_OK, ok);
        result.putExtra(EXTRA_CACHE_SIZE, Math.max(0L, size));
        result.putExtra(EXTRA_CACHE_ERROR, strings(error));
        if (localBroadcastManager != null) {
            localBroadcastManager.sendBroadcast(result);
        }
    }

    private void trimMusicCacheAsync() {
        if (isTrimmingCache) {
            return;
        }
        isTrimmingCache = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    trimMusicCache();
                } finally {
                    isTrimmingCache = false;
                }
            }
        }, "music-cache-trim-svc").start();
    }

    private void trimMusicCache() {
        File root = getFilesDir();
        if (root == null) {
            root = getCacheDir();
        }
        if (root == null) {
            return;
        }
        File dir = new File(root, MUSIC_CACHE_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }
        FileSortCompat.sortByLastModifiedAsc(files);

        long total = 0;
        int count = 0;
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file == null || !file.isFile()) {
                continue;
            }
            total += file.length();
            count++;
        }
        for (int i = 0; i < files.length && (total > MUSIC_CACHE_MAX_BYTES || count > MUSIC_CACHE_MAX_FILES); i++) {
            File file = files[i];
            if (file == null || !file.isFile()) {
                continue;
            }
            long len = file.length();
            if (file.delete()) {
                total -= len;
                count--;
            }
        }
    }
}
