package com.example.firstpage;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class Loadingpage extends AppCompatActivity {

    private VideoView videoView;
    private ProgressBar progressBar;
    private static final int LOADING_TIME = 3000; // 3 seconds delay
    private static final String TAG = "LoadingPage";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loadingpage);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize VideoView and ProgressBar
        videoView = findViewById(R.id.videoViewBackground);
        progressBar = findViewById(R.id.progressBar);

        if (videoView == null || progressBar == null) {
            Log.e(TAG, "VideoView or ProgressBar is not initialized.");
            return;
        }

        // Hide ProgressBar as it will remain invisible
        progressBar.setVisibility(View.INVISIBLE);

        // Set up VideoView with background video
        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.mbgblur);
        videoView.setVideoURI(uri);
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            videoView.start();
        });

        // Delay for 3 seconds before checking user state and redirecting
        new Handler().postDelayed(this::checkUserState, LOADING_TIME);
    }

    private void checkUserState() {
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            navigateTo(Signin.class);
            return;
        }

        String userEmail = user.getEmail();
        if (userEmail == null) {
            Log.e(TAG, "User email is null.");
            navigateTo(Signin.class);
            return;
        }

        // Fetch username from Firestore based on email
        fetchUsername(userEmail);
    }

    private void fetchUsername(String userEmail) {
        db.collection("users").whereEqualTo("email", userEmail)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                        String username = document.getString("username");

                        if (username == null || username.isEmpty()) {
                            Log.e(TAG, "Username not found.");
                            navigateTo(Signin.class);
                            return;
                        }

                        // Check if user has any data (BMI or Carbon Footprint)
                        checkUserData(username);
                    } else {
                        Log.e(TAG, "No user data found.");
                        navigateTo(Signin.class);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching username", e);
                    navigateTo(Signin.class);
                });
    }

    private void checkUserData(String username) {
        db.collection("bmi_records").document(username).get()
                .addOnSuccessListener(bmiSnapshot -> {
                    db.collection("carbon_footprint").document(username).get()
                            .addOnSuccessListener(footprintSnapshot -> {
                                if (bmiSnapshot.exists() || footprintSnapshot.exists()) {
                                    // User has at least one record, go to navbar
                                    navigateTo(navbar.class);
                                } else {
                                    // User has no data at all, go to Question1
                                    navigateTo(Question1.class);
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error checking footprint data", e);
                                navigateTo(Question1.class);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking BMI data", e);
                    navigateTo(Question1.class);
                });
    }

    private void navigateTo(Class<?> destination) {
        Intent intent = new Intent(Loadingpage.this, destination);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoView != null) {
            videoView.stopPlayback();
        }
    }
}