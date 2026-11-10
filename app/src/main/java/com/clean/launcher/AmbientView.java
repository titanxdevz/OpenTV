package com.clean.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Soft radial ambient glow behind the launcher content.
 * Replaces the old flat-fill View, which erased the gradient sky.
 */
public class AmbientView extends View {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int     mColor   = 0;   // current (interpolated) colour
    private int     mTarget  = 0;

    private RadialGradient mGradient;
    private int  mCachedColor = -1;
    private int  mCachedW     = 0;
    private int  mCachedH     = 0;

    private ValueAnimator mAnim;

    public AmbientView(Context ctx) { super(ctx); init(); }
    public AmbientView(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        setWillNotDraw(false);
        setAlpha(0f);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** 0 = hide, any other ARGB = blend the glow toward that colour. */
    public void setAccent(int color) {
        if (color == 0) {
            if (mAnim != null) { mAnim.cancel(); mAnim = null; }
            mTarget = 0;
            animate().alpha(0f).setDuration(320).start();
            return;
        }
        mTarget = color;
        animate().alpha(1f).setDuration(360).start();
        if (color == mColor) return;

        if (mAnim != null) { mAnim.cancel(); mAnim = null; }
        final int from = mColor == 0 ? color : mColor;
        mAnim = ValueAnimator.ofFloat(0f, 1f);
        mAnim.setDuration(520);
        mAnim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            mColor = blend(from, color, t);
            invalidate();
        });
        mAnim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mColor == 0 || mTarget == 0) return;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        if (mGradient == null || mColor != mCachedColor
                || w != mCachedW || h != mCachedH) {
            float cx = w * 0.50f;
            float cy = h * 0.42f;
            float radius = Math.max(w, h) * 0.72f;
            mGradient = new RadialGradient(cx, cy, radius,
                new int[] {
                    withAlpha(mColor, 0x70),
                    withAlpha(mColor, 0x30),
                    0x00000000
                },
                new float[] { 0f, 0.55f, 1f },
                Shader.TileMode.CLAMP);
            mPaint.setShader(mGradient);
            mCachedColor = mColor;
            mCachedW = w;
            mCachedH = h;
        }

        canvas.drawRect(0, 0, w, h, mPaint);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int blend(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return 0xFF000000
            | ((int) (r1 + (r2 - r1) * t) << 16)
            | ((int) (g1 + (g2 - g1) * t) << 8)
            |  (int) (b1 + (b2 - b1) * t);
    }
}
