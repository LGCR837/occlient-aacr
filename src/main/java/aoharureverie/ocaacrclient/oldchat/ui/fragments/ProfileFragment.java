package aoharureverie.ocaacrclient.oldchat.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.ui.ProfileEditActivity;
import aoharureverie.ocaacrclient.oldchat.ui.FavoritesActivity;
import aoharureverie.ocaacrclient.oldchat.ui.LoginActivity;
import aoharureverie.ocaacrclient.oldchat.ui.SettingsActivity;
import aoharureverie.ocaacrclient.oldchat.api.WSManager;
import aoharureverie.ocaacrclient.oldchat.service.MessageService;
import aoharureverie.ocaacrclient.oldchat.util.AccountDataCleaner;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;
import org.json.JSONObject;

public class ProfileFragment extends Fragment {
    private static final String PROFILE_CACHE_PREFS = "profile_cache";
    private static final String PROFILE_CACHE_KEY = "me_profile_json";
    private ImageView ivAvatar;
    private TextView tvUser;
    private TextView tvTitleBadge;
    private TextView tvEmail;
    private TextView tvWalletBalance;
    private TextView tvReputationScore;
    private View profileCard;
    private View btnMySpace;
    private View btnMyFavorites;
    private View btnSettings;
    private View btnSwitchAccount;
    private View btnLogout;
    private String token;
    private String currentAvatarUrl;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        ivAvatar = view.findViewById(R.id.ivAvatar);
        tvUser = view.findViewById(R.id.tvMyUsername);
        tvTitleBadge = view.findViewById(R.id.tvMyTitleBadge);
        tvEmail = view.findViewById(R.id.tvMyEmail);
        tvWalletBalance = view.findViewById(R.id.tvWalletBalance);
        tvReputationScore = view.findViewById(R.id.tvReputationScore);
        profileCard = view.findViewById(R.id.profile_card);
        btnMySpace = view.findViewById(R.id.btnMySpace);
        btnMyFavorites = view.findViewById(R.id.btnMyFavorites);
        btnSettings = view.findViewById(R.id.btnSettings);
        btnSwitchAccount = view.findViewById(R.id.btnSwitchAccount);
        btnLogout = view.findViewById(R.id.btnLogout);

        SharedPreferences prefs = getActivity().getSharedPreferences("auth", Context.MODE_PRIVATE);
        token = prefs.getString("access_token", "");

        if (profileCard != null) {
            profileCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(getActivity(), ProfileEditActivity.class));
                }
            });
        }
        if (btnMySpace != null) {
            btnMySpace.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getActivity(), aoharureverie.ocaacrclient.oldchat.ui.UserSpaceActivity.class);
                    startActivity(intent);
                }
            });
        }
        if (btnMyFavorites != null) {
            btnMyFavorites.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(getActivity(), FavoritesActivity.class));
                }
            });
        }
        if (btnSettings != null) {
            btnSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(getActivity(), SettingsActivity.class));
                }
            });
        }
        if (btnSwitchAccount != null) {
            btnSwitchAccount.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(getActivity(), aoharureverie.ocaacrclient.oldchat.ui.AccountListActivity.class));
                }
            });
        }
        if (btnLogout != null) {
            btnLogout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getActivity() == null) return;
                    SharedPreferences prefs = getActivity().getSharedPreferences("auth", Context.MODE_PRIVATE);
                    prefs.edit().clear().apply();
                    AccountDataCleaner.clearAll(getActivity());
                    WSManager.getInstance().stop();
                    MessageService.stop(getActivity());
                    Intent intent = new Intent(getActivity(), LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    getActivity().finish();
                }
            });
        }

        loadProfile();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProfile();
    }

    private void loadProfile() {
        loadCachedProfile();
        HttpUtil.get("/me", token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    persistProfileCache(response);
                    applyProfile(response);
                } catch (Exception e) {
                    Toast.makeText(getActivity(), "加载资料失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(int code, String error) {
                if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                    return;
                }
                Toast.makeText(getActivity(), "加载资料失败: " + code, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadCachedProfile() {
        if (getActivity() == null) {
            return;
        }
        SharedPreferences prefs = getActivity().getSharedPreferences(PROFILE_CACHE_PREFS, Context.MODE_PRIVATE);
        String cached = prefs.getString(PROFILE_CACHE_KEY, "");
        if (cached == null || cached.isEmpty()) {
            return;
        }
        applyProfile(cached);
    }

    private void persistProfileCache(String payload) {
        if (getActivity() == null || payload == null) {
            return;
        }
        SharedPreferences prefs = getActivity().getSharedPreferences(PROFILE_CACHE_PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(PROFILE_CACHE_KEY, payload).apply();
    }

    private void applyProfile(String payload) {
        try {
            JSONObject obj = new JSONObject(payload);
            String uid = obj.optString("uid", "");
            String username = obj.optString("username", "");
            String displayName = obj.optString("display_name", "");
            String userTitle = obj.optString("user_title", "");
            currentAvatarUrl = obj.optString("avatar_url", "");
            String primary = displayName;
            if (primary == null || primary.isEmpty()) {
                primary = uid;
            }
            if (tvUser != null) {
                tvUser.setText(primary == null ? "" : primary);
            }
            aoharureverie.ocaacrclient.oldchat.ui.UserTitleBinder.bind(tvTitleBadge, userTitle);
            StringBuilder secondary = new StringBuilder();
            if (uid != null && !uid.isEmpty()) {
                secondary.append("UID: ").append(uid);
            }
            if (username != null && !username.isEmpty()) {
                if (secondary.length() > 0) {
                    secondary.append("  ");
                }
                secondary.append("用户名: ").append(username);
            }
            if (tvEmail != null) {
                tvEmail.setText(secondary.toString());
            }
            int balance = obj.optInt("coin_balance", 0);
            int reputationScore = obj.optInt("reputation_score", 0);
            if (tvWalletBalance != null) {
                String amountText = getString(R.string.profile_wallet_amount_format, balance);
                tvWalletBalance.setText(getString(R.string.wallet_balance_label) + ": " + amountText);
            }
            if (tvReputationScore != null) {
                String scoreText = getString(R.string.profile_reputation_format, reputationScore);
                tvReputationScore.setText(getString(R.string.profile_reputation_label) + ": " + scoreText);
            }
            ImageLoader.loadAvatar(ivAvatar, currentAvatarUrl);
        } catch (Exception e) {
        }
    }
}
