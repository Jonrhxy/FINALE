package com.example.firstpage;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class FragmentRewardM extends AppCompatActivity {

    private Button redeemButton1, redeemButton2, redeemButton3, redeemButton4;
    private TextView TotalPoints;
    private SharedPreferences sharedPreferences;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private int userTotalPoints = 0;

    // Required points to redeem each reward
    private final int MASUG5PER_POINTS = 1000;
    private final int MASUG10PER_POINTS = 1000;
    private final int WANI5PER_POINTS = 1000;
    private final int PLAYM5PER_POINTS = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_reward_m);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("RewardPrefs", MODE_PRIVATE);

        // Initialize UI elements
        redeemButton1 = findViewById(R.id.claim_masug5per);
        redeemButton2 = findViewById(R.id.claim_masug10per);
        redeemButton3 = findViewById(R.id.claim_wani5per);
        redeemButton4 = findViewById(R.id.claim_playm5per);
        TotalPoints = findViewById(R.id.pointsinR);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Fetch and update points
        fetchAndUpdatePoints();

        // Set click listeners
        redeemButton1.setOnClickListener(v -> redeemReward("MASUG5PER_CLAIMED", MASUG5PER_POINTS, redeemButton1));
        redeemButton2.setOnClickListener(v -> redeemReward("MASUG10PER_CLAIMED", MASUG10PER_POINTS, redeemButton2));
        redeemButton3.setOnClickListener(v -> redeemReward("WANI5PER_CLAIMED", WANI5PER_POINTS, redeemButton3));
        redeemButton4.setOnClickListener(v -> redeemReward("PLAYM5PER_CLAIMED", PLAYM5PER_POINTS, redeemButton4));
    }

    private void fetchAndUpdatePoints() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Error: User not logged in!", Toast.LENGTH_LONG).show();
            return;
        }

        String username = mAuth.getCurrentUser().getDisplayName();
        if (username == null || username.isEmpty()) {
            username = mAuth.getCurrentUser().getUid(); // Fallback to UID if DisplayName is empty
        }

        DocumentReference gameRef = db.collection("Games").document(username);
        DocumentReference quizRef = db.collection("Quiz").document(username);
        DocumentReference quizRef2 = db.collection("Quiz2").document(username);

        // Fetch all points in parallel
        gameRef.get().addOnSuccessListener(gameSnapshot -> {
            int gamePoints = gameSnapshot.exists() && gameSnapshot.contains("highScore") ?
                    gameSnapshot.getLong("highScore").intValue() : 0;

            quizRef.get().addOnSuccessListener(quizSnapshot -> {
                int quizPoints = quizSnapshot.exists() && quizSnapshot.contains("score") ?
                        quizSnapshot.getLong("score").intValue() : 0;

                quizRef2.get().addOnSuccessListener(quiz2Snapshot -> {
                    int quiz2Points = quiz2Snapshot.exists() && quiz2Snapshot.contains("score") ?
                            quiz2Snapshot.getLong("score").intValue() : 0;

                    // Compute total points
                    userTotalPoints = gamePoints + quizPoints + quiz2Points;

                    // Update UI
                    TotalPoints.setText(String.format(Locale.getDefault(), "Total Points: %d", userTotalPoints));

                    // Update button text based on eligibility
                    updateButtonStatus();

                }).addOnFailureListener(e -> showError("Error fetching Quiz2 points", e));
            }).addOnFailureListener(e -> showError("Error fetching Quiz points", e));
        }).addOnFailureListener(e -> showError("Error fetching Game points", e));
    }

    private void updateButtonStatus() {
        updateButtonText(redeemButton1, "MASUG5PER_CLAIMED", MASUG5PER_POINTS);
        updateButtonText(redeemButton2, "MASUG10PER_CLAIMED", MASUG10PER_POINTS);
        updateButtonText(redeemButton3, "WANI5PER_CLAIMED", WANI5PER_POINTS);
        updateButtonText(redeemButton4, "PLAYM5PER_CLAIMED", PLAYM5PER_POINTS);
    }

    private void updateButtonText(Button button, String rewardKey, int requiredPoints) {
        if (sharedPreferences.getBoolean(rewardKey, false)) {
            button.setText("Reward Claimed");
        } else if (userTotalPoints >= requiredPoints) {
            button.setText("Claim Reward");
        } else {
            button.setText("Insufficient Points");
        }
    }

    private void redeemReward(String rewardKey, int requiredPoints, Button button) {
        if (sharedPreferences.getBoolean(rewardKey, false)) {
            Toast.makeText(this, "Reward already claimed!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (userTotalPoints >= requiredPoints) {
            showRedeemDialog(rewardKey, button);
        } else {
            Toast.makeText(this, "Insufficient Points to claim this reward!", Toast.LENGTH_SHORT).show();
        }
    }

    private void showRedeemDialog(String rewardKey, Button button) {
        FragmentManager fm = getSupportFragmentManager();
        RedeemDialog redeemDialog = new RedeemDialog();
        redeemDialog.show(fm, "redeem_dialog");

        // Mark reward as claimed
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(rewardKey, true);
        editor.apply();

        // Update button text
        button.setText("Reward Claimed");
    }

    private void showError(String message, Exception e) {
        Log.e("FragmentRewardM", message, e);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}