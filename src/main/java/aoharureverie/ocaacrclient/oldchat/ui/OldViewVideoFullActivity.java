package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.FileProvider;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.util.MediaUrlResolver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class OldViewVideoFullActivity extends BaseActivity {
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_RESULT_POSITION = "result_position";
    public static final String EXTRA_COMPLETED = "completed";

    private VideoView vvPlayer;
    private View layoutLoading;
    private TextView tvLoadingHint;
    private int startPosition = 0;
    private int lastPosition = 0;
    private boolean completed = false;
    private String videoUrl;
    private boolean triedLocal = false;
    private boolean playingLocal = false;
    private File cachedFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_old_view_fullscreen);

        vvPlayer = (VideoView) findViewByIdCompat(R.id.vvOldViewFullPlayer);
        layoutLoading = (View) findViewByIdCompat(R.id.layoutOldViewFullLoading);
        tvLoadingHint = (TextView) findViewByIdCompat(R.id.tvOldViewFullHint);

        TextView tvTitle = (TextView) findViewByIdCompat(R.id.tvOldViewFullTitle);
        View btnBack = (View) findViewByIdCompat(R.id.btnOldViewFullBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finishWithResult();
                }
            });
        }

        Intent intent = getIntent();
        String url = intent != null ? intent.getStringExtra(EXTRA_URL) : null;
        String title = intent != null ? intent.getStringExtra(EXTRA_TITLE) : null;
        startPosition = intent != null ? intent.getIntExtra(EXTRA_POSITION, 0) : 0;
        if (tvTitle != null) {
            tvTitle.setText(title != null ? title : "");
        }
        if (url == null || url.length() == 0) {
            Toast.makeText(this, "无法获取播放地址", Toast.LENGTH_SHORT).show();
            finishWithResult();
            return;
        }
        videoUrl = MediaUrlResolver.resolve(url);
        if (videoUrl == null || videoUrl.length() == 0) {
            Toast.makeText(this, "无法获取播放地址", Toast.LENGTH_SHORT).show();
            finishWithResult();
            return;
        }

        MediaController controller = new MediaController(this);
        controller.setAnchorView(vvPlayer);
        vvPlayer.setMediaController(controller);
        bindPlayerListeners();
        playUri(Uri.parse(videoUrl), "视频加载中...");
    }

    private void bindPlayerListeners() {
        if (vvPlayer == null) {
            return;
        }
        vvPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                showLoading(false, null);
                if (startPosition > 0) {
                    try {
                        vvPlayer.seekTo(startPosition);
                    } catch (Exception e) {
                    }
                }
                startPosition = 0;
                try {
                    vvPlayer.start();
                } catch (Exception e) {
                    showLoading(false, null);
                    openSystemPlayer();
                }
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            vvPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer mp, int what, int extra) {
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                        showLoading(true, "缓冲中...");
                    } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END
                            || what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        showLoading(false, null);
                    }
                    return false;
                }
            });
        }

        vvPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                completed = true;
                finishWithResult();
            }
        });

        vvPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                lastPosition = safeGetPosition();
                showLoading(false, null);
                if (!triedLocal && !playingLocal && videoUrl != null && videoUrl.startsWith("http")) {
                    triedLocal = true;
                    tryCachePlayback();
                    return true;
                }
                openSystemPlayer();
                return true;
            }
        });
    }

    private void playUri(Uri uri, String hint) {
        if (vvPlayer == null || uri == null) {
            return;
        }
        showLoading(true, hint);
        try {
            vvPlayer.stopPlayback();
        } catch (Exception e) {
        }
        vvPlayer.setVideoURI(uri);
        vvPlayer.requestFocus();
    }

    private void showLoading(boolean show, String hint) {
        if (layoutLoading != null) {
            layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (show && tvLoadingHint != null && hint != null && hint.length() > 0) {
            tvLoadingHint.setText(hint);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (vvPlayer != null && vvPlayer.isPlaying()) {
                vvPlayer.pause();
            }
            lastPosition = safeGetPosition();
        } catch (Exception e) {
        }
    }

    @Override
    protected void onDestroy() {
        if (vvPlayer != null) {
            try {
                vvPlayer.stopPlayback();
            } catch (Exception e) {
            }
        }
        if (cachedFile != null && cachedFile.exists()) {
            cachedFile.delete();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finishWithResult();
    }

    private int safeGetPosition() {
        try {
            if (vvPlayer != null) {
                return Math.max(0, vvPlayer.getCurrentPosition());
            }
        } catch (Exception e) {
        }
        return 0;
    }

    private void finishWithResult() {
        if (lastPosition <= 0) {
            lastPosition = safeGetPosition();
        }
        Intent data = new Intent();
        data.putExtra(EXTRA_RESULT_POSITION, lastPosition);
        data.putExtra(EXTRA_COMPLETED, completed);
        setResult(RESULT_OK, data);
        finish();
    }

    private void tryCachePlayback() {
        showLoading(true, "网络波动，尝试缓存播放...");
        final String targetUrl = videoUrl;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final File file = downloadToCache(targetUrl);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (file == null || !file.exists()) {
                            showLoading(false, null);
                            openSystemPlayer();
                            return;
                        }
                        cachedFile = file;
                        playingLocal = true;
                        if (lastPosition > 0) {
                            startPosition = lastPosition;
                        }
                        playUri(Uri.fromFile(file), "正在加载本地缓存...");
                    }
                });
            }
        }).start();
    }

    private File downloadToCache(String url) {
        if (url == null || url.length() == 0) {
            return null;
        }
        File dir = new File(getCacheDir(), "video_play_cache");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String ext = guessExtension(url);
        File out = new File(dir, "video_" + System.currentTimeMillis() + ext);
        InputStream is = null;
        FileOutputStream os = null;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();
            if (conn.getResponseCode() != 200) {
                return null;
            }
            is = conn.getInputStream();
            os = new FileOutputStream(out);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            os.flush();
            return out;
        } catch (Exception e) {
            if (out.exists()) {
                out.delete();
            }
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
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
        }
    }

    private String guessExtension(String url) {
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
                if (ext.length() <= 5) {
                    return ext;
                }
            }
        }
        return ".mp4";
    }

    private void openSystemPlayer() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri targetUri;
            if (playingLocal && cachedFile != null && cachedFile.exists()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    targetUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cachedFile);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    targetUri = Uri.fromFile(cachedFile);
                }
            } else if (videoUrl != null && videoUrl.length() > 0) {
                targetUri = Uri.parse(videoUrl);
            } else {
                Toast.makeText(this, "播放失败", Toast.LENGTH_SHORT).show();
                finishWithResult();
                return;
            }
            intent.setDataAndType(targetUri, "video/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "播放失败", Toast.LENGTH_SHORT).show();
        }
        finishWithResult();
    }
}
