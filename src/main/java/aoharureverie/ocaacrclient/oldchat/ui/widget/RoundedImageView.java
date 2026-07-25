package aoharureverie.ocaacrclient.oldchat.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import aoharureverie.ocaacrclient.oldchat.R;

public class RoundedImageView extends AppCompatImageView {
    private final RectF rect = new RectF();
    private final Path clipPath = new Path();
    private float radiusPx;

    public RoundedImageView(Context context) {
        super(context);
        init(context, null);
    }

    public RoundedImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public RoundedImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        float defaultDp = 6f;
        float d = context.getResources().getDisplayMetrics().density;
        radiusPx = defaultDp * d;

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RoundedImageView);
            try {
                radiusPx = a.getDimension(R.styleable.RoundedImageView_cornerRadius, radiusPx);
            } finally {
                a.recycle();
            }
        }

        if (Build.VERSION.SDK_INT >= 11 && Build.VERSION.SDK_INT < 18) {
            // clipPath is unreliable with HW acceleration on older APIs.
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(0, 0, w, h);
        updatePath();
    }

    public void setCornerRadiusPx(float radiusPx) {
        if (radiusPx < 0) radiusPx = 0;
        this.radiusPx = radiusPx;
        updatePath();
        invalidate();
    }

    private void updatePath() {
        clipPath.reset();
        if (radiusPx > 0f) {
            clipPath.addRoundRect(rect, radiusPx, radiusPx, Path.Direction.CW);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (radiusPx <= 0f) {
            super.onDraw(canvas);
            return;
        }
        int save = canvas.save();
        canvas.clipPath(clipPath);
        super.onDraw(canvas);
        canvas.restoreToCount(save);
    }
}
