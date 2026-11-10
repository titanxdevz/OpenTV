package com.clean.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/**
 * Small input picker under the "Inputs" pill.
 * Each row opens the MediaTek input setup activity with a Settings fallback.
 */
public class InputsDropdown {

    public static void show(Activity activity, View anchor) {
        List<TvInputInfo> hardware = new ArrayList<>();
        try {
            TvInputManager tim = (TvInputManager)
                    activity.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null)
                for (TvInputInfo info : tim.getTvInputList())
                    if (info.isPassthroughInput()) hardware.add(info);
        } catch (Exception ignored) { }

        View content = LayoutInflater.from(activity)
                .inflate(R.layout.dropdown_inputs, null);
        RecyclerView rv = content.findViewById(R.id.inputs_list);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        rv.setItemAnimator(null);

        final PopupWindow popup = new PopupWindow(content,
                dp(activity, 290), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(activity, 14));
        popup.setFocusable(true);

        rv.setAdapter(new InputAdapter(hardware, info -> {
            popup.dismiss();
            launch(activity, info);
        }));

        content.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && (keyCode == KeyEvent.KEYCODE_BACK
                        || keyCode == KeyEvent.KEYCODE_ESCAPE)) {
                popup.dismiss();
                return true;
            }
            return false;
        });

        popup.showAsDropDown(anchor, 0, dp(activity, 10));
        rv.postDelayed(() -> {
            if (rv.getChildCount() > 0) rv.getChildAt(0).requestFocus();
        }, 60);
    }

    private static void launch(Activity activity, TvInputInfo info) {
        Intent input = new Intent();
        input.setComponent(new android.content.ComponentName("com.mediatek.tvinput",
            info.getType() == TvInputInfo.TYPE_TUNER
                ? "com.mediatek.tvinput.tuner.TunerInputServiceSetup"
                : "com.mediatek.tvinput.hdmi.HDMIInputServiceSetup"));
        input.putExtra(TvInputInfo.EXTRA_INPUT_ID, info.getId());

        Intent[] chain = { input, new Intent(Settings.ACTION_SETTINGS) };
        for (Intent i : chain) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(i);
                return;
            } catch (Exception ignored) { }
        }
    }

    private static int dp(Context ctx, int value) {
        return (int) (value * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    // ── Adapter ───────────────────────────────────────────────────────────────
    static class InputAdapter extends RecyclerView.Adapter<InputAdapter.VH> {
        interface OnInputSelected { void onSelected(TvInputInfo info); }

        private final List<TvInputInfo> mInputs;
        private final OnInputSelected   mCallback;

        InputAdapter(List<TvInputInfo> inputs, OnInputSelected cb) {
            mInputs   = inputs;
            mCallback = cb;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_input, parent, false));
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            final TvInputInfo info = mInputs.get(position);
            holder.label.setText(info.loadLabel(holder.itemView.getContext()));

            holder.itemView.setOnClickListener(v -> mCallback.onSelected(info));
            holder.itemView.setOnFocusChangeListener((v, has) -> {
                v.animate().cancel();
                v.animate()
                    .scaleX(has ? 1.03f : 1f)
                    .scaleY(has ? 1.03f : 1f)
                    .alpha(has ? 1f : 0.80f)
                    .setDuration(has ? 170 : 120)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();
            });
        }

        @Override
        public int getItemCount() { return mInputs.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView label;
            VH(View v) {
                super(v);
                label = v.findViewById(R.id.input_label);
            }
        }
    }
}
