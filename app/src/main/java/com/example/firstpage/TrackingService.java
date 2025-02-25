package com.example.firstpage;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TrackingService extends Service {
    private static final String CHANNEL_ID = "TrackingServiceChannel";
    private static final String TAG = "TrackingService";

    // Thresholds for location updates
    private static final float MIN_ACCURACY = 100.0f;  // Accept locations with accuracy <= 100m
    private static final float MIN_MOVEMENT = 0.5f;      // Count movements >= 0.5m

    // Speed thresholds (m/s) for determining travel mode
    private static final float WALK_MAX_SPEED = 1.5f;
    private static final float BIKE_MAX_SPEED = 11.0f;
    private static final float MOTORCYCLE_MAX_SPEED = 16.7f;
    private static final float JEEPNEY_MAX_SPEED = 22.2f;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location lastLocation = null;
    private float totalDistance = 0;
    private float totalCarbon = 0;
    private String selectedMode = "Car"; // Current mode used for emissions calculation
    private String lastMode = "Car";     // Previous mode (vehicle if applicable)
    private boolean confirmationPending = false;
    private SharedPreferences sharedPreferences;
    private long lastUpdateTime = 0;

    // Receiver to handle vehicle status responses from the UI
    private final BroadcastReceiver vehicleStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Expect extra "in_vehicle": true if user confirms they're still in vehicle
            boolean inVehicle = intent.getBooleanExtra("in_vehicle", false);
            if (inVehicle) {
                // Continue with the previous vehicle mode
                selectedMode = lastMode;
            } else {
                // Switch to walking mode
                selectedMode = "Walking";
            }
            confirmationPending = false;
            Log.d(TAG, "Vehicle status response: inVehicle=" + inVehicle + ", selectedMode=" + selectedMode);
            // Send update broadcast so UI can refresh (if needed)
            sendUpdates();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this);
        sharedPreferences = getSharedPreferences("TrackingData", Context.MODE_PRIVATE);
        selectedMode = sharedPreferences.getString("selected_mode", "Car");
        lastMode = selectedMode; // initialize lastMode

        // Debug: Check if GPS is enabled.
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            Log.d(TAG, "GPS enabled: " + lm.isProviderEnabled(LocationManager.GPS_PROVIDER));
        }

        // Register receiver for vehicle status responses.
        registerReceiver(vehicleStatusReceiver, new IntentFilter("VEHICLE_STATUS_RESPONSE"), Context.RECEIVER_NOT_EXPORTED);

        createNotificationChannel();
        startForegroundServiceWithNotification();
        setupLocationUpdates();
        requestImmediateLocationUpdate();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Tracking Service", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundServiceWithNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Tracking Active")
                .setContentText("Using GPS & Network for location.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        startForeground(1, builder.build());
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
    }

    @SuppressLint("MissingPermission")
    private void startTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "❌ Location permission not granted!");
            return;
        }
        fusedLocationClient.requestLocationUpdates(
                new LocationRequest.Builder(2000)
                        .setMinUpdateDistanceMeters(MIN_MOVEMENT)
                        .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                        .build(),
                locationCallback,
                Looper.getMainLooper()
        );
        Log.d(TAG, "✅ GPS/Network tracking started.");
    }

    @SuppressLint("MissingPermission")
    private void requestImmediateLocationUpdate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "❌ Location permission not granted!");
            return;
        }
        fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                new CancellationTokenSource().getToken()
        ).addOnSuccessListener(location -> {
            if (location != null) {
                updateLocation(location);
            }
        });
    }

    // Main method that processes each location update.
    private void updateLocation(Location location) {
        long currentTime = System.currentTimeMillis();
        // Limit updates to every 2 seconds.
        if (currentTime - lastUpdateTime < 2000) return;
        lastUpdateTime = currentTime;

        Log.d(TAG, "📍 New location: Lat=" + location.getLatitude() +
                ", Lng=" + location.getLongitude() +
                ", Accuracy=" + location.getAccuracy() + "m" +
                ", Speed=" + location.getSpeed() + "m/s");

        if (location.getAccuracy() > MIN_ACCURACY) {
            Log.d(TAG, "⚠️ Poor accuracy (" + location.getAccuracy() + "m), forcing refresh.");
            requestImmediateLocationUpdate();
            return;
        }

        float speed = location.getSpeed();
        // Calculate new mode based solely on speed.
        String newMode = getTravelMode(speed);

        // Check if new mode is "Walking" but last mode was a vehicle.
        if (newMode.equals("Walking") && !lastMode.equals("Walking") && !confirmationPending) {
            // Instead of automatically switching to Walking, ask the user.
            confirmationPending = true;
            Intent promptIntent = new Intent("ASK_VEHICLE_STATUS");
            promptIntent.putExtra("message", "Are you still in vehicle?");
            promptIntent.putExtra("vehicle_mode", lastMode); // pass the previous vehicle mode
            sendBroadcast(promptIntent);
            Log.d(TAG, "Prompting user for vehicle status. Last mode: " + lastMode);
            // Set a timeout (10 seconds) in case no response is received.
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (confirmationPending) {
                    confirmationPending = false;
                    // Default answer: assume user is not in vehicle (i.e. walking).
                    selectedMode = "Walking";
                    Log.d(TAG, "No response received. Defaulting to Walking mode.");
                    sendUpdates();
                }
            }, 10000);
            // Do not update further until confirmation is received.
            return;
        } else {
            // No confirmation needed; use the newMode as determined.
            selectedMode = newMode;
        }

        // If there is a previous location, update totals.
        if (lastLocation != null) {
            float distance = lastLocation.distanceTo(location);
            Log.d(TAG, "📏 Distance: " + distance + "m");
            if (distance < MIN_MOVEMENT) {
                Log.d(TAG, "⚠️ Movement < " + MIN_MOVEMENT + "m, ignoring.");
                return;
            }
            totalDistance += distance;
            totalCarbon = totalDistance * CarbonUtils.getCarbonEmissionRate(selectedMode);

            Log.d(TAG, "Total distance: " + totalDistance + "m, Total carbon: " + totalCarbon +
                    "kg, Mode: " + selectedMode);

            saveValues();
            sendUpdates();
            Log.d(TAG, "Updating Firestore with this segment's carbon emission.");
            updateTransportationInFirestore(distance, selectedMode);
        }
        lastLocation = location;
        lastMode = selectedMode; // Update lastMode for next update.
    }

    /**
     * Determines travel mode based on speed (m/s).
     * Returns "Walking", "Biking", "Motorcycle", "Jeepney", or "Car".
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

    // Save current totals and mode to shared preferences.
    private void saveValues() {
        sharedPreferences.edit()
                .putFloat("totalDistance", totalDistance)
                .putFloat("totalCarbon", totalCarbon)
                .putString("selected_mode", selectedMode)
                .apply();
    }

    // Broadcast current tracking updates so that the UI can update.
    private void sendUpdates() {
        Intent intent = new Intent("TRACKING_UPDATE");
        intent.putExtra("total_distance", (int) totalDistance);
        intent.putExtra("total_carbon", (int) totalCarbon);
        intent.putExtra("travel_mode", selectedMode);
        sendBroadcast(intent);
    }

    /**
     * Updates Firestore with the latest segment's carbon data.
     * If mode is "Walking", also calculates the saved carbon emission
     * (difference between using a car and walking).
     */
    private void updateTransportationInFirestore(float distance, String mode) {
        // Calculate carbon for this segment.
        float segmentCarbon = distance * CarbonUtils.getCarbonEmissionRate(mode);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "No user logged in; cannot update Firestore.");
            return;
        }
        String displayName = user.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            Log.e(TAG, "User has no display name; cannot update Firestore with display name as document ID.");
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
            Map<String, Object> updates = new HashMap<>();
            updates.put("total_carbon_footprint", String.valueOf(updatedCarbon));

            if (mode.equals("Walking")) {
                float carRate = CarbonUtils.getCarbonEmissionRate("Car");
                float walkingRate = CarbonUtils.getCarbonEmissionRate("Walking");
                float segmentReduction = distance * (carRate - walkingRate);
                double updatedReduction = currentReduction + segmentReduction;
                updates.put("total_carbon_reduced", String.valueOf(updatedReduction));
            }
            docRef.set(updates, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Firestore updated. New total carbon: " + updatedCarbon +
                                (mode.equals("Walking") ? ", and carbon reduction updated." : ""));
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to update Firestore", e));
        }).addOnFailureListener(e -> Log.e(TAG, "Failed to get daily document for " + displayName, e));
    }

    // Returns the current date as a string in "yyyy-MM-dd" format.
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
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
