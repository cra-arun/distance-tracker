package com.example.distance_track;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class fragement_distance extends Fragment {

    private TextView distanceText, statusText, timerText, calorieText, totalMinutesText, totalCaloriesText;
    private Button btnToggleTracking, backButton;

    private String selectedUnit = "km";
    private BroadcastReceiver serviceDataReceiver, serviceStateReceiver;
    private ActivityResultLauncher<String[]> locationPermissionRequestLauncher;

    private long startTimeMillis = 0;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable;
    private double lastDistanceInMeters = 0.0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            selectedUnit = getArguments().getString("unit", "km");
        }

        locationPermissionRequestLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                    boolean fineLocationGranted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    boolean coarseLocationGranted = permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                    boolean postNotificationsGranted = true;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        postNotificationsGranted = permissions.getOrDefault(Manifest.permission.POST_NOTIFICATIONS, false);
                    }

                    if (fineLocationGranted && coarseLocationGranted) {
                        btnToggleTracking.setEnabled(true);
                        sendCommandToService(LocationTrackingService.ACTION_START_OR_RESUME_TRACKING);
                    } else {
                        Toast.makeText(getContext(), "Location permissions are required to track distance.", Toast.LENGTH_LONG).show();
                        btnToggleTracking.setEnabled(false);
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_fragement_distance, container, false);

        distanceText = view.findViewById(R.id.distanceText);
        statusText = view.findViewById(R.id.statustext);
        btnToggleTracking = view.findViewById(R.id.btnToggleTracking);
        backButton = view.findViewById(R.id.back_button_distance_fragment);
        timerText = view.findViewById(R.id.timerText);
        calorieText = view.findViewById(R.id.calorieText);
        totalMinutesText = view.findViewById(R.id.totalMinutesText);
        totalCaloriesText = view.findViewById(R.id.totalCaloriesText);

        updateCurrentUIState(LocationTrackingService.isTracking);
        if (!LocationTrackingService.isTracking) updateDistanceDisplay(0.0, selectedUnit);

        btnToggleTracking.setOnClickListener(v -> {
            if (LocationTrackingService.isTracking) {
                sendCommandToService(LocationTrackingService.ACTION_STOP_TRACKING);
            } else {
                updateDistanceDisplay(0.0, selectedUnit);
                checkAndRequestPermissions();
            }
        });

        backButton.setOnClickListener(v -> {
            if (LocationTrackingService.isTracking) {
                sendCommandToService(LocationTrackingService.ACTION_STOP_TRACKING);
            }
            if (getActivity() != null) getActivity().getSupportFragmentManager().popBackStack();
        });

        serviceDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (LocationTrackingService.BROADCAST_SERVICE_DATA.equals(intent.getAction())) {
                    double currentSessionDistanceMeters = intent.getDoubleExtra(LocationTrackingService.EXTRA_CURRENT_SESSION_DISTANCE_METERS, 0.0);
                    lastDistanceInMeters = currentSessionDistanceMeters;
                    updateDistanceDisplay(currentSessionDistanceMeters, selectedUnit);
                    updateCalorieDisplay(currentSessionDistanceMeters);
                }
            }
        };

        serviceStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (LocationTrackingService.ACTION_SERVICE_STATE_CHANGED.equals(intent.getAction())) {
                    boolean isTracking = intent.getBooleanExtra(LocationTrackingService.EXTRA_IS_TRACKING, false);
                    updateCurrentUIState(isTracking);
                    if (!isTracking) updateDistanceDisplay(0.0, selectedUnit);
                }
            }
        };

        return view;
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);

        if (!permissionsToRequest.isEmpty()) {
            btnToggleTracking.setEnabled(false);
            locationPermissionRequestLauncher.launch(permissionsToRequest.toArray(new String[0]));
        } else {
            btnToggleTracking.setEnabled(true);
            sendCommandToService(LocationTrackingService.ACTION_START_OR_RESUME_TRACKING);
        }
    }

    private void sendCommandToService(String action) {
        if (getActivity() == null) return;
        Intent intent = new Intent(getActivity(), LocationTrackingService.class);
        intent.setAction(action);
        if (LocationTrackingService.ACTION_START_OR_RESUME_TRACKING.equals(action)) {
            intent.putExtra(LocationTrackingService.EXTRA_SELECTED_UNIT_SERVICE, selectedUnit);
        }
        ContextCompat.startForegroundService(getActivity(), intent);
    }

    private void updateCurrentUIState(boolean isTracking) {
        if (isTracking) {
            btnToggleTracking.setText("Stop Tracking");
            statusText.setText("Status: Tracking Active");
            btnToggleTracking.setEnabled(true);
            startTimer();
        } else {
            btnToggleTracking.setText("Start Tracking");
            statusText.setText("Status: Not Tracking");
            btnToggleTracking.setEnabled(true);
            stopTimerAndSaveToFirebase();
            timerText.setText("00:00:00");
            calorieText.setText("Calories: 0");
        }
    }

    private void updateDistanceDisplay(double meters, String unit) {
        double value;
        String label;
        DecimalFormat df = new DecimalFormat("0.00");

        if ("km".equals(unit)) {
            value = meters / 1000.0;
            label = "km";
        } else if ("m".equals(unit)) {
            value = meters;
            label = "m";
        } else {
            value = meters * 100.0;
            label = "cm";
        }

        distanceText.setText(df.format(value) + " " + label);
    }

    private void updateCalorieDisplay(double meters) {
        double km = meters / 1000.0;
        int calories = (int) (km * 50);
        calorieText.setText("Calories: " + calories);
    }

    private void startTimer() {
        startTimeMillis = System.currentTimeMillis();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTimeMillis;
                int seconds = (int) (elapsed / 1000);
                int hours = seconds / 3600;
                int minutes = (seconds % 3600) / 60;
                seconds = seconds % 60;
                timerText.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimerAndSaveToFirebase() {
        timerHandler.removeCallbacks(timerRunnable);
        long totalMillis = System.currentTimeMillis() - startTimeMillis;
        int sessionMinutes = (int) (totalMillis / 60000);
        saveTimeAndCaloriesToFirebase(sessionMinutes, lastDistanceInMeters);
    }

    private void saveTimeAndCaloriesToFirebase(int sessionMinutes, double sessionMeters) {
        double sessionKm = sessionMeters / 1000.0;
        int sessionCalories = (int) (sessionKm * 50);
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("TrackingData")
                .child(uid)
                .child(date);

        ref.get().addOnSuccessListener(snapshot -> {
            int previousMinutes = 0;
            int previousCalories = 0;

            if (snapshot.exists()) {
                if (snapshot.hasChild("timeMinutes")) {
                    previousMinutes = snapshot.child("timeMinutes").getValue(Integer.class);
                }
                if (snapshot.hasChild("calories")) {
                    previousCalories = snapshot.child("calories").getValue(Integer.class);
                }
            }

            int updatedMinutes = previousMinutes + sessionMinutes;
            int updatedCalories = previousCalories + sessionCalories;

            ref.child("timeMinutes").setValue(updatedMinutes);
            ref.child("calories").setValue(updatedCalories);

            totalMinutesText.setText("Total Minutes Today: " + updatedMinutes);
            totalCaloriesText.setText("Total Calories Today: " + updatedCalories);

        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "Failed to update time/calories in Firebase.", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(requireActivity()).registerReceiver(serviceDataReceiver,
                new IntentFilter(LocationTrackingService.BROADCAST_SERVICE_DATA));
        LocalBroadcastManager.getInstance(requireActivity()).registerReceiver(serviceStateReceiver,
                new IntentFilter(LocationTrackingService.ACTION_SERVICE_STATE_CHANGED));
        updateCurrentUIState(LocationTrackingService.isTracking);
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(serviceDataReceiver);
        LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(serviceStateReceiver);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}
