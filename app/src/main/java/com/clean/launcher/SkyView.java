package com.clean.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import java.util.Calendar;

/**
 * Time-of-day gradient background.
 *
 * BUG FIXES:
 *  • LinearGradient only recreated when colors actually change — not on
 *    every onDraw() call (was allocating on every frame before).
 *  • ValueAnimator cancelled before starting a new one — prevents 10
 *    simultaneous animators from rapid D-pad focus changes.
 *  • Gradient cached as a field, invalidated only on color change.
 */
public class SkyView extends View {

    private final Paint mPaint = new Paint();

    // Current rendered colors (interpolated)
    private int   mTopColor    = 0xFF0D0D1A;
    private int   mBottomColor = 0xFF000000;

    // Cached gradient — only recreated when colors change
    private LinearGradient mGradient;
    private int            mCachedTop    = -1;
    private int            mCachedBottom = -1;
    private int            mCachedW      = 0;
    private int            mCachedH      = 0;

    // Active blend animator — cancelled before starting new one
    private ValueAnimator  mBlendAnim;

    public SkyView(Context ctx) { super(ctx); }
    public SkyView(Context ctx, AttributeSet a) { super(ctx, a); }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        // Only rebuild gradient if colors or size changed
        if (mGradient == null
                || mTopColor    != mCachedTop
                || mBottomColor != mCachedBottom
                || w != mCachedW || h != mCachedH) {
            mGradient = new LinearGradient(
                0, 0, w * 0.55f, h * 0.65f,
                new int[]{ mTopColor, mBottomColor },
                new float[]{ 0f, 1f },
                Shader.TileMode.CLAMP);
            mPaint.setShader(mGradient);
            mCachedTop    = mTopColor;
            mCachedBottom = mBottomColor;
            mCachedW      = w;
            mCachedH      = h;
        }
        canvas.drawRect(0, 0, w, h, mPaint);
    }

    /** Call when focused app changes — blends bg toward app's accent color. */
    public void setAccent(int argbColor) {
        int targetTop;
        if (argbColor == 0) {
            targetTop = skyColor(); // back to time-of-day
        } else {
            // Darken the accent so text stays readable
            float[] hsv = new float[3];
            android.graphics.Color.colorToHSV(argbColor, hsv);
            hsv[2] = Math.min(hsv[2], 0.28f);
            hsv[1] = Math.min(hsv[1], 0.85f);
            targetTop = android.graphics.Color.HSVToColor(hsv);
        }

        if (targetTop == mTopColor) return; // no change

        // Cancel any in-flight animation before starting new one
        if (mBlendAnim != null) {
            mBlendAnim.cancel();
            mBlendAnim = null;
        }

        final int fromTop = mTopColor;
        mBlendAnim = ValueAnimator.ofFloat(0f, 1f);
        mBlendAnim.setDuration(600);
        mBlendAnim.addUpdateListener(a -> {
            float t  = (float) a.getAnimatedValue();
            mTopColor = blendColor(fromTop, targetTop, t);
            invalidate();
        });
        mBlendAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                mBlendAnim = null;
            }
        });
        mBlendAnim.start();
    }

    private int skyColor() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if      (hour <  5) return 0xFF0D0D1A;
        else if (hour < 10) return 0xFF3D1A00;
        else if (hour < 17) return 0xFF0A1628;
        else if (hour < 21) return 0xFF2A0A00;
        else                return 0xFF150A2A;
    }

    private static int blendColor(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return 0xFF000000
            | ((int)(r1 + (r2 - r1) * t) << 16)
            | ((int)(g1 + (g2 - g1) * t) <<  8)
            | ((int)(b1 + (b2 - b1) * t));
    }
}
