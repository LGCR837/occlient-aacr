package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageView;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.bili.BiliModels;

public class OldViewVideoDetailActivity extends OldViewVideoDetailSupport7 {
    private OldViewVideoActionController videoActionController;
    private long currentOwnerMid;
    private String currentOwnerName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_old_view_detail);
        logStep("onCreate");

        bindViews();
        videoActionController = new OldViewVideoActionController(this);
        setupHeaderAndList();
        setupActions();
        readIntentArgs();
        loadDetail();
        loadRelatedVideos(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (enteringFullscreen) {
            pausePlaybackForFullscreen();
            return;
        }
        stopPlayback(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (enteringFullscreen) {
            enteringFullscreen = false;
        }
        if (videoActionController != null) {
            videoActionController.refreshLikeState(true);
        }
    }

    @Override
    protected void onDestroy() {
        stopPlayback(false);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FULLSCREEN) {
            enteringFullscreen = false;
            int pos = data != null ? data.getIntExtra(OldViewVideoFullActivity.EXTRA_RESULT_POSITION, 0) : 0;
            boolean completed = data != null && data.getBooleanExtra(OldViewVideoFullActivity.EXTRA_COMPLETED, false);
            if (completed) {
                stopPlayback(true);
                return;
            }
            if (pos > 0) {
                lastPlaybackPos = pos;
            }
            if (currentPlayUrl != null && currentPlayUrl.length() > 0) {
                pausedByScroll = true;
                isPlaying = false;
                hasStarted = true;
                playUrl(currentPlayUrl);
            }
        }
    }

    @Override
    protected void bindDetail(BiliModels.VideoDetailData data) {
        super.bindDetail(data);
        bindOwnerProfileEntry(data);
        resetDescriptionState();
        loadRelatedVideos(false);
    }

    protected void bindViews() {
        pbLoading = findViewByIdCompat(R.id.pbOldViewDetailLoading);
        lvComments = findViewByIdCompat(R.id.lvOldViewDetailComments);
        tvEmpty = findViewByIdCompat(R.id.tvOldViewDetailEmpty);
        btnShare = findViewByIdCompat(R.id.btnOldViewDetailShare);
        etComment = findViewByIdCompat(R.id.etOldViewComment);
        btnSendComment = findViewByIdCompat(R.id.btnOldViewCommentSend);
        layoutCommentInput = findViewByIdCompat(R.id.layoutOldViewCommentInput);
        if (btnShare instanceof ImageView) {
            ((ImageView) btnShare).setColorFilter(getResources().getColor(R.color.color_text_primary));
        }
        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        token = prefs.getString("access_token", "");
    }

    protected void setupHeaderAndList() {
        if (lvComments != null) {
            headerView = getLayoutInflater().inflate(R.layout.view_old_view_detail_header, lvComments, false);
            lvComments.addHeaderView(headerView, null, false);
        }

        if (headerView == null) {
            return;
        }

        layoutPlayer = headerView.findViewById(R.id.layoutOldViewPlayer);
        ivCover = (ImageView) headerView.findViewById(R.id.ivOldViewDetailCover);
        vvPlayer = (android.widget.VideoView) headerView.findViewById(R.id.vvOldViewPlayer);
        btnPlay = headerView.findViewById(R.id.btnOldViewPlay);
        btnFullscreen = headerView.findViewById(R.id.btnOldViewFullscreen);
        btnRetry = headerView.findViewById(R.id.btnOldViewRetry);
        layoutPlayerLoading = headerView.findViewById(R.id.layoutOldViewPlayerLoading);
        pbPlayerLoading = (android.widget.ProgressBar) headerView.findViewById(R.id.pbOldViewPlayerLoading);
        tvPlayerHint = (android.widget.TextView) headerView.findViewById(R.id.tvOldViewPlayerHint);
        layoutOwner = headerView.findViewById(R.id.layoutOldViewOwner);
        ivOwnerAvatar = (ImageView) headerView.findViewById(R.id.ivOldViewDetailOwnerAvatar);
        tvOwnerName = (android.widget.TextView) headerView.findViewById(R.id.tvOldViewDetailOwnerName);
        tvOwnerTag = (android.widget.TextView) headerView.findViewById(R.id.tvOldViewDetailOwnerTag);
        tvVideoTitle = (android.widget.TextView) headerView.findViewById(R.id.tvOldViewDetailVideoTitle);
        tvMeta = (android.widget.TextView) headerView.findViewById(R.id.tvOldViewDetailMeta);
        layoutStats = headerView.findViewById(R.id.layoutOldViewStats);
        tvStatPlay = (android.widget.TextView) headerView.findViewById(R.id.tvOldViewDetailStatPlay);
        tvStatDanmaku = (android.widget.TextView) headerView.findViewById(R.id.tvOldViewDetailStatDanmaku);
        tvStatReply = (android.widget.TextView) headerView.findViewById(R.id.tvOldViewDetailStatReply);
        tvStatLike = (android.widget.TextView) headerView.findViewById(R.id.tvOldViewDetailStatLike);
        tvDesc = (android.widget.TextView) headerView.findViewById(R.id.tvOldViewDetailDesc);

        if (videoActionController != null) {
            videoActionController.bindHeader(headerView);
        }
        bindBottomTabs(headerView);

        commentAdapter = new OldViewCommentAdapter(this);
        bindCommentActions();
        relatedAdapter = new OldViewVideoAdapter(this);

        if (lvComments != null) {
            lvComments.setAdapter(relatedAdapter);
            lvComments.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (commentTabSelected) {
                        return;
                    }
                    Object obj = parent.getItemAtPosition(position);
                    if (obj instanceof BiliModels.RecommendItem) {
                        openRelatedVideo((BiliModels.RecommendItem) obj);
                    }
                }
            });
            lvComments.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    updatePlayerVisibility();
                    if (!commentTabSelected) {
                        return;
                    }
                    if (!hasMoreComments || isLoadingComments || commentPage <= 0 || totalItemCount <= 0) {
                        return;
                    }
                    int last = firstVisibleItem + visibleItemCount;
                    if (last >= totalItemCount - 2) {
                        loadComments(commentPage + 1, false);
                    }
                }
            });
        }
    }

    protected void readIntentArgs() {
        Intent intent = getIntent();
        if (intent != null) {
            currentBvid = intent.getStringExtra(EXTRA_BVID);
            currentAid = intent.getLongExtra(EXTRA_AID, 0L);
            currentCid = intent.getLongExtra(EXTRA_CID, 0L);
            preloadUrl = intent.getStringExtra(EXTRA_PRELOAD_URL);
            coverUrl = intent.getStringExtra(EXTRA_COVER);
            String title = intent.getStringExtra(EXTRA_TITLE);
            if (title != null && title.length() > 0 && tvVideoTitle != null) {
                tvVideoTitle.setText(title);
                currentTitle = title;
            }
        }
        if (!isValidBvid(currentBvid)) {
            currentBvid = null;
        }
        if (currentAid <= 0 && currentBvid != null) {
            long aidFromBvid = bvidToAid(currentBvid);
            if (aidFromBvid > 0) {
                currentAid = aidFromBvid;
                logStep("intent: computed aid=" + currentAid + " from bvid");
            }
        }
        logStep("intent: bvid=" + (currentBvid != null ? currentBvid : "") + " aid=" + currentAid
                + " cid=" + currentCid + " preload=" + (preloadUrl != null && preloadUrl.length() > 0));
        if (videoActionController != null) {
            videoActionController.onVideoMeta(currentAid, currentBvid, -1);
        }
    }

    @Override
    protected void bindStats(BiliModels.VideoDetailData data) {
        super.bindStats(data);
        if (data != null) {
            if (data.aid > 0) {
                currentAid = data.aid;
            }
            if (data.bvid != null && data.bvid.trim().length() > 0) {
                currentBvid = data.bvid.trim();
            }
            int likes = data.stat != null ? data.stat.like : -1;
            if (videoActionController != null) {
                videoActionController.onVideoMeta(currentAid, currentBvid, likes);
            }
        }
    }

    private void bindOwnerProfileEntry(BiliModels.VideoDetailData data) {
        currentOwnerMid = 0L;
        currentOwnerName = "";
        if (data != null && data.owner != null) {
            currentOwnerMid = data.owner.mid;
            currentOwnerName = data.owner.name != null ? data.owner.name : "";
        }
        if (layoutOwner == null) {
            return;
        }
        if (currentOwnerMid <= 0) {
            layoutOwner.setOnClickListener(null);
            layoutOwner.setClickable(false);
            if (ivOwnerAvatar != null) {
                ivOwnerAvatar.setOnClickListener(null);
            }
            if (tvOwnerName != null) {
                tvOwnerName.setOnClickListener(null);
            }
            if (tvOwnerTag != null) {
                tvOwnerTag.setOnClickListener(null);
            }
            return;
        }
        View.OnClickListener openOwner = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(OldViewVideoDetailActivity.this, OldViewUpProfileActivity.class);
                intent.putExtra(OldViewUpProfileActivity.EXTRA_UP_MID, currentOwnerMid);
                intent.putExtra(OldViewUpProfileActivity.EXTRA_UP_NAME, currentOwnerName);
                startActivity(intent);
            }
        };
        layoutOwner.setClickable(true);
        layoutOwner.setOnClickListener(openOwner);
        if (ivOwnerAvatar != null) {
            ivOwnerAvatar.setOnClickListener(openOwner);
        }
        if (tvOwnerName != null) {
            tvOwnerName.setOnClickListener(openOwner);
        }
        if (tvOwnerTag != null) {
            tvOwnerTag.setOnClickListener(openOwner);
        }
    }
}
