package aoharureverie.ocaacrclient.oldchat.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Looper;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.service.MusicPlaybackService;
import aoharureverie.ocaacrclient.oldchat.util.DownloadUtil;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;
import aoharureverie.ocaacrclient.oldchat.util.MediaUrlResolver;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Iterator;

public class MusicPlazaActivity extends BaseActivity {
    private static final int REQ_PICK_SONG = 4301;
    private static final int REQ_PICK_COVER = 4302;
    private static final int REQ_PICK_MEDIA_PERMISSION = 4303;
    private static final int PICK_KIND_NONE = 0;
    private static final int PICK_KIND_SONG = 1;
    private static final int PICK_KIND_COVER = 2;
    private static final int PAGE_SIZE = 50;
    private static final long MAX_SONG_BYTES = 20L * 1024L * 1024L;
    private static final long MAX_COVER_BYTES = 1L * 1024L * 1024L;
    private static final String PREF_MUSIC_LOCAL = "music_plaza_local";
    private static final String KEY_RECENT_PLAY = "recent_play";
    private static final int MAX_RECENT_PLAY = 30;

    private EditText etKeyword;
    private TextView btnSearch;
    private TextView btnNowPlaying;
    private TextView btnDownloads;
    private TextView btnUpload;
    private TextView btnFilterAll;
    private TextView btnFilterMine;
    private TextView btnMineManage;
    private View layoutFeatured;
    private ImageView ivFeaturedCover;
    private TextView tvFeaturedTitle;
    private TextView tvFeaturedSub;
    private TextView btnFeaturedPlay;
    private TextView tvEmpty;
    private TextView tvSummary;
    private android.widget.LinearLayout layoutRecommendRows;
    private android.widget.LinearLayout layoutRankingSongs;
    private android.widget.LinearLayout layoutAllSongs;
    private TextView tvRecommendEmpty;
    private TextView tvRankingEmpty;
    private TextView tvAllEmpty;
    private TextView btnOpenAllList;
    private TextView tvPageInfo;
    private TextView btnPrev;
    private TextView btnNext;
    private ListView lv;

    private String token;
    private String myUid;
    private final List<MusicItem> items = new ArrayList<MusicItem>();
    private final List<MusicItem> recommendItems = new ArrayList<MusicItem>();
    private final List<MusicItem> rankingItems = new ArrayList<MusicItem>();
    private MusicAdapter adapter;
    private int currentOffset = 0;
    private int totalCount = 0;
    private boolean hasMore = false;
    private boolean showMineOnly = false;

    private Uri pendingSongUri;
    private Uri pendingCoverUri;
    private String pendingName;
    private int pendingPickKind = PICK_KIND_NONE;
    private AlertDialog uploadDialog;
    private TextView uploadSongTextView;
    private TextView uploadCoverTextView;

    private MediaPlayer mediaPlayer;
    private String currentPlayUrl;
    private String currentPlayingSongName = "";
    private String currentPlayingSongUrl = "";
    private String currentPlayingCoverUrl = "";
    private String currentPlayingOwnerUid = "";
    private String currentPlayingOwnerName = "";
    private String currentPlayingOwnerTitle = "";
    private String currentPlayingOwnerAvatar = "";
    private boolean currentPlayingActive = false;
    private boolean homeSectionDirty = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable homeSectionRefreshTask;

    private final Object durationProbeLock = new Object();
    private final HashMap<String, Integer> durationProbeCache = new HashMap<String, Integer>();
    private final HashSet<String> durationProbeRunning = new HashSet<String>();

    private final HashSet<String> cacheDownloadingUrls = new HashSet<String>();
    private final BroadcastReceiver musicCacheReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleMusicCacheResult(intent);
        }
    };
    private final BroadcastReceiver playbackStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handlePlaybackState(intent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_plaza);

        SharedPreferences prefs = getSharedPreferences("auth", Context.MODE_PRIVATE);
        token = prefs.getString("access_token", "");
        myUid = prefs.getString("my_uid", "");

        etKeyword = findViewByIdCompat(R.id.etMusicPlazaKeyword);
        btnSearch = findViewByIdCompat(R.id.btnMusicPlazaSearch);
        btnNowPlaying = findViewByIdCompat(R.id.btnMusicPlazaNowPlaying);
        btnDownloads = findViewByIdCompat(R.id.btnMusicPlazaDownloads);
        btnUpload = findViewByIdCompat(R.id.btnMusicPlazaUpload);
        btnFilterAll = findViewByIdCompat(R.id.btnMusicPlazaFilterAll);
        btnFilterMine = findViewByIdCompat(R.id.btnMusicPlazaFilterMine);
        btnMineManage = findViewByIdCompat(R.id.btnMusicPlazaMineManage);
        layoutFeatured = findViewByIdCompat(R.id.layoutMusicPlazaHero);
        ivFeaturedCover = findViewByIdCompat(R.id.ivMusicPlazaFeaturedCover);
        tvFeaturedTitle = findViewByIdCompat(R.id.tvMusicPlazaFeaturedTitle);
        tvFeaturedSub = findViewByIdCompat(R.id.tvMusicPlazaFeaturedSub);
        btnFeaturedPlay = findViewByIdCompat(R.id.btnMusicPlazaFeaturedPlay);
        tvEmpty = findViewByIdCompat(R.id.tvMusicPlazaEmpty);
        tvSummary = findViewByIdCompat(R.id.tvMusicPlazaSummary);
        layoutRecommendRows = findViewByIdCompat(R.id.layoutMusicPlazaRecommendRows);
        layoutRankingSongs = findViewByIdCompat(R.id.layoutMusicPlazaRankingSongs);
        layoutAllSongs = findViewByIdCompat(R.id.layoutMusicPlazaAllSongs);
        tvRecommendEmpty = findViewByIdCompat(R.id.tvMusicPlazaRecommendEmpty);
        tvRankingEmpty = findViewByIdCompat(R.id.tvMusicPlazaRankingEmpty);
        tvAllEmpty = findViewByIdCompat(R.id.tvMusicPlazaAllEmpty);
        btnOpenAllList = findViewByIdCompat(R.id.btnMusicPlazaOpenAllList);
        tvPageInfo = findViewByIdCompat(R.id.tvMusicPlazaPageInfo);
        btnPrev = findViewByIdCompat(R.id.btnMusicPlazaPrev);
        btnNext = findViewByIdCompat(R.id.btnMusicPlazaNext);
        lv = findViewByIdCompat(R.id.lvMusicPlaza);

        View btnBack = findViewByIdCompat(R.id.btnMusicPlazaBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        adapter = new MusicAdapter();
        lv.setAdapter(adapter);
        lv.setEmptyView(tvEmpty);
        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= items.size()) {
                    return;
                }
                playItem(items.get(position));
            }
        });
        lv.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= items.size()) {
                    return false;
                }
                MusicItem item = items.get(position);
                if (item == null) {
                    return false;
                }
                showItemActions(item);
                return true;
            }
        });

        if (btnSearch != null) {
            btnSearch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentOffset = 0;
                    loadItems();
                }
            });
        }
        if (btnUpload != null) {
            btnUpload.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showMineMenu();
                }
            });
        }
        if (btnNowPlaying != null) {
            btnNowPlaying.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openNowPlayingPage();
                }
            });
        }
        if (btnDownloads != null) {
            btnDownloads.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(MusicPlazaActivity.this, MusicDownloadsActivity.class));
                }
            });
        }
        if (btnOpenAllList != null) {
            btnOpenAllList.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MusicPlazaActivity.this, MusicManageActivity.class);
                    intent.putExtra("all_list_mode", true);
                    startActivity(intent);
                }
            });
        }
        if (btnFilterAll != null) {
            btnFilterAll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setShowMineOnly(false, true);
                }
            });
        }
        if (btnFilterMine != null) {
            btnFilterMine.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setShowMineOnly(true, true);
                }
            });
        }
        if (btnMineManage != null) {
            btnMineManage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MusicPlazaActivity.this, MusicManageActivity.class);
                    startActivity(intent);
                }
            });
        }
        if (etKeyword != null) {
            etKeyword.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                        currentOffset = 0;
                        loadItems();
                        return true;
                    }
                    if (event != null && event.getAction() == KeyEvent.ACTION_DOWN
                            && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                        currentOffset = 0;
                        loadItems();
                        return true;
                    }
                    return false;
                }
            });
        }

        if (btnPrev != null) {
            btnPrev.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (currentOffset <= 0) {
                        return;
                    }
                    currentOffset = Math.max(0, currentOffset - PAGE_SIZE);
                    loadItems();
                }
            });
        }
        if (btnNext != null) {
            btnNext.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!hasMore) {
                        return;
                    }
                    currentOffset += PAGE_SIZE;
                    loadItems();
                }
            });
        }
        if (btnFeaturedPlay != null) {
            btnFeaturedPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (items.isEmpty()) {
                        return;
                    }
                    playItem(items.get(0));
                }
            });
        }

        applyFilterUi();
        updateNowPlayingButton();
        loadItems();
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.registerReceiver(musicCacheReceiver,
                new IntentFilter(MusicPlaybackService.ACTION_CACHE_RESULT));
        manager.registerReceiver(playbackStateReceiver,
                new IntentFilter(MusicPlaybackService.ACTION_STATE_CHANGED));
        requestPlaybackState();
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        try {
            manager.unregisterReceiver(musicCacheReceiver);
        } catch (Exception ignore) {
        }
        try {
            manager.unregisterReceiver(playbackStateReceiver);
        } catch (Exception ignore) {
        }
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPlay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (homeSectionDirty) {
            homeSectionDirty = false;
            updateHomeSections();
        }
    }

    @Override
    protected void onDestroy() {
        if (homeSectionRefreshTask != null) {
            mainHandler.removeCallbacks(homeSectionRefreshTask);
        }
        stopPlay();
        super.onDestroy();
    }

    private void setShowMineOnly(boolean mineOnly, boolean reload) {
        if (showMineOnly == mineOnly) {
            if (reload) {
                currentOffset = 0;
                loadItems();
            }
            return;
        }
        showMineOnly = mineOnly;
        applyFilterUi();
        if (reload) {
            currentOffset = 0;
            loadItems();
        }
    }

    private void applyFilterUi() {
        if (btnFilterAll != null) {
            btnFilterAll.setBackgroundResource(showMineOnly ? R.drawable.bg_music_plaza_tag_neutral : R.drawable.bg_music_plaza_tag_positive);
            btnFilterAll.setTextColor(getResources().getColor(showMineOnly ? R.color.color_text_secondary : R.color.color_text_primary));
            btnFilterAll.setTextSize(showMineOnly ? 12f : 12.5f);
        }
        if (btnFilterMine != null) {
            btnFilterMine.setBackgroundResource(showMineOnly ? R.drawable.bg_music_plaza_tag_positive : R.drawable.bg_music_plaza_tag_neutral);
            btnFilterMine.setTextColor(getResources().getColor(showMineOnly ? R.color.color_text_primary : R.color.color_text_secondary));
            btnFilterMine.setTextSize(showMineOnly ? 12.5f : 12f);
        }
        if (btnMineManage != null) {
            btnMineManage.setText("管理上传");
        }
        if (tvEmpty != null) {
            tvEmpty.setText(showMineOnly ? "你还没有上传歌曲，点右上角上传吧" : "暂无歌曲，去右上角上传你的第一首吧");
        }
        if (btnUpload != null) {
            btnUpload.setText("我的");
        }
    }

    private void showMineMenu() {
        final String[] actions = new String[]{"最近听过", "我的发布", "上传音乐"};
        new AlertDialog.Builder(this)
                .setTitle("我的")
                .setItems(actions, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == 0) {
                            showRecentPlayedDialog();
                            return;
                        }
                        if (which == 1) {
                            Intent intent = new Intent(MusicPlazaActivity.this, MusicManageActivity.class);
                            startActivity(intent);
                            return;
                        }
                        if (which == 2) {
                            showUploadDialog();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRecentPlayedDialog() {
        final List<String> entries = getRecentPlayEntries();
        if (entries.isEmpty()) {
            Toast.makeText(this, "暂无最近听过", Toast.LENGTH_SHORT).show();
            return;
        }
        final CharSequence[] labels = new CharSequence[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            labels[i] = decodeRecentLabel(entries.get(i));
        }
        new AlertDialog.Builder(this)
                .setTitle("最近听过")
                .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which < 0 || which >= entries.size()) {
                            return;
                        }
                        String raw = entries.get(which);
                        MusicItem item = parseRecentEntry(raw);
                        if (item == null || item.songUrl == null || item.songUrl.length() == 0) {
                            return;
                        }
                        playItem(item);
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void updateFeaturedCard() {
        if (layoutFeatured == null) {
            return;
        }
        if (items.isEmpty()) {
            layoutFeatured.setVisibility(View.GONE);
            return;
        }
        final MusicItem featured = items.get(0);
        if (featured == null) {
            layoutFeatured.setVisibility(View.GONE);
            return;
        }
        layoutFeatured.setVisibility(View.VISIBLE);
        if (tvFeaturedTitle != null) {
            String title = featured.name == null || featured.name.length() == 0 ? "精选歌曲" : featured.name;
            tvFeaturedTitle.setText(title);
        }
        if (tvFeaturedSub != null) {
            String owner = featured.ownerName;
            if (owner == null || owner.length() == 0) {
                owner = featured.ownerUid;
            }
            if (owner == null || owner.length() == 0) {
                owner = "来自音乐广场";
            } else {
                owner = "推荐上传者 · " + owner;
            }
            tvFeaturedSub.setText(owner);
        }
        if (ivFeaturedCover != null) {
            if (featured.coverUrl != null && featured.coverUrl.length() > 0) {
                ImageLoader.load(ivFeaturedCover, featured.coverUrl);
            } else if (featured.ownerAvatar != null && featured.ownerAvatar.length() > 0) {
                ImageLoader.load(ivFeaturedCover, featured.ownerAvatar);
            } else {
                ivFeaturedCover.setImageResource(R.drawable.ic_avatar_placeholder);
            }
        }
    }

    private void rebuildRecommendItems() {
        recommendItems.clear();
        if (items.isEmpty()) {
            return;
        }
        ArrayList<MusicItem> pool = new ArrayList<MusicItem>(items);
        Collections.shuffle(pool);
        int max = Math.min(pool.size(), 12);
        for (int i = 0; i < max; i++) {
            recommendItems.add(pool.get(i));
        }
    }

    private void updateHomeSections() {
        renderRecommendGrid();
        renderRankingRow();
        renderAllSongsRow();
    }

    private void scheduleHomeSectionsRefresh() {
        if (isFinishing()) {
            return;
        }
        homeSectionDirty = true;
        if (homeSectionRefreshTask == null) {
            homeSectionRefreshTask = new Runnable() {
                @Override
                public void run() {
                    if (isFinishing()) {
                        return;
                    }
                    homeSectionDirty = false;
                    updateHomeSections();
                }
            };
        }
        mainHandler.removeCallbacks(homeSectionRefreshTask);
        mainHandler.postDelayed(homeSectionRefreshTask, 220);
    }

    private void renderRecommendGrid() {
        if (layoutRecommendRows == null) {
            return;
        }
        layoutRecommendRows.removeAllViews();
        List<MusicItem> source = recommendItems.isEmpty() ? items : recommendItems;
        int count = Math.min(source.size(), 4);
        if (count <= 0) {
            if (tvRecommendEmpty != null) {
                tvRecommendEmpty.setVisibility(View.VISIBLE);
            }
            return;
        }
        if (tvRecommendEmpty != null) {
            tvRecommendEmpty.setVisibility(View.GONE);
        }
        int index = 0;
        while (index < count) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            View left = buildHomeSongCard(source.get(index), true);
            ensureDurationResolved(source.get(index));
            android.widget.LinearLayout.LayoutParams leftLp = new android.widget.LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            );
            leftLp.rightMargin = dpToPx(5);
            row.addView(left, leftLp);
            index++;

            if (index < count) {
                View right = buildHomeSongCard(source.get(index), true);
                ensureDurationResolved(source.get(index));
                android.widget.LinearLayout.LayoutParams rightLp = new android.widget.LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );
                rightLp.leftMargin = dpToPx(5);
                row.addView(right, rightLp);
                index++;
            } else {
                View placeholder = new View(this);
                android.widget.LinearLayout.LayoutParams emptyLp = new android.widget.LinearLayout.LayoutParams(
                        0,
                        1,
                        1f
                );
                row.addView(placeholder, emptyLp);
            }
            android.widget.LinearLayout.LayoutParams rowLp = (android.widget.LinearLayout.LayoutParams) row.getLayoutParams();
            rowLp.bottomMargin = dpToPx(8);
            layoutRecommendRows.addView(row, rowLp);
        }
    }

    private void renderRankingRow() {
        if (layoutRankingSongs == null) {
            return;
        }
        layoutRankingSongs.removeAllViews();
        if (rankingItems.isEmpty()) {
            if (tvRankingEmpty != null) {
                tvRankingEmpty.setVisibility(View.VISIBLE);
            }
            return;
        }
        if (tvRankingEmpty != null) {
            tvRankingEmpty.setVisibility(View.GONE);
        }
        int maxCount = Math.min(rankingItems.size(), 10);
        for (int i = 0; i < maxCount; i++) {
            View card = buildHomeSongCard(rankingItems.get(i), false);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    dpToPx(156),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.rightMargin = dpToPx(8);
            layoutRankingSongs.addView(card, lp);
        }
    }

    private void renderAllSongsRow() {
        if (layoutAllSongs == null) {
            return;
        }
        layoutAllSongs.removeAllViews();
        if (items.isEmpty()) {
            if (tvAllEmpty != null) {
                tvAllEmpty.setVisibility(View.VISIBLE);
            }
            return;
        }
        if (tvAllEmpty != null) {
            tvAllEmpty.setVisibility(View.GONE);
        }
        int maxCount = Math.min(items.size(), 12);
        for (int i = 0; i < maxCount; i++) {
            View card = buildHomeSongCard(items.get(i), false);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    dpToPx(156),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.rightMargin = dpToPx(8);
            layoutAllSongs.addView(card, lp);
        }
    }

    private View buildHomeSongCard(final MusicItem item, boolean compact) {
        android.widget.LinearLayout card = new android.widget.LinearLayout(this);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_music_plaza_card_selector);
        card.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

        ImageView cover = new ImageView(this);
        int coverSize = compact ? dpToPx(118) : dpToPx(132);
        android.widget.LinearLayout.LayoutParams coverLp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                coverSize
        );
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundResource(R.drawable.bg_music_plaza_cover_frame);
        cover.setPadding(1, 1, 1, 1);
        if (item != null) {
            if (item.coverUrl != null && item.coverUrl.length() > 0) {
                ImageLoader.load(cover, item.coverUrl);
            } else if (item.ownerAvatar != null && item.ownerAvatar.length() > 0) {
                ImageLoader.load(cover, item.ownerAvatar);
            } else {
                cover.setImageResource(R.drawable.ic_avatar_placeholder);
            }
        } else {
            cover.setImageResource(R.drawable.ic_avatar_placeholder);
        }
        card.addView(cover, coverLp);

        TextView name = new TextView(this);
        name.setTextColor(getResources().getColor(R.color.color_text_primary));
        name.setTextSize(13f);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, dpToPx(6), 0, 0);
        name.setText(item == null || item.name == null || item.name.length() == 0 ? "未命名歌曲" : item.name);
        card.addView(name);

        TextView meta = new TextView(this);
        meta.setTextColor(getResources().getColor(R.color.color_text_secondary));
        meta.setTextSize(11f);
        meta.setMaxLines(2);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        String owner = item == null ? "匿名用户" : item.ownerName;
        if (owner == null || owner.length() == 0) {
            owner = item == null ? "匿名用户" : item.ownerUid;
        }
        if (owner == null || owner.length() == 0) {
            owner = "匿名用户";
        }
        int duration = item == null ? 0 : item.durationMs;
        meta.setText(owner + " · " + formatDuration(duration) + " · 播放 " + (item == null ? 0 : item.playCount));
        card.addView(meta);

        final MusicItem clickItem = item;
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (clickItem == null) {
                    return;
                }
                playItem(clickItem);
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (clickItem == null) {
                    return false;
                }
                showItemActions(clickItem);
                return true;
            }
        });
        return card;
    }

    private void loadItems() {
        if (token == null || token.length() == 0) {
            Toast.makeText(this, getString(R.string.error_not_logged_in), Toast.LENGTH_SHORT).show();
            return;
        }
        final String q = etKeyword == null || etKeyword.getText() == null
                ? "" : etKeyword.getText().toString().trim();
        StringBuilder path = new StringBuilder("/music/plaza?limit=").append(PAGE_SIZE)
                .append("&offset=").append(currentOffset)
                .append("&sort=latest");
        if (showMineOnly) {
            path.append("&mine=1");
        }
        if (q.length() > 0) {
            path.append("&q=").append(urlEncode(q));
        }
        HttpUtil.get(path.toString(), token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject obj = new JSONObject(response);
                    JSONArray arr = obj.optJSONArray("items");
                    totalCount = obj.optInt("total", 0);
                    hasMore = obj.optBoolean("has_more", false);
                    items.clear();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject one = arr.optJSONObject(i);
                            if (one == null) {
                                continue;
                            }
                            MusicItem item = new MusicItem();
                            item.id = one.optString("id", "");
                            item.name = one.optString("name", "");
                            item.songUrl = one.optString("song_url", "");
                            item.coverUrl = one.optString("cover_url", "");
                            item.sizeBytes = one.optLong("size_bytes", 0);
                            item.durationMs = parseDurationMsFromJson(one);
                            item.ownerUid = one.optString("owner_uid", "");
                            item.ownerName = one.optString("owner_name", "");
                            item.ownerTitle = one.optString("owner_title", "");
                            item.ownerAvatar = one.optString("owner_avatar", "");
                            item.likes = one.optInt("likes", 0);
                            item.comments = one.optInt("comments", 0);
                            item.playCount = one.optInt("play_count", 0);
                            item.liked = one.optBoolean("liked", false);
                            item.canDelete = one.optBoolean("can_delete", false);
                            if (item.id.length() == 0 || item.songUrl.length() == 0) {
                                continue;
                            }
                            items.add(item);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    rebuildRecommendItems();
                    updateFeaturedCard();
                    updateHomeSections();
                    updatePagerUi();
                    loadRankingItems();
                } catch (Exception e) {
                    Toast.makeText(MusicPlazaActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(int code, String error) {
                if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                    return;
                }
                Toast.makeText(MusicPlazaActivity.this, "加载失败: " + code, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRankingItems() {
        if (token == null || token.length() == 0) {
            return;
        }
        HttpUtil.get("/music/plaza/ranking?limit=10", token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject obj = new JSONObject(response);
                    JSONArray arr = obj.optJSONArray("items");
                    rankingItems.clear();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject one = arr.optJSONObject(i);
                            if (one == null) {
                                continue;
                            }
                            MusicItem item = new MusicItem();
                            item.id = one.optString("id", "");
                            item.name = one.optString("name", "");
                            item.songUrl = one.optString("song_url", "");
                            item.coverUrl = one.optString("cover_url", "");
                            item.sizeBytes = one.optLong("size_bytes", 0);
                            item.durationMs = parseDurationMsFromJson(one);
                            item.ownerUid = one.optString("owner_uid", "");
                            item.ownerName = one.optString("owner_name", "");
                            item.ownerTitle = one.optString("owner_title", "");
                            item.ownerAvatar = one.optString("owner_avatar", "");
                            item.likes = one.optInt("likes", 0);
                            item.comments = one.optInt("comments", 0);
                            item.playCount = one.optInt("play_count", 0);
                            item.liked = one.optBoolean("liked", false);
                            item.canDelete = one.optBoolean("can_delete", false);
                            if (item.id.length() == 0 || item.songUrl.length() == 0) {
                                continue;
                            }
                            rankingItems.add(item);
                        }
                    }
                    updateHomeSections();
                } catch (Exception e) {
                    // ignore
                }
            }

            @Override
            public void onError(int code, String error) {
                // ignore
            }
        });
    }

    private void updatePagerUi() {
        if (tvPageInfo != null) {
            int from = totalCount <= 0 ? 0 : (currentOffset + 1);
            int to = Math.min(totalCount, currentOffset + items.size());
            tvPageInfo.setText("第 " + from + " - " + to + " 条，共 " + totalCount + " 条");
        }
        int mineCountInPage = 0;
        int likedCountInPage = 0;
        for (int i = 0; i < items.size(); i++) {
            MusicItem one = items.get(i);
            if (one == null) {
                continue;
            }
            if (one.canDelete) {
                mineCountInPage++;
            }
            if (one.liked) {
                likedCountInPage++;
            }
        }
        if (tvSummary != null) {
            if (showMineOnly) {
                tvSummary.setText("我的上传：当前页 " + items.size() + " 首，累计 " + totalCount + " 首");
            } else {
                tvSummary.setText("共 " + totalCount + " 首 · 当前加载 " + items.size() + " 首 · 我的 " + mineCountInPage + " 首 · 已赞 " + likedCountInPage + " 首");
            }
        }
        if (btnPrev != null) {
            btnPrev.setEnabled(currentOffset > 0);
            ViewCompat.setAlpha(btnPrev, currentOffset > 0 ? 1f : 0.55f);
        }
        if (btnNext != null) {
            btnNext.setEnabled(hasMore);
            ViewCompat.setAlpha(btnNext, hasMore ? 1f : 0.55f);
        }
    }

    private void showUploadDialog() {
        final EditText nameInput = new EditText(this);
        nameInput.setHint("请输入歌曲名称");
        nameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)});
        final TextView songText = new TextView(this);
        final TextView coverText = new TextView(this);
        uploadSongTextView = songText;
        uploadCoverTextView = coverText;
        refreshUploadSelectionLabels();
        songText.setPadding(0, dpToPx(6), 0, dpToPx(4));
        coverText.setPadding(0, dpToPx(2), 0, dpToPx(4));

        final TextView chooseSong = buildFlatTextButton("选择歌曲文件");
        final TextView chooseCover = buildFlatTextButton("选择封面文件");

        pendingSongUri = null;
        pendingCoverUri = null;
        pendingName = null;

        final android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(dpToPx(14), dpToPx(6), dpToPx(14), 0);
        root.addView(nameInput);
        root.addView(songText);
        root.addView(chooseSong);
        root.addView(coverText);
        root.addView(chooseCover);

        chooseSong.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pendingName = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
                tryPickSongFile();
            }
        });
        chooseCover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pendingName = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
                tryPickCoverFile();
            }
        });
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("上传音乐")
                .setView(root)
                .setNegativeButton("取消", null)
                .setPositiveButton("开始上传", null)
                .create();
        uploadDialog = dialog;
        dialog.show();
        refreshUploadSelectionLabels();
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface dialogInterface) {
                if (uploadDialog == dialog) {
                    uploadDialog = null;
                    uploadSongTextView = null;
                    uploadCoverTextView = null;
                }
            }
        });

        View positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String name = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
                    if (name.length() == 0) {
                        Toast.makeText(MusicPlazaActivity.this, "请填写歌曲名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (pendingSongUri == null) {
                        Toast.makeText(MusicPlazaActivity.this, "请选择歌曲文件", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    pendingName = name;
                    dialog.dismiss();
                    uploadSongPackage();
                }
            });
        }

    }

    private void refreshUploadSelectionLabels() {
        if (uploadSongTextView != null) {
            uploadSongTextView.setText("歌曲文件：" + safeFileName(pendingSongUri, "未选择（≤20MB）"));
        }
        if (uploadCoverTextView != null) {
            uploadCoverTextView.setText("封面文件：" + safeFileName(pendingCoverUri, "未选择（可选，≤1MB）"));
        }
    }

    private TextView buildFlatTextButton(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(getResources().getColor(R.color.color_text_primary));
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));
        tv.setBackgroundResource(R.drawable.flat_button_bg);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dpToPx(6);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void pickSongFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(intent, "选择歌曲文件"), REQ_PICK_SONG);
    }

    private void pickCoverFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(intent, "选择封面图片"), REQ_PICK_COVER);
    }

    private void tryPickSongFile() {
        if (ensurePickPermission(PICK_KIND_SONG)) {
            pickSongFile();
        }
    }

    private void tryPickCoverFile() {
        if (ensurePickPermission(PICK_KIND_COVER)) {
            pickCoverFile();
        }
    }

    private boolean ensurePickPermission(int pickKind) {
        if (hasPickPermission(pickKind)) {
            return true;
        }
        String permission = resolveReadPermissionForPick(pickKind);
        if (permission == null || permission.length() == 0) {
            return true;
        }
        pendingPickKind = pickKind;
        ActivityCompat.requestPermissions(this, new String[]{permission}, REQ_PICK_MEDIA_PERMISSION);
        return false;
    }

    private boolean hasPickPermission(int pickKind) {
        String permission = resolveReadPermissionForPick(pickKind);
        if (permission == null || permission.length() == 0) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private String resolveReadPermissionForPick(int pickKind) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return "";
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (pickKind == PICK_KIND_COVER) {
                return "android.permission.READ_MEDIA_IMAGES";
            }
            return "android.permission.READ_MEDIA_AUDIO";
        }
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PICK_MEDIA_PERMISSION) {
            return;
        }
        int kind = pendingPickKind;
        pendingPickKind = PICK_KIND_NONE;
        boolean granted = grantResults != null
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            Toast.makeText(this, "未授予媒体读取权限，无法选择文件", Toast.LENGTH_SHORT).show();
            return;
        }
        if (kind == PICK_KIND_COVER) {
            pickCoverFile();
        } else if (kind == PICK_KIND_SONG) {
            pickSongFile();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        if (requestCode == REQ_PICK_SONG) {
            pendingSongUri = uri;
            refreshUploadSelectionLabels();
            Toast.makeText(this, "已选择歌曲：" + safeFileName(uri, "song"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (requestCode == REQ_PICK_COVER) {
            pendingCoverUri = uri;
            refreshUploadSelectionLabels();
            Toast.makeText(this, "已选择封面：" + safeFileName(uri, "cover"), Toast.LENGTH_SHORT).show();
            return;
        }
    }

    private void uploadSongPackage() {
        if (pendingSongUri == null || pendingName == null || pendingName.length() == 0) {
            return;
        }
        final Uri songUri = pendingSongUri;
        final Uri coverUri = pendingCoverUri;
        final String name = pendingName;

        String songName = queryDisplayName(songUri);
        if (songName == null || songName.length() == 0) {
            songName = "song.mp3";
        }
        final String songFileName = songName;
        final String songMime = resolveSongMime(songUri, songFileName);
        long songSize = resolveContentLength(songUri);
        if (songSize > MAX_SONG_BYTES) {
            Toast.makeText(this, "歌曲不能超过20MB", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasPickPermission(PICK_KIND_SONG)) {
            Toast.makeText(this, "未授予媒体读取权限，请先在系统设置中开启后重试", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!canReadUri(songUri)) {
            Toast.makeText(this, "歌曲文件不可读取，请重新选择本地文件", Toast.LENGTH_SHORT).show();
            return;
        }

        byte[] coverBytes = null;
        String coverName = "cover.jpg";
        String coverType = "image/jpeg";
        if (coverUri != null) {
            long coverSize = resolveContentLength(coverUri);
            if (coverSize > MAX_COVER_BYTES) {
                Toast.makeText(this, "封面不能超过1MB", Toast.LENGTH_SHORT).show();
                return;
            }
            coverName = queryDisplayName(coverUri);
            if (coverName == null || coverName.length() == 0) {
                coverName = "cover.jpg";
            }
            coverType = resolveImageMime(coverUri, coverName);
            coverBytes = readUriBytesSafe(coverUri, MAX_COVER_BYTES);
            if (coverBytes == null || coverBytes.length == 0) {
                Toast.makeText(this, "封面读取失败或超过1MB", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        final byte[] finalCoverBytes = coverBytes;
        final String finalCoverName = coverName;
        final String finalCoverType = coverType;

        Toast.makeText(this, "正在上传音乐...", Toast.LENGTH_SHORT).show();
        HttpUtil.StreamProvider provider = new HttpUtil.StreamProvider() {
            @Override
            public InputStream open() throws Exception {
                return openInputStreamCompat(songUri);
            }

            @Override
            public long length() {
                return resolveContentLength(songUri);
            }
        };

        HttpUtil.postMultipartStreamWithThumb(
                "/music/plaza/upload?name=" + urlEncode(name),
                provider,
                songFileName,
                songMime,
                finalCoverBytes,
                finalCoverName,
                finalCoverType,
                token,
                null,
                new HttpUtil.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        String itemId = "";
                        try {
                            JSONObject obj = new JSONObject(response);
                            itemId = obj.optString("id", "");
                        } catch (Exception e) {
                            itemId = "";
                        }
                        Toast.makeText(MusicPlazaActivity.this, "上传成功", Toast.LENGTH_SHORT).show();
                        currentOffset = 0;
                        loadItems();
                        pendingSongUri = null;
                        pendingCoverUri = null;
                        pendingName = null;
                    }

                    @Override
                    public void onError(int code, String error) {
                        if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                            return;
                        }
                        if (code == 409 || (error != null && error.contains("duplicate_song"))) {
                            Toast.makeText(MusicPlazaActivity.this, "你已上传过同一首歌", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (code == 413 && error != null && error.contains("cover_too_large")) {
                            Toast.makeText(MusicPlazaActivity.this, "封面不能超过1MB", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (code == 413 || (error != null && error.contains("song_too_large"))) {
                            Toast.makeText(MusicPlazaActivity.this, "歌曲不能超过20MB", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (code == 400 && error != null && error.contains("invalid_song")) {
                            Toast.makeText(MusicPlazaActivity.this, "歌曲格式不支持", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (code == 400 && error != null && error.contains("invalid_cover")) {
                            Toast.makeText(MusicPlazaActivity.this, "封面格式不支持", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (code == -1) {
                            String lower = error == null ? "" : error.toLowerCase(Locale.US);
                            if (lower.contains("cannot_open_stream") || lower.contains("open failed") || lower.contains("enoent")) {
                                Toast.makeText(MusicPlazaActivity.this, "上传失败：文件读取异常，请重新选择歌曲", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (lower.contains("timeout") || lower.contains("timed out")) {
                                Toast.makeText(MusicPlazaActivity.this, "上传超时，请检查网络后重试", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            Toast.makeText(MusicPlazaActivity.this, "上传失败：网络异常，请稍后重试", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Toast.makeText(MusicPlazaActivity.this, "上传失败: " + code, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void showItemActions(final MusicItem item) {
        if (item == null) {
            return;
        }
        final java.util.ArrayList<String> actions = new java.util.ArrayList<String>();
        actions.add("播放");
        actions.add("下载到本地");
        actions.add("缓存到本地");
        actions.add(item.liked ? "取消点赞" : "点赞");
        actions.add("评论");
        actions.add("分享到聊天");
        actions.add("收藏");
        actions.add("举报");
        if (item.canDelete) {
            actions.add("删除歌曲");
        }
        final CharSequence[] array = actions.toArray(new CharSequence[actions.size()]);
        new AlertDialog.Builder(this)
                .setTitle(item.name == null || item.name.length() == 0 ? "歌曲操作" : item.name)
                .setItems(array, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which < 0 || which >= actions.size()) {
                            return;
                        }
                        String action = actions.get(which);
                        if ("播放".equals(action)) {
                            playItem(item);
                            return;
                        }
                        if ("下载到本地".equals(action)) {
                            downloadMusicItem(item);
                            return;
                        }
                        if ("缓存到本地".equals(action)) {
                            cacheMusicItem(item);
                            return;
                        }
                        if ("点赞".equals(action) || "取消点赞".equals(action)) {
                            toggleLike(item);
                            return;
                        }
                        if ("评论".equals(action)) {
                            openComments(item);
                            return;
                        }
                        if ("分享到聊天".equals(action)) {
                            shareMusicItem(item);
                            return;
                        }
                        if ("收藏".equals(action)) {
                            String owner = item.ownerName;
                            if (owner == null || owner.length() == 0) {
                                owner = item.ownerUid;
                            }
                            FavoriteHelper.addMusicFavorite(MusicPlazaActivity.this,
                                    item.id,
                                    item.name,
                                    owner,
                                    item.songUrl,
                                    item.coverUrl);
                            return;
                        }
                        if ("举报".equals(action)) {
                            showMusicReportDialog(item);
                            return;
                        }
                        if ("删除歌曲".equals(action)) {
                            deleteMusicItem(item);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showMusicReportDialog(final MusicItem item) {
        if (item == null) {
            return;
        }
        final String targetUid = item.ownerUid == null ? "" : item.ownerUid.trim();
        if (targetUid.length() == 0) {
            Toast.makeText(this, "无法举报：缺少发布者信息", Toast.LENGTH_SHORT).show();
            return;
        }
        if (myUid != null && myUid.length() > 0 && myUid.equalsIgnoreCase(targetUid)) {
            Toast.makeText(this, "不能举报自己发布的歌曲", Toast.LENGTH_SHORT).show();
            return;
        }
        final EditText input = new EditText(this);
        input.setHint("如：侵权、违规内容、恶意音频等");
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(180)});
        int pad = dpToPx(10);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("举报歌曲")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("提交", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String reason = input.getText() == null ? "" : input.getText().toString().trim();
                        StringBuilder detail = new StringBuilder();
                        detail.append("[音乐广场举报]");
                        if (item.id != null && item.id.length() > 0) {
                            detail.append(" item_id=").append(item.id);
                        }
                        if (item.name != null && item.name.length() > 0) {
                            detail.append(" name=").append(item.name);
                        }
                        String full = reason;
                        if (full.length() > 0) {
                            full += "\n";
                        }
                        full += detail.toString();
                        submitUserReport(targetUid, full);
                    }
                })
                .show();
    }

    private void submitUserReport(String targetUid, String reason) {
        if (targetUid == null || targetUid.length() == 0) {
            Toast.makeText(this, "举报失败：缺少目标用户", Toast.LENGTH_SHORT).show();
            return;
        }
        if (token == null || token.length() == 0) {
            Toast.makeText(this, getString(R.string.error_not_logged_in), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("target_uid", targetUid);
            json.put("reason", reason == null ? "" : reason);
            HttpUtil.post("/reports/user", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    Toast.makeText(MusicPlazaActivity.this, "举报已提交，已进入公开法庭", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(int code, String error) {
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    Toast.makeText(MusicPlazaActivity.this, "举报失败: " + code, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "举报失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadMusicItem(final MusicItem item) {
        if (item == null || item.songUrl == null || item.songUrl.length() == 0) {
            Toast.makeText(this, "歌曲链接不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        String resolvedUrl = MediaUrlResolver.resolve(item.songUrl);
        if (resolvedUrl == null || resolvedUrl.length() == 0) {
            Toast.makeText(this, "歌曲链接不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "开始下载到本地", Toast.LENGTH_SHORT).show();
        DownloadUtil.saveUrlToDownloadsAsync(this,
                resolvedUrl,
                "oldchat_music_",
                ".mp3",
                new DownloadUtil.Callback() {
                    @Override
                    public void onResult(boolean success, String message, java.io.File file) {
                        Toast.makeText(MusicPlazaActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void cacheMusicItem(final MusicItem item) {
        if (item == null || item.songUrl == null || item.songUrl.length() == 0) {
            Toast.makeText(this, "歌曲链接不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        String resolvedUrl = MediaUrlResolver.resolve(item.songUrl);
        if (resolvedUrl == null || resolvedUrl.length() == 0) {
            Toast.makeText(this, "歌曲链接不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        synchronized (cacheDownloadingUrls) {
            if (cacheDownloadingUrls.contains(resolvedUrl)) {
                Toast.makeText(this, "该歌曲正在缓存中", Toast.LENGTH_SHORT).show();
                return;
            }
            cacheDownloadingUrls.add(resolvedUrl);
        }
        Toast.makeText(this, "已开始缓存，完成后会提示", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, MusicPlaybackService.class);
        intent.setAction(MusicPlaybackService.ACTION_CACHE_SONG);
        intent.putExtra(MusicPlaybackService.EXTRA_SONG_URL, resolvedUrl);
        startMusicService(intent);
    }

    private void startMusicService(Intent intent) {
        if (intent == null) {
            return;
        }
        try {
            startService(intent);
        } catch (Exception e) {
            synchronized (cacheDownloadingUrls) {
                cacheDownloadingUrls.remove(MediaUrlResolver.resolve(intent.getStringExtra(MusicPlaybackService.EXTRA_SONG_URL)));
            }
            Toast.makeText(this, "启动下载失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleMusicCacheResult(Intent intent) {
        if (intent == null) {
            return;
        }
        if (!MusicPlaybackService.ACTION_CACHE_RESULT.equals(intent.getAction())) {
            return;
        }
        String url = intent.getStringExtra(MusicPlaybackService.EXTRA_CACHE_URL);
        if (url == null) {
            url = "";
        }
        synchronized (cacheDownloadingUrls) {
            cacheDownloadingUrls.remove(url);
        }
        boolean ok = intent.getBooleanExtra(MusicPlaybackService.EXTRA_CACHE_OK, false);
        long size = intent.getLongExtra(MusicPlaybackService.EXTRA_CACHE_SIZE, 0L);
        String error = intent.getStringExtra(MusicPlaybackService.EXTRA_CACHE_ERROR);
        if (ok) {
            Toast.makeText(this, "已缓存到本地（" + formatSize(size) + "）", Toast.LENGTH_SHORT).show();
            return;
        }
        if (error != null && error.length() > 0) {
            Toast.makeText(this, "缓存失败：" + error, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "缓存失败", Toast.LENGTH_SHORT).show();
    }

    private void toggleLike(final MusicItem item) {
        if (item == null || item.id == null || item.id.length() == 0) {
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("item_id", item.id);
            String path = item.liked ? "/music/plaza/unlike" : "/music/plaza/like";
            HttpUtil.post(path, json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    try {
                        JSONObject obj = new JSONObject(response);
                        item.liked = obj.optBoolean("liked", item.liked);
                        item.likes = obj.optInt("likes", item.likes);
                    } catch (Exception e) {
                    }
                    adapter.notifyDataSetChanged();
                }

                @Override
                public void onError(int code, String error) {
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    Toast.makeText(MusicPlazaActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "操作失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void openComments(MusicItem item) {
        if (item == null || item.id == null || item.id.length() == 0) {
            return;
        }
        Intent intent = new Intent(this, MusicCommentsActivity.class);
        intent.putExtra("item_id", item.id);
        intent.putExtra("owner_uid", item.ownerUid == null ? "" : item.ownerUid);
        startActivity(intent);
    }

    private void shareMusicItem(MusicItem item) {
        if (item == null || item.songUrl == null || item.songUrl.length() == 0) {
            Toast.makeText(this, "歌曲链接不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        MusicShareActionHelper.ShareItem share = new MusicShareActionHelper.ShareItem();
        share.id = item.id;
        share.name = item.name;
        share.songUrl = item.songUrl;
        share.coverUrl = item.coverUrl;
        if ((share.coverUrl == null || share.coverUrl.length() == 0)
                && item.ownerAvatar != null && item.ownerAvatar.length() > 0) {
            share.coverUrl = item.ownerAvatar;
        }
        share.ownerUid = item.ownerUid;
        share.ownerName = item.ownerName;
        share.durationMs = item.durationMs;
        MusicShareActionHelper.showShareDialog(this, token, share);
    }

    private void deleteMusicItem(final MusicItem item) {
        if (item == null || item.id == null || item.id.length() == 0 || !item.canDelete) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除歌曲")
                .setMessage("确定删除这首歌曲吗？")
                .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        try {
                            JSONObject json = new JSONObject();
                            json.put("item_id", item.id);
                            HttpUtil.post("/music/plaza/delete", json, token, new HttpUtil.Callback() {
                                @Override
                                public void onSuccess(String response) {
                                    items.remove(item);
                                    adapter.notifyDataSetChanged();
                                    updatePagerUi();
                                    Toast.makeText(MusicPlazaActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onError(int code, String error) {
                                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                                        return;
                                    }
                                    Toast.makeText(MusicPlazaActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } catch (Exception e) {
                            Toast.makeText(MusicPlazaActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void requestPlaybackState() {
        Intent intent = new Intent(this, MusicPlaybackService.class);
        intent.setAction(MusicPlaybackService.ACTION_REQUEST_STATE);
        try {
            startService(intent);
        } catch (Exception ignore) {
        }
    }

    private void handlePlaybackState(Intent intent) {
        if (intent == null) {
            return;
        }
        if (!MusicPlaybackService.ACTION_STATE_CHANGED.equals(intent.getAction())) {
            return;
        }
        currentPlayingSongName = safeString(intent.getStringExtra(MusicPlaybackService.EXTRA_SONG_NAME));
        currentPlayingSongUrl = safeString(intent.getStringExtra(MusicPlaybackService.EXTRA_SONG_URL));
        currentPlayingCoverUrl = safeString(intent.getStringExtra(MusicPlaybackService.EXTRA_COVER_URL));
        currentPlayingOwnerUid = safeString(intent.getStringExtra(MusicPlaybackService.EXTRA_OWNER_UID));
        currentPlayingOwnerName = safeString(intent.getStringExtra(MusicPlaybackService.EXTRA_OWNER_NAME));
        currentPlayingOwnerTitle = safeString(intent.getStringExtra(MusicPlaybackService.EXTRA_OWNER_TITLE));
        currentPlayingOwnerAvatar = safeString(intent.getStringExtra(MusicPlaybackService.EXTRA_OWNER_AVATAR));
        currentPlayingActive = intent.getBooleanExtra(MusicPlaybackService.EXTRA_IS_PLAYING, false)
                || intent.getBooleanExtra(MusicPlaybackService.EXTRA_IS_PREPARING, false);
        updateNowPlayingButton();
    }

    private void updateNowPlayingButton() {
        if (btnNowPlaying == null) {
            return;
        }
        if (currentPlayingSongUrl.length() == 0) {
            btnNowPlaying.setText("正在播放");
            ViewCompat.setAlpha(btnNowPlaying, 0.85f);
            return;
        }
        ViewCompat.setAlpha(btnNowPlaying, 1.0f);
        btnNowPlaying.setText(currentPlayingActive ? "播放中" : "继续播放");
    }

    private void openNowPlayingPage() {
        if (currentPlayingSongUrl.length() == 0) {
            requestPlaybackState();
            Toast.makeText(this, "当前没有正在播放的歌曲", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, MusicPlayerActivity.class);
        intent.putExtra("song_name", currentPlayingSongName);
        intent.putExtra("song_url", currentPlayingSongUrl);
        intent.putExtra("cover_url", currentPlayingCoverUrl);
        intent.putExtra("owner_uid", currentPlayingOwnerUid);
        intent.putExtra("owner_name", currentPlayingOwnerName);
        intent.putExtra("owner_title", currentPlayingOwnerTitle);
        intent.putExtra("owner_avatar", currentPlayingOwnerAvatar);
        startActivity(intent);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private void playItem(final MusicItem item) {
        if (item == null || item.songUrl == null || item.songUrl.length() == 0) {
            Toast.makeText(this, "歌曲链接不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        saveRecentPlay(item);
        reportPlay(item);
        currentPlayingSongName = item.name == null ? "" : item.name;
        currentPlayingSongUrl = item.songUrl == null ? "" : item.songUrl;
        currentPlayingCoverUrl = item.coverUrl == null ? "" : item.coverUrl;
        currentPlayingOwnerUid = item.ownerUid == null ? "" : item.ownerUid;
        currentPlayingOwnerName = item.ownerName == null ? "" : item.ownerName;
        currentPlayingOwnerTitle = item.ownerTitle == null ? "" : item.ownerTitle;
        currentPlayingOwnerAvatar = item.ownerAvatar == null ? "" : item.ownerAvatar;
        currentPlayingActive = true;
        updateNowPlayingButton();

        Intent intent = new Intent(this, MusicPlayerActivity.class);
        intent.putExtra("song_name", currentPlayingSongName);
        intent.putExtra("song_url", currentPlayingSongUrl);
        intent.putExtra("cover_url", currentPlayingCoverUrl);
        intent.putExtra("owner_uid", currentPlayingOwnerUid);
        intent.putExtra("owner_name", currentPlayingOwnerName);
        intent.putExtra("owner_title", currentPlayingOwnerTitle);
        intent.putExtra("owner_avatar", currentPlayingOwnerAvatar);
        startActivity(intent);
    }

    private void reportPlay(final MusicItem item) {
        if (item == null || item.id == null || item.id.length() == 0) {
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("item_id", item.id);
            HttpUtil.post("/music/plaza/play", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    try {
                        JSONObject obj = new JSONObject(response);
                        int count = obj.optInt("play_count", item.playCount + 1);
                        updatePlayCountById(item.id, count);
                    } catch (Exception e) {
                        updatePlayCountById(item.id, item.playCount + 1);
                    }
                }

                @Override
                public void onError(int code, String error) {
                    // ignore
                }
            });
        } catch (Exception e) {
            // ignore
        }
    }

    private void updatePlayCountById(String itemId, int playCount) {
        if (itemId == null || itemId.length() == 0) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            MusicItem one = items.get(i);
            if (one != null && itemId.equals(one.id)) {
                one.playCount = playCount;
            }
        }
        for (int i = 0; i < rankingItems.size(); i++) {
            MusicItem one = rankingItems.get(i);
            if (one != null && itemId.equals(one.id)) {
                one.playCount = playCount;
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateHomeSections();
    }

    private void saveRecentPlay(MusicItem item) {
        if (item == null || item.songUrl == null || item.songUrl.length() == 0) {
            return;
        }
        String name = item.name == null ? "" : item.name;
        String owner = item.ownerName;
        if (owner == null || owner.length() == 0) {
            owner = item.ownerUid == null ? "" : item.ownerUid;
        }
        String raw = encodeRecentPart(item.id) + "|"
                + encodeRecentPart(name) + "|"
                + encodeRecentPart(owner) + "|"
                + encodeRecentPart(item.songUrl) + "|"
                + encodeRecentPart(item.coverUrl);

        List<String> all = getRecentPlayEntries();
        List<String> next = new ArrayList<String>();
        next.add(raw);
        for (int i = 0; i < all.size(); i++) {
            String one = all.get(i);
            if (one == null || one.length() == 0 || one.equals(raw)) {
                continue;
            }
            next.add(one);
            if (next.size() >= MAX_RECENT_PLAY) {
                break;
            }
        }
        SharedPreferences prefs = getSharedPreferences(PREF_MUSIC_LOCAL, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_RECENT_PLAY, joinRecentEntries(next)).apply();
    }

    private List<String> getRecentPlayEntries() {
        SharedPreferences prefs = getSharedPreferences(PREF_MUSIC_LOCAL, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_RECENT_PLAY, "");
        List<String> out = new ArrayList<String>();
        if (raw == null || raw.length() == 0) {
            return out;
        }
        String[] lines = raw.split("\\n");
        for (int i = 0; i < lines.length; i++) {
            String one = lines[i] == null ? "" : lines[i].trim();
            if (one.length() == 0) {
                continue;
            }
            out.add(one);
            if (out.size() >= MAX_RECENT_PLAY) {
                break;
            }
        }
        return out;
    }

    private String joinRecentEntries(List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            String one = entries.get(i);
            if (one == null || one.length() == 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(one);
        }
        return sb.toString();
    }

    private String encodeRecentPart(String value) {
        String safe = value == null ? "" : value;
        safe = safe.replace("\\", "\\\\");
        safe = safe.replace("|", "\\p");
        safe = safe.replace("\n", " ");
        return safe;
    }

    private String decodeRecentPart(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (esc) {
                if (c == 'p') {
                    sb.append('|');
                } else {
                    sb.append(c);
                }
                esc = false;
                continue;
            }
            if (c == '\\') {
                esc = true;
                continue;
            }
            sb.append(c);
        }
        if (esc) {
            sb.append('\\');
        }
        return sb.toString();
    }

    private List<String> splitRecentParts(String raw) {
        List<String> out = new ArrayList<String>();
        if (raw == null) {
            return out;
        }
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (esc) {
                sb.append('\\');
                sb.append(c);
                esc = false;
                continue;
            }
            if (c == '\\') {
                esc = true;
                continue;
            }
            if (c == '|') {
                out.add(decodeRecentPart(sb.toString()));
                sb.setLength(0);
                continue;
            }
            sb.append(c);
        }
        if (esc) {
            sb.append('\\');
        }
        out.add(decodeRecentPart(sb.toString()));
        return out;
    }

    private String decodeRecentLabel(String raw) {
        List<String> parts = splitRecentParts(raw);
        String name = parts.size() > 1 ? parts.get(1) : "";
        String owner = parts.size() > 2 ? parts.get(2) : "";
        if (name == null || name.length() == 0) {
            name = "未命名歌曲";
        }
        if (owner == null || owner.length() == 0) {
            owner = "匿名用户";
        }
        return name + " · " + owner;
    }

    private MusicItem parseRecentEntry(String raw) {
        List<String> parts = splitRecentParts(raw);
        if (parts.size() < 4) {
            return null;
        }
        MusicItem item = new MusicItem();
        item.id = parts.get(0);
        item.name = parts.get(1);
        item.ownerName = parts.get(2);
        item.songUrl = parts.get(3);
        item.coverUrl = parts.size() > 4 ? parts.get(4) : "";
        return item;
    }

    private void stopPlay() {

        if (mediaPlayer != null) {
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
        currentPlayUrl = null;
    }

    private String safeFileName(Uri uri, String fallback) {
        String name = queryDisplayName(uri);
        if (name == null || name.length() == 0) {
            return fallback;
        }
        return name;
    }

    private String queryDisplayName(Uri uri) {
        if (uri == null) {
            return "";
        }
        String name = null;
        android.database.Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    name = cursor.getString(idx);
                }
            }
        } catch (Exception e) {
            name = null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (name == null || name.length() == 0) {
            String path = uri.getLastPathSegment();
            if (path != null && path.length() > 0) {
                name = path;
            }
        }
        return name == null ? "" : name;
    }

    private InputStream openInputStreamCompat(Uri uri) throws IOException {
        if (uri == null) {
            throw new IOException("uri_empty");
        }
        InputStream input = null;
        try {
            input = getContentResolver().openInputStream(uri);
        } catch (Exception e) {
            input = null;
        }
        if (input != null) {
            return input;
        }
        try {
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
            if (pfd != null) {
                return new ParcelFileDescriptor.AutoCloseInputStream(pfd);
            }
        } catch (Exception e) {
        }
        throw new IOException("cannot_open_stream");
    }

    private boolean canReadUri(Uri uri) {
        if (uri == null) {
            return false;
        }
        InputStream is = null;
        try {
            is = openInputStreamCompat(uri);
            return is != null;
        } catch (Exception e) {
            return false;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                }
            }
        }
    }

    private String resolveSongMime(Uri uri, String fileName) {
        String type = null;
        try {
            type = getContentResolver().getType(uri);
        } catch (Exception e) {
            type = null;
        }
        if (!TextUtils.isEmpty(type)) {
            return type;
        }
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.US);
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lower.endsWith(".m4a") || lower.endsWith(".mp4")) {
            return "audio/mp4";
        }
        if (lower.endsWith(".aac")) {
            return "audio/aac";
        }
        if (lower.endsWith(".amr")) {
            return "audio/amr";
        }
        if (lower.endsWith(".3gp") || lower.endsWith(".3gpp")) {
            return "audio/3gpp";
        }
        if (lower.endsWith(".flac")) {
            return "audio/flac";
        }
        if (lower.endsWith(".ogg") || lower.endsWith(".oga")) {
            return "audio/ogg";
        }
        if (lower.endsWith(".wav") || lower.endsWith(".wave")) {
            return "audio/wav";
        }
        return "audio/mpeg";
    }

    private String resolveImageMime(Uri uri, String fileName) {
        String type = null;
        try {
            type = getContentResolver().getType(uri);
        } catch (Exception e) {
            type = null;
        }
        if (!TextUtils.isEmpty(type)) {
            return type;
        }
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.US);
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        return "image/jpeg";
    }

    private byte[] readUriBytesSafe(Uri uri, long maxBytes) {
        if (uri == null) {
            return null;
        }
        InputStream is = null;
        java.io.ByteArrayOutputStream bos = null;
        try {
            is = openInputStreamCompat(uri);
            if (is == null) {
                return null;
            }
            bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            long total = 0;
            while ((len = is.read(buf)) != -1) {
                total += len;
                if (maxBytes > 0 && total > maxBytes) {
                    return null;
                }
                bos.write(buf, 0, len);
            }
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                }
            }
            if (bos != null) {
                try {
                    bos.close();
                } catch (Exception e) {
                }
            }
        }
    }

    private long resolveContentLength(Uri uri) {
        if (uri == null) {
            return -1;
        }

        android.database.Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !cursor.isNull(idx)) {
                    long size = cursor.getLong(idx);
                    if (size > 0) {
                        return size;
                    }
                }
            }
        } catch (Exception e) {
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception e) {
                }
            }
        }

        android.content.res.AssetFileDescriptor afd = null;
        try {
            afd = getContentResolver().openAssetFileDescriptor(uri, "r");
            if (afd != null && afd.getLength() > 0) {
                return afd.getLength();
            }
        } catch (Exception e) {
        } finally {
            if (afd != null) {
                try {
                    afd.close();
                } catch (Exception e) {
                }
            }
        }

        ParcelFileDescriptor pfd = null;
        try {
            pfd = getContentResolver().openFileDescriptor(uri, "r");
            if (pfd != null) {
                long size = pfd.getStatSize();
                if (size > 0) {
                    return size;
                }
            }
        } catch (Exception e) {
        } finally {
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (Exception e) {
                }
            }
        }

        return -1;
    }

    private String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private String formatSize(long sizeBytes) {
        if (sizeBytes <= 0) {
            return "0B";
        }
        float size = sizeBytes;
        if (size < 1024) {
            return (int) size + "B";
        }
        size = size / 1024f;
        if (size < 1024) {
            return String.format(Locale.getDefault(), "%.1fKB", size);
        }
        size = size / 1024f;
        return String.format(Locale.getDefault(), "%.1fMB", size);
    }

    private String formatDuration(int durationMs) {
        if (durationMs <= 0) {
            return "--:--";
        }
        int totalSeconds = durationMs / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        if (minutes > 99) {
            minutes = 99;
        }
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private int parseDurationMsFromJson(JSONObject obj) {
        if (obj == null) {
            return 0;
        }
        String[] numberKeys = new String[]{
                "duration_ms",
                "durationMs",
                "duration",
                "duration_sec",
                "durationSec",
                "seconds",
                "length"
        };
        for (int i = 0; i < numberKeys.length; i++) {
            String key = numberKeys[i];
            long raw = parseDurationNumber(obj.opt(key));
            int normalized = normalizeDurationToMs(raw, key);
            if (normalized > 0) {
                return normalized;
            }
        }

        String[] textKeys = new String[]{"duration_text", "duration", "length"};
        for (int i = 0; i < textKeys.length; i++) {
            int parsed = parseDurationTextToMs(obj.optString(textKeys[i], ""));
            if (parsed > 0) {
                return parsed;
            }
        }

        return findDurationRecursively(obj, 0);
    }

    private long parseDurationNumber(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return -1;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (!(value instanceof String)) {
            return -1;
        }
        String text = ((String) value).trim();
        if (text.length() == 0 || text.contains(":")) {
            return -1;
        }
        try {
            if (text.contains(".")) {
                return Math.round(Double.parseDouble(text));
            }
            return Long.parseLong(text);
        } catch (Exception e) {
            return -1;
        }
    }

    private int normalizeDurationToMs(long raw, String keyHint) {
        if (raw <= 0) {
            return 0;
        }
        String key = keyHint == null ? "" : keyHint.toLowerCase(Locale.US);
        if (key.contains("ms") || key.contains("milli")) {
            if (raw > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return (int) raw;
        }
        if (key.contains("sec") || key.contains("second")) {
            long msSec = raw * 1000L;
            if (msSec > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return (int) msSec;
        }
        long ms = raw;
        if (raw <= 10000L) {
            ms = raw * 1000L;
        }
        if (ms > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) ms;
    }

    private int findDurationRecursively(Object value, int depth) {
        if (value == null || value == JSONObject.NULL || depth > 4) {
            return 0;
        }
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object child = obj.opt(key);
                String lowerKey = key == null ? "" : key.toLowerCase(Locale.US);
                if (isDurationLikeKey(lowerKey)) {
                    long number = parseDurationNumber(child);
                    int normalized = normalizeDurationToMs(number, lowerKey);
                    if (normalized > 0) {
                        return normalized;
                    }
                    if (child instanceof String) {
                        int parsedText = parseDurationTextToMs((String) child);
                        if (parsedText > 0) {
                            return parsedText;
                        }
                    }
                }
                int nested = findDurationRecursively(child, depth + 1);
                if (nested > 0) {
                    return nested;
                }
            }
            return 0;
        }
        if (value instanceof org.json.JSONArray) {
            org.json.JSONArray arr = (org.json.JSONArray) value;
            for (int i = 0; i < arr.length(); i++) {
                int nested = findDurationRecursively(arr.opt(i), depth + 1);
                if (nested > 0) {
                    return nested;
                }
            }
            return 0;
        }
        if (value instanceof String) {
            return parseDurationTextToMs((String) value);
        }
        return 0;
    }

    private boolean isDurationLikeKey(String lowerKey) {
        if (lowerKey == null || lowerKey.length() == 0) {
            return false;
        }
        return lowerKey.contains("duration")
                || lowerKey.contains("length")
                || lowerKey.contains("seconds")
                || lowerKey.contains("second")
                || lowerKey.contains("time");
    }

    private void ensureDurationResolved(final MusicItem item) {
        if (item == null || item.durationMs > 0) {
            return;
        }
        final String resolvedUrl = MediaUrlResolver.resolve(item.songUrl);
        if (resolvedUrl == null || resolvedUrl.length() == 0) {
            return;
        }
        synchronized (durationProbeLock) {
            Integer cached = durationProbeCache.get(resolvedUrl);
            if (cached != null && cached.intValue() > 0) {
                item.durationMs = cached.intValue();
                return;
            }
            if (durationProbeRunning.contains(resolvedUrl)) {
                return;
            }
            durationProbeRunning.add(resolvedUrl);
        }

        AsyncTask<Void, Void, Integer> task = new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... voids) {
                return probeDurationMs(resolvedUrl);
            }

            @Override
            protected void onPostExecute(Integer result) {
                int duration = result == null ? 0 : result.intValue();
                synchronized (durationProbeLock) {
                    durationProbeRunning.remove(resolvedUrl);
                    if (duration > 0) {
                        durationProbeCache.put(resolvedUrl, duration);
                    }
                }
                if (duration <= 0) {
                    return;
                }
                boolean changed = false;
                for (int i = 0; i < items.size(); i++) {
                    MusicItem one = items.get(i);
                    if (one == null || one.durationMs > 0 || one.songUrl == null) {
                        continue;
                    }
                    String oneResolved = MediaUrlResolver.resolve(one.songUrl);
                    if (resolvedUrl.equals(oneResolved)) {
                        one.durationMs = duration;
                        changed = true;
                    }
                }
                if (changed) {
                    adapter.notifyDataSetChanged();
                    scheduleHomeSectionsRefresh();
                }
            }
        };
        if (Build.VERSION.SDK_INT >= 11) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
        } else {
            task.execute((Void[]) null);
        }
    }

    private int probeDurationMs(String url) {
        if (url == null || url.length() == 0) {
            return 0;
        }
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            if (Build.VERSION.SDK_INT >= 14) {
                retriever.setDataSource(url, new HashMap<String, String>());
            } else {
                retriever.setDataSource(url);
            }
            String durationText = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long raw = parseDurationNumber(durationText);
            int parsed = normalizeDurationToMs(raw, "duration_ms");
            if (parsed > 0) {
                return parsed;
            }
            return parseDurationTextToMs(durationText);
        } catch (Throwable t) {
            return 0;
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    private int parseDurationTextToMs(String text) {
        if (text == null) {
            return 0;
        }
        String raw = text.trim();
        if (raw.length() == 0) {
            return 0;
        }
        String[] parts = raw.split(":");
        if (parts.length < 2 || parts.length > 3) {
            return 0;
        }
        try {
            int seconds = 0;
            for (int i = 0; i < parts.length; i++) {
                String one = parts[i] == null ? "" : parts[i].trim();
                if (one.length() == 0) {
                    return 0;
                }
                int value;
                if (i == parts.length - 1 && one.contains(".")) {
                    value = (int) Double.parseDouble(one);
                } else {
                    value = Integer.parseInt(one);
                }
                if (value < 0) {
                    return 0;
                }
                seconds = seconds * 60 + value;
            }
            if (seconds <= 0) {
                return 0;
            }
            long ms = seconds * 1000L;
            if (ms > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return (int) ms;
        } catch (Exception e) {
            return 0;
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private static class MusicItem {
        String id;
        String name;
        String songUrl;
        String coverUrl;
        long sizeBytes;
        int durationMs;
        String ownerUid;
        String ownerName;
        String ownerTitle;
        String ownerAvatar;
        int likes;
        int comments;
        int playCount;
        boolean liked;
        boolean canDelete;
    }

    private class MusicAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            if (position < 0 || position >= items.size()) {
                return null;
            }
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(MusicPlazaActivity.this)
                        .inflate(R.layout.item_music_plaza, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final MusicItem item = (MusicItem) getItem(position);
            if (item == null) {
                return convertView;
            }
            holder.tvName.setText(item.name == null || item.name.length() == 0 ? "未命名歌曲" : item.name);
            String owner = item.ownerName;
            if (owner == null || owner.length() == 0) {
                owner = item.ownerUid;
            }
            if (owner == null || owner.length() == 0) {
                owner = "匿名用户";
            }
            String title = item.ownerTitle == null ? "" : item.ownerTitle.trim();
            String ownerPrefix = item.canDelete ? "我上传 · " : "上传者 · ";
            if (title.length() > 0) {
                holder.tvOwner.setText(ownerPrefix + owner + " · " + title);
            } else {
                holder.tvOwner.setText(ownerPrefix + owner);
            }

            String likedText = item.liked ? "已赞" : "赞";
            ensureDurationResolved(item);
            holder.tvDuration.setText(formatDuration(item.durationMs));
            holder.tvMeta.setText("大小 " + formatSize(item.sizeBytes));
            holder.tvStats.setText(likedText + " " + item.likes + " · 评论 " + item.comments + " · 播放 " + item.playCount);
            holder.tvTag.setText(item.canDelete ? "我的上传" : "广场");
            holder.tvTag.setBackgroundResource(item.canDelete ? R.drawable.bg_music_plaza_tag_positive : R.drawable.bg_music_plaza_tag_neutral);
            holder.tvTag.setTextColor(getResources().getColor(item.canDelete ? R.color.color_text_primary : R.color.color_text_secondary));
            holder.btnPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playItem(item);
                }
            });
            holder.btnMore.setText(item.canDelete ? "管理" : "更多");
            holder.btnMore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showItemActions(item);
                }
            });
            if (item.coverUrl != null && item.coverUrl.length() > 0) {
                ImageLoader.load(holder.ivCover, item.coverUrl);
            } else if (item.ownerAvatar != null && item.ownerAvatar.length() > 0) {
                ImageLoader.load(holder.ivCover, item.ownerAvatar);
            } else {
                holder.ivCover.setImageResource(R.drawable.ic_avatar_placeholder);
            }
            return convertView;
        }
    }

    private static class ViewHolder {
        final ImageView ivCover;
        final TextView tvName;
        final TextView tvDuration;
        final TextView tvOwner;
        final TextView tvMeta;
        final TextView tvStats;
        final TextView tvTag;
        final TextView btnPlay;
        final TextView btnMore;

        ViewHolder(View root) {
            ivCover = root.findViewById(R.id.ivMusicPlazaCover);
            tvName = root.findViewById(R.id.tvMusicPlazaName);
            tvDuration = root.findViewById(R.id.tvMusicPlazaDuration);
            tvOwner = root.findViewById(R.id.tvMusicPlazaOwner);
            tvMeta = root.findViewById(R.id.tvMusicPlazaMeta);
            tvStats = root.findViewById(R.id.tvMusicPlazaStats);
            tvTag = root.findViewById(R.id.tvMusicPlazaTag);
            btnPlay = root.findViewById(R.id.btnMusicPlazaPlay);
            btnMore = root.findViewById(R.id.btnMusicPlazaMore);
        }
    }
}

