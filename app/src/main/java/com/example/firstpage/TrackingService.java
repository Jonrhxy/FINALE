package com.example.firstpage;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
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
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

public class TrackingService extends Service {
    private static final String CHANNEL_ID = "TrackingServiceChannel";

    // Relaxed accuracy & movement thresholds for indoor use
    private static final float MIN_ACCURACY = 100.0f; // Accept location up to 100m accuracy
    private static final float MIN_MOVEMENT = 0.5f;   // Count movements >= 0.5m

    // Adjusted speed thresholds (in m/s):
    // ~1.5 m/s ~ 5.4 km/h for walking
    // ~3.0 m/s ~ 10.8 km/h for jogging
    // ~11.0 m/s ~ 39.6 km/h for faster biking
    // ~16.7 m/s ~ 60 km/h for motorcycle
    // ~22.2 m/s ~ 80 km/h for jeepney
    // above 22.2 m/s ~ car
    private static final float WALK_MAX_SPEED = 1.5f;         // walking < 1.5 m/s
    private static final float BIKE_MAX_SPEED = 11.0f;        // biking < 11 m/s
    private static final float MOTORCYCLE_MAX_SPEED = 16.7f;  // < 60 km/h
    private static final float JEEPNEY_MAX_SPEED = 22.2f;     // < 80 km/h

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location lastLocation = null;
    private float totalDistance = 0;
    private float totalCarbon = 0;
    private String selectedMode = "Car"; // auto-detected
    private SharedPreferences sharedPreferences;
    private long lastUpdateTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        sharedPreferences = getSharedPreferences("TrackingData", Context.MODE_PRIVATE);
        selectedMode = sharedPreferences.getString("selected_mode", "Car");

        // Debug: check if GPS is enabled
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            Log.d("TrackingService", "GPS enabled: " + lm.isProviderEnabled(LocationManager.GPS_PROVIDER));
        }

        createNotificationChannel();
        startForegroundServiceWithNotification();
        setupLocationUpdates();
        requestImmediateLocationUpdate();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Tracking Service", NotificationManager.IMPORTANCE_LOW);
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
        // Use PRIORITY_BALANCED_POWER_ACCURACY for fallback to network indoors
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
            Log.e("TrackingService", "❌ Location permission not granted!");
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
        Log.d("TrackingService", "✅ GPS/Network tracking started.");
    }

    private void updateLocation(Location location) {
        long currentTime = System.currentTimeMillis();
        // Limit updates to every 2 seconds
        if (currentTime - lastUpdateTime < 2000) return;
        lastUpdateTime = currentTime;

        Log.d("TrackingService", "📍 New location: Lat=" + location.getLatitude()
                + ", Lng=" + location.getLongitude()
                + ", Accuracy=" + location.getAccuracy() + "m"
                + ", Speed=" + location.getSpeed() + "m/s");

        if (location.getAccuracy() > MIN_ACCURACY) {
            Log.d("TrackingService", "⚠️ Poor accuracy (" + location.getAccuracy() + "m), forcing refresh.");
            requestImmediateLocationUpdate();
            return;
        }

        float speed = location.getSpeed();
        selectedMode = getTravelMode(speed);

        if (lastLocation != null) {
            float distance = lastLocation.distanceTo(location);
            Log.d("TrackingService", "📏 Distance: " + distance + "m");
            if (distance < MIN_MOVEMENT) {
                Log.d("TrackingService", "⚠️ Movement < " + MIN_MOVEMENT + "m, ignoring.");
                return;
            }
            totalDistance += distance;
            totalCarbon = totalDistance * CarbonUtils.getCarbonEmissionRate(selectedMode);
            saveValues();
            sendUpdates();
        }
        lastLocation = location;
    }

    /**
     * Decide travel mode based on speed in m/s
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
    }

    private void sendUpdates() {
        Intent intent = new Intent("TRACKING_UPDATE");
        intent.putExtra("total_distance", (int) totalDistance);
        intent.putExtra("total_carbon", (int) totalCarbon);
        intent.putExtra("travel_mode", selectedMode);
        sendBroadcast(intent);
    }

    @SuppressLint("MissingPermission")
    private void requestImmediateLocationUpdate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e("TrackingService", "❌ Location permission not granted!");
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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if ("FORCE_UPDATE".equals(intent.getAction())) {
                requestImmediateLocationUpdate();
            }
        }
        startTracking();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}