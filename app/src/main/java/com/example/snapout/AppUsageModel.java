package com.example.snapout;

import android.graphics.drawable.Drawable;

public class AppUsageModel {
    public String appName;
    public Drawable appIcon;
    public long usageTime; // in milliseconds

    public AppUsageModel(String appName, Drawable appIcon, long usageTime) {
        this.appName = appName;
        this.appIcon = appIcon;
        this.usageTime = usageTime;
    }
}