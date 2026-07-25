package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
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
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MusicManageActivity extends BaseActivity {
    private static final int PAGE_SIZE = 50;

    private EditText etKeyword;
    private TextView btnSearch;
    private TextView btnBatchDelete;
    private TextView tvSummary;
    private TextView tvPageInfo;
    private TextView btnPrev;
    private TextView btnNext;
    private TextView tvEmpty;
    private ListView lv;

    private String token;
    private boolean allListMode = false;
    private final List<MusicItem> items = new ArrayList<MusicItem>();
    private MusicAdapter adapter;
    private int currentOffset = 0;
    private int totalCount = 0;
    private boolean hasMore = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_manage);

        allListMode = getIntent() != null && getIntent().getBooleanExtra("all_list_mode", false);

        SharedPreferences prefs = getSharedPreferences("auth", Context.MODE_PRIVATE);
        token = prefs.getString("access_token", "");

        View back = findViewByIdCompat(R.id.btnMusicManageBack);
        TextView tvTitle = findViewByIdCompat(R.id.tvMusicManageTitle);
        etKeyword = findViewByIdCompat(R.id.etMusicManageKeyword);
        btnSearch = findViewByIdCompat(R.id.btnMusicManageSearch);
        btnBatchDelete = findViewByIdCompat(R.id.btnMusicManageBatchDelete);
        tvSummary = findViewByIdCompat(R.id.tvMusicManageSummary);
        tvPageInfo = findViewByIdCompat(R.id.tvMusicManagePageInfo);
        btnPrev = findViewByIdCompat(R.id.btnMusicManagePrev);
        btnNext = findViewByIdCompat(R.id.btnMusicManageNext);
        tvEmpty = findViewByIdCompat(R.id.tvMusicManageEmpty);
        lv = findViewByIdCompat(R.id.lvMusicManage);

        if (allListMode) {
            if (tvTitle != null) {
                tvTitle.setText("全部歌曲");
            }
            if (btnBatchDelete != null) {
                btnBatchDelete.setVisibility(View.GONE);
            }
            if (tvEmpty != null) {
                tvEmpty.setText("暂无歌曲");
            }
        }

        if (back != null) {
            back.setOnClickListener(new View.OnClickListener() {
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
                showItemActions(items.get(position));
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
        if (btnBatchDelete != null) {
            btnBatchDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (allListMode) {
                        return;
                    }
                    deleteCurrentPage();
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

        loadItems();
    }

    private void loadItems() {
        if (token == null || token.length() == 0) {
            Toast.makeText(this, getString(R.string.error_not_logged_in), Toast.LENGTH_SHORT).show();
            return;
        }
        final String q = etKeyword == null || etKeyword.getText() == null
                ? "" : etKeyword.getText().toString().trim();
        StringBuilder path;
        if (allListMode) {
            path = new StringBuilder("/music/plaza?limit=").append(PAGE_SIZE)
                    .append("&offset=").append(currentOffset)
                    .append("&sort=latest");
        } else {
            path = new StringBuilder("/music/plaza/mine?limit=").append(PAGE_SIZE)
                    .append("&offset=").append(currentOffset);
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
                            item.ownerUid = one.optString("owner_uid", "");
                            item.ownerName = one.optString("owner_name", "");
                            item.ownerTitle = one.optString("owner_title", "");
                            item.ownerAvatar = one.optString("owner_avatar", "");
                            item.sizeBytes = one.optLong("size_bytes", 0);
                            item.durationMs = parseDurationMs(one);
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
                    updatePagerUi();
                } catch (Exception e) {
                    Toast.makeText(MusicManageActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(int code, String error) {
                if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                    return;
                }
                Toast.makeText(MusicManageActivity.this, "加载失败: " + code, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updatePagerUi() {
        if (tvSummary != null) {
            int likedCount = 0;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) != null && items.get(i).liked) {
                    likedCount++;
                }
            }
            if (allListMode) {
                tvSummary.setText("全部歌曲 " + totalCount + " 首 · 当前页 " + items.size() + " 首 · 已点赞 " + likedCount + " 首");
            } else {
                tvSummary.setText("我的上传 " + totalCount + " 首 · 当前页 " + items.size() + " 首 · 已点赞 " + likedCount + " 首");
            }
        }
        if (tvPageInfo != null) {
            int from = totalCount <= 0 ? 0 : (currentOffset + 1);
            int to = Math.min(totalCount, currentOffset + items.size());
            tvPageInfo.setText("第 " + from + " - " + to + " 条，共 " + totalCount + " 条");
        }
        if (btnPrev != null) {
            btnPrev.setEnabled(currentOffset > 0);
            ViewCompat.setAlpha(btnPrev, currentOffset > 0 ? 1f : 0.55f);
        }
        if (btnNext != null) {
            btnNext.setEnabled(hasMore);
            ViewCompat.setAlpha(btnNext, hasMore ? 1f : 0.55f);
        }
        if (btnBatchDelete != null && !allListMode) {
            boolean canDelete = !items.isEmpty();
            btnBatchDelete.setEnabled(canDelete);
            ViewCompat.setAlpha(btnBatchDelete, canDelete ? 1f : 0.55f);
        }
    }

    private void showItemActions(final MusicItem item) {
        if (item == null) {
            return;
        }
        final ArrayList<String> actions = new ArrayList<String>();
        actions.add("播放");
        actions.add("评论");
        actions.add("分享到聊天");
        if (allListMode) {
            actions.add(item.liked ? "取消点赞" : "点赞");
            actions.add("收藏");
            if (item.canDelete) {
                actions.add("删除");
            }
        } else {
            actions.add("删除");
        }
        final CharSequence[] array = actions.toArray(new CharSequence[actions.size()]);
        new AlertDialog.Builder(this)
                .setTitle(item.name == null || item.name.length() == 0 ? "歌曲管理" : item.name)
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
                        if ("评论".equals(action)) {
                            openComments(item);
                            return;
                        }
                        if ("分享到聊天".equals(action)) {
                            shareMusicItem(item);
                            return;
                        }
                        if ("点赞".equals(action) || "取消点赞".equals(action)) {
                            toggleLike(item);
                            return;
                        }
                        if ("收藏".equals(action)) {
                            String owner = item.ownerName;
                            if (owner == null || owner.length() == 0) {
                                owner = item.ownerUid;
                            }
                            FavoriteHelper.addMusicFavorite(MusicManageActivity.this,
                                    item.id,
                                    item.name,
                                    owner,
                                    item.songUrl,
                                    item.coverUrl);
                            return;
                        }
                        if ("删除".equals(action)) {
                            deleteMusicItem(item);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteCurrentPage() {
        if (items.isEmpty()) {
            Toast.makeText(this, "当前页没有可删除歌曲", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("批量删除")
                .setMessage("确定删除当前页全部 " + items.size() + " 首歌曲吗？")
                .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        try {
                            JSONArray itemIDs = new JSONArray();
                            for (int i = 0; i < items.size(); i++) {
                                MusicItem item = items.get(i);
                                if (item != null && item.id != null && item.id.length() > 0) {
                                    itemIDs.put(item.id);
                                }
                            }
                            if (itemIDs.length() == 0) {
                                Toast.makeText(MusicManageActivity.this, "没有可删除项", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            JSONObject json = new JSONObject();
                            json.put("item_ids", itemIDs);
                            HttpUtil.post("/music/plaza/mine/delete-batch", json, token, new HttpUtil.Callback() {
                                @Override
                                public void onSuccess(String response) {
                                    int deleted = 0;
                                    try {
                                        JSONObject obj = new JSONObject(response);
                                        deleted = obj.optInt("deleted", 0);
                                    } catch (Exception e) {
                                    }
                                    Toast.makeText(MusicManageActivity.this,
                                            "已删除 " + deleted + " 首",
                                            Toast.LENGTH_SHORT).show();
                                    loadItems();
                                }

                                @Override
                                public void onError(int code, String error) {
                                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                                        return;
                                    }
                                    Toast.makeText(MusicManageActivity.this, "批量删除失败", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } catch (Exception e) {
                            Toast.makeText(MusicManageActivity.this, "批量删除失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteMusicItem(final MusicItem item) {
        if (item == null || !item.canDelete || TextUtils.isEmpty(item.id)) {
            if (allListMode) {
                Toast.makeText(this, "只能删除自己发布的歌曲", Toast.LENGTH_SHORT).show();
            }
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
                                    Toast.makeText(MusicManageActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                                    loadItems();
                                }

                                @Override
                                public void onError(int code, String error) {
                                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                                        return;
                                    }
                                    Toast.makeText(MusicManageActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } catch (Exception e) {
                            Toast.makeText(MusicManageActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
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

    private void playItem(MusicItem item) {
        if (item == null || item.songUrl == null || item.songUrl.length() == 0) {
            Toast.makeText(this, "歌曲链接不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        reportPlay(item);
        Intent intent = new Intent(this, MusicPlayerActivity.class);
        intent.putExtra("song_name", item.name == null ? "" : item.name);
        intent.putExtra("song_url", item.songUrl == null ? "" : item.songUrl);
        intent.putExtra("cover_url", item.coverUrl == null ? "" : item.coverUrl);
        intent.putExtra("owner_uid", item.ownerUid == null ? "" : item.ownerUid);
        intent.putExtra("owner_name", item.ownerName == null ? "" : item.ownerName);
        intent.putExtra("owner_title", item.ownerTitle == null ? "" : item.ownerTitle);
        intent.putExtra("owner_avatar", item.ownerAvatar == null ? "" : item.ownerAvatar);
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
                        item.playCount = obj.optInt("play_count", item.playCount + 1);
                    } catch (Exception e) {
                        item.playCount = item.playCount + 1;
                    }
                    adapter.notifyDataSetChanged();
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
                    Toast.makeText(MusicManageActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "操作失败", Toast.LENGTH_SHORT).show();
        }
    }

    private int parseDurationMs(JSONObject obj) {
        if (obj == null) {
            return 0;
        }
        long raw = obj.optLong("duration_ms", 0);
        if (raw <= 0) {
            raw = obj.optLong("duration", 0);
        }
        if (raw <= 0) {
            raw = obj.optLong("duration_sec", 0);
            if (raw > 0) {
                raw = raw * 1000L;
            }
        }
        if (raw <= 0) {
            String text = obj.optString("duration_text", "");
            int parsedText = parseDurationTextToMs(text);
            if (parsedText > 0) {
                return parsedText;
            }
        }
        if (raw <= 0) {
            JSONObject stats = obj.optJSONObject("stats");
            if (stats != null) {
                raw = stats.optLong("duration_ms", 0);
                if (raw <= 0) {
                    raw = stats.optLong("duration", 0);
                }
                if (raw <= 0) {
                    raw = stats.optLong("duration_sec", 0);
                    if (raw > 0) {
                        raw = raw * 1000L;
                    }
                }
                if (raw <= 0) {
                    int parsedStatsText = parseDurationTextToMs(stats.optString("duration_text", ""));
                    if (parsedStatsText > 0) {
                        return parsedStatsText;
                    }
                }
            }
        }
        if (raw <= 0) {
            return 0;
        }
        if (raw < 1000) {
            raw = raw * 1000L;
        }
        if (raw > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) raw;
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

    private static class MusicItem {
        String id;
        String name;
        String songUrl;
        String coverUrl;
        String ownerUid;
        String ownerName;
        String ownerTitle;
        String ownerAvatar;
        long sizeBytes;
        int durationMs;
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
                convertView = LayoutInflater.from(MusicManageActivity.this)
                        .inflate(R.layout.item_music_manage, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final MusicItem item = (MusicItem) getItem(position);
            if (item == null) {
                return convertView;
            }
            String name = item.name == null || item.name.length() == 0 ? "未命名歌曲" : item.name;
            holder.tvName.setText(name);
            String owner = item.ownerName;
            if (owner == null || owner.length() == 0) {
                owner = item.ownerUid;
            }
            if (owner == null || owner.length() == 0) {
                owner = "我";
            }
            String title = item.ownerTitle == null ? "" : item.ownerTitle.trim();
            if (title.length() > 0) {
                holder.tvOwner.setText("上传者 · " + owner + " · " + title);
            } else {
                holder.tvOwner.setText("上传者 · " + owner);
            }
            holder.tvMeta.setText("时长 " + formatDuration(item.durationMs) + " · 大小 " + formatSize(item.sizeBytes));
            holder.tvStats.setText((item.liked ? "已赞 " : "赞 ") + item.likes + " · 评论 " + item.comments + " · 播放 " + item.playCount);

            holder.btnPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playItem(item);
                }
            });
            holder.btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteMusicItem(item);
                }
            });
            holder.btnMore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showItemActions(item);
                }
            });
            boolean canDelete = item.canDelete;
            if (allListMode && !canDelete) {
                holder.btnDelete.setVisibility(View.GONE);
            } else {
                holder.btnDelete.setVisibility(View.VISIBLE);
                holder.btnDelete.setEnabled(canDelete);
                ViewCompat.setAlpha(holder.btnDelete, canDelete ? 1f : 0.55f);
            }

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
        final TextView tvOwner;
        final TextView tvMeta;
        final TextView tvStats;
        final TextView btnPlay;
        final TextView btnDelete;
        final TextView btnMore;

        ViewHolder(View root) {
            ivCover = root.findViewById(R.id.ivMusicManageCover);
            tvName = root.findViewById(R.id.tvMusicManageName);
            tvOwner = root.findViewById(R.id.tvMusicManageOwner);
            tvMeta = root.findViewById(R.id.tvMusicManageMeta);
            tvStats = root.findViewById(R.id.tvMusicManageStats);
            btnPlay = root.findViewById(R.id.btnMusicManagePlay);
            btnDelete = root.findViewById(R.id.btnMusicManageDelete);
            btnMore = root.findViewById(R.id.btnMusicManageMore);
        }
    }
}
