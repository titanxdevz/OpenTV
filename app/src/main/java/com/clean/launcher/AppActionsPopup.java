package com.clean.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Long-press "manage app" card: Open / App info / Uninstall / Cancel.
 * Stays inside the launcher; every action has a fallback.
 */
public class AppActionsPopup {

    public interface OnDismiss { void onDismiss(); }

    private final Activity  mActivity;
    private final View      mOverlay;
    private final View      mCard;
    private final View      mScrim;
    private final OnDismiss mOnDismiss;
    private boolean         mDismissing = false;

    public static AppActionsPopup show(Activity activity, AppInfo app, OnDismiss onDismiss) {
        return new AppActionsPopup(activity, app, onDismiss);
    }

    private AppActionsPopup(Activity activity, AppInfo app, OnDismiss onDismiss) {
        mActivity  = activity;
        mOnDismiss = onDismiss;

        ViewGroup root = activity.getWindow().getDecorView()
                .findViewById(android.R.id.content);
        mOverlay = LayoutInflater.from(activity)
                .inflate(R.layout.overlay_app_actions, root, false);
        root.addView(mOverlay);

        mCard  = mOverlay.findViewById(R.id.actions_card);
        mScrim = mOverlay.findViewById(R.id.actions_scrim);

        ImageView icon = mOverlay.findViewById(R.id.actions_icon);
        TextView  name = mOverlay.findViewById(R.id.actions_title);
        name.setText(app.label);
        if (app.icon != null) icon.setImageDrawable(copyOf(app.icon));

        View open     = mOverlay.findViewById(R.id.action_open);
        View info     = mOverlay.findViewById(R.id.action_info);
        View uninstall = mOverlay.findViewById(R.id.action_uninstall);
        View cancel   = mOverlay.findViewById(R.id.action_cancel);

        open.setOnClickListener(v -> { dismissNow(); launch(app); });
        info.setOnClickListener(v -> { dismissNow(); openAppInfo(app); });
        if (canUninstall(app.packageName)) {
            uninstall.setOnClickListener(v -> { dismissNow(); uninstall(app); });
        } else {
            uninstall.setVisibility(View.GONE);
        }
        cancel.setOnClickListener(v -> dismiss());

        mScrim.setOnClickListener(v -> dismiss());

        mOverlay.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                dismiss();
                return true;
            }
            return false;
        });

        animateIn(open);
    }

    // ── Actions ───────────────────────────────────────────────────────────────
    private void launch(AppInfo app) {
        PackageManager pm = mActivity.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(app.packageName);
        if (i == null) i = pm.getLaunchIntentForPackage(app.packageName);
        if (i == null) return;
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mActivity.startActivity(i);
        } catch (Exception ignored) { }
    }

    private void openAppInfo(AppInfo app) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + app.packageName));
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mActivity.startActivity(i);
        } catch (Exception e) {
            try {
                mActivity.startActivity(new Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        }
    }

    private void uninstall(AppInfo app) {
        Intent i = new Intent(Intent.ACTION_DELETE,
                Uri.parse("package:" + app.packageName));
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mActivity.startActivity(i);
        } catch (Exception e) {
            // No uninstall screen — fall back to the app's info page.
            openAppInfo(app);
        }
    }

    private boolean canUninstall(String pkg) {
        try {
            ApplicationInfo ai = mActivity.getPackageManager()
                    .getApplicationInfo(pkg, 0);
            return (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Drawable copyOf(Drawable d) {
        try {
            Drawable.ConstantState cs = d.getConstantState();
            return cs != null ? cs.newDrawable().mutate() : d;
        } catch (Exception e) {
            return d;
        }
    }

    // ── Animation ─────────────────────────────────────────────────────────────
    private void animateIn(View first) {
        mScrim.setAlpha(0f);
        mScrim.animate().alpha(1f).setDuration(180).start();

        mCard.setAlpha(0f);
        mCard.setScaleX(0.94f);
        mCard.setScaleY(0.94f);
        mCard.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setInterpolator(new DecelerateInterpolator(1.6f))
            .start();

        mOverlay.post(() -> {
            if (first != null && !mDismissing) first.requestFocus();
        });
    }

    public void dismiss() {
        if (mDismissing) return;
        mDismissing = true;

        mCard.animate()
            .alpha(0f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(150)
            .start();
        mScrim.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction(this::finishDismiss)
            .start();
    }

    private void dismissNow() {
        if (mDismissing) return;
        mDismissing = true;
        finishDismiss();
    }

    private void finishDismiss() {
        ViewGroup parent = (ViewGroup) mOverlay.getParent();
        if (parent != null) parent.removeView(mOverlay);
        if (mOnDismiss != null) mOnDismiss.onDismiss();
    }
}
