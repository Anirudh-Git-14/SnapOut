package com.example.snapout;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import android.net.Uri;
import android.provider.Settings;
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.AppCompatButton;
import android.widget.Toast;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import android.util.Log;
import java.util.Set;

public class FocusFragment extends Fragment implements AppSelectionAdapter.OnAppSelectionChangedListener {

    private RecyclerView recyclerAppSelection;
    private TextView txtSelectedCount;
    private TextView txtToggleStatus;
    private TextView txtDefenseMeme;
    private SwitchCompat switchFocusMode;
    private TextView txtDailyLimit;
    private AppCompatButton btnLimitMinus;
    private AppCompatButton btnLimitPlus;

    private int selectedLimitMinutes = 60;

    private static final String KEY_DAILY_LIMIT_MINUTES = "daily_limit_minutes_key";
    private static final int MIN_LIMIT_MINUTES = 30;
    private static final int MAX_LIMIT_MINUTES = 300; // 5 hours
    private static final int LIMIT_STEP_MINUTES = 30;

    private AppSelectionAdapter adapter;
    private ArrayList<AppSelectionModel> displayAppsList;
    private Set<String> blockedPackagesSet;
    private SharedPreferences prefs;

    private static final String PREFS_NAME = "SnapOutFocusPrefs";
    private static final String KEY_BLOCKED_APPS = "blocked_packages_key";
    private static final String KEY_FOCUS_MODE_ACTIVE = "focus_mode_active_key";

    // Modern Android API replacement for permission handling
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d("SnapOut_Engine", "Notification permissions granted by user.");
                    if (getActivity() != null) {
                        Toast.makeText(getActivity(), "SHIELD DEFENSES AUTHORIZED", Toast.LENGTH_SHORT).show();
                    }
                    if (switchFocusMode != null) {
                        prefs.edit().putBoolean(KEY_FOCUS_MODE_ACTIVE, true).apply();
                        switchFocusMode.setChecked(true);
                        syncFocusToggleUI(true);
                        manageMonitoringService(true);
                    }
                } else {
                    Log.w("SnapOut_Engine", "Notification permissions denied by user.");
                    if (getActivity() != null) {
                        Toast.makeText(getActivity(), "Permission Denied. Cannot arm shield without notification view.", Toast.LENGTH_LONG).show();
                    }
                    if (switchFocusMode != null) {
                        switchFocusMode.setChecked(false);
                        syncFocusToggleUI(false);
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_focus, container, false);

        recyclerAppSelection = view.findViewById(R.id.recyclerAppSelection);
        txtSelectedCount = view.findViewById(R.id.txtSelectedCount);
        txtToggleStatus = view.findViewById(R.id.txtToggleStatus);
        txtDefenseMeme = view.findViewById(R.id.txtDefenseMeme);
        switchFocusMode = view.findViewById(R.id.switchFocusMode);
        txtDailyLimit = view.findViewById(R.id.txtDailyLimit);
        btnLimitMinus = view.findViewById(R.id.btnLimitMinus);
        btnLimitPlus = view.findViewById(R.id.btnLimitPlus);

        recyclerAppSelection.setLayoutManager(new LinearLayoutManager(getActivity()));
        displayAppsList = new ArrayList<>();

        prefs = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        selectedLimitMinutes = prefs.getInt(KEY_DAILY_LIMIT_MINUTES, 60);
        updateDailyLimitUI();

        btnLimitMinus.setOnClickListener(v -> {
            if (selectedLimitMinutes > MIN_LIMIT_MINUTES) {
                selectedLimitMinutes -= LIMIT_STEP_MINUTES;
                prefs.edit().putInt(KEY_DAILY_LIMIT_MINUTES, selectedLimitMinutes).apply();
                updateDailyLimitUI();
            }
        });

        btnLimitPlus.setOnClickListener(v -> {
            if (selectedLimitMinutes < MAX_LIMIT_MINUTES) {
                selectedLimitMinutes += LIMIT_STEP_MINUTES;
                prefs.edit().putInt(KEY_DAILY_LIMIT_MINUTES, selectedLimitMinutes).apply();
                updateDailyLimitUI();
            }
        });

        Set<String> savedSet = prefs.getStringSet(KEY_BLOCKED_APPS, new HashSet<>());
        blockedPackagesSet = new HashSet<>(savedSet);

        boolean isFocusActive = prefs.getBoolean(KEY_FOCUS_MODE_ACTIVE, false);
        switchFocusMode.setChecked(isFocusActive);

        syncFocusToggleUI(isFocusActive);
        updateCountUI();

        // Handle background tracking orchestration on switch toggle state change
        switchFocusMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                android.util.Log.d("SnapOut_Engine", "Switch toggled. New state: " + isChecked);

                if (isChecked) {
                    if (!Settings.canDrawOverlays(getActivity())) {
                        switchFocusMode.setChecked(false);

                        Toast.makeText(getActivity(), "Allow Display over other apps for SnapOut", Toast.LENGTH_LONG).show();

                        Intent overlayIntent = new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getActivity().getPackageName())
                        );
                        startActivity(overlayIntent);
                        return;
                    }
                    // Check for Android 13+ Notification Permissions dynamically
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(getActivity(), android.Manifest.permission.POST_NOTIFICATIONS)
                                != android.content.pm.PackageManager.PERMISSION_GRANTED) {

                            // Revert visual toggle states temporarily until permission is validated
                            switchFocusMode.setChecked(false);

                            // Request permission using the modern Launcher API
                            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                            return;
                        }
                    }
                }

                // Persist modified focus state configurations and manage service
                prefs.edit().putBoolean(KEY_FOCUS_MODE_ACTIVE, isChecked).apply();

                try {
                    syncFocusToggleUI(isChecked);
                } catch (Exception e) {
                    android.util.Log.e("SnapOut_Engine", "CRASH IN UI SYNC: ", e);
                }

                try {
                    manageMonitoringService(isChecked);
                } catch (Exception e) {
                    android.util.Log.e("SnapOut_Engine", "CRASH IN SERVICE MANAGER: ", e);
                }

            } catch (Exception e) {
                android.util.Log.e("SnapOut_Engine", "FATAL SWITCH CRASH CAUGHT: ", e);
            }
        });


        // Initialize background tracking checks on interface startup if active
        if (isFocusActive) {
            manageMonitoringService(true);
        }

        loadSystemAppsAsync();

        return view;
    }

    private void manageMonitoringService(boolean start) {
        try {
            Context context = getContext();
            if (context == null) context = getActivity();
            if (context == null) return;

            Intent serviceIntent = new Intent(context, MonitoringService.class);

            if (start) {
                android.util.Log.d("SnapOut_Engine", "Sending START command to service...");
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } else {
                android.util.Log.d("SnapOut_Engine", "Sending safe STOP command intent down to engine...");
                serviceIntent.setAction("ACTION_STOP_SNAPOUT_SERVICE");
                context.startService(serviceIntent);
            }
        } catch (Exception e) {
            android.util.Log.e("SnapOut_Engine", "🚨 Caught fragment intent crash: ", e);
        }
    }

    private void syncFocusToggleUI(boolean isEnabled) {
        if (isEnabled) {
            txtToggleStatus.setText("Focus Mode On");
            txtToggleStatus.setTextColor(0xFF00F0FF);
        } else {
            txtToggleStatus.setText("Focus Mode Off");
            txtToggleStatus.setTextColor(0xFFE6E6EA);
        }

        // Hide the extra sentence under Focus Mode
        txtDefenseMeme.setVisibility(View.GONE);
    }

    private void loadSystemAppsAsync() {
        if (getActivity() == null) return;
        PackageManager pm = getActivity().getPackageManager();

        new Thread(() -> {
            ArrayList<AppSelectionModel> rawList = new ArrayList<>();
            String myPackage = getActivity().getPackageName();

            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> availableActivities = pm.queryIntentActivities(intent, 0);

            for (ResolveInfo ri : availableActivities) {
                if (ri.activityInfo != null) {
                    String packageName = ri.activityInfo.packageName;

                    if (packageName.equals(myPackage) ||
                            packageName.equals("com.android.settings") ||
                            packageName.equals("com.android.systemui")) {
                        continue;
                    }

                    try {
                        ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
                        String appName = pm.getApplicationLabel(ai).toString();
                        Drawable icon = pm.getApplicationIcon(ai);
                        boolean isSelected = blockedPackagesSet.contains(packageName);

                        boolean exactDuplicateFound = false;
                        for (AppSelectionModel existing : rawList) {
                            if (existing.packageName.equals(packageName)) {
                                exactDuplicateFound = true;
                                break;
                            }
                        }

                        if (!exactDuplicateFound) {
                            rawList.add(new AppSelectionModel(appName, packageName, icon, isSelected));
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        // Suppress background targets cleanly
                    }
                }
            }

            Collections.sort(rawList, (a, b) -> a.appName.compareToIgnoreCase(b.appName));

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    displayAppsList.clear();
                    displayAppsList.addAll(rawList);
                    adapter = new AppSelectionAdapter(displayAppsList, this);
                    recyclerAppSelection.setAdapter(adapter);
                });
            }
        }).start();
    }

    @Override
    public void onSelectionChanged(String packageName, boolean isChecked) {
        if (isChecked) {
            blockedPackagesSet.add(packageName);
        } else {
            blockedPackagesSet.remove(packageName);
        }

        prefs.edit().putStringSet(KEY_BLOCKED_APPS, blockedPackagesSet).apply();
        updateCountUI();
    }

    private void updateCountUI() {
        int totalSelected = blockedPackagesSet.size();

        if (totalSelected == 1) {
            txtSelectedCount.setText("1 app selected");
            txtSelectedCount.setTextColor(0xFF00F0FF);
        } else if (totalSelected > 1) {
            txtSelectedCount.setText(totalSelected + " apps selected");
            txtSelectedCount.setTextColor(0xFF00F0FF);
        } else {
            txtSelectedCount.setText("0 apps selected");
            txtSelectedCount.setTextColor(0xFF8A8A95);
        }
    }
    private void updateDailyLimitUI() {
        if (txtDailyLimit == null) return;

        if (selectedLimitMinutes < 60) {
            txtDailyLimit.setText(selectedLimitMinutes + " min");
        } else {
            int hours = selectedLimitMinutes / 60;
            int minutes = selectedLimitMinutes % 60;

            if (minutes == 0) {
                txtDailyLimit.setText(hours + " hr");
            } else {
                txtDailyLimit.setText(hours + " hr " + minutes + " min");
            }
        }
    }
    @Override
    public void onResume() {
        super.onResume();

        if (prefs == null) return;

        Set<String> savedSet = prefs.getStringSet(KEY_BLOCKED_APPS, new HashSet<>());
        blockedPackagesSet = new HashSet<>(savedSet);

        boolean isFocusActive = prefs.getBoolean(KEY_FOCUS_MODE_ACTIVE, false);

        if (switchFocusMode != null) {
            switchFocusMode.setChecked(isFocusActive);
        }

        syncFocusToggleUI(isFocusActive);
        updateCountUI();

        if (displayAppsList != null && adapter != null) {
            for (AppSelectionModel app : displayAppsList) {
                app.isSelected = blockedPackagesSet.contains(app.packageName);
            }
            adapter.notifyDataSetChanged();
        }
    }
}