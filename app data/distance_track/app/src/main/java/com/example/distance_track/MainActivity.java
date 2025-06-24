package com.example.distance_track;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem; // Make sure to import this
// Removed android.view.View and Snackbar as they are not directly used for this feature

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
// androidx.appcompat.widget.Toolbar is implicitly used via binding.appBarMain.toolbar
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.distance_track.databinding.ActivityMainBinding;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    // For displaying total distance in Toolbar
    private DatabaseReference dailyTotalDistanceFirebaseRef;
    private ValueEventListener dailyTotalDistanceListener;
    private MenuItem totalDistanceMenuItem; // To hold a reference to the menu item

    private String userId;
    private String currentDate;
    private final DecimalFormat distanceFormatter = new DecimalFormat("0.00");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // This is crucial: sets up the Toolbar from your binding as the ActionBar
        setSupportActionBar(binding.appBarMain.toolbar);

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;

        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_unit_select, R.id.nav_distance,
                R.id.nav_maps, R.id.nav_history, R.id.nav_end, R.id.nav_logout) // Added nav_end here
                .setOpenableLayout(drawer)
                .build();

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            // Your existing nav_end and nav_logout logic
            if (id == R.id.nav_end) {
                finishAffinity();
                System.exit(0);
                return true;
            }
            if (id == R.id.nav_logout) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(MainActivity.this, login.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return true;
            }

            boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
            if (handled) {
                drawer.closeDrawers();
            }
            return handled;
        });

        // --- Firebase User and Date Setup for Toolbar Display ---
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
            // Initialize date here, checkForDateChange will manage updates
            currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            initializeFirebaseDistanceListener();
        } else {
            Log.w(TAG, "User not logged in, cannot display total distance.");
        }
    }

    private void initializeFirebaseDistanceListener() {
        if (userId == null || currentDate == null) {
            Log.e(TAG, "User ID or Current Date is null. Cannot initialize Firebase listener.");
            return;
        }

        // --- IMPORTANT: Replace with your Firebase Database URL if not the default ---
        DatabaseReference userDistancePerDayRef = FirebaseDatabase.getInstance("https://payment11-4e916-default-rtdb.firebaseio.com/")
                .getReference("Users").child(userId).child("DistancePerDay");

        // If switching dates, remove listener from old date ref
        if (dailyTotalDistanceFirebaseRef != null && dailyTotalDistanceListener != null) {
            dailyTotalDistanceFirebaseRef.removeEventListener(dailyTotalDistanceListener);
        }

        dailyTotalDistanceFirebaseRef = userDistancePerDayRef.child(currentDate);
        Log.d(TAG, "Setting up Firebase listener for path: " + dailyTotalDistanceFirebaseRef.toString());


        dailyTotalDistanceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                double totalKmToday = 0.0;
                if (snapshot.exists() && snapshot.getValue() != null) {
                    try {
                        totalKmToday = snapshot.getValue(Double.class);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing daily total distance from Firebase: " + snapshot.getValue(), e);
                        totalKmToday = 0.0; // Reset on error
                    }
                } else {
                    Log.d(TAG, "No data found for " + currentDate + " at " + dailyTotalDistanceFirebaseRef.toString());
                }
                updateTotalDistanceDisplay(totalKmToday);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase daily total distance read cancelled: ", error.toException());
                updateTotalDistanceDisplay(0.0); // Display 0 or empty on error
            }
        };
        dailyTotalDistanceFirebaseRef.addValueEventListener(dailyTotalDistanceListener);
    }

    private void updateTotalDistanceDisplay(double totalKm) {
        if (totalDistanceMenuItem != null) {
            String displayText = "Total: " + distanceFormatter.format(totalKm) + " km";
            totalDistanceMenuItem.setTitle(displayText);
            Log.d(TAG, "Updating Toolbar distance: " + displayText);
        } else {
            // This can happen if Firebase callback occurs before onCreateOptionsMenu has run
            // or if user is not logged in and menu item was never initialized.
            Log.d(TAG, "totalDistanceMenuItem is null when trying to update display. Value: " + totalKm);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        totalDistanceMenuItem = menu.findItem(R.id.action_total_distance);

        // If user is not logged in when menu is created, ensure display is clear or default
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            if (totalDistanceMenuItem != null) {
                totalDistanceMenuItem.setTitle(""); // Or "Total: 0.00 km"
            }
        } else {
            // If user is logged in, and Firebase data might have arrived before menu creation,
            // or to ensure it's updated when menu appears.
            // A single event listener can ensure the value is up-to-date when the menu is created.
            if (dailyTotalDistanceFirebaseRef != null) { // Check if ref is initialized
                dailyTotalDistanceFirebaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        double totalKmToday = 0.0;
                        if (snapshot.exists() && snapshot.getValue() != null) {
                            try {
                                totalKmToday = snapshot.getValue(Double.class);
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing on single event: " + snapshot.getValue(), e);
                                totalKmToday = 0.0;
                            }
                        }
                        updateTotalDistanceDisplay(totalKmToday);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.w(TAG, "Single event read cancelled for menu creation.", error.toException());
                        updateTotalDistanceDisplay(0.0); // Default on error
                    }
                });
            } else if (userId != null && currentDate != null) {
                // If ref wasn't set up yet but user/date known, try to init and fetch
                initializeFirebaseDistanceListener(); // This will attach the persistent listener
            }
        }
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dailyTotalDistanceFirebaseRef != null && dailyTotalDistanceListener != null) {
            dailyTotalDistanceFirebaseRef.removeEventListener(dailyTotalDistanceListener);
            Log.d(TAG, "Removed Firebase listener on destroy for path: " + dailyTotalDistanceFirebaseRef.toString());
        }
        binding = null; // Important for ViewBinding if activity is destroyed
    }

    // Call this to update the listener if the date changes (e.g., app open across midnight)
    private void checkForDateChangeAndReinitializeListener() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            userId = null; // Clear userId if logged out
            if (totalDistanceMenuItem != null) totalDistanceMenuItem.setTitle(""); // Clear display
            if (dailyTotalDistanceFirebaseRef != null && dailyTotalDistanceListener != null) {
                dailyTotalDistanceFirebaseRef.removeEventListener(dailyTotalDistanceListener); // Remove old listener
                dailyTotalDistanceFirebaseRef = null;
                dailyTotalDistanceListener = null;
            }
            return;
        }
        // User is logged in, proceed with date check
        userId = currentUser.getUid(); // Ensure userId is current

        String newDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        if (!newDate.equals(currentDate) || dailyTotalDistanceFirebaseRef == null) { // also re-init if ref is null
            Log.d(TAG, "Date changed from " + currentDate + " to " + newDate + " or listener needs re-init.");
            currentDate = newDate;
            initializeFirebaseDistanceListener(); // This will remove old listener and add new one
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if user is still logged in and if date has changed
        checkForDateChangeAndReinitializeListener();
    }
}