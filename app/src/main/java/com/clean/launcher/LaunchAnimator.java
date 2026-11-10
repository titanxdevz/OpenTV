package com.clean.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Apple TV–style zoom launch animation.
 *
 * BUG FIXES vs previous version:
 *  • Translation math was wrong — used pivot-based scale offset which
 *    caused the overlay to fly to the wrong position. Fixed: translate the
 *    tile's top-left corner to (0,0) while scaling around the tile centre,
 *    so the expansion looks like it grows from the tile outward.
 *  • Overlay is now removed even if startActivity() throws (finally block).
 *  • Guard against zero-size tile (not laid out).
 *  • Overlay alpha ends at 1.0 (not 0.6) so the transition looks solid.
 */
public class LaunchAnimator {

    private static final int DURATION_MS = 280;

    public static void launch(Activity activity,
                              View     tileView,
                              Drawable artwork,
                              Intent   appIntent) {

        if (tileView == null || !tileView.isAttachedToWindow()
                || tileView.getWidth() == 0 || tileView.getHeight() == 0) {
            startSafe(activity, appIntent);
            return;
        }

        ViewGroup root = activity.getWindow().getDecorView()
                .findViewById(android.R.id.content);

        // ── Measure tile position relative to root ──────────────────
        int[] tileLoc = new int[2];
        int[] rootLoc = new int[2];
        tileView.getLocationOnScreen(tileLoc);
        root.getLocationOnScreen(rootLoc);

        final float startLeft = tileLoc[0] - rootLoc[0];
        final float startTop  = tileLoc[1] - rootLoc[1];
        final float startW    = tileView.getWidth();
        final float startH    = tileView.getHeight();
        final float endW      = root.getWidth();
        final float endH      = root.getHeight();

        if (startW == 0 || startH == 0 || endW == 0 || endH == 0) {
            startSafe(activity, appIntent);
            return;
        }

        // ── Build overlay at exact tile position ─────────────────────
        ImageView overlay = new ImageView(activity);
        overlay.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Drawable art = artwork;
        if (artwork != null) {
            // Independent copy — the tile ImageView owns the original Drawable.
            try {
                Drawable.ConstantState cs = artwork.getConstantState();
                if (cs != null) art = cs.newDrawable().mutate();
            } catch (Exception ignored) { }
            overlay.setImageDrawable(art);
        } else {
            overlay.setBackgroundColor(0xFF000000);
        }

        FrameLayout.LayoutParams lp =
            new FrameLayout.LayoutParams((int) startW, (int) startH);
        lp.leftMargin = (int) startLeft;
        lp.topMargin  = (int) startTop;
        overlay.setLayoutParams(lp);
        root.addView(overlay);

        // ── Scale factors ─────────────────────────────────────────────
        float scaleX = endW / startW;
        float scaleY = endH / startH;

        // Pivot = centre of tile
        float pivotX = startW / 2f;
        float pivotY = startH / 2f;
        overlay.setPivotX(pivotX);
        overlay.setPivotY(pivotY);

        // Translation needed to move the scaled tile's center to screen center:
        // After scale, tile centre is still at (startLeft + pivotX, startTop + pivotY).
        // We want it at (endW/2, endH/2).
        float tx = (endW / 2f) - (startLeft + pivotX);
        float ty = (endH / 2f) - (startTop  + pivotY);

        AnimatorSet set = new AnimatorSet();
        set.setDuration(DURATION_MS);
        set.setInterpolator(new DecelerateInterpolator(1.8f));
        set.playTogether(
            ObjectAnimator.ofFloat(overlay, "scaleX",       1f, scaleX),
            ObjectAnimator.ofFloat(overlay, "scaleY",       1f, scaleY),
            ObjectAnimator.ofFloat(overlay, "translationX", 0f, tx),
            ObjectAnimator.ofFloat(overlay, "translationY", 0f, ty)
        );

        set.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                // Always remove overlay, even if startActivity throws
                try {
                    startSafe(activity, appIntent);
                } finally {
                    overlay.postDelayed(() -> {
                        ViewGroup p = (ViewGroup) overlay.getParent();
                        if (p != null) p.removeView(overlay);
                    }, 350);
                }
            }

            @Override public void onAnimationCancel(Animator a) {
                ViewGroup p = (ViewGroup) overlay.getParent();
                if (p != null) p.removeView(overlay);
            }
        });

        set.start();
    }

    private static void startSafe(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent);
        } catch (Exception ignored) {}
    }
}
