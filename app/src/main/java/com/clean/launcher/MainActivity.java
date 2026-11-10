package com.clean.launcher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CleanLauncher";

    // Pinned apps always appear first, in this order
    private static final String[] PINNED = {
        "com.netflix.ninja",
        "com.amazon.amazonvideo.livingroom",
        "com.google.android.youtube.tv",
        "in.startv.hotstar"
    };

    // ── Views ──────────────────────────────────────────────────────────────────
    private SkyView           mSky;
    private AmbientView       mAmbient;
    private ImageView         mHeroArt;
    private TextView          mGreeting;
    private View              mHeroBlock;
    private TextView          mHeroName;
    private RecyclerView      mRow;
    private GlassView         mGlass;
    private View              mLoadingShimmer;
    private RowIndicatorView  mIndicator;
    private View              mBtnInputs, mBtnWifi, mBtnSettings;

    // ── State ──────────────────────────────────────────────────────────────────
    private List<AppInfo>  mApps       = new ArrayList<>();
    private AppRowAdapter  mAdapter;
    private int            mZone       = 1;   // 0=topbar  1=row
    private int            mTopBarIdx  = 0;
    private int            mLastRowPos = 0;
    /** True while we still want the app row to hold focus (boot / return). */
    private boolean        mRowFocusRequested = true;
    /** True while the long-press manage card is on screen. */
    private boolean        mModalOpen = false;

    private final Handler         mHandler  = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private Runnable              mGreetingRunnable;

    /** Keeps the row in sync when a package is uninstalled from our own menu. */
    private final BroadcastReceiver mPkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) return;
            Uri data = intent.getData();
            if (data == null) return;
            removeApp(data.getSchemeSpecificPart());
        }
    };

    // ── Lifecycle ──────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupGreeting();
        setupTopBar();
        showLoadingShimmer();
        loadApps();

        IntentFilter pkgFilter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addDataScheme("package");
        ContextCompat.registerReceiver(this, mPkgReceiver, pkgFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);

        if (savedInstanceState != null) {
            mZone       = savedInstanceState.getInt("zone",   1);
            mLastRowPos = savedInstanceState.getInt("rowPos", 0);
            mTopBarIdx  = savedInstanceState.getInt("topIdx", 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateGreeting();
        // Coming back from system Settings / an app: restore focus once the
        // row has laid out. focusRow() retries, so an early call is harmless.
        if (!mModalOpen && mAdapter != null) restoreFocus();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("zone",   mZone);
        out.putInt("rowPos", mLastRowPos);
        out.putInt("topIdx", mTopBarIdx);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(mPkgReceiver); } catch (Exception ignored) { }
        if (mGreetingRunnable != null) mHandler.removeCallbacks(mGreetingRunnable);
        mExecutor.shutdown();
        if (mAdapter != null) mAdapter.shutdown();
    }

    // ── View binding ───────────────────────────────────────────────────────────
    private void bindViews() {
        mSky            = findViewById(R.id.sky);
        mAmbient        = findViewById(R.id.ambient);
        mHeroArt        = findViewById(R.id.hero_art);
        mGreeting       = findViewById(R.id.greeting);
        mHeroBlock      = findViewById(R.id.hero_block);
        mHeroName       = findViewById(R.id.hero_name);
        mRow            = findViewById(R.id.app_row);
        mGlass          = findViewById(R.id.glass);
        mLoadingShimmer = findViewById(R.id.loading_shimmer);
        mIndicator      = findViewById(R.id.row_indicator);
        mBtnInputs      = findViewById(R.id.btn_inputs);
        mBtnWifi        = findViewById(R.id.btn_wifi);
        mBtnSettings    = findViewById(R.id.btn_settings);
    }

    // ── Greeting ───────────────────────────────────────────────────────────────
    private void setupGreeting() {
        updateGreeting();
        mGreetingRunnable = new Runnable() {
            @Override public void run() {
                updateGreeting();
                mHandler.postDelayed(this, 60_000);
            }
        };
        mHandler.postDelayed(mGreetingRunnable, 60_000);
    }

    private void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        mGreeting.setText(
            h >= 5  && h < 12 ? "GOOD MORNING"   :
            h >= 12 && h < 17 ? "GOOD AFTERNOON"  :
            h >= 17 && h < 21 ? "GOOD EVENING"    : "GOOD NIGHT");
        if (mSky != null) mSky.invalidate();
    }

    // ── Loading shimmer ────────────────────────────────────────────────────────
    private void showLoadingShimmer() {
        if (mLoadingShimmer == null) return;
        mLoadingShimmer.setVisibility(View.VISIBLE);
        mLoadingShimmer.setAlpha(1f);
        android.animation.ObjectAnimator pulse =
            android.animation.ObjectAnimator.ofFloat(
                mLoadingShimmer, "alpha", 0.2f, 1f);
        pulse.setDuration(850);
        pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulse.start();
        mLoadingShimmer.setTag(pulse);
    }

    private void hideLoadingShimmer() {
        if (mLoadingShimmer == null) return;
        Object t = mLoadingShimmer.getTag();
        if (t instanceof android.animation.ObjectAnimator)
            ((android.animation.ObjectAnimator) t).cancel();
        mLoadingShimmer.animate().alpha(0f).setDuration(300)
            .withEndAction(() -> mLoadingShimmer.setVisibility(View.GONE)).start();
    }

    // ── App loading ────────────────────────────────────────────────────────────
    private void loadApps() {
        mExecutor.execute(() -> {
            PackageManager pm     = getPackageManager();
            List<String>   pinned = Arrays.asList(PINNED);
            List<AppInfo>  result = new ArrayList<>();

            for (String pkg : PINNED) {
                AppInfo ai = loadSingle(pm, pkg);
                if (ai != null) result.add(ai);
            }

            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);
            for (ResolveInfo ri : resolved) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || pinned.contains(pkg)) continue;
                try {
                    result.add(new AppInfo(pkg,
                        ri.loadLabel(pm).toString(),
                        ri.loadIcon(pm),
                        ri.activityInfo.loadBanner(pm)));
                } catch (Exception ignored) {}
            }

            mApps = result;
            mHandler.post(() -> {
                hideLoadingShimmer();
                setupRow();
            });
        });
    }

    private AppInfo loadSingle(PackageManager pm, String pkg) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setPackage(pkg);
            i.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            List<ResolveInfo> ri = pm.queryIntentActivities(i, 0);
            if (ri.isEmpty()) {
                i = new Intent(Intent.ACTION_MAIN);
                i.setPackage(pkg);
                ri = pm.queryIntentActivities(i, 0);
            }
            if (ri.isEmpty()) return null;
            ResolveInfo r = ri.get(0);
            return new AppInfo(pkg, r.loadLabel(pm).toString(),
                    r.loadIcon(pm), r.activityInfo.loadBanner(pm));
        } catch (Exception e) { return null; }
    }

    /** Remove an uninstalled app from the row without reloading everything. */
    private void removeApp(String pkg) {
        for (int i = 0; i < mApps.size(); i++) {
            if (!mApps.get(i).packageName.equals(pkg)) continue;

            mApps.remove(i);
            if (mLastRowPos >= i) mLastRowPos = Math.max(0, mLastRowPos - 1);

            if (mAdapter != null) {
                mAdapter.notifyItemRemoved(i);
                if (!mApps.isEmpty())
                    mAdapter.notifyItemRangeChanged(i, mApps.size() - i);
            }

            if (mApps.isEmpty()) {
                mHeroName.setText("");
                mGlass.hide();
            } else {
                focusRow(Math.min(mLastRowPos, mApps.size() - 1));
            }
            return;
        }
    }

    // ── Row setup ──────────────────────────────────────────────────────────────
    private void setupRow() {        mRow.setLayoutManager(new SmoothRowLayoutManager(this));

        mAdapter = new AppRowAdapter(mApps, new AppRowAdapter.Listener() {
            @Override
            public void onClick(AppInfo app, View tileView) {
                PackageManager pm = getPackageManager();
                Intent i = pm.getLeanbackLaunchIntentForPackage(app.packageName);
                if (i == null) i = pm.getLaunchIntentForPackage(app.packageName);
                if (i == null) return;
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                LaunchAnimator.launch(MainActivity.this, tileView, app.banner, i);
            }

            /** Called synchronously on focus — no palette wait. */
            @Override
            public void onFocus(AppInfo app, View tileView, int pos) {
                mLastRowPos = pos;
                mZone       = 1;
                animateHeroName(app.label);
                // If focus moved on before this post ran, the newer onFocus
                // will place the glass — never let a stale tile win.
                mGlass.post(() -> {
                    if (tileView.isFocused()) mGlass.moveTo(tileView, 1.10f);
                });
            }

            /** Long press = manage this app (open / info / uninstall). */
            @Override
            public void onLongPress(AppInfo app, View tileView) {
                mModalOpen = true;
                mGlass.hide();
                AppActionsPopup.show(MainActivity.this, app, () -> {
                    mModalOpen = false;
                    if (isFinishing() || isDestroyed()) return;
                    if (tileView.isAttachedToWindow()) {
                        tileView.requestFocus();
                        mGlass.post(() -> mGlass.moveTo(tileView, 1.10f));
                    } else if (!mApps.isEmpty()) {
                        focusRow(Math.min(mLastRowPos, mApps.size() - 1));
                    }
                });
            }

            /** Arrives a few ms later from the palette thread. */
            @Override
            public void onAccent(int accentColor) {
                mAmbient.setAccent(accentColor);
                mSky.setAccent(accentColor);
            }

            /** Blurred banner used as the cinematic backdrop (no ghost logo). */
            @Override
            public void onBackdrop(Bitmap backdrop) {
                if (backdrop == null || isDestroyed()) return;
                mHeroArt.setImageBitmap(backdrop);
                mHeroArt.setVisibility(View.VISIBLE);
                mHeroArt.animate().alpha(0.16f).setDuration(420).start();
            }

            @Override
            public void onPositionChange(int pos, int total) {
                if (mIndicator != null) mIndicator.update(pos, total);
            }
        });
        mRow.setAdapter(mAdapter);

        mRow.setAlpha(0f);
        mRow.setTranslationY(50f);
        mRow.animate().alpha(1f).translationY(0f)
            .setDuration(420)
            .setInterpolator(new DecelerateInterpolator(2f))
            .start();

        mHeroBlock.setAlpha(0f);
        mHeroBlock.setTranslationY(32f);
        mHeroBlock.animate().alpha(1f).translationY(0f)
            .setDuration(380).setStartDelay(60)
            .setInterpolator(new DecelerateInterpolator(2f))
            .start();

        if (!mApps.isEmpty()) mHeroName.setText(mApps.get(0).label);

        restoreFocus();
    }

    // ── Hero name cross-fade ───────────────────────────────────────────────────
    private void animateHeroName(String name) {
        if (name.equals(mHeroName.getText().toString())) return;
        mHeroName.animate().cancel();
        mHeroName.setAlpha(1f);
        mHeroName.animate().alpha(0f).setDuration(80).withEndAction(() -> {
            if (isDestroyed()) return;
            mHeroName.setText(name);
            mHeroName.animate().alpha(1f).setDuration(200).start();
        }).start();
    }

    // ── Top bar ────────────────────────────────────────────────────────────────
    private void setupTopBar() {
        setupPillFocus(mBtnInputs,   0);
        setupPillFocus(mBtnWifi,     1);
        setupPillFocus(mBtnSettings, 2);

        // Inputs → input picker (stays in launcher)
        mBtnInputs.setOnClickListener(v -> InputsDropdown.show(this, mBtnInputs));
        // Wi-Fi → system Wi-Fi page (default UI)
        mBtnWifi.setOnClickListener(v -> openExternal(wifiIntents()));
        // Settings → system Settings (default UI)
        mBtnSettings.setOnClickListener(v -> openExternal(settingsIntents()));
    }

    private void setupPillFocus(View v, final int idx) {
        if (v == null) return;
        v.setOnFocusChangeListener((view, has) -> {
            if (has) {
                mZone      = 0;
                mTopBarIdx = idx;
                mRowFocusRequested = false;
                // Pills have their own inversion + scale styling; a floating
                // glass rectangle on top of them never lines up cleanly.
                mGlass.hide();
            }
            view.animate()
                .scaleX(has ? 1.06f : 1f)
                .scaleY(has ? 1.06f : 1f)
                .setDuration(130).start();
        });
    }

    // ── System screens (default UI, with fallbacks) ────────────────────────────
    private Intent[] wifiIntents() {
        return new Intent[] {
            new Intent(Settings.ACTION_WIFI_SETTINGS),
            new Intent().setComponent(new ComponentName(
                "com.android.tv.settings",
                "com.android.tv.settings.connectivity.NetworkActivity")),
            new Intent().setComponent(new ComponentName(
                "com.android.tv.settings",
                "com.android.tv.settings.MainSettings")),
            new Intent(Settings.ACTION_SETTINGS)
        };
    }

    private Intent[] settingsIntents() {
        return new Intent[] {
            new Intent(Settings.ACTION_SETTINGS),
            new Intent().setComponent(new ComponentName(
                "com.android.tv.settings",
                "com.android.tv.settings.MainSettings")),
            new Intent().setComponent(new ComponentName(
                "com.android.settings",
                "com.android.settings.Settings"))
        };
    }

    /** Try each intent until one opens. Never throws. */
    private void openExternal(Intent... candidates) {
        for (Intent i : candidates) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Exception ignored) { }
        }
    }

    // ── Focus management ───────────────────────────────────────────────────────
    private void restoreFocus() {
        mHandler.postDelayed(() -> {
            if (mZone == 0) focusTopBar(mTopBarIdx);
            else            focusRow(mLastRowPos);
        }, 300);
    }

    private void focusRow(int pos) {
        if (mAdapter == null || mApps.isEmpty()) return;
        mRowFocusRequested = true;
        int p = Math.min(Math.max(pos, 0), mApps.size() - 1);
        mRow.scrollToPosition(p);
        tryFocusRow(p, 0);
    }

    /**
     * Focus a row tile, retrying over the next few frames.
     * On a cold boot the RecyclerView often has no laid-out child yet, and the
     * window then hands focus to the first pill — so we keep re-asserting until
     * a tile really holds focus (or the user moves to the top bar).
     */
    private void tryFocusRow(final int pos, final int attempt) {
        if (isFinishing() || isDestroyed() || !mRowFocusRequested || mModalOpen) return;

        RecyclerView.ViewHolder vh = mRow.findViewHolderForAdapterPosition(pos);
        if (vh != null && vh.itemView.requestFocus()) {
            mGlass.post(() -> mGlass.moveTo(vh.itemView, 1.10f));
            // Re-assert if focus is stolen right after launch.
            mHandler.postDelayed(() -> {
                if (mRowFocusRequested && mRow.findFocus() == null)
                    tryFocusRow(pos, attempt + 1);
            }, 320);
            return;
        }
        if (attempt >= 12) return;
        mHandler.postDelayed(() -> tryFocusRow(pos, attempt + 1), 70);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || mAdapter == null) return;
        if (mRowFocusRequested && mRow.findFocus() == null) {
            focusRow(mLastRowPos);
        } else if (getCurrentFocus() == null) {
            restoreFocus();
        }
    }

    private void focusTopBar(int idx) {
        mRowFocusRequested = false;
        View[] btns = { mBtnInputs, mBtnWifi, mBtnSettings };
        if (idx >= 0 && idx < btns.length && btns[idx] != null)
            btns[idx].requestFocus();
    }

    // ── D-pad ──────────────────────────────────────────────────────────────────
    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (mModalOpen) return super.dispatchKeyEvent(ev);
        if (ev.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(ev);
        int c = ev.getKeyCode();

        if (mZone == 0) {
            if (c == KeyEvent.KEYCODE_DPAD_DOWN)                     { focusRow(mLastRowPos); return true; }
            if (c == KeyEvent.KEYCODE_DPAD_LEFT  && mTopBarIdx > 0)  { focusTopBar(--mTopBarIdx); return true; }
            if (c == KeyEvent.KEYCODE_DPAD_RIGHT && mTopBarIdx < 2)  { focusTopBar(++mTopBarIdx); return true; }
            if (c == KeyEvent.KEYCODE_DPAD_LEFT  && mTopBarIdx == 0) return true;
            if (c == KeyEvent.KEYCODE_DPAD_RIGHT && mTopBarIdx == 2) return true;
        } else {
            if (c == KeyEvent.KEYCODE_DPAD_UP)                                   { focusTopBar(mTopBarIdx); return true; }
            if (c == KeyEvent.KEYCODE_DPAD_LEFT  && mLastRowPos == 0)            return true;
            if (c == KeyEvent.KEYCODE_DPAD_RIGHT && mLastRowPos >= mApps.size()-1) return true;
        }
        return super.dispatchKeyEvent(ev);
    }
}
