package com.clean.launcher;

import android.content.Context;
import android.graphics.PointF;
import android.util.DisplayMetrics;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Horizontal LinearLayoutManager with Apple TV–style scroll physics.
 *
 * NOTE: onRequestChildFocus() is deliberately NOT overridden. The previous
 * version called scrollToPositionWithOffset() from there, which detached the
 * focused child mid-layout — the focused tile lost focus and the D-pad ended
 * up in the top bar. Let RecyclerView handle focus + scrolling itself.
 */
public class SmoothRowLayoutManager extends LinearLayoutManager {

    private static final float MS_PER_PX = 80f; // higher = slower

    public SmoothRowLayoutManager(Context context) {
        super(context, HORIZONTAL, false);
    }

    @Override
    public void smoothScrollToPosition(RecyclerView recyclerView,
                                       RecyclerView.State state,
                                       int position) {
        LinearSmoothScroller scroller =
            new LinearSmoothScroller(recyclerView.getContext()) {

                @Override
                protected float calculateSpeedPerPixel(DisplayMetrics dm) {
                    return MS_PER_PX / dm.densityDpi;
                }

                @Override
                protected int calculateTimeForScrolling(int dx) {
                    return Math.min(Math.max(
                        super.calculateTimeForScrolling(dx), 120), 380);
                }

                @Override
                public PointF computeScrollVectorForPosition(int targetPosition) {
                    return SmoothRowLayoutManager.this
                            .computeScrollVectorForPosition(targetPosition);
                }

                @Override
                protected int getHorizontalSnapPreference() {
                    return SNAP_TO_START;
                }
            };
        scroller.setTargetPosition(position);
        startSmoothScroll(scroller);
    }
}
