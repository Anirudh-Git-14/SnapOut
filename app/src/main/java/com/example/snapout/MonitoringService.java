package com.example.snapout;

// CRITICAL FIX: Explicitly importing your project resource pointer to clear "Cannot resolve symbol 'R'"
import com.example.snapout.R;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.view.ContextThemeWrapper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

public class MonitoringService extends Service {

    private WindowManager windowManager;
    private View overlayView;
    private boolean isOverlayVisible = false;

    private long lastInterventionLaunchTime = 0;
    private static final String TAG = "SnapOut_Engine";
    private static final String PREFS_NAME = "SnapOutFocusPrefs";
    private static final String KEY_BLOCKED_APPS = "blocked_packages_key";
    private static final String KEY_FOCUS_MODE_ACTIVE = "focus_mode_active_key";
    private static final String KEY_ANALYTICS_DATE = "analytics_date_key";
    private static final String KEY_BLOCKED_ATTEMPTS = "analytics_blocked_attempts_today";
    private static final String KEY_CONTINUE_CLICKS = "analytics_continue_clicks_today";
    private static final String KEY_TRIGGER_PREFIX = "trigger_count_";
    private static final String KEY_CONTINUE_PREFIX = "continue_count_";
    private static final String KEY_LOCKIN_PREFIX = "lockin_count_";
    private static final String KEY_OPEN_SNAPOUT_PREFIX = "open_snapout_count_";
    private static final String KEY_DAILY_LIMIT_MINUTES = "daily_limit_minutes_key";
    private static final String CHANNEL_ID = "SnapOut_Monitor_Channel";

    private static final int NOTIFICATION_ID = 99;

    // Performance Optimization: Moved the bypass tracking window to a strict class constant
    private static final long BYPASS_DURATION_MS = 600_000; // for 10 Minutes

    private HandlerThread backgroundThread;
    private Handler monitoringHandler;
    private Runnable monitoringRunnable;
    private UsageStatsManager usageStatsManager;
    private SharedPreferences prefs;
    private PowerManager powerManager;

    private String cachedSelfPackageName = "";
    private final Set<String> homeLauncherPackages = new HashSet<>();
    private boolean isLoopRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        cachedSelfPackageName = getPackageName();
        resetAnalyticsIfNewDay();

        cacheHomeLaunchers();

        backgroundThread = new HandlerThread("SnapOutMonitorThread");
        backgroundThread.start();
        monitoringHandler = new Handler(backgroundThread.getLooper());

        createNotificationChannel();
        startForegroundServiceNotification();

        startMonitoringLoop();
    }

    private void cacheHomeLaunchers() {
        homeLauncherPackages.clear();
        homeLauncherPackages.add("com.android.settings");
        homeLauncherPackages.add("com.android.systemui");
        homeLauncherPackages.add("android");

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        PackageManager pm = getPackageManager();
        if (pm != null) {
            try {
                for (ResolveInfo ri : pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
                    if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                        homeLauncherPackages.add(ri.activityInfo.packageName);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error querying launcher packages", e);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SnapOut Brainrot Defense Shield",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundServiceNotification() {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("SNAPOUT SHIELD ACTIVE")
                    .setContentText("Monitoring restriction access parameters.")
                    .setSmallIcon(R.drawable.ic_snapout_notification)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE);
            } else {
                startForeground(NOTIFICATION_ID, builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize foreground notification execution: ", e);
        }
    }


    private synchronized void startMonitoringLoop() {
        if (isLoopRunning) return;
        isLoopRunning = true;

        monitoringRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (prefs == null) return;

                    boolean isFocusActive = prefs.getBoolean(KEY_FOCUS_MODE_ACTIVE, false);

                    if (powerManager == null || powerManager.isInteractive()) {
                        String currentForegroundApp = getForegroundPackageName();

                        if (isFocusActive && currentForegroundApp != null && !currentForegroundApp.isEmpty()) {
                            cleanExpiredBypasses();
                            evaluateAppTarget(currentForegroundApp);
                        }
                    }

                    if (monitoringHandler != null) {
                        monitoringHandler.postDelayed(this, 1000);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Loop exception occurred: ", e);
                }
            }
        };

        if (monitoringHandler != null) {
            monitoringHandler.post(monitoringRunnable);
        }
    }

    private String getForegroundPackageName() {
        try {
            if (usageStatsManager == null) return "";
            long time = System.currentTimeMillis();

            List<UsageStats> stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60 * 60 * 2, time);
            if (stats == null || stats.isEmpty()) return "";

            UsageStats recentStats = null;
            for (UsageStats usageStats : stats) {
                if (recentStats == null || usageStats.getLastTimeUsed() > recentStats.getLastTimeUsed()) {
                    recentStats = usageStats;
                }
            }

            if (recentStats == null || recentStats.getTotalTimeInForeground() == 0) return "";
            String detectedPackage = recentStats.getPackageName();

            if (homeLauncherPackages.contains(detectedPackage) || detectedPackage.equals(cachedSelfPackageName)) {
                return "";
            }

            return detectedPackage;
        } catch (Exception e) {
            return "";
        }
    }
    private long getTodayUsageForPackage(String packageName) {
        try {
            if (usageStatsManager == null || packageName == null) return 0;

            long endTime = System.currentTimeMillis();

            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(endTime);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            long startTime = calendar.getTimeInMillis();

            Map<String, UsageStats> statsMap =
                    usageStatsManager.queryAndAggregateUsageStats(startTime, endTime);

            if (statsMap == null || !statsMap.containsKey(packageName)) {
                return 0;
            }

            UsageStats stats = statsMap.get(packageName);

            if (stats == null) return 0;

            return stats.getTotalTimeInForeground();

        } catch (Exception e) {
            Log.e(TAG, "Error getting today's usage for: " + packageName, e);
            return 0;
        }
    }

    private void evaluateAppTarget(String packageName) {
        // If overlay is already showing, don't show another one
        if (overlayView != null && overlayView.isAttachedToWindow()) return;

        Set<String> blockedApps = prefs.getStringSet(KEY_BLOCKED_APPS, new HashSet<>());

        // If current app is not selected in Focus Mode, ignore it
        if (!blockedApps.contains(packageName)) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Check temporary bypass first
        long bypassExpiry = prefs.getLong("bypass_timestamp_" + packageName, 0);
        if (currentTime < bypassExpiry) {
            return;
        }

        // Get selected daily limit from FocusFragment
        int dailyLimitMinutes = prefs.getInt(KEY_DAILY_LIMIT_MINUTES, 60);
        long dailyLimitMillis = dailyLimitMinutes * 60L * 1000L;

        // Get today's usage for this app
        long todayUsageMillis = getTodayUsageForPackage(packageName);

        Log.d(TAG, "App: " + packageName
                + " | Today usage min: " + (todayUsageMillis / 60000)
                + " | Limit min: " + dailyLimitMinutes);

        // If app usage is still below selected limit, don't show popup
        if (todayUsageMillis < dailyLimitMillis) {
            return;
        }

        // Small cooldown to avoid launching overlay many times quickly
        if (currentTime - lastInterventionLaunchTime < 3000) return;

        try {
            lastInterventionLaunchTime = currentTime;
            new Handler(Looper.getMainLooper()).post(() -> showOverlay(packageName));
        } catch (Exception e) {
            Log.e(TAG, "Failed window layer instantiation overlay: ", e);
        }
    }

    private void cleanExpiredBypasses() {
        if (prefs == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        boolean stateChanged = false;

        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("bypass_timestamp_")) {
                try {
                    long expiryTime = prefs.getLong(key, 0);
                    if (System.currentTimeMillis() >= expiryTime) {
                        editor.remove(key);
                        stateChanged = true;
                    }
                } catch (Exception e) {
                    // Safety trap
                }
            }
        }

        if (stateChanged) {
            editor.apply();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "ACTION_STOP_SNAPOUT_SERVICE".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        hideOverlay();
        synchronized (this) {
            isLoopRunning = false;
        }
        if (monitoringHandler != null && monitoringRunnable != null) {
            monitoringHandler.removeCallbacks(monitoringRunnable);
        }
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }



    private void loadAppDataAsync(String packageName, TextView textName, ImageView imgIcon) {
        new Thread(() -> {
            try {
                PackageManager pm = getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
                String label = pm.getApplicationLabel(ai).toString().toUpperCase();
                Drawable icon = pm.getApplicationIcon(ai);

                // Thread Handshake: Push tasks out of disk IO worker pools cleanly into UI thread loops
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (textName != null) textName.setText(label);
                    if (imgIcon != null) imgIcon.setImageDrawable(icon);
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (textName != null) textName.setText(packageName.toUpperCase());
                });
            }
        }).start();
    }
    private int getTodayKey() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
        return year * 1000 + dayOfYear;
    }

    private void resetAnalyticsIfNewDay() {
        if (prefs == null) return;

        int todayKey = getTodayKey();
        int savedKey = prefs.getInt(KEY_ANALYTICS_DATE, -1);

        if (savedKey != todayKey) {
            SharedPreferences.Editor editor = prefs.edit();

            editor.putInt(KEY_ANALYTICS_DATE, todayKey);
            editor.putInt(KEY_BLOCKED_ATTEMPTS, 0);
            editor.putInt(KEY_CONTINUE_CLICKS, 0);

            for (String key : prefs.getAll().keySet()) {
                if (key.startsWith(KEY_TRIGGER_PREFIX)
                        || key.startsWith(KEY_CONTINUE_PREFIX)
                        || key.startsWith(KEY_LOCKIN_PREFIX)
                        || key.startsWith(KEY_OPEN_SNAPOUT_PREFIX)) {
                    editor.remove(key);
                }
            }

            editor.apply();
        }
    }

    private void recordBlockedAttempt(String packageName) {
        if (prefs == null || packageName == null) return;

        resetAnalyticsIfNewDay();

        int oldAttempts = prefs.getInt(KEY_BLOCKED_ATTEMPTS, 0);
        int oldAppCount = prefs.getInt(KEY_TRIGGER_PREFIX + packageName, 0);

        prefs.edit()
                .putInt(KEY_BLOCKED_ATTEMPTS, oldAttempts + 1)
                .putInt(KEY_TRIGGER_PREFIX + packageName, oldAppCount + 1)
                .apply();

        Log.d(TAG, "Analytics blocked attempt recorded for: " + packageName);
    }

    private void recordContinueClick(String packageName) {
        if (prefs == null || packageName == null) return;

        resetAnalyticsIfNewDay();

        int oldGlobalCount = prefs.getInt(KEY_CONTINUE_CLICKS, 0);
        int oldAppCount = prefs.getInt(KEY_CONTINUE_PREFIX + packageName, 0);

        prefs.edit()
                .putInt(KEY_CONTINUE_CLICKS, oldGlobalCount + 1)
                .putInt(KEY_CONTINUE_PREFIX + packageName, oldAppCount + 1)
                .apply();

        Log.d(TAG, "Analytics continue click recorded for: " + packageName);
    }
    private void recordLockInClick(String packageName) {
        if (prefs == null || packageName == null) return;

        resetAnalyticsIfNewDay();

        int oldAppCount = prefs.getInt(KEY_LOCKIN_PREFIX + packageName, 0);

        prefs.edit()
                .putInt(KEY_LOCKIN_PREFIX + packageName, oldAppCount + 1)
                .apply();

        Log.d(TAG, "Analytics Lock In click recorded for: " + packageName);
    }

    private void recordOpenSnapOutClick(String packageName) {
        if (prefs == null || packageName == null) return;

        resetAnalyticsIfNewDay();

        int oldAppCount = prefs.getInt(KEY_OPEN_SNAPOUT_PREFIX + packageName, 0);

        prefs.edit()
                .putInt(KEY_OPEN_SNAPOUT_PREFIX + packageName, oldAppCount + 1)
                .apply();

        Log.d(TAG, "Analytics Open SnapOut click recorded for: " + packageName);
    }
    private void showOverlay(String packageName) {
        if (overlayView != null && overlayView.isAttachedToWindow()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Draw overlay operations aborted: Permission denied.");
            return;
        }

        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

            // FIXED HERE: Using the built-in system DeviceDefault theme removes the 'Theme_AppCompat' crash completely
            ContextThemeWrapper themedContext = new ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Light_NoActionBar);
            overlayView = LayoutInflater.from(themedContext).inflate(R.layout.activity_intervention, null);

            int layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT
            );

            AppCompatButton btnStartSession = overlayView.findViewById(R.id.btnStartSession);
            AppCompatButton btnReturnToFocus = overlayView.findViewById(R.id.btnReturnToFocus);
            TextView txtContinueAnyway = overlayView.findViewById(R.id.txtContinueAnyway);
            TextView txtTargetAppName = overlayView.findViewById(R.id.txtTargetAppName);
            ImageView imgTargetIcon = overlayView.findViewById(R.id.imgTargetIcon);

            loadAppDataAsync(packageName, txtTargetAppName, imgTargetIcon);

            btnStartSession.setOnClickListener(v -> {
                recordLockInClick(packageName);

                hideOverlay();

                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(homeIntent);
            });

            btnReturnToFocus.setOnClickListener(v -> {
                recordOpenSnapOutClick(packageName);

                hideOverlay();

                Intent snapOutIntent = new Intent(this, MainActivity.class);
                snapOutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(snapOutIntent);
            });

            txtContinueAnyway.setOnClickListener(v -> {
                recordContinueClick(packageName);

                long bypassExpiryTime = System.currentTimeMillis() + BYPASS_DURATION_MS;
                prefs.edit().putLong("bypass_timestamp_" + packageName, bypassExpiryTime).apply();

                hideOverlay();
            });

            windowManager.addView(overlayView, params);
            isOverlayVisible = true;

            recordBlockedAttempt(packageName);
        } catch (Exception e) {
            Log.e(TAG, "Failed window layer insertion operation: ", e);
        }
    }

    private void hideOverlay() {
        if (windowManager != null && overlayView != null && overlayView.isAttachedToWindow()) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                Log.e(TAG, "Error removing window view structure: ", e);
            }
        }
        overlayView = null;
        isOverlayVisible = false;
    }
}