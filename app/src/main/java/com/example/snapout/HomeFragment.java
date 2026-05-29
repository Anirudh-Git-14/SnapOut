package com.example.snapout;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class HomeFragment extends Fragment {

    private static final String LOG_TAG = "SNAPOUT_USAGE";

    // Apps below 1 minute are counted in total but not shown in ranking list.
    private static final long MIN_DISPLAY_TIME_MS = 60_000L;

    // For UsageEvents, we look 12 hours before start time to detect apps
    // already open before midnight.
    private static final long EVENT_LOOKBACK_MS = 12L * 60L * 60L * 1000L;

    private TextView txtTotalTime;
    private TextView txtTimeLabel;
    private LinearLayout appsListContainer;
    private UsageStatsManager usageStatsManager;

    private Button btnToday, btnWeek, btnMonth, btn3M, btn6M;
    private String currentSelectedPeriod = "TODAY";

    private final Handler uiRefreshHandler = new Handler(Looper.getMainLooper());
    private Runnable uiRefreshRunnable;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_home, container, false);

        txtTotalTime = view.findViewById(R.id.txtTotalTime);
        appsListContainer = view.findViewById(R.id.appsListContainer);
        txtTimeLabel = view.findViewById(R.id.txtTimeLabel);

        if (txtTimeLabel != null) {
            txtTimeLabel.setText("SCREEN TIME");
        }

        if (getActivity() != null) {
            usageStatsManager = (UsageStatsManager)
                    getActivity().getSystemService(Context.USAGE_STATS_SERVICE);
        }

        btnToday = view.findViewById(R.id.btnToday);
        btnWeek = view.findViewById(R.id.btnWeek);
        btnMonth = view.findViewById(R.id.btnMonth);
        btn3M = view.findViewById(R.id.btn3M);
        btn6M = view.findViewById(R.id.btn6M);

        /*
         * XML button meaning:
         * btnToday  = TODAY
         * btnWeek   = YESTERDAY
         * btnMonth  = DAY BEFORE
         * btn3M     = WEEK
         * btn6M     = unused / hidden
         */

        btnToday.setOnClickListener(v -> selectPeriod("TODAY", btnToday));
        btnWeek.setOnClickListener(v -> selectPeriod("YESTERDAY", btnWeek));
        btnMonth.setOnClickListener(v -> selectPeriod("DAY_BEFORE", btnMonth));
        btn3M.setOnClickListener(v -> selectPeriod("WEEK", btn3M));

        if (btn6M != null) {
            btn6M.setVisibility(View.GONE);
        }

        checkPermission();
        setButtonActive(btnToday);

        uiRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAdded() && getActivity() != null) {
                    loadStats(currentSelectedPeriod);
                    uiRefreshHandler.postDelayed(this, 30_000L);
                }
            }
        };

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (uiRefreshRunnable != null) {
            uiRefreshHandler.removeCallbacks(uiRefreshRunnable);
            uiRefreshHandler.post(uiRefreshRunnable);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (uiRefreshRunnable != null) {
            uiRefreshHandler.removeCallbacks(uiRefreshRunnable);
        }
    }

    private void selectPeriod(String period, Button btn) {
        currentSelectedPeriod = period;
        setButtonActive(btn);
        loadStats(period);
    }

    private void setButtonActive(Button activeBtn) {

        Button[] allButtons = {btnToday, btnWeek, btnMonth, btn3M};

        for (Button btn : allButtons) {
            if (btn == null) continue;

            if (btn == activeBtn) {
                btn.setBackgroundResource(R.drawable.bg_btn_selected);
                btn.setTextColor(Color.BLACK);
            } else {
                btn.setBackgroundResource(R.drawable.bg_btn_unselected);
                btn.setTextColor(Color.WHITE);
            }
        }
    }

    private void checkPermission() {

        if (getActivity() == null) return;

        AppOpsManager appOps = (AppOpsManager)
                getActivity().getSystemService(Context.APP_OPS_SERVICE);

        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                getActivity().getPackageName()
        );

        if (mode != AppOpsManager.MODE_ALLOWED) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
    }

    private void loadStats(String period) {

        if (getActivity() == null || usageStatsManager == null) return;

        PackageManager pm = getActivity().getPackageManager();
        String launcherPackage = getLauncherPackage(pm);

        long now = System.currentTimeMillis();

        Calendar todayStartCal = Calendar.getInstance();
        todayStartCal.setTimeInMillis(now);
        resetToMidnight(todayStartCal);

        long todayStart = todayStartCal.getTimeInMillis();

        HashMap<String, Long> finalUsageMap = new HashMap<>();

        Log.d(LOG_TAG, "========== LOAD PERIOD: " + period + " ==========");

        switch (period) {

            case "TODAY": {
                /*
                 * Today:
                 * Use UsageEvents session calculation instead of aggregate stats.
                 * This avoids wrong carry-over usage near midnight.
                 */
                finalUsageMap = queryEventsUsageMap(
                        todayStart,
                        now,
                        pm,
                        launcherPackage,
                        "TODAY_EVENTS"
                );
                break;
            }

            case "YESTERDAY": {
                Calendar startCal = Calendar.getInstance();
                startCal.setTimeInMillis(todayStart);
                startCal.add(Calendar.DAY_OF_YEAR, -1);

                long startTime = startCal.getTimeInMillis();
                long endTime = todayStart;

                /*
                 * For completed past day, use UsageEvents session calculation.
                 * This avoids some queryAndAggregateUsageStats overcounting.
                 */
                finalUsageMap = queryEventsUsageMap(
                        startTime,
                        endTime,
                        pm,
                        launcherPackage,
                        "YESTERDAY_EVENTS"
                );
                break;
            }

            case "DAY_BEFORE": {
                Calendar startCal = Calendar.getInstance();
                startCal.setTimeInMillis(todayStart);
                startCal.add(Calendar.DAY_OF_YEAR, -2);

                Calendar endCal = Calendar.getInstance();
                endCal.setTimeInMillis(todayStart);
                endCal.add(Calendar.DAY_OF_YEAR, -1);

                long startTime = startCal.getTimeInMillis();
                long endTime = endCal.getTimeInMillis();

                finalUsageMap = queryEventsUsageMap(
                        startTime,
                        endTime,
                        pm,
                        launcherPackage,
                        "DAY_BEFORE_EVENTS"
                );
                break;
            }

            case "WEEK": {
                /*
                 * Important fix:
                 * Do NOT calculate week using one big aggregate query.
                 * Instead calculate each day separately and add them.
                 *
                 * If today is Monday:
                 * WEEK = Sunday + Monday
                 *
                 * Sunday is completed day → UsageEvents
                 * Today is running day → queryAndAggregateUsageStats
                 */
                finalUsageMap = queryWeekDayByDay(
                        now,
                        pm,
                        launcherPackage
                );
                break;
            }

            default: {
                finalUsageMap = queryAggregateUsageMap(
                        todayStart,
                        now,
                        pm,
                        launcherPackage,
                        "DEFAULT_TODAY_AGGREGATE"
                );
                break;
            }
        }

        buildAndShowUIFromUsageMap(finalUsageMap, pm, period);
    }

    private HashMap<String, Long> queryWeekDayByDay(long now,
                                                    PackageManager pm,
                                                    String launcherPackage) {

        HashMap<String, Long> weekMap = new HashMap<>();

        Calendar todayStartCal = Calendar.getInstance();
        todayStartCal.setTimeInMillis(now);
        resetToMidnight(todayStartCal);

        long todayStart = todayStartCal.getTimeInMillis();

        Calendar weekStartCal = Calendar.getInstance();
        weekStartCal.setTimeInMillis(todayStart);

        int dayOfWeek = weekStartCal.get(Calendar.DAY_OF_WEEK);
        int daysSinceSunday = dayOfWeek - Calendar.SUNDAY;
        weekStartCal.add(Calendar.DAY_OF_YEAR, -daysSinceSunday);

        long weekStart = weekStartCal.getTimeInMillis();

        Log.d(LOG_TAG, "WEEK day-by-day start=" + weekStart + " todayStart=" + todayStart);

        Calendar cursor = Calendar.getInstance();
        cursor.setTimeInMillis(weekStart);

        while (cursor.getTimeInMillis() < todayStart) {

            long dayStart = cursor.getTimeInMillis();

            Calendar nextDay = Calendar.getInstance();
            nextDay.setTimeInMillis(dayStart);
            nextDay.add(Calendar.DAY_OF_YEAR, 1);

            long dayEnd = nextDay.getTimeInMillis();

            HashMap<String, Long> oneDayMap = queryEventsUsageMap(
                    dayStart,
                    dayEnd,
                    pm,
                    launcherPackage,
                    "WEEK_COMPLETED_DAY_EVENTS"
            );

            mergeUsageMaps(weekMap, oneDayMap);

            cursor.add(Calendar.DAY_OF_YEAR, 1);
        }

        HashMap<String, Long> todayMap = queryEventsUsageMap(
                todayStart,
                now,
                pm,
                launcherPackage,
                "WEEK_TODAY_EVENTS"
        );

        mergeUsageMaps(weekMap, todayMap);

        return weekMap;
    }

    private HashMap<String, Long> queryAggregateUsageMap(long startTime,
                                                         long endTime,
                                                         PackageManager pm,
                                                         String launcherPackage,
                                                         String label) {

        HashMap<String, Long> usageMap = new HashMap<>();

        Log.d(LOG_TAG, "--- " + label + " start=" + startTime + " end=" + endTime + " ---");

        Map<String, UsageStats> aggregatedStats =
                usageStatsManager.queryAndAggregateUsageStats(startTime, endTime);

        if (aggregatedStats == null || aggregatedStats.isEmpty()) {
            Log.w(LOG_TAG, label + " returned empty data.");
            return usageMap;
        }

        for (Map.Entry<String, UsageStats> entry : aggregatedStats.entrySet()) {

            String packageName = entry.getKey();
            UsageStats usageStats = entry.getValue();

            if (packageName == null || usageStats == null) continue;

            long timeMs = usageStats.getTotalTimeInForeground();

            Log.d(LOG_TAG, label + " RAW pkg=" + packageName + " time=" + formatMs(timeMs));

            if (timeMs <= 0) continue;

            String excludeReason = shouldExclude(pm, packageName, launcherPackage);
            if (excludeReason != null) {
                Log.d(LOG_TAG, label + " SKIP pkg=" + packageName + " reason=" + excludeReason);
                continue;
            }

            addUsage(usageMap, packageName, timeMs);
        }

        return usageMap;
    }

    private HashMap<String, Long> queryEventsUsageMap(long startTime,
                                                      long endTime,
                                                      PackageManager pm,
                                                      String launcherPackage,
                                                      String label) {

        HashMap<String, Long> usageMap = new HashMap<>();

        long queryStart = Math.max(0L, startTime - EVENT_LOOKBACK_MS);

        Log.d(LOG_TAG, "--- " + label
                + " start=" + startTime
                + " end=" + endTime
                + " queryStart=" + queryStart + " ---");

        UsageEvents usageEvents = usageStatsManager.queryEvents(queryStart, endTime);

        UsageEvents.Event event = new UsageEvents.Event();

        String currentPackage = null;
        long currentStartTime = 0L;

        while (usageEvents.hasNextEvent()) {

            usageEvents.getNextEvent(event);

            String packageName = event.getPackageName();
            int eventType = event.getEventType();
            long eventTime = event.getTimeStamp();

            if (eventTime > endTime) break;

            if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {

                /*
                 * If another app was already considered foreground,
                 * close it at this new foreground event time.
                 */
                if (currentPackage != null && currentStartTime > 0L && eventTime > currentStartTime) {

                    long duration = Math.min(eventTime, endTime) - currentStartTime;

                    if (duration > 0) {
                        addUsage(usageMap, currentPackage, duration);
                        Log.d(LOG_TAG, label + " SESSION_CLOSE_BY_SWITCH pkg="
                                + currentPackage + " duration=" + formatMs(duration));
                    }
                }

                if (packageName != null &&
                        shouldExclude(pm, packageName, launcherPackage) == null) {

                    currentPackage = packageName;
                    currentStartTime = Math.max(eventTime, startTime);

                } else {
                    currentPackage = null;
                    currentStartTime = 0L;
                }
            }

            else if (eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {

                if (currentPackage != null &&
                        packageName != null &&
                        packageName.equals(currentPackage) &&
                        currentStartTime > 0L) {

                    long realEnd = Math.min(eventTime, endTime);
                    long duration = realEnd - currentStartTime;

                    if (duration > 0) {
                        addUsage(usageMap, currentPackage, duration);
                        Log.d(LOG_TAG, label + " SESSION pkg="
                                + currentPackage + " duration=" + formatMs(duration));
                    }

                    currentPackage = null;
                    currentStartTime = 0L;
                }
            }
        }

        /*
         * If an app was still foreground at endTime, close it at endTime.
         */
        if (currentPackage != null && currentStartTime > 0L && endTime > currentStartTime) {

            long duration = endTime - currentStartTime;

            if (duration > 0) {
                addUsage(usageMap, currentPackage, duration);
                Log.d(LOG_TAG, label + " SESSION_CLOSE_AT_END pkg="
                        + currentPackage + " duration=" + formatMs(duration));
            }
        }

        return usageMap;
    }

    private void buildAndShowUIFromUsageMap(HashMap<String, Long> usageMap,
                                            PackageManager pm,
                                            String period) {

        ArrayList<AppUsageModel> appList = new ArrayList<>();
        long totalUsageMs = 0L;

        for (Map.Entry<String, Long> entry : usageMap.entrySet()) {

            String packageName = entry.getKey();
            long timeMs = entry.getValue();

            if (timeMs <= 0) continue;

            if (timeMs < MIN_DISPLAY_TIME_MS) {
                Log.d(LOG_TAG, period + " BELOW_MIN pkg="
                        + packageName + " time=" + formatMs(timeMs));
                continue;
            }

            try {
                ApplicationInfo appInfo;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appInfo = pm.getApplicationInfo(
                            packageName,
                            PackageManager.ApplicationInfoFlags.of(0)
                    );
                } else {
                    appInfo = pm.getApplicationInfo(packageName, 0);
                }

                String appName = pm.getApplicationLabel(appInfo).toString();
                Drawable appIcon = pm.getApplicationIcon(appInfo);


                appList.add(new AppUsageModel(appName, appIcon, timeMs));
                totalUsageMs += timeMs;

                Log.d(LOG_TAG, period + " SHOW pkg=" + packageName
                        + " name=" + appName
                        + " time=" + formatMs(timeMs));

            } catch (PackageManager.NameNotFoundException e) {
                Log.d(LOG_TAG, period + " NOT_FOUND pkg=" + packageName);
            }
        }

        Collections.sort(appList, (a, b) -> Long.compare(b.usageTime, a.usageTime));

        Log.d(LOG_TAG, "=== TOTAL period=" + period
                + " appsShown=" + appList.size()
                + " total=" + formatMs(totalUsageMs)
                + " ===");

        updateUI(totalUsageMs, appList);
    }

    private void addUsage(HashMap<String, Long> map, String packageName, long durationMs) {

        if (packageName == null || durationMs <= 0) return;

        Long oldValue = map.get(packageName);

        if (oldValue == null) {
            map.put(packageName, durationMs);
        } else {
            map.put(packageName, oldValue + durationMs);
        }
    }

    private void mergeUsageMaps(HashMap<String, Long> target,
                                HashMap<String, Long> source) {

        for (Map.Entry<String, Long> entry : source.entrySet()) {
            addUsage(target, entry.getKey(), entry.getValue());
        }
    }

    private void resetToMidnight(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private String shouldExclude(PackageManager pm,
                                 String packageName,
                                 String launcherPackage) {

        switch (packageName) {

            case "android":
            case "com.android.settings":
            case "com.android.systemui":

            case "com.google.android.apps.wellbeing":
            case "com.google.android.permissioncontroller":
            case "com.android.permissioncontroller":

            case "com.google.android.packageinstaller":
            case "com.android.packageinstaller":

            case "com.samsung.android.lool":
            case "com.samsung.android.app.settings.bixby":

            case "com.sec.android.app.launcher":
            case "com.miui.home":
            case "com.oppo.launcher":
            case "com.coloros.launcher":
            case "com.google.android.apps.nexuslauncher":
            case "com.android.launcher":
            case "com.android.launcher3":

                return "system_or_launcher";
        }

        if (packageName.startsWith("com.android.providers.")) {
            return "android_provider";
        }

        if (launcherPackage != null && packageName.equals(launcherPackage)) {
            return "default_launcher";
        }

        try {
            if (pm.getLaunchIntentForPackage(packageName) == null) {
                return "no_launch_intent";
            }
        } catch (Exception e) {
            return "package_manager_error";
        }

        return null;
    }

    private String getLauncherPackage(PackageManager pm) {

        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);

            ResolveInfo resolveInfo = pm.resolveActivity(
                    homeIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
            );

            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                return resolveInfo.activityInfo.packageName;
            }

        } catch (Exception ignored) {
        }

        return null;
    }

    private void updateUI(long totalMillis, ArrayList<AppUsageModel> appList) {

        txtTotalTime.setText(formatMsDisplay(totalMillis));

        appsListContainer.removeAllViews();

        int limit = Math.min(appList.size(), 30);

        for (int i = 0; i < limit; i++) {

            AppUsageModel app = appList.get(i);

            View row = getLayoutInflater().inflate(R.layout.row_app_usage, null);

            ImageView imgIcon = row.findViewById(R.id.imgAppIcon);
            TextView txtName = row.findViewById(R.id.txtAppName);
            TextView txtTime = row.findViewById(R.id.txtAppTime);

            imgIcon.setImageDrawable(app.appIcon);
            txtName.setText(app.appName);
            txtTime.setText(formatMsDisplay(app.usageTime));

            appsListContainer.addView(row);
        }
    }

    private String formatMsDisplay(long ms) {

        long hours = ms / (1000L * 60L * 60L);
        long mins = (ms / (1000L * 60L)) % 60L;
        long secs = (ms / 1000L) % 60L;

        if (hours > 0) {
            return hours + "h " + mins + "m";
        } else if (mins > 0) {
            return mins + "m";
        } else {
            return secs + "s";
        }
    }

    private String formatMs(long ms) {

        long hours = ms / (1000L * 60L * 60L);
        long mins = (ms / (1000L * 60L)) % 60L;
        long secs = (ms / 1000L) % 60L;

        return hours + "h " + mins + "m " + secs + "s (" + ms + "ms)";
    }
}