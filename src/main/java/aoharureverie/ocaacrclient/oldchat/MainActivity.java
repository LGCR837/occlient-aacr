package aoharureverie.ocaacrclient.oldchat;

import android.support.v7.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.ViewPager;
import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.api.WSManager;
import aoharureverie.ocaacrclient.oldchat.data.FriendRequestStore;
import aoharureverie.ocaacrclient.oldchat.data.NotificationReadStore;
import aoharureverie.ocaacrclient.oldchat.ui.LoginActivity;
import aoharureverie.ocaacrclient.oldchat.ui.TopStatusBar;
import aoharureverie.ocaacrclient.oldchat.ui.fragments.ChatsFragment;
import aoharureverie.ocaacrclient.oldchat.ui.fragments.DiscoverFragment;
import aoharureverie.ocaacrclient.oldchat.ui.fragments.FriendsFragment;
import aoharureverie.ocaacrclient.oldchat.ui.fragments.ProfileFragment;
import aoharureverie.ocaacrclient.oldchat.util.NetworkStateManager;
import aoharureverie.ocaacrclient.oldchat.util.UpdateManager;
import aoharureverie.ocaacrclient.oldchat.util.AvatarSyncManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseActivity {

    private LinearLayout tabLayout;
    private ViewPager viewPager;
    private final List<View> tabViews = new ArrayList<View>();
    private boolean hasCheckedNotifications = false;
    private TopStatusBar topStatusBar;
    private View friendsBadge;
    private boolean refreshingRequests = false;
    private boolean skipLogin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        skipLogin = getIntent() != null && getIntent().getBooleanExtra("skip_login", false);

        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        String token = prefs.getString("access_token", null);
        if (token == null) {
            token = prefs.getString("token", null);
        }

        if (token == null && !skipLogin) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        WSManager.getInstance().start(this);

        topStatusBar = findViewByIdCompat(R.id.topStatusBar);
        if (topStatusBar != null) {
            topStatusBar.setOnRetryClickListener(new TopStatusBar.RetryClickListener() {
                @Override
                public void onRetry() {
                    if (!skipLogin) {
                        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
                        String token = prefs.getString("access_token", null);
                        if (token == null) {
                            token = prefs.getString("token", null);
                        }
                        if (token == null || token.length() == 0) {
                            startActivity(new Intent(MainActivity.this, LoginActivity.class));
                            finish();
                            return;
                        }
                    }
                    WSManager.getInstance().start(MainActivity.this);
                    refreshChatHome();
                    refreshFriendRequestsBadge();
                    checkSystemNotifications();
                }
            });
        }
        viewPager = findViewByIdCompat(R.id.viewpager);
        setupViewPager(viewPager);

        tabLayout = findViewByIdCompat(R.id.tabs);
        setupTabViews();

        if (token != null) {
            AvatarSyncManager.syncAll(this, token);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        WSManager.getInstance().start(this);
        refreshChatHome();
        refreshFriendRequestsBadge();

        // 每次回到主界面都检测更新（但有24小时间隔限制）
        UpdateManager.check(this);

        // 只在第一次onResume时检查通知
        if (!hasCheckedNotifications) {
            hasCheckedNotifications = true;
            checkSystemNotifications();
        }
    }

    private void setupViewPager(ViewPager viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new ChatsFragment(), "聊天");
        adapter.addFragment(new FriendsFragment(), "好友");
        adapter.addFragment(new DiscoverFragment(), "发现");
        adapter.addFragment(new ProfileFragment(), "我的");
        viewPager.setAdapter(adapter);
    }

    private void refreshChatHome() {
        if (viewPager == null) {
            return;
        }
        String tag = "android:switcher:" + viewPager.getId() + ":0";
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if (fragment instanceof ChatsFragment) {
            ((ChatsFragment) fragment).refreshRecents();
        }
    }

    private void setupTabViews() {
        tabLayout.removeAllViews();
        tabViews.clear();
        setupTabView(0, R.drawable.ic_tab_chat_alt, "聊天");
        setupTabView(1, R.drawable.ic_tab_friends, "好友");
        setupTabView(2, R.drawable.news, "发现");
        setupTabView(3, R.drawable.ic_tab_profile, "我的");
        updateTabSelection(viewPager.getCurrentItem());
        updateFriendsBadge(FriendRequestStore.getPendingCount(this));
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                updateTabSelection(position);
            }
        });
    }

    private void setupTabView(int position, int iconRes, String text) {
        View view = LayoutInflater.from(this).inflate(R.layout.view_tab_item, tabLayout, false);
        ImageView icon = (ImageView) view.findViewById(R.id.tabIcon);
        TextView label = (TextView) view.findViewById(R.id.tabLabel);
        View badge = (View) view.findViewById(R.id.tabBadge);
        icon.setImageDrawable(getResources().getDrawable(iconRes));
        label.setText(text);
        ColorStateList tint = getResources().getColorStateList(R.color.tab_icon_tint);
        if (tint != null) {
            label.setTextColor(tint);
            applyIconTint(icon, tint);
        }
        view.setClickable(true);
        view.setFocusable(true);
        final int tabIndex = position;
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (viewPager != null) {
                    viewPager.setCurrentItem(tabIndex, false);
                }
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
        );
        tabLayout.addView(view, params);
        tabViews.add(view);
        if (position == 1) {
            friendsBadge = badge;
        }
    }

    private void updateTabSelection(int selectedPosition) {
        for (int i = 0; i < tabViews.size(); i++) {
            setTabSelected(tabViews.get(i), i == selectedPosition);
        }
    }

    private void setTabSelected(View tabView, boolean selected) {
        if (tabView == null) {
            return;
        }
        ImageView icon = (ImageView) tabView.findViewById(R.id.tabIcon);
        TextView label = (TextView) tabView.findViewById(R.id.tabLabel);
        View indicator = (View) tabView.findViewById(R.id.tabIndicator);
        tabView.setSelected(selected);
        if (icon != null) {
            icon.setSelected(selected);
        }
        if (label != null) {
            label.setSelected(selected);
            label.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        if (indicator != null) {
            indicator.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void applyIconTint(ImageView icon, ColorStateList tint) {
        if (icon == null || tint == null) {
            return;
        }
        Drawable src = icon.getDrawable();
        if (src == null) {
            return;
        }
        Drawable wrapped = DrawableCompat.wrap(src.mutate());
        DrawableCompat.setTintList(wrapped, tint);
        icon.setImageDrawable(wrapped);
    }

    public void updateFriendsBadge(int count) {
        if (friendsBadge == null) {
            return;
        }
        friendsBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    private void refreshFriendRequestsBadge() {
        if (refreshingRequests) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        final String token = prefs.getString("access_token", "");
        if (token == null || token.isEmpty() || !NetworkStateManager.getInstance().isServerAvailable()) {
            updateFriendsBadge(FriendRequestStore.getPendingCount(this));
            return;
        }
        refreshingRequests = true;
        if (topStatusBar != null) {
            topStatusBar.setLoading(true);
        }
        HttpUtil.get("/friends/requests", token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                refreshingRequests = false;
                if (topStatusBar != null) {
                    topStatusBar.setLoading(false);
                }
                int count = 0;
                try {
                    JSONObject obj = new JSONObject(response);
                    JSONArray arr = obj.getJSONArray("requests");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject rObj = arr.getJSONObject(i);
                        if (rObj.optInt("status", 1) == 0) {
                            count++;
                        }
                    }
                } catch (Exception e) {
                    count = FriendRequestStore.getPendingCount(MainActivity.this);
                }
                FriendRequestStore.setPendingCount(MainActivity.this, count);
                updateFriendsBadge(count);
            }

            @Override
            public void onError(int code, String error) {
                refreshingRequests = false;
                if (topStatusBar != null) {
                    topStatusBar.setLoading(false);
                }
                updateFriendsBadge(FriendRequestStore.getPendingCount(MainActivity.this));
            }
        });
    }

    private void checkSystemNotifications() {
        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        String token = prefs.getString("access_token", "");

        HttpUtil.get("/notifications?limit=1", token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject data = new JSONObject(response);
                    JSONArray arr = data.getJSONArray("notifications");

                    if (arr.length() > 0) {
                        JSONObject notification = arr.getJSONObject(0);
                        String id = notification.optString("id", "");
                        String title = notification.optString("title", "");
                        String body = notification.optString("body", "");
                        boolean important = notification.optBoolean("important", false);
                        final String idFinal = id;
                        final String titleFinal = title;
                        final String bodyFinal = body;
                        final boolean importantFinal = important;

                        // 普通通知：已读就不显示
                        // 重要通知：总是显示，除非已明确标记不再提醒
                        if (!id.isEmpty() && !NotificationReadStore.isRead(MainActivity.this, id)) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    showNotificationDialog(idFinal, titleFinal, bodyFinal, importantFinal);
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(int code, String error) {
                // 静默失败，不影响用户体验
            }
        });
    }

    private void showNotificationDialog(String notificationId, String title, String body, boolean important) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppDialogTheme);
        final String notificationIdFinal = notificationId;

        if (title != null && !title.isEmpty()) {
            builder.setTitle(title);
        } else {
            builder.setTitle("系统通知");
        }

        builder.setMessage(body);
        builder.setCancelable(true);

        if (important) {
            // 重要通知：需要勾选"不再提醒"
            final boolean[] dontShowAgain = {false};

            android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
            android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            layout.setPadding(50, 20, 50, 0);

            android.widget.TextView tvBody = new android.widget.TextView(this);
            tvBody.setText(body);
            tvBody.setTextSize(15);
            tvBody.setLineSpacing(0, 1.2f);
            layout.addView(tvBody);

            android.widget.CheckBox checkbox = new android.widget.CheckBox(this);
            checkbox.setText("不再提醒");
            android.widget.LinearLayout.LayoutParams cbParams = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            cbParams.topMargin = 20;
            checkbox.setLayoutParams(cbParams);
            checkbox.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                    dontShowAgain[0] = isChecked;
                }
            });
            layout.addView(checkbox);

            scrollView.addView(layout);
            builder.setView(scrollView);
            builder.setPositiveButton("知道了", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    if (dontShowAgain[0]) {
                        NotificationReadStore.markAsRead(MainActivity.this, notificationIdFinal);
                    }
                    dialog.dismiss();
                }
            });
        } else {
            // 普通通知：点击"知道了"即标记为已读
            builder.setPositiveButton("知道了", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    NotificationReadStore.markAsRead(MainActivity.this, notificationIdFinal);
                    dialog.dismiss();
                }
            });
        }

        final AlertDialog dialog = builder.create();
        dialog.setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(android.content.DialogInterface d) {
                NotificationReadStore.markAsRead(MainActivity.this, notificationIdFinal);
            }
        });
        dialog.show();
    }

    class ViewPagerAdapter extends FragmentPagerAdapter {
        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();

        public ViewPagerAdapter(FragmentManager manager) {
            super(manager);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        public void addFragment(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mFragmentTitleList.get(position);
        }
    }
}
