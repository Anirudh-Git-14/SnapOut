package com.example.snapout;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AnalyticsFragment extends Fragment {

    private TextView txtFocusStatus;
    private TextView txtSelectedApps;
    private TextView txtDailyLimit;
    private TextView txtBlockedAttempts;
    private TextView txtContinueCount;
    private TextView txtMostTriggeredApp;
    private TextView txtNoAppBreakdown;
    private LinearLayout layoutAppBreakdownContainer;

    private SharedPreferences prefs;

    private static final String PREFS_NAME = "SnapOutFocusPrefs";
    private static final String KEY_BLOCKED_APPS = "blocked_packages_key";
    private static final String KEY_FOCUS_MODE_ACTIVE = "focus_mode_active_key";
    private static final String KEY_DAILY_LIMIT_MINUTES = "daily_limit_minutes_key";

    private static final String KEY_BLOCKED_ATTEMPTS = "analytics_blocked_attempts_today";
    private static final String KEY_CONTINUE_CLICKS = "analytics_continue_clicks_today";

    private static final String KEY_TRIGGER_PREFIX = "trigger_count_";
    private static final String KEY_CONTINUE_PREFIX = "continue_count_";
    private static final String KEY_LOCKIN_PREFIX = "lockin_count_";
    private static final String KEY_OPEN_SNAPOUT_PREFIX = "open_snapout_count_";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_analytics, container, false);

        txtFocusStatus = view.findViewById(R.id.txtFocusStatus);
        txtSelectedApps = view.findViewById(R.id.txtSelectedApps);
        txtDailyLimit = view.findViewById(R.id.txtDailyLimit);
        txtBlockedAttempts = view.findViewById(R.id.txtBlockedAttempts);
        txtContinueCount = view.findViewById(R.id.txtContinueCount);
        txtMostTriggeredApp = view.findViewById(R.id.txtMostTriggeredApp);

        layoutAppBreakdownContainer = view.findViewById(R.id.layoutAppBreakdownContainer);
        txtNoAppBreakdown = view.findViewById(R.id.txtNoAppBreakdown);

        if (getActivity() != null) {
            prefs = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }

        updateAnalyticsUI();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAnalyticsUI();
    }

    private void updateAnalyticsUI() {
        if (prefs == null) return;

        boolean isFocusActive = prefs.getBoolean(KEY_FOCUS_MODE_ACTIVE, false);

        if (isFocusActive) {
            txtFocusStatus.setText("Focus Mode: On");
            txtFocusStatus.setTextColor(0xFF00F0FF);
        } else {
            txtFocusStatus.setText("Focus Mode: Off");
            txtFocusStatus.setTextColor(0xFF8A8A95);
        }

        Set<String> selectedApps = prefs.getStringSet(KEY_BLOCKED_APPS, new HashSet<>());
        int selectedCount = selectedApps == null ? 0 : selectedApps.size();
        txtSelectedApps.setText("Selected apps: " + selectedCount);

        int dailyLimitMinutes = prefs.getInt(KEY_DAILY_LIMIT_MINUTES, 60);
        txtDailyLimit.setText("Daily limit: " + formatMinutes(dailyLimitMinutes));

        int blockedAttempts = prefs.getInt(KEY_BLOCKED_ATTEMPTS, 0);
        int continueClicks = prefs.getInt(KEY_CONTINUE_CLICKS, 0);

        txtBlockedAttempts.setText(String.valueOf(blockedAttempts));
        txtContinueCount.setText(String.valueOf(continueClicks));

        txtMostTriggeredApp.setText(getMostTriggeredAppName());

        updateAppWiseBreakdown();
    }

    private void updateAppWiseBreakdown() {
        if (prefs == null || layoutAppBreakdownContainer == null) return;

        layoutAppBreakdownContainer.removeAllViews();

        Set<String> packageSet = getAllAnalyticsPackages();

        if (packageSet.isEmpty()) {
            txtNoAppBreakdown.setVisibility(View.VISIBLE);
            layoutAppBreakdownContainer.addView(txtNoAppBreakdown);
            return;
        }

        txtNoAppBreakdown.setVisibility(View.GONE);

        ArrayList<String> packageList = new ArrayList<>(packageSet);

        Collections.sort(packageList, (a, b) -> {
            int countA = prefs.getInt(KEY_TRIGGER_PREFIX + a, 0);
            int countB = prefs.getInt(KEY_TRIGGER_PREFIX + b, 0);
            return Integer.compare(countB, countA);
        });

        for (String packageName : packageList) {
            int popupCount = prefs.getInt(KEY_TRIGGER_PREFIX + packageName, 0);
            int continueCount = prefs.getInt(KEY_CONTINUE_PREFIX + packageName, 0);
            int lockInCount = prefs.getInt(KEY_LOCKIN_PREFIX + packageName, 0);
            int openSnapOutCount = prefs.getInt(KEY_OPEN_SNAPOUT_PREFIX + packageName, 0);

            View row = createAppBreakdownRow(
                    getAppName(packageName),
                    popupCount,
                    continueCount,
                    lockInCount,
                    openSnapOutCount
            );

            layoutAppBreakdownContainer.addView(row);
        }
    }

    private View createAppBreakdownRow(String appName,
                                       int popupCount,
                                       int continueCount,
                                       int lockInCount,
                                       int openSnapOutCount) {

        LinearLayout row = new LinearLayout(getActivity());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackgroundColor(0xFF15151C);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, dp(12));
        row.setLayoutParams(rowParams);

        TextView txtAppName = new TextView(getActivity());
        txtAppName.setText(appName);
        txtAppName.setTextColor(0xFFE6E6EA);
        txtAppName.setTextSize(16);
        txtAppName.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView txtStatsOne = new TextView(getActivity());
        txtStatsOne.setText("Popups: " + popupCount + "   •   Continue: " + continueCount);
        txtStatsOne.setTextColor(0xFF8A8A95);
        txtStatsOne.setTextSize(13);
        txtStatsOne.setPadding(0, dp(8), 0, 0);

        TextView txtStatsTwo = new TextView(getActivity());
        txtStatsTwo.setText("Return To Home: " + lockInCount + "   •   Open SnapOut: " + openSnapOutCount);
        txtStatsTwo.setTextColor(0xFF8A8A95);
        txtStatsTwo.setTextSize(13);
        txtStatsTwo.setPadding(0, dp(4), 0, 0);

        row.addView(txtAppName);
        row.addView(txtStatsOne);
        row.addView(txtStatsTwo);

        return row;
    }

    private Set<String> getAllAnalyticsPackages() {
        Set<String> packageSet = new HashSet<>();

        Map<String, ?> allPrefs = prefs.getAll();

        for (String key : allPrefs.keySet()) {
            if (key.startsWith(KEY_TRIGGER_PREFIX)) {
                packageSet.add(key.replace(KEY_TRIGGER_PREFIX, ""));
            } else if (key.startsWith(KEY_CONTINUE_PREFIX)) {
                packageSet.add(key.replace(KEY_CONTINUE_PREFIX, ""));
            } else if (key.startsWith(KEY_LOCKIN_PREFIX)) {
                packageSet.add(key.replace(KEY_LOCKIN_PREFIX, ""));
            } else if (key.startsWith(KEY_OPEN_SNAPOUT_PREFIX)) {
                packageSet.add(key.replace(KEY_OPEN_SNAPOUT_PREFIX, ""));
            }
        }

        return packageSet;
    }

    private String getMostTriggeredAppName() {
        if (prefs == null || getActivity() == null) return "No data yet";

        Map<String, ?> allPrefs = prefs.getAll();

        String topPackage = null;
        int topCount = 0;

        for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
            String key = entry.getKey();

            if (key.startsWith(KEY_TRIGGER_PREFIX)) {
                Object value = entry.getValue();

                if (value instanceof Integer) {
                    int count = (Integer) value;

                    if (count > topCount) {
                        topCount = count;
                        topPackage = key.replace(KEY_TRIGGER_PREFIX, "");
                    }
                }
            }
        }

        if (topPackage == null || topCount == 0) {
            return "No data yet";
        }

        return getAppName(topPackage) + " (" + topCount + ")";
    }

    private String getAppName(String packageName) {
        if (getActivity() == null) return packageName;

        try {
            PackageManager pm = getActivity().getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(appInfo).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    private String formatMinutes(int minutes) {
        if (minutes < 60) {
            return minutes + " min";
        }

        int hours = minutes / 60;
        int remainingMinutes = minutes % 60;

        if (remainingMinutes == 0) {
            return hours + " hr";
        }

        return hours + " hr " + remainingMinutes + " min";
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}