package com.example.snapout;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.HashSet;

public class SettingsFragment extends Fragment {

    private TextView txtUsagePermissionStatus;
    private TextView txtOverlayPermissionStatus;
    private TextView txtNotificationPermissionStatus;

    private LinearLayout cardResetAnalytics;
    private LinearLayout cardClearSelectedApps;

    private SharedPreferences prefs;

    private static final String PREFS_NAME = "SnapOutFocusPrefs";

    private static final String KEY_BLOCKED_APPS = "blocked_packages_key";
    private static final String KEY_FOCUS_MODE_ACTIVE = "focus_mode_active_key";

    private static final String KEY_BLOCKED_ATTEMPTS = "analytics_blocked_attempts_today";
    private static final String KEY_CONTINUE_CLICKS = "analytics_continue_clicks_today";

    private static final String KEY_TRIGGER_PREFIX = "trigger_count_";
    private static final String KEY_CONTINUE_PREFIX = "continue_count_";
    private static final String KEY_LOCKIN_PREFIX = "lockin_count_";
    private static final String KEY_OPEN_SNAPOUT_PREFIX = "open_snapout_count_";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        txtUsagePermissionStatus = view.findViewById(R.id.txtUsagePermissionStatus);
        txtOverlayPermissionStatus = view.findViewById(R.id.txtOverlayPermissionStatus);
        txtNotificationPermissionStatus = view.findViewById(R.id.txtNotificationPermissionStatus);

        cardResetAnalytics = view.findViewById(R.id.cardResetAnalytics);
        cardClearSelectedApps = view.findViewById(R.id.cardClearSelectedApps);

        if (getActivity() != null) {
            prefs = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }

        updatePermissionStatus();

        cardResetAnalytics.setOnClickListener(v -> {
            resetAnalyticsData();
            Toast.makeText(getActivity(), "Analytics reset successfully", Toast.LENGTH_SHORT).show();
        });

        cardClearSelectedApps.setOnClickListener(v -> {
            clearSelectedApps();
            Toast.makeText(getActivity(), "Selected apps cleared", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePermissionStatus();
    }

    private void updatePermissionStatus() {
        if (getActivity() == null) return;

        if (hasUsageAccessPermission()) {
            txtUsagePermissionStatus.setText("Usage Access: Allowed");
            txtUsagePermissionStatus.setTextColor(0xFF00F0FF);
        } else {
            txtUsagePermissionStatus.setText("Usage Access: Not allowed");
            txtUsagePermissionStatus.setTextColor(0xFFFF0055);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(getActivity())) {
            txtOverlayPermissionStatus.setText("Overlay Permission: Allowed");
            txtOverlayPermissionStatus.setTextColor(0xFF00F0FF);
        } else {
            txtOverlayPermissionStatus.setText("Overlay Permission: Not allowed");
            txtOverlayPermissionStatus.setTextColor(0xFFFF0055);
        }

        if (hasNotificationPermission()) {
            txtNotificationPermissionStatus.setText("Notification Permission: Allowed");
            txtNotificationPermissionStatus.setTextColor(0xFF00F0FF);
        } else {
            txtNotificationPermissionStatus.setText("Notification Permission: Not allowed");
            txtNotificationPermissionStatus.setTextColor(0xFFFF0055);
        }
    }

    private boolean hasUsageAccessPermission() {
        if (getActivity() == null) return false;

        try {
            AppOpsManager appOps = (AppOpsManager) getActivity().getSystemService(Context.APP_OPS_SERVICE);

            int mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    getActivity().getPackageName()
            );

            return mode == AppOpsManager.MODE_ALLOWED;

        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasNotificationPermission() {
        if (getActivity() == null) return false;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        return ContextCompat.checkSelfPermission(
                getActivity(),
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void resetAnalyticsData() {
        if (prefs == null) return;

        SharedPreferences.Editor editor = prefs.edit();

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

    private void clearSelectedApps() {
        if (prefs == null || getActivity() == null) return;

        prefs.edit()
                .putStringSet(KEY_BLOCKED_APPS, new HashSet<>())
                .putBoolean(KEY_FOCUS_MODE_ACTIVE, false)
                .apply();

        Intent serviceIntent = new Intent(getActivity(), MonitoringService.class);
        serviceIntent.setAction("ACTION_STOP_SNAPOUT_SERVICE");
        getActivity().startService(serviceIntent);
    }
}