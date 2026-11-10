package com.clean.launcher;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

public class AppInfo {
    public final String   packageName;
    public final String   label;
    public final Drawable icon;
    public final Drawable banner;

    /** Cached blurred backdrop, generated on first focus (palette thread). */
    public Bitmap backdrop;

    public AppInfo(String packageName, String label, Drawable icon, Drawable banner) {
        this.packageName = packageName;
        this.label       = label;
        this.icon        = icon;
        this.banner      = banner;
    }
}
