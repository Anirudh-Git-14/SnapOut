package com.example.snapout;

import android.graphics.drawable.Drawable;

public class AppSelectionModel {
    public String appName;
    public String packageName;
    public Drawable appIcon;
    public boolean isSelected;

    public AppSelectionModel(String appName, String packageName, Drawable appIcon, boolean isSelected) {
        this.appName = appName;
        this.packageName = packageName;
        this.appIcon = appIcon;
        this.isSelected = isSelected;
    }
}