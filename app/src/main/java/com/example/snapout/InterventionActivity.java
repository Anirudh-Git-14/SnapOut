package com.example.snapout;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

public class InterventionActivity extends AppCompatActivity {

    private ImageView imgTargetIcon;
    private TextView txtTargetAppName;
    private AppCompatButton btnStartSession;
    private AppCompatButton btnReturnToFocus;
    private TextView txtContinueAnyway;

    private String targetPackageName = "";
    private SharedPreferences prefs;

    private static final String PREFS_NAME = "SnapOutFocusPrefs";
    private static final String KEY_BYPASS_PREFIX = "bypass_timestamp_";

    // Cooldown duration configured to exactly 5 minutes (300,000 milliseconds)
    private static final long COOLDOWN_DURATION_MS = 300000;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intervention);

        // Enforce full-screen premium visual canvas bounds
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Bind interactive user interface view models
        imgTargetIcon = findViewById(R.id.imgTargetIcon);
        txtTargetAppName = findViewById(R.id.txtTargetAppName);
        btnStartSession = findViewById(R.id.btnStartSession);
        btnReturnToFocus = findViewById(R.id.btnReturnToFocus);
        txtContinueAnyway = findViewById(R.id.txtContinueAnyway);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Retrieve the incoming distraction package identifier safely
        if (getIntent() != null && getIntent().hasExtra("TARGET_PACKAGE")) {
            targetPackageName = getIntent().getStringExtra("TARGET_PACKAGE");
        }

        resolveTargetApplicationMetadata();
        configureInterventionActionListeners();

        // Modern Android 13+ platform back-gesture interceptor vector
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Completely consumes and neutralizes the system back action.
                // Forces the user to make a explicit choices via the active layout buttons.
            }
        });
    }

    private void resolveTargetApplicationMetadata() {
        if (targetPackageName == null || targetPackageName.isEmpty()) {
            txtTargetAppName.setText("UNKNOWN APPARATUS");
            return;
        }

        PackageManager pm = getPackageManager();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(targetPackageName, 0);
            String appName = pm.getApplicationLabel(ai).toString();
            Drawable icon = pm.getApplicationIcon(ai);

            txtTargetAppName.setText(appName.toUpperCase());
            imgTargetIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            txtTargetAppName.setText(targetPackageName.toUpperCase());
            imgTargetIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
    }

    private void configureInterventionActionListeners() {

        // 1. Start Focus Session Button (Placeholder logic)
        btnStartSession.setOnClickListener(v -> {
            Toast.makeText(InterventionActivity.this,
                    "FOCUS SESSION STARTED",
                    Toast.LENGTH_LONG).show();
        });

        // 2. Return To Focus Button (Exits the distraction app safely)
        btnReturnToFocus.setOnClickListener(v -> {
            Intent snapOutIntent = new Intent(InterventionActivity.this, MainActivity.class);
            snapOutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(snapOutIntent);
            finish();
        });

        // 3. Continue Anyway Button (Point 5 Cooldown Tracker)
        txtContinueAnyway.setOnClickListener(v -> {
            if (targetPackageName != null && !targetPackageName.isEmpty()) {
                // Force baseline expiry to exactly 5 minutes (300,000 ms)
                long bypassExpiryTime = System.currentTimeMillis() + 300000;

                // Explicitly sync preference token key formatting with core monitoring architecture
                prefs.edit().putLong("bypass_timestamp_" + targetPackageName, bypassExpiryTime).apply();

                Toast.makeText(InterventionActivity.this,
                        "ACCESS GRANTED FOR 5 MINUTES",
                        Toast.LENGTH_SHORT).show();
            }
            finish();
        });
    }
}