package com.example.distance_track;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class LocationTrackingService extends Service {

    private static final String TAG = "LocationService";
    public static final String ACTION_START_OR_RESUME_TRACKING = "ACTION_START_OR_RESUME_TRACKING";
    public static final String ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING";
    public static final String ACTION_SERVICE_STATE_CHANGED = "ACTION_SERVICE_STATE_CHANGED"; // For broadcasting state
    public static final String EXTRA_IS_TRACKING = "EXTRA_IS_TRACKING"; // To send tracking state

    public static final String EXTRA_SELECTED_UNIT_SERVICE = "EXTRA_SELECTED_UNIT_SERVICE";

    private static final String NOTIFICATION_CHANNEL_ID = "distance_tracking_channel";
    private static final String NOTIFICATION_CHANNEL_NAME = "Distance Tracking";
    private static final int NOTIFICATION_ID = 123;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location lastLocation;

    private double currentSessionDistanceMeters = 0.0;
    private double cumulativeDailyDistanceMetersFromFirebase = 0.0;

    private DatabaseReference dailyTotalDistanceRef;
    private DatabaseReference pathPointsRef;
    private String userId;
    private String currentTrackingDate;

    private String currentSelectedUnit = "km";

    public static final String BROADCAST_SERVICE_DATA = "BROADCAST_SERVICE_DATA";
    public static final String EXTRA_CURRENT_SESSION_DISTANCE_METERS = "EXTRA_CURRENT_SESSION_DISTANCE_METERS";
    public static final String EXTRA_LAST_LOCATION = "EXTRA_LAST_LOCATION";

    public static volatile boolean isTracking = false; // volatile for thread safety if accessed from different threads

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        // isTracking is false by default

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not logged in. Stopping service onCreate.");
            stopSelf();
            return;
        }
        userId = currentUser.getUid();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (!isTracking) return; // Crucial check

                for (Location location : locationResult.getLocations()) {
                    // ... (location processing and Firebase update logic remains the same)
                    Log.d(TAG, "New location: " + location.getLatitude() + ", " + location.getLongitude());
                    if (lastLocation != null) {
                        float distanceSegmentMeters = lastLocation.distanceTo(location);
                        if (distanceSegmentMeters > 0.1) {
                            currentSessionDistanceMeters += distanceSegmentMeters;
                            updateDailyTotalDistanceInFirebase(distanceSegmentMeters);
                        }
                    }
                    lastLocation = location;
                    saveLocationPointToFirebase(location);

                    broadcastCurrentSessionData(); // Broadcast distance
                    // Update notification
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm != null) {
                        nm.notify(NOTIFICATION_ID, createNotification(currentSessionDistanceMeters, currentSelectedUnit));
                    }
                }
            }
        };
    }

    private void broadcastCurrentSessionData() {
        Intent intent = new Intent(BROADCAST_SERVICE_DATA);
        intent.putExtra(EXTRA_CURRENT_SESSION_DISTANCE_METERS, currentSessionDistanceMeters);
        if (lastLocation != null) {
            intent.putExtra(EXTRA_LAST_LOCATION, lastLocation);
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    // NEW: Broadcasts the service's tracking state
    private void broadcastServiceState() {
        Intent intent = new Intent(ACTION_SERVICE_STATE_CHANGED);
        intent.putExtra(EXTRA_IS_TRACKING, isTracking);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d(TAG, "Broadcasting service state: isTracking = " + isTracking);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.d(TAG, "onStartCommand received action: " + action + ", current isTracking: " + isTracking);

            // Always attempt to get unit if provided, especially for start
            if (intent.hasExtra(EXTRA_SELECTED_UNIT_SERVICE)) {
                currentSelectedUnit = intent.getStringExtra(EXTRA_SELECTED_UNIT_SERVICE);
                if (currentSelectedUnit == null || currentSelectedUnit.isEmpty()) {
                    currentSelectedUnit = "km";
                }
            }


            switch (action) {
                case ACTION_START_OR_RESUME_TRACKING:
                    if (!isTracking) { // Only start if not already tracking
                        Log.i(TAG, "Command: START_OR_RESUME_TRACKING. Service was not tracking. Starting...");
                        currentSessionDistanceMeters = 0.0;
                        lastLocation = null;

                        currentTrackingDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                        initializeFirebaseRefsForDate(currentTrackingDate);
                        loadInitialDailyTotalFromFirebase(currentTrackingDate); // For Firebase calculation

                        startForegroundServiceInternal(); // Sets isTracking = true, starts foreground, location updates
                        broadcastServiceState(); // Broadcast that tracking has started
                        broadcastCurrentSessionData(); // Broadcast initial 0.0 distance
                    } else {
                        Log.i(TAG, "Command: START_OR_RESUME_TRACKING. Service already tracking. Ignoring redundant start.");
                        // Optionally, resend current state if needed, but usually not necessary
                        // broadcastServiceState();
                        // broadcastCurrentSessionData();
                    }
                    break;
                case ACTION_STOP_TRACKING:
                    Log.i(TAG, "Command: STOP_TRACKING.");
                    stopTracking(); // Handles setting isTracking = false, stopping updates, foreground, and service
                    break;
            }
        } else {
            Log.w(TAG, "onStartCommand received null intent or action.");
        }
        return START_STICKY;
    }

    // ... (initializeFirebaseRefsForDate, loadInitialDailyTotalFromFirebase - remain the same) ...
    private void initializeFirebaseRefsForDate(String date) {
        if (userId == null) {
            Log.e(TAG, "User ID is null, cannot initialize Firebase refs.");
            stopSelf();
            return;
        }
        dailyTotalDistanceRef = FirebaseDatabase.getInstance("https://payment11-4e916-default-rtdb.firebaseio.com/")
                .getReference("Users").child(userId).child("DistancePerDay").child(date);
        pathPointsRef = FirebaseDatabase.getInstance("https://payment11-4e916-default-rtdb.firebaseio.com/")
                .getReference("Users").child(userId).child("PathPoints").child(date);
    }

    private void loadInitialDailyTotalFromFirebase(String date) {
        if (dailyTotalDistanceRef == null) initializeFirebaseRefsForDate(date);
        dailyTotalDistanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Double firebaseDistanceKm = snapshot.getValue(Double.class);
                    if (firebaseDistanceKm != null) {
                        cumulativeDailyDistanceMetersFromFirebase = firebaseDistanceKm * 1000.0;
                        Log.d(TAG, "Initial DAILY total for " + date + " loaded: " + cumulativeDailyDistanceMetersFromFirebase + "m");
                    } else {
                        cumulativeDailyDistanceMetersFromFirebase = 0.0;
                    }
                } else {
                    cumulativeDailyDistanceMetersFromFirebase = 0.0;
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                cumulativeDailyDistanceMetersFromFirebase = 0.0;
                Log.w(TAG, "Failed to load initial daily total for " + date, error.toException());
            }
        });
    }

    private void startForegroundServiceInternal() {
        if (isTracking) {
            Log.w(TAG, "startForegroundServiceInternal called but already tracking.");
            return;
        }
        isTracking = true; // Set state FIRST
        Log.d(TAG, "startForegroundServiceInternal: isTracking set to true");
        createNotificationChannel(); // Ensure channel exists
        startForeground(NOTIFICATION_ID, createNotification(currentSessionDistanceMeters, currentSelectedUnit));
        startLocationUpdates();
    }

    private void stopTracking() {
        if (!isTracking && Looper.myLooper() == null && fusedLocationClient == null) { // Heuristic: if already effectively stopped
            Log.w(TAG, "stopTracking called but service appears already stopped or stopping.");
            // Ensure isTracking is false if somehow missed
            if (isTracking) {
                isTracking = false;
                broadcastServiceState(); // Broadcast the definitive stopped state
            }
            return;
        }

        Log.d(TAG, "stopTracking: Setting isTracking to false.");
        isTracking = false; // Set state FIRST

        if (fusedLocationClient != null && locationCallback != null) {
            try {
                Log.d(TAG, "stopTracking: Removing location updates.");
                fusedLocationClient.removeLocationUpdates(locationCallback);
            } catch (Exception e) {
                Log.e(TAG, "Error removing location updates in stopTracking: ", e);
            }
        } else {
            Log.w(TAG, "stopTracking: fusedLocationClient or locationCallback is null. Cannot remove updates.");
        }

        Log.d(TAG, "stopTracking: Stopping foreground service.");
        stopForeground(true); // Remove notification

        Log.d(TAG, "stopTracking: Stopping self (service).");
        stopSelf(); // Stop the service itself

        broadcastServiceState(); // Broadcast that tracking has stopped
        // Optionally, broadcast final distance if needed by fragment for a summary
        // broadcastCurrentSessionData(); // currentSessionDistanceMeters will have the final session value
        Log.i(TAG, "Service definitively stopped.");
    }


    // ... (startLocationUpdates, updateDailyTotalDistanceInFirebase, saveLocationPointToFirebase - remain the same) ...
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permissions not granted in service. Cannot start updates.");
            isTracking = false; // Ensure state is correct
            broadcastServiceState();
            stopSelf();
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.SECONDS.toMillis(2))
                .setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(1))
                .build();
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            Log.d(TAG, "Requested location updates.");
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Lost location permission during requestLocationUpdates. " + unlikely);
            isTracking = false; // Ensure state is correct
            broadcastServiceState();
            stopSelf();
        }
    }

    private void updateDailyTotalDistanceInFirebase(double distanceSegmentMeters) {
        if (dailyTotalDistanceRef == null) {
            Log.e(TAG, "dailyTotalDistanceRef is null in updateDailyTotalDistanceInFirebase. Date: " + currentTrackingDate);
            if (currentTrackingDate != null) initializeFirebaseRefsForDate(currentTrackingDate);
            else return;
        }
        dailyTotalDistanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                double currentTotalKmInFirebase = 0.0;
                if (snapshot.exists()) {
                    Double val = snapshot.getValue(Double.class);
                    currentTotalKmInFirebase = val != null ? val : 0.0;
                }
                double newTotalKmForDay = currentTotalKmInFirebase + (distanceSegmentMeters / 1000.0);
                dailyTotalDistanceRef.setValue(newTotalKmForDay)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Firebase daily total updated successfully to: " + newTotalKmForDay + " km");
                            cumulativeDailyDistanceMetersFromFirebase = newTotalKmForDay * 1000.0;
                        })
                        .addOnFailureListener(e -> Log.e(TAG, "FirebaseUpdate Daily Distance Error: ", e));
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "FirebaseUpdate Daily Distance Read Error: ", error.toException());
            }
        });
    }

    private void saveLocationPointToFirebase(Location location) {
        if (location == null || pathPointsRef == null) return;
        String key = pathPointsRef.push().getKey();
        if (key == null) return;
        Map<String, Object> pathPointData = new HashMap<>();
        pathPointData.put("lat", location.getLatitude());
        pathPointData.put("lng", location.getLongitude());
        pathPointData.put("timestamp", System.currentTimeMillis());
        pathPointsRef.child(key).setValue(pathPointData)
                .addOnFailureListener(e -> Log.e(TAG, "FirebaseSave PathPoint Error: ", e));
    }


    private Notification createNotification(double distanceForNotificationMeters, String unitForNotification) {
        // createNotificationChannel() called in startForegroundServiceInternal

        Intent activityIntent = new Intent(this, MainActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopServiceIntent = new Intent(this, LocationTrackingService.class);
        stopServiceIntent.setAction(ACTION_STOP_TRACKING);
        // No need to pass unit for stop, service knows its current unit.
        PendingIntent stopPendingIntent = PendingIntent.getService(this,
                1, stopServiceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); // FLAG_UPDATE_CURRENT is important

        String formattedDistance = formatDistanceForDisplay(distanceForNotificationMeters, unitForNotification, "Session");

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setAutoCancel(false)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_start_tracking)
                .setContentTitle("Tracking Distance")
                .setContentText(formattedDistance)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.outline_autostop_24, "Stop Tracking", stopPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true) // Prevents sound/vibration on subsequent updates
                .build();
    }

    // ... (formatDistanceForDisplay, createNotificationChannel - remain the same) ...
    private String formatDistanceForDisplay(double distanceInMeters, String unit, String prefix) {
        double displayValue;
        String unitLabel;
        DecimalFormat df = new DecimalFormat("0.00");
        if ("km".equalsIgnoreCase(unit)) {
            displayValue = distanceInMeters / 1000.0;
            unitLabel = "km";
        } else if ("m".equalsIgnoreCase(unit)) {
            displayValue = distanceInMeters;
            unitLabel = "m";
        } else {
            displayValue = distanceInMeters * 100.0;
            unitLabel = "cm";
        }
        return (prefix != null && !prefix.isEmpty() ? prefix + " " : "") + df.format(displayValue) + " " + unitLabel;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Channel for the distance tracking foreground service.");
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: Service is being destroyed. Current isTracking: " + isTracking);
        // Ensure isTracking is false if service is destroyed unexpectedly
        if (isTracking) {
            isTracking = false;
            // No need to broadcast here as the service is gone
        }
        // Location updates should have been removed by stopTracking or if permissions were revoked
        // but as a final cleanup, though fusedLocationClient might be null if service stopped cleanly.
        if (fusedLocationClient != null && locationCallback != null) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback);
                Log.d(TAG, "onDestroy: Final removal of location updates attempt.");
            } catch (Exception e) {
                // Ignore if already removed or client is gone
            }
        }
        Log.i(TAG, "LocationTrackingService destroyed.");
        super.onDestroy();
    }
}