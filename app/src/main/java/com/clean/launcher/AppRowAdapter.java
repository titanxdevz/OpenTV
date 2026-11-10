package com.clean.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppRowAdapter extends RecyclerView.Adapter<AppRowAdapter.VH> {

    public interface Listener {
        void onClick(AppInfo app, View tileView);
        /** Fired synchronously on focus — hero name + glass must not wait. */
        void onFocus(AppInfo app, View tileView, int pos);
        void onAccent(int accentColor);
        void onBackdrop(Bitmap backdrop);
        void onPositionChange(int pos, int total);
    }

    private static final RequestOptions BANNER_OPTS = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .override(320, 180)
            .format(DecodeFormat.PREFER_RGB_565)
            .centerCrop();

    private static final RequestOptions ICON_OPTS = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .override(96, 96)
            .format(DecodeFormat.PREFER_RGB_565)
            .centerInside();

    private final List<AppInfo> mApps;
    private final Listener      mL;
    private final ExecutorService mPalettePool = Executors.newSingleThreadExecutor();

    public AppRowAdapter(List<AppInfo> apps, Listener l) {
        mApps = apps;
        mL    = l;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_row_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AppInfo app = mApps.get(position);

        Glide.with(h.banner).clear(h.banner);
        Glide.with(h.icon).clear(h.icon);
        h.itemView.animate().cancel();
        h.label.animate().cancel();

        h.itemView.setScaleX(1f);
        h.itemView.setScaleY(1f);
        h.itemView.setTranslationZ(0f);
        h.label.setAlpha(0f);

        if (app.banner != null) {
            Glide.with(h.banner).load(app.banner).apply(BANNER_OPTS).into(h.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.iconWrap.setVisibility(View.GONE);
        } else {
            Glide.with(h.icon).load(app.icon).apply(ICON_OPTS).into(h.icon);
            h.iconWrap.setVisibility(View.VISIBLE);
            h.banner.setVisibility(View.GONE);
        }
        h.label.setText(app.label);

        h.itemView.setOnClickListener(v -> {
            int pos = h.getAdapterPosition();
            if (pos == RecyclerView.NO_ID) return;
            mL.onClick(app, h.itemView);
        });

        h.itemView.setOnFocusChangeListener((v, has) -> {
            final int focusPos = h.getAdapterPosition();

            if (has) {
                h.itemView.animate()
                    .scaleX(1.10f).scaleY(1.10f).translationZ(10f)
                    .setDuration(280)
                    .setInterpolator(new OvershootInterpolator(1.6f))
                    .start();
                h.label.animate().alpha(1f).setDuration(160).start();

                if (focusPos != RecyclerView.NO_ID) {
                    mL.onPositionChange(focusPos, mApps.size());

                    // Visuals first — palette/backdrop must never delay them.
                    mL.onFocus(app, h.itemView, focusPos);

                    // Private copy: never mutate the Drawable the ImageViews use.
                    final Drawable src = copyOf(
                        app.banner != null ? app.banner : app.icon);

                    if (src != null) {
                        mPalettePool.execute(() -> {
                            int accent = sampleAccent(src);
                            Bitmap bd = app.backdrop;
                            if (bd == null) {
                                bd = blurredBackdrop(src);
                                app.backdrop = bd;
                            }
                            final Bitmap outBd = bd;

                            if (v.isAttachedToWindow()) {
                                v.post(() -> {
                                    if (h.getAdapterPosition() == focusPos) {
                                        mL.onAccent(accent);
                                        if (outBd != null) mL.onBackdrop(outBd);
                                    }
                                });
                            }
                        });
                    }
                }
            } else {
                h.itemView.animate()
                    .scaleX(1f).scaleY(1f).translationZ(0f)
                    .setDuration(180).start();
                h.label.animate().alpha(0f).setDuration(130).start();
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull VH h) {
        super.onViewRecycled(h);
        Glide.with(h.banner).clear(h.banner);
        Glide.with(h.icon).clear(h.icon);
        h.itemView.animate().cancel();
        h.label.animate().cancel();
        h.itemView.setScaleX(1f);
        h.itemView.setScaleY(1f);
        h.itemView.setTranslationZ(0f);
        h.label.setAlpha(0f);
    }

    /** Drawable instances are shared — take an independent copy before mutating. */
    private static Drawable copyOf(Drawable d) {
        if (d == null) return null;
        try {
            Drawable.ConstantState cs = d.getConstantState();
            return cs != null ? cs.newDrawable().mutate() : d;
        } catch (Exception e) {
            return d;
        }
    }

    /** Sample the WHOLE artwork (banner/icon) downscaled to 64×36. */
    private int sampleAccent(Drawable d) {
        if (d == null) return 0;
        Bitmap bmp = null;
        try {
            bmp = Bitmap.createBitmap(64, 36, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(bmp);
            d.setBounds(0, 0, 64, 36);
            d.draw(c);

            Palette p = Palette.from(bmp).maximumColorCount(8).generate();
            int color = p.getVibrantColor(
                        p.getDarkVibrantColor(
                        p.getDominantColor(Color.DKGRAY)));
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            hsv[1] = Math.min(hsv[1], 0.90f);
            hsv[2] = 0.26f;
            return Color.HSVToColor(hsv);
        } catch (Exception e) {
            return 0;
        } finally {
            if (bmp != null) bmp.recycle();
        }
    }

    /** Tiny → large upscale with bilinear filtering = cheap, smooth blur. */
    private Bitmap blurredBackdrop(Drawable d) {
        if (d == null) return null;
        Bitmap small = null, big = null;
        try {
            small = Bitmap.createBitmap(28, 16, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(small);
            d.setBounds(0, 0, 28, 16);
            d.draw(c);

            big = Bitmap.createBitmap(240, 135, Bitmap.Config.RGB_565);
            Canvas c2 = new Canvas(big);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            c2.drawBitmap(small, null, new Rect(0, 0, 240, 135), p);
            return big;
        } catch (Exception e) {
            if (big != null) big.recycle();
            return null;
        } finally {
            if (small != null) small.recycle();
        }
    }

    public void shutdown() { mPalettePool.shutdown(); }

    @Override public int getItemCount() { return mApps.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView banner, icon;
        View      iconWrap;
        TextView  label;
        VH(View v) {
            super(v);
            banner   = v.findViewById(R.id.row_app_banner);
            icon     = v.findViewById(R.id.row_app_icon);
            iconWrap = v.findViewById(R.id.row_app_icon_wrap);
            label    = v.findViewById(R.id.row_app_label);
        }
    }
}
