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
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestoreException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.os.Handler;
import android.os.Looper;

public class Transportation extends AppCompatActivity {
    private SharedPreferences sharedPreferences;
    private TextView distanceTextView, carbonTextView, travelModeTextView;
    private static final int LOCATION_PERMISSION_REQUEST = 1000;
    private int totalDistance = 0;
    private int totalCarbon = 0;
    private String selectedMode = "Car";
    private FirebaseFirestore db;
    private FirebaseUser user;
    private DocumentReference dailyDocRef;

    // Local broadcast receiver for updates from TrackingService
    private final BroadcastReceiver trackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            totalDistance = intent.getIntExtra("total_distance", 0);
            totalCarbon = intent.getIntExtra("total_carbon", 0);
            selectedMode = intent.getStringExtra("travel_mode");
            updateUI(totalDistance, totalCarbon, selectedMode);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transportation);

        distanceTextView = findViewById(R.id.textViewDistance);
        carbonTextView = findViewById(R.id.textViewCarbon);
        travelModeTextView = findViewById(R.id.textViewTravelMode);

        sharedPreferences = getSharedPreferences("TrackingData", Context.MODE_PRIVATE);

        db = FirebaseFirestore.getInstance();
        user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String today = getCurrentDateString();
            dailyDocRef = db.collection("transportation")
                    .document(user.getUid())
                    .collection("daily")
                    .document(today);
            listenToFirestoreUpdates();
        }

        registerReceiver(trackingReceiver, new IntentFilter("TRACKING_UPDATE"));

        checkPermissions();
        checkGpsEnabled();
        startForegroundTracking();
    }

    private void listenToFirestoreUpdates() {
        if (dailyDocRef != null) {
            dailyDocRef.addSnapshotListener((DocumentSnapshot snapshot, FirebaseFirestoreException e) -> {
                if (e != null) {
                    Toast.makeText(Transportation.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (snapshot != null && snapshot.exists()) {
                    String carbonStr = snapshot.getString("total_carbon_footprint");
                    int firestoreCarbon = 0;
                    if (carbonStr != null && !carbonStr.isEmpty()) {
                        try {
                            firestoreCarbon = (int) Double.parseDouble(carbonStr);
                        } catch (NumberFormatException ex) {
                            firestoreCarbon = 0;
                        }
                    }
                    carbonTextView.setText("CO₂ Emission: " + firestoreCarbon + " kg");
                } else {
                    carbonTextView.setText("CO₂ Emission: 0 kg");
                }
            });
        }
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

    private void updateUI(int totalDistance, int totalCarbon, String selectedMode) {
        distanceTextView.setText("Distance Traveled: " + totalDistance + " meters");
        carbonTextView.setText("CO₂ Emission: " + totalCarbon + " kg");
        travelModeTextView.setText("Mode: " + selectedMode);
    }

    private String getCurrentDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date());
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(trackingReceiver);
        super.onDestroy();
    }
}
