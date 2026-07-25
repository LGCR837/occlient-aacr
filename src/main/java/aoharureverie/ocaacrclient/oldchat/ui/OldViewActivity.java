package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.bili.BiliModels;

public class OldViewActivity extends OldViewActivitySupport1 {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_old_view);
        bindViews();
        initList();
        initActions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        logStep("onResume: ensureLoginState");
        ensureLoginState();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
    }

    protected void bindViews() {
        ivQr = findViewByIdCompat(R.id.ivOldViewQr);
        tvStatus = findViewByIdCompat(R.id.tvOldViewStatus);
        tvLoginTitle = findViewByIdCompat(R.id.tvOldViewLoginTitle);
        panelLogin = findViewByIdCompat(R.id.panelOldViewLogin);
        btnMenu = findViewByIdCompat(R.id.btnOldViewMenu);
        btnSearch = findViewByIdCompat(R.id.btnOldViewSearch);
        btnRefresh = findViewByIdCompat(R.id.btnOldViewRefresh);
        pbLoading = findViewByIdCompat(R.id.pbOldViewLoading);
        lvVideos = findViewByIdCompat(R.id.lvOldViewVideos);
        tvEmpty = findViewByIdCompat(R.id.tvOldViewEmpty);
        if (lvVideos != null && tvEmpty != null) {
            lvVideos.setEmptyView(tvEmpty);
        }
    }

    protected void initList() {
        adapter = new OldViewVideoAdapter(this);
        adapter.setOnVideoActionListener(new OldViewVideoAdapter.OnVideoActionListener() {
            @Override
            public void onOpenUpProfile(BiliModels.RecommendItem item) {
                openUpProfile(item);
            }
        });
        if (lvVideos == null) {
            return;
        }
        lvVideos.setAdapter(adapter);
        lvVideos.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Object obj = adapter != null ? adapter.getItem(position) : null;
                if (!(obj instanceof aoharureverie.ocaacrclient.oldchat.bili.BiliModels.RecommendItem)) {
                    return;
                }
                aoharureverie.ocaacrclient.oldchat.bili.BiliModels.RecommendItem item = (aoharureverie.ocaacrclient.oldchat.bili.BiliModels.RecommendItem) obj;
                openDetail(item);
            }
        });
        lvVideos.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(android.widget.AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!searchMode || !searchHasMore || searchLoading) {
                    return;
                }
                if (totalItemCount <= 0) {
                    return;
                }
                int last = firstVisibleItem + visibleItemCount;
                if (last >= totalItemCount - 2) {
                    loadSearch(searchPage + 1, false);
                }
            }
        });
    }

    protected void initActions() {
        View btnBack = findViewByIdCompat(R.id.btnOldViewBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
        if (btnMenu != null) {
            btnMenu.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showMenu(v);
                }
            });
        }
        if (btnSearch instanceof ImageView) {
            ((ImageView) btnSearch).setColorFilter(getResources().getColor(R.color.color_text_primary));
        }
        if (btnSearch != null) {
            btnSearch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showSearchDialog();
                }
            });
        }
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startLogin(true);
                }
            });
        }

        View btnQuickSearch = findViewByIdCompat(R.id.btnOldViewQuickSearch);
        if (btnQuickSearch != null) {
            btnQuickSearch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showSearchDialog();
                }
            });
        }
        View btnQuickHistory = findViewByIdCompat(R.id.btnOldViewQuickHistory);
        if (btnQuickHistory != null) {
            btnQuickHistory.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(OldViewActivity.this, OldViewHistoryActivity.class));
                }
            });
        }
        View btnQuickFavorites = findViewByIdCompat(R.id.btnOldViewQuickFavorites);
        if (btnQuickFavorites != null) {
            btnQuickFavorites.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(OldViewActivity.this, OldViewFavoritesActivity.class));
                }
            });
        }
    }

    private void openUpProfile(BiliModels.RecommendItem item) {
        long upMid = extractUpMid(item);
        if (upMid <= 0) {
            Toast.makeText(this, getString(R.string.old_view_up_profile_unavailable), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, OldViewUpProfileActivity.class);
        intent.putExtra(OldViewUpProfileActivity.EXTRA_UP_MID, upMid);
        if (item != null && item.args != null) {
            intent.putExtra(OldViewUpProfileActivity.EXTRA_UP_NAME, item.args.upName);
        }
        startActivity(intent);
    }

    private long extractUpMid(BiliModels.RecommendItem item) {
        if (item == null || item.args == null) {
            return 0L;
        }
        if (item.args.upId > 0) {
            return item.args.upId;
        }
        if (item.args.mid > 0) {
            return item.args.mid;
        }
        return 0L;
    }
}
