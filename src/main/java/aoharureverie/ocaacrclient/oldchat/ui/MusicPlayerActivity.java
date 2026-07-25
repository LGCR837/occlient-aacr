package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewCompat;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.service.MusicPlaybackService;
import aoharureverie.ocaacrclient.oldchat.util.DownloadUtil;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;
import aoharureverie.ocaacrclient.oldchat.util.MediaUrlResolver;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MusicPlayerActivity extends BaseActivity {
    private static final Pattern LYRIC_TIME_PATTERN =
            Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?\\]");
    private static final int LYRICS_CACHE_LIMIT = 18;
    private static final LinkedHashMap<String, List<LyricLine>> LYRICS_CACHE =
            new LinkedHashMap<String, List<LyricLine>>(24, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<LyricLine>> eldest) {
                    return size() > LYRICS_CACHE_LIMIT;
                }
            };

    private ImageView ivCover;
    private TextView tvSongName;
    private TextView tvOwner;
    private TextView tvCurrent;
    private TextView tvDuration;
    private ImageView ivPlayPause;
    private ImageView ivStop;
    private ImageView ivFastForward;
    private ImageView ivRepeatOne;
    private TextView tvStatus;
    private TextView tvLyricsEmpty;
    private SeekBar seekBar;
    private TextView btnDownload;
    private ScrollView svLyrics;
    private LinearLayout layoutLyrics;

    private boolean isSeeking;
    private int lyricColorNormal;
    private int lyricColorActive;

    private String songName = "";
    private String songUrl = "";
    private String coverUrl = "";
    private String lyricsUrl = "";
    private String ownerName = "";
    private String ownerUid = "";
    private String ownerTitle = "";
    private String ownerAvatar = "";

    private boolean isPreparing;
    private boolean isPlaying;
    private boolean repeatOne;
    private int durationMs;
    private int positionMs;

    private final List<LyricLine> lyricLines = new ArrayList<LyricLine>();
    private final List<TextView> lyricLineViews = new ArrayList<TextView>();
    private int currentLyricIndex = -1;
    private AsyncTask<String, Void, String> lyricTask;
    private int lyricTaskToken;
    private String lastResolvedLyricsUrl = "";

    private final BroadcastReceiver playbackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            if (!MusicPlaybackService.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }
            applyPlaybackState(intent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_player);

        bindViews();
        readIntentData(getIntent());
        bindHeaderInfo();
        bindActions();

        startOrAttachPlayback();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) {
            return;
        }
        setIntent(intent);
        readIntentData(intent);
        bindHeaderInfo();
        startOrAttachPlayback();
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver(playbackReceiver,
                new IntentFilter(MusicPlaybackService.ACTION_STATE_CHANGED));
        requestPlaybackState();
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(playbackReceiver);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void bindViews() {
        ivCover = findViewByIdCompat(R.id.ivMusicPlayerCover);
        tvSongName = findViewByIdCompat(R.id.tvMusicPlayerSongName);
        tvOwner = findViewByIdCompat(R.id.tvMusicPlayerOwner);
        tvCurrent = findViewByIdCompat(R.id.tvMusicPlayerCurrent);
        tvDuration = findViewByIdCompat(R.id.tvMusicPlayerDuration);
        ivPlayPause = findViewByIdCompat(R.id.btnMusicPlayerPlayPause);
        ivStop = findViewByIdCompat(R.id.btnMusicPlayerStop);
        ivFastForward = findViewByIdCompat(R.id.btnMusicPlayerFastForward);
        ivRepeatOne = findViewByIdCompat(R.id.btnMusicPlayerRepeatOne);
        tvStatus = findViewByIdCompat(R.id.tvMusicPlayerStatus);
        seekBar = findViewByIdCompat(R.id.sbMusicPlayerProgress);
        btnDownload = findViewByIdCompat(R.id.btnMusicPlayerDownload);
    }

    private void readIntentData(Intent intent) {
        if (intent == null) {
            return;
        }
        songName = value(intent.getStringExtra(MusicPlaybackService.EXTRA_SONG_NAME));
        songUrl = value(intent.getStringExtra(MusicPlaybackService.EXTRA_SONG_URL));
        coverUrl = value(intent.getStringExtra(MusicPlaybackService.EXTRA_COVER_URL));
        lyricsUrl = "";
        ownerUid = value(intent.getStringExtra(MusicPlaybackService.EXTRA_OWNER_UID));
        ownerName = value(intent.getStringExtra(MusicPlaybackService.EXTRA_OWNER_NAME));
        ownerTitle = value(intent.getStringExtra(MusicPlaybackService.EXTRA_OWNER_TITLE));
        ownerAvatar = value(intent.getStringExtra(MusicPlaybackService.EXTRA_OWNER_AVATAR));
    }

    private void bindHeaderInfo() {
        View btnBack = findViewByIdCompat(R.id.btnMusicPlayerBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        String displayName = songName;
        if (displayName.length() == 0) {
            displayName = "未命名歌曲";
        }
        tvSongName.setText(displayName);

        String owner = ownerName;
        if (owner.length() == 0) {
            owner = ownerUid;
        }
        if (owner.length() == 0) {
            owner = "匿名用户";
        }
        if (!TextUtils.isEmpty(ownerTitle)) {
            tvOwner.setText(owner + " · " + ownerTitle);
        } else {
            tvOwner.setText(owner);
        }

        if (coverUrl.length() > 0) {
            ImageLoader.load(ivCover, coverUrl);
        } else if (ownerAvatar.length() > 0) {
            ImageLoader.load(ivCover, ownerAvatar);
        } else {
            ivCover.setImageResource(R.drawable.ic_avatar_placeholder);
        }

        tvCurrent.setText(formatDuration(0));
        tvDuration.setText(formatDuration(0));
        tvStatus.setText("准备播放");
        if (ivPlayPause != null) {
            ivPlayPause.setImageResource(android.R.drawable.ic_media_play);
        }
        updateRepeatUi();
    }

    private void bindActions() {
        if (ivPlayPause != null) {
            ivPlayPause.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendPlaybackAction(MusicPlaybackService.ACTION_TOGGLE_PLAY);
                }
            });
        }
        if (ivFastForward != null) {
            ivFastForward.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MusicPlayerActivity.this, MusicPlaybackService.class);
                    intent.setAction(MusicPlaybackService.ACTION_SEEK_RELATIVE);
                    intent.putExtra(MusicPlaybackService.EXTRA_SEEK_DELTA_MS, 10000);
                    startMusicService(intent);
                }
            });
        }
        if (ivRepeatOne != null) {
            ivRepeatOne.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendPlaybackAction(MusicPlaybackService.ACTION_TOGGLE_REPEAT_ONE);
                }
            });
        }
        if (ivStop != null) {
            ivStop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendPlaybackAction(MusicPlaybackService.ACTION_STOP);
                    finish();
                }
            });
        }
        if (btnDownload != null) {
            btnDownload.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    downloadCurrentSong();
                }
            });
        }

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCurrent.setText(formatDuration(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isSeeking = false;
                Intent intent = new Intent(MusicPlayerActivity.this, MusicPlaybackService.class);
                intent.setAction(MusicPlaybackService.ACTION_SEEK);
                intent.putExtra(MusicPlaybackService.EXTRA_POSITION_MS, seekBar.getProgress());
                startMusicService(intent);
            }
        });
    }

    private void downloadCurrentSong() {
        if (songUrl == null || songUrl.length() == 0) {
            Toast.makeText(this, "歌曲链接不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        String resolvedSongUrl = MediaUrlResolver.resolve(songUrl);
        if (resolvedSongUrl == null || resolvedSongUrl.length() == 0) {
            Toast.makeText(this, "歌曲链接不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "开始下载到本地", Toast.LENGTH_SHORT).show();
        DownloadUtil.saveUrlToDownloadsAsync(this,
                resolvedSongUrl,
                "oldchat_music_",
                ".mp3",
                new DownloadUtil.Callback() {
                    @Override
                    public void onResult(boolean success, String message, java.io.File file) {
                        Toast.makeText(MusicPlayerActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void startOrAttachPlayback() {
        String resolvedSongUrl = MediaUrlResolver.resolve(songUrl);
        if (resolvedSongUrl.length() == 0) {
            Toast.makeText(this, "歌曲链接不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent playIntent = new Intent(this, MusicPlaybackService.class);
        playIntent.setAction(MusicPlaybackService.ACTION_PLAY_SONG);
        playIntent.putExtra(MusicPlaybackService.EXTRA_SONG_NAME, songName);
        playIntent.putExtra(MusicPlaybackService.EXTRA_SONG_URL, resolvedSongUrl);
        playIntent.putExtra(MusicPlaybackService.EXTRA_COVER_URL, coverUrl);
        playIntent.putExtra(MusicPlaybackService.EXTRA_OWNER_UID, ownerUid);
        playIntent.putExtra(MusicPlaybackService.EXTRA_OWNER_NAME, ownerName);
        playIntent.putExtra(MusicPlaybackService.EXTRA_OWNER_TITLE, ownerTitle);
        playIntent.putExtra(MusicPlaybackService.EXTRA_OWNER_AVATAR, ownerAvatar);
        startMusicService(playIntent);
    }

    private void requestPlaybackState() {
        Intent intent = new Intent(this, MusicPlaybackService.class);
        intent.setAction(MusicPlaybackService.ACTION_REQUEST_STATE);
        startMusicService(intent);
    }

    private void sendPlaybackAction(String action) {
        Intent intent = new Intent(this, MusicPlaybackService.class);
        intent.setAction(action);
        startMusicService(intent);
    }

    private void startMusicService(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = value(intent.getAction());
        boolean requireForegroundStart = MusicPlaybackService.ACTION_PLAY_SONG.equals(action);
        try {
            if (Build.VERSION.SDK_INT >= 26 && requireForegroundStart) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            try {
                startService(intent);
            } catch (Exception ignored) {
            }
        }
    }

    private void applyPlaybackState(Intent stateIntent) {
        String newSongUrl = value(stateIntent.getStringExtra(MusicPlaybackService.EXTRA_SONG_URL));
        if (newSongUrl.length() > 0 && !TextUtils.equals(newSongUrl, songUrl)) {
            songUrl = newSongUrl;
            songName = value(stateIntent.getStringExtra(MusicPlaybackService.EXTRA_SONG_NAME));
            coverUrl = value(stateIntent.getStringExtra(MusicPlaybackService.EXTRA_COVER_URL));
            ownerUid = value(stateIntent.getStringExtra(MusicPlaybackService.EXTRA_OWNER_UID));
            ownerName = value(stateIntent.getStringExtra(MusicPlaybackService.EXTRA_OWNER_NAME));
            ownerTitle = value(stateIntent.getStringExtra(MusicPlaybackService.EXTRA_OWNER_TITLE));
            ownerAvatar = value(stateIntent.getStringExtra(MusicPlaybackService.EXTRA_OWNER_AVATAR));
            bindHeaderInfo();
        }

        isPreparing = stateIntent.getBooleanExtra(MusicPlaybackService.EXTRA_IS_PREPARING, false);
        isPlaying = stateIntent.getBooleanExtra(MusicPlaybackService.EXTRA_IS_PLAYING, false);
        repeatOne = stateIntent.getBooleanExtra(MusicPlaybackService.EXTRA_REPEAT_ONE, false);
        durationMs = Math.max(0, stateIntent.getIntExtra(MusicPlaybackService.EXTRA_DURATION_MS, 0));
        positionMs = Math.max(0, stateIntent.getIntExtra(MusicPlaybackService.EXTRA_POSITION_MS, 0));
        String error = value(stateIntent.getStringExtra(MusicPlaybackService.EXTRA_ERROR));
        updateRepeatUi();

        if (!isSeeking) {
            seekBar.setMax(Math.max(durationMs, 1));
            seekBar.setProgress(Math.min(positionMs, Math.max(durationMs, 0)));
        }
        tvCurrent.setText(formatDuration(positionMs));
        tvDuration.setText(formatDuration(durationMs));

        if (isPreparing) {
            tvStatus.setText("加载中...（可后台播放）");
            if (ivPlayPause != null) {
                ivPlayPause.setImageResource(android.R.drawable.ic_popup_sync);
                ivPlayPause.setEnabled(false);
            }
            return;
        }

        if (ivPlayPause != null) {
            ivPlayPause.setEnabled(true);
        }
        if (error.length() > 0) {
            tvStatus.setText(error);
            if (ivPlayPause != null) {
                ivPlayPause.setImageResource(android.R.drawable.ic_media_play);
            }
            return;
        }
        if (isPlaying) {
            tvStatus.setText("正在播放（后台持续）");
            if (ivPlayPause != null) {
                ivPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            }
            return;
        }
        if (durationMs > 0 && positionMs >= durationMs - 400) {
            tvStatus.setText("播放完成");
            if (ivPlayPause != null) {
                ivPlayPause.setImageResource(android.R.drawable.ic_media_play);
            }
            return;
        }
        if (durationMs > 0 || positionMs > 0) {
            tvStatus.setText("已暂停（通知栏可继续）");
        } else {
            tvStatus.setText("准备播放");
        }
        if (ivPlayPause != null) {
            ivPlayPause.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    private void updateRepeatUi() {
        if (ivRepeatOne == null) {
            return;
        }
        ViewCompat.setAlpha(ivRepeatOne, repeatOne ? 1.0f : 0.55f);
    }

    private void loadLyrics() {
        String resolvedLyricsUrl = MediaUrlResolver.resolve(lyricsUrl);
        lastResolvedLyricsUrl = resolvedLyricsUrl;
        if (resolvedLyricsUrl.length() == 0) {
            lyricTaskToken++;
            cancelLyricsTask();
            showLyricsEmpty("该歌曲暂无歌词");
            return;
        }

        List<LyricLine> cached = getCachedLyrics(resolvedLyricsUrl);
        if (cached != null) {
            lyricTaskToken++;
            cancelLyricsTask();
            renderLyrics(cached);
            return;
        }

        showLyricsEmpty("歌词加载中...");
        lyricTaskToken++;
        final int token = lyricTaskToken;
        cancelLyricsTask();
        lyricTask = new AsyncTask<String, Void, String>() {
            @Override
            protected String doInBackground(String... params) {
                if (params == null || params.length == 0) {
                    return null;
                }
                if (isCancelled()) {
                    return null;
                }
                return downloadText(params[0]);
            }

            @Override
            protected void onPostExecute(String content) {
                if (token != lyricTaskToken) {
                    return;
                }
                lyricTask = null;
                if (content == null || content.length() == 0) {
                    showLyricsEmpty("歌词加载失败");
                    return;
                }
                List<LyricLine> parsed = parseLrc(content);
                cacheLyrics(lastResolvedLyricsUrl, parsed);
                renderLyrics(parsed);
            }
        };
        lyricTask.execute(resolvedLyricsUrl);
    }

    private void cancelLyricsTask() {
        if (lyricTask == null) {
            return;
        }
        try {
            lyricTask.cancel(true);
        } catch (Exception e) {
        }
        lyricTask = null;
    }

    private List<LyricLine> getCachedLyrics(String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        synchronized (LYRICS_CACHE) {
            List<LyricLine> cached = LYRICS_CACHE.get(url);
            if (cached == null) {
                return null;
            }
            return new ArrayList<LyricLine>(cached);
        }
    }

    private void cacheLyrics(String url, List<LyricLine> lines) {
        if (TextUtils.isEmpty(url) || lines == null) {
            return;
        }
        synchronized (LYRICS_CACHE) {
            LYRICS_CACHE.put(url, new ArrayList<LyricLine>(lines));
        }
    }

    private String downloadText(String url) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        ByteArrayOutputStream bos = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setUseCaches(false);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }
            inputStream = connection.getInputStream();
            bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            String text = new String(bos.toByteArray(), "UTF-8");
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            return text;
        } catch (Exception e) {
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                }
            }
            if (bos != null) {
                try {
                    bos.close();
                } catch (Exception e) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private List<LyricLine> parseLrc(String content) {
        List<LyricLine> result = new ArrayList<LyricLine>();
        if (content == null || content.length() == 0) {
            return result;
        }

        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        long fallbackTime = 0;
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i] == null ? "" : lines[i].trim();
            if (raw.length() == 0) {
                continue;
            }

            Matcher matcher = LYRIC_TIME_PATTERN.matcher(raw);
            List<Long> times = new ArrayList<Long>();
            while (matcher.find()) {
                long ms = toMillis(matcher.group(1), matcher.group(2), matcher.group(3));
                if (ms >= 0) {
                    times.add(ms);
                }
            }

            String text = raw.replaceAll("\\[[^\\]]*\\]", "").trim();
            if (!times.isEmpty()) {
                if (text.length() == 0) {
                    text = "♪";
                }
                for (int j = 0; j < times.size(); j++) {
                    result.add(new LyricLine(times.get(j), text));
                }
                continue;
            }

            if (raw.startsWith("[") && raw.endsWith("]")) {
                continue;
            }
            if (text.length() > 0) {
                result.add(new LyricLine(fallbackTime, text));
                fallbackTime += 3000;
            }
        }

        Collections.sort(result, new Comparator<LyricLine>() {
            @Override
            public int compare(LyricLine o1, LyricLine o2) {
                if (o1.timeMs == o2.timeMs) {
                    return 0;
                }
                return o1.timeMs < o2.timeMs ? -1 : 1;
            }
        });
        return result;
    }

    private long toMillis(String min, String sec, String fraction) {
        try {
            int m = Integer.parseInt(min);
            int s = Integer.parseInt(sec);
            int ms = 0;
            if (fraction != null && fraction.length() > 0) {
                if (fraction.length() == 1) {
                    ms = Integer.parseInt(fraction) * 100;
                } else if (fraction.length() == 2) {
                    ms = Integer.parseInt(fraction) * 10;
                } else {
                    ms = Integer.parseInt(fraction.substring(0, 3));
                }
            }
            return m * 60L * 1000L + s * 1000L + ms;
        } catch (Exception e) {
            return -1;
        }
    }

    private void renderLyrics(List<LyricLine> lines) {
        lyricLines.clear();
        lyricLineViews.clear();
        layoutLyrics.removeAllViews();
        currentLyricIndex = -1;

        if (lines == null || lines.isEmpty()) {
            showLyricsEmpty("暂无滚动歌词");
            return;
        }

        lyricLines.addAll(lines);
        tvLyricsEmpty.setVisibility(View.GONE);

        addLyricSpacer(80);
        for (int i = 0; i < lyricLines.size(); i++) {
            TextView lineView = new TextView(this);
            lineView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            lineView.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
            lineView.setGravity(Gravity.CENTER_HORIZONTAL);
            lineView.setText(lyricLines.get(i).text);
            lineView.setTextSize(16f);
            lineView.setTextColor(lyricColorNormal);
            ViewCompat.setAlpha(lineView, 0.85f);
            layoutLyrics.addView(lineView);
            lyricLineViews.add(lineView);
        }
        addLyricSpacer(120);
        highlightLyricByPosition(0);
    }

    private void addLyricSpacer(int heightDp) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(heightDp)
        ));
        layoutLyrics.addView(spacer);
    }

    private void showLyricsEmpty(String text) {
        lyricLines.clear();
        lyricLineViews.clear();
        layoutLyrics.removeAllViews();
        tvLyricsEmpty.setText(text);
        tvLyricsEmpty.setVisibility(View.VISIBLE);
    }

    private void highlightLyricByPosition(int positionMs) {
        if (lyricLines.isEmpty()) {
            return;
        }

        int targetIndex = findLyricIndex(positionMs);
        if (targetIndex == currentLyricIndex) {
            return;
        }

        if (currentLyricIndex >= 0 && currentLyricIndex < lyricLineViews.size()) {
            TextView oldLine = lyricLineViews.get(currentLyricIndex);
            oldLine.setTextColor(lyricColorNormal);
            oldLine.setTextSize(16f);
            oldLine.setTypeface(Typeface.DEFAULT);
            ViewCompat.setAlpha(oldLine, 0.85f);
        }

        if (targetIndex >= 0 && targetIndex < lyricLineViews.size()) {
            TextView activeLine = lyricLineViews.get(targetIndex);
            activeLine.setTextColor(lyricColorActive);
            activeLine.setTextSize(20f);
            activeLine.setTypeface(Typeface.DEFAULT_BOLD);
            ViewCompat.setAlpha(activeLine, 1.0f);
            scrollToLyricLine(activeLine);
        }

        currentLyricIndex = targetIndex;
    }

    private int findLyricIndex(int positionMs) {
        int left = 0;
        int right = lyricLines.size() - 1;
        int answer = -1;
        while (left <= right) {
            int mid = (left + right) / 2;
            long t = lyricLines.get(mid).timeMs;
            if (t <= positionMs) {
                answer = mid;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        return answer;
    }

    private void scrollToLyricLine(final TextView lineView) {
        if (lineView == null) {
            return;
        }
        svLyrics.post(new Runnable() {
            @Override
            public void run() {
                int targetY = lineView.getTop() - (svLyrics.getHeight() - lineView.getHeight()) / 2;
                if (targetY < 0) {
                    targetY = 0;
                }
                int currentY = svLyrics.getScrollY();
                if (Math.abs(targetY - currentY) < dpToPx(20)) {
                    return;
                }
                svLyrics.smoothScrollTo(0, targetY);
            }
        });
    }

    private String formatDuration(int durationMs) {
        if (durationMs <= 0) {
            return "00:00";
        }
        int totalSeconds = durationMs / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private String value(String raw) {
        return raw == null ? "" : raw;
    }

    private static class LyricLine {
        final long timeMs;
        final String text;

        LyricLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text == null ? "" : text;
        }
    }
}

