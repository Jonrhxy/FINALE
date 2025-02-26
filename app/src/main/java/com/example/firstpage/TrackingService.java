package com.example.firstpage;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TrackingService extends Service {
    private static final String CHANNEL_ID = "TrackingServiceChannel";
    private static final String TAG = "TrackingService";

    // Thresholds for location updates.
    private static final float MIN_ACCURACY = 100.0f;  // Accept locations with accuracy <= 100m.
    private static final float MIN_MOVEMENT = 0.5f;      // Count movements >= 0.5m.

    // Speed thresholds (m/s) for determining travel mode.
    private static final float WALK_MAX_SPEED = 1.5f;
    private static final float BIKE_MAX_SPEED = 11.0f;
    private static final float MOTORCYCLE_MAX_SPEED = 16.7f;
    private static final float JEEPNEY_MAX_SPEED = 22.2f;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location lastLocation = null;
    private float totalDistance = 0;
    private float totalCarbon = 0;
    // 'selectedMode' is the current mode used for emissions calculation.
    private String selectedMode = "Car";
    // 'confirmedMode' holds the last verified (vehicle) mode.
    private String confirmedMode = "Car";
    // Flag to ensure only one prompt is pending.
    private boolean confirmationPending = false;
    private SharedPreferences sharedPreferences;
    private long lastUpdateTime = 0;

    // Receiver to handle vehicle status responses.
    private final BroadcastReceiver vehicleStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean inVehicle = intent.getBooleanExtra("in_vehicle", false);
            Log.d(TAG, "Received VEHICLE_STATUS_RESPONSE: in_vehicle = " + inVehicle);
            if (inVehicle) {
                // Maintain the previous vehicle mode.
                selectedMode = confirmedMode;
                Log.d(TAG, "User confirmed vehicle mode. Keeping confirmedMode: " + confirmedMode);
            } else {
                // Switch to walking.
                selectedMode = "Walking";
                confirmedMode = "Walking";
                Log.d(TAG, "User indicated not in vehicle. Switching to Walking mode.");
            }
            confirmationPending = false;
            sendUpdates();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this);
        sharedPreferences = getSharedPreferences("TrackingData", Context.MODE_PRIVATE);
        // Default to Walking if nothing is stored.
        selectedMode = sharedLocationPreferences();
        confirmedMode = selectedMode;
        Log.d(TAG, "onCreate: selectedMode and confirmedMode initialized to: " + selectedMode);

        // Check if GPS is enabled.
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            Log.d(TAG, "GPS enabled: " + lm.isProviderEnabled(LocationManager.GPS_PROVIDER));
        }

        // Register receiver for vehicle status responses.
        registerReceiver(vehicleStatusReceiver,
                new IntentFilter("VEHICLE_STATUS_RESPONSE"),
                Context.RECEIVER_NOT_EXPORTED);
        Log.d(TAG, "Registered vehicleStatusReceiver.");

        createNotificationChannel();
        startForegroundServiceWithNotification();
        setupLocationUpdates();
        requestImmediateLocationUpdate();
    }

    // Retrieve saved mode from SharedPreferences (default to "Walking").
    private String sharedLocationPreferences() {
        String mode = sharedPreferences.getString("selected_mode", "Walking");
        Log.d(TAG, "sharedLocationPreferences: mode = " + mode);
        return mode;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Tracking Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created.");
            }
        }
    }

    private void startForegroundServiceWithNotification() {
        // This is the ongoing notification for the service.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Tracking Active")
                .setContentText("Using GPS & Network for location.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        startForeground(1, builder.build());
        Log.d(TAG, "Foreground service started with notification.");
    }

    private void setupLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(2000)
                .setMinUpdateDistanceMeters(MIN_MOVEMENT)
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    updateLocation(location);
                }
            }
        };
        Log.d(TAG, "Location updates set up.");
    }

    @SuppressLint("MissingPermission")
    private void startTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted!");
            return;
        }
        fusedLocationClient.requestLocationUpdates(
                new LocationRequest.Builder(2000)
                        .setMinUpdateDistanceMeters(MIN_MOVEMENT)
                        .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                        .build(),
                locationCallback,
                Looper.getMainLooper());
        Log.d(TAG, "GPS/Network tracking started.");
    }

    @SuppressLint("MissingPermission")
    private void requestImmediateLocationUpdate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted!");
            return;
        }
        fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        new CancellationTokenSource().getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        Log.d(TAG, "Immediate location update received.");
                        updateLocation(location);
                    }
                });
    }

    /**
     * Launches a high-priority full-screen prompt by sending a full-screen intent via a notification.
     * This prompt will attempt to launch VehicleStatusActivity even when the app is minimized.
     */
    private void showHighPriorityVehicleStatusPrompt() {
        Log.d(TAG, "Building high priority full-screen notification for vehicle status prompt.");
        Intent fullScreenIntent = new Intent(this, VehicleStatusActivity.class);
        fullScreenIntent.putExtra("message", "Are you still in a vehicle?");
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                this, 0, fullScreenIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE :
                        PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Confirm Vehicle Status")
                .setContentText("Are you still in a vehicle?")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(1002, builder.build());
            Log.d(TAG, "High priority full-screen notification displayed.");
        }
    }

    /**
     * Processes each location update.
     */
    private void updateLocation(Location location) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < 2000) return;
        lastUpdateTime = currentTime;

        Log.d(TAG, "New location: Lat=" + location.getLatitude() +
                ", Lng=" + location.getLongitude() +
                ", Accuracy=" + location.getAccuracy() + "m, Speed=" + location.getSpeed() + "m/s");

        if (location.getAccuracy() > MIN_ACCURACY) {
            Log.d(TAG, "Location accuracy (" + location.getAccuracy() + "m) exceeds threshold. Refreshing location.");
            requestImmediateLocationUpdate();
            return;
        }

        float speed = location.getSpeed();
        String newMode = getTravelMode(speed);
        Log.d(TAG, "Computed mode from speed (" + speed + " m/s): " + newMode);

        // If computed mode would be "Walking" but confirmed mode is still a vehicle,
        // then launch the high priority full-screen prompt.
        if (newMode.equals("Walking") && !confirmedMode.equals("Walking") && !confirmationPending) {
            confirmationPending = true;
            Log.d(TAG, "Condition met: newMode is Walking but confirmedMode is " + confirmedMode +
                    ". Launching high priority full-screen prompt.");
            showHighPriorityVehicleStatusPrompt();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (confirmationPending) {
                    confirmationPending = false;
                    selectedMode = "Walking";
                    confirmedMode = "Walking";
                    Log.d(TAG, "No response received from full-screen prompt; defaulting to Walking mode.");
                    sendUpdates();
                }
            }, 10000);
            return;
        } else {
            selectedMode = newMode;
            if (!newMode.equals("Walking")) {
                confirmedMode = newMode;
            }
        }

        if (lastLocation != null) {
            float distance = lastLocation.distanceTo(location);
            Log.d(TAG, "Distance from last location: " + distance + "m");
            if (distance < MIN_MOVEMENT) {
                Log.d(TAG, "Distance (" + distance + "m) below threshold, ignoring.");
                return;
            }
            totalDistance += distance;
            totalCarbon = totalDistance * CarbonUtils.getCarbonEmissionRate(selectedMode);
            Log.d(TAG, "Updated totals: totalDistance=" + totalDistance + "m, totalCarbon=" + totalCarbon +
                    "kg, Mode=" + selectedMode);

            saveValues();
            sendUpdates();
            Log.d(TAG, "Updating Firestore with segment carbon emission.");
            updateTransportationInFirestore(distance, selectedMode);
        }
        lastLocation = location;
    }

    /**
     * Determines travel mode based on speed (m/s).
     */
    private String getTravelMode(float speed) {
        if (speed < WALK_MAX_SPEED) {
            return "Walking";
        } else if (speed < BIKE_MAX_SPEED) {
            return "Biking";
        } else if (speed < MOTORCYCLE_MAX_SPEED) {
            return "Motorcycle";
        } else if (speed < JEEPNEY_MAX_SPEED) {
            return "Jeepney";
        } else {
            return "Car";
        }
    }

    private void saveValues() {
        sharedPreferences.edit()
                .putFloat("totalDistance", totalDistance)
                .putFloat("totalCarbon", totalCarbon)
                .putString("selected_mode", selectedMode)
                .apply();
        Log.d(TAG, "Saved values to SharedPreferences: selected_mode=" + selectedMode);
    }

    private void sendUpdates() {
        Intent intent = new Intent("TRACKING_UPDATE");
        intent.putExtra("total_distance", (int) totalDistance);
        intent.putExtra("total_carbon", (int) totalCarbon);
        intent.putExtra("travel_mode", selectedMode);
        sendBroadcast(intent);
        Log.d(TAG, "Broadcast sent with updated tracking info.");
    }

    /**
     * Updates Firestore with raw, reduction, and net carbon values.
     */
    private void updateTransportationInFirestore(float distance, String mode) {
        float segmentCarbon = distance * CarbonUtils.getCarbonEmissionRate(mode);
        Log.d(TAG, "Segment carbon calculated: " + segmentCarbon + " kg for mode " + mode);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "No user logged in; cannot update Firestore.");
            return;
        }
        String displayName = user.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            Log.e(TAG, "User has no display name; cannot update Firestore.");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String today = getCurrentDateString();
        DocumentReference docRef = db.collection("transportation")
                .document(displayName)
                .collection("daily")
                .document(today);

        docRef.get().addOnSuccessListener(documentSnapshot -> {
            double currentCarbon = 0.0;
            double currentReduction = 0.0;
            if (documentSnapshot.exists()) {
                String carbonStr = documentSnapshot.getString("total_carbon_footprint");
                if (carbonStr != null && !carbonStr.isEmpty()) {
                    try {
                        currentCarbon = Double.parseDouble(carbonStr);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Error parsing current carbon: " + carbonStr, e);
                    }
                }
                String reductionStr = documentSnapshot.getString("total_carbon_reduced");
                if (reductionStr != null && !reductionStr.isEmpty()) {
                    try {
                        currentReduction = Double.parseDouble(reductionStr);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Error parsing current reduction: " + reductionStr, e);
                    }
                }
            }
            double updatedCarbon = currentCarbon + segmentCarbon;
            double updatedNet = updatedCarbon - currentReduction;
            Log.d(TAG, "Firestore current values: currentCarbon=" + currentCarbon + ", currentReduction=" + currentReduction);
            Log.d(TAG, "Calculated updatedCarbon=" + updatedCarbon + ", updatedNet=" + updatedNet);

            Map<String, Object> updates = new HashMap<>();
            updates.put("total_carbon_footprint", String.valueOf(updatedCarbon));
            updates.put("net_carbon_footprint", String.valueOf(updatedNet));

            if (mode.equals("Walking")) {
                float carRate = CarbonUtils.getCarbonEmissionRate("Car");
                float walkingRate = CarbonUtils.getCarbonEmissionRate("Walking");
                float segmentReduction = distance * (carRate - walkingRate);
                double updatedReduction = currentReduction + segmentReduction;
                updates.put("total_carbon_reduced", String.valueOf(updatedReduction));
                updatedNet = updatedCarbon - updatedReduction;
                updates.put("net_carbon_footprint", String.valueOf(updatedNet));
                Log.d(TAG, "Mode is Walking; updatedReduction=" + updatedReduction + ", new updatedNet=" + updatedNet);
            }
            docRef.set(updates, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Firestore successfully updated."))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to update Firestore", e));
        }).addOnFailureListener(e -> Log.e(TAG, "Failed to get daily document for " + displayName, e));
    }

    private String getCurrentDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "FORCE_UPDATE".equals(intent.getAction())) {
            requestImmediateLocationUpdate();
        }
        startTracking();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        fusedLocationClient.removeLocationUpdates(locationCallback);
        unregisterReceiver(vehicleStatusReceiver);
        Log.d(TAG, "Service destroyed, receivers unregistered.");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
