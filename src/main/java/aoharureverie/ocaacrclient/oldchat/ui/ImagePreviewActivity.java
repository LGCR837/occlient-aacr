package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;

public class ImagePreviewActivity extends BaseActivity {
    private static final String EXTRA_URL = "url";

    public static void start(Context context, String url) {
        if (context == null || url == null || url.isEmpty()) {
            return;
        }
        Intent intent = new Intent(context, ImagePreviewActivity.class);
        intent.putExtra(EXTRA_URL, url);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        final ImageView imageView = (ImageView) findViewByIdCompat(R.id.ivPreview);
        ImageView btnBack = (ImageView) findViewByIdCompat(R.id.btnPreviewBack);
        final android.view.View loading = findViewByIdCompat(R.id.pbPreviewLoading);
        final String url = getIntent().getStringExtra(EXTRA_URL);

        if (loading != null) {
            loading.setVisibility(android.view.View.VISIBLE);
        }

        ImageLoader.loadLarge(imageView, url, new aoharureverie.ocaacrclient.oldchat.util.ImageLoader.ImageLoadListener() {
            @Override
            public void onComplete(String u) {
                if (loading != null) {
                    loading.setVisibility(android.view.View.GONE);
                }
            }
        });

        if (btnBack != null) {
            btnBack.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    finish();
                }
            });
        }
    }
}
