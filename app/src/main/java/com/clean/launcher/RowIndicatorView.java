package com.clean.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Slim horizontal progress bar showing scroll position within the app row.
 *
 * BUG FIXES:
 *  • Division-by-zero when total=1: guard added.
 *  • Infinite post() loop when width=0: replaced with onLayout() callback.
 *  • Animator not cancelled before new one: fixed.
 */
public class RowIndicatorView extends View {

    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mTrackRect  = new RectF();
    private final RectF mThumbRect  = new RectF();

    private float mThumbLeft  = 0f;
    private float mThumbRight = 0f;

    // Pending update if width wasn't known yet
    private int     mPendingPos   = -1;
    private int     mPendingTotal = -1;
    private boolean mInitialized  = false;

    private ValueAnimator mAnimator;

    public RowIndicatorView(Context ctx) { super(ctx); init(); }
    public RowIndicatorView(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        mTrackPaint.setColor(0x33FFFFFF);
        mTrackPaint.setStyle(Paint.Style.FILL);
        mThumbPaint.setColor(0xCCFFFFFF);
        mThumbPaint.setStyle(Paint.Style.FILL);
        setWillNotDraw(false);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // Apply any pending update that arrived before we were laid out
        if (mPendingPos >= 0 && getWidth() > 0) {
            updateNow(mPendingPos, mPendingTotal);
            mPendingPos   = -1;
            mPendingTotal = -1;
        }
    }

    /** Call on every focus change with the new focused position. */
    public void update(int pos, int total) {
        if (total <= 1) {
            animate().alpha(0f).setDuration(200).start();
            return;
        }
        animate().alpha(1f).setDuration(200).start();

        if (getWidth() == 0) {
            // Store for later — onLayout() will apply it
            mPendingPos   = pos;
            mPendingTotal = total;
            return;
        }
        updateNow(pos, total);
    }

    private void updateNow(int pos, int total) {
        float w = getWidth();
        // Guard division-by-zero: total=1 handled above, but be safe
        if (total <= 1 || w == 0) return;

        int   visible = Math.min(total, 6);
        float thumbW  = w * visible / (float) total;
        float maxOff  = w - thumbW;
        // Guard: (total-1) is safe because total >= 2 here
        float newLeft = maxOff * pos / (float) (total - 1);
        float newRight = newLeft + thumbW;

        if (!mInitialized) {
            mInitialized  = true;
            mThumbLeft    = newLeft;
            mThumbRight   = newRight;
            invalidate();
            return;
        }

        if (mAnimator != null) { mAnimator.cancel(); mAnimator = null; }

        final float fl = mThumbLeft, fr = mThumbRight;
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(180);
        mAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        mAnimator.addUpdateListener(a -> {
            float t     = (float) a.getAnimatedValue();
            mThumbLeft  = fl + (newLeft  - fl) * t;
            mThumbRight = fr + (newRight - fr) * t;
            invalidate();
        });
        mAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                mAnimator = null;
            }
        });
        mAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = getHeight();
        float r = h / 2f;
        float w = getWidth();

        mTrackRect.set(0, 0, w, h);
        canvas.drawRoundRect(mTrackRect, r, r, mTrackPaint);

        if (mThumbRight > mThumbLeft) {
            mThumbRect.set(mThumbLeft, 0, mThumbRight, h);
            canvas.drawRoundRect(mThumbRect, r, r, mThumbPaint);
        }
    }
}
