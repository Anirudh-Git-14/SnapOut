package com.example.snapout;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class MainActivity extends AppCompatActivity {

    // Cache fragment instances to prevent structural leaks and duplicate loop initialization
    private final Fragment homeFragment = new HomeFragment();
    private final Fragment focusFragment = new FocusFragment();
    private final Fragment analyticsFragment = new AnalyticsFragment();
    private final Fragment settingsFragment = new SettingsFragment();
    private final FragmentManager fm = getSupportFragmentManager();
    private Fragment activeFragment = homeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        // Build the fragment navigation manager layer securely
        fm.beginTransaction().add(R.id.fragment_container, settingsFragment, "4").hide(settingsFragment).commit();
        fm.beginTransaction().add(R.id.fragment_container, analyticsFragment, "3").hide(analyticsFragment).commit();
        fm.beginTransaction().add(R.id.fragment_container, focusFragment, "2").hide(focusFragment).commit();
        fm.beginTransaction().add(R.id.fragment_container, homeFragment, "1").commit();

        bottomNav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                Fragment target = null;

                if (id == R.id.nav_home) {
                    target = homeFragment;
                } else if (id == R.id.nav_focus) {
                    target = focusFragment;
                } else if (id == R.id.nav_analytics) {
                    target = analyticsFragment;
                } else if (id == R.id.nav_settings) {
                    target = settingsFragment;
                }

                if (target != null && target != activeFragment) {
                    fm.beginTransaction().hide(activeFragment).show(target).commit();
                    activeFragment = target;

                    // If returning to the home viewport, refresh stats calculations cleanly
                    if (target instanceof HomeFragment) {
                        ((HomeFragment) target).onResume();
                    }
                    return true;
                }
                return false;
            }
        });
    }
}