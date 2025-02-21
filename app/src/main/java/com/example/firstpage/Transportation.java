package com.example.firstpage;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class Transportation extends AppCompatActivity {
    private SharedPreferences sharedPreferences;
    private TextView distanceTextView, carbonTextView, travelModeTextView;
    private Button resetButton;
    private static final int LOCATION_PERMISSION_REQUEST = 1000;
    private int totalDistance = 0;
    private int totalCarbon = 0;
    private String selectedMode = "Car";

    private final BroadcastReceiver trackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            totalDistance = intent.getIntExtra("total_distance", 0);
            totalCarbon = intent.getIntExtra("total_carbon", 0);
            selectedMode = intent.getStringExtra("travel_mode");
            updateUI();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transportation);

        distanceTextView = findViewById(R.id.textViewDistance);
        carbonTextView = findViewById(R.id.textViewCarbon);
        travelModeTextView = findViewById(R.id.textViewTravelMode);
        resetButton = findViewById(R.id.resetButton);

        // Use same SharedPreferences name as TrackingService ("TrackingData")
        sharedPreferences = getSharedPreferences("TrackingData", Context.MODE_PRIVATE);
        totalDistance = (int) sharedPreferences.getFloat("totalDistance", 0);
        totalCarbon = (int) sharedPreferences.getFloat("totalCarbon", 0);
        selectedMode = sharedPreferences.getString("selected_mode", "Car");

        updateUI();
        checkPermissions();
        checkGpsEnabled();

        registerReceiver(trackingReceiver, new IntentFilter("TRACKING_UPDATE"), Context.RECEIVER_NOT_EXPORTED);
        startForegroundTracking();

        resetButton.setOnClickListener(v -> resetTracking());
    }

    private void checkGpsEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager != null && !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "GPS is disabled! Please enable it.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        }
    }

    private void checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
            }, LOCATION_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startForegroundTracking();
            } else {
                Toast.makeText(this, "Location permission required!", Toast.LENGTH_LONG).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void startForegroundTracking() {
        Intent serviceIntent = new Intent(this, TrackingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void resetTracking() {
        Intent serviceIntent = new Intent(this, TrackingService.class);
        stopService(serviceIntent);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();

        distanceTextView.setText("Distance Traveled: 0 m");
        carbonTextView.setText("CO₂ Emission: 0 kg");
        travelModeTextView.setText("Mode: Unknown");

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startService(serviceIntent);
            Intent forceUpdateIntent = new Intent(this, TrackingService.class);
            forceUpdateIntent.setAction("FORCE_UPDATE");
            startService(forceUpdateIntent);
        }, 1000);
    }

    private void updateUI() {
        distanceTextView.setText("Distance Traveled: " + totalDistance + " meters");
        carbonTextView.setText("CO₂ Emission: " + totalCarbon + " kg");
        travelModeTextView.setText("Mode: " + selectedMode);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(trackingReceiver);
        super.onDestroy();
    }
}