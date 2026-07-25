package aoharureverie.ocaacrclient.oldchat.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.data.MomentNoticeStore;
import aoharureverie.ocaacrclient.oldchat.data.SettingsStore;
import aoharureverie.ocaacrclient.oldchat.ui.EmojiPlazaActivity;
import aoharureverie.ocaacrclient.oldchat.ui.MomentsActivity;
import aoharureverie.ocaacrclient.oldchat.ui.MusicPlazaActivity;
import aoharureverie.ocaacrclient.oldchat.ui.OldViewActivity;
import aoharureverie.ocaacrclient.oldchat.ui.PublicCourtActivity;
import aoharureverie.ocaacrclient.oldchat.ui.ReportProgressActivity;

import org.json.JSONObject;

public class DiscoverFragment extends Fragment {
    private View momentsDot;
    private View btnPublicCourt;
    private View btnCheckIn;
    private String token;
    private boolean checkingIn;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_discover, container, false);
        refreshToken();

        View btnMoments = view.findViewById(R.id.btnDiscoverMoments);
        momentsDot = view.findViewById(R.id.viewDiscoverMomentsDot);
        if (btnMoments != null) {
            btnMoments.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getActivity() == null) {
                        return;
                    }
                    startActivity(new Intent(getActivity(), MomentsActivity.class));
                }
            });
        }

        View btnEmojiPlaza = view.findViewById(R.id.btnDiscoverEmojiPlaza);
        if (btnEmojiPlaza != null) {
            btnEmojiPlaza.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getActivity() == null) {
                        return;
                    }
                    startActivity(new Intent(getActivity(), EmojiPlazaActivity.class));
                }
            });
        }

        View btnMusicPlaza = view.findViewById(R.id.btnDiscoverMusicPlaza);
        if (btnMusicPlaza != null) {
            btnMusicPlaza.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getActivity() == null) {
                        return;
                    }
                    startActivity(new Intent(getActivity(), MusicPlazaActivity.class));
                }
            });
        }

        View btnOldView = view.findViewById(R.id.btnDiscoverOldView);
        if (btnOldView != null) {
            btnOldView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getActivity() == null) {
                        return;
                    }
                    startActivity(new Intent(getActivity(), OldViewActivity.class));
                }
            });
        }

        View btnProgress = view.findViewById(R.id.btnDiscoverProgress);
        if (btnProgress != null) {
            btnProgress.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getActivity() == null) {
                        return;
                    }
                    startActivity(new Intent(getActivity(), ReportProgressActivity.class));
                }
            });
        }

        btnCheckIn = view.findViewById(R.id.btnDiscoverCheckIn);
        if (btnCheckIn != null) {
            btnCheckIn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    submitDailyCheckIn();
                }
            });
        }

        btnPublicCourt = view.findViewById(R.id.btnDiscoverPublicCourt);
        if (btnPublicCourt != null) {
            btnPublicCourt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getActivity() == null) {
                        return;
                    }
                    startActivity(new Intent(getActivity(), PublicCourtActivity.class));
                }
            });
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshToken();
        if (momentsDot != null) {
            boolean has = MomentNoticeStore.hasNotices(getActivity());
            momentsDot.setVisibility(has ? View.VISIBLE : View.GONE);
        }
        if (btnPublicCourt != null) {
            boolean enabled = SettingsStore.isPublicCourtEnabled(getActivity());
            btnPublicCourt.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }

    private void refreshToken() {
        if (getActivity() == null) {
            token = "";
            return;
        }
        SharedPreferences prefs = getActivity().getSharedPreferences("auth", Context.MODE_PRIVATE);
        token = prefs.getString("access_token", "");
    }

    private void setCheckInBusy(boolean busy) {
        checkingIn = busy;
        if (btnCheckIn != null) {
            btnCheckIn.setEnabled(!busy);
            ViewCompat.setAlpha(btnCheckIn, busy ? 0.6f : 1f);
        }
    }

    private void submitDailyCheckIn() {
        if (checkingIn) {
            return;
        }
        if (getActivity() == null) {
            return;
        }
        if (token == null || token.length() == 0) {
            Toast.makeText(getActivity(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        setCheckInBusy(true);
        HttpUtil.post("/me/checkin", new JSONObject(), token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                setCheckInBusy(false);
                if (getActivity() == null) {
                    return;
                }
                try {
                    JSONObject obj = new JSONObject(response);
                    boolean already = obj.optBoolean("already_checked", false);
                    if (already) {
                        Toast.makeText(getActivity(), "今天已签到", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int coinReward = obj.optInt("coin_reward", 10);
                    int reputationReward = obj.optInt("reputation_reward", 50);
                    Toast.makeText(getActivity(), "签到成功 +" + coinReward + "旧币，+" + reputationReward + "信誉分", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(getActivity(), "签到成功", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(int code, String error) {
                setCheckInBusy(false);
                if (getActivity() == null) {
                    return;
                }
                if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                    return;
                }
                if (code == 409) {
                    Toast.makeText(getActivity(), "今天已签到", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(getActivity(), "签到失败，请稍后重试", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
