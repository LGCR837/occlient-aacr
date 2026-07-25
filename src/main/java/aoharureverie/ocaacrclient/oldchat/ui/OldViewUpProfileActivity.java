package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.bili.BiliAuthStore;

public class OldViewUpProfileActivity extends BaseActivity {
    public static final String EXTRA_UP_MID = "up_mid";
    public static final String EXTRA_UP_NAME = "up_name";

    private ProgressBar pbLoading;
    private TextView tvTitle;
    private TextView tvEmpty;
    private TextView tvUpName;
    private TextView tvUpUid;
    private TextView btnFollow;
    private WebView webView;
    private long upMid;
    private String upName;
    private String accessToken;
    private String cookie;
    private boolean followed;
    private boolean followLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_old_view_up_profile);

        pbLoading = findViewByIdCompat(R.id.pbOldViewUpProfileLoading);
        tvTitle = findViewByIdCompat(R.id.tvOldViewUpProfileTitle);
        tvEmpty = findViewByIdCompat(R.id.tvOldViewUpProfileEmpty);
        tvUpName = findViewByIdCompat(R.id.tvOldViewUpProfileName);
        tvUpUid = findViewByIdCompat(R.id.tvOldViewUpProfileUid);
        btnFollow = findViewByIdCompat(R.id.btnOldViewUpProfileFollow);
        webView = findViewByIdCompat(R.id.wvOldViewUpProfile);

        View btnBack = findViewByIdCompat(R.id.btnOldViewUpProfileBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        readArgs();
        if (upMid <= 0L) {
            Toast.makeText(this, getString(R.string.old_view_up_profile_unavailable), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (tvTitle != null) {
            tvTitle.setText(resolveUpName());
        }
        if (tvUpName != null) {
            tvUpName.setText(resolveUpName());
        }
        if (tvUpUid != null) {
            tvUpUid.setText(getString(R.string.old_view_up_uid_prefix, String.valueOf(upMid)));
        }

        accessToken = BiliAuthStore.getAccessToken(this);
        cookie = BiliAuthStore.getCookies(this);
        if (btnFollow != null) {
            btnFollow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleFollow();
                }
            });
        }
        updateFollowButton();
        queryFollowState();
        bindWebView();
        loadUpPage();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.setWebChromeClient(null);
                webView.setWebViewClient(null);
                webView.removeAllViews();
                webView.destroy();
            } catch (Throwable ignored) {
            }
            webView = null;
        }
        super.onDestroy();
    }

    private void readArgs() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        upMid = intent.getLongExtra(EXTRA_UP_MID, 0L);
        upName = intent.getStringExtra(EXTRA_UP_NAME);
    }

    private void bindWebView() {
        if (webView == null) {
            return;
        }
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        trySetDisplayZoomControls(settings, false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (pbLoading == null) {
                    return;
                }
                if (newProgress >= 100) {
                    pbLoading.setVisibility(View.GONE);
                } else {
                    pbLoading.setVisibility(View.VISIBLE);
                }
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url == null || url.length() == 0) {
                    return true;
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    view.loadUrl(url);
                    return true;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (pbLoading != null) {
                    pbLoading.setVisibility(View.VISIBLE);
                }
                if (tvEmpty != null) {
                    tvEmpty.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (pbLoading != null) {
                    pbLoading.setVisibility(View.GONE);
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (pbLoading != null) {
                    pbLoading.setVisibility(View.GONE);
                }
                if (tvEmpty != null) {
                    tvEmpty.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void loadUpPage() {
        if (webView == null || upMid <= 0L) {
            return;
        }
        String url = "https://space.bilibili.com/" + upMid;
        webView.loadUrl(url);
    }

    private void trySetDisplayZoomControls(WebSettings settings, boolean show) {
        if (settings == null) {
            return;
        }
        try {
            java.lang.reflect.Method method = WebSettings.class.getMethod("setDisplayZoomControls", Boolean.TYPE);
            method.invoke(settings, Boolean.valueOf(show));
        } catch (Throwable ignored) {
        }
    }

    private String resolveUpName() {
        if (upName != null && upName.length() > 0) {
            return upName;
        }
        return getString(R.string.old_view_up_profile_title);
    }

    private void queryFollowState() {
        if (upMid <= 0) {
            return;
        }
        setFollowLoading(true);
        OldViewUpProfileActionHelper.queryFollowState(upMid, accessToken, cookie,
                new OldViewUpProfileActionHelper.FollowStateCallback() {
                    @Override
                    public void onDone(boolean ok, String msg, boolean isFollowed) {
                        setFollowLoading(false);
                        if (ok) {
                            followed = isFollowed;
                            updateFollowButton();
                            return;
                        }
                        updateFollowButton();
                    }
                });
    }

    private void toggleFollow() {
        if (followLoading) {
            return;
        }
        final boolean targetFollow = !followed;
        setFollowLoading(true);
        OldViewUpProfileActionHelper.toggleFollow(upMid, targetFollow, accessToken, cookie,
                new OldViewUpProfileActionHelper.ToggleFollowCallback() {
                    @Override
                    public void onDone(boolean ok, String msg, boolean followedAfter) {
                        setFollowLoading(false);
                        if (ok) {
                            followed = followedAfter;
                            updateFollowButton();
                            if (msg != null && msg.length() > 0) {
                                Toast.makeText(OldViewUpProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }
                        if (msg == null || msg.length() == 0) {
                            msg = getString(R.string.old_view_up_follow_toggle_failed);
                        }
                        Toast.makeText(OldViewUpProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
                        updateFollowButton();
                    }
                });
    }

    private void setFollowLoading(boolean loading) {
        followLoading = loading;
        updateFollowButton();
    }

    private void updateFollowButton() {
        if (btnFollow == null) {
            return;
        }
        if (followLoading) {
            btnFollow.setText(getString(R.string.old_view_up_following));
            btnFollow.setEnabled(false);
            ViewCompat.setAlpha(btnFollow, 0.7f);
            return;
        }
        btnFollow.setText(getString(followed ? R.string.old_view_up_followed : R.string.old_view_up_follow));
        btnFollow.setEnabled(true);
        ViewCompat.setAlpha(btnFollow, 1f);
    }
}
