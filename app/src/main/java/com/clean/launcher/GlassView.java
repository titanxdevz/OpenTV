package com.clean.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * Floating glass highlight that springs between focused tiles.
 *
 * BUG FIXES vs previous version:
 *  • LinearGradient no longer created inside onDraw() — only recreated when
 *    the rect size changes (avoids per-frame allocation on 1GB RAM TV).
 *  • mFirstMove reset in hide() so it correctly snaps on next show.
 *  • Spring animator properly cancelled before starting a new one.
 *  • getLocationOnScreen() guards against detached views.
 */
public class GlassView extends View {

    private final Paint mFillPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRimPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlowPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect       = new RectF();
    private final RectF mSheenRect  = new RectF();
    private final RectF mBodyLocal  = new RectF();
    private final RectF mGlowRect   = new RectF();

    // Current drawn position (interpolated)
    private float mLeft, mTop, mRight, mBottom;
    // Target position
    private float mTL, mTT, mTR, mTB;
    private float mRadius = 14f;

    // Cached sheen gradient — only recreated when height changes
    private LinearGradient mSheenGradient;
    private float          mLastSheenH = -1f;

    private ValueAnimator mSpring;
    private boolean       mFirstMove = true;

    public GlassView(Context ctx) { super(ctx); init(); }
    public GlassView(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(0x28FFFFFF);

        mRimPaint.setStyle(Paint.Style.STROKE);
        mRimPaint.setStrokeWidth(1.5f);
        mRimPaint.setColor(0x99FFFFFF);

        mGlowPaint.setStyle(Paint.Style.STROKE);
        mGlowPaint.setStrokeWidth(6f);
        mGlowPaint.setColor(0x22FFFFFF);

        mSheenPaint.setStyle(Paint.Style.FILL);

        setWillNotDraw(false);
        setAlpha(0f);
        setZ(20f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mRight <= mLeft || mBottom <= mTop) return;

        mRect.set(mLeft, mTop, mRight, mBottom);
        mRadius = Math.min(12f,
            Math.min(mRight - mLeft, mBottom - mTop) * 0.075f);

        // Outer glow rings (3 passes) — thickness follows the target size
        float glowStep = Math.min(4f, Math.max(2f, (mBottom - mTop) * 0.03f));
        for (int i = 3; i >= 1; i--) {
            float expand = i * glowStep;
            mGlowRect.set(
                mLeft - expand, mTop - expand,
                mRight + expand, mBottom + expand);
            mGlowPaint.setAlpha(10 * i);
            canvas.drawRoundRect(mGlowRect,
                mRadius + expand, mRadius + expand, mGlowPaint);
        }

        // Glass fill
        canvas.drawRoundRect(mRect, mRadius, mRadius, mFillPaint);

        // Top specular sheen — gradient is built in LOCAL coords, so moving
        // the glass never allocates a new shader.
        float sheenH = (mBottom - mTop) * 0.30f;
        if (mSheenGradient == null || Math.abs(sheenH - mLastSheenH) > 0.5f) {
            mSheenGradient = new LinearGradient(
                0, 0, 0, sheenH,
                new int[]{ 0x40FFFFFF, 0x00FFFFFF },
                null, Shader.TileMode.CLAMP);
            mSheenPaint.setShader(mSheenGradient);
            mLastSheenH   = sheenH;
        }
        canvas.save();
        canvas.translate(mLeft, mTop);
        mSheenRect.set(0, 0, mRight - mLeft, sheenH);
        canvas.clipRect(mSheenRect);
        mBodyLocal.set(0, 0, mRight - mLeft, mBottom - mTop);
        canvas.drawRoundRect(mBodyLocal, mRadius, mRadius, mSheenPaint);
        canvas.restore();

        // Crisp rim stroke
        canvas.drawRoundRect(mRect, mRadius, mRadius, mRimPaint);
    }

    /** Animate the glass to cover the given view at its resting scale. */
    public void moveTo(View target) { moveTo(target, 1f); }

    public void moveTo(View target, float scale) {
        if (target == null || !target.isAttachedToWindow()) {
            animate().alpha(0f).setDuration(150).start();
            return;
        }

        int[] loc  = new int[2];
        int[] mine = new int[2];
        target.getLocationOnScreen(loc);
        getLocationOnScreen(mine);

        float nl = loc[0] - mine[0];
        float nt = loc[1] - mine[1];
        float nr = nl + target.getWidth();
        float nb = nt + target.getHeight();

        // Compensate for the view's focused scale (tiles 1.10, pills 1.06).
        float cx    = (nl + nr) / 2f;
        float cy    = (nt + nb) / 2f;
        float halfW = (nr - nl) * scale / 2f;
        float halfH = (nb - nt) * scale / 2f;
        nl = cx - halfW; nr = cx + halfW;
        nt = cy - halfH; nb = cy + halfH;

        // Guard: target has zero size (not laid out yet)
        if (nr <= nl || nb <= nt) return;

        mTL = nl; mTT = nt; mTR = nr; mTB = nb;

        if (mFirstMove) {
            mFirstMove = false;
            mLeft = nl; mTop = nt; mRight = nr; mBottom = nb;
            animate().alpha(1f).setDuration(200).start();
            invalidate();
            return;
        }

        // Cancel any in-flight spring
        if (mSpring != null) { mSpring.cancel(); mSpring = null; }

        final float sl = mLeft, st = mTop, sr = mRight, sb = mBottom;
        mSpring = ValueAnimator.ofFloat(0f, 1f);
        mSpring.setDuration(300);
        mSpring.setInterpolator(new OvershootInterpolator(1.4f));
        mSpring.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            mLeft   = sl + (mTL - sl) * f;
            mTop    = st + (mTT - st) * f;
            mRight  = sr + (mTR - sr) * f;
            mBottom = sb + (mTB - sb) * f;
            invalidate();
        });
        mSpring.start();
        animate().alpha(1f).setDuration(120).start();
    }

    public void hide() {
        if (mSpring != null) { mSpring.cancel(); mSpring = null; }
        animate().alpha(0f).setDuration(200).start();
        mFirstMove = true; // next moveTo() will snap, not spring
    }
}
