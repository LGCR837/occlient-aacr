package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;

import org.json.JSONObject;

public class RedPacketOpenActivity extends BaseActivity {
    public static final String EXTRA_PACKET_ID = "packet_id";

    private ImageView ivCover;
    private TextView tvTitle;
    private TextView tvStatus;
    private TextView btnOpen;
    private ProgressBar pbOpen;
    private TextView tvResult;
    private TextView btnDetail;
    private String token;
    private String packetId;
    private int myClaimAmount = 0;
    private boolean canClaim = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_red_packet_open);

        View btnBack = findViewByIdCompat(R.id.btnRedPacketOpenBack);
        ivCover = findViewByIdCompat(R.id.ivRedPacketOpenIcon);
        tvTitle = findViewByIdCompat(R.id.tvRedPacketOpenTitle);
        tvStatus = findViewByIdCompat(R.id.tvRedPacketOpenStatus);
        btnOpen = findViewByIdCompat(R.id.btnOpenRedPacket);
        pbOpen = findViewByIdCompat(R.id.pbRedPacketOpen);
        tvResult = findViewByIdCompat(R.id.tvRedPacketResult);
        btnDetail = findViewByIdCompat(R.id.btnRedPacketDetail);

        SharedPreferences prefs = getSharedPreferences("auth", Context.MODE_PRIVATE);
        token = prefs.getString("access_token", "");

        Intent intent = getIntent();
        packetId = intent.getStringExtra(EXTRA_PACKET_ID);

        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        if (btnOpen != null) {
            btnOpen.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    claimRedPacket();
                }
            });
        }

        if (btnDetail != null) {
            btnDetail.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openDetail();
                }
            });
        }

        loadDetail();
    }

    private void loadDetail() {
        if (packetId == null || packetId.isEmpty()) {
            Toast.makeText(this, R.string.red_packet_invalid, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        HttpUtil.get("/redpackets/" + packetId, token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                applyDetail(response);
            }

            @Override
            public void onError(int code, String error) {
                if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                    return;
                }
                Toast.makeText(RedPacketOpenActivity.this, R.string.red_packet_open_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyDetail(String response) {
        try {
            JSONObject obj = new JSONObject(response);
            String title = obj.optString("title", "");
            if (title == null || title.isEmpty()) {
                title = getString(R.string.red_packet_title_default);
            }
            if (tvTitle != null) {
                tvTitle.setText(title);
            }

            String coverUrl = obj.optString("cover_url", "");
            if (ivCover != null) {
                if (coverUrl != null && !coverUrl.trim().isEmpty()) {
                    ImageLoader.load(ivCover, coverUrl.trim());
                } else {
                    ivCover.setImageResource(R.drawable.ic_red_packet);
                }
            }

            String status = obj.optString("status", "active");
            int remainingCount = obj.optInt("remaining_count", 0);
            boolean done = "done".equalsIgnoreCase(status) || remainingCount <= 0;
            if (tvStatus != null) {
                tvStatus.setText(done ? getString(R.string.red_packet_status_done) : getString(R.string.red_packet_status_open));
            }

            myClaimAmount = obj.optInt("my_claim_amount", 0);
            canClaim = obj.optBoolean("can_claim", false);

            if (btnOpen != null) {
                btnOpen.setVisibility(canClaim ? View.VISIBLE : View.GONE);
            }
            if (tvResult != null) {
                if (myClaimAmount > 0) {
                    tvResult.setText(getString(R.string.red_packet_received_format, myClaimAmount));
                    tvResult.setVisibility(View.VISIBLE);
                } else {
                    tvResult.setVisibility(View.GONE);
                }
            }
            if (btnDetail != null) {
                boolean showDetail = myClaimAmount > 0 || !canClaim;
                btnDetail.setVisibility(showDetail ? View.VISIBLE : View.GONE);
            }
        } catch (Exception e) {
        }
    }

    private void claimRedPacket() {
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, R.string.error_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }
        if (packetId == null || packetId.isEmpty()) {
            Toast.makeText(this, R.string.red_packet_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        setOpening(true);
        try {
            JSONObject json = new JSONObject();
            json.put("packet_id", packetId);
            HttpUtil.post("/redpackets/claim", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    setOpening(false);
                    handleClaimSuccess(response);
                }

                @Override
                public void onError(int code, String error) {
                    setOpening(false);
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    if (error != null && error.contains("red_packet_already_claimed")) {
                        Toast.makeText(RedPacketOpenActivity.this, R.string.red_packet_already_claimed, Toast.LENGTH_SHORT).show();
                        loadDetail();
                        return;
                    }
                    if (error != null && error.contains("red_packet_empty")) {
                        Toast.makeText(RedPacketOpenActivity.this, R.string.red_packet_empty, Toast.LENGTH_SHORT).show();
                        loadDetail();
                        return;
                    }
                    if (error != null && error.contains("red_packet_no_permission")) {
                        Toast.makeText(RedPacketOpenActivity.this, R.string.red_packet_no_permission, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(RedPacketOpenActivity.this, R.string.red_packet_open_failed, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            setOpening(false);
        }
    }

    private void handleClaimSuccess(String response) {
        try {
            JSONObject obj = new JSONObject(response);
            int amount = obj.optInt("amount", 0);
            if (tvResult != null && amount > 0) {
                tvResult.setText(getString(R.string.red_packet_received_format, amount));
                tvResult.setVisibility(View.VISIBLE);
            }
            loadDetail();
        } catch (Exception e) {
            loadDetail();
        }
    }

    private void setOpening(boolean opening) {
        if (pbOpen != null) {
            pbOpen.setVisibility(opening ? View.VISIBLE : View.GONE);
        }
        if (btnOpen != null) {
            btnOpen.setEnabled(!opening);
            ViewCompat.setAlpha(btnOpen, opening ? 0.65f : 1f);
        }
    }

    private void openDetail() {
        if (packetId == null || packetId.isEmpty()) {
            return;
        }
        Intent intent = new Intent(this, RedPacketDetailActivity.class);
        intent.putExtra(RedPacketDetailActivity.EXTRA_PACKET_ID, packetId);
        startActivity(intent);
    }
}
