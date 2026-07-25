package aoharureverie.ocaacrclient.oldchat.ui;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import aoharureverie.ocaacrclient.oldchat.R;

public final class UserTitleBinder {
    private static final int COMPACT_MAX_CHARS = 8;

    private UserTitleBinder() {
    }

    public static void bind(TextView badge, String title) {
        bindInternal(badge, title, 0);
    }

    public static void bindCompact(TextView badge, String title) {
        bindInternal(badge, title, COMPACT_MAX_CHARS);
    }

    private static void bindInternal(TextView badge, String title, int maxChars) {
        if (badge == null) {
            return;
        }
        if (TextUtils.isEmpty(title)) {
            badge.setVisibility(View.GONE);
            return;
        }
        String trimmed = title.trim();
        if (trimmed.length() == 0) {
            badge.setVisibility(View.GONE);
            return;
        }
        trimmed = normalize(trimmed);
        if (trimmed.length() == 0) {
            badge.setVisibility(View.GONE);
            return;
        }
        if (maxChars > 0 && trimmed.length() > maxChars) {
            trimmed = trimmed.substring(0, maxChars) + "…";
        }
        badge.setText(trimmed);
        badge.setBackgroundResource(R.drawable.bg_badge_title);
        badge.setVisibility(View.VISIBLE);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String out = value.replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .trim();
        while (out.contains("  ")) {
            out = out.replace("  ", " ");
        }
        return out;
    }
}
